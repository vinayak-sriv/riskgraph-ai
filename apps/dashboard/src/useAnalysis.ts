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

/** Owns requests; presentation components only consume the platform's evidence. */
export function useAnalysis() {
  const [analysis, setAnalysis] = useState<AnalysisResult>(
    fixtures["authorization-removal"] as AnalysisResult,
  );
  const [user, setUser] = useState<Account | null>(null);
  const [github, setGithub] = useState<GitHubConnectionState>({
    status: "NOT_CONFIGURED",
  });
  const [loading, setLoading] = useState(true);
  const [operation, setOperation] = useState("Loading analysis");
  const [error, setError] = useState<string | null>(null);
  const [offline, setOffline] = useState(false);
  const [toast, setToast] = useState<string | null>(null);
  const requestId = useRef(0);
  const activeRequest = useRef<AbortController | null>(null);
  const currentUser = useRef<Account | null>(null);
  const githubConnected = github.status === "CONNECTED";
  const canRead = !!user && githubConnected;
  const canRun =
    githubConnected && (user?.role === "ANALYST" || user?.role === "ADMIN");

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
      const result = await response.json();
      updateUser(result.authenticated ? result.user : null);
      setGithub(result.github ?? { status: "NOT_CONFIGURED" });
    } catch {
      updateUser(null);
      setGithub({ status: "NOT_CONFIGURED" });
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
        if (notify) setToast("Analysis refreshed");
      } catch {
        if (request.id !== requestId.current) return;
        setAnalysis(fixtures[name] as AnalysisResult);
        setOffline(true);
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
      setToast("Analysis result updated");
      return true;
    } catch (reason) {
      if (request.id === requestId.current)
        setError(
          `${failure}. The previous result remains visible. ${reason instanceof Error ? reason.message : "Please try again."}`,
        );
    } finally {
      if (request.id === requestId.current) setLoading(false);
    }
    return false;
  };

  const runSource = async (input: SourceInput) => {
    if (!canRun) return false;
    return requestScan(
      "/analyses",
      {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(input),
      },
      "Analyzing source revisions",
      "Source analysis failed",
    );
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
    githubConnected,
    canRead,
    canRun,
    loading,
    operation,
    error,
    offline,
    toast,
    setToast,
    handleUserChange,
    handleGithubChange,
    refreshSession,
    loadDemo,
    runSource,
    runValidation,
    openScan,
    refresh,
  };
}
