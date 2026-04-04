# Milvus Docker Demo

这个目录现在采用你提供的参考思路，使用 `RustFS + etcd + Milvus Standalone + Attu`。

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

- `etcd` 参数改成了 `--advertise-client-urls` 和 `--listen-client-urls`，避免单横线参数带来的兼容性问题。
- `depends_on` 改成基于健康检查，减少 Milvus 过早启动导致的连通性问题。
- 数据卷落到了仓库内 `docker/milvus/volumes`，更方便本地排查和清理。

## 跑通仓库里的检索链路

`luoluo-rag` 已经支持：

- 使用离线 `demo` embedding 生成向量
- 自动创建 Milvus collection
- 自动 upsert 示例 chunk
- 执行向量检索并返回结果

推荐配置：

```yaml
app:
  rag:
    enabled: true
    embedding-provider: demo
    demo-embedding-dimension: 64
    milvus:
      enabled: true
      uri: http://127.0.0.1:19530
      token: ""
      collection-name: rag_demo_chunks
```

集成测试入口：

```powershell
mvn -pl luoluo-rag -DMILVUS_IT_ENABLED=true -Dtest=MilvusEmbeddingSearchIntegrationTest test
```
