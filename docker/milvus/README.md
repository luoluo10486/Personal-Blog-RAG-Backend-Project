# Milvus Docker Demo

这个目录使用 `RustFS + etcd + Milvus Standalone + Attu` 提供本地 Milvus 环境。

## 启动

```powershell
docker compose -f docker/milvus/docker-compose.yml up -d
```

## 访问

Milvus gRPC:

```text
127.0.0.1:19530
```

Milvus health:

```powershell
curl http://127.0.0.1:9091/healthz
```

Attu:

```text
http://127.0.0.1:8000
```

RustFS console:

```text
http://127.0.0.1:9001
```

## 和参考示例相比的调整

- `etcd` 参数改成了 `--advertise-client-urls` 和 `--listen-client-urls`，避免单横线参数带来的兼容性问题
- `depends_on` 改成基于健康检查，减少 Milvus 过早启动导致的连通性问题
- 数据卷落到了仓库内 `docker/milvus/volumes`，更方便本地排查和清理

## 当前仓库里的 Milvus 定位

当前仓库已经把正式 RAG 主链路放到了 `luoluo-knowledge`，而 `luoluo-rag` 只保留为 Demo 模块。

因此这里需要区分两件事：

- 正式链路：`luoluo-knowledge`
- Demo / 实验链路：`luoluo-rag`

## 正式链路和 Milvus 的关系

当前正式链路会读取 `app.knowledge.vector.milvus.*` 配置，并据此解析知识库向量空间、集合名和健康信息。

但要注意：

- 这并不等于当前正式检索已经直接走 Milvus 向量召回
- 目前 `luoluo-knowledge` 默认检索实现仍然是 `JdbcKnowledgeRetriever`
- Milvus 配置当前更多用于正式链路的向量空间命名和后续扩展准备

推荐先把正式配置保持清晰：

```yaml
app:
  knowledge:
    enabled: true
    jdbc:
      enabled: true
    vector:
      type: milvus
      milvus:
        enabled: true
        uri: http://127.0.0.1:19530
        token: ""
        collection-prefix: kb_
    defaults:
      collection-name: knowledge_default_store
```

## 如果你还要继续跑旧 Demo

`luoluo-rag` 目录仍然保留，可以继续作为 Milvus Demo 实验场使用，只是它已经不再是父工程聚合模块。

如果你想单独跑旧 Demo 相关测试，需要显式指定该模块，例如：

```powershell
.\ragent\mvnw.cmd -f luoluo-rag\pom.xml -DMILVUS_IT_ENABLED=true -Dtest=MilvusEmbeddingSearchIntegrationTest test
```

## 当前更推荐的验证方式

如果你关注的是正式主链路，建议优先验证：

- `luoluo-admin` 启动后 `/luoluo/knowledge/health`
- `POST /luoluo/knowledge/ask`
- `POST /luoluo/knowledge/document/parse`
- `POST /luoluo/knowledge/document/chunk`

如果你关注的是旧实验链路，再回到 `luoluo-rag` 模块单独处理。
