FROM node:22-alpine AS build
WORKDIR /app
ARG VITE_PLATFORM_API_BASE_URL=http://localhost:8080
ENV VITE_PLATFORM_API_BASE_URL=$VITE_PLATFORM_API_BASE_URL
COPY package.json package-lock.json* ./
RUN npm install
COPY index.html tsconfig.json vite.config.ts ./
COPY src ./src
RUN npm run build

FROM node:22-alpine
WORKDIR /app
RUN npm install -g serve
COPY --from=build /app/dist ./dist
EXPOSE 5173
CMD ["serve", "-s", "dist", "-l", "5173"]
