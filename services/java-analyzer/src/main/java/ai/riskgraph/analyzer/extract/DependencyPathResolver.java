package ai.riskgraph.analyzer.extract;

import static ai.riskgraph.analyzer.model.AnalysisModels.ChangedRange;
import static ai.riskgraph.analyzer.model.AnalysisModels.DependencyPath;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

final class DependencyPathResolver {
    private final SensitivityPolicy sensitivityPolicy;
    private final ChangedSurfaceAnalyzer changedSurface;

    DependencyPathResolver(SensitivityPolicy sensitivityPolicy, ChangedSurfaceAnalyzer changedSurface) {
        this.sensitivityPolicy = sensitivityPolicy;
        this.changedSurface = changedSurface;
    }

    Result resolve(Path snapshot, CtMethod<?> endpointMethod, ExecutableResolver executableResolver,
            Map<String, List<ChangedRange>> changedRanges) {
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
            if (!visited.add(methodKey)) continue;
            if (method != endpointMethod && changedSurface.intersects(snapshot, method, changedRanges)) {
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
                if (targetName == null) continue;
                if (isRepository(targetName, target)) {
                    String resource = targetName.replaceFirst("Repository$", "");
                    paths.add(path(state.rootService(), targetName, resource));
                    ExecutableResolver.MethodResult methodResult = target == null
                            ? new ExecutableResolver.MethodResult(null, false)
                            : executableResolver.resolveMethod(invocation, target);
                    ambiguous |= methodResult.ambiguous();
                    if (methodResult.method() != null
                            && changedSurface.intersects(snapshot, methodResult.method(), changedRanges)) {
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
                paths.add(path(service, null, service.replaceFirst("Service$", "")));
            }
        }
        List<DependencyPath> ordered = paths.stream()
                .sorted(Comparator.comparing((DependencyPath path) -> path.service() == null ? "" : path.service())
                        .thenComparing(path -> path.repository() == null ? "" : path.repository())
                        .thenComparing(DependencyPath::resource))
                .toList();
        return new Result(ordered, changedDependency, ambiguous);
    }

    DependencyPath primaryPath(List<DependencyPath> paths, CtType<?> controller) {
        if (!paths.isEmpty()) return paths.getFirst();
        return path(null, null, controller.getSimpleName().replaceFirst("Controller$", ""));
    }

    private List<CtInvocation<?>> directInvocations(CtMethod<?> method) {
        return method.getElements(new TypeFilter<>(CtInvocation.class)).stream()
                .<CtInvocation<?>>map(invocation -> invocation)
                .filter(invocation -> invocation.getParent(CtMethod.class) == method)
                .toList();
    }

    private DependencyPath path(String service, String repository, String resource) {
        return new DependencyPath(service, repository, resource,
                sensitivityPolicy.classify(resource, repository != null).sensitivity());
    }

    private String invocationType(CtInvocation<?> invocation) {
        CtTypeReference<?> type = invocation.getTarget() == null ? null : invocation.getTarget().getType();
        if (type == null) type = invocation.getExecutable().getDeclaringType();
        return type == null ? null : type.getSimpleName();
    }

    private boolean isService(String name, CtType<?> type) {
        return name.endsWith("Service") || (type != null && annotation(type, "Service") != null);
    }

    private boolean isRepository(String name, CtType<?> type) {
        if (name.endsWith("Repository") || (type != null && annotation(type, "Repository") != null)) return true;
        return type != null && type.getSuperInterfaces().stream()
                .anyMatch(value -> value.getSimpleName().endsWith("Repository"));
    }

    private CtAnnotation<?> annotation(CtType<?> type, String simpleName) {
        return type.getAnnotations().stream()
                .filter(value -> simpleName.equals(value.getAnnotationType().getSimpleName()))
                .findFirst().orElse(null);
    }

    record Result(List<DependencyPath> paths, boolean changedDependency, boolean ambiguous) { }
    private record TraversalState(String rootService, CtMethod<?> method) { }
}
