package ai.riskgraph.analyzer.extract;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import spoon.reflect.CtModel;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.visitor.filter.TypeFilter;

/** Resolves the common exact-path requestMatchers/anyRequest authorization subset. */
final class AuthorizationResolver {
    // PHASE 2 — out of current scope: full SecurityFilterChain matcher, ordering,
    // authorization-manager, and custom DSL interpretation.
    enum Access { AUTHENTICATED, PERMIT_ALL, NONE, UNRESOLVED }

    private final Map<String, Access> exactRules;
    private final Access defaultRule;
    private final boolean configurationPresent;
    private final boolean unsupported;

    private AuthorizationResolver(
            Map<String, Access> exactRules, Access defaultRule,
            boolean configurationPresent, boolean unsupported) {
        this.exactRules = exactRules;
        this.defaultRule = defaultRule;
        this.configurationPresent = configurationPresent;
        this.unsupported = unsupported;
    }

    static AuthorizationResolver from(CtModel model, SpringMappingResolver mappings) {
        Map<String, Access> rules = new HashMap<>();
        Access defaultRule = Access.NONE;
        boolean present = false;
        boolean unsupported = false;
        for (CtInvocation<?> terminal : model.getElements(new TypeFilter<>(CtInvocation.class))) {
            String operation = terminal.getExecutable().getSimpleName();
            if (!List.of("authenticated", "permitAll", "hasRole", "hasAuthority", "access").contains(operation)) {
                continue;
            }
            if (!(terminal.getTarget() instanceof CtInvocation<?> matcher)) {
                continue;
            }
            CtMethod<?> enclosingMethod;
            try {
                enclosingMethod = terminal.getParent(CtMethod.class);
            } catch (spoon.reflect.declaration.ParentNotInitializedException ignored) {
                continue;
            }
            if (enclosingMethod == null || enclosingMethod.getType() == null
                    || !"SecurityFilterChain".equals(enclosingMethod.getType().getSimpleName())) {
                continue;
            }
            String matcherName = matcher.getExecutable().getSimpleName();
            if (!List.of("requestMatchers", "anyRequest").contains(matcherName)) {
                continue;
            }
            present = true;
            Access access = "authenticated".equals(operation) ? Access.AUTHENTICATED
                    : "permitAll".equals(operation) ? Access.PERMIT_ALL : Access.UNRESOLVED;
            if (access == Access.UNRESOLVED) unsupported = true;
            if ("anyRequest".equals(matcherName)) {
                if (defaultRule != Access.NONE && defaultRule != access) unsupported = true;
                defaultRule = defaultRule == Access.NONE ? access : merge(defaultRule, access);
                continue;
            }
            List<CtExpression<?>> pathArguments = matcher.getArguments().stream()
                    .filter(argument -> argument.getType() == null
                            || "java.lang.String".equals(argument.getType().getQualifiedName())
                            || "String".equals(argument.getType().getSimpleName()))
                    .toList();
            SpringMappingResolver.Result resolved = mappings.resolveExpressions(pathArguments);
            if (resolved.status() == SpringMappingResolver.Status.UNRESOLVED
                    || pathArguments.size() != matcher.getArguments().size()) {
                unsupported = true;
                continue;
            }
            for (String path : resolved.paths()) {
                if (path.contains("*") || path.contains("?") || !path.startsWith("/")) {
                    unsupported = true;
                    continue;
                }
                rules.merge(path, access, AuthorizationResolver::merge);
                if (rules.get(path) == Access.UNRESOLVED) unsupported = true;
            }
        }
        return new AuthorizationResolver(Map.copyOf(rules), defaultRule, present, unsupported);
    }

    Access accessFor(String route) {
        return exactRules.getOrDefault(route, defaultRule);
    }

    boolean hasConfiguration() { return configurationPresent; }
    boolean hasUnsupportedConfiguration() { return unsupported; }

    private static Access merge(Access first, Access second) {
        return first == second ? first : Access.UNRESOLVED;
    }
}
