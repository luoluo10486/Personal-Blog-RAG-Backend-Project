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
- Milvus 向量库接入

但是现在还**不能算完整 RAG 闭环已经打通**。

更准确地说，当前状态是：

- `document/parse` 是独立的文档解析能力
- `embedding/search` 是独立的向量检索 demo
- `chat` / `chat/stream` 是独立的大模型聊天能力

它们目前还是并列能力，还没有真正打成下面这条链路：

```text
上传文档 -> 解析 -> 切 chunk -> 生成 embedding -> 入向量库 -> query 检索 -> 拼接 prompt -> 模型回答 -> 返回引用来源
```

所以你现在能测通接口，但还不能说“知识库问答闭环已经通了”。

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
- 文档解析服务
  - `TikaParseService`
- 配置层
  - `RagProperties`
  - `RagHttpClientConfig`
  - `MilvusClientConfig`

## 3. 当前暴露的接口总览

当前对外主要有 6 个接口：

- `GET /luoluo/rag/demo/health`
- `POST /luoluo/rag/demo/chat`
- `POST /luoluo/rag/demo/chat/stream`
- `POST /luoluo/rag/demo/embedding/search`
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

## 10. 这 6 个接口之间现在是什么关系

当前它们之间的关系是“并列能力”，不是闭环依赖关系。

### 10.1 `health`

只负责告诉你当前配置长什么样。

### 10.2 `chat`

只负责做一次完整的大模型调用。

### 10.3 `chat/stream`

只负责把大模型调用变成 SSE 流式输出。

### 10.4 `embedding/search`

只负责演示 query 向量化和向量检索。

### 10.5 `document/parse`

只负责从上传文件里抽正文。

### 10.6 `document/chunk`

负责把解析后的正文切成适合后续 embedding 的 chunk。

### 10.7 当前还没有打通的地方

当前没有下面这条真正的链路：

```text
document/parse -> chunk -> embedding/search 的数据源 -> chat
```

更具体地说：

- `document/parse` 的输出不会自动进入 `embedding/search`
- `embedding/search` 的结果不会自动进入 `chat`
- `chat` 完全不知道用户上传过哪些文档

这就是为什么当前它还不是完整 RAG 闭环。

---

## 11. 当前 demo 和真实闭环之间还差什么

如果以后要把当前模块升级成真正可用的 RAG 系统，至少还要补下面这些关键环节：

- 文档表、chunk 表、索引任务表
- 文档上传后的 chunk 切分
- chunk 的 embedding 生成
- 向量库持久化写入
- query 检索流程抽象成正式 retrieval 服务
- 在 `chat` 前增加 retrieval
- 把召回文本拼到 prompt
- 输出引用来源
- 可选 rerank
- 会话上下文管理

当前最核心的缺口只有一句话：

> 检索结果还没有真正进入生成链路。

---

## 12. 当前最适合怎么测试

如果只是想确认“代码能力都能跑”，建议按这个顺序测：

1. `GET /luoluo/rag/demo/health`
2. `POST /luoluo/rag/demo/embedding/search`
3. `POST /luoluo/rag/document/parse`
4. `POST /luoluo/rag/document/chunk`
5. `POST /luoluo/rag/demo/chat`
6. `POST /luoluo/rag/demo/chat/stream`

其中：

- 前 4 个更偏“组件可用性”
- 后 2 个更偏“模型链路可用性”

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
- Milvus 检索

这些能力都拆开实现并暴露出来了，而且单接口层面是可以测通的。

但从严格意义上说，当前仍然是“RAG 相关 demo 集合”，不是“完整 RAG 问答闭环”，因为最关键的一步还没做完：

- 检索结果还没有注入到聊天生成过程里。
