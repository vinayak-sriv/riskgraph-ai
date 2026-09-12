import type { ReactNode } from "react";
import { AlertTriangle, CheckCircle2, ShieldAlert } from "lucide-react";
import type { Category, Verdict } from "../types";
import { certaintyDescription, type EvidenceCertainty } from "../view-model";

export function Metric({
  icon,
  label,
  value,
  detail,
  tone,
  scale,
}: {
  icon: ReactNode;
  label: string;
  value: string | number;
  detail: string;
  tone: "safe" | "danger" | "warning";
  scale?: number;
}) {
  return (
    <article
      className={`metric metric-${tone}`}
      aria-label={`${label}: ${value}${scale ? ` out of ${scale}` : ""}. ${detail}`}
    >
      <div className="metric-top">
        <span>{label}</span>
        <i>{icon}</i>
      </div>
      <div className="metric-value">
        <strong>{value}</strong>
        {scale && <span>/ {scale}</span>}
      </div>
      <div className="metric-bottom">
        <small>{detail}</small>
        {scale && (
          <span className="metric-meter" aria-hidden="true">
            <i style={{ width: `${Number(value)}%` }} />
          </span>
        )}
      </div>
    </article>
  );
}

export function RiskDeltaTrack({
  before,
  after,
  categoryBefore,
  categoryAfter,
}: {
  before: number;
  after: number;
  categoryBefore: Category;
  categoryAfter: Category;
}) {
  const start = Math.min(before, after);
  const width = Math.abs(after - before);
  const direction =
    after > before ? "increased" : after < before ? "decreased" : "unchanged";
  return (
    <div className={`risk-delta-track direction-${direction}`}>
      <div className="delta-values">
        <div>
          <span>Before</span>
          <strong>{before}</strong>
          <small>{categoryBefore}</small>
        </div>
        <div>
          <span>After</span>
          <strong>{after}</strong>
          <small>{categoryAfter}</small>
        </div>
      </div>
      <div
        className="delta-axis"
        aria-label={`Risk changed from ${before} to ${after}`}
      >
        <i
          className="delta-span"
          style={{ left: `${start}%`, width: `${Math.max(width, 1)}%` }}
        />
        <span
          className="delta-marker marker-before"
          role="meter"
          aria-label="Before risk"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={before}
          style={{ left: `${before}%` }}
        >
          <b>{before}</b>
        </span>
        <span
          className="delta-marker marker-after"
          role="meter"
          aria-label="After risk"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={after}
          style={{ left: `${after}%` }}
        >
          <b>{after}</b>
        </span>
      </div>
      <div className="delta-scale" aria-hidden="true">
        <span>0</span>
        <span>20</span>
        <span>40</span>
        <span>60</span>
        <span>80</span>
        <span>100</span>
      </div>
    </div>
  );
}

export function CertaintyBadge({
  certainty,
}: {
  certainty: EvidenceCertainty;
}) {
  return (
    <span
      className={`certainty-badge certainty-${certainty.toLowerCase()}`}
      title={certaintyDescription(certainty)}
    >
      {certainty}
    </span>
  );
}

export function PipelineStep({
  icon,
  label,
  detail,
  state,
}: {
  icon: ReactNode;
  label: string;
  detail: string;
  state: "complete" | "pending";
}) {
  return (
    <div className={`pipeline-step ${state}`}>
      <span>{icon}</span>
      <div>
        <strong>{label}</strong>
        <small>{detail}</small>
      </div>
    </div>
  );
}

export function VerdictBadge({ verdict }: { verdict: Verdict }) {
  const Icon =
    verdict === "ALLOW"
      ? CheckCircle2
      : verdict === "BLOCK"
        ? ShieldAlert
        : AlertTriangle;
  return (
    <span className={`verdict verdict-${verdict.toLowerCase()}`}>
      <Icon size={17} /> {verdict}
    </span>
  );
}
