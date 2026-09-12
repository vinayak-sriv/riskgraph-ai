package ai.riskgraph.analyzer.extract;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
public class SensitivityPolicy {
    private static final Set<String> ALLOWED = Set.of("LOW", "MODERATE", "MEDIUM", "HIGH", "CRITICAL");
    private final String version;
    private final String defaultSensitivity;
    private final Map<String, Rule> rules;
    private final String source;

    public SensitivityPolicy(@Value("${riskgraph.analyzer.sensitivity-policy:}") String configuredPath) {
        PolicyDocument document;
        String loadedSource;
        try {
            if (configuredPath == null || configuredPath.isBlank()) {
                try (InputStream stream = SensitivityPolicy.class.getResourceAsStream("/sensitivity-policy.yml")) {
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
        this.rules = document.rules();
        this.source = loadedSource;
    }

    public Classification classify(String resource) {
        Rule exact = rules.get(resource.toLowerCase(Locale.ROOT));
        if (exact != null) {
            return new Classification(exact.sensitivity(), source, exact.name() + "@" + version);
        }
        return new Classification(defaultSensitivity, source, "default@" + version);
    }

    public String fingerprint() {
        StringBuilder canonical = new StringBuilder(version).append('\n')
                .append(defaultSensitivity).append('\n');
        rules.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> canonical.append(entry.getKey()).append('=')
                        .append(entry.getValue().sensitivity()).append('\n'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    @SuppressWarnings("unchecked")
    private PolicyDocument parse(InputStream stream) {
        Object loaded = new Yaml().load(stream);
        if (!(loaded instanceof Map<?, ?> root)) {
            throw new IllegalArgumentException("Sensitivity policy must be a YAML mapping");
        }
        String loadedVersion = requiredString(root, "version");
        String loadedDefault = requiredString(root, "default_sensitivity");
        Object resourcesValue = root.get("resources");
        if (!(resourcesValue instanceof Map<?, ?> resources)) {
            throw new IllegalArgumentException("Sensitivity policy resources must be a mapping");
        }
        Map<String, Rule> loadedRules = new LinkedHashMap<>();
        resources.forEach((key, value) -> {
            String name = String.valueOf(key);
            loadedRules.put(name.toLowerCase(Locale.ROOT), new Rule(name, normalizeSensitivity(String.valueOf(value))));
        });
        return new PolicyDocument(loadedVersion, loadedDefault, Map.copyOf(loadedRules));
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

    private record Rule(String name, String sensitivity) {
    }

    private record PolicyDocument(String version, String defaultSensitivity, Map<String, Rule> rules) {
    }

    public record Classification(String sensitivity, String source, String matchedRule) {
    }
}
