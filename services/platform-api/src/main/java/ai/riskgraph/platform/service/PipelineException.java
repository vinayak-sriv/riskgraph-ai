package ai.riskgraph.platform.service;

public class PipelineException extends RuntimeException {
    public final String code;
    public final int status;
    public PipelineException(String code, int status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }
}
