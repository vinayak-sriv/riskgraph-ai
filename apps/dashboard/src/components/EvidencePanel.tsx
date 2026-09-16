import { useEffect, useRef } from "react";
import {
  ArrowUpRight,
  Bot,
  ChevronRight,
  Fingerprint,
  ShieldAlert,
  ShieldCheck,
  TriangleAlert,
} from "lucide-react";
import type { AnalysisResult } from "../types";
import {
  evidenceCertainty,
  formatLabel,
  nodesForEvidence,
  type EvidenceCertainty,
} from "../view-model";
import { CertaintyBadge, VerdictBadge } from "./ui";
import { ValidationEvidence } from "./ValidationEvidence";

export function EvidencePanel({
  analysis,
  focusedEvidence,
  onFocusNode,
  onNotify,
}: {
  analysis: AnalysisResult;
  focusedEvidence?: string | null;
  onFocusNode?: (nodeId: string) => void;
  onNotify?: (message: string) => void;
}) {
  const risk = analysis.risk_result;
  const verdict = analysis.final_verdict ?? analysis.verdict;
  const certainty = evidenceCertainty(analysis);
  const evidenceRefs = useRef(new Map<string, HTMLLIElement>());
  useEffect(() => {
    if (!focusedEvidence) return;
    const item = evidenceRefs.current.get(focusedEvidence);
    item?.scrollIntoView({
      block: "center",
      behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches
        ? "instant"
        : "smooth",
    });
    item?.focus({ preventScroll: true });
  }, [focusedEvidence]);

  return (
    <section
      id="analysis-evidence"
      className="section-block evidence-section"
      aria-labelledby="evidence-title"
    >
      <div className="section-title-row">
        <div className="section-label">
          <span className="section-number">03</span>
          <div>
            <h2 id="evidence-title">Evidence and scoring</h2>
            <p className="section-description">
              Traceable facts, confidence, and unresolved extraction limits.
            </p>
          </div>
        </div>
        <CertaintyBadge certainty={certainty} />
      </div>

      <div
        className="certainty-explainer surface"
        aria-label="Evidence certainty"
      >
        <div>
          <Fingerprint size={18} />
          <span>
            <strong>Analysis certainty</strong>
            <small>{certaintyCopy(certainty)}</small>
          </span>
        </div>
        <div className="certainty-scale">
          <span className={certainty === "CONFIRMED" ? "active" : ""}>
            Confirmed
          </span>
          <span className={certainty === "INFERRED" ? "active" : ""}>
            Inferred
          </span>
          <span className={certainty === "AMBIGUOUS" ? "active" : ""}>
            Ambiguous
          </span>
          <span className="certainty-unresolved">Unresolved diagnostics</span>
        </div>
      </div>

      {!!analysis.findings?.length && (
        <div className="surface finding-status-surface">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">Finding-level validation</p>
              <h3>Independent finding status</h3>
            </div>
            <span className="count-badge">{analysis.findings.length}</span>
          </div>
          <ul className="finding-status-list">
            {analysis.findings.map((finding) => (
              <li key={finding.finding_id}>
                <div>
                  <strong>
                    {finding.method} {finding.path}
                  </strong>
                  <span>
                    {finding.resource} · {finding.severity}
                  </span>
                  {finding.risk_result && (
                    <span>
                      Risk {finding.risk_result.risk_before} →{" "}
                      {finding.risk_result.risk_after} · delta{" "}
                      {finding.risk_result.risk_delta >= 0 ? "+" : ""}
                      {finding.risk_result.risk_delta}
                    </span>
                  )}
                  {!!finding.handler_refs?.length && (
                    <span>
                      {finding.handler_refs.length}{" "}
                      {finding.handler_refs.length === 1
                        ? "handler"
                        : "handlers"}
                    </span>
                  )}
                </div>
                <div className="finding-validation-badges">
                  <span
                    className={`validation-status status-${finding.validation.status.toLowerCase()}`}
                  >
                    {formatLabel(finding.validation.status)}
                  </span>
                  <span className="runtime-badge">
                    {formatLabel(finding.validation_capability)}
                  </span>
                </div>
                <p>{finding.validation.reason_code}</p>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="evidence-grid">
        <div className="surface evidence-surface">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">Deterministic findings</p>
              <h3>Evidence</h3>
            </div>
            <span className="count-badge">{risk.evidence.length}</span>
          </div>
          <ol className="evidence-list">
            {risk.evidence.map((item, index) => {
              const matchingNodes = nodesForEvidence(analysis, item);
              return (
                <li
                  key={item}
                  ref={(element) => {
                    if (element) evidenceRefs.current.set(item, element);
                    else evidenceRefs.current.delete(item);
                  }}
                  tabIndex={-1}
                  className={focusedEvidence === item ? "is-focused" : ""}
                >
                  <span className="evidence-index">{index + 1}</span>
                  <div className="evidence-card-copy">
                    <div>
                      <CertaintyBadge certainty={certainty} />
                    </div>
                    <p>{item}</p>
                    {matchingNodes.length > 0 && (
                      <div
                        className="evidence-node-links"
                        aria-label="Related graph nodes"
                      >
                        {matchingNodes.slice(0, 3).map((node) => (
                          <button
                            type="button"
                            key={node.id}
                            onClick={() => onFocusNode?.(node.id)}
                          >
                            {node.name}
                            <ArrowUpRight size={13} />
                          </button>
                        ))}
                      </div>
                    )}
                  </div>
                </li>
              );
            })}
          </ol>
          {risk.evidence.length === 0 && (
            <div className="empty-surface">
              <ShieldCheck size={18} />
              <strong>No new risk evidence</strong>
              <p>
                The deterministic comparison did not report a newly reachable
                sensitive path.
              </p>
            </div>
          )}
        </div>

        <div className="surface factors-surface">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">Transparent formula</p>
              <h3>Risk components</h3>
            </div>
            <span className="formula-total">Total {risk.risk_after}</span>
          </div>
          <div
            className="factor-table-wrap"
            role="region"
            aria-label="Risk component comparison"
            tabIndex={0}
          >
            <table className="factor-table">
              <thead>
                <tr>
                  <th scope="col">Component</th>
                  <th scope="col">Weight</th>
                  <th scope="col">Before</th>
                  <th scope="col">After</th>
                  <th scope="col">Contribution</th>
                </tr>
              </thead>
              <tbody>
                {Object.entries(risk.components).map(([name, component]) => {
                  const before = risk.components_before?.[name];
                  return (
                    <tr key={name}>
                      <th scope="row">{formatLabel(name)}</th>
                      <td>{Math.round(component.weight * 100)}%</td>
                      <td>{before?.score ?? "—"}</td>
                      <td>{component.score}</td>
                      <td>
                        <strong>{component.weighted_score}</strong>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>

        <div
          className={`surface policy-surface policy-${verdict.toLowerCase()}`}
        >
          <div className="policy-icon">
            {verdict === "ALLOW" ? (
              <ShieldCheck size={22} />
            ) : (
              <ShieldAlert size={22} />
            )}
          </div>
          <div>
            <p className="eyebrow">Deterministic decision</p>
            <h3>Decision: {verdict}</h3>
            <p>
              {(analysis.reason_codes ?? [verdict])
                .map(formatLabel)
                .join(" · ")}
              . Risk is {risk.risk_after}/100 ({risk.category_after}).
            </p>
          </div>
          <VerdictBadge verdict={verdict} />
        </div>
        {analysis.validation && (
          <ValidationEvidence
            validation={analysis.validation}
            onNotify={onNotify}
          />
        )}
      </div>

      {(analysis.provenance || analysis.diagnostics?.length) && (
        <details
          className="surface technical-details"
          open={Boolean(analysis.quality?.incomplete)}
        >
          <summary>
            <span>
              <Fingerprint size={17} />
              <strong>Technical details</strong>
            </span>
            <span>
              {analysis.diagnostics?.length ?? 0} diagnostics{" "}
              <ChevronRight size={15} />
            </span>
          </summary>
          <div className="technical-details-body">
            {analysis.provenance && <SourceProvenance analysis={analysis} />}
            {!!analysis.diagnostics?.length && (
              <Diagnostics analysis={analysis} />
            )}
          </div>
        </details>
      )}

      {analysis.ai && (
        <aside
          className="surface ai-panel"
          aria-labelledby="ai-explanation-title"
        >
          <div className="evidence-detail-heading">
            <div>
              <p className="eyebrow">
                <Bot size={14} /> Non-authoritative interpretation
              </p>
              <h3 id="ai-explanation-title">AI explanation · unconfirmed</h3>
            </div>
            <span className="runtime-badge">
              {formatLabel(analysis.ai.status)}
            </span>
          </div>
          <div className="ai-boundary">
            <TriangleAlert size={16} />
            <p>
              This hypothesis is separate from deterministic evidence and does
              not set the verdict or risk score.
            </p>
          </div>
          <p>{analysis.ai.analysis.finding}</p>
          <dl className="ai-details">
            <div>
              <dt>Hypothesis</dt>
              <dd>{analysis.ai.analysis.hypothesis}</dd>
            </div>
            <div>
              <dt>Proposed HTTP test</dt>
              <dd>
                <code>{analysis.ai.analysis.recommended_test}</code>
              </dd>
            </div>
            <div>
              <dt>AI confidence</dt>
              <dd>{analysis.ai.analysis.confidence}</dd>
            </div>
          </dl>
        </aside>
      )}
    </section>
  );
}

function SourceProvenance({ analysis }: { analysis: AnalysisResult }) {
  const provenance = analysis.provenance!;
  return (
    <section id="source-provenance" className="provenance technical-section">
      <div className="evidence-detail-heading">
        <h3>Source provenance</h3>
        <Fingerprint size={18} />
      </div>
      <dl className="provenance-grid">
        <div>
          <dt>Repository identity</dt>
          <dd>{provenance.repository_identity}</dd>
        </div>
        <div>
          <dt>Analyzer version</dt>
          <dd>{analysis.analyzer_version ?? "Unknown"}</dd>
        </div>
        <div>
          <dt>Before commit</dt>
          <dd>
            <code>{provenance.old_commit}</code>
          </dd>
        </div>
        <div>
          <dt>After commit</dt>
          <dd>
            <code>{provenance.new_commit}</code>
          </dd>
        </div>
        {analysis.scan_id && (
          <div className="scan-identity">
            <dt>Saved scan ID</dt>
            <dd>
              <code>{analysis.scan_id}</code>
            </dd>
          </div>
        )}
      </dl>
      {Object.entries(analysis.source_evidence ?? {}).map(
        ([revision, rows]) => (
          <div
            className="source-table-wrap"
            key={revision}
            role="region"
            aria-label={`${formatLabel(revision)} source evidence`}
            tabIndex={0}
          >
            <table className="source-table">
              <caption>
                {formatLabel(revision)} revision · {rows.length} extracted{" "}
                {rows.length === 1 ? "endpoint" : "endpoints"}
              </caption>
              <thead>
                <tr>
                  <th scope="col">Endpoint</th>
                  <th scope="col">Source location</th>
                  <th scope="col">Confidence</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row, index) => (
                  <tr key={index}>
                    <td>
                      <code>
                        {row.endpoint.method} {row.endpoint.endpoint}
                      </code>
                    </td>
                    <td>
                      <code>
                        {row.source_location.path}:
                        {row.source_location.start_line}–
                        {row.source_location.end_line}
                      </code>
                    </td>
                    <td>{row.extraction_confidence.overall}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            {rows.length === 0 && (
              <p className="empty-copy">
                No endpoint evidence for this revision.
              </p>
            )}
          </div>
        ),
      )}
    </section>
  );
}

function Diagnostics({ analysis }: { analysis: AnalysisResult }) {
  return (
    <section className="provenance technical-section">
      <div className="evidence-detail-heading">
        <h3>Extraction diagnostics</h3>
        <span className="count-badge">{analysis.diagnostics?.length ?? 0}</span>
      </div>
      <ul className="diagnostic-list">
        {analysis.diagnostics?.map((item, index) => (
          <li key={index}>
            <div>
              <CertaintyBadge certainty="UNRESOLVED" />
              <code>{item.code}</code>
              <span className="diagnostic-severity">{item.severity}</span>
            </div>
            <p>{item.message}</p>
            {item.path && <code className="diagnostic-path">{item.path}</code>}
          </li>
        ))}
      </ul>
    </section>
  );
}

function certaintyCopy(certainty: EvidenceCertainty) {
  if (certainty === "CONFIRMED")
    return "The registered Docker validation confirmed this result.";
  if (certainty === "AMBIGUOUS")
    return "Static findings are available, but extraction coverage is incomplete.";
  return "Deterministic structure supports the finding; runtime exploitability is not confirmed.";
}
