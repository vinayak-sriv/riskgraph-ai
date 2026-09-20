package ai.riskgraph.analyzer.extract;

import static ai.riskgraph.analyzer.model.AnalysisModels.ChangedRange;
import static ai.riskgraph.analyzer.model.AnalysisModels.DependencyPath;
import static ai.riskgraph.analyzer.model.AnalysisModels.Diagnostic;
import static ai.riskgraph.analyzer.model.AnalysisModels.EndpointEvidence;
import static ai.riskgraph.analyzer.model.AnalysisModels.EndpointIr;
import static ai.riskgraph.analyzer.model.AnalysisModels.ExtractionConfidence;
import static ai.riskgraph.analyzer.model.AnalysisModels.ExtractionCoverage;
import static ai.riskgraph.analyzer.model.AnalysisModels.SensitivityEvidence;
import static ai.riskgraph.analyzer.model.AnalysisModels.SourceLocation;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

@Component
public class SpringEndpointExtractor {
    private static final Pattern REQUEST_METHOD_PATTERN = Pattern.compile("RequestMethod\\.([A-Z]+)");
    private static final Set<String> MAPPING_ANNOTATIONS = Set.of(
            "GetMapping", "PostMapping", "PutMapping", "PatchMapping", "DeleteMapping", "RequestMapping");

    private final SensitivityPolicy sensitivityPolicy;
    private final SpringMappingResolver mappingResolver = new SpringMappingResolver();
    private final AnnotationAuthorizationExtractor authorizationExtractor =
            new AnnotationAuthorizationExtractor();
    private final ChangedSurfaceAnalyzer changedSurface = new ChangedSurfaceAnalyzer(MAPPING_ANNOTATIONS);
    private final ConfidenceEvaluator confidenceEvaluator = new ConfidenceEvaluator();
    private final DependencyPathResolver dependencyResolver;

    public SpringEndpointExtractor(SensitivityPolicy sensitivityPolicy) {
        this.sensitivityPolicy = sensitivityPolicy;
        this.dependencyResolver = new DependencyPathResolver(sensitivityPolicy, changedSurface);
    }

    public ExtractionResult extract(Path snapshot, Map<String, List<ChangedRange>> changedRanges) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (changedRanges.isEmpty()) {
            return new ExtractionResult(List.of(), new ExtractionCoverage(
                    0, 0, 0, 0, 0, 1.0), List.of());
        }
        List<Path> inputRoots = modelInputRoots(snapshot, changedRanges, diagnostics);
        if (inputRoots.isEmpty()) {
            return new ExtractionResult(List.of(), new ExtractionCoverage(
                    0, 0, 0, 0, 0, 0.0), List.copyOf(diagnostics));
        }
        Set<String> analyzedChangedPaths = changedRanges.keySet().stream()
                .filter(path -> belongsToInputRoot(snapshot, inputRoots, path))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        // One Launcher over every source root. Building a model per root made a
        // multi-module repository pay N full Spoon parses and hold N live models.
        CtModel combined = buildModel(inputRoots, diagnostics);
        if (combined == null) {
            return new ExtractionResult(List.of(), new ExtractionCoverage(
                    analyzedChangedPaths.size(), 0, 0, 0, 0, 0.0), diagnostics);
        }
        List<CtModel> models = List.of(combined);

        Set<String> modeledPaths = new HashSet<>();
        models.stream().flatMap(model -> model.getAllTypes().stream())
                .filter(type -> type.getPosition().isValidPosition())
                .map(type -> relativePath(snapshot, type.getPosition().getFile()))
                .forEach(modeledPaths::add);
        analyzedChangedPaths.stream()
                .filter(path -> !path.endsWith("package-info.java") && !path.endsWith("module-info.java"))
                .filter(path -> !modeledPaths.contains(path))
                .forEach(path -> diagnostics.add(new Diagnostic(
                        "ERROR", "SOURCE_NOT_PARSED", "Changed Java source was not represented in the Spoon model", path)));

        List<EndpointEvidence> endpoints = new ArrayList<>();
        int controllers = 0;
        for (CtModel model : models) {
            ModelExtraction modelExtraction = extractModel(snapshot, model, changedRanges, diagnostics);
            endpoints.addAll(modelExtraction.endpoints());
            controllers += modelExtraction.controllers();
        }

        endpoints.sort(Comparator.comparing((EndpointEvidence value) -> value.endpoint().endpoint())
                .thenComparing(value -> value.endpoint().method())
                .thenComparing(EndpointEvidence::qualified_controller)
                .thenComparing(EndpointEvidence::method_signature));
        int withService = (int) endpoints.stream().filter(value -> value.endpoint().service() != null).count();
        int withRepository = (int) endpoints.stream().filter(value -> value.endpoint().repository() != null).count();
        double coverage = endpoints.isEmpty() ? 1.0
                : (withService + withRepository) / (2.0 * endpoints.size());
        return new ExtractionResult(List.copyOf(endpoints), new ExtractionCoverage(
                analyzedChangedPaths.size(), controllers, endpoints.size(), withService, withRepository,
                Math.round(coverage * 1000.0) / 1000.0), List.copyOf(diagnostics));
    }

    private ModelExtraction extractModel(
            Path snapshot,
            CtModel model,
            Map<String, List<ChangedRange>> changedRanges,
            List<Diagnostic> diagnostics
    ) {
        ExecutableResolver executableResolver = new ExecutableResolver(model.getAllTypes());
        // One traversal answers both questions. getElements materializes every
        // CtMethod in the model, so doing it twice doubled the cost for nothing.
        List<CtMethod<?>> allMethods = model.getElements(new TypeFilter<>(CtMethod.class));
        List<CtMethod<?>> securityFilterChains = allMethods.stream()
                .filter(this::isSecurityFilterChainMethod)
                .toList();
        boolean filterSecurityPresent = !securityFilterChains.isEmpty();
        boolean filterSecurityChanged = securityFilterChains.stream()
                .anyMatch(method -> changedSurface.intersects(snapshot, method, changedRanges));
        if (filterSecurityPresent) {
            diagnostics.add(new Diagnostic("WARNING", "UNRESOLVED_SECURITY_FILTER_CHAIN",
                    "SecurityFilterChain authorization is outside the annotation-only MVP scope", null));
        }

        List<EndpointEvidence> endpoints = new ArrayList<>();
        int controllers = 0;
        for (CtType<?> type : model.getAllTypes()) {
            if (!isController(type)) {
                continue;
            }
            controllers++;
            SpringMappingResolver.Result classMapping = mappingResolver.resolve(annotation(type, "RequestMapping"));
            if (classMapping.status() == SpringMappingResolver.Status.UNRESOLVED) {
                diagnostics.add(new Diagnostic("WARNING", "UNRESOLVED_SPRING_MAPPING",
                        "Controller mapping is not a provable compile-time string: " + classMapping.expression(),
                        relativePath(snapshot, type.getPosition().getFile())));
                continue;
            }
            CtAnnotation<?> classAuthorization = authorizationExtractor.find(type);
            boolean classSecurityChanged = changedSurface.classSecurityChanged(snapshot, type, changedRanges);
            boolean wiringChanged = type.getFields().stream()
                .anyMatch(field -> changedSurface.intersects(snapshot, field, changedRanges));
            if (type instanceof spoon.reflect.declaration.CtClass<?> concrete) {
                wiringChanged |= concrete.getConstructors().stream()
                    .anyMatch(constructor -> changedSurface.intersects(snapshot, constructor, changedRanges));
            }
            for (CtMethod<?> method : type.getMethods()) {
                CtAnnotation<?> mapping = mappingAnnotation(method);
                if (mapping == null) {
                    continue;
                }
                DependencyPathResolver.Result resolution = dependencyResolver.resolve(
                        snapshot, method, executableResolver, changedRanges);
                boolean endpointChanged = changedSurface.methodSurfaceChanged(snapshot, method, changedRanges);
                if (!endpointChanged && !classSecurityChanged && !filterSecurityChanged
                        && !wiringChanged && !resolution.changedDependency()) {
                    continue;
                }

                SpringMappingResolver.Result methodMapping = mappingResolver.resolve(mapping);
                if (methodMapping.status() == SpringMappingResolver.Status.UNRESOLVED) {
                    diagnostics.add(new Diagnostic("WARNING", "UNRESOLVED_SPRING_MAPPING",
                            "Method mapping is not a provable compile-time string: " + methodMapping.expression(),
                            sourceLocation(snapshot, method).path()));
                    continue;
                }
                List<String> httpMethods = httpMethods(mapping);
                CtAnnotation<?> authorization = authorizationExtractor.find(method);
                if (authorization == null) {
                    authorization = classAuthorization;
                }
                DependencyPathResolver.ServiceAuthorization serviceAuthorization =
                        resolution.serviceAuthorization();
                boolean annotationAuthenticated = authorization != null;
                boolean serviceAuthenticated = !annotationAuthenticated && serviceAuthorization != null;
                boolean authenticatedByAnnotation = annotationAuthenticated || serviceAuthenticated;
                String annotationRole = annotationAuthenticated
                        ? authorizationExtractor.requiredRole(authorization)
                        : serviceAuthenticated ? serviceAuthorization.role() : null;
                DependencyPath primaryPath = dependencyResolver.primaryPath(resolution.paths(), type);
                SensitivityPolicy.Classification classification = sensitivityPolicy.classify(
                        primaryPath.resource(), primaryPath.repository() != null);
                SourceLocation location = sourceLocation(snapshot, method);

                addDiagnostics(diagnostics, location, authenticatedByAnnotation, annotationRole, resolution);
                if (classification.defaulted() && primaryPath.repository() != null) {
                    diagnostics.add(new Diagnostic("INFO", "SENSITIVITY_POLICY_DEFAULTED",
                            "Unmatched repository-backed resource was conservatively classified as "
                                    + classification.sensitivity(),
                            location.path()));
                }
                boolean simpleAuthorization = annotationAuthenticated
                        ? authorizationExtractor.isSimple(authorization)
                        : serviceAuthorization == null || serviceAuthorization.simple();
                if (authenticatedByAnnotation && annotationRole != null && !simpleAuthorization) {
                    diagnostics.add(new Diagnostic("WARNING", "COMPLEX_AUTHORIZATION",
                        "Authorization cannot be fully represented by a single canonical role", location.path()));
                }
                emitRouteEndpoints(type, method,
                        new AuthorizedMapping(classMapping, methodMapping, authenticatedByAnnotation,
                                annotationRole, simpleAuthorization),
                        filterSecurityPresent, resolution, primaryPath, classification, location,
                        httpMethods, diagnostics, endpoints);
            }
        }
        return new ModelExtraction(List.copyOf(endpoints), controllers);
    }

    // Bundles the mapping + authorization facts extractModel already resolved for one
    // controller method, so emitRouteEndpoints below can take one parameter for them
    // instead of five (Checkstyle's ParameterNumber limit is 12).
    private record AuthorizedMapping(
            SpringMappingResolver.Result classMapping,
            SpringMappingResolver.Result methodMapping,
            boolean authenticatedByAnnotation,
            String annotationRole,
            boolean simpleAuthorization
    ) {
    }

    // Split out of extractModel to stay under Checkstyle's MethodLength limit; this is
    // the leaf that turns one resolved (controller, method) pair into its route(s).
    private void emitRouteEndpoints(
            CtType<?> type,
            CtMethod<?> method,
            AuthorizedMapping mapping,
            boolean filterSecurityPresent,
            DependencyPathResolver.Result resolution,
            DependencyPath primaryPath,
            SensitivityPolicy.Classification classification,
            SourceLocation location,
            List<String> httpMethods,
            List<Diagnostic> diagnostics,
            List<EndpointEvidence> endpoints
    ) {
        boolean authenticatedByAnnotation = mapping.authenticatedByAnnotation();
        String annotationRole = mapping.annotationRole();
        boolean simpleAuthorization = mapping.simpleAuthorization();
        String callConfidence = confidenceEvaluator.callConfidence(resolution);
        for (String classPath : mapping.classMapping().paths()) {
            for (String methodPath : mapping.methodMapping().paths()) {
                String route = normalizePath(classPath, methodPath);
                boolean authenticated = authenticatedByAnnotation;
                String requiredRole = authenticatedByAnnotation ? annotationRole : null;
                boolean unresolvedFilter = !authenticatedByAnnotation
                        && filterSecurityPresent;
                String authorizationConfidence = !simpleAuthorization || unresolvedFilter
                        || resolution.serviceAuthorizationAmbiguous() ? "LOW" : "HIGH";
                if (unresolvedFilter) {
                    diagnostics.add(new Diagnostic("WARNING", "UNRESOLVED_ROUTE_AUTHORIZATION",
                            "SecurityFilterChain does not prove authorization for " + route, location.path()));
                }
                ExtractionConfidence confidence = new ExtractionConfidence(
                        "HIGH", authorizationConfidence, callConfidence,
                        confidenceEvaluator.minimum("HIGH", authorizationConfidence, callConfidence));
                for (String httpMethod : httpMethods) {
                    EndpointIr endpoint = new EndpointIr(
                            route, httpMethod, type.getSimpleName(), authenticated,
                            requiredRole, primaryPath.service(), primaryPath.repository(), primaryPath.resource(),
                            classification.sensitivity());
                    endpoints.add(new EndpointEvidence(
                            endpoint, location, type.getQualifiedName(), method.getSignature(), resolution.paths(),
                            new SensitivityEvidence(classification.sensitivity(), classification.source(),
                                    classification.matchedRule()),
                            confidence));
                }
            }
        }
    }

    private List<Path> modelInputRoots(
            Path snapshot,
            Map<String, List<ChangedRange>> changedRanges,
            List<Diagnostic> diagnostics
    ) {
        List<Path> conventionalRoots;
        try (var paths = Files.walk(snapshot)) {
            conventionalRoots = paths
                    .filter(Files::isDirectory)
                    .filter(path -> {
                        String relative = normalizedRelativePath(snapshot, path);
                        return relative.equals("src/main/java") || relative.endsWith("/src/main/java");
                    })
                    .sorted()
                    .toList();
        } catch (IOException error) {
            diagnostics.add(new Diagnostic("ERROR", "SOURCE_ROOT_DISCOVERY_FAILED",
                    "Unable to discover Java source roots", null));
            return List.of();
        }
        if (conventionalRoots.isEmpty()) {
            return List.of(snapshot);
        }
        List<Path> relevantRoots = conventionalRoots.stream()
                .filter(root -> changedRanges.keySet().stream()
                        .anyMatch(path -> belongsToInputRoot(snapshot, List.of(root), path)))
                .toList();
        if (relevantRoots.isEmpty()) {
            diagnostics.add(new Diagnostic("ERROR", "SOURCE_ROOT_NOT_IDENTIFIED",
                    "Changed Java source is outside a conventional src/main/java source root", null));
        }
        return relevantRoots;
    }

    private boolean belongsToInputRoot(Path snapshot, List<Path> inputRoots, String changedPath) {
        String normalizedChange = changedPath.replace('\\', '/');
        return inputRoots.stream().anyMatch(root -> {
            String relativeRoot = normalizedRelativePath(snapshot, root);
            return relativeRoot.isEmpty() || normalizedChange.equals(relativeRoot)
                    || normalizedChange.startsWith(relativeRoot + "/");
        });
    }

    private String normalizedRelativePath(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private CtModel buildModel(List<Path> inputRoots, List<Diagnostic> diagnostics) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(false);
        inputRoots.forEach(root -> launcher.addInputResource(root.toString()));
        try {
            CtModel model = launcher.buildModel();
            if (launcher.getEnvironment().getErrorCount() > 0) {
                diagnostics.add(new Diagnostic("ERROR", "SPOON_MODEL_FAILED",
                        "Spoon reported " + launcher.getEnvironment().getErrorCount() + " source parsing error(s)", null));
                return null;
            }
            return model;
        } catch (RuntimeException error) {
            diagnostics.add(new Diagnostic("ERROR", "SPOON_MODEL_FAILED",
                    error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(), null));
            return null;
        }
    }

    private void addDiagnostics(
            List<Diagnostic> diagnostics,
            SourceLocation location,
            boolean authenticated,
            String requiredRole,
            DependencyPathResolver.Result resolution
    ) {
        if (authenticated && requiredRole == null) {
            diagnostics.add(new Diagnostic("WARNING", "UNRESOLVED_AUTHORIZATION",
                    "Authorization annotation is present but its role/authority is outside the MVP expression subset",
                    location.path()));
        }
        if (resolution.ambiguous()) {
            diagnostics.add(new Diagnostic("WARNING", "AMBIGUOUS_CALL_RESOLUTION",
                    "One or more call targets could not be resolved uniquely", location.path()));
        }
        if (resolution.serviceAuthorizationAmbiguous()) {
            diagnostics.add(new Diagnostic("WARNING", "AMBIGUOUS_SERVICE_AUTHORIZATION",
                    "Resolved dependency paths do not share one provable service authorization requirement",
                    location.path()));
        }
        if (resolution.paths().size() > 1) {
            diagnostics.add(new Diagnostic("INFO", "MULTIPLE_DEPENDENCY_PATHS",
                    "Endpoint reaches multiple resolved dependency paths", location.path()));
        }
    }

    private boolean isSecurityFilterChainMethod(CtMethod<?> method) {
        return method.getType() != null
                && "SecurityFilterChain".equals(method.getType().getSimpleName());
    }

    public String configurationFingerprint() {
        return sensitivityPolicy.fingerprint();
    }

    private boolean isController(CtType<?> type) {
        return annotation(type, "RestController") != null || annotation(type, "Controller") != null;
    }

    private CtAnnotation<?> mappingAnnotation(CtMethod<?> method) {
        return method.getAnnotations().stream()
                .filter(value -> MAPPING_ANNOTATIONS.contains(value.getAnnotationType().getSimpleName()))
                .findFirst().orElse(null);
    }

    private CtAnnotation<?> annotation(CtType<?> type, String simpleName) {
        return type.getAnnotations().stream()
                .filter(value -> simpleName.equals(value.getAnnotationType().getSimpleName()))
                .findFirst().orElse(null);
    }

    private CtAnnotation<?> annotation(CtMethod<?> method, String simpleName) {
        return method.getAnnotations().stream()
                .filter(value -> simpleName.equals(value.getAnnotationType().getSimpleName()))
                .findFirst().orElse(null);
    }

    private List<String> httpMethods(CtAnnotation<?> mapping) {
        String annotationName = mapping.getAnnotationType().getSimpleName();
        if (!"RequestMapping".equals(annotationName)) {
            return List.of(annotationName.substring(0, annotationName.length() - "Mapping".length())
                    .toUpperCase(Locale.ROOT));
        }
        CtExpression<?> expression = mapping.getValues().get("method");
        if (expression == null) {
            return List.of("DELETE", "GET", "PATCH", "POST", "PUT");
        }
        Matcher matcher = REQUEST_METHOD_PATTERN.matcher(expression.toString());
        List<String> methods = new ArrayList<>();
        while (matcher.find()) {
            methods.add(matcher.group(1));
        }
        return methods.isEmpty() ? List.of("DELETE", "GET", "PATCH", "POST", "PUT") : methods;
    }

    private SourceLocation sourceLocation(Path snapshot, CtMethod<?> method) {
        return new SourceLocation(relativePath(snapshot, method.getPosition().getFile()),
                method.getPosition().getLine(), method.getPosition().getEndLine());
    }

    private String relativePath(Path snapshot, File file) {
        return snapshot.relativize(file.toPath().toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private String normalizePath(String classPath, String methodPath) {
        String joined = ("/" + classPath + "/" + methodPath).replaceAll("/{2,}", "/");
        return joined.length() > 1 && joined.endsWith("/") ? joined.substring(0, joined.length() - 1) : joined;
    }

    private record ModelExtraction(List<EndpointEvidence> endpoints, int controllers) {
    }

    public record ExtractionResult(
            List<EndpointEvidence> endpoints,
            ExtractionCoverage coverage,
            List<Diagnostic> diagnostics
    ) {
    }
}
