# 当前 RAG 流程与 Demo 说明

本文档描述当前仓库里正式 RAG 主链路和 Demo 链路的最新分工，避免继续把 `luoluo-rag` 和 `luoluo-knowledge` 混在一起理解。

## 1. 一句话结论

- 正式 RAG 主链路现在放在 `luoluo-knowledge`
- `luoluo-admin` 与 `luoluo-rag-mcp` 都已经切到 `luoluo-knowledge`
- `luoluo-rag` 继续保留在仓库里，作为 Demo / Playground，不删文件，但不再是正式运行依赖

## 2. 调整后的模块定位

| 模块 | 当前定位 | 是否进入正式运行时 |
| --- | --- | --- |
| `luoluo-knowledge` | 正式知识库 RAG 主链路 | 是 |
| `luoluo-admin` | 统一启动入口 | 是 |
| `luoluo-rag-mcp` | 基于正式知识库链路的 MCP 服务 | 是 |
| `luoluo-rag` | 老的 Demo / 实验链路 | 否，默认不进入父工程聚合构建 |

当前根 `pom.xml` 已经不再聚合 `luoluo-rag`，因此：

- 正式启动 `luoluo-admin` 时，不会再把 `luoluo-rag` 打进运行包
- 正式 MCP 服务也不会再依赖 `luoluo-rag`
- `luoluo-rag` 目录仍然保留，后续如果你想继续做 Demo 实验，仍然可以单独进入该模块处理

## 3. 正式 RAG 链路放在哪里

正式链路现在以 `luoluo-knowledge` 为中心，原因是这个模块的职责层次更清晰，已经具备：

- `application`：RAG 应用编排
- `controller`：正式对外接口
- `service.retrieval`：检索实现
- `service.generation`：答案生成
- `service.vector`：向量空间命名与解析
- `service.document`：文档解析与切块

对应的正式入口类主要是：

- `KnowledgeRagController`
- `KnowledgeDocumentController`
- `KnowledgeRagApplicationService`
- `KnowledgeRetriever`
- `KnowledgeAnswerGenerator`
- `KnowledgeVectorSpaceResolver`

## 4. 正式对外接口

当前正式链路优先使用 `/luoluo/knowledge/*`：

- `GET /luoluo/knowledge/health`
- `POST /luoluo/knowledge/ask`
- `POST /luoluo/knowledge/document/parse`
- `POST /luoluo/knowledge/document/chunk`

为了兼容旧命名，还保留了 `/luoluo/rag/*` 这组别名：

- `GET /luoluo/rag/health`
- `POST /luoluo/rag/ask`
- `POST /luoluo/rag/document/parse`
- `POST /luoluo/rag/document/chunk`

这里要特别注意：

- 现在 `/luoluo/rag/*` 是正式知识库链路的兼容路由
- 旧 Demo 风格的 `/luoluo/rag/demo/*` 不再属于当前 `luoluo-admin` 正式运行时

## 5. 正式 `ask` 链路怎么跑

当前正式问答链路如下：

1. `KnowledgeRagController.ask()` 接收 `KnowledgeAskRequest`
2. `KnowledgeRagApplicationService.ask()` 统一编排
3. 先规范化 `baseCode` 与 `topK`
4. `KnowledgeVectorSpaceResolver` 解析知识库编码、集合名和向量空间信息
5. `KnowledgeRetriever.retrieve()` 执行检索
6. `KnowledgeAnswerGenerator.generate()` 基于检索结果生成答案
7. 组装 `KnowledgeAskResponse`，返回答案、引用和 trace

当前正式链路返回的核心数据包括：

- `answer`：最终回答
- `baseCode`：本次命中的知识库编码
- `citations`：参与回答的片段列表
- `trace`：当前链路的基础执行轨迹

## 6. 当前正式检索实现是什么

这点很重要，容易被误解。

虽然 `luoluo-knowledge` 已经引入了向量空间解析和 Milvus 相关配置，但当前正式检索默认实现仍然是 `JdbcKnowledgeRetriever`，不是直接用 Milvus 做主检索。

当前默认行为是：

- 在存在 `JdbcTemplate` 时，`JdbcKnowledgeRetriever` 会生效
- `app.knowledge.jdbc.enabled` 默认为开启
- 检索会查询 `rag_knowledge_chunk`、`rag_knowledge_document`、`rag_knowledge_base`
- 先做基于 token 的候选召回与打分
- 如果有 `RerankService` 且开启了重排，再执行可选 rerank

也就是说：

- 正式主链路的位置已经切到了 `luoluo-knowledge`
- 但正式主链路当前的检索实现，还是“JDBC 检索 + 可选 rerank”
- `app.knowledge.vector.*` 现在更多用于知识库集合命名、健康信息展示，以及后续向量化扩展

## 7. 当前正式生成实现是什么

当前正式生成默认由 `TemplateKnowledgeAnswerGenerator` 负责：

- 如果 `luoluo-infra-ai` 提供了可用的 `LLMService`，就走 LLM 生成答案
- 如果当前 AI 服务不可用，就回退为“返回若干最相关片段摘要”

因此当前正式链路已经具备：

- 有检索结果时生成答案
- AI 不可用时的兜底返回
- 基础引用信息回传

## 8. 文档解析与切块现在放在哪里

正式文档相关能力也已经放进 `luoluo-knowledge`：

- `KnowledgeDocumentController`
- `TikaDocumentParseService`
- `KnowledgeDocumentChunkService`

当前暴露的接口是：

- `POST /luoluo/knowledge/document/parse`
- `POST /luoluo/knowledge/document/chunk`

兼容别名：

- `POST /luoluo/rag/document/parse`
- `POST /luoluo/rag/document/chunk`

当前边界也需要说清楚：

- 现在 parse 和 chunk 已经属于正式模块
- 但还没有自动接上完整的 `parse -> chunk -> embedding -> 入库 -> 检索` 闭环
- `KnowledgeIngestionService` 目前主要负责入库计划编排信息，还不是完整自动化入库流水线

## 9. MCP 链路现在怎么挂

`luoluo-rag-mcp` 当前已经切换到正式知识库链路：

- 应用扫描包改为 `com.personalblog.ragbackend.knowledge`
- 配置扫描改为 `app.knowledge.*`
- `RagMcpTools` 现在直接依赖：
  - `KnowledgeRagApplicationService`
  - `KnowledgeRetriever`
  - `KnowledgeVectorSpaceResolver`
  - `KnowledgeDocumentChunkService`

这意味着 MCP 侧的查询、生成、切块，已经不再经过 `luoluo-rag` Demo 服务。

## 10. Demo 模块现在的定位

`luoluo-rag` 现在建议理解为：

- 保留下来的实验田
- 旧接口、旧思路、旧检索实验的承载目录
- 不再参与正式运行时依赖

因此后续处理原则可以简单定成：

- 正式能力往 `luoluo-knowledge` 放
- Demo 试验、临时验证、玩法原型可以继续放 `luoluo-rag`
- 不要再让 `luoluo-admin` 或 `luoluo-rag-mcp` 反向依赖 `luoluo-rag`

## 11. 当前配置口径

当前推荐的配置分工如下：

- `app.knowledge.*`：正式知识库链路配置
- `app.rag.*`：老 Demo 链路配置，保留兼容，不再作为正式入口
- `app.ai.*`：通用 AI 提供方与模型配置

如果你后续再看配置文件，优先关注的是：

- `app.knowledge.enabled`
- `app.knowledge.default-base-code`
- `app.knowledge.jdbc.enabled`
- `app.knowledge.search.*`
- `app.knowledge.vector.*`
- `app.ai.*`

## 12. 下一步最值得继续补的地方

如果目标是把正式知识库链路继续做扎实，当前最值得补的是：

1. 把 `parse -> chunk -> embedding -> 向量入库 -> ask 检索` 连成闭环
2. 在 `luoluo-knowledge` 内补真正的向量召回实现，而不是只保留向量空间配置
3. 明确正式知识库的数据入库流程和数据表约束
4. 视需要再决定是否保留或继续弱化 `luoluo-rag` Demo 的独立价值
