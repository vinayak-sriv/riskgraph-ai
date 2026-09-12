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
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

@Component
public class SpringEndpointExtractor {
    private static final Pattern ROLE_PATTERN = Pattern.compile(
            "has(?:Any)?(?:Role|Authority)\\s*\\(\\s*['\\\"]([^'\\\"]+)['\\\"]");
    private static final Pattern REQUEST_METHOD_PATTERN = Pattern.compile("RequestMethod\\.([A-Z]+)");
    private static final Set<String> MAPPING_ANNOTATIONS = Set.of(
            "GetMapping", "PostMapping", "PutMapping", "PatchMapping", "DeleteMapping", "RequestMapping");
    private static final List<String> AUTHORIZATION_ANNOTATIONS = List.of(
            "PreAuthorize", "Secured", "RolesAllowed");

    private final SensitivityPolicy sensitivityPolicy;
    private final SpringMappingResolver mappingResolver = new SpringMappingResolver();

    public SpringEndpointExtractor(SensitivityPolicy sensitivityPolicy) {
        this.sensitivityPolicy = sensitivityPolicy;
    }

    public ExtractionResult extract(Path snapshot, Map<String, List<ChangedRange>> changedRanges) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        CtModel model = buildModel(snapshot, diagnostics);
        if (model == null) {
            return new ExtractionResult(List.of(), new ExtractionCoverage(
                    changedRanges.size(), 0, 0, 0, 0, 0.0), diagnostics);
        }

        Set<String> modeledPaths = new HashSet<>();
        model.getAllTypes().stream()
                .filter(type -> type.getPosition().isValidPosition())
                .map(type -> relativePath(snapshot, type.getPosition().getFile()))
                .forEach(modeledPaths::add);
        changedRanges.keySet().stream()
                .filter(path -> !path.endsWith("package-info.java") && !path.endsWith("module-info.java"))
                .filter(path -> !modeledPaths.contains(path))
                .forEach(path -> diagnostics.add(new Diagnostic(
                        "ERROR", "SOURCE_NOT_PARSED", "Changed Java source was not represented in the Spoon model", path)));

        ExecutableResolver executableResolver = new ExecutableResolver(model.getAllTypes());
        boolean filterSecurityPresent = hasSecurityFilterChain(model);
        boolean filterSecurityChanged = securityFilterChanged(snapshot, model, changedRanges);
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
            CtAnnotation<?> classAuthorization = authorizationAnnotation(type);
            boolean classSecurityChanged = classSecurityChanged(snapshot, type, changedRanges);
            boolean wiringChanged = type.getFields().stream()
                .anyMatch(field -> intersectsChangedRanges(snapshot, field, changedRanges));
            if (type instanceof spoon.reflect.declaration.CtClass<?> concrete) {
                wiringChanged |= concrete.getConstructors().stream()
                    .anyMatch(constructor -> intersectsChangedRanges(snapshot, constructor, changedRanges));
            }
            for (CtMethod<?> method : type.getMethods()) {
                CtAnnotation<?> mapping = mappingAnnotation(method);
                if (mapping == null) {
                    continue;
                }
                Resolution resolution = resolveDependencies(
                        snapshot, method, executableResolver, changedRanges);
                boolean endpointChanged = methodSurfaceChanged(snapshot, method, changedRanges);
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
                CtAnnotation<?> authorization = authorizationAnnotation(method);
                if (authorization == null) {
                    authorization = classAuthorization;
                }
                String annotationRole = requiredRole(authorization);
                boolean annotationAuthenticated = authorization != null;
                DependencyPath primaryPath = primaryPath(resolution.paths(), type);
                SensitivityPolicy.Classification classification = sensitivityPolicy.classify(primaryPath.resource());
                SourceLocation location = sourceLocation(snapshot, method);
                String callConfidence = callConfidence(resolution);

                addDiagnostics(diagnostics, location, annotationAuthenticated, annotationRole, resolution);
                if (annotationAuthenticated && annotationRole != null && !simpleAuthorization(authorization)) {
                    diagnostics.add(new Diagnostic("WARNING", "COMPLEX_AUTHORIZATION",
                        "Authorization cannot be fully represented by a single canonical role", location.path()));
                }
                for (String classPath : classMapping.paths()) {
                    for (String methodPath : methodMapping.paths()) {
                        String route = normalizePath(classPath, methodPath);
                        boolean authenticated = annotationAuthenticated;
                        String requiredRole = annotationAuthenticated ? annotationRole : null;
                        boolean unresolvedFilter = !annotationAuthenticated
                                && filterSecurityPresent;
                        String authorizationConfidence = (annotationAuthenticated && !simpleAuthorization(authorization))
                                || unresolvedFilter ? "LOW" : "HIGH";
                        if (unresolvedFilter) {
                            diagnostics.add(new Diagnostic("WARNING", "UNRESOLVED_ROUTE_AUTHORIZATION",
                                    "SecurityFilterChain does not prove authorization for " + route, location.path()));
                        }
                        ExtractionConfidence confidence = new ExtractionConfidence(
                                "HIGH", authorizationConfidence, callConfidence,
                                minimumConfidence("HIGH", authorizationConfidence, callConfidence));
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
                changedRanges.size(), controllers, endpoints.size(), withService, withRepository,
                Math.round(coverage * 1000.0) / 1000.0), List.copyOf(diagnostics));
    }

    private CtModel buildModel(Path snapshot, List<Diagnostic> diagnostics) {
        Launcher launcher = new Launcher();
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.addInputResource(snapshot.toString());
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
            Resolution resolution
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
        if (resolution.paths().size() > 1) {
            diagnostics.add(new Diagnostic("INFO", "MULTIPLE_DEPENDENCY_PATHS",
                    "Endpoint reaches multiple resolved dependency paths", location.path()));
        }
    }

    private Resolution resolveDependencies(
            Path snapshot,
            CtMethod<?> endpointMethod,
            ExecutableResolver executableResolver,
            Map<String, List<ChangedRange>> changedRanges
    ) {
        Set<DependencyPath> paths = new LinkedHashSet<>();
        Set<String> discoveredServices = new LinkedHashSet<>();
        Queue<TraversalState> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(new TraversalState(null, endpointMethod));
        boolean changedDependency = false;
        boolean ambiguous = false;

        while (!queue.isEmpty()) {
            TraversalState state = queue.remove();
            CtMethod<?> method = state.method();
            String methodKey = state.rootService() + ":" + method.getDeclaringType().getQualifiedName()
                    + "#" + method.getSignature();
            if (!visited.add(methodKey)) {
                continue;
            }
            if (method != endpointMethod && intersectsChangedRanges(snapshot, method, changedRanges)) {
                changedDependency = true;
            }
            for (CtInvocation<?> invocation : directInvocations(method)) {
                ExecutableResolver.TypeResult typeResult = executableResolver.resolveType(invocation);
                if (typeResult.ambiguous()) {
                    ambiguous = true;
                    continue;
                }
                CtType<?> target = typeResult.type();
                String targetName = target == null ? invocationType(invocation) : target.getSimpleName();
                if (targetName == null) {
                    continue;
                }
                if (isRepository(targetName, target)) {
                    String resource = targetName.replaceFirst("Repository$", "");
                    paths.add(dependencyPath(state.rootService(), targetName, resource));
                    ExecutableResolver.MethodResult methodResult = target == null
                            ? new ExecutableResolver.MethodResult(null, false)
                            : executableResolver.resolveMethod(invocation, target);
                    ambiguous |= methodResult.ambiguous();
                    if (methodResult.method() != null
                            && intersectsChangedRanges(snapshot, methodResult.method(), changedRanges)) {
                        changedDependency = true;
                    }
                } else if (isService(targetName, target)) {
                    String rootService = state.rootService() == null ? targetName : state.rootService();
                    if (target == null) {
                        ambiguous = true;
                        continue;
                    }
                    ExecutableResolver.MethodResult methodResult = executableResolver.resolveMethod(invocation, target);
                    if (methodResult.ambiguous() || methodResult.method() == null) {
                        ambiguous = true;
                        continue;
                    }
                    discoveredServices.add(rootService);
                    queue.add(new TraversalState(rootService, methodResult.method()));
                }
            }
        }

        for (String service : discoveredServices) {
            if (paths.stream().noneMatch(path -> service.equals(path.service()))) {
                paths.add(dependencyPath(service, null, service.replaceFirst("Service$", "")));
            }
        }
        List<DependencyPath> ordered = paths.stream()
                .sorted(Comparator.comparing((DependencyPath path) -> path.service() == null ? "" : path.service())
                        .thenComparing(path -> path.repository() == null ? "" : path.repository())
                        .thenComparing(DependencyPath::resource))
                .toList();
        return new Resolution(ordered, changedDependency, ambiguous);
    }

    private List<CtInvocation<?>> directInvocations(CtMethod<?> method) {
        return method.getElements(new TypeFilter<>(CtInvocation.class)).stream()
                .<CtInvocation<?>>map(invocation -> invocation)
                .filter(invocation -> invocation.getParent(CtMethod.class) == method)
                .toList();
    }

    private DependencyPath primaryPath(List<DependencyPath> paths, CtType<?> controller) {
        if (!paths.isEmpty()) {
            return paths.getFirst();
        }
        return dependencyPath(null, null, controller.getSimpleName().replaceFirst("Controller$", ""));
    }

    private DependencyPath dependencyPath(String service, String repository, String resource) {
        return new DependencyPath(service, repository, resource, sensitivityPolicy.classify(resource).sensitivity());
    }

    private CtAnnotation<?> authorizationAnnotation(CtType<?> type) {
        return AUTHORIZATION_ANNOTATIONS.stream()
                .map(name -> annotation(type, name)).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private CtAnnotation<?> authorizationAnnotation(CtMethod<?> method) {
        return AUTHORIZATION_ANNOTATIONS.stream()
                .map(name -> annotation(method, name)).filter(Objects::nonNull).findFirst().orElse(null);
    }

    private String requiredRole(CtAnnotation<?> authorization) {
        if (authorization == null) {
            return null;
        }
        CtExpression<?> value = authorization.getValues().get("value");
        List<String> values = expressionStrings(value);
        String annotationName = authorization.getAnnotationType().getSimpleName();
        if ("Secured".equals(annotationName) || "RolesAllowed".equals(annotationName)) {
            return values.stream().findFirst().map(role -> role.replaceFirst("^ROLE_", "")).orElse(null);
        }
        String expression = values.stream().findFirst().orElse(value == null ? "" : value.toString());
        Matcher matcher = ROLE_PATTERN.matcher(expression);
        return matcher.find() ? matcher.group(1).replaceFirst("^ROLE_", "") : null;
    }

    private boolean simpleAuthorization(CtAnnotation<?> authorization) {
        if (authorization == null) return true;
        List<String> values = expressionStrings(authorization.getValues().get("value"));
        if (values.size() != 1) return false;
        if (!"PreAuthorize".equals(authorization.getAnnotationType().getSimpleName())) return true;
        // A role substring inside arbitrary SpEL is not a resolved access rule.
        return values.getFirst().matches("\\s*has(?:Role|Authority)\\s*\\(\\s*['\\\"][A-Za-z0-9_:-]+['\\\"]\\s*\\)\\s*");
    }

    private boolean classSecurityChanged(
            Path snapshot, CtType<?> type, Map<String, List<ChangedRange>> changedRanges) {
        boolean annotationChanged = type.getAnnotations().stream()
                .filter(annotation -> MAPPING_ANNOTATIONS.contains(annotation.getAnnotationType().getSimpleName())
                        || AUTHORIZATION_ANNOTATIONS.contains(annotation.getAnnotationType().getSimpleName())
                        || "RestController".equals(annotation.getAnnotationType().getSimpleName())
                        || "Controller".equals(annotation.getAnnotationType().getSimpleName()))
                .anyMatch(annotation -> intersectsChangedRanges(snapshot, annotation, changedRanges));
        if (annotationChanged || !type.getPosition().isValidPosition()) {
            return annotationChanged;
        }
        List<ChangedRange> ranges = rangesFor(snapshot, type, changedRanges);
        return intersects(type.getPosition().getLine(), type.getPosition().getLine(), ranges);
    }

    private boolean methodSurfaceChanged(
            Path snapshot, CtMethod<?> method, Map<String, List<ChangedRange>> changedRanges) {
        return intersectsChangedRanges(snapshot, method, changedRanges)
                || method.getAnnotations().stream()
                .anyMatch(annotation -> intersectsChangedRanges(snapshot, annotation, changedRanges));
    }

    private boolean securityFilterChanged(
            Path snapshot, CtModel model, Map<String, List<ChangedRange>> changedRanges) {
        return model.getElements(new TypeFilter<>(CtMethod.class)).stream()
                .filter(this::isSecurityFilterChainMethod)
                .anyMatch(method -> intersectsChangedRanges(snapshot, method, changedRanges));
    }

    private boolean hasSecurityFilterChain(CtModel model) {
        return model.getElements(new TypeFilter<>(CtMethod.class)).stream()
                .anyMatch(this::isSecurityFilterChainMethod);
    }

    private boolean isSecurityFilterChainMethod(CtMethod<?> method) {
        return method.getType() != null
                && "SecurityFilterChain".equals(method.getType().getSimpleName());
    }

    public String configurationFingerprint() {
        return sensitivityPolicy.fingerprint();
    }

    private boolean intersectsChangedRanges(
            Path snapshot, CtElement element, Map<String, List<ChangedRange>> changedRanges) {
        if (!element.getPosition().isValidPosition()) {
            return false;
        }
        return intersects(element.getPosition().getLine(), element.getPosition().getEndLine(),
                rangesFor(snapshot, element, changedRanges));
    }

    private List<ChangedRange> rangesFor(
            Path snapshot, CtElement element, Map<String, List<ChangedRange>> changedRanges) {
        if (!element.getPosition().isValidPosition()) {
            return List.of();
        }
        return changedRanges.getOrDefault(relativePath(snapshot, element.getPosition().getFile()), List.of());
    }

    private boolean intersects(int startLine, int endLine, List<ChangedRange> ranges) {
        return ranges.stream().anyMatch(range -> startLine <= range.end_line() && range.start_line() <= endLine);
    }

    private String callConfidence(Resolution resolution) {
        if (resolution.ambiguous()) {
            return "LOW";
        }
        if (resolution.paths().stream().anyMatch(path -> path.repository() != null)) {
            return "HIGH";
        }
        return resolution.paths().isEmpty() ? "LOW" : "MEDIUM";
    }

    private String minimumConfidence(String... values) {
        List<String> order = List.of("LOW", "MEDIUM", "HIGH");
        return Arrays.stream(values).min(Comparator.comparingInt(order::indexOf)).orElse("LOW");
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

    private List<String> expressionStrings(CtExpression<?> expression) {
        if (expression instanceof CtLiteral<?> literal && literal.getValue() instanceof String value) {
            return List.of(value);
        }
        if (expression instanceof CtNewArray<?> array) {
            return array.getElements().stream()
                    .filter(CtLiteral.class::isInstance).map(CtLiteral.class::cast).map(CtLiteral::getValue)
                    .filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
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

    private String invocationType(CtInvocation<?> invocation) {
        CtTypeReference<?> type = invocation.getTarget() == null ? null : invocation.getTarget().getType();
        if (type == null) {
            type = invocation.getExecutable().getDeclaringType();
        }
        return type == null ? null : type.getSimpleName();
    }

    private boolean isService(String name, CtType<?> type) {
        return name.endsWith("Service") || (type != null && annotation(type, "Service") != null);
    }

    private boolean isRepository(String name, CtType<?> type) {
        if (name.endsWith("Repository") || (type != null && annotation(type, "Repository") != null)) {
            return true;
        }
        return type != null && type.getSuperInterfaces().stream()
                .anyMatch(value -> value.getSimpleName().endsWith("Repository"));
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

    private record TraversalState(String rootService, CtMethod<?> method) {
    }

    private record Resolution(List<DependencyPath> paths, boolean changedDependency, boolean ambiguous) {
    }

    public record ExtractionResult(
            List<EndpointEvidence> endpoints,
            ExtractionCoverage coverage,
            List<Diagnostic> diagnostics
    ) {
    }
}
