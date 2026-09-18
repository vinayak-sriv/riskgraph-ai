import { expect, it } from "vitest";
import { reconcileNodePositions } from "./SecurityGraph";

const current = [{ id: "one", position: { x: 0, y: 0 }, label: "existing" }];
const laidOut = [{ id: "one", position: { x: 120, y: 240 }, label: "updated" }];

it("applies a newly completed graph layout", () => {
  expect(reconcileNodePositions(current, laidOut, true)).toEqual(laidOut);
});

it("preserves a dragged position during presentation-only updates", () => {
  expect(reconcileNodePositions(current, laidOut, false)).toEqual([
    { id: "one", position: { x: 0, y: 0 }, label: "updated" },
  ]);
});
