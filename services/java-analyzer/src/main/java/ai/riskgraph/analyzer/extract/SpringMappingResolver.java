package ai.riskgraph.analyzer.extract;

import java.util.ArrayList;
import java.util.List;

import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtField;

/** Resolves only mapping values that Spoon can prove are compile-time strings. */
final class SpringMappingResolver {
    enum Status { RESOLVED, EXPLICITLY_EMPTY, UNRESOLVED }

    record Result(Status status, List<String> paths, String expression) {
        static Result unresolved(CtExpression<?> expression) {
            return new Result(Status.UNRESOLVED, List.of(), expression == null ? "<missing>" : expression.toString());
        }
    }

    Result resolve(CtAnnotation<?> mapping) {
        if (mapping == null) {
            return new Result(Status.EXPLICITLY_EMPTY, List.of(""), "<absent>");
        }
        CtExpression<?> expression = mapping.getValues().get("path");
        if (expression == null) {
            expression = mapping.getValues().get("value");
        }
        if (expression == null) {
            return new Result(Status.EXPLICITLY_EMPTY, List.of(""), "<empty>");
        }
        List<String> values = new ArrayList<>();
        if (!collect(expression, values)) {
            return Result.unresolved(expression);
        }
        if (values.isEmpty() || values.stream().allMatch(String::isEmpty)) {
            return new Result(Status.EXPLICITLY_EMPTY, List.of(""), expression.toString());
        }
        return new Result(Status.RESOLVED, List.copyOf(values), expression.toString());
    }

    Result resolveExpressions(List<CtExpression<?>> expressions) {
        if (expressions.isEmpty()) {
            return new Result(Status.EXPLICITLY_EMPTY, List.of(""), "<empty>");
        }
        List<String> values = new ArrayList<>();
        for (CtExpression<?> expression : expressions) {
            if (!collect(expression, values)) {
                return Result.unresolved(expression);
            }
        }
        if (values.stream().allMatch(String::isEmpty)) {
            return new Result(Status.EXPLICITLY_EMPTY, List.of(""), expressions.toString());
        }
        return new Result(Status.RESOLVED, List.copyOf(values), expressions.toString());
    }

    private boolean collect(CtExpression<?> expression, List<String> values) {
        if (expression instanceof CtLiteral<?> literal && literal.getValue() instanceof String value) {
            values.add(value);
            return true;
        }
        if (expression instanceof CtNewArray<?> array) {
            for (CtExpression<?> element : array.getElements()) {
                if (!collect(element, values)) {
                    return false;
                }
            }
            return true;
        }
        if (expression instanceof CtFieldRead<?> fieldRead) {
            CtField<?> field = fieldRead.getVariable().getFieldDeclaration();
            return field != null && field.getDefaultExpression() != null
                    && collect(field.getDefaultExpression(), values);
        }
        try {
            CtExpression<?> evaluated = expression.partiallyEvaluate();
            return evaluated != expression && collect(evaluated, values);
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
