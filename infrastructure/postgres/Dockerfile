FROM golang:1.27.0-alpine3.23@sha256:3747dcba41c8b0db3211fda4db61638b980e17ac5bb3c94460a975a9cfe19395 AS gosu-builder

# gosu is pure Go. Rebuild release 1.19 at its immutable commit with the first
# fixed Go 1.25 toolchain for CVE-2025-68121 instead of shipping the stale
# helper embedded in the current official PostgreSQL image.
RUN apk add --no-cache git \
    && git init /src/gosu \
    && git -C /src/gosu fetch --depth 1 https://github.com/tianon/gosu.git 6456aaa0f3c854d199d0f037f068eb97515b7513 \
    && git -C /src/gosu checkout --detach FETCH_HEAD \
    && cd /src/gosu \
    && go mod download \
    && CGO_ENABLED=0 go build -trimpath -ldflags '-d -w' -o /go/bin/gosu . \
    && /go/bin/gosu --version \
    && /go/bin/gosu nobody true

# The upstream entrypoint drops to postgres after initializing ownership on a
# fresh named volume; USER postgres breaks that required initialization flow.
FROM postgres:18.6-alpine3.24@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873
COPY --from=gosu-builder /go/bin/gosu /usr/local/bin/gosu

# Keep the upstream entrypoint's initial root phase. It owns a fresh named volume,
# then immediately execs PostgreSQL as the postgres user through the rebuilt gosu.
# Setting USER postgres here prevents clean-volume initialization on Docker Desktop.
