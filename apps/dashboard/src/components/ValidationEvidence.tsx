import { ClipboardCheck, Copy, Fingerprint, TriangleAlert } from "lucide-react";
import type { AnalysisResult } from "../types";

const IMAGE_ID = /^sha256:[0-9a-f]{64}$/;
const SHA256 = /^[0-9a-f]{64}$/;
const COMMIT = /^[0-9a-f]{40}$/;

export function ValidationEvidence({
  validation,
  onNotify,
}: {
  validation: NonNullable<AnalysisResult["validation"]>;
  onNotify?: (message: string) => void;
}) {
  const readiness = validationReadiness(validation);
  const fields = [
    ["Sandbox revision", validation.sandbox_revision],
    ["Source commit", validation.source_commit],
    ["Container image", validation.container_image_id],
    ["Probe image", validation.probe_image_id],
    ["Response SHA-256", validation.response_sha256],
  ] as const;

  const copyProvenance = async () => {
    try {
      await navigator.clipboard.writeText(JSON.stringify(validation, null, 2));
      onNotify?.("Validation provenance copied");
    } catch {
      onNotify?.(
        "Clipboard unavailable. Export the full analysis JSON instead.",
      );
    }
  };

  return (
    <article className="surface validation-provenance">
      <div className="validation-provenance-heading">
        <div>
          <p className="eyebrow">
            <Fingerprint size={14} /> Runtime evidence
          </p>
          <h3>Validation provenance</h3>
        </div>
        <span
          className={`provenance-status ${readiness.ready ? "ready" : "incomplete"}`}
        >
          {readiness.ready ? (
            <ClipboardCheck size={15} />
          ) : (
            <TriangleAlert size={15} />
          )}
          {readiness.label}
        </span>
      </div>
      <p className="validation-provenance-copy">{readiness.detail}</p>
      <dl className="validation-provenance-grid">
        {fields.map(([label, value]) => (
          <div key={label}>
            <dt>{label}</dt>
            <dd>
              {value ? (
                <code title={value}>{compactIdentity(value)}</code>
              ) : (
                <span className="missing-value">Not recorded</span>
              )}
            </dd>
          </div>
        ))}
        <div>
          <dt>Cleanup</dt>
          <dd>{validation.cleanup_complete ? "Complete" : "Not verified"}</dd>
        </div>
      </dl>
      <button
        className="secondary-button"
        type="button"
        onClick={() => void copyProvenance()}
      >
        <Copy size={15} /> Copy provenance
      </button>
    </article>
  );
}

export function validationReadiness(
  validation: NonNullable<AnalysisResult["validation"]>,
) {
  if (validation.status !== "CONFIRMED" || validation.confirmed !== true) {
    return {
      ready: false,
      label: "Not confirmed",
      detail:
        "Runtime evidence is visible, but it cannot seed a permanent regression.",
    };
  }
  const complete =
    validation.sandbox_revision === "vulnerable" &&
    IMAGE_ID.test(validation.container_image_id ?? "") &&
    IMAGE_ID.test(validation.probe_image_id ?? "") &&
    SHA256.test(validation.response_sha256 ?? "") &&
    COMMIT.test(validation.source_commit ?? "") &&
    validation.cleanup_complete === true;
  return complete
    ? {
        ready: true,
        label: "Regression-ready evidence",
        detail:
          "This confirmation is bound to immutable sandbox, probe, response, and source identities.",
      }
    : {
        ready: false,
        label: "Incomplete provenance",
        detail:
          "The confirmation is missing current immutable evidence and must not seed a new regression.",
      };
}

function compactIdentity(value: string) {
  return value.length > 28 ? `${value.slice(0, 16)}…${value.slice(-8)}` : value;
}
