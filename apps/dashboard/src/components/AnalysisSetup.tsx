import { useEffect, useMemo, useState } from "react";
import {
  ArrowRight,
  Check,
  FileSearch,
  GitCompareArrows,
  History,
  LockKeyhole,
  Search,
  ShieldCheck,
  Terminal,
} from "lucide-react";
import type { Account } from "./AccountPanel";
import type { AnalysisResult } from "../types";
import type { SourceInput } from "../useAnalysis";

const SHA_PATTERN = /^[0-9a-fA-F]{40}$/;

export function NewAnalysisPanel({
  analysis,
  user,
  githubConnected,
  loading,
  requestError,
  onRun,
  onSuccess,
}: {
  analysis: AnalysisResult;
  user: Account | null;
  githubConnected: boolean;
  loading: boolean;
  requestError: string | null;
  onRun: (input: SourceInput) => Promise<boolean | undefined>;
  onSuccess: () => void;
}) {
  const [input, setInput] = useState<SourceInput>({
    repository_path: "",
    old_commit: "",
    new_commit: "",
  });
  const canRun = user?.role === "ADMIN" || user?.role === "ANALYST";
  useEffect(() => {
    if (analysis.provenance) {
      const { repository_path, old_commit, new_commit } = analysis.provenance;
      setInput({ repository_path, old_commit, new_commit });
    }
  }, [analysis.provenance]);
  useEffect(() => {
    if (!user)
      setInput({ repository_path: "", old_commit: "", new_commit: "" });
  }, [user]);

  const checks = useMemo(
    () => ({
      repository: input.repository_path.trim().length > 0,
      oldCommit: SHA_PATTERN.test(input.old_commit),
      newCommit: SHA_PATTERN.test(input.new_commit),
      distinct:
        input.old_commit.length === 0 ||
        input.new_commit.length === 0 ||
        input.old_commit.toLowerCase() !== input.new_commit.toLowerCase(),
    }),
    [input],
  );
  const formValid =
    checks.repository &&
    checks.oldCommit &&
    checks.newCommit &&
    checks.distinct;

  return (
    <section className="workspace-view" aria-labelledby="new-analysis-title">
      <div className="page-heading compact-heading">
        <div>
          <p className="eyebrow">
            <GitCompareArrows size={13} /> New analysis
          </p>
          <h1 id="new-analysis-title">Compare source revisions</h1>
          <p>
            Review the exact local repository and immutable commits before
            analysis starts.
          </p>
        </div>
        <span className="context-tag">
          <Terminal size={14} /> Local workspace
        </span>
      </div>
      {!githubConnected ? (
        <GitHubRequired />
      ) : (
        <form
          className="analysis-form surface analysis-form-refined"
          onSubmit={(event) => {
            event.preventDefault();
            if (canRun && formValid)
              void onRun(input).then((success) => {
                if (success) onSuccess();
              });
          }}
        >
          <div className="form-intro">
            <span className="context-icon">
              <GitCompareArrows size={20} />
            </span>
            <div>
              <p className="eyebrow">Source</p>
              <h2>Analysis inputs</h2>
              <p>
                Only allowlisted local repositories are accepted by the
                platform.
              </p>
            </div>
          </div>
          <label className="repository-input">
            Repository path
            <input
              aria-label="Repository path"
              required
              value={input.repository_path}
              onChange={(event) =>
                setInput({ ...input, repository_path: event.target.value })
              }
              placeholder="Allowlisted local repository"
              spellCheck={false}
              aria-describedby="repository-help"
            />
            <FieldStatus
              id="repository-help"
              valid={checks.repository}
              empty={!input.repository_path}
              validText="Repository path ready"
              emptyText="Enter an allowlisted local path"
            />
          </label>
          <label>
            Old commit SHA
            <input
              aria-label="Old commit SHA"
              required
              pattern="[0-9a-fA-F]{40}"
              value={input.old_commit}
              onChange={(event) =>
                setInput({ ...input, old_commit: event.target.value })
              }
              placeholder="40-character SHA"
              spellCheck={false}
              aria-describedby="old-commit-help"
            />
            <FieldStatus
              id="old-commit-help"
              valid={checks.oldCommit}
              empty={!input.old_commit}
              validText="Valid immutable revision"
              emptyText="Enter the base revision"
            />
          </label>
          <label>
            New commit SHA
            <input
              aria-label="New commit SHA"
              required
              pattern="[0-9a-fA-F]{40}"
              value={input.new_commit}
              onChange={(event) =>
                setInput({ ...input, new_commit: event.target.value })
              }
              placeholder="40-character SHA"
              spellCheck={false}
              aria-describedby="new-commit-help"
            />
            <FieldStatus
              id="new-commit-help"
              valid={checks.newCommit && checks.distinct}
              empty={!input.new_commit}
              validText="Valid comparison revision"
              emptyText="Enter the changed revision"
              invalidText={
                !checks.distinct
                  ? "Choose a revision different from the base"
                  : undefined
              }
            />
          </label>
          <aside
            className="submission-review"
            aria-label="Analysis submission review"
          >
            <div>
              <p className="eyebrow">Pre-submit review</p>
              <h3>Confirm analysis scope</h3>
            </div>
            <dl>
              <div>
                <dt>Repository</dt>
                <dd>{input.repository_path || "Not provided"}</dd>
              </div>
              <div>
                <dt>Revision pair</dt>
                <dd>
                  <code>{shortSha(input.old_commit)}</code>
                  <ArrowRight size={13} />
                  <code>{shortSha(input.new_commit)}</code>
                </dd>
              </div>
              <div>
                <dt>Execution</dt>
                <dd>Configured local environment</dd>
              </div>
            </dl>
          </aside>
          <div className="form-footer">
            <p>
              <ShieldCheck size={15} />
              {canRun
                ? "The platform will preserve provenance and return deterministic evidence."
                : "Security Analyst or Admin access is required."}
            </p>
            <button
              className="primary-button"
              disabled={loading || !canRun || !formValid}
              type="submit"
            >
              <GitCompareArrows size={16} />
              {loading ? "Analyzing…" : "Run Analysis"}
              <ArrowRight size={16} />
            </button>
          </div>
        </form>
      )}
      {requestError && (
        <p className="fixture-notice" role="alert">
          {requestError}
        </p>
      )}
    </section>
  );
}

export function SavedScansPanel({
  analysis,
  user,
  githubConnected,
  loading,
  requestError,
  onOpen,
  onSuccess,
}: {
  analysis: AnalysisResult;
  user: Account | null;
  githubConnected: boolean;
  loading: boolean;
  requestError: string | null;
  onOpen: (id: string) => Promise<boolean | undefined>;
  onSuccess: () => void;
}) {
  const [savedScan, setSavedScan] = useState("");
  useEffect(() => {
    if (!user) setSavedScan("");
  }, [user]);
  const currentScan = analysis.scan_id && analysis.provenance ? analysis : null;
  return (
    <section className="workspace-view" aria-labelledby="saved-scans-title">
      <div className="page-heading compact-heading">
        <div>
          <p className="eyebrow">
            <History size={13} /> Saved scans
          </p>
          <h1 id="saved-scans-title">Open previous analysis</h1>
          <p>Return to a registered scan without rerunning source analysis.</p>
        </div>
        <span className="runtime-badge">Listing API pending</span>
      </div>
      {!githubConnected ? (
        <GitHubRequired />
      ) : (
        <>
          <form
            className="surface saved-scan-search"
            onSubmit={(event) => {
              event.preventDefault();
              void onOpen(savedScan).then((success) => {
                if (success) onSuccess();
              });
            }}
          >
            <label>
              Saved scan ID
              <div className="search-input">
                <Search size={16} />
                <input
                  required
                  pattern="[0-9a-f]{64}"
                  value={savedScan}
                  placeholder="Enter a 64-character scan ID"
                  spellCheck={false}
                  onChange={(event) => setSavedScan(event.target.value)}
                />
              </div>
            </label>
            <button
              className="primary-button"
              disabled={loading || savedScan.length !== 64}
            >
              <FileSearch size={16} /> Open scan
            </button>
          </form>
          <div className="surface saved-scans-table-wrap">
            <div className="surface-heading">
              <div>
                <p className="eyebrow">Available in this session</p>
                <h2>Recent result</h2>
              </div>
              <span className="count-badge">{currentScan ? 1 : 0}</span>
            </div>
            {currentScan ? (
              <div
                className="source-table-wrap"
                role="region"
                aria-label="Current saved scan"
                tabIndex={0}
              >
                <table className="source-table saved-scans-table">
                  <thead>
                    <tr>
                      <th>Repository</th>
                      <th>Revisions</th>
                      <th>Verdict</th>
                      <th>Risk delta</th>
                      <th>Confidence</th>
                      <th>
                        <span className="sr-only">Action</span>
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td>{currentScan.provenance!.repository_identity}</td>
                      <td>
                        <code>
                          {currentScan.provenance!.old_commit.slice(0, 7)} →{" "}
                          {currentScan.provenance!.new_commit.slice(0, 7)}
                        </code>
                      </td>
                      <td>
                        {currentScan.final_verdict ?? currentScan.verdict}
                      </td>
                      <td>
                        <code>
                          {currentScan.risk_result.risk_delta > 0 ? "+" : ""}
                          {currentScan.risk_result.risk_delta}
                        </code>
                      </td>
                      <td>{currentScan.quality?.confidence ?? "UNKNOWN"}</td>
                      <td>
                        <button
                          className="secondary-button"
                          type="button"
                          onClick={onSuccess}
                        >
                          View
                        </button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </div>
            ) : (
              <div className="empty-surface">
                <History size={20} />
                <strong>No source scan opened in this session</strong>
                <p>
                  Enter a known scan ID above. Searchable history will appear
                  here when the platform exposes its scan-listing API.
                </p>
              </div>
            )}
          </div>
        </>
      )}
      {requestError && (
        <p className="fixture-notice" role="alert">
          {requestError}
        </p>
      )}
    </section>
  );
}

function GitHubRequired() {
  return (
    <div className="state-panel state-empty github-required" role="status">
      <LockKeyhole size={20} />
      <div>
        <strong>Connect your GitHub account to access this page</strong>
        <p>
          GitHub authorization is required before RiskGraph can open source
          scans, saved evidence, or validation services.
        </p>
      </div>
      <a className="primary-button" href="#account">
        Connect GitHub
      </a>
    </div>
  );
}

function FieldStatus({
  id,
  valid,
  empty,
  validText,
  emptyText,
  invalidText = "Check the required format",
}: {
  id: string;
  valid: boolean;
  empty: boolean;
  validText: string;
  emptyText: string;
  invalidText?: string;
}) {
  return (
    <span
      id={id}
      className={`field-status ${valid ? "valid" : empty ? "empty" : "invalid"}`}
    >
      {valid && <Check size={13} />}
      {valid ? validText : empty ? emptyText : invalidText}
    </span>
  );
}

function shortSha(value: string) {
  return value ? value.slice(0, 7) + (value.length > 7 ? "…" : "") : "Pending";
}
