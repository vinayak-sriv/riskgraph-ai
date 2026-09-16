package ai.riskgraph.analyzer.extract;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
public class SensitivityPolicy {
    private static final Set<String> ALLOWED = Set.of(
            "LOW", "MODERATE", "MEDIUM", "HIGH", "CRITICAL");
    private static final int MAX_PATTERN_LENGTH = 256;
    private static final int MAX_RESOURCE_LENGTH = 512;
    private final String version;
    private final String defaultSensitivity;
    private final String unmatchedRepositorySensitivity;
    private final List<Rule> rules;
    private final String source;

    public SensitivityPolicy(@Value("${riskgraph.analyzer.sensitivity-policy:}") String configuredPath) {
        PolicyDocument document;
        String loadedSource;
        try {
            if (configuredPath == null || configuredPath.isBlank()) {
                try (InputStream stream = SensitivityPolicy.class.getResourceAsStream(
                        "/sensitivity-policy.yml")) {
                    if (stream == null) {
                        throw new IllegalStateException("Bundled sensitivity policy is missing");
                    }
                    document = parse(stream);
                }
                loadedSource = "classpath:sensitivity-policy.yml";
            } else {
                Path path = Path.of(configuredPath).toRealPath();
                try (InputStream stream = Files.newInputStream(path)) {
                    document = parse(stream);
                }
                loadedSource = path.toString();
            }
        } catch (IOException error) {
            throw new IllegalArgumentException("Unable to load sensitivity policy", error);
        }
        this.version = document.version();
        this.defaultSensitivity = normalizeSensitivity(document.defaultSensitivity());
        this.unmatchedRepositorySensitivity = normalizeSensitivity(
                document.unmatchedRepositorySensitivity());
        this.rules = document.rules();
        this.source = loadedSource;
    }

    public Classification classify(String resource) {
        return classify(resource, false);
    }

    public Classification classify(String resource, boolean repositoryBacked) {
        if (resource == null || resource.isBlank() || resource.length() > MAX_RESOURCE_LENGTH) {
            throw new IllegalArgumentException("Resource name must contain 1 to 512 characters");
        }
        String normalized = resource.toLowerCase(Locale.ROOT);
        Rule match = rules.stream()
                .filter(rule -> rule.matches(normalized))
                .min(Rule.MATCH_PRECEDENCE)
                .orElse(null);
        if (match != null) {
            return new Classification(
                    match.sensitivity(), source, match.id() + "@" + version,
                    match.kind().externalName, false);
        }
        String fallback = repositoryBacked
                ? unmatchedRepositorySensitivity : defaultSensitivity;
        return new Classification(fallback, source, "default@" + version, "default", true);
    }

    public String fingerprint() {
        StringBuilder canonical = new StringBuilder(version).append('\n')
                .append(defaultSensitivity).append('\n')
                .append(unmatchedRepositorySensitivity).append('\n');
        rules.stream().sorted(Comparator.comparingInt(Rule::order))
                .forEach(rule -> canonical.append(rule.order()).append('=')
                        .append(rule.id()).append(':').append(rule.kind().externalName).append(':')
                        .append(rule.expression()).append(':').append(rule.sensitivity()).append('\n'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private PolicyDocument parse(InputStream stream) {
        Object loaded = new Yaml().load(stream);
        if (!(loaded instanceof Map<?, ?> root)) {
            throw new IllegalArgumentException("Sensitivity policy must be a YAML mapping");
        }
        String loadedVersion = requiredString(root, "version");
        String loadedDefault = normalizeSensitivity(requiredString(root, "default_sensitivity"));
        String repositoryDefault = root.containsKey("unmatched_repository_sensitivity")
                ? normalizeSensitivity(requiredString(root, "unmatched_repository_sensitivity"))
                : atLeastMedium(loadedDefault);
        List<Rule> loadedRules = new ArrayList<>();
        Set<String> matchIdentities = new HashSet<>();

        Object resourcesValue = root.get("resources");
        if (resourcesValue != null) {
            if (!(resourcesValue instanceof Map<?, ?> resources)) {
                throw new IllegalArgumentException("Sensitivity policy resources must be a mapping");
            }
            resources.forEach((key, value) -> addRule(
                    loadedRules, matchIdentities, String.valueOf(key),
                    normalizeSensitivity(String.valueOf(value)), MatchKind.EXACT,
                    String.valueOf(key)));
        }

        Object rulesValue = root.get("rules");
        if (rulesValue != null) {
            if (!(rulesValue instanceof List<?> configuredRules)) {
                throw new IllegalArgumentException("Sensitivity policy rules must be a list");
            }
            for (Object configuredRule : configuredRules) {
                if (!(configuredRule instanceof Map<?, ?> rule)) {
                    throw new IllegalArgumentException("Each sensitivity rule must be a mapping");
                }
                String id = requiredString(rule, "id");
                String sensitivity = normalizeSensitivity(requiredString(rule, "sensitivity"));
                int before = loadedRules.size();
                addExpressions(loadedRules, matchIdentities, rule, id, sensitivity,
                        "exact", MatchKind.EXACT);
                addExpressions(loadedRules, matchIdentities, rule, id, sensitivity,
                        "prefix", MatchKind.PREFIX);
                addExpressions(loadedRules, matchIdentities, rule, id, sensitivity,
                        "suffix", MatchKind.SUFFIX);
                addExpressions(loadedRules, matchIdentities, rule, id, sensitivity,
                        "regex", MatchKind.REGEX);
                if (before == loadedRules.size()) {
                    throw new IllegalArgumentException(
                            "Sensitivity rule " + id + " has no match expressions");
                }
            }
        }
        if (loadedRules.isEmpty()) {
            throw new IllegalArgumentException("Sensitivity policy must define at least one rule");
        }
        return new PolicyDocument(
                loadedVersion, loadedDefault, repositoryDefault, List.copyOf(loadedRules));
    }

    private void addExpressions(
            List<Rule> loadedRules,
            Set<String> matchIdentities,
            Map<?, ?> configuredRule,
            String id,
            String sensitivity,
            String field,
            MatchKind kind
    ) {
        Object value = configuredRule.get(field);
        if (value == null) {
            return;
        }
        if (value instanceof String expression) {
            addRule(loadedRules, matchIdentities, id, sensitivity, kind, expression);
            return;
        }
        if (!(value instanceof List<?> expressions)) {
            throw new IllegalArgumentException(field + " must be a string or list of strings");
        }
        for (Object expression : expressions) {
            addRule(loadedRules, matchIdentities, id, sensitivity, kind,
                    String.valueOf(expression));
        }
    }

    private void addRule(
            List<Rule> loadedRules,
            Set<String> matchIdentities,
            String id,
            String sensitivity,
            MatchKind kind,
            String expression
    ) {
        if (id.isBlank() || expression.isBlank() || expression.length() > MAX_PATTERN_LENGTH) {
            throw new IllegalArgumentException("Sensitivity rule ID/expression is invalid");
        }
        String normalized = expression.toLowerCase(Locale.ROOT);
        String identity = kind.externalName + ':' + normalized;
        if (!matchIdentities.add(identity)) {
            throw new IllegalArgumentException("Duplicate sensitivity match: " + identity);
        }
        Pattern compiled = null;
        if (kind == MatchKind.REGEX) {
            if (expression.contains("(?") || expression.matches(".*\\\\[1-9].*")) {
                throw new IllegalArgumentException("Regex lookarounds/backreferences are not supported");
            }
            try {
                compiled = Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            } catch (PatternSyntaxException error) {
                throw new IllegalArgumentException("Invalid sensitivity regex", error);
            }
        }
        loadedRules.add(new Rule(
                id, sensitivity, kind, expression, normalized, compiled, loadedRules.size()));
    }

    private String requiredString(Map<?, ?> root, String key) {
        Object value = root.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException("Sensitivity policy is missing " + key);
        }
        return String.valueOf(value);
    }

    private String normalizeSensitivity(String sensitivity) {
        String normalized = sensitivity.toUpperCase(Locale.ROOT);
        if (!ALLOWED.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported sensitivity: " + sensitivity);
        }
        return normalized;
    }

    private String atLeastMedium(String sensitivity) {
        return Set.of("LOW", "MODERATE").contains(sensitivity) ? "MEDIUM" : sensitivity;
    }

    private enum MatchKind {
        EXACT("exact", 0), PREFIX("prefix", 1), SUFFIX("suffix", 1), REGEX("regex", 2);

        private final String externalName;
        private final int precedence;

        MatchKind(String externalName, int precedence) {
            this.externalName = externalName;
            this.precedence = precedence;
        }
    }

    private record Rule(
            String id,
            String sensitivity,
            MatchKind kind,
            String expression,
            String normalizedExpression,
            Pattern compiled,
            int order
    ) {
        private static final Comparator<Rule> MATCH_PRECEDENCE = Comparator
                .comparingInt((Rule rule) -> rule.kind.precedence)
                .thenComparing(Comparator.comparingInt(Rule::literalLength).reversed())
                .thenComparingInt(Rule::order);

        boolean matches(String resource) {
            return switch (kind) {
                case EXACT -> resource.equals(normalizedExpression);
                case PREFIX -> resource.startsWith(normalizedExpression);
                case SUFFIX -> resource.endsWith(normalizedExpression);
                case REGEX -> compiled.matcher(resource).matches();
            };
        }

        int literalLength() {
            return kind == MatchKind.REGEX ? 0 : normalizedExpression.length();
        }
    }

    private record PolicyDocument(
            String version,
            String defaultSensitivity,
            String unmatchedRepositorySensitivity,
            List<Rule> rules
    ) {
    }

    public record Classification(
            String sensitivity,
            String source,
            String matchedRule,
            String matchKind,
            boolean defaulted
    ) {
    }
}
