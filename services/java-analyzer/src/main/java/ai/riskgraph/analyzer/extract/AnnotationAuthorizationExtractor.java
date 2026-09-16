package ai.riskgraph.analyzer.extract;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;

final class AnnotationAuthorizationExtractor {
    static final List<String> ANNOTATION_NAMES = List.of(
            "PreAuthorize", "Secured", "RolesAllowed");
    private static final Pattern ROLE_PATTERN = Pattern.compile(
            "has(?:Any)?(?:Role|Authority)\\s*\\(\\s*['\\\"]([^'\\\"]+)['\\\"]");

    CtAnnotation<?> find(CtType<?> type) {
        return ANNOTATION_NAMES.stream().map(name -> annotation(type, name))
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    CtAnnotation<?> find(CtMethod<?> method) {
        return ANNOTATION_NAMES.stream().map(name -> annotation(method, name))
                .filter(Objects::nonNull).findFirst().orElse(null);
    }

    String requiredRole(CtAnnotation<?> authorization) {
        if (authorization == null) return null;
        CtExpression<?> value = authorization.getValues().get("value");
        List<String> values = expressionStrings(value);
        String annotationName = authorization.getAnnotationType().getSimpleName();
        if ("Secured".equals(annotationName) || "RolesAllowed".equals(annotationName)) {
            return values.stream().findFirst().map(role -> role.replaceFirst("^ROLE_", ""))
                    .orElse(null);
        }
        String expression = values.stream().findFirst()
                .orElse(value == null ? "" : value.toString());
        Matcher matcher = ROLE_PATTERN.matcher(expression);
        return matcher.find() ? matcher.group(1).replaceFirst("^ROLE_", "") : null;
    }

    boolean isSimple(CtAnnotation<?> authorization) {
        if (authorization == null) return true;
        List<String> values = expressionStrings(authorization.getValues().get("value"));
        if (values.size() != 1) return false;
        if (!"PreAuthorize".equals(authorization.getAnnotationType().getSimpleName())) return true;
        return values.getFirst().matches(
                "\\s*has(?:Role|Authority)\\s*\\(\\s*['\\\"][A-Za-z0-9_:-]+['\\\"]\\s*\\)\\s*");
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
            return array.getElements().stream().filter(CtLiteral.class::isInstance)
                    .map(CtLiteral.class::cast).map(CtLiteral::getValue)
                    .filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }
}
