import type { SecurityGraph } from "../types";
import type {
  GraphLayoutRequest,
  GraphLayoutResponse,
} from "./graph-layout.worker";

let nextRequestId = 1;

function fallbackPositions(graph: SecurityGraph) {
  const columns = Math.max(1, Math.ceil(Math.sqrt(graph.nodes.length)));
  return new Map(
    graph.nodes.map((node, index) => [
      node.id,
      { x: (index % columns) * 280, y: Math.floor(index / columns) * 100 },
    ]),
  );
}

// One shared worker instead of spawning and terminating a ~47KB module per
// layout. Two graph panels x every filter toggle was re-instantiating and
// re-parsing dagre each time; responses are matched by requestId instead.
let sharedWorker: Worker | null = null;
const pending = new Map<
  number,
  (response: GraphLayoutResponse | null) => void
>();

function resolveAll(response: GraphLayoutResponse | null) {
  const waiting = [...pending.values()];
  pending.clear();
  waiting.forEach((settle) => settle(response));
}

function layoutWorker(): Worker | null {
  if (sharedWorker) return sharedWorker;
  try {
    sharedWorker = new Worker(
      new URL("./graph-layout.worker.ts", import.meta.url),
      { type: "module" },
    );
  } catch {
    return null;
  }
  sharedWorker.onmessage = (event: MessageEvent<GraphLayoutResponse>) => {
    const settle = pending.get(event.data.requestId);
    if (!settle) return;
    pending.delete(event.data.requestId);
    settle(event.data);
  };
  sharedWorker.onerror = () => {
    // A dead worker cannot serve the queue; drop it and fall back.
    sharedWorker?.terminate();
    sharedWorker = null;
    resolveAll(null);
  };
  return sharedWorker;
}

export function requestGraphLayout(
  graph: SecurityGraph,
  signal: AbortSignal,
): Promise<Map<string, { x: number; y: number }>> {
  if (typeof Worker === "undefined") {
    return Promise.resolve(fallbackPositions(graph));
  }
  const worker = layoutWorker();
  if (!worker) return Promise.resolve(fallbackPositions(graph));

  const requestId = nextRequestId++;
  const request: GraphLayoutRequest = { requestId, graph };

  return new Promise((resolve, reject) => {
    const onAbort = () => {
      pending.delete(requestId);
      reject(new DOMException("Graph layout cancelled", "AbortError"));
    };
    signal.addEventListener("abort", onAbort, { once: true });
    pending.set(requestId, (response) => {
      signal.removeEventListener("abort", onAbort);
      if (signal.aborted) return;
      resolve(
        response
          ? new Map(Object.entries(response.positions))
          : fallbackPositions(graph),
      );
    });
    worker.postMessage(request);
  });
}
