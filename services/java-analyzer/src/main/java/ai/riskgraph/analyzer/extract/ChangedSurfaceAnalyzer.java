package ai.riskgraph.analyzer.extract;

import static ai.riskgraph.analyzer.model.AnalysisModels.ChangedRange;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;

final class ChangedSurfaceAnalyzer {
    private final Set<String> mappingAnnotations;

    ChangedSurfaceAnalyzer(Set<String> mappingAnnotations) {
        this.mappingAnnotations = Set.copyOf(mappingAnnotations);
    }

    boolean classSecurityChanged(
            Path snapshot, CtType<?> type, Map<String, List<ChangedRange>> changedRanges) {
        boolean annotationChanged = type.getAnnotations().stream()
                .filter(annotation -> mappingAnnotations.contains(annotation.getAnnotationType().getSimpleName())
                        || AnnotationAuthorizationExtractor.ANNOTATION_NAMES.contains(
                                annotation.getAnnotationType().getSimpleName())
                        || "RestController".equals(annotation.getAnnotationType().getSimpleName())
                        || "Controller".equals(annotation.getAnnotationType().getSimpleName()))
                .anyMatch(annotation -> intersects(snapshot, annotation, changedRanges));
        if (annotationChanged || !type.getPosition().isValidPosition()) return annotationChanged;
        List<ChangedRange> ranges = rangesFor(snapshot, type, changedRanges);
        return intersectsLines(type.getPosition().getLine(), type.getPosition().getLine(), ranges);
    }

    boolean methodSurfaceChanged(
            Path snapshot, CtMethod<?> method, Map<String, List<ChangedRange>> changedRanges) {
        return intersects(snapshot, method, changedRanges)
                || method.getAnnotations().stream()
                .anyMatch(annotation -> intersects(snapshot, annotation, changedRanges));
    }

    boolean intersects(Path snapshot, CtElement element, Map<String, List<ChangedRange>> changedRanges) {
        if (!element.getPosition().isValidPosition()) return false;
        return intersectsLines(element.getPosition().getLine(), element.getPosition().getEndLine(),
                rangesFor(snapshot, element, changedRanges));
    }

    private List<ChangedRange> rangesFor(
            Path snapshot, CtElement element, Map<String, List<ChangedRange>> changedRanges) {
        if (!element.getPosition().isValidPosition()) return List.of();
        String path = snapshot.relativize(element.getPosition().getFile().toPath()
                        .toAbsolutePath().normalize()).toString().replace('\\', '/');
        return changedRanges.getOrDefault(path, List.of());
    }

    private boolean intersectsLines(int startLine, int endLine, List<ChangedRange> ranges) {
        return ranges.stream().anyMatch(range -> startLine <= range.end_line() && range.start_line() <= endLine);
    }
}
