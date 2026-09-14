import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import type { AnalysisResult } from "../types";
import { ValidationEvidence } from "./ValidationEvidence";

const validation: NonNullable<AnalysisResult["validation"]> = {
  status: "CONFIRMED",
  confirmed: true,
  reason_code: "HTTP_AUTH_PROBE",
  sandbox_revision: "vulnerable",
  container_image_id: "sha256:" + "a".repeat(64),
  probe_image_id: "sha256:" + "b".repeat(64),
  response_sha256: "c".repeat(64),
  source_commit: "d".repeat(40),
  cleanup_complete: true,
};

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

it("labels immutable confirmation evidence as regression ready", async () => {
  const writeText = vi.fn().mockResolvedValue(undefined);
  vi.stubGlobal("navigator", { clipboard: { writeText } });
  const notify = vi.fn();
  render(<ValidationEvidence validation={validation} onNotify={notify} />);

  expect(screen.getByText("Regression-ready evidence")).toBeInTheDocument();
  expect(screen.getByText("Complete")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "Copy provenance" }));

  expect(writeText).toHaveBeenCalledWith(JSON.stringify(validation, null, 2));
  await waitFor(() =>
    expect(notify).toHaveBeenCalledWith("Validation provenance copied"),
  );
});

it("does not present legacy or malformed evidence as regression ready", () => {
  render(
    <ValidationEvidence validation={{ ...validation, probe_image_id: null }} />,
  );

  expect(screen.getByText("Incomplete provenance")).toBeInTheDocument();
  expect(screen.getByText("Not recorded")).toBeInTheDocument();
  expect(
    screen.queryByText("Regression-ready evidence"),
  ).not.toBeInTheDocument();
});
