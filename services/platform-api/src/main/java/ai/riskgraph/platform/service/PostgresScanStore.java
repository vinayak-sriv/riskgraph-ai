package ai.riskgraph.platform.service;

import java.util.HashMap;
import java.util.function.UnaryOperator;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component @Profile("!local")
public class PostgresScanStore implements ScanStore {
    private final JdbcTemplate db;
    private final ObjectMapper mapper;
    public PostgresScanStore(JdbcTemplate db, ObjectMapper mapper) { this.db=db; this.mapper=mapper; }

    @Transactional
    public void save(JsonNode result) {
        String external = result.path("scan_id").asString();
        // Serializes same-result writes without locking unrelated scans.
        db.queryForList("SELECT pg_advisory_xact_lock(hashtext(?))", external);
        var existing = db.queryForList("SELECT id FROM scans WHERE external_id=?", Long.class, external);
        if (!existing.isEmpty()) {
            db.update("UPDATE scans SET result_json=?::jsonb, status=?, final_verdict=?, validation_status=?, updated_at=now() WHERE id=?",
                result.toString(), result.path("final_verdict").asString(),result.path("final_verdict").asString(),result.path("validation_status").asString(),existing.getFirst());
            persistFindings(existing.getFirst(), result);
            return;
        }
        JsonNode provenance=result.path("provenance");
        String repository=provenance.path("repository_identity").asString();
        Long project=db.queryForObject("""
            INSERT INTO projects(repository,framework) VALUES (?, 'Spring Boot')
            ON CONFLICT (repository) DO UPDATE SET repository=excluded.repository
            RETURNING id
            """,Long.class,repository);
        Long pr=db.queryForObject("INSERT INTO pull_requests(project_id,old_commit,new_commit) VALUES (?,?,?) RETURNING id",Long.class,
            project,provenance.path("old_commit").asString(),provenance.path("new_commit").asString());
        Long scan=db.queryForObject("INSERT INTO scans(pull_request_id,status,risk_before,risk_after,external_id,result_json,pre_validation_verdict,final_verdict,validation_status) VALUES (?,?,?,?,?,?::jsonb,?,?,?) RETURNING id",Long.class,
            pr,result.path("final_verdict").asString(),result.at("/risk_result/risk_before").asInt(),result.at("/risk_result/risk_after").asInt(),external,result.toString(),
            result.path("pre_validation_verdict").asString(),result.path("final_verdict").asString(),result.path("validation_status").asString());
        for (String revision : new String[]{"before","after"}) persistGraph(scan, revision, result.at("/graph_delta/"+revision));
        persistFindings(scan, result);
    }

    private void persistGraph(Long scan, String revision, JsonNode graph) {
        var nodeBatch = new java.util.ArrayList<Object[]>();
        for (JsonNode node : graph.path("nodes")) nodeBatch.add(new Object[]{scan,
                node.path("node_type").asString(), node.path("name").asString(), revision,
                node.path("id").asString()});
        db.batchUpdate("INSERT INTO graph_nodes(scan_id,node_type,name,revision,stable_id) VALUES (?,?,?,?,?)", nodeBatch);
        var ids = new HashMap<String,Long>();
        for (var row : db.queryForList(
                "SELECT stable_id,id FROM graph_nodes WHERE scan_id=? AND revision=?", scan, revision)) {
            ids.put((String) row.get("stable_id"), ((Number) row.get("id")).longValue());
        }
        var edgeBatch = new java.util.ArrayList<Object[]>();
        for (JsonNode edge : graph.path("edges")) edgeBatch.add(new Object[]{
                ids.get(edge.path("source_id").asString()), ids.get(edge.path("target_id").asString()),
                edge.path("relationship").asString()});
        db.batchUpdate("INSERT INTO graph_edges(source_id,target_id,relationship) VALUES (?,?,?)", edgeBatch);
    }

    private void persistFindings(Long scan, JsonNode result) {
        var findings = new java.util.ArrayList<JsonNode>();
        var findingBatch = new java.util.ArrayList<Object[]>();
        for (JsonNode finding : result.path("findings")) {
            findings.add(finding);
            String externalId = finding.path("finding_id").asString();
            findingBatch.add(new Object[]{scan, "Anonymous sensitive path", finding.path("severity").asString(),
                    finding.path("dependency_path").toString(), externalId});
        }
        if (findings.isEmpty()) return;
        db.batchUpdate("""
                INSERT INTO findings(scan_id,type,severity,description,external_id) VALUES (?,?,?,?,?)
                ON CONFLICT (external_id) WHERE external_id IS NOT NULL DO UPDATE SET
                    severity=excluded.severity, description=excluded.description
                """, findingBatch);

        String placeholders = String.join(",", java.util.Collections.nCopies(findings.size(), "?"));
        Object[] externalIds = findings.stream().map(finding -> finding.path("finding_id").asString()).toArray();
        var ids = new HashMap<String, Long>();
        for (var row : db.queryForList(
                "SELECT external_id,id FROM findings WHERE external_id IN (" + placeholders + ")", externalIds)) {
            ids.put((String) row.get("external_id"), ((Number) row.get("id")).longValue());
        }

        var aiBatch = new java.util.ArrayList<Object[]>();
        var validationBatch = new java.util.ArrayList<Object[]>();
        for (JsonNode finding : findings) {
            Long id = ids.get(finding.path("finding_id").asString());
            if (id == null) throw new IllegalStateException("Persisted finding identity was not returned");
            if (finding.has("ai")) aiBatch.add(new Object[]{id,
                    finding.at("/ai/analysis/hypothesis").asString(), finding.path("ai").toString()});
            JsonNode validation = finding.path("validation");
            validationBatch.add(new Object[]{id, "HTTP authorization test", "Anonymous access denied",
                    validation.toString(), validation.path("status").asString("NOT_RUN")});
        }
        if (!aiBatch.isEmpty()) db.batchUpdate("""
                INSERT INTO ai_analysis(finding_id,hypothesis,explanation) VALUES (?,?,?)
                ON CONFLICT (finding_id) DO UPDATE SET hypothesis=excluded.hypothesis,
                    explanation=excluded.explanation
                """, aiBatch);
        db.batchUpdate("""
                INSERT INTO validation_tests(finding_id,test,expected_result,actual_result,status) VALUES (?,?,?,?,?)
                ON CONFLICT (finding_id) DO UPDATE SET actual_result=excluded.actual_result,
                    status=excluded.status, updated_at=now()
                """, validationBatch);
    }

    public JsonNode get(String id) {
        var values=db.queryForList("SELECT result_json::text FROM scans WHERE external_id=?",String.class,id);
        return values.isEmpty()?null:mapper.readTree(values.getFirst());
    }

    @Transactional
    public JsonNode update(String id, UnaryOperator<ObjectNode> mutation) {
        db.queryForList("SELECT pg_advisory_xact_lock(hashtext(?))", id);
        var values=db.queryForList(
                "SELECT result_json::text FROM scans WHERE external_id=? FOR UPDATE",String.class,id);
        if (values.isEmpty()) return null;
        ObjectNode updated=mutation.apply((ObjectNode) mapper.readTree(values.getFirst()));
        Long scan=db.queryForObject("SELECT id FROM scans WHERE external_id=?",Long.class,id);
        db.update("UPDATE scans SET result_json=?::jsonb, status=?, final_verdict=?, validation_status=?, updated_at=now() WHERE id=?",
                updated.toString(),updated.path("final_verdict").asString(),
                updated.path("final_verdict").asString(),updated.path("validation_status").asString(),scan);
        persistFindings(scan, updated);
        return updated.deepCopy();
    }
}
