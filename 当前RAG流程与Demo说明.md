# 当前 RAG 流程与 Demo 说明（与最新代码对齐）

本文档基于当前仓库 `luoluo-rag` 模块的最新实现整理，目标是让你可以“按调用链”审查系统现在到底做到了哪一步、每条接口内部到底怎么跑、哪些边界仍然缺失。

文档重点聚焦：

- 现在的真实能力与真实边界（不夸大、不假设）
- 接口视角的调用链拆解（入口 -> service -> 外部依赖 -> 返回）
- 你后续继续补全闭环时，应该从哪一层往下加

---

## 0. 一句话结论（当前阶段定位）

当前 `luoluo-rag` 已经是“带入口意图路由的多能力 RAG Demo”：

- `knowledge`：走 RAG 检索 +（可选）function call 核验 + 引用回答
- `chitchat`：直接走普通聊天模型，不进检索
- `clarification`：直接返回澄清引导，不进检索
- `tool`：能识别出来并拦截提示，但尚未真正接入 MCP/业务工具执行

同时：

- 检索数据源仍以 `demo chunks` 为主（真实文档入库链路尚未自动接入 `generate`）
- `history` 已支持参与“意图分类”，但尚未进入“Query 改写 + 多轮检索”的完整链路
- 已新增 `/evaluate` 用于离线/联调评测（可选启用 LLM-as-Judge）

---

## 1. 模块与关键类（按职责拆）

### 1.1 控制器入口

- `RagDemoController`
  - `/health` 配置摘要
  - `/chat` 普通聊天
  - `/chat/stream` SSE 流式聊天
  - `/embedding/search` 检索 demo
  - `/generate` RAG 生成（入口意图路由 + knowledge 分支）
  - `/evaluate` 评测（汇总指标 + bad case 结果）
- `DocumentController`
  - `/document/parse` 文档解析
  - `/document/chunk` 文档切分

### 1.2 核心服务

- `SiliconFlowChatDemoService`
  - 负责普通聊天与 SSE 流式聊天（含 SSE 事件解析、增量回调、usage 处理、容错）
- `SiliconFlowEmbeddingDemoService`
  - 负责 embedding + 粗召回 +（可选）rerank（目前默认 demo chunks 数据源）
- `RagGenerationDemoService`
  - 负责 `/generate` 主链路：先意图分类，再路由；knowledge 分支拼上下文 + 触发 function call（可选）+ 引用解析
- `IntentClassifierService`
  - 规则优先 + 小模型兜底的意图分类（`knowledge/tool/chitchat/clarification`）
- `RagEvaluationService`
  - 评测入口：意图路由准确率、Hit/MRR、生成三维评分（可选 judge）、bad case 归因

### 1.3 配置入口

- `RagProperties`：`app.rag.*` 配置聚合
  - `intent.*`：意图分类小模型配置
  - `evaluation.*`：评测 judge 模型配置
- `RagHttpClientConfig`：统一 HttpClient 超时配置（connect/read）

---

## 2. 配置总览（你审查时最应该先看的）

以下为关键配置的逻辑含义（字段名以 `RagProperties` 为准）：

- `app.rag.enabled`
  - 总开关；关闭时多数接口会返回“演示功能未启用”
- `app.rag.api-url`
  - SiliconFlow chat completions 地址（普通聊天、流式聊天、judge 评分都会用到）
- `app.rag.api-key`
  - SiliconFlow API key；未配置会阻止调用外部模型
- `app.rag.model`
  - 默认聊天模型（chat / generate / 流式 chat 的生成模型）
- `app.rag.embedding-provider`
  - embedding 提供方：`demo`（本地哈希向量）或 `siliconflow`
- `app.rag.embedding-model`
  - embedding 模型名（当 provider=siliconflow 时生效）
- `app.rag.retrieval.mode`
  - `DENSE_ONLY` / `SPARSE_ONLY` / `HYBRID`
- `app.rag.rerank.enabled / provider / model`
  - 是否启用二阶段重排；provider 可为 demo 或真实 API
- `app.rag.intent.enabled / model / temperature / maxTokens`
  - 意图分类是否启用 LLM 兜底、使用哪个小模型
- `app.rag.evaluation.judgeModel / judgeTemperature / judgeMaxTokens`
  - LLM-as-Judge 的模型配置（`runJudge=true` 才会触发）

---

## 3. 对外接口总览（最新代码）

当前对外主要接口为 8 个：

- `GET  /luoluo/rag/demo/health`
- `POST /luoluo/rag/demo/chat`
- `POST /luoluo/rag/demo/chat/stream`（SSE）
- `POST /luoluo/rag/demo/embedding/search`
- `POST /luoluo/rag/demo/generate`（带意图路由）
- `POST /luoluo/rag/demo/evaluate`（评测）
- `POST /luoluo/rag/document/parse`
- `POST /luoluo/rag/document/chunk`

下面按“审查顺序”逐个拆解：先看入口路由与 SSE，再看 generate，再看 evaluate，最后看文档链路。

---

## 4. `GET /luoluo/rag/demo/health`（配置摘要）

### 4.1 做什么

这是一个“配置级健康检查接口”，用于把当前关键配置返回给前端/联调人员快速确认：

- RAG 是否启用
- chat/embedding/rerank 的模型和开关
- Milvus 与检索模式配置等

它不会真正探测外部服务可达性。

### 4.2 调用链

`RagDemoController.health()` -> 读取 `RagProperties` -> 组装 `RagDemoHealthResponse` -> 返回

---

## 5. `POST /luoluo/rag/demo/chat`（普通聊天）

### 5.1 做什么

纯聊天：把 system/user 组装成标准消息列表，调用 SiliconFlow chat completions，等待完整答案返回。

### 5.2 调用链

`RagDemoController.chat()` -> `SiliconFlowChatDemoService.chat()` -> `sendChatRequest()` -> `parseResponse()`

### 5.3 关键返回字段

- `answer`：最终回答文本
- `promptTokens/completionTokens/totalTokens`：usage（若平台返回）

---

## 6. `POST /luoluo/rag/demo/chat/stream`（SSE 流式聊天）

### 6.1 做什么

返回一个 SSE 连接，服务端持续推送事件给前端，以实现“模型逐 chunk 输出”的体验。

### 6.2 输入与输出（审查时先对齐“契约”）

- 请求体：`RagDemoChatRequest`
- 关键字段：`systemPrompt`（可选）、`message`（必填）
- 响应：`text/event-stream`，由后端创建并返回 `SseEmitter`

### 6.3 事件协议（后端对前端的固定协议）

后端不会把 SiliconFlow 的原始 SSE 事件原样透传给前端，而是统一成 4 种事件，便于前端按固定结构消费：

| event | data 结构 | 什么时候发 |
| --- | --- | --- |
| `delta` | `string` | 解析到上游 `choices[0].delta.content` 的增量文本时 |
| `usage` | `{ promptTokens, completionTokens, totalTokens }` | 上游 chunk 内包含 `usage` 时（不保证每次都有） |
| `complete` | `RagDemoChatResponse` | 整条流解析结束后，汇总答案与 token 消耗 |
| `error` | `{ message }` | 上游 HTTP 非 2xx、超时、IO 异常等 |

### 6.4 调用链（逐步，对照真实代码）

1. `RagDemoController.streamChat(request)`
2. `SiliconFlowChatDemoService.streamChat(request)`
3. `validateAvailability()`：检查 `app.rag.enabled/apiKey` 等关键配置，避免“没配好还硬调模型”
4. `new SseEmitter(timeoutMs)`：超时时间 = `readTimeoutSeconds + 5s`
5. 注册 emitter 生命周期回调
6. `CompletableFuture.runAsync(() -> streamChatInternal(request, emitter))`：异步执行真正的流式请求（Controller 线程立刻返回 emitter）
7. `streamChatInternal(request, emitter)`
8. `buildHttpRequest(request, true)`：构造对 SiliconFlow 的 HTTP 请求，关键点是 `stream=true`
9. `httpClient.send(..., BodyHandlers.ofInputStream())`：得到上游 `InputStream`
10. 上游状态码非 2xx：读取 error body 文本，`sendErrorEvent()`，然后 `emitter.complete()`
11. 上游状态码 2xx：进入 `parseStreamingResponse(responseBody, listener)`
12. `parseStreamingResponse()` 负责做两件事
13. 解析 SSE 事件块（空行分隔），把每个事件块的 `data:` 行收集成 `dataLines`
14. `parseSseEvent(dataLines)`：把 `dataLines` 合并成一段 JSON 文本后解析成 `ParsedChunk`
15. 把 `ParsedChunk.content` 通过 `listener.onDelta()` 推给 `sendDeltaEvent()`，同时累加到 `fullContent`
16. 把 `ParsedChunk.usage` 通过 `listener.onUsage()` 推给 `sendUsageEvent()`，同时更新 token 统计
17. 收到上游 `[DONE]`（done marker）后结束循环，组装最终 `RagDemoChatResponse`
18. `streamChatInternal()` 收到最终响应后发送 `event=complete`，最后 `emitter.complete()`

### 6.5 上游 SSE 解析细节（为什么它“真的能流”）

`parseStreamingResponse()` 的读取逻辑是“事件块级别”的，而不是“见到一行 data 就当成一个 JSON”：

1. 逐行 `readLine()`
2. `line.isBlank()` 视为一个 SSE 事件块结束，触发一次 `parseSseEvent(dataLines)`
3. `line.startsWith(":")` 视为注释行，忽略
4. `line.startsWith("data:")` 才加入 `dataLines`

`parseSseEvent()` 的容错点：

1. `data == "[DONE]"` 返回 `ParsedChunk.doneMarker()`，告诉上层结束
2. 单个 chunk JSON 解析失败：只打 `warn` 并返回 null，上层会跳过，不会炸整条流
3. `delta.content` 同时兼容 `string` 与 `array` 两种格式（用 `extractDeltaContent()` 统一抽取）

### 6.6 这条链路做了哪些“生产级容错”

1. `SseEmitter` 生命周期处理：`onTimeout/onError/onCompletion`
2. 超时会走 `completeStreamWithError()`：先发送 `event=error`（统一结构），再 `complete()`
3. 上游 HTTP 非 2xx：把响应体读出来回给前端，避免“前端只看到断流但不知道原因”
4. `sendDeltaEvent/sendUsageEvent/sendErrorEvent` 发送失败会抛 `UncheckedIOException`，由上层捕获并结束连接

### 6.7 常见“看起来不流”的原因（审查时要记住）

即使后端 SSE 正确，如果链路中间代理启用了响应缓冲（Nginx/CDN/网关），前端可能看到“攒一会儿一次性吐出来”。

这属于链路问题，不一定是代码问题。

---

## 7. `POST /luoluo/rag/demo/embedding/search`（检索 demo）

### 7.1 做什么

对用户 query 做 embedding + 粗召回（Dense/Sparse/Hybrid）+（可选）rerank，返回最终 Top-K 列表。

当前实现默认以 `demo chunks` 为数据源（代码内置），Milvus 可选启用。

### 7.2 调用链（核心顺序）

`RagDemoController.embeddingSearch()` -> `SiliconFlowEmbeddingDemoService.search()` -> embed(query/chunks) -> coarse recall -> rerank（可选）-> 返回 `RagEmbeddingSearchResponse`

### 7.3 关键返回字段

- `results[*].metadata.doc_id/title/category`：后续 `generate` 会用这些组织引用信息
- `recallMode`：DENSE/SPARSE/HYBRID
- `rerankApplied/provider/model`：是否走了二阶段重排

---

## 8. `POST /luoluo/rag/demo/generate`（入口意图路由 + knowledge 分支 RAG）

### 8.1 请求结构（最新）

`RagGenerationRequest` 现在包含：

- `query`：必填
- `topK`：可选（1~20）
- `systemPrompt`：可选（覆盖默认 systemPrompt）
- `history`：可选（用于意图分类；目前还未进入 query rewrite）

### 8.2 返回结构（审查时重点看哪些字段）

`RagGenerationResponse` 里这些字段最关键：

| 字段 | 你用它确认什么 |
| --- | --- |
| `recallMode` | 是否真的进入检索（`SKIPPED` 表示没进检索） |
| `retrievedChunkCount` | 召回了几条 chunk（0 代表无资料或跳过） |
| `rerankApplied/rerankProvider/rerankModel` | 是否走了二阶段重排 |
| `functionCallApplied/calledTools` | 是否触发了 function call 核验，以及调用了哪些工具 |
| `citations` | 是否解析到 `[1][2]...` 并能回填引用内容 |
| `model/requestId/finishReason/usage` | 生成模型与 token 消耗，用于对齐“你看到的答案来自哪次调用” |

### 8.2 为什么要先做意图分类

因为现在系统不再假设“所有输入都是知识问答”。入口会先判断：

- `knowledge`：应该进 RAG
- `chitchat`：直接聊天
- `clarification`：先澄清
- `tool`：应走工具链路（当前仅提示，尚未真正调用 MCP）

### 8.3 调用链（真实顺序，逐步）

1. `RagDemoController.generate(request)`
2. `RagGenerationDemoService.generate(request)`
3. `query = request.query().trim()`
4. `classifyIntent(request, query)`
5. `IntentClassifierService.classify(request.history(), query)`：规则优先，未命中再走小模型兜底
6. `switch(intent)` 路由到不同分支（下面用“分支对照表”讲清楚）

分支对照表（你审查返回时直接按这张表对齐）：

| intent | 实际执行的方法 | 是否检索 | 返回特征 |
| --- | --- | --- | --- |
| `knowledge` | `generateKnowledgeResponse()` | 是 | `recallMode != SKIPPED` 且 `retrievedChunkCount > 0`（一般） |
| `chitchat` | `generateChitchatResponse()` | 否 | `recallMode=SKIPPED`、`citations=[]` |
| `clarification` | `buildRoutedResponse(CLARIFICATION_ANSWER)` | 否 | `model=intent-router`、`recallMode=SKIPPED` |
| `tool` | `buildRoutedResponse(TOOL_ROUTE_ANSWER)` | 否 | `model=intent-router`、`recallMode=SKIPPED` |

### 8.4 knowledge 分支内部：检索 +（可选）function call + 引用回答

knowledge 才会进入 RAG 主链路：

1. `retrieval = SiliconFlowEmbeddingDemoService.search(new RagEmbeddingSearchRequest(query, topK))`
2. `chunks = toRetrievedChunks(retrieval.results())`：把 `metadata.doc_id/title/category/source_url` 转成内部 `RetrievedChunk`
3. `chunks.isEmpty()` 时直接返回兜底答案 `FALLBACK_ANSWER`（此时 `citations=[]`，`retrievedChunkCount=0`）
4. 组装 prompt 与工具入参（关键中间产物）
5. `systemPrompt = resolveSystemPrompt(request.systemPrompt())`
6. `functionCallPrompt = buildFunctionCallPrompt(systemPrompt)`：在 systemPrompt 后追加“工具使用说明 + 强约束提示”
7. `chunkCatalog = buildChunkCatalog(chunks)`：只包含“标题/分类/摘要”的目录，避免第一轮就塞满内容
8. `toolUserMessage = buildToolUserMessage(chunkCatalog, query)`
9. `tools = buildFunctionTools()`：注册两个本地函数工具 `listRetrievedChunks/getRetrievedChunkByIndex`
10. 第一轮 `firstRound = siliconFlowChatDemoService.chatWithTools(functionCallPrompt, toolUserMessage, tools)`
11. `functionCallApplied = !firstRound.toolCalls().isEmpty()`，`calledTools` 取 toolCalls 的 name 去重列表
12. 若触发 function call：执行与回填
13. `toolResults = executeToolCalls(firstRound.toolCalls(), chunks)`：在服务端本地把 toolCalls 变成结构化 JSON 结果
14. `chatResponse = siliconFlowChatDemoService.completeToolChat(functionCallPrompt, toolUserMessage, toolCalls, toolResults)`
15. 若未触发 function call：回退“普通 RAG 直答”
16. `context = buildContext(chunks)`：把完整 chunk 内容拼成【参考资料】块（带编号）
17. `chatResponse = siliconFlowChatDemoService.chat(new RagDemoChatRequest(systemPrompt, buildUserMessage(context, query)))`
18. `citations = parseCitations(chatResponse.answer(), chunks)`：用正则从答案里抓 `[1][2]...` 并回填 chunk 原文
19. 组装并返回 `RagGenerationResponse`（包含 recall/rerank/function-call/citations 等审查字段）

重要审查点（非常容易被误解）：

1. `citations` 不是“检索到了就一定有”，而是“模型答案里写了 `[n]` 才能解析出来”
2. 当前 `history` 只用于意图分类，不参与检索；knowledge 检索的 query 就是用户原始 `query`

### 8.5 非 knowledge 分支返回长什么样（便于你审查）

当走 `chitchat/clarification/tool` 分支时，返回会有共同特征：

- `recallMode = SKIPPED`
- `retrievedChunkCount = 0`
- `citations = []`

其中 `tool/clarification` 会返回：

- `model = intent-router`

这让前端可以快速识别“没进检索，不要期待 citations”。

---

## 9. `POST /luoluo/rag/demo/evaluate`（评测接口）

### 9.1 为什么加这个接口

当系统具备“入口路由 + 检索 + 生成”后，靠感觉优化会非常痛苦。

`/evaluate` 提供一条“可回归”的评测路径，帮助你：

- 验证意图路由是否稳定
- 评估 knowledge 分支的 Hit/MRR
- 可选用 judge 模型评估忠实度/相关性/正确率
- 输出 bad case 与根因归因（路由/检索/生成/知识库）

### 9.2 请求结构

`RagEvaluationRequest`：

- `cases`：可选；不传则使用服务端默认评测集
- `runJudge`：是否启用 LLM-as-Judge（true 会额外调用 judge 模型）
- `topK`：评测时的 topK

### 9.3 调用链（核心顺序）

1. `RagDemoController.evaluate(request)`：如果 request 为空会用默认 `runJudge=false/topK=3`
2. `RagEvaluationService.evaluate(request)`
3. 选择数据集：`request.cases()` 为空则 `buildDefaultDataset()`，否则用传入 cases
4. 逐条执行 `evaluateCase(evalCase, topK, runJudge)` 并收集 `caseResults`
5. 汇总 `buildSummary(caseResults)` 输出 summary

`evaluateCase()` 的真实顺序（每一步做什么）：

1. `query = safeText(evalCase.query())`
2. `intentClassifierService.classify(List.of(), query)`：注意这里评测用的是“空 history”
3. `predictedIntent = intentResult.intent()`，`routeMatched = expectedIntent.equalsIgnoreCase(predictedIntent)`
4. 若 `predictedIntent == knowledge` 才执行检索评估
5. `retrieval = embeddingDemoService.search(new RagEmbeddingSearchRequest(query, topK))`
6. `retrievedDocIds = retrieval.results().map(metadata.doc_id)`：注意依赖 `metadata.doc_id` 字段
7. `hit = calculateHit(retrievedDocIds, relevantDocIds)`（Top-K 是否命中）
8. `reciprocalRank = calculateReciprocalRank(...)`（第 1 个命中的倒数排名）
9. 无论 predictedIntent 是什么，都会再跑一次真实生成
10. `generation = ragGenerationDemoService.generate(new RagGenerationRequest(query, topK, null))`
11. `runJudge=true` 时触发 LLM-as-Judge（走你配置的 `evaluation.judgeModel`）
12. `faithfulness` 只在 `predictedIntent==knowledge` 时评分（避免对非检索分支做“基于 chunk 的忠实度”）
13. `relevancy/correctness` 对所有 case 都会评分（它们不依赖 chunks）
14. `rootCause = determineRootCause(routeMatched, hit, faithfulness, correctness)`：用启发式把 bad case 归因到路由/检索/生成/知识库

`determineRootCause()` 的归因规则（按优先级）：

1. `routeMatched=false` -> `路由问题`
2. `hit=false` -> `检索问题`
3. `faithfulness.score <= 3` -> `生成问题`
4. `correctness.score < 4` -> `知识库问题`
5. 否则 -> `无明显问题`

### 9.4 返回结构

- `summary`：整体指标（intentAccuracy、hitRate、mrr、平均分、兜底率、幻觉率等）
- `caseResults`：逐条样本的详细结果 + 根因归因

---

## 10. `POST /luoluo/rag/document/parse`（文档解析）

### 10.1 做什么

使用 Tika 将上传文档解析成文本（偏“文档处理”能力）。

### 10.2 当前边界

它目前是独立能力：

- parse 的结果没有自动进入 chunk/embedding/入库
- 也没有自动接入 `generate` 的检索数据源

---

## 11. `POST /luoluo/rag/document/chunk`（文档切分）

### 11.1 做什么

把文本切成 chunk（为后续 embedding 与检索做准备）。

### 11.2 当前边界

同样是独立能力：

- chunk 结果没有自动 embedding
- 没有自动入 Milvus
- 没有自动接入 generate 的检索数据源

---

## 12. 当前 demo 与“真实知识库闭环”还差什么

如果你以“真实知识库问答闭环”为目标，目前关键缺口在于把三条链路串起来：

```text
document/parse -> document/chunk -> embedding -> 入库（Milvus） -> generate 使用真实库检索
```

除此之外，从“多轮 RAG”角度还差：

- `history -> query rewrite -> retrieval`（真正把对话上下文用于检索改写）
- tool 分支接入 MCP/业务工具执行（而不是仅提示）
- 会话记忆持久化（内存/redis/mysql）与超时清理策略

---

## 13. 当前最推荐怎么审查（你现在这轮想做的事）

如果你要“重新审查”当前代码是否符合预期，建议按这条顺序：

1. `/health` 看配置是否符合你预期（enabled/model/embedding/rerank）
2. `/chat` 看普通聊天是否稳定（基本可用性）
3. `/chat/stream` 看流式事件是否符合前端消费方式（delta/usage/complete/error）
4. `/generate` 分别测 4 种意图：
   - knowledge：是否进入检索并产出 citations
   - chitchat：是否绕过检索（SKIPPED）
   - clarification：是否先引导补充信息（SKIPPED）
   - tool：是否被拦截且不误进检索（SKIPPED）
5. `/evaluate` 跑默认评测集，观察：
   - intentAccuracy 是否稳定
   - Hit/MRR 是否合理
   - runJudge 打开后是否能定位生成问题/知识库问题

---

## 14. 你现在最容易踩的坑（提前说清楚）

- SSE 看起来“不流”：优先排查网关/代理缓冲，而不是先怀疑模型没 stream
- 兜底样本不要硬算 Hit/MRR：本来就没答案的 case 更适合评忠实度与兜底话术
- tool 分支目前只是“识别并拦截”：想要真正查订单/查个人数据，需要接 MCP/业务工具执行
