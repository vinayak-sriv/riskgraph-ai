package ai.riskgraph.analyzer.service;

public class AnalysisException extends RuntimeException {
    private final String code;

    public AnalysisException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
