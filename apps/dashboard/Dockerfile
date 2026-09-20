FROM node:22.23.2-alpine3.24@sha256:c610fcdfb1d5b4740dd70c284ed3cb16bb857e0f7166196e36a5501df7a3aa32 AS build
WORKDIR /app
ARG VITE_PLATFORM_API_BASE_URL=http://localhost:8080
ENV VITE_PLATFORM_API_BASE_URL=$VITE_PLATFORM_API_BASE_URL
COPY package.json package-lock.json* ./
RUN npm ci
COPY index.html tsconfig.json vite.config.ts ./
COPY src ./src
RUN npm run build

FROM node:22.23.2-alpine3.24@sha256:c610fcdfb1d5b4740dd70c284ed3cb16bb857e0f7166196e36a5501df7a3aa32
WORKDIR /app
RUN npm install -g serve@14.2.4 \
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
