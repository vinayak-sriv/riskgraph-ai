import { lazy, Suspense } from "react";
import {
  AlertTriangle,
  ArrowRight,
  FileSearch,
  Fingerprint,
  GitBranch,
  GitCompareArrows,
  RefreshCw,
  SlidersHorizontal,
} from "lucide-react";
import { Overview } from "./Overview";
import { EvidencePanel } from "./EvidencePanel";
import { CertaintyBadge } from "./ui";
import { evidenceCertainty, type Theme } from "../view-model";
import type { AnalysisResult } from "../types";
import type { Scenario } from "../useAnalysis";

const GraphComparison = lazy(() =>
  import("./GraphComparison").then((module) => ({
    default: module.GraphComparison,
  })),
);

type FocusRequest<T> = { requestId: number } & T;

export function AnalysisView({
  analysis,
  loading,
  operation,
  error,
  offline,
  staleResult,
  canRun,
  theme,
  graphFocus,
  evidenceFocus,
  onLoadDemo,
  onValidate,
  onNotify,
  onGraphFocus,
  onEvidenceFocus,
}: {
  analysis: AnalysisResult;
  loading: boolean;
  operation: string;
  error: string | null;
  offline: boolean;
  staleResult: {
    repository: string;
    oldCommit: string;
    newCommit: string;
  } | null;
  canRun: boolean;
  theme: Theme;
  graphFocus: FocusRequest<{ nodeId: string }> | null;
  evidenceFocus: FocusRequest<{ evidence: string }> | null;
  onLoadDemo: (scenario: Scenario) => Promise<void>;
  onValidate: () => Promise<boolean | undefined>;
  onNotify: (message: string) => void;
  onGraphFocus: (nodeId: string) => void;
  onEvidenceFocus: (evidence: string) => void;
}) {
  const certainty = evidenceCertainty(analysis);

  return (
    <div className="workspace-view analysis-view">
      <div className="page-heading">
        <div>
          <p className="eyebrow">
            <GitCompareArrows size={13} /> Change intelligence
          </p>
          <h1>Security analysis</h1>
          <p>
            Decide from deterministic evidence, then inspect the exact path and
            provenance.
          </p>
        </div>
        <a className="primary-button" href="#new-analysis">
          <SlidersHorizontal size={16} /> New analysis <ArrowRight size={16} />
        </a>
      </div>
      <div className="analysis-context surface">
        <div className="context-identity">
          <span className="context-icon">
            <GitBranch size={20} />
          </span>
          <div>
            <strong>
              {analysis.provenance ? "Repository analysis" : "Spring Boot demo"}
            </strong>
            <span>
              {analysis.provenance
                ? `${analysis.provenance.old_commit.slice(0, 7)} → ${analysis.provenance.new_commit.slice(0, 7)}`
                : "Java / Spring Boot"}
            </span>
          </div>
        </div>
        <label className="scenario-control">
          <span>Demo scenario</span>
          <select
            aria-label="Demo scenario"
            value={analysis.provenance ? "" : analysis.scenario}
            disabled={loading}
            onChange={(event) =>
              void onLoadDemo(event.target.value as Scenario)
            }
          >
            {analysis.provenance && (
              <option value="" disabled>
                Choose a demo scenario
              </option>
            )}
            <option value="authorization-removal">Authorization removal</option>
            <option value="safe-change">Safe change</option>
            <option value="new-public-sensitive-endpoint">
              New public sensitive endpoint
            </option>
            <option value="sensitive-resource-exposure">
              Sensitive resource exposure
            </option>
          </select>
        </label>
        <span className="context-tag">
          <Fingerprint size={14} />{" "}
          {analysis.provenance ? "Source evidence" : "Demo fixture"}
        </span>
      </div>
      {error && (
        <div className="state-banner state-error" role="alert">
          <AlertTriangle size={17} />
          <div>
            <strong>
              {offline ? "Offline fixture active" : "Request failed"}
            </strong>
            <span>{error}</span>
          </div>
        </div>
      )}
      {staleResult && (
        <div className="state-banner state-stale" role="alert">
          <AlertTriangle size={17} />
          <div>
            <strong>STALE RESULT</strong>
            <span>
              Displaying {staleResult.repository} ·{" "}
              {staleResult.oldCommit.slice(0, 7)} →{" "}
              {staleResult.newCommit.slice(0, 7)} after the latest request
              failed.
            </span>
          </div>
        </div>
      )}
      <div className="quality-strip" role="status">
        <span>
          <FileSearch size={15} />
          <strong>
            {analysis.provenance ? "Source analysis" : "Demonstration fixture"}
          </strong>
        </span>
        <span>
          <CertaintyBadge certainty={certainty} />
        </span>
        <span>
          {analysis.quality?.confidence ??
            (analysis.provenance ? "UNKNOWN" : "HIGH")}{" "}
          extraction confidence
        </span>
        <span>
          {analysis.quality
            ? `${Math.round(analysis.quality.coverage_ratio * 100)}% call-chain coverage`
            : analysis.provenance
              ? "Coverage unavailable"
              : "100% fixture coverage"}
        </span>
        <span>Validation: {analysis.validation_status ?? "NOT_RUN"}</span>
      </div>
      {analysis.status === "DEGRADED" && (
        <div className="state-banner state-degraded" role="status">
          <AlertTriangle size={17} />
          <div>
            <strong>Partial analysis available</strong>
            <span>
              Review diagnostics for unavailable stages. Deterministic results
              remain visible.
            </span>
          </div>
        </div>
      )}
      {loading && (
        <div className="state-banner state-refreshing" role="status">
          <RefreshCw className="spin" size={16} />
          <div>
            <strong>{operation}…</strong>
            <span>
              The current result remains visible while this request completes.
            </span>
          </div>
        </div>
      )}
      <Overview
        analysis={analysis}
        loading={loading}
        canRun={canRun}
        onValidate={() => void onValidate()}
      />
      <Suspense
        fallback={
          <section
            id="analysis-graph"
            className="section-block graph-loading surface"
            aria-label="Loading security graph"
            role="status"
          >
            <RefreshCw className="spin" size={18} />
            <div>
              <strong>Loading graph workspace</strong>
              <span>
                The decision and deterministic evidence remain available.
              </span>
            </div>
          </section>
        }
      >
        <GraphComparison
          analysis={analysis}
          theme={theme}
          onNotify={onNotify}
          focusTarget={graphFocus}
          onEvidenceFocus={onEvidenceFocus}
        />
      </Suspense>
      <EvidencePanel
        analysis={analysis}
        focusedEvidence={evidenceFocus?.evidence}
        onFocusNode={onGraphFocus}
        onNotify={onNotify}
      />
    </div>
  );
}
