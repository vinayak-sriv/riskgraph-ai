# Docker assets

RiskGraph keeps its primary Compose files in [`infrastructure/`](../) and each
service Dockerfile beside its source. This directory documents shared Docker
boundaries that do not belong to a single service.

The validation design requires:

- registered, locally built sandbox images;
- a private Docker-in-Docker daemon;
- fixed probe commands and an internal validation network;
- no host Docker socket in application containers;
- CPU, memory, process, time, and cleanup limits; and
- no live, public, or third-party validation targets.

See [`docker-compose.validation.yml`](../docker-compose.validation.yml) for the
implemented runtime topology.
