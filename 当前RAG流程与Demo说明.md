# 当前 RAG 流程与 Demo 说明

本文档基于当前仓库代码整理，重点说明两件事：

- 当前 `luoluo-rag` 模块到底已经实现了哪些能力
- 当前已经暴露出来的几个接口，内部到底是怎么跑的

这份文档尽量按“接口视角 + 调用链视角 + 当前边界”来写，方便你自己联调、给前端说明、或者后续继续补完整 RAG 闭环。

## 1. 先给结论

当前仓库里的 RAG 相关能力，已经具备下面这些组件：

- 文档解析
- 普通大模型聊天
- SSE 流式聊天
- Embedding 检索
- RAG 生成（检索 + 上下文组装 + 引用回答）
- Milvus 向量库接入

但是现在还**不能算“真实知识库”RAG 闭环已经打通**。

更准确地说，当前状态是：

- `document/parse` 是独立的文档解析能力
- `embedding/search` 是独立的向量检索 demo
- `chat` / `chat/stream` 是独立的大模型聊天能力
- `generate` 已经能把“内置 demo chunks 的检索结果”真正送入生成链路

也就是说，目前已经打通了下面这条“内置 demo 数据”的演示链路：

```text
用户问题 -> embedding/search 粗检索 + rerank -> 拼接参考资料上下文 -> Chat API 生成答案 -> 解析引用编号
```

但还没有真正打成下面这条“真实文档知识库”链路：

```text
上传文档 -> 解析 -> 切 chunk -> 生成 embedding -> 入向量库 -> query 检索 -> 拼接 prompt -> 模型回答 -> 返回引用来源
```

所以你现在可以说“demo 级 RAG 生成闭环已经通了”，但还不能说“真实知识库问答闭环已经通了”。

## 2. 当前模块结构

当前 `luoluo-rag` 模块主要由这几部分组成：

- 控制器层
  - `RagDemoController`
  - `DocumentController`
- 聊天服务
  - `SiliconFlowChatDemoService`
- Embedding 检索服务
  - `SiliconFlowEmbeddingDemoService`
  - `DemoHashEmbeddingService`
  - `MilvusVectorStoreService`
- RAG 生成服务
  - `RagGenerationDemoService`
- 文档解析服务
  - `TikaParseService`
- 配置层
  - `RagProperties`
  - `RagHttpClientConfig`
  - `MilvusClientConfig`

## 3. 当前暴露的接口总览

当前对外主要有 7 个接口：

- `GET /luoluo/rag/demo/health`
- `POST /luoluo/rag/demo/chat`
- `POST /luoluo/rag/demo/chat/stream`
- `POST /luoluo/rag/demo/embedding/search`
- `POST /luoluo/rag/demo/generate`
- `POST /luoluo/rag/document/parse`
- `POST /luoluo/rag/document/chunk`

下面开始逐个详细解释。

---

## 4. `GET /luoluo/rag/demo/health`

### 4.1 这个接口是干什么的

这是一个“配置级健康检查接口”。

它的作用不是去真正探测外部服务，而是把当前 RAG 模块的关键配置摘要返回出来，让你快速确认：

- RAG 功能有没有打开
- 当前聊天模型是什么
- 当前 Embedding 提供方是什么
- Milvus 是否启用
- 当前检索召回模式是什么（Dense / Sparse / Hybrid）
- 是否启用 rerank（重排序）

### 4.2 请求方式

```text
GET /luoluo/rag/demo/health
```

请求体：

- 无

请求头：

- 无特殊要求

### 4.3 控制器入口

入口在：

- [RagDemoController.java](d:\develop\Personal-Blog-RAG-Backend-Project\luoluo-rag\src\main\java\com\personalblog\ragbackend\controller\RagDemoController.java)

对应方法：

- `health()`

### 4.4 内部逻辑

这个接口逻辑非常直接，几乎没有业务处理链：

1. 进入 `RagDemoController.health()`
2. 直接读取 `RagProperties`
3. 组装 `RagDemoHealthResponse`
4. 用统一响应体 `R.ok(...)` 返回

也就是说，这个接口：

- 不会调用 SiliconFlow
- 不会连接 Milvus
- 不会访问 Redis
- 不会访问数据库

### 4.5 返回字段说明

返回结构是统一包装：

```json
{
  "code": 0,
  "message": "RAG 演示模块已就绪",
  "data": {
    "enabled": true,
    "apiUrl": "https://api.siliconflow.cn/v1/chat/completions",
    "model": "Qwen/Qwen3-32B",
    "embeddingApiUrl": "https://api.siliconflow.cn/v1/embeddings",
    "embeddingModel": "Qwen/Qwen3-Embedding-8B",
    "embeddingProvider": "demo",
    "milvusEnabled": true,
    "retrievalMode": "HYBRID",
    "rerankEnabled": true,
    "rerankProvider": "demo",
    "rerankModel": "BAAI/bge-reranker-v2-m3"
  }
}
```

字段含义：

- `enabled`
  - 当前 RAG 功能总开关
- `apiUrl`
  - 聊天接口调用地址
- `model`
  - 当前聊天模型
- `embeddingApiUrl`
  - Embedding 接口调用地址
- `embeddingModel`
  - 当前 Embedding 模型
- `embeddingProvider`
  - 当前 Embedding 提供方，通常是 `demo` 或 `siliconflow`
- `milvusEnabled`
  - 当前 Milvus 是否启用
- `retrievalMode`
  - 当前粗检索模式：`DENSE_ONLY` / `SPARSE_ONLY` / `HYBRID`
- `rerankEnabled`
  - 是否启用二阶段重排序
- `rerankProvider`
  - 重排序提供方：`demo` / `siliconflow`
- `rerankModel`
  - 重排序模型名（当 provider=demo 时为本地启发式模型名）

### 4.6 这个接口能回答什么，不能回答什么

它能回答：

- 当前配置大致是什么
- 当前代码打算走哪条路径

它不能回答：

- API Key 是否真的有效
- SiliconFlow 是否真的可访问
- Milvus 当前服务是否在线
- 聊天/检索链路是否真的通

所以它适合做“联调前检查”，不适合做“最终可用性确认”。

---

## 5. `POST /luoluo/rag/demo/chat`

### 5.1 这个接口是干什么的

这是当前仓库里的“非流式聊天接口”。

它的本质是：

- 把用户问题包装成标准聊天请求
- 调用 SiliconFlow Chat Completions
- 等模型完整生成完答案后
- 一次性返回完整结果

注意，这个接口当前是**纯模型聊天**，不是“检索增强聊天”。

也就是说，当前它：

- 不会先查向量库
- 不会先做文档召回
- 不会把检索结果拼进 prompt

### 5.2 请求方式

```text
POST /luoluo/rag/demo/chat
Content-Type: application/json
```

请求体结构来自：

- [RagDemoChatRequest.java](d:\develop\Personal-Blog-RAG-Backend-Project\luoluo-rag\src\main\java\com\personalblog\ragbackend\dto\rag\RagDemoChatRequest.java)

字段：

- `systemPrompt`
  - 可选
  - 如果不传，则使用配置文件里的默认 system prompt
- `message`
  - 必填
  - 用户问题正文

请求示例：

```json
{
  "message": "hello"
}
```

或者：

```json
{
  "systemPrompt": "你是一名专业的电商客服助手，请用简洁的中文回答。",
  "message": "退货政策是什么？"
}
```

### 5.3 参数校验

当前会做的最直接校验是：

- `消息内容不能为空`

也就是说：

- `message = null`
- `message = ""`
- `message = "   "`

都会被当成非法请求。

### 5.4 控制器与服务调用链

主链路如下：

1. `RagDemoController.chat(...)`
2. `SiliconFlowChatDemoService.chat(...)`
3. `validateAvailability()`
4. `buildHttpRequest(request, false)`
5. Java `HttpClient.send(...)`
6. `parseResponse(...)`
7. 包装成 `R<RagDemoChatResponse>` 返回

### 5.5 接口内部详细逻辑

#### 第一步：检查功能可用性

`SiliconFlowChatDemoService.validateAvailability()` 会校验：

1. `app.rag.enabled` 是否为 `true`
2. `app.rag.api-key` 是否已配置

如果不满足，会直接抛出：

- `RAG 演示功能未启用`
- `SiliconFlow API Key 未配置`

#### 第二步：确定 system prompt

如果请求里传了 `systemPrompt`，则优先使用请求里的值。  
如果请求没传，或传的是空白字符串，则回退到：

- `app.rag.system-prompt`

当前默认配置是：

```text
你是一名专业的电商客服助手。请用简洁、明确的中文回答用户问题。
```

#### 第三步：组装 SiliconFlow 请求体

代码会构造一个类似下面的 JSON：

```json
{
  "model": "Qwen/Qwen3-32B",
  "temperature": 0,
  "max_tokens": 1024,
  "stream": false,
  "messages": [
    {
      "role": "system",
      "content": "..."
    },
    {
      "role": "user",
      "content": "..."
    }
  ]
}
```

注意：

- 当前只发两条消息
  - 一条 system
  - 一条 user
- 还没有多轮上下文拼接

#### 第四步：发起 HTTP 请求

请求头会带：

- `Authorization: Bearer {apiKey}`
- `Content-Type: application/json`

超时设置：

- 连接超时：`app.rag.connect-timeout-seconds`
- 读取超时：`app.rag.read-timeout-seconds`

#### 第五步：解析模型返回

如果 HTTP 状态码不是 2xx，会抛：

- `SiliconFlow 请求失败：HTTP 状态码=...，响应体=...`

如果是 2xx，则解析 JSON，提取：

- `id`
- `model`
- `choices[0].message.content`
- `choices[0].finish_reason`
- `usage.prompt_tokens`
- `usage.completion_tokens`
- `usage.total_tokens`

如果 `choices[0].message.content` 为空，会抛：

- `SiliconFlow 响应中不包含有效答案内容`

### 5.6 返回结构

返回统一包装结构：

```json
{
  "code": 0,
  "message": "对话完成",
  "data": {
    "requestId": "xxx",
    "model": "Qwen/Qwen3-32B",
    "answer": "Hello! How can I assist you today?",
    "finishReason": "stop",
    "promptTokens": 26,
    "completionTokens": 98,
    "totalTokens": 124
  }
}
```

### 5.7 常见失败场景

常见错误包括：

- `消息内容不能为空`
- `RAG 演示功能未启用`
- `SiliconFlow API Key 未配置`
- `SiliconFlow 连接超时（... 秒）`
- `SiliconFlow 请求超时（... 秒）`
- `SiliconFlow 请求失败：...`
- `SiliconFlow 响应中不包含有效答案内容`

### 5.8 当前局限

当前 `chat` 接口的局限很明确：

- 没有 retrieval
- 没有引用来源
- 没有多轮上下文
- 没有知识库选择
- 没有用户上传文档参与回答

所以它现在更像“LLM 调用 demo”，不是最终 RAG 问答接口。

---

## 6. `POST /luoluo/rag/demo/chat/stream`

### 6.1 这个接口是干什么的

这是 `chat` 的流式版本。

它的本质是：

- 调用同一个 SiliconFlow 聊天模型
- 但不等全部内容生成完再返回
- 而是通过 SSE 按增量持续推送内容

适合前端做：

- 实时打字机效果
- 增量回答展示
- 过程态交互

### 6.2 请求方式

```text
POST /luoluo/rag/demo/chat/stream
Content-Type: application/json
Accept: text/event-stream
```

请求体和 `chat` 一样：

```json
{
  "message": "hello"
}
```

或者：

```json
{
  "systemPrompt": "你是一名专业的电商客服助手，请用简洁的中文回答。",
  "message": "What is your return policy?"
}
```

### 6.3 控制器与服务调用链

主链路如下：

1. `RagDemoController.streamChat(...)`
2. `SiliconFlowChatDemoService.streamChat(...)`
3. `validateAvailability()`
4. 创建 `SseEmitter`
5. `CompletableFuture.runAsync(...)`
6. `streamChatInternal(...)`
7. 按 SSE 行解析模型流式响应
8. 持续向前端发送 `delta`
9. 结束时发送 `complete`

### 6.4 内部详细逻辑

#### 第一步：做和普通 chat 一样的可用性校验

仍然会检查：

- `app.rag.enabled`
- `app.rag.api-key`

#### 第二步：创建 `SseEmitter`

当前超时时间是：

- `readTimeoutSeconds + 5 秒`

这意味着服务端给 SSE 连接留了一个略大于普通读取超时的窗口。

#### 第三步：异步执行真实请求

控制器本身不会阻塞等待所有内容生成完，而是：

- 先返回一个 `SseEmitter`
- 后台异步去请求 SiliconFlow

#### 第四步：构造流式请求

与普通 `chat` 相比，唯一明显区别是：

- 请求体里的 `stream=true`

#### 第五步：解析上游流式响应

服务会逐行读上游返回内容。

只处理形如：

```text
data: ...
```

的行。

如果内容是：

```text
[DONE]
```

就认为流结束。

#### 第六步：处理每个流式 chunk

每个 chunk 会提取：

- `id`
- `model`
- `choices[0].delta.content`
- `choices[0].finish_reason`
- `usage`

如果这次 chunk 里有增量文本，就：

1. 把文本追加到 `fullContent`
2. 发送一个 `delta` 事件给前端

#### 第七步：最终组装完整响应

等流结束后，会基于累计内容组装：

- `requestId`
- `model`
- `answer`
- `finishReason`
- token usage

然后发送一个：

- `complete` 事件

### 6.5 SSE 事件说明

#### `delta`

用途：

- 推送本次新增文本

示例：

```text
event: delta
data: Hello
```

#### `complete`

用途：

- 推送最终完整结果对象

示例：

```text
event: complete
data: {"requestId":"...","model":"Qwen/Qwen3-32B","answer":"Hello!...","finishReason":"stop","promptTokens":26,"completionTokens":98,"totalTokens":124}
```

#### `error`

用途：

- 推送错误信息

示例：

```text
event: error
data: {"message":"SiliconFlow 请求超时（60 秒）"}
```

### 6.6 常见失败分支

流式接口的失败分两类。

#### A. 建立连接前失败

这类和普通 `chat` 基本一样：

- `RAG 演示功能未启用`
- `SiliconFlow API Key 未配置`

#### B. 建立连接后，在流式过程中失败

这类会通过 `error` 事件发给客户端，例如：

- 上游返回非 2xx
- 请求超时
- 连接超时
- 响应流里没有有效答案内容

### 6.7 为什么你在终端里会看到很多 `event:delta`

因为当前后端本来就是按 SSE 事件一条条往外推。  
你在 `curl.exe -N` 里看到：

```text
event:delta
data: How
```

说明这条流式链路是正常的，不是异常。

### 6.8 当前局限

和普通 `chat` 一样，当前流式聊天也没有：

- 检索增强
- 引用来源
- 多轮上下文
- 知识库选择

它只是“流式 LLM 输出”，不是“流式 RAG 输出”。

---

## 7. `POST /luoluo/rag/demo/embedding/search`

### 7.1 这个接口是干什么的

这是当前仓库里最接近“RAG 检索”的接口。

它的功能是：

- 接收用户 query
- 生成 query embedding
- 执行“粗检索召回”（dense / sparse / hybrid）
- 对粗召回候选做“重排序 rerank”（可选）
- 返回 topK 命中结果（最终排序结果）

但是要特别注意：

- 现在检索的不是用户上传文档
- 而是代码里内置的 demo 文本（当前为 6 条）

### 7.2 请求方式

```text
POST /luoluo/rag/demo/embedding/search
Content-Type: application/json
```

请求体来自：

- [RagEmbeddingSearchRequest.java](d:\develop\Personal-Blog-RAG-Backend-Project\luoluo-rag\src\main\java\com\personalblog\ragbackend\dto\rag\RagEmbeddingSearchRequest.java)

字段：

- `query`
  - 必填
- `topK`
  - 可选
  - 最小 1，最大 20
  - 不传默认 3

请求示例：

```json
{
  "query": "Can I still return something after a week?",
  "topK": 3
}
```

### 7.3 参数校验

当前会做这些校验：

- `检索问题不能为空`
- `topK 不能小于 1`
- `topK 不能大于 20`

### 7.4 当前 demo chunks 是什么

当前代码里内置了 6 段 demo 文本，主题大概包括：

1. 7 天无理由退货
2. 退货运费承担规则
3. 订单物流状态（带订单号）
4. 发货后物流更新时间
5. 会员积分抵扣规则
6. 生鲜商品退货例外规则

每段文本都会带 metadata，例如：

- `doc_id`
- `title`
- `category`

### 7.5 控制器与服务调用链

主链路如下：

1. `RagDemoController.embeddingSearch(...)`
2. `SiliconFlowEmbeddingDemoService.search(...)`
3. `validateAvailability()`
4. 先给所有 demo chunks 生成 dense 向量
5. 再给 query 生成 dense 向量
6. 为 query 与 chunks 计算稀疏分数（BM25，用于 SPARSE/HYBRID）
7. 按是否启用 Milvus 分流，执行粗召回（Dense/Sparse/Hybrid）
8. 可选：对粗召回候选进行 rerank（重排序）
9. 返回统一结果

### 7.6 接口内部详细逻辑

#### 第一步：检查可用性

会先检查：

- `app.rag.enabled`
- 如果使用 `siliconflow` 作为 embedding provider，则还会检查 API Key

注意：

- 当 `embedding-provider=demo` 时，不要求 API Key

#### 第二步：给 demo chunks 生成向量

服务会把 6 条内置 demo 文本全部取出来，生成向量。

这一点很关键，因为当前检索不是查已有索引，而是每次请求时都重新准备当前 demo 数据。

#### 第三步：给 query 生成向量

对用户 `query` 做 embedding。

#### 第四步：确定 topK

规则：

- 如果请求没传 `topK`，默认 `3`
- 如果传了，就使用传入值

#### 第五步：按 Milvus 开关决定走哪条检索链

##### A. 不启用 Milvus

如果：

```yaml
app.rag.milvus.enabled: false
```

则使用内存检索逻辑：

1. Dense：逐条计算 query 向量与 chunk 向量的余弦相似度
2. Sparse：用本地 BM25 对 query 与 chunk 做稀疏打分
3. Hybrid：对 dense/sparse 两路做 RRF 融合得到粗排
4. 可选：调用 rerank（或本地启发式）对候选重排
5. 取前 topK 条

##### B. 启用 Milvus

如果：

```yaml
app.rag.milvus.enabled: true
```

则流程变成：

1. 先准备 collection
2. collection 不存在就创建
3. 把 demo chunks 写入 Milvus（包含 dense 向量；sparse 向量由 Milvus BM25 function 自动生成）
4. 按配置选择 Dense / Sparse / Hybrid 粗检索
5. 可选：对候选做 rerank
6. 把结果转成统一输出

粗检索模式由配置决定：

```yaml
app:
  rag:
    retrieval:
      mode: HYBRID   # DENSE_ONLY / SPARSE_ONLY / HYBRID
```

### 7.7 当前支持两种 embedding 提供方

#### `demo`

这是当前默认值，也是最适合本地测试的模式。

特点：

- 不依赖外网
- 不依赖 SiliconFlow API Key
- 用本地哈希算法生成伪 embedding

底层实现：

- `DemoHashEmbeddingService`

当前返回模型名：

- `demo-hash-embedding-v1`

#### `siliconflow`

如果配置成：

```yaml
app.rag.embedding-provider: siliconflow
```

则会调用真正的 SiliconFlow Embedding API。

请求体大致类似：

```json
{
  "model": "Qwen/Qwen3-Embedding-8B",
  "encoding_format": "float",
  "input": [
    "text1",
    "text2"
  ]
}
```

### 7.8 返回结构

返回统一包装：

```json
{
  "code": 0,
  "message": "检索完成",
  "data": {
    "query": "Can I still return something after a week?",
    "embeddingModel": "demo-hash-embedding-v1",
    "chunkCount": 6,
    "vectorDimension": 64,
    "recallMode": "HYBRID",
    "recallCount": 6,
    "rerankApplied": true,
    "rerankProvider": "demo",
    "rerankModel": "heuristic-rerank-v1",
    "results": [
      {
        "rank": 1,
        "similarity": 0.98,
        "content": "Within 7 days after receipt, unused goods that still allow resale can be returned without reason.",
        "metadata": {
          "doc_id": "policy_001",
          "title": "Return Policy",
          "category": "return_policy"
        }
      }
    ]
  }
}
```

字段含义：

- `query`
  - 原始查询文本
- `embeddingModel`
  - 当前实际使用的 embedding 模型名
- `chunkCount`
  - 当前参与检索的 chunk 总数
- `vectorDimension`
  - 当前向量维度
- `results`
  - 检索命中列表
- `recallMode`
  - 粗召回模式：`DENSE_ONLY` / `SPARSE_ONLY` / `HYBRID`
- `recallCount`
  - 参与粗召回并进入二阶段候选的条数（便于联调观察）
- `rerankApplied`
  - 是否对候选集执行了 rerank（重排序）
- `rerankProvider`
  - rerank 提供方：`demo` / `siliconflow`（未启用时为 `未启用`）
- `rerankModel`
  - rerank 使用的模型名

### 7.9 常见失败场景

常见错误包括：

- `RAG 演示功能未启用`
- `SiliconFlow API Key 未配置`
- `SiliconFlow Embedding 连接超时（... 秒）`
- `SiliconFlow Embedding 请求超时（... 秒）`
- `SiliconFlow Embedding 请求失败：...`
- `Embedding 返回条数与 demo chunk 数量不一致`
- `Milvus 向量库服务不可用（请检查 app.rag.milvus.enabled 是否已开启）`

另外，如果你在服务启动阶段看到类似 `DEADLINE_EXCEEDED` / `deadline exceeded` 的异常，一般意味着：

- Milvus 没启动，或 `app.rag.milvus.uri` 配错了
- 机器端口不可达（本机/容器网络、端口映射、防火墙等）

最直接的验证方式是确认 `19530` 端口可用，并且服务端确实是 Milvus 的 gRPC 服务。

### 7.10 当前局限

当前这个接口最大的局限是：

- 它检索的是写死的 demo 文本，不是你的真实知识库

所以它当前更像：

- 向量检索能力验证接口

而不是：

- 真正的知识库召回接口

### 7.11 `POST /luoluo/rag/demo/generate`

这是当前项目里真正把“检索结果送进生成链路”的 demo 接口。

当前它已经支持一个最小可用的 function call 流程：

- 第一轮：模型先看到“已召回资料目录”与工具列表
- 第二轮：如果模型发起 tool call，后端执行本地工具，再把工具结果回传给模型生成最终答案

它内部会完成下面 4 步：

1. 复用 `embedding/search` 的检索逻辑，拿到 Top-K chunk
2. 第一轮先把 Top-K chunk 转成“资料目录”，并把本地工具定义发给模型
3. 如果模型发起 tool call，就执行本地工具并进入第二轮生成
4. 解析回答中的 `[1]`、`[2]` 等引用编号，并回填来源信息

请求方式：

```text
POST /luoluo/rag/demo/generate
Content-Type: application/json
```

请求体字段：

- `query`
  - 必填，用户问题
- `topK`
  - 可选，最终参与生成的候选条数
- `systemPrompt`
  - 可选，自定义生成阶段的 system prompt；不传时使用内置“带引用规则”的默认提示词

请求示例：

```json
{
  "query": "订单号 2026012345 的物流状态",
  "topK": 3
}
```

主链路如下：

1. `RagDemoController.generate(...)`
2. `RagGenerationDemoService.generate(...)`
3. 调 `SiliconFlowEmbeddingDemoService.search(...)`
4. 把检索结果转成 `【已召回资料目录】`
5. 第一轮调 `SiliconFlowChatDemoService.chatWithTools(...)`
6. 可选：执行本地 function call 工具
7. 第二轮调 `SiliconFlowChatDemoService.completeToolChat(...)`
8. 解析回答中的引用编号
9. 返回答案 + 检索摘要 + 引用列表

当前第一轮给模型的“资料目录”大致如下：

```text
【已召回资料目录】

[1] 标题：物流状态 | 文档ID：logistics_002 | 分类：logistics | 摘要：订单号 2026012345 的物流状态：已于 2026-02-18 14:21 从杭州仓发出...
[2] 标题：物流说明 | 文档ID：logistics_001 | 分类：logistics | 摘要：订单发货后 24 小时内会更新物流信息，用户可在订单详情页查看配送进度。

【用户问题】
订单号 2026012345 的物流状态
```

当前可调用的本地工具有两个：

- `listRetrievedChunks`
  - 返回本次已召回资料的目录、标题、分类和摘要
- `getRetrievedChunkByIndex`
  - 按编号返回某条资料的完整内容，供模型二次确认细节

如果模型没有发起 tool call，则会回退到普通 RAG 生成路径，直接使用完整 `【参考资料】` 上下文生成答案。

回退时使用的完整上下文格式大致如下：

```text
【参考资料】

[1] 标题：物流状态 | 文档ID：logistics_002 | 分类：logistics
订单号 2026012345 的物流状态：已于 2026-02-18 14:21 从杭州仓发出，承运商顺丰，当前状态运输中。

[2] 标题：物流说明 | 文档ID：logistics_001 | 分类：logistics
订单发货后 24 小时内会更新物流信息，用户可在订单详情页查看配送进度。

【用户问题】
订单号 2026012345 的物流状态
```

返回结构示例：

```json
{
  "code": 0,
  "message": "RAG 生成完成",
  "data": {
    "query": "订单号 2026012345 的物流状态",
    "answer": "订单已从杭州仓发出，承运商为顺丰，当前状态为运输中。[1] 发货后 24 小时内会更新物流信息，可在订单详情页查看配送进度。[2]",
    "requestId": "chatcmpl-123",
    "model": "Qwen/Qwen3-32B",
    "finishReason": "stop",
    "promptTokens": 128,
    "completionTokens": 66,
    "totalTokens": 194,
    "embeddingModel": "demo-hash-embedding-v1",
    "retrievedChunkCount": 2,
    "recallMode": "HYBRID",
    "rerankApplied": true,
    "rerankProvider": "demo",
    "rerankModel": "heuristic-rerank-v1",
    "functionCallApplied": true,
    "calledTools": [
      "getRetrievedChunkByIndex"
    ],
    "citations": [
      {
        "index": 1,
        "source": "物流状态（logistics_002）",
        "docId": "logistics_002",
        "title": "物流状态",
        "category": "logistics",
        "sourceUrl": "",
        "chunkContent": "订单号 2026012345 的物流状态：已于 2026-02-18 14:21 从杭州仓发出，承运商顺丰，当前状态运输中。"
      }
    ]
  }
}
```

这个接口当前已经能演示“检索增强生成 + 引用解析”的完整过程，但要注意：

- 它依赖的仍然是内置 demo chunks，不是用户上传文档
- 当前 function call 调用的是后端本地工具，不是外部业务系统接口
- 如果模型没有调用工具，服务会回退到普通 RAG 生成流程
- 如果模型没有按要求输出 `[1]` 这类编号，`citations` 可能为空
- 它复用的是普通 `chat` 接口，不是 SSE 流式生成

新增字段含义：

- `functionCallApplied`
  - 本次生成是否真的发生了 tool call
- `calledTools`
  - 本次被模型调用过的工具名列表

---

## 8. `POST /luoluo/rag/document/parse`

### 8.1 这个接口是干什么的

这个接口负责：

- 接收上传文档
- 判断文档类型
- 提取正文文本
- 返回文档 metadata

当前它是“文档解析接口”，还不是“知识库导入接口”。

### 8.2 请求方式

```text
POST /luoluo/rag/document/parse
Content-Type: multipart/form-data
```

表单字段：

- `file`

### 8.3 控制器入口

入口在：

- [DocumentController.java](d:\develop\Personal-Blog-RAG-Backend-Project\luoluo-rag\src\main\java\com\personalblog\ragbackend\controller\DocumentController.java)

对应方法：

- `parseDocument(@RequestPart("file") MultipartFile file)`

### 8.4 返回格式为什么和别的接口不一样

这个接口没有走统一 `R<T>` 包装，而是直接返回：

- `ResponseEntity<ParseResult>`

也就是说：

- 成功时 HTTP 200，body 是 `ParseResult`
- 失败时 HTTP 400，body 仍然是 `ParseResult`

### 8.5 控制器与服务调用链

主链路如下：

1. `DocumentController.parseDocument(...)`
2. `TikaParseService.parseFile(file)`
3. 根据 `ParseResult.success()` 判断返回 200 还是 400

### 8.6 接口内部详细逻辑

#### 第一步：检查文件是否为空

如果：

- `file == null`
- `file.isEmpty() == true`

则直接返回失败：

- `文件为空`

#### 第二步：识别 MIME 类型

使用：

- `Tika.detect(...)`

根据文件流和原始文件名判断 MIME 类型。

#### 第三步：解析正文

使用：

- `AutoDetectParser`
- `BodyContentHandler`

进行正文提取。

当前正文最大长度限制是：

- `10 * 1024 * 1024`
- 也就是 10MB 文本量级

#### 第四步：附带文件名到 metadata

服务会在 metadata 里写入：

- `resourceName = 原始文件名`

#### 第五步：清洗文本

当前会做基础清洗：

- 统一换行符
- 去掉行首行尾空白
- 合并过多空行
- 压缩重复空格

#### 第六步：提取 metadata

会遍历 Tika metadata，把非空字段提取到 `Map<String, String>` 中。

#### 第七步：判断解析结果是否为空

如果最终 `content` 是空字符串，则认为解析失败，并返回：

- `解析结果为空，可能是扫描件或加密文档。`

### 8.7 返回结构

成功示例：

```json
{
  "success": true,
  "mimeType": "text/plain",
  "content": "Hello World",
  "metadata": {
    "resourceName": "test.txt"
  },
  "contentLength": 11,
  "errorMessage": null
}
```

失败示例：

```json
{
  "success": false,
  "mimeType": null,
  "content": null,
  "metadata": {},
  "contentLength": 0,
  "errorMessage": "文件为空"
}
```

### 8.8 常见失败场景

当前常见失败原因包括：

- 文件为空
- 文档读取失败
- Tika 解析失败
- XML 结构解析失败
- 解析结果为空，可能是扫描件或加密文档

### 8.9 当前局限

这个接口当前只做到：

- 解析并返回正文

没有做到：

- chunk 切分
- embedding 生成
- 向量入库
- 文档记录持久化
- 和 `chat` 联动

所以它更像“预处理 demo”，而不是完整知识库导入接口。

---

## 9. `POST /luoluo/rag/document/chunk`

### 9.1 这个接口是干什么的

这是当前新增的“文档分块接口”。

它的目标不是直接回答问题，而是把已经解析出的整篇正文切成更适合后续做 embedding 和向量检索的 chunk。

这条链路当前做的是：

```text
上传文件 -> Tika 解析正文 -> 结构化切块 -> 返回 chunk 列表
```

它还没有做到：

- 自动生成 embedding
- 自动写 Milvus
- 自动进入 `embedding/search`
- 自动进入 `chat`

### 9.2 请求方式

```text
POST /luoluo/rag/document/chunk
Content-Type: multipart/form-data
```

表单字段：

- `file`

### 9.3 控制器与服务调用链

主链路如下：

1. `DocumentController.chunkDocument(...)`
2. `DocumentChunkService.chunkFile(...)`
3. `TikaParseService.parseFile(file)`
4. `DocumentChunkService.extractBlocks(...)`
5. `DocumentChunkService.buildChunks(...)`
6. 返回 `DocumentChunkResponse`

### 9.4 当前分块策略

当前实现的是一套“结构优先、长度兜底、同章节少量 overlap”的策略。

#### 第一步：结构优先

优先识别这些边界：

- Markdown 标题
- 中文/数字章节标题
- 代码块
- 列表块
- 表格块
- 普通段落

这一步的目标是尽量不破坏文档原始结构。

#### 第二步：长度兜底

如果结构块太长，再按长度切。

当前内置参数是：

- `targetChunkSize = 700`
- `maxChunkSize = 1100`
- `overlapSize = 120`

含义：

- 希望一个 chunk 尽量接近 700 字符
- 单 chunk 最大不要超过 1100 左右
- 如果同一章节切成多个 chunk，尽量保留 120 字符左右的上下文重叠

#### 第三步：不跨章节合并

当前实现会尽量遵守：

- 同一章节内的小块可以合并
- 不同章节之间不合并

这样能保证“结构优先”不会因为长度合并而失效。

### 9.5 返回结构

这个接口和 `/parse` 一样，不走统一 `R<T>` 包装，而是直接返回结果对象。

成功示例大致如下：

```json
{
  "success": true,
  "mimeType": "text/markdown",
  "metadata": {
    "resourceName": "policy.md"
  },
  "contentLength": 3280,
  "chunkCount": 6,
  "targetChunkSize": 700,
  "maxChunkSize": 1100,
  "overlapSize": 120,
  "chunks": [
    {
      "chunkIndex": 1,
      "sectionTitle": "Order Policy",
      "content": "Within 7 days after receipt ...",
      "contentLength": 268,
      "overlapFromPrevious": false
    }
  ],
  "errorMessage": null
}
```

字段说明：

- `contentLength`
  - 解析后全文总长度
- `chunkCount`
  - 切出来的 chunk 数量
- `targetChunkSize`
  - 当前目标 chunk 长度
- `maxChunkSize`
  - 当前单 chunk 最大长度
- `overlapSize`
  - 设计上的 overlap 长度
- `chunks`
  - 实际 chunk 列表

每个 chunk 目前包含：

- `chunkIndex`
- `sectionTitle`
- `content`
- `contentLength`
- `overlapFromPrevious`

### 9.6 失败场景

如果底层解析失败，这个接口也会失败。

常见情况包括：

- 文件为空
- 文档无法解析
- 解析结果为空

失败时会返回：

- `success = false`
- `errorMessage = ...`

### 9.7 当前局限

这个接口虽然已经完成了“解析 -> 分块”，但后续链路还没接：

- 没有自动 embedding
- 没有自动入向量库
- 没有自动和检索联动
- 没有自动和聊天联动

所以它现在是“知识库入库前的中间能力接口”。

---

## 10. 这 7 个接口之间现在是什么关系

当前它们之间已经不是完全并列了。

更准确地说：

- `health` / `chat` / `chat/stream` 仍然是独立能力
- `embedding/search` 是独立的检索能力
- `generate` 已经把“检索 -> 上下文 -> 生成 -> 引用解析”串成了一条 demo 闭环
- `document/parse` / `document/chunk` 仍然没有自动接入 `generate`

### 10.1 `health`

只负责告诉你当前配置长什么样。

### 10.2 `chat`

只负责做一次完整的大模型调用。

### 10.3 `chat/stream`

只负责把大模型调用变成 SSE 流式输出。

### 10.4 `embedding/search`

只负责演示 query 向量化和向量检索。

### 10.5 `generate`

负责把检索结果真正送入生成链路，并返回带引用的答案。

### 10.6 `document/parse`

只负责从上传文件里抽正文。

### 10.7 `document/chunk`

负责把解析后的正文切成适合后续 embedding 的 chunk。

### 10.8 当前还没有打通的地方

当前没有下面这条真正的链路：

```text
document/parse -> chunk -> 向量入库 -> generate
```

更具体地说：

- `document/parse` 的输出不会自动进入 `embedding/search`
- `document/chunk` 的结果不会自动进入 Milvus
- `generate` 当前只会检索内置 demo chunks
- `chat` 仍然完全不知道用户上传过哪些文档

这就是为什么当前它还不是“真实知识库”RAG 闭环。

---

## 11. 当前 demo 和真实闭环之间还差什么

如果以后要把当前模块升级成真正可用的 RAG 系统，至少还要补下面这些关键环节：

- 文档表、chunk 表、索引任务表
- 文档上传后的 chunk 切分
- chunk 的 embedding 生成
- 向量库持久化写入
- query 检索流程抽象成正式 retrieval 服务
- 把 `generate` 的数据源从 demo chunks 切换为真实文档
- 可选 rerank
- 会话上下文管理

当前最核心的缺口只有一句话：

> 真实文档检索结果还没有真正进入生成链路。

---

## 12. 当前最适合怎么测试

如果只是想确认“代码能力都能跑”，建议按这个顺序测：

1. `GET /luoluo/rag/demo/health`
2. `POST /luoluo/rag/demo/embedding/search`
3. `POST /luoluo/rag/demo/generate`
4. `POST /luoluo/rag/document/parse`
5. `POST /luoluo/rag/document/chunk`
6. `POST /luoluo/rag/demo/chat`
7. `POST /luoluo/rag/demo/chat/stream`

其中：

- 前 5 个更偏“组件与 RAG 主链路可用性”
- 后 2 个更偏“纯模型链路可用性”

---

## 14. 当前推荐的向量检索策略

如果结合当前项目阶段、现有代码实现（Dense/Sparse/Hybrid 粗检索 + 可选 rerank）、以及后续准备接“真实文档入库”的方向来看，当前最推荐的策略是一个典型的“两阶段检索”：

```text
第一阶段（粗检索/召回）：Hybrid（Dense 向量 + Sparse(BM25)）+ RRF 融合 -> TopK 候选
第二阶段（可选重排）：rerank（siliconflow 或 demo 启发式）-> 最终 TopK
```

这一套策略的核心价值是：

- Dense 负责语义召回（同义改写、口语化表达、上下文相关）
- Sparse(BM25) 负责关键词/编号/规则条款等“字面命中”（例如订单号、时间、具体术语）
- RRF 把两路召回结果融合成一个更稳的候选集
- rerank 在候选集上做更精细的判别，降低“看似相似但不相关”的误召回

### 14.1 为什么当前推荐 Hybrid（Dense + Sparse）

在客服 FAQ、政策说明、订单物流等场景里，很多 query 既有语义成分，也有强关键词成分：

- “订单号 2026012345 的物流状态”这类问题，BM25 对“2026012345”这种编号会非常敏感
- “退货多久到账”这类问题，Dense 对语义改写更友好

所以相比单纯 Dense 或单纯 BM25，Hybrid 更容易做到“既不漏召回，也不太乱召回”。

### 14.2 与当前代码实现的对应关系

当前项目里已经按 Hybrid 的方向把 Milvus schema 和检索链路落地了：

- dense 向量字段：`vector`
- sparse 向量字段：`sparse_vector`
  - 由 Milvus 的 BM25 function 基于 `content` 自动生成（无需你在 insert 时自己算稀疏向量）
- dense 索引：`HNSW` + `COSINE`
- sparse 索引：`AUTOINDEX` + `BM25`
- 融合：RRF（`rrfK`）
- 预留元数据字段：`doc_id` / `title` / `category`
  - 当前 demo 侧主要用于展示；后续要做过滤时，可在 Milvus 查询里补 filter 表达式

### 14.3 建议的参数范围（可直接照抄作为默认值）

如果你希望“先稳住效果，再慢慢调参”，可以优先用下面这组默认值：

- `denseRecallTopK = 20`
- `sparseRecallTopK = 20`
- `finalTopK = 8`
- `nprobe = 16`
- `dropRatioSearch = 0.2`
- `rrfK = 60`

### 14.4 什么时候用 DENSE_ONLY / SPARSE_ONLY

虽然推荐 Hybrid，但单路模式仍然有价值：

- `DENSE_ONLY`
  - 适合 query 比较“语义化”，且不太依赖关键词精确命中
  - 也适合你还没启用 BM25 function 的早期阶段
- `SPARSE_ONLY`
  - 适合强关键词/编号/术语检索（订单号、规则编号、接口名、错误码等）
  - 也适合你暂时不想接 embedding API 的场景

### 14.5 是否要启用 rerank（重排序）

建议按项目阶段分两步走：

1. 第一阶段先把 Hybrid 粗检索跑稳
2. 当知识库变大、相似条目变多时，再打开 `rerankEnabled=true`

原因很简单：

- rerank 更准，但更慢也更贵（如果走外部 API）
- 在小数据量阶段，Hybrid + 小 TopK 往往已经够用

### 14.6 当前阶段最推荐的“正式表述”

如果你要把这件事写成项目方案说明，当前最合适的说法可以直接写成：

> 当前 RAG 检索采用两阶段策略：第一阶段使用 Milvus HybridSearch（Dense 向量 + BM25 稀疏）并通过 RRF 融合召回候选；第二阶段可选启用 rerank 对候选重排。在现阶段以中小规模知识库为目标，优先保证召回质量、结果稳定性与后续扩展性。

### 14.7 后续升级路线（从 demo 走向真实闭环）

等真实文档入库链路打通后，建议按这个顺序升级：

1. 打通真实文档 `parse -> chunk -> embedding -> Milvus`
2. 为检索补充 `category/doc_id` 等过滤表达式，降低主题串扰
3. 扩大粗召回候选（例如 `denseRecallTopK/sparseRecallTopK` 从 20 增到 50），再用 rerank 收敛
4. 增加缓存与批量 embedding，降低接口耗时
5. 最后再考虑更复杂的多路召回与策略化调参

---

## 13. 一句话总结

当前这几个接口已经把：

- 文档解析
- 文档分块
- 大模型聊天
- 流式聊天
- Embedding 检索
- RAG 生成
- Milvus 检索

这些能力都拆开实现并暴露出来了，而且单接口层面是可以测通的。

其中，`POST /luoluo/rag/demo/generate` 已经把“检索 -> 上下文组装 -> 生成 -> 引用解析”这条 demo 主链路打通了。

但从严格意义上说，当前仍然不是“完整真实知识库 RAG 问答闭环”，因为最关键的一步还没做完：

- 用户上传文档的解析/分块结果还没有真正进入检索与生成过程。
---

## 15. Function Call 触发与日志观察

### 15.1 为什么有时不会触发

当前 `/luoluo/rag/demo/generate` 的 function call 是一个“两轮模式”：

1. 第一轮先把“已召回资料目录 + 工具列表”发给模型
2. 如果模型返回 `tool_calls`，则本地执行工具，再进入第二轮生成
3. 如果模型没有返回 `tool_calls`，则回退到普通 RAG 生成路径

因此你在接口返回中看到：

- `functionCallApplied = false`
- `calledTools = []`

就表示这一次模型判断“不需要调工具也能回答”，所以直接回退到了普通生成。

### 15.2 什么样的问题更容易触发

更容易触发 function call 的问题，通常具备下面这些特点：

- 问题中带有明确编号，例如订单号、单号、规则编号
- 问题中带有需要核验的细节，例如时间、金额、天数、状态、条件
- 问题要求“先核对原文再回答”
- 问题要求“说明依据哪条资料”

### 15.3 建议你在 Apifox 里直接测的示例

这些 query 比较容易触发：

```json
{
  "query": "请先调用工具核对原文，再告诉我订单号 2026012345 当前的物流状态和发货时间，并标注引用编号",
  "topK": 3
}
```

```json
{
  "query": "不要只看资料目录摘要。请先读取相关资料原文，再回答订单号 2026012345 的物流状态、承运商和更新时间",
  "topK": 3
}
```

```json
{
  "query": "这个订单的物流状态我需要精确答案。请必须先调用 getRetrievedChunkByIndex 核验原文后再回答",
  "topK": 3
}
```

```json
{
  "query": "请分别核验与物流相关的资料，再总结订单号 2026012345 的物流状态和后续查询方式",
  "topK": 3
}
```

### 15.4 当前已补充的日志

现在服务端已经增加了 function call 过程日志，主要包括：

- 第一轮目录预览
- 第一轮模型返回结果
- `tool_calls` 内容
- 本地工具执行参数与结果
- 第二轮回传工具结果
- 如果未触发 function call，也会打印“已回退普通 RAG 生成”

### 15.5 典型日志示例

实际日志大致会类似下面这样：

```text
RAG 生成开始: query=订单号 2026012345 的物流状态, retrievedChunkCount=3, recallMode=HYBRID, rerankApplied=true
RAG function call 第一轮目录预览: query=订单号 2026012345 的物流状态, chunkCatalog=【已召回资料目录】...
SiliconFlow function call 第一轮完成: requestId=xxx, model=Qwen/Qwen3-32B, toolCallCount=1, toolCalls=[{"id":"call_xxx","type":"function","function":{"name":"getRetrievedChunkByIndex","arguments":"{\"index\":1}"}}], assistantContent=<empty>
RAG function call 第一轮结果: query=订单号 2026012345 的物流状态, functionCallApplied=true, calledTools=[getRetrievedChunkByIndex], assistantContent=<empty>, toolCalls=[getRetrievedChunkByIndex(arguments={"index":1})]
RAG function call 执行工具: toolName=getRetrievedChunkByIndex, arguments={"index":1}, result={"index":1,"title":"物流状态","docId":"logistics_002","category":"logistics","sourceUrl":"","content":"订单号 2026012345 的物流状态：已于 2026-02-18 14:21 从杭州仓发出，承运商顺丰，当前状态运输中。"}
RAG function call 本地工具执行完成: query=订单号 2026012345 的物流状态, toolResults=[getRetrievedChunkByIndex(content={"index":1,...})]
SiliconFlow function call 第二轮开始: toolCallCount=1, toolResults=[getRetrievedChunkByIndex={"index":1,...}]
SiliconFlow function call 第二轮完成: requestId=xxx, finishReason=stop, answerPreview=订单号 2026012345 的物流状态...
```

---

## 16. `/luoluo/rag/demo/generate` 新增的意图分类入口层

这一部分是当前代码相对前一版最重要的变化。

现在 `/luoluo/rag/demo/generate` 不再是“拿到 query 就直接进入检索”，而是先经过一层**意图分类与路由分发**，再决定是否进入后续 RAG 链路。

也就是说，当前 `generate` 的真实入口逻辑已经变成：

```text
用户 query
-> 意图分类（规则优先 + 小模型兜底）
-> 按意图路由
   -> knowledge: 进入原有 RAG 检索生成链路
   -> chitchat: 直接走聊天模型
   -> clarification: 直接返回澄清引导
   -> tool: 先识别出来并拦住，不进入 RAG
```

### 16.1 为什么要先加这一层

因为并不是所有用户输入都应该直接进入 RAG。

例如：

- `退货政策是什么`
  - 这是知识检索问题，适合走 RAG
- `你好`
  - 这是闲聊，不需要检索
- `有什么推荐的`
  - 问题信息不足，应该先澄清
- `查一下我的订单状态`
  - 这是工具调用型问题，不应该误进知识库检索

如果没有意图分类层，系统就会把这些问题都当成“检索问答”，这会导致：

- 闲聊也去查知识库
- 模糊问题不先澄清
- 个人数据问题误走知识检索
- 工具调用类问题无法正确分流

所以当前 `generate` 已经从“单一路径 RAG”升级成“带入口路由的 RAG Demo”。

### 16.2 当前意图分类是怎么实现的

当前实现采用的是一个典型的**混合方案**：

```text
第一层：规则快速命中
第二层：小模型兜底分类
```

具体来说：

#### A. 规则层

规则层目前主要做两类快速判断：

1. 闲聊关键词
   - 例如：`你好`、`谢谢`、`收到`、`好的`
2. 澄清类短语
   - 例如：`有什么推荐的`、`怎么办`、`帮我看看`

如果命中规则，就直接返回对应意图，不再额外调用模型。

#### B. 小模型分类层

如果规则没有命中，则会调用一个更轻量的小模型做意图分类。

当前默认配置是：

- `Qwen/Qwen2.5-7B-Instruct`

分类标签目前有 4 个：

- `knowledge`
- `tool`
- `chitchat`
- `clarification`

这样做的目的很明确：

- 明确问题尽量用规则快速解决
- 模糊或复杂表达再交给模型理解
- 兼顾速度、成本和准确率

### 16.3 当前 4 类意图分别怎么处理

#### A. `knowledge`

这类问题会继续进入原有 RAG 主链路，也就是：

```text
embedding/search -> retrieval -> rerank -> function call（可选）-> 生成答案 -> 解析引用
```

典型示例：

- `退货政策是什么`
- `保修期多久`
- `发货后多久能看到物流信息`

#### B. `chitchat`

这类问题不会再进入检索链路，而是直接走聊天模型。

也就是说：

- 不会查向量库
- 不会拼参考资料
- 不会返回 citations

典型示例：

- `你好`
- `谢谢`
- `你是谁`

此时返回结果里通常会表现为：

- `recallMode = SKIPPED`
- `retrievedChunkCount = 0`

#### C. `clarification`

这类问题表示“当前信息不足，系统暂时不该直接回答，也不该直接检索”，因此当前实现会直接返回一段引导澄清的话术。

典型示例：

- `有什么推荐的`
- `怎么办`
- `帮我看看`

此时同样不会进入 RAG，返回会表现为：

- `model = intent-router`
- `recallMode = SKIPPED`
- `retrievedChunkCount = 0`

#### D. `tool`

这类问题已经能被识别出来，但**当前 `luoluo-rag` 模块里还没有把它真正接入 MCP / 外部工具执行链路**。

所以现在的处理方式是：

- 识别它是工具调用型问题
- 不让它误进 RAG
- 直接返回一段提示，说明这类问题应走 MCP / Function Call 工具链路

典型示例：

- `查一下我的订单状态`
- `帮我申请退货`
- `我还剩几天年假`

这一步很重要，因为它说明当前系统已经开始具备“入口路由意识”，只是 `tool` 路由还没真正打通到外部能力执行层。

### 16.4 这对 `/generate` 的定位意味着什么

这意味着 `/generate` 现在已经不是“纯知识检索生成接口”了，而是一个更接近真实 Agent / Copilot 入口的 demo：

- 先判断这句话该怎么处理
- 再决定是否进入 RAG
- 最后才真正执行对应链路

但是仍然要注意：

- 当前真正完整打通的，仍然只有 `knowledge -> RAG` 这一条链路
- `tool` 还只是识别并拦截，还没有接入真实 MCP Server
- `clarification` 还只是直接返回固定引导语
- `chitchat` 是直接走普通聊天模型

所以当前状态更准确的说法是：

> `generate` 已经升级为“带意图分类入口层的 RAG Demo 接口”，但还不是“多路能力全部打通的生产级 Agent 接口”。

---

## 17. `history` 字段现在起什么作用

当前 `RagGenerationRequest` 已经新增了一个可选字段：

- `history`

结构大致如下：

```json
{
  "query": "那它多久能到",
  "topK": 3,
  "history": [
    { "role": "user", "content": "我想查订单 2026012345 的物流" },
    { "role": "assistant", "content": "好的，请问您想看当前状态还是预计送达时间？" }
  ]
}
```

### 17.1 当前已经生效的地方

现在 `history` 已经会参与：

- 意图分类

也就是说，小模型做意图判断时，会结合历史消息一起看，而不是只看当前这一句 query。

这很重要，因为：

- 相同的一句话，放在不同上下文里，意图可能完全不同
- `这个怎么办`
- `那它多久能到`
- `帮我看看`

这些话如果脱离上下文，很难判断到底是：

- 知识检索
- 工具调用
- 还是澄清问题

### 17.2 当前还没有生效的地方

现在要特别说明一个边界：

当前 `history` **还没有真正进入 Query 改写和检索阶段**。

也就是说，现在还没有打成下面这条完整多轮链路：

```text
history -> query rewrite -> retrieval -> rerank -> generation
```

当前真实情况是：

```text
history -> 意图分类
query -> knowledge 时进入现有 RAG 主链路
```

所以如果你现在传了 `history`，要这样理解：

- 它已经能帮助系统更准确地区分“这句话到底属于哪种意图”
- 但它还不能帮助检索层自动完成“指代消解 / 上下文补全 / Query 改写”

### 17.3 这说明什么

这说明当前代码已经为“多轮 RAG”预留了入口，但还没有把整条多轮链路完全补齐。

也可以更直白地说：

- 入口层已经开始理解上下文
- 检索层还没有真正用上上下文

这正好对应当前项目的实际阶段。

---

## 18. 当前 `generate` 的真实完整链路

如果只看当前代码实际落地情况，那么 `/luoluo/rag/demo/generate` 的完整调用链应该更新为下面这张逻辑图：

```text
用户问题
-> 意图分类（规则优先 + 小模型兜底）
-> 路由分发
   -> knowledge
      -> embedding/search 粗检索
      -> rerank（可选）
      -> 构造资料目录
      -> 第一轮 function call 判断
      -> 本地工具执行（可选）
      -> 第二轮生成 / 或回退普通 RAG 生成
      -> 解析引用 citations
   -> chitchat
      -> 直接调用聊天模型
   -> clarification
      -> 直接返回澄清提示
   -> tool
      -> 当前先拦截并提示应走 MCP / Function Call
```

### 18.1 `knowledge` 分支的内部细节

只有在意图分类结果是 `knowledge` 时，才会进入当前已经实现好的 RAG 主链路。

这条链路仍然保持原来的两段结构：

#### 第一段：检索

```text
query
-> embedding/search
-> Dense / Sparse / Hybrid 粗召回
-> rerank（可选）
-> 得到 retrieved chunks
```

#### 第二段：生成

```text
retrieved chunks
-> 构造资料目录 / 完整参考资料
-> 模型先判断要不要调工具
-> 如果调工具，则二轮生成
-> 如果不调工具，则回退普通 RAG 生成
-> 解析引用编号 -> citations
```

### 18.2 这条链路当前还缺什么

虽然现在入口层更完整了，但如果从“生产级多轮 RAG / Agent”标准来看，还缺下面这些关键点：

- `tool` 分支还没真正接 MCP
- `history` 还没进入 query rewrite
- 还没有真正的多轮记忆持久化机制
- `knowledge` 分支检索的数据源仍然是 demo chunks
- `document/chunk -> embedding -> Milvus -> generate` 还没自动打通

所以当前最准确的定位是：

> 已具备“入口意图分类 + 单轮知识检索生成 + function call 演示”的多能力 demo，但还不是完整生产级多轮 Agent / RAG 系统。

---

## 19. 现在最推荐怎么测试 `/generate`

如果你要验证当前代码是否符合实际实现，最推荐按下面 4 组样例测。

### 19.1 测 `knowledge`

请求：

```json
{
  "query": "退货政策是什么",
  "topK": 3
}
```

预期：

- 会进入 RAG
- `recallMode != SKIPPED`
- `retrievedChunkCount > 0`
- 正常情况下会有 `citations`

### 19.2 测 `chitchat`

请求：

```json
{
  "query": "你好",
  "topK": 3
}
```

预期：

- 不进入检索
- `recallMode = SKIPPED`
- `retrievedChunkCount = 0`
- `citations = []`

### 19.3 测 `clarification`

请求：

```json
{
  "query": "有什么推荐的",
  "topK": 3
}
```

预期：

- 不进入检索
- `model = intent-router`
- `recallMode = SKIPPED`
- 返回内容会先引导用户补充信息

### 19.4 测 `tool`

请求：

```json
{
  "query": "查一下我的订单状态",
  "topK": 3
}
```

预期：

- 不进入检索
- `model = intent-router`
- `recallMode = SKIPPED`
- 返回内容会提示“这类问题更适合走 MCP / Function Call 工具链路”

### 19.5 测带 `history` 的分类能力

请求：

```json
{
  "query": "那它多久能到",
  "topK": 3,
  "history": [
    { "role": "user", "content": "我想查订单 2026012345 的物流" },
    { "role": "assistant", "content": "好的，请问您想看当前状态还是预计送达时间？" }
  ]
}
```

这个请求当前主要用来验证：

- 意图分类器是否能读取对话历史
- 但不是用来验证“完整多轮 query 改写检索”是否已经打通

这一点一定要分清。

---

## 20. 当前阶段最准确的一句话总结

如果把当前 `luoluo-rag` 模块的能力状态更新成一句最准确的话，可以写成：

> 当前 `luoluo-rag` 已经从“单一路径 RAG Demo”升级为“带意图分类入口层的多能力 RAG Demo”：知识检索问题会进入原有 RAG 主链路，闲聊和澄清会被直接分流，工具调用类问题也已经能被识别出来，但尚未正式接入 MCP / 外部工具执行链路；同时，请求已支持 `history` 字段用于意图分类，但 Query 改写与真正多轮检索链路仍待后续补齐。

---

## 21. 当前项目最适合落地的 RAG 评估策略

当 `luoluo-rag` 开始具备：

- 意图分类入口层
- knowledge 路由下的检索增强生成
- function call 演示链路
- 多种“非知识问答”的分流能力

这时候就不能只靠“感觉回答得还行”来判断系统效果了，而需要建立一套可重复、可量化、可定位问题来源的评估策略。

你给出的 `RAGEvaluator` 代码，本质上就是一个很好的评估框架雏形。它最重要的价值，不是某几个 API 调用，而是把评估拆成了下面四层：

```text
入口层评估
-> 检索层评估
-> 生成层评估
-> 端到端评估
```

这正是当前项目最适合采用的评估思路。

### 21.1 为什么当前项目不能只评“答案对不对”

如果只看最终答案是否正确，会有一个很大的问题：

你很难知道问题到底出在：

- 意图分类错了
- 检索没召回对
- rerank 没排对
- 模型生成时编造了
- 还是知识库本身就缺内容

所以当前项目的评估，必须分层做，而不能只做单一的“最终答案打分”。

### 21.2 当前项目最适合先评哪些样本

在你现在的 `luoluo-rag` 阶段，建议先把评测集分成四类：

#### A. `knowledge` 样本

这类样本用于评估当前最核心的 RAG 主链路。

每条样本最好包含：

- `query`
- `expectedAnswer`
- `relevantChunkIds`
- `intent = knowledge`

这正对应你代码里的 `EvalCase` 设计。

#### B. `tool` 样本

这类样本当前还不能评“工具链路回答是否正确”，因为你现在只是识别并拦截，还没真正打通 MCP。

所以当前阶段更适合评的是：

- 能不能识别成 `tool`
- 有没有错误走进 `knowledge` 检索链路

#### C. `chitchat` 样本

这类样本不用评检索效果，而是评：

- 是否成功分流到聊天模型
- 是否错误触发检索

#### D. `clarification` 样本

这类样本重点评：

- 是否能被识别成需要澄清
- 是否返回了合理的引导语
- 是否误进入检索链路

也就是说，当前项目的评测集已经不能只围绕知识问答设计，而应该和“入口路由”能力一起设计。

### 21.3 检索层应该评什么

对于 `knowledge` 样本，当前最实用的检索指标，正是你代码里用到的两个：

#### A. Hit Rate

也就是：

- Top-K 检索结果里有没有命中正确 chunk

这个指标适合回答：

- 检索至少有没有把正确资料召回来

#### B. MRR（平均倒数排名）

也就是：

- 正确 chunk 排在第几位

这个指标适合回答：

- 检索虽然命中了，但排位是不是足够靠前

这两个指标放在当前项目里尤其合适，因为你现在已经有：

- retrieval
- rerank
- final topK

所以完全可以用它们来观察：

- Hybrid 是否比单路更稳
- rerank 是否真的把正确 chunk 往前推了
- Query 改写以后检索排名有没有改善

### 21.4 生成层应该评什么

你给出的评估代码里，生成层使用了三个维度，这个设计非常适合当前项目。

#### A. Faithfulness（忠实度）

核心问题是：

- 模型的回答是否忠实于检索到的 chunk 内容

这个指标最适合定位：

- 是否发生了幻觉
- 是否在没有依据时擅自补充细节
- function call 没触发时是否过度相信目录摘要

#### B. Relevancy（相关性）

核心问题是：

- 模型是否真的回答了用户问题

这个指标最适合定位：

- 回答虽然没有编造，但答偏了
- 回答虽然安全，但没有真正回应用户诉求
- 澄清与闲聊类话术是否合适

#### C. Correctness（正确率）

核心问题是：

- 最终回答与标准答案是否一致

这个指标更接近业务视角，适合回答：

- 从用户角度看，这个回答到底算不算答对

所以对于当前项目来说，这三者最好一起看，而不是只看其中一个。

### 21.5 当前项目还应该新增一个“入口路由评估”

这是因为你现在的系统已经不是单一路径 RAG 了。

因此除了检索和生成指标之外，还应该加一层：

```text
意图分类准确率 / 路由准确率
```

最简单的做法是：

- 给每个评测样本标一个 `intent`
- 记录系统最终判成了什么意图
- 统计分类准确率

例如：

- `knowledge` 问题是否误判成 `tool`
- `tool` 问题是否误走 RAG
- `clarification` 问题是否先澄清
- `chitchat` 是否误触发检索

这一层在当前阶段非常关键，因为如果入口路由错了，后面的检索和生成指标都会失真。

### 21.6 端到端阶段应该怎么评

在当前项目里，端到端评估最适合关注下面几个结果：

#### A. 正确率均值

也就是所有样本的 `correctness` 平均分。

#### B. 答案正确率

例如：

- `correctness >= 4` 的样本占比

这比只看均值更直观，因为均值有时会掩盖 bad case。

#### C. 兜底率

这一点你代码里也考虑到了，非常实用。

当前项目中，兜底率可以理解成：

- 模型直接说“抱歉、未找到、建议联系客服”等保守回答的比例

这个指标特别适合观察：

- 系统是不是过于保守
- 知识库覆盖是否不足
- 检索命中差时模型是否经常兜底

#### D. 明显幻觉率

也就是忠实度很低的样本比例。

这个指标最适合拿来做“生成质量红线指标”。

### 21.7 Bad Case 应该怎么归因

你给出的代码里已经有一个非常值得保留的思路：

不要只列 bad case，还要给 bad case 做问题归因。

当前项目最适合的归因框架可以写成：

#### A. 检索阶段问题

特征：

- `hit = false`
- Top-K 没有正确 chunk

说明：

- 问题大概率出在 retrieval / rerank / query 改写

#### B. 生成阶段问题

特征：

- 检索命中了
- 但 `faithfulness` 很低

说明：

- 模型没有忠实依据 chunk 回答
- 可能发生了幻觉或过度推断

#### C. 知识库问题

特征：

- 检索命中了
- 忠实度也不低
- 但 `correctness` 仍然不高

说明：

- chunk 本身信息可能不完整
- 知识库内容可能过时
- 当前切块方式可能丢失关键信息

这套归因方式特别适合你现在这个项目，因为它能直接指导你下一步该改哪一层。

### 21.8 当前项目最适合的评估落地方式

如果从“现在就能做”的角度出发，最建议分两步：

#### 第一步：离线评测

先像你给出的 `RAGEvaluator` 一样，构造一批固定样本，离线跑：

- 意图分类
- 检索
- 生成
- 评分
- 输出报告

这一步的价值是：

- 便于反复回归测试
- 修改 Prompt / 检索参数后可以对比前后效果
- 不影响线上接口调用逻辑

#### 第二步：联调日志埋点

在线上 demo 接口里补充关键埋点，例如：

- query
- predicted intent
- recallMode
- retrievedChunkCount
- functionCallApplied
- calledTools
- citations count
- finishReason

这样做的好处是：

- 可以把离线评测和真实调用日志对照起来
- 便于发现“离线很好、线上不稳”的问题

### 21.9 当前阶段最推荐的一套评估框架

如果把当前项目评估策略浓缩成一句可以直接写进方案文档的话，最推荐这样表述：

> 当前 `luoluo-rag` 的评估采用分层策略：首先评估入口意图分类与路由是否正确；对于 `knowledge` 路由，再分别评估检索层的 Hit Rate 与 MRR、生成层的 Faithfulness / Relevancy / Correctness，以及端到端的答案正确率、兜底率与明显幻觉率；最后对 Bad Case 按“检索问题、生成问题、知识库问题”进行归因，用于指导后续优化。

### 21.10 当前阶段最重要的认识

你现在最需要建立的不是“有没有一个评测脚本”，而是下面这套判断框架：

- 入口层先看意图路由对不对
- 检索层再看资料有没有召回来
- 生成层再看回答是不是忠实且相关
- 端到端最后看用户视角是否答对
- Bad Case 必须能追到问题根因

只有这样，评测才真正能反过来推动系统变好，而不只是生成一份分数报告。

---

## 22. 参考 `RAGEvaluator` 代码后，当前项目的评估实现策略

你给出的 `RAGEvaluator` 代码，已经把一个比较标准的 RAG 评测流程拆出来了。

它的核心不是“用某个模型打分”，而是把整套评估过程拆成了下面这些明确步骤：

1. 构建评测数据集
2. 获取系统实际检索结果
3. 获取系统实际生成答案
4. 计算检索指标
5. 用 Judge Model 对生成质量打分
6. 汇总端到端指标
7. 输出 Bad Case 并做归因

这套思路非常适合迁移到当前 `luoluo-rag` 项目里。

### 22.1 评测数据集应该怎么设计

你代码里的 `EvalCase` 设计是对的，当前项目也非常适合沿用：

```text
query
expectedAnswer
relevantChunkIds
intent
```

这四个字段分别解决四个问题：

- `query`
  - 用户问题是什么
- `expectedAnswer`
  - 标准答案是什么
- `relevantChunkIds`
  - 哪些 chunk 才是这道题的正确证据
- `intent`
  - 这道题理论上应该走哪条入口路由

对于当前项目来说，这个 `intent` 字段尤其重要，因为你的系统现在已经不是单一路径 RAG，而是：

- `knowledge`
- `tool`
- `chitchat`
- `clarification`

所以以后每条评测样本，都应该先标注“这条样本理论上该走哪条链路”。

### 22.2 当前项目里，哪些代码应该替换掉示例中的“模拟检索 / 模拟生成”

你给出的示例代码里，这两段是模拟逻辑：

- `simulateRetrieval()`
- `simulateGeneration()`

如果迁移到当前 `luoluo-rag`，这两段应该替换成真实项目调用：

#### A. 检索阶段

可以替换为：

- `SiliconFlowEmbeddingDemoService.search(...)`

你真正需要拿到的是：

- 实际召回的 chunk 列表
- 每个 chunk 的 `doc_id / title / category`
- 最终的 `retrievedChunkIds`

也就是说，示例代码里这段：

```text
Map<String, List<String>> retrievalResults
```

在项目版实现里，应该变成：

```text
query -> RagEmbeddingSearchResponse -> results -> metadata.doc_id 列表
```

#### B. 生成阶段

可以替换为：

- `RagGenerationDemoService.generate(...)`

你真正需要拿到的是：

- `answer`
- `functionCallApplied`
- `calledTools`
- `citations`
- `retrievedChunkCount`
- `recallMode`

也就是说，示例代码里的：

```text
Map<String, String> generationResults
```

在项目版实现里，应该变成：

```text
query -> RagGenerationResponse -> answer + retrieval metadata + citations
```

### 22.3 当前项目版评测流程应该长什么样

如果把当前项目真实可落地的评测流程写成链路，可以整理成：

```text
评测样本
-> 先跑意图分类
-> 对比 predicted intent 和 expected intent
-> 如果是 knowledge
   -> 调 embedding/search
   -> 计算 Hit Rate / MRR
   -> 再调 generate
   -> 拿 answer / citations / functionCallApplied
   -> 用 Judge Model 打 Faithfulness / Relevancy / Correctness
-> 如果是 tool / chitchat / clarification
   -> 不评检索命中
   -> 重点评意图路由是否正确、回答是否符合该路由预期
-> 汇总报告
```

这个流程和你给出的示例代码相比，最大的升级点在于：

- 评估入口层也纳入了范围
- 不再只盯 `knowledge` 问答

### 22.4 检索指标为什么仍然推荐用 Hit Rate 和 MRR

你代码里已经用了两个最实用的检索指标：

- `Hit Rate`
- `MRR`

这两个放到当前项目里非常合适。

#### A. Hit Rate

判断方式：

- Top-K 结果里是否至少出现一个正确 chunk

它回答的问题是：

- 检索至少有没有把证据召回来

#### B. MRR

判断方式：

- 第一个正确 chunk 出现在第几位

它回答的问题是：

- 正确证据虽然召回了，但排名是否足够靠前

当前项目里这两个指标可以帮助你观察：

- Hybrid 检索是否比单路更稳
- rerank 是否真的提升了正确证据的排序位置
- 以后接入 query rewrite 后，排序是否改善

### 22.5 生成阶段为什么仍然建议保留三种 Judge 评分

你代码里把 Judge 评分拆成：

- `Faithfulness`
- `Relevancy`
- `Correctness`

这个拆法非常适合当前项目。

#### A. Faithfulness

它不是看“答对没”，而是看：

- 回答有没有忠实依据检索到的 chunk

在当前项目里，这个指标尤其适合发现：

- function call 没触发时，模型是否过度依赖目录摘要
- 模型有没有擅自补充知识库里没有的信息
- 有没有出现明显幻觉

#### B. Relevancy

它关心的是：

- 回答有没有真正回应用户的问题

这个指标可以帮助你区分：

- 回答虽然没编造，但答偏了
- 回答虽然安全，但没有解决用户问题

#### C. Correctness

它关心的是：

- 和标准答案相比，最终到底算不算答对

这是最接近业务结果的指标。

所以当前项目最推荐的做法仍然是：

- 三个维度一起看
- 不要只看 Correctness

### 22.6 当前项目还应该新增一个“路由准确率”指标

这是你给的示例代码里还没有显式写出来，但当前项目非常需要补的一层。

因为现在 `/generate` 会先做意图分类，所以评测时应该新增：

```text
Intent Accuracy / Route Accuracy
```

也就是：

- 这道题理论上是什么意图
- 系统实际判成了什么意图

例如：

- `knowledge` 问题是否误判成 `tool`
- `tool` 问题是否误进检索
- `clarification` 问题是否真的先澄清
- `chitchat` 是否还触发了 retrieval

如果这一层不评，你后面看到的很多检索 / 生成指标都会被入口错误污染。

### 22.7 兜底样本应该怎么处理

你给的示例代码里有一个很好的点：

- 对“知识库中本来就没有答案”的样本，不强行参与 Hit / MRR 计算

这在当前项目里应该继续保留。

例如：

- 问一个知识库中根本没有的信息
- 标准答案本来就是“抱歉，没有找到相关信息”

这种样本更适合评：

- 兜底回答是否合理
- 模型是否克制，没有乱编
- 忠实度是否够高

而不应该强行拿去算“检索没召回就是坏事”。

### 22.8 Bad Case 归因为什么一定要保留

你代码里 `printEvalReport()` 最有价值的一部分，不是打印平均分，而是：

- 打印 Bad Case
- 给出问题归因

这套机制非常适合当前项目，因为你现在的链路已经足够复杂，必须知道问题掉在哪一层。

当前项目里最推荐的归因规则仍然是：

#### A. 检索问题

- `hit = false`
- Top-K 没命中正确 chunk

归因：

- retrieval / rerank / query rewrite 问题

#### B. 生成问题

- 检索命中了
- 但 `faithfulness` 偏低

归因：

- 回答编造、补充了 chunk 中没有的信息

#### C. 知识库问题

- 检索命中了
- 忠实度也不差
- 但 `correctness` 仍然不高

归因：

- chunk 内容本身不完整、过时，或者切块不合理

#### D. 路由问题

这个是当前项目新增后特别建议补的第四类归因：

- 预期 `intent` 和实际 `intent` 不一致

归因：

- 入口分类 / 路由问题

### 22.9 项目版评估报告建议增加哪些字段

如果以后你真的要把示例代码改成项目版 `Evaluator`，建议最后的报告里至少输出这些内容：

#### A. 入口层

- `intent accuracy`
- 各意图类别的混淆情况

#### B. 检索层

- `Hit Rate`
- `MRR`
- `retrievedChunkCount`
- `recallMode`

#### C. 生成层

- `Faithfulness`
- `Relevancy`
- `Correctness`
- `functionCallApplied`
- `calledTools`

#### D. 端到端

- `correctness >= 4` 的占比
- `fallback rate`
- `hallucination rate`

#### E. Bad Case

- query
- expected intent
- predicted intent
- expected answer
- actual answer
- retrieved chunk ids
- citations
- 问题归因

这样输出后，报告就不只是“分数表”，而是一份真正可以指导优化的分析结果。

### 22.10 如果把它落成一句最准确的项目策略

可以直接总结成下面这句话：

> 当前 `luoluo-rag` 的评估实现，应参考 `RAGEvaluator` 的分层思路，但要升级为项目版四层评估：先评入口意图分类与路由，再评 `knowledge` 路由下的检索命中与排序，再评生成阶段的忠实度、相关性和正确率，最后在端到端维度统计答案正确率、兜底率、明显幻觉率，并对 Bad Case 做“路由问题 / 检索问题 / 生成问题 / 知识库问题”的归因。
