# Validation sandbox boundary

This directory documents the shared sandbox policy. The runnable demonstration
application is maintained under [`samples/sandbox`](../../../samples/sandbox/), and
commit-bound images are prepared by `tools/dev/build_sandboxes.py`.

Validation is restricted to registered local Docker images and a fixed HTTP probe.
Sandboxes run with blocked external egress, read-only filesystems, dropped
capabilities, non-root users, resource limits, readiness deadlines, and mandatory
cleanup. The runner must never target a live, public, or third-party system.
