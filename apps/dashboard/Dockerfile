FROM node:26.10.0-alpine3.24@sha256:0b36e8c136b94cd4fcf02188228e76c31ad5872eef3fec8cbd2eee500cfd9e80 AS build
WORKDIR /app
ARG VITE_PLATFORM_API_BASE_URL=http://localhost:8080
ENV VITE_PLATFORM_API_BASE_URL=$VITE_PLATFORM_API_BASE_URL
COPY package.json package-lock.json* ./
RUN npm ci --fetch-retries=5 --fetch-retry-factor=2 \
    --fetch-retry-mintimeout=10000 --fetch-retry-maxtimeout=60000
COPY index.html tsconfig.json vite.config.ts ./
COPY src ./src
RUN npm run build

FROM node:26.10.0-alpine3.24@sha256:0b36e8c136b94cd4fcf02188228e76c31ad5872eef3fec8cbd2eee500cfd9e80
WORKDIR /app
RUN npm install -g serve@14.2.4 --fetch-retries=5 --fetch-retry-factor=2 \
        --fetch-retry-mintimeout=10000 --fetch-retry-maxtimeout=60000 \
    && rm -rf /usr/local/lib/node_modules/npm \
        /usr/local/lib/node_modules/corepack \
        /opt/yarn-v1.22.22 \
    && rm -f /usr/local/bin/npm /usr/local/bin/npx \
        /usr/local/bin/corepack /usr/local/bin/yarn /usr/local/bin/yarnpkg \
    && addgroup -S -g 10001 riskgraph \
    && adduser -S -u 10001 -G riskgraph riskgraph
COPY --from=build --chown=riskgraph:riskgraph /app/dist ./dist
USER 10001:10001
EXPOSE 5173
CMD ["serve", "-s", "dist", "-l", "5173"]
