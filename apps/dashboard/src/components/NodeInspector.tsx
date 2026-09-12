import { useEffect, useRef } from "react";
import { ArrowUpRight, FileCode2, X } from "lucide-react";
import type { SourceEvidenceRow } from "../types";
import { formatLabel, type SelectedNode } from "../view-model";

export function NodeInspector({
  selection,
  evidence,
  sourceEvidence,
  onEvidenceFocus,
  onClose,
}: {
  selection: SelectedNode;
  evidence: string[];
  sourceEvidence: Array<{ revision: string; row: SourceEvidenceRow }>;
  onEvidenceFocus?: (evidence: string) => void;
  onClose: () => void;
}) {
  const closeButton = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    const previous = document.activeElement as HTMLElement | null;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    closeButton.current?.focus();
    return () => {
      document.body.style.overflow = overflow;
      previous?.focus();
    };
  }, []);
  const incoming = selection.graph.edges.filter(
    (edge) => edge.target_id === selection.node.id,
  );
  const outgoing = selection.graph.edges.filter(
    (edge) => edge.source_id === selection.node.id,
  );
  const nodeName = (id: string) =>
    selection.graph.nodes.find((node) => node.id === id)?.name ?? id;

  return (
    <>
      <button
        className="drawer-backdrop"
        aria-label="Close node inspector"
        onClick={onClose}
      />
      <aside
        className="node-drawer"
        role="dialog"
        aria-modal="true"
        aria-labelledby="node-inspector-title"
        onKeyDown={(event) => {
          if (event.key === "Escape") onClose();
          if (event.key === "Tab") {
            const controls = Array.from(
              event.currentTarget.querySelectorAll<HTMLElement>(
                "button:not(:disabled),a[href]",
              ),
            );
            const first = controls[0];
            const last = controls.at(-1);
            if (event.shiftKey && document.activeElement === first) {
              event.preventDefault();
              last?.focus();
            }
            if (!event.shiftKey && document.activeElement === last) {
              event.preventDefault();
              first?.focus();
            }
          }
        }}
      >
        <header>
          <div>
            <p className="eyebrow">
              {selection.revision} graph ·{" "}
              {formatLabel(selection.node.node_type)}
            </p>
            <h2 id="node-inspector-title">{selection.node.name}</h2>
          </div>
          <button
            ref={closeButton}
            className="icon-button"
            title="Close inspector"
            aria-label="Close inspector"
            onClick={onClose}
          >
            <X size={17} />
          </button>
        </header>
        <div className="drawer-status">
          <span className={selection.isPathNode ? "on-path" : "off-path"}>
            {selection.isPathNode
              ? "On new attack path"
              : "Outside new attack path"}
          </span>
          <code>{selection.node.id}</code>
        </div>
        <section>
          <p className="eyebrow">Incoming connections</p>
          {incoming.length === 0 ? (
            <p className="empty-copy">No incoming edges</p>
          ) : (
            incoming.map((edge) => (
              <ConnectionRow
                key={`${edge.source_id}-${edge.relationship}`}
                name={nodeName(edge.source_id)}
                relationship={edge.relationship}
                direction="incoming"
              />
            ))
          )}
        </section>
        <section>
          <p className="eyebrow">Outgoing connections</p>
          {outgoing.length === 0 ? (
            <p className="empty-copy">No outgoing edges</p>
          ) : (
            outgoing.map((edge) => (
              <ConnectionRow
                key={`${edge.target_id}-${edge.relationship}`}
                name={nodeName(edge.target_id)}
                relationship={edge.relationship}
                direction="outgoing"
              />
            ))
          )}
        </section>
        <section>
          <p className="eyebrow">Related deterministic evidence</p>
          {evidence.length ? (
            <div className="drawer-evidence-list">
              {evidence.map((item) => (
                <button
                  type="button"
                  key={item}
                  onClick={() => {
                    onEvidenceFocus?.(item);
                    onClose();
                  }}
                >
                  <span>{item}</span>
                  <ArrowUpRight size={14} />
                </button>
              ))}
            </div>
          ) : (
            <p className="empty-copy">
              No direct evidence-to-node mapping is available for this item.
            </p>
          )}
        </section>
        <section className="provenance-pending">
          <p className="eyebrow">Source provenance</p>
          {sourceEvidence.length ? (
            sourceEvidence.map(({ revision, row }) => (
              <div
                className="drawer-source"
                key={`${revision}-${row.source_location.path}-${row.source_location.start_line}`}
              >
                <FileCode2 size={15} />
                <div>
                  <strong>
                    {formatLabel(revision)} · {row.endpoint.method}{" "}
                    {row.endpoint.endpoint}
                  </strong>
                  <code>
                    {row.source_location.path}:{row.source_location.start_line}–
                    {row.source_location.end_line}
                  </code>
                </div>
                <span>{row.extraction_confidence.overall}</span>
              </div>
            ))
          ) : (
            <p>Direct source mapping is unavailable for this node.</p>
          )}
          <a href="#analysis-evidence" onClick={onClose}>
            Open evidence and technical details <ArrowUpRight size={14} />
          </a>
        </section>
      </aside>
    </>
  );
}

function ConnectionRow({
  name,
  relationship,
  direction,
}: {
  name: string;
  relationship: string;
  direction: "incoming" | "outgoing";
}) {
  return (
    <div className="connection-row">
      <span>{direction === "incoming" ? "From" : "To"}</span>
      <strong>{name}</strong>
      <small>{formatLabel(relationship)}</small>
    </div>
  );
}
