package ai.riskgraph.platform.service;

public final class DecisionPolicy {
    private DecisionPolicy() {}
    public static String finalVerdict(String preliminary, String validation) {
        if (!java.util.Set.of("ALLOW", "REVIEW", "BLOCK").contains(preliminary)) return "REVIEW";
        // A protected response only rejects this HTTP hypothesis; static evidence remains.
        if (preliminary.equals("BLOCK")) return "BLOCK";
        if (validation.equals("CONFIRMED")) return "BLOCK";
        if (validation.equals("ERROR") || validation.equals("INCONCLUSIVE")) return "REVIEW";
        if (!java.util.Set.of("NOT_RUN", "REJECTED").contains(validation)) return "REVIEW";
        return preliminary;
    }
}
