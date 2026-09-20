FROM python:3.14.6-slim-bookworm@sha256:4c92ffcde4dd6f1ff72a24518f49fd4990b27134987dfa31a733badde66df9f8
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt \
    && groupadd --system --gid 10001 riskgraph \
    && useradd --system --uid 10001 --gid riskgraph --no-create-home riskgraph
COPY --chown=riskgraph:riskgraph app ./app
USER 10001:10001
EXPOSE 8083
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8083"]
