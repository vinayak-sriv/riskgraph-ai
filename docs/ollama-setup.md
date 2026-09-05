# Ollama Setup

The AI layer defaults to a local-network Ollama instance. In this project, Ollama
may run on a Kali VM while the application services run elsewhere.

## Environment

```bash
OLLAMA_BASE_URL=http://<kali-vm-ip>:11434
OLLAMA_MODEL=llama3.1:8b
```

## Kali VM

Start Ollama so it listens on the network:

```bash
OLLAMA_HOST=0.0.0.0:11434 ollama serve
```

Confirm from the host or container that will run `ai-validation-service`:

```bash
curl http://<kali-vm-ip>:11434/api/tags
```

## Contract

All Stage 7 and Stage 8 calls must use Ollama's `format` parameter with a JSON
Schema. Prompt-only JSON instructions are not enough.

The parsed response must still be validated before use because schema-constrained
output guarantees shape, not correctness.
