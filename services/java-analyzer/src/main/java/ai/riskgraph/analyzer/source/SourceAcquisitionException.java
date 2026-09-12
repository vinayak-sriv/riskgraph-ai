package ai.riskgraph.analyzer.source;

public class SourceAcquisitionException extends RuntimeException {
    private final String code;

    public SourceAcquisitionException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
