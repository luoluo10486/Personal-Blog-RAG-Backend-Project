# Milvus Docker Demo

This directory keeps a standalone Milvus playground for local experiments only.

## Status

- The active RAG runtime in this repository now aligns with `RAgent` and uses `PostgreSQL + pgvector`.
- `luoluo-admin`, `luoluo-knowledge`, and `luoluo-rag-mcp` no longer depend on Milvus for the main retrieval path.
- You do not need this stack to run the current project.

## When to use it

Use this directory only if you want to:

- compare pgvector and Milvus behavior locally
- keep an old demo environment for historical reference
- experiment with Milvus outside the main runtime

## Start

```powershell
docker compose -f docker/milvus/docker-compose.yml up -d
```

## Endpoints

- Milvus gRPC: `127.0.0.1:19530`
- Attu: `http://127.0.0.1:8000`
- RustFS console: `http://127.0.0.1:9001`

## Main runtime reminder

For the actual application, use PostgreSQL with the `vector` extension enabled and keep:

```yaml
app:
  knowledge:
    vector:
      type: pgvector
      pg:
        schema: public
        table-name: t_knowledge_vector
```
