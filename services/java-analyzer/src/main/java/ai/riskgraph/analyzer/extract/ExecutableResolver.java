package ai.riskgraph.analyzer.extract;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtTypeReference;

/** Uses Spoon type/executable metadata and refuses ambiguous overloads. */
final class ExecutableResolver {
    private final Map<String, CtType<?>> qualified = new HashMap<>();
    private final Map<String, List<CtType<?>>> simple = new HashMap<>();

    ExecutableResolver(Collection<CtType<?>> types) {
        for (CtType<?> type : types) {
            qualified.put(type.getQualifiedName(), type);
            simple.computeIfAbsent(type.getSimpleName(), ignored -> new ArrayList<>()).add(type);
        }
    }

    TypeResult resolveType(CtInvocation<?> invocation) {
        CtTypeReference<?> reference = invocation.getTarget() == null ? null : invocation.getTarget().getType();
        if (reference == null) {
            reference = invocation.getExecutable().getDeclaringType();
        }
        if (reference == null) {
            return new TypeResult(null, true);
        }
        CtType<?> exact = qualified.get(reference.getQualifiedName());
        if (exact != null) {
            return new TypeResult(exact, false);
        }
        try {
            CtType<?> declaration = reference.getTypeDeclaration();
            if (declaration != null && qualified.containsKey(declaration.getQualifiedName())) {
                return new TypeResult(qualified.get(declaration.getQualifiedName()), false);
            }
        } catch (RuntimeException ignored) {
            // Continue to the deterministic unique-simple-name fallback.
        }
        List<CtType<?>> candidates = simple.getOrDefault(reference.getSimpleName(), List.of());
        return candidates.size() == 1
                ? new TypeResult(candidates.getFirst(), false)
                : new TypeResult(null, !candidates.isEmpty());
    }

    MethodResult resolveMethod(CtInvocation<?> invocation, CtType<?> target) {
        List<CtMethod<?>> named = target.getMethodsByName(invocation.getExecutable().getSimpleName());
        CtExecutable<?> declaration = invocation.getExecutable().getDeclaration();
        if (declaration instanceof CtMethod<?> method
                && named.size() <= 1
                && method.getDeclaringType() != null
                && method.getDeclaringType().getQualifiedName().equals(target.getQualifiedName())) {
            return new MethodResult(method, false);
        }

        List<CtMethod<?>> compatible = named.stream()
                .filter(method -> method.getParameters().size() == invocation.getArguments().size())
                .filter(method -> compatible(method, invocation.getArguments()))
                .toList();
        return compatible.size() == 1
                ? new MethodResult(compatible.getFirst(), false)
                : new MethodResult(null, compatible.size() > 1 || named.size() > 1);
    }

    private boolean compatible(CtMethod<?> method, List<CtExpression<?>> arguments) {
        for (int index = 0; index < arguments.size(); index++) {
            CtTypeReference<?> parameter = method.getParameters().get(index).getType();
            CtExpression<?> argument = arguments.get(index);
            if (argument instanceof CtLiteral<?> literal && literal.getValue() == null) {
                if (parameter.isPrimitive()) return false;
                continue;
            }
            CtTypeReference<?> argumentType = argument.getType();
            if (argumentType == null) return false;
            if (sameType(argumentType, parameter)) continue;
            try {
                if (argumentType.isSubtypeOf(parameter)) continue;
            } catch (RuntimeException ignored) {
                // An unresolved no-classpath relationship is not evidence of assignability.
            }
            return false;
        }
        return true;
    }

    private boolean sameType(CtTypeReference<?> left, CtTypeReference<?> right) {
        if (left.getQualifiedName().equals(right.getQualifiedName())) return true;
        return boxed(left.getQualifiedName()).equals(boxed(right.getQualifiedName()));
    }

    private String boxed(String name) {
        return switch (name) {
            case "int" -> "java.lang.Integer";
            case "long" -> "java.lang.Long";
            case "boolean" -> "java.lang.Boolean";
            case "double" -> "java.lang.Double";
            case "float" -> "java.lang.Float";
            case "short" -> "java.lang.Short";
            case "byte" -> "java.lang.Byte";
            case "char" -> "java.lang.Character";
            default -> name;
        };
    }

    record TypeResult(CtType<?> type, boolean ambiguous) {}
    record MethodResult(CtMethod<?> method, boolean ambiguous) {}
}
