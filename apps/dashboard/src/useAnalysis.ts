import { useCallback, useEffect, useRef, useState } from "react";
import { api, platformUrl } from "./api";
import fixtures from "./fixtures.json";
import type { AnalysisResult } from "./types";
import type { Account, GitHubConnectionState } from "./components/AccountPanel";

export type SourceInput = {
  repository_path: string;
  old_commit: string;
  new_commit: string;
};
export type Scenario = keyof typeof fixtures;
type SessionResponse = {
  authenticated: boolean;
  user?: Account;
  github?: GitHubConnectionState;
  github_connection_required?: boolean;
};
type StaleResult = {
  repository: string;
  oldCommit: string;
  newCommit: string;
};
export type SavedScanHistoryItem = {
  job_id: string;
  scan_id: string | null;
  repository: string;
  old_commit: string;
  new_commit: string;
  status: "QUEUED" | "RUNNING" | "COMPLETED" | "FAILED" | "CANCELLED";
  risk_delta: number | null;
  verdict: string | null;
  updated_at: string;
};

function abortableDelay(milliseconds: number, signal: AbortSignal) {
  return new Promise<void>((resolve, reject) => {
    const timeout = window.setTimeout(() => {
      signal.removeEventListener("abort", aborted);
      resolve();
    }, milliseconds);
    const aborted = () => {
      window.clearTimeout(timeout);
      reject(
        signal.reason ?? new DOMException("Request aborted", "AbortError"),
      );
    };
    signal.addEventListener("abort", aborted, { once: true });
  });
}

/** Owns requests; presentation components only consume the platform's evidence. */
export function useAnalysis() {
  const [analysis, setAnalysis] = useState<AnalysisResult>(
    fixtures["authorization-removal"] as AnalysisResult,
  );
  const [user, setUser] = useState<Account | null>(null);
  const [github, setGithub] = useState<GitHubConnectionState>({
    status: "NOT_CONFIGURED",
  });
  const [githubConnectionRequired, setGithubConnectionRequired] =
    useState(true);
  const [loading, setLoading] = useState(true);
  const [operation, setOperation] = useState("Loading analysis");
  const [error, setError] = useState<string | null>(null);
  const [offline, setOffline] = useState(false);
  const [staleResult, setStaleResult] = useState<StaleResult | null>(null);
  const [toast, setToast] = useState<string | null>(null);
  const [history, setHistory] = useState<SavedScanHistoryItem[]>([]);
  const [historyCursor, setHistoryCursor] = useState<string | null>(null);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const requestId = useRef(0);
  const activeRequest = useRef<AbortController | null>(null);
  const activeJob = useRef<string | null>(null);
  const currentUser = useRef<Account | null>(null);
  const githubConnected = github.status === "CONNECTED";
  const sourceAccessEnabled = !githubConnectionRequired || githubConnected;
  const canRead = !!user && sourceAccessEnabled;
  const canRun =
    sourceAccessEnabled && (user?.role === "ANALYST" || user?.role === "ADMIN");

  const updateUser = useCallback((account: Account | null) => {
    const signedOut = currentUser.current !== null && account === null;
    currentUser.current = account;
    setUser(account);
    if (signedOut) {
      // Never restore a protected result from a late request after sign-out.
      ++requestId.current;
      activeRequest.current?.abort();
      setLoading(false);
      setError(null);
      setStaleResult(null);
      setHistory([]);
      setHistoryCursor(null);
      setAnalysis((current) =>
        current.provenance
          ? (fixtures["authorization-removal"] as AnalysisResult)
          : current,
      );
    }
  }, []);

  const refreshSession = useCallback(async () => {
    try {
      const response = await api("/auth/session");
      const result = (await response.json()) as SessionResponse;
      updateUser(result.authenticated && result.user ? result.user : null);
      setGithub(result.github ?? { status: "NOT_CONFIGURED" });
      // Older or malformed responses fail closed until the backend explicitly
      // confirms that GitHub linkage is optional.
      setGithubConnectionRequired(result.github_connection_required !== false);
    } catch {
      updateUser(null);
      setGithub({ status: "NOT_CONFIGURED" });
      setGithubConnectionRequired(true);
    }
  }, [updateUser]);

  const begin = useCallback((label: string) => {
    activeRequest.current?.abort();
    const controller = new AbortController();
    activeRequest.current = controller;
    const id = ++requestId.current;
    setLoading(true);
    setError(null);
    setOperation(label);
    return {
      id,
      signal: AbortSignal.any([controller.signal, AbortSignal.timeout(120000)]),
    };
  }, []);

  const loadDemo = useCallback(
    async (name: Scenario, notify = false) => {
      const request = begin("Loading demo scenario");
      try {
        const response = await fetch(`${platformUrl}/demo/scenarios/${name}`, {
          signal: request.signal,
        });
        if (!response.ok)
          throw new Error(`Platform API returned HTTP ${response.status}`);
        const result = await response.json();
        if (request.id !== requestId.current) return;
        setAnalysis(result);
        setOffline(false);
        setStaleResult(null);
        if (notify) setToast("Analysis refreshed");
      } catch {
        if (request.id !== requestId.current) return;
        setAnalysis(fixtures[name] as AnalysisResult);
        setOffline(true);
        setStaleResult(null);
        setError(
          "Platform unavailable. Showing a saved deterministic fixture; validation has not run.",
        );
        if (notify) setToast("Showing the saved demo fixture");
      } finally {
        if (request.id === requestId.current) setLoading(false);
      }
    },
    [begin],
  );

  useEffect(() => {
    void loadDemo("authorization-removal");
    return () => {
      ++requestId.current;
      activeRequest.current?.abort();
    };
  }, [loadDemo]);

  useEffect(() => {
    void refreshSession();
    window.addEventListener("riskgraph-session-expired", refreshSession);
    window.addEventListener("focus", refreshSession);
    return () => {
      window.removeEventListener("riskgraph-session-expired", refreshSession);
      window.removeEventListener("focus", refreshSession);
    };
  }, [refreshSession]);

  const handleUserChange = useCallback(
    (account: Account | null) => updateUser(account),
    [updateUser],
  );
  const handleGithubChange = useCallback(
    (connection: GitHubConnectionState) =>
      setGithub((current) =>
        connection.status === "DISCONNECTED" &&
        current.status === "NOT_CONFIGURED"
          ? current
          : connection,
      ),
    [],
  );

  const requestScan = async (
    route: string,
    options: RequestInit,
    label: string,
    failure: string,
  ) => {
    const request = begin(label);
    try {
      const response = await api(route, { ...options, signal: request.signal });
      const result = await response.json();
      if (request.id !== requestId.current) return;
      if (!response.ok)
        throw new Error(
          `${result.code ?? response.status}: ${result.message ?? failure}`,
        );
      setAnalysis(result);
      setOffline(false);
      setStaleResult(null);
      setToast("Analysis result updated");
      return true;
    } catch (reason) {
      if (request.id === requestId.current) {
        setError(
          `${failure}. The previous result remains visible. ${reason instanceof Error ? reason.message : "Please try again."}`,
        );
        if (analysis.provenance) {
          setStaleResult({
            repository: analysis.provenance.repository_identity,
            oldCommit: analysis.provenance.old_commit,
            newCommit: analysis.provenance.new_commit,
          });
        }
      }
    } finally {
      if (request.id === requestId.current) setLoading(false);
    }
    return false;
  };

  const runSource = async (input: SourceInput) => {
    if (!canRun) return false;
    const request = begin("Submitting source analysis");
    try {
      const submissionResponse = await api("/scans", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          repository: input.repository_path,
          old_commit: input.old_commit,
          new_commit: input.new_commit,
        }),
        signal: request.signal,
      });
      const submission = await submissionResponse.json();
      if (!submissionResponse.ok) {
        throw new Error(
          `${submission.code ?? submissionResponse.status}: ${submission.message ?? "Submission failed"}`,
        );
      }
      // Compatibility for older local platform builds; deployed v2 returns job_id.
      let result = submission;
      if (submission.job_id) {
        activeJob.current = submission.job_id;
        let delay = 250;
        let job = submission;
        while (job.status === "QUEUED" || job.status === "RUNNING") {
          await abortableDelay(delay, request.signal);
          delay = Math.min(2000, delay * 2);
          const statusResponse = await api(`/scan-jobs/${submission.job_id}`, {
            signal: request.signal,
          });
          job = await statusResponse.json();
          if (!statusResponse.ok)
            throw new Error(job.code ?? `HTTP ${statusResponse.status}`);
        }
        if (job.status !== "COMPLETED") {
          throw new Error(
            job.reason_code ?? `Scan job ${job.status.toLowerCase()}`,
          );
        }
        const resultResponse = await api(
          `/scan-jobs/${submission.job_id}/result`,
          {
            signal: request.signal,
          },
        );
        result = await resultResponse.json();
        if (!resultResponse.ok)
          throw new Error(result.code ?? `HTTP ${resultResponse.status}`);
      }
      if (request.id !== requestId.current) return false;
      activeJob.current = null;
      setAnalysis(result);
      setOffline(false);
      setStaleResult(null);
      setHistory([]);
      setHistoryCursor(null);
      setToast("Analysis result updated");
      return true;
    } catch (reason) {
      if (request.id === requestId.current) {
        setError(
          `Source analysis failed. The previous result remains visible. ${
            reason instanceof Error ? reason.message : "Please try again."
          }`,
        );
        if (analysis.provenance) {
          setStaleResult({
            repository: analysis.provenance.repository_identity,
            oldCommit: analysis.provenance.old_commit,
            newCommit: analysis.provenance.new_commit,
          });
        }
      }
      return false;
    } finally {
      if (request.id === requestId.current) setLoading(false);
    }
  };
  const runValidation = async () => {
    if (!canRun || !analysis.scan_id) return false;
    return requestScan(
      `/analyses/${analysis.scan_id}/validation`,
      { method: "POST" },
      "Validating the registered Docker sandbox",
      "Validation failed",
    );
  };
  const openScan = async (id: string) => {
    if (!canRead) return false;
    return requestScan(
      `/analyses/${id}`,
      {},
      "Opening saved scan",
      "Saved scan unavailable",
    );
  };
  const loadHistory = useCallback(
    async (cursor?: string) => {
      if (!canRead) return;
      setHistoryLoading(true);
      setHistoryError(null);
      try {
        const query = new URLSearchParams({ limit: "20" });
        if (cursor) query.set("cursor", cursor);
        const response = await api(`/scans?${query}`);
        const result = (await response.json()) as {
          items?: SavedScanHistoryItem[];
          next_cursor?: string | null;
          code?: string;
        };
        if (!response.ok)
          throw new Error(result.code ?? `HTTP ${response.status}`);
        setHistory((current) =>
          cursor ? [...current, ...(result.items ?? [])] : (result.items ?? []),
        );
        setHistoryCursor(result.next_cursor ?? null);
      } catch (reason) {
        setHistoryError(
          reason instanceof Error ? reason.message : "Saved scans unavailable",
        );
      } finally {
        setHistoryLoading(false);
      }
    },
    [canRead],
  );
  const refresh = () =>
    analysis.provenance
      ? runSource({
          repository_path: analysis.provenance.repository_path,
          old_commit: analysis.provenance.old_commit,
          new_commit: analysis.provenance.new_commit,
        })
      : loadDemo(analysis.scenario as Scenario, true);

  return {
    analysis,
    user,
    github,
    githubConnectionRequired,
    canRead,
    canRun,
    loading,
    operation,
    error,
    offline,
    staleResult,
    toast,
    setToast,
    handleUserChange,
    handleGithubChange,
    refreshSession,
    loadDemo,
    runSource,
    runValidation,
    openScan,
    history,
    historyCursor,
    historyLoading,
    historyError,
    loadHistory,
    refresh,
  };
}
