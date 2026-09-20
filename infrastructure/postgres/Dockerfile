FROM golang:1.25.7-alpine3.23@sha256:f6751d823c26342f9506c03797d2527668d095b0a15f1862cddb4d927a7a4ced AS gosu-builder

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
FROM postgres:18.4-alpine3.24@sha256:9a8afca54e7861fd90fab5fdf4c42477a6b1cb7d293595148e674e0a3181de15
COPY --from=gosu-builder /go/bin/gosu /usr/local/bin/gosu

# Keep the upstream entrypoint's initial root phase. It owns a fresh named volume,
# then immediately execs PostgreSQL as the postgres user through the rebuilt gosu.
# Setting USER postgres here prevents clean-volume initialization on Docker Desktop.
