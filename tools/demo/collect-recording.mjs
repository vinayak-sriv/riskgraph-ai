import { createHash } from "node:crypto";
import {
  copyFileSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  rmSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { execFileSync } from "node:child_process";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL("../..", import.meta.url));
const rawDirectory = join(root, "dist", "demo-recording");
const output = join(root, "dist", "riskgraph-demo.webm");
const manifest = join(root, "dist", "riskgraph-demo.manifest.json");

function findVideos(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const path = join(directory, entry.name);
    return entry.isDirectory()
      ? findVideos(path)
      : entry.name === "video.webm"
        ? [path]
        : [];
  });
}

const videos = findVideos(rawDirectory);
if (videos.length !== 1) {
  throw new Error(`Expected one Playwright video, found ${videos.length}`);
}

mkdirSync(dirname(output), { recursive: true });
copyFileSync(videos[0], output);
const bytes = readFileSync(output);
const sourceCommit = execFileSync("git", ["rev-parse", "HEAD"], {
  cwd: root,
  encoding: "utf8",
}).trim();
const metadata = {
  schema_version: "1.0.0",
  artifact: "riskgraph-demo.webm",
  source_commit: sourceCommit,
  bytes: statSync(output).size,
  sha256: createHash("sha256").update(bytes).digest("hex"),
  generated_at: new Date().toISOString(),
  content:
    "Deterministic dashboard fixture walkthrough; no live or third-party target is contacted.",
};
writeFileSync(manifest, `${JSON.stringify(metadata, null, 2)}\n`, "utf8");
rmSync(rawDirectory, { recursive: true, force: true });
console.log(JSON.stringify(metadata));
