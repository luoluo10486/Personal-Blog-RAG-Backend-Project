# RAG 与 RAgent 源码级对齐待办

## 1. 对齐范围

本文件用于记录当前仓库 `D:\develop\Personal-Blog-RAG-Backend-Project\ragent` 与参考仓库 `D:\develop\RAgent\ragent` 的源码级差异，以及后续对齐顺序。

对齐范围只聚焦 `ragent/` 子工程的 RAG 相关内容，默认排除登录、鉴权、用户中心等非 RAG 业务模块。

忽略生成物后（如 `target/`、`node_modules/`、`dist/`、`build/`），当前比对结果为：

- 共同文件：651 个
- 内容不同：46 个
- 仅当前仓库存在：23 个
- 仅参考仓库存在：25 个

结论很明确：

- `framework/`、`infra-ai/`、`resources/` 基本已经与参考仓库对齐。
- `frontend/` 只剩少量页面级差异。
- 真正的核心差异集中在 `bootstrap/` 和 `mcp-server/`。

## 2. 已基本对齐的部分

| 模块 | 状态 | 说明 |
| --- | --- | --- |
| `framework/` | 已对齐 | 源码级一致，属于可直接复用的公共基础能力。 |
| `infra-ai/` | 已对齐 | 模型路由、Embedding、Rerank 等基础实现已接近或一致。 |
| `resources/` | 已对齐 | 数据库、Docker、知识库样例等资源整体一致。 |
| `frontend/` | 基本对齐 | 只剩 2 个文件差异，主要是页面实现细节。 |

## 3. 仍然存在的核心差异

### 3.1 `bootstrap/` 差异最大

当前仓库与参考仓库在 `bootstrap/` 下存在 39 个内容差异文件、11 个仅当前存在文件、11 个仅参考仓库存在文件。

主要差异集中在以下几类：

- **限流实现**：当前仓库保留了自定义的 `ChatRateLimit` / `ChatRateLimitAspect` / `ChatQueueLimiter` 风格；参考仓库是 `ChatRateLimiterConfig`、`FairDistributedRateLimiter` 这套结构。
- **MCP 接入**：当前仓库是自定义 `MCPClient` / `HttpMCPClient` / `RemoteMCPToolExecutor` / `MCPRequest` / `MCPResponse` / `MCPTool`；参考仓库是 `McpClientAutoConfiguration` / `McpClientProperties` / `McpClientToolExecutor`。
- **检索与提示词**：当前仓库偏向 `MultiChannelRetrievalEngine`、`IntentDirectedSearchChannel`、`VectorGlobalSearchChannel`、本地 prompt 资源；参考仓库保留 `IntentGuidanceService`、`IntentResolver`、`StreamChatTraceRunner` 以及 `context-format.st`、`guidance-ambiguity-check.st`、`mcp-parameter-extract-user.st`。
- **包名与命名**：当前仓库存在 `MCP` 大写风格的类型名；参考仓库更多使用 `Mcp` 风格，命名一致性需要收敛。
- **配置差异**：`bootstrap/src/main/resources/application.yaml` 基本一致，但仍有细节差异，例如当前仓库 `rag.rate-limit.global.max-wait-seconds` 是 `3`，参考仓库是 `15`。

### 3.2 `mcp-server/` 架构差异明显

当前仓库的 `mcp-server/` 仍是自定义协议层：

- `core/`
- `endpoint/`
- `protocol/`
- `executor/`

参考仓库则更偏向官方 MCP SDK 直连：

- `mcp-server/config/McpServerConfig.java`
- `McpServer` / `McpSyncServer`
- `HttpServletStreamableServerTransportProvider`

这意味着两边不是“同一套实现的小改动”，而是 **接入方式不同**。如果目标是严格对齐参考仓库，`mcp-server/` 需要按参考仓库的 SDK 方式重构，而不是继续扩展当前自研 JSON-RPC/Registry 结构。

### 3.3 `frontend/` 只有局部差异

当前仓库与参考仓库仅差异两个文件：

- `frontend/src/components/chat/MessageList.tsx`
- `frontend/src/pages/admin/settings/SystemSettingsPage.tsx`

这部分优先级低于后端核心链路，建议放到后期统一处理。

### 3.4 根目录文档与资产仍有分叉

参考仓库额外包含：

- `mcp-server-architecture.md`
- `Ragent全项目串联总览.md`
- `StreamChatPipeline完整链路深度解析.md`
- `全链路追踪.md`

当前仓库则保留了自己的说明文档与部分图示。
这些文件不影响主链路运行，但会影响“源码级对齐”的叙述一致性，建议在核心代码收敛后再统一整理。

## 4. 建议的对齐策略

### P0：先锁定参考基线

1. 固定参考仓库 `D:\develop\RAgent\ragent` 的当前版本为基线。
2. 生成可重复执行的差异清单，至少按以下维度输出：
   - 文件路径
   - 类名 / 包名
   - 配置项
   - SQL 表结构
   - Controller 接口
3. 明确“保留当前增强”还是“严格跟参考仓库对齐”的边界，避免边改边分叉。

### P1：先收敛 `bootstrap/`

1. 对齐限流实现，统一到参考仓库的 `ChatRateLimiterConfig` / `FairDistributedRateLimiter` 风格。
2. 对齐 MCP 客户端层，统一类型命名、自动配置类、工具执行器和 prompt 提取逻辑。
3. 对齐 prompt 资源目录，按参考仓库保留 `context-format.st`、`guidance-ambiguity-check.st`、`mcp-parameter-extract-user.st`。
4. 对齐配置项默认值，尤其是限流、检索与 trace 相关参数。

### P2：重构 `mcp-server/`

1. 以参考仓库的官方 MCP SDK 接入方式为准，替换当前自研协议层。
2. 收敛执行器、工具注册、服务启动入口的命名与结构。
3. 如果必须保留当前实现，只能放在兼容层，不应作为主链路入口。

### P3：收尾前端与文档

1. 对齐 `frontend/` 的两个差异文件。
2. 统一根目录说明文档的叙述口径。
3. 清理不再需要的本地资产或中间产物说明。

## 5. 推荐执行顺序

1. 先做 `bootstrap/`，因为它决定 RAG 主链路是否还能继续稳定跑。
2. 再做 `mcp-server/`，因为它是外部工具接入的关键入口。
3. 然后处理 `frontend/` 的局部差异。
4. 最后统一文档、图示和说明文件。

## 6. 验收标准

对齐完成后，建议满足以下标准：

- 忽略生成物后，`framework/`、`infra-ai/`、`resources/` 保持一致。
- `bootstrap/` 仅保留有明确理由的差异，且差异点能说清“为什么不完全照搬”。
- `mcp-server/` 的启动方式、工具注册方式、协议层与参考仓库一致。
- `frontend/` 的两个页面差异消失。
- `mvn -DskipTests compile` 能通过。
