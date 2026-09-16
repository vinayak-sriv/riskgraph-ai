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

export function requestGraphLayout(
  graph: SecurityGraph,
  signal: AbortSignal,
): Promise<Map<string, { x: number; y: number }>> {
  if (typeof Worker === "undefined") {
    return Promise.resolve(fallbackPositions(graph));
  }

  const requestId = nextRequestId++;
  const worker = new Worker(
    new URL("./graph-layout.worker.ts", import.meta.url),
    {
      type: "module",
    },
  );
  const request: GraphLayoutRequest = { requestId, graph };

  return new Promise((resolve, reject) => {
    const stop = () => worker.terminate();
    signal.addEventListener(
      "abort",
      () => {
        stop();
        reject(new DOMException("Graph layout cancelled", "AbortError"));
      },
      { once: true },
    );
    worker.onerror = () => {
      stop();
      resolve(fallbackPositions(graph));
    };
    worker.onmessage = (event: MessageEvent<GraphLayoutResponse>) => {
      if (event.data.requestId !== requestId || signal.aborted) return;
      stop();
      resolve(new Map(Object.entries(event.data.positions)));
    };
    worker.postMessage(request);
  });
}
