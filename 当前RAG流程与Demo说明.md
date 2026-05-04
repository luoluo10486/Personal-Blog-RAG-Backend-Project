# 当前 RAG 流程与 Demo 说明

## 结论

- 正式 RAG 主链路在 `luoluo-knowledge`
- `luoluo-admin` 和 `luoluo-rag-mcp` 都已对齐正式链路
- `luoluo-rag` 只保留为 Demo / Playground，不再参与正式运行依赖

## 正式链路

`luoluo-knowledge` 现在覆盖：

- `application` 编排
- `controller` 对外接口
- `service.document` 文档解析与切块
- `service.ingest` 入库计划、预览、正式入库
- `service.retrieval` 检索聚合
- `service.vector` 向量空间与向量存取
- `service.generation` 答案生成

正式入口：

- `KnowledgeRagController`
- `KnowledgeDocumentController`
- `KnowledgeRagApplicationService`
- `KnowledgeDocumentApplicationService`

## 流程

### 预览

`PLAN_ONLY`:

- 只生成入库计划
- 不解析文件
- 不切块

`PREVIEW`:

- 生成计划
- 解析文件
- 切块
- 不落库、不写向量

### 正式入库

`INGEST`:

- 生成计划
- 解析文件
- 切块
- 持久化文档、chunk、vector ref
- 生成 embedding
- 写入向量库
- 回写入库结果

## 检索

当前正式检索链路由 `KnowledgeRetrievalEngine` 聚合：

- `JdbcKnowledgeRetriever`
- `VectorKnowledgeRetriever`
- `NoopKnowledgeRetriever`

再经过：

- 去重
- 置信度过滤
- 可选 rerank

## MCP

`luoluo-rag-mcp` 现在直接依赖正式模块，并使用：

- `app.ai.*`
- `app.knowledge.*`

## Demo

`luoluo-rag` 只保留旧实验代码，不再作为正式链路入口。
