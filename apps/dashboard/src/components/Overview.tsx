import {
  ArrowDownRight,
  ArrowRight,
  ArrowUpRight,
  CheckCircle2,
  CircleDot,
  Fingerprint,
  GitCompareArrows,
  Route,
  ShieldAlert,
  ShieldCheck,
} from "lucide-react";
import type { AnalysisResult } from "../types";
import { evidenceCertainty, formatLabel, riskTone } from "../view-model";
import {
  CertaintyBadge,
  Metric,
  PipelineStep,
  RiskDeltaTrack,
  VerdictBadge,
} from "./ui";

export function Overview({
  analysis,
  loading,
  canRun,
  onValidate,
}: {
  analysis: AnalysisResult;
  loading: boolean;
  canRun: boolean;
  onValidate: () => void;
}) {
  const risk = analysis.risk_result;
  const verdict = analysis.final_verdict ?? analysis.verdict;
  const paths = analysis.graph_delta.new_paths.length;
  const validation = analysis.validation_status ?? "NOT_RUN";
  const certainty = evidenceCertainty(analysis);
  const headline =
    verdict === "ALLOW"
      ? "No policy review required"
      : verdict === "BLOCK"
        ? "This change is blocked by policy"
        : "This change needs a closer look";
  return (
    <section
      id="overview"
      className="section-block"
      aria-labelledby="overview-title"
      tabIndex={-1}
    >
      <div className="section-title-row">
        <div className="section-label">
          <span className="section-number">01</span>
          <h2 id="overview-title">Decision summary</h2>
        </div>
        <span className="context-tag">
          <Fingerprint size={14} /> Evidence-based assessment
        </span>
      </div>

      <div className={`decision-banner decision-${verdict.toLowerCase()}`}>
        <div className="decision-symbol">
          {verdict === "ALLOW" ? (
            <ShieldCheck size={26} />
          ) : (
            <ShieldAlert size={26} />
          )}
        </div>
        <div className="decision-signal">
          <VerdictBadge verdict={verdict} />
          <div className="after-risk">
            <span>After risk</span>
            <strong>{risk.risk_after}</strong>
            <small>/100 · {risk.category_after}</small>
          </div>
          <span
            className={`hero-delta ${risk.risk_delta > 0 ? "increased" : risk.risk_delta < 0 ? "decreased" : ""}`}
          >
            {risk.risk_delta > 0 ? "+" : ""}
            {risk.risk_delta} delta
          </span>
        </div>
        <div className="decision-copy">
          <div className="decision-heading">
            <h3>{headline}</h3>
            <CertaintyBadge certainty={certainty} />
          </div>
          <p>
            {risk.evidence[0] ??
              "Inspect the analysis evidence and policy reasons below."}
          </p>
          <span>
            {validation === "CONFIRMED"
              ? "Confirmed in the registered Docker sandbox"
              : "Static assessment"}
            {validation === "NOT_RUN"
              ? " · Runtime validation has not run"
              : validation !== "CONFIRMED"
                ? ` · Validation ${formatLabel(validation).toLowerCase()}`
                : ""}
          </span>
        </div>
        <a
          href={paths ? "#analysis-graph" : "#analysis-evidence"}
          className="decision-action"
        >
          {paths ? "Investigate path" : "Review evidence"}
          <ArrowRight size={16} />
        </a>
      </div>

      <div className="metric-grid metric-grid-supporting">
        <Metric
          icon={<ShieldCheck />}
          label="Risk before"
          value={risk.risk_before}
          detail={risk.category_before}
          tone={riskTone(risk.category_before)}
          scale={100}
        />
        <Metric
          icon={<GitCompareArrows />}
          label="Risk delta"
          value={`${risk.risk_delta > 0 ? "+" : ""}${risk.risk_delta}`}
          detail={
            risk.risk_delta === 0
              ? "No change in risk"
              : risk.risk_delta > 0
                ? "Risk increased"
                : "Risk reduced"
          }
          tone={risk.risk_delta > 0 ? "warning" : "safe"}
        />
        <Metric
          icon={<Route />}
          label="New attack paths"
          value={paths.toString().padStart(2, "0")}
          detail={paths ? "Anonymous → sensitive" : "No new sensitive access"}
          tone={paths ? "danger" : "safe"}
        />
      </div>

      <div className="overview-grid">
        <div className="risk-comparison surface">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">Change impact</p>
              <h3>Risk comparison</h3>
            </div>
            <span
              className={`delta-label ${risk.risk_delta > 0 ? "increased" : ""}`}
            >
              {risk.risk_delta > 0 ? (
                <ArrowUpRight size={15} />
              ) : risk.risk_delta < 0 ? (
                <ArrowDownRight size={15} />
              ) : (
                <GitCompareArrows size={15} />
              )}
              {risk.risk_delta > 0 ? "+" : ""}
              {risk.risk_delta} points
            </span>
          </div>
          <div className="risk-chart">
            <RiskDeltaTrack
              before={risk.risk_before}
              after={risk.risk_after}
              categoryBefore={risk.category_before}
              categoryAfter={risk.category_after}
            />
          </div>
          <div className="risk-range-legend">
            <span>
              LOW <code>0–20</code>
            </span>
            <span>
              MODERATE <code>21–40</code>
            </span>
            <span>
              MEDIUM <code>41–60</code>
            </span>
            <span>
              HIGH <code>61–80</code>
            </span>
            <span>
              CRITICAL <code>81–100</code>
            </span>
          </div>
          <div className="comparison-note">
            <Fingerprint size={15} />
            <span>Six weighted factors. Fully traceable.</span>
            <a href="#analysis-evidence">
              View scoring <ArrowUpRight size={14} />
            </a>
          </div>
        </div>
        <div className="pipeline surface">
          <div className="surface-heading">
            <div>
              <p className="eyebrow">Analysis pipeline</p>
              <h3>Evidence to decision</h3>
            </div>
            <span className="runtime-badge">
              {analysis.provenance ? "Source scan" : "Demo fixture"}
            </span>
          </div>
          <PipelineStep
            icon={<CheckCircle2 />}
            label="Graph construction"
            detail="Before + after"
            state="complete"
          />
          <PipelineStep
            icon={<CheckCircle2 />}
            label="BFS reachability"
            detail={`${paths} new ${paths === 1 ? "path" : "paths"}`}
            state="complete"
          />
          <PipelineStep
            icon={<CheckCircle2 />}
            label="Risk and policy"
            detail={`${risk.risk_after}/100 · ${analysis.pre_validation_verdict ?? analysis.verdict}`}
            state="complete"
          />
          <PipelineStep
            icon={
              analysis.ai?.status === "AVAILABLE" ? (
                <CheckCircle2 />
              ) : (
                <CircleDot />
              )
            }
            label="AI explanation"
            detail={formatLabel(analysis.ai?.status ?? "NOT_RUN")}
            state={analysis.ai?.status === "AVAILABLE" ? "complete" : "pending"}
          />
          <PipelineStep
            icon={
              validation === "CONFIRMED" || validation === "REJECTED" ? (
                <CheckCircle2 />
              ) : (
                <CircleDot />
              )
            }
            label="Docker validation"
            detail={formatLabel(validation)}
            state={
              validation === "CONFIRMED" || validation === "REJECTED"
                ? "complete"
                : "pending"
            }
          />
          <p className="pipeline-verdicts">
            Preliminary{" "}
            <strong>
              {analysis.pre_validation_verdict ?? analysis.verdict}
            </strong>
            <ArrowRight size={13} /> Final <strong>{verdict}</strong>
          </p>
          {analysis.scan_id && (
            <button
              className="secondary-button"
              disabled={loading || !canRun}
              onClick={onValidate}
            >
              Validate registered Docker sandbox
            </button>
          )}
          {analysis.validation && (
            <p role="status">
              {analysis.validation.status}: {analysis.validation.reason_code}
            </p>
          )}
        </div>
      </div>
    </section>
  );
}
