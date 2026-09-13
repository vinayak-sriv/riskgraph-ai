FROM golang:1.25.7-alpine3.23@sha256:f6751d823c26342f9506c03797d2527668d095b0a15f1862cddb4d927a7a4ced AS gosu-builder

# gosu is pure Go. Rebuild release 1.19 at its immutable commit with the first
# fixed Go 1.25 toolchain for CVE-2025-68121 instead of shipping the stale
# helper embedded in the current official PostgreSQL image.
RUN git init /src/gosu \
    && git -C /src/gosu fetch --depth 1 https://github.com/tianon/gosu.git 6456aaa0f3c854d199d0f037f068eb97515b7513 \
    && git -C /src/gosu checkout --detach FETCH_HEAD \
    && cd /src/gosu \
    && go mod download \
    && CGO_ENABLED=0 go build -trimpath -ldflags '-d -w' -o /go/bin/gosu . \
    && /go/bin/gosu --version \
    && /go/bin/gosu nobody true

FROM postgres:16.15-alpine3.24@sha256:cf78e76683b9ca8c5733cbbdce6c9262b45b6767934dd0a95e671f9a0fc20685
COPY --from=gosu-builder /go/bin/gosu /usr/local/bin/gosu
