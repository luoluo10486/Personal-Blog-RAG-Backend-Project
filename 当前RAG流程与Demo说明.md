# 当前 RAG 流程与 Demo 说明

本文档说明的是当前仓库里 `luoluo-rag` 模块“已经存在的 RAG 相关能力”和“当前到底有哪些 demo”。

先给结论：

- 当前仓库已经有文档解析、聊天调用、SSE 流式聊天、Embedding 检索、Milvus 检索接入这些能力
- 但它们还没有被打通成一个完整的“上传文档 -> 切分 -> 向量化 -> 入库 -> 检索 -> 拼 Prompt -> 生成回答”的闭环 RAG 产品
- 当前更准确地说，是“若干个独立的 RAG 能力 demo + 一个演示型向量检索样例”

这点非常重要，因为“当前 RAG 流程”如果不区分开，很容易误以为系统已经具备完整知识库问答链路。

## 1. 当前模块定位

`luoluo-rag` 目前主要承担 4 类能力：

- 文档解析
  - 上传文件后用 Apache Tika 提取正文和元数据
- 聊天模型调用
  - 调用 SiliconFlow Chat Completions
- Embedding 检索演示
  - 对固定 demo 文本做向量化和相似度检索
- Milvus 向量库接入
  - 在启用 Milvus 时，把 demo 向量写入 Milvus 并检索

## 2. 当前已经暴露的接口

### 2.1 健康检查

`GET /luoluo/rag/demo/health`

用途：

- 返回当前 RAG 开关状态
- 返回聊天模型配置
- 返回 Embedding 配置
- 返回 Milvus 是否开启

注意：

- 这是“配置级健康检查”
- 不是“真的去探活 SiliconFlow 或 Milvus”
- 返回能看到配置值，不代表外部服务一定可用

### 2.2 非流式聊天

`POST /luoluo/rag/demo/chat`

用途：

- 调用 SiliconFlow 聊天模型
- 等模型完整回答结束后一次性返回

### 2.3 流式聊天

`POST /luoluo/rag/demo/chat/stream`

用途：

- 用 SSE 持续返回模型增量内容

当前 SSE 事件名有：

- `delta`
- `complete`
- `error`

### 2.4 Embedding 检索

`POST /luoluo/rag/demo/embedding/search`

用途：

- 把用户 query 向量化
- 与当前内置 demo chunks 做相似度检索
- 返回 topK 命中结果

### 2.5 文档解析

`POST /luoluo/rag/document/parse`

用途：

- 上传文件
- 解析 MIME 类型
- 抽取正文文本
- 返回元数据

注意：

- 这个接口返回的不是统一 `R<T>` 包装
- 而是直接返回 `ParseResult`

## 3. 当前真正存在的“RAG 流程”要拆成 4 条看

## 3.1 流程一：健康检查流程

这是最简单的一条：

1. 请求 `/luoluo/rag/demo/health`
2. 控制器直接读取 `RagProperties`
3. 返回：
   - `enabled`
   - `apiUrl`
   - `model`
   - `embeddingApiUrl`
   - `embeddingModel`
   - `embeddingProvider`
   - `milvusEnabled`

它能回答的问题是：

- 当前模块有没有打开
- 当前配置打算连哪个模型
- Embedding 是用 demo 还是 SiliconFlow
- Milvus 是否打开

它不能回答的问题是：

- API Key 对不对
- SiliconFlow 当前能不能通
- Milvus 当前服务是否真的在线

## 3.2 流程二：文档解析流程

这是当前仓库里最接近“知识库前置处理”的能力，但它还只是“解析”，不是完整入库链路。

### 3.2.1 接口

```text
POST /luoluo/rag/document/parse
Content-Type: multipart/form-data
```

字段：

- `file`

### 3.2.2 处理流程

`DocumentController -> TikaParseService`

具体步骤：

1. 校验文件是否为空
2. 用 `Tika.detect(...)` 检测 MIME 类型
3. 用 `AutoDetectParser` 解析文件
4. 用 `BodyContentHandler` 提取正文
5. 清洗文本：
   - 统一换行
   - 去掉多余空格
   - 合并过多空行
6. 读取 Tika metadata
7. 组装 `ParseResult`

### 3.2.3 返回字段

成功时返回：

- `success=true`
- `mimeType`
- `content`
- `metadata`
- `contentLength`
- `errorMessage=null`

失败时返回：

- `success=false`
- `errorMessage`

### 3.2.4 当前能力边界

当前文档解析只做到：

- 解析文件
- 把正文和元数据返回给调用方

当前没有做到：

- 自动切 chunk
- 自动生成 embedding
- 自动写向量库
- 自动建立文档管理记录
- 自动进入聊天检索链路

也就是说：

- 现在的 `parse` 接口是一个独立 demo 能力
- 不是完整知识库导入入口

## 3.3 流程三：Embedding 检索流程

这是当前仓库里最像“RAG 检索”的那部分，但它检索的是固定 demo 文本，不是用户上传的文档。

### 3.3.1 当前 demo chunks 是什么

`SiliconFlowEmbeddingDemoService` 里写死了 5 段英文电商示例文本：

1. 7 天无理由退货政策
2. 退货运费规则
3. 发货后物流更新时间
4. 会员积分抵扣规则
5. 生鲜商品退货例外规则

每个 chunk 还带简单 metadata，例如：

- `doc_id`
- `title`

所以当前 Embedding 检索更准确地说是：

- 一个固定样本文本集上的向量检索 demo

### 3.3.2 请求示例

```json
{
  "query": "Can I still return something after a week?",
  "topK": 3
}
```

### 3.3.3 当前处理主链路

`RagDemoController -> SiliconFlowEmbeddingDemoService`

内部步骤是：

1. 校验 `app.rag.enabled`
2. 根据配置决定 Embedding 提供方
3. 先给所有 demo chunks 生成向量
4. 再给 query 生成向量
5. 根据是否开启 Milvus 分两条路：
   - 未开启 Milvus：内存余弦相似度检索
   - 开启 Milvus：写入 Milvus 后再检索
6. 返回 topK 结果

### 3.3.4 当前支持两种 Embedding 提供方式

#### 方式 A：`demo`

这是默认值，也是最适合本地演示的模式。

特点：

- 不依赖外部网络
- 不依赖 SiliconFlow API Key
- 用本地哈希向量算法生成 embedding

底层实现是：

- `DemoHashEmbeddingService`

它做的事情不是“真正的大模型 embedding”，而是：

- 按 token 做 hash
- 落到固定维度向量
- 再做归一化

当前默认模型名返回为：

- `demo-hash-embedding-v1`

当前默认维度：

- `64`

#### 方式 B：`siliconflow`

当配置：

```yaml
app.rag.embedding-provider: siliconflow
```

时，会真正调用 SiliconFlow Embedding API。

特点：

- 需要 `app.rag.api-key`
- 需要外网可用
- 需要 `embeddingApiUrl`、`embeddingModel` 可用

当前默认配置值：

- `embeddingApiUrl = https://api.siliconflow.cn/v1/embeddings`
- `embeddingModel = Qwen/Qwen3-Embedding-8B`

### 3.3.5 不开 Milvus 时怎么检索

如果：

```yaml
app.rag.milvus.enabled: false
```

则当前检索流程是纯内存的：

1. demo chunks 各自生成向量
2. query 生成向量
3. 逐条计算余弦相似度
4. 按分数倒序排序
5. 取 topK 返回

这部分由：

- `CosineSimilarity`
- `SiliconFlowEmbeddingDemoService.buildInMemoryResults(...)`

共同完成。

### 3.3.6 开 Milvus 时怎么检索

如果：

```yaml
app.rag.milvus.enabled: true
```

并且 Milvus 客户端成功创建，则走 Milvus 流程：

1. 先准备 collection
2. collection 名称按“基础名 + 维度”生成
3. 把 5 个 demo chunks upsert 到 Milvus
4. 再用 query 向量做检索
5. 把返回结果转成统一响应对象

当前 collection 命名规则类似：

```text
rag_demo_chunks_64
```

说明：

- `rag_demo_chunks` 是默认基础名
- `64` 是向量维度

### 3.3.7 当前 Milvus 检索的一个关键事实

当前代码不是“提前离线建索引，再在线查询”，而是：

- 每次检索时都可能先把 demo chunks upsert 一次

这说明当前 Milvus 这一段主要还是 demo 性质，不是成熟生产链路。

## 3.4 流程四：聊天流程

当前聊天流程是“纯模型调用 demo”，不是“检索增强聊天”。

### 3.4.1 非流式聊天

接口：

```text
POST /luoluo/rag/demo/chat
```

请求体：

```json
{
  "systemPrompt": "可选，自定义系统提示词",
  "message": "用户问题"
}
```

处理流程：

1. 校验 `app.rag.enabled`
2. 校验 `app.rag.api-key` 已配置
3. 选择 system prompt
   - 如果请求里传了，用请求值
   - 否则回退到配置项 `app.rag.system-prompt`
4. 构造 SiliconFlow Chat Completions 请求
5. 发送 HTTP 请求
6. 解析返回 JSON
7. 提取：
   - `id`
   - `model`
   - `answer`
   - `finishReason`
   - token usage

### 3.4.2 流式聊天

接口：

```text
POST /luoluo/rag/demo/chat/stream
```

处理流程：

1. 先做与非流式相同的可用性校验
2. 创建 `SseEmitter`
3. 异步请求 SiliconFlow
4. 按 SSE 行读取响应
5. 遇到增量内容就发送 `delta` 事件
6. 全部结束后发送 `complete` 事件
7. 出错则发送 `error` 事件

### 3.4.3 当前流式 SSE 事件说明

#### `delta`

含义：

- 增量文本片段

前端通常做法：

- 收到一段就拼到当前答案里

#### `complete`

含义：

- 最终完整响应对象

包含：

- `requestId`
- `model`
- `answer`
- `finishReason`
- token usage

#### `error`

含义：

- 流式过程中发生错误

当前发送结构大致是：

```json
{
  "message": "xxx"
}
```

## 4. 当前最关键的事实：聊天没有接检索结果

这点值得单独强调。

虽然模块名叫 RAG，而且已经有：

- 文档解析
- Embedding 检索
- Milvus 接入
- 聊天接口

但从当前代码实现看：

- `chat` 不会调用 `embedding/search`
- `chat` 不会读取 Milvus
- `chat` 不会把检索结果拼进 prompt
- `document/parse` 的输出也不会进入 `chat`

所以当前实际情况是：

- “聊天 demo” 是纯大模型问答
- “Embedding 检索 demo” 是独立的相似度检索
- “文档解析 demo” 是独立的内容提取

它们是并列能力，不是闭环链路。

如果一定要用一句话描述当前状态，可以说：

> 当前仓库是“RAG 组件级 demo 已有，但端到端知识库问答闭环尚未打通”。

## 5. 当前有哪些 Demo

这里分“接口级 demo”和“测试级 demo”两类。

## 5.1 接口级 demo

### 5.1.1 `GET /luoluo/rag/demo/health`

用途：

- 查看当前 RAG 配置摘要

适合：

- 前端联调前确认配置
- 判断当前是 demo embedding 还是 SiliconFlow embedding

### 5.1.2 `POST /luoluo/rag/demo/chat`

用途：

- 演示普通聊天调用

特点：

- 需要 API Key
- 不走检索增强

### 5.1.3 `POST /luoluo/rag/demo/chat/stream`

用途：

- 演示流式输出

特点：

- SSE 形式
- 有 `delta/complete/error` 三类事件

### 5.1.4 `POST /luoluo/rag/demo/embedding/search`

用途：

- 演示 query 向量化和相似度检索

特点：

- 默认可离线跑
- 检索的不是用户文档，而是内置的 5 条 demo chunks

### 5.1.5 `POST /luoluo/rag/document/parse`

用途：

- 演示文件解析

特点：

- 可直接用于 TXT/PDF/Office 等文档正文抽取测试
- 当前不自动接后续入库

## 5.2 测试级 demo

### 5.2.1 `DocumentControllerTest`

演示内容：

- 上传文件到 `/luoluo/rag/document/parse`
- 断言能拿到 `mimeType`、`content`、`metadata`

说明：

- 这是文档解析接口 demo

### 5.2.2 `TikaParseServiceTest`

演示内容：

- 直接测试 Tika 对纯文本的提取
- 测空文件失败分支

### 5.2.3 `RagDemoControllerTest`

演示内容：

- `health` 接口
- `embedding/search` 接口
- `chat/stream` SSE 接口

说明：

- 这是控制器层联调 demo

### 5.2.4 `SiliconFlowChatDemoServiceTest`

演示内容：

- 请求超时分支
- 连接超时分支
- 流式响应解析逻辑

这个测试很有价值，因为它直接说明了：

- 当前流式聊天实现是按 `data: ...` 的 SSE 行解析的

### 5.2.5 `SiliconFlowEmbeddingDemoServiceTest`

演示内容：

- Embedding 响应 JSON 解析
- 排序逻辑
- topK 返回逻辑

### 5.2.6 `MilvusEmbeddingSearchIntegrationTest`

演示内容：

- 在 Milvus 可达时做真实集成测试

它的前提条件很明确：

- `MILVUS_IT_ENABLED=true`
- `127.0.0.1:19530` 有 Milvus 服务

这说明当前仓库已经具备：

- 本地 demo 检索
- Milvus 集成检索 demo

两层能力。

## 6. 当前默认配置现状

当前 `luoluo-admin/src/main/resources/application.yml` 里的 RAG 相关默认值大致是：

- `app.rag.enabled: true`
- `app.rag.embedding-provider: demo`
- `app.rag.api-url: https://api.siliconflow.cn/v1/chat/completions`
- `app.rag.embedding-api-url: https://api.siliconflow.cn/v1/embeddings`
- `app.rag.api-key: ${SILICONFLOW_API_KEY:}`
- `app.rag.model: Qwen/Qwen3-32B`
- `app.rag.embedding-model: Qwen/Qwen3-Embedding-8B`
- `app.rag.temperature: 0`
- `app.rag.max-tokens: 1024`
- `app.rag.connect-timeout-seconds: 30`
- `app.rag.read-timeout-seconds: 60`
- `app.rag.demo-embedding-dimension: 64`
- `app.rag.system-prompt: You are a professional ecommerce support assistant. Keep answers concise.`
- `app.rag.milvus.enabled: false`

这套默认值意味着：

### 6.1 默认“可直接体验”的 demo

开箱最容易体验的是：

- `health`
- `document/parse`
- `embedding/search`

原因：

- `embedding-provider=demo`
- `milvus.enabled=false`

所以默认不依赖 SiliconFlow Embedding，也不依赖 Milvus。

### 6.2 默认“可能不能直接跑”的 demo

默认不一定能直接跑的是：

- `/luoluo/rag/demo/chat`
- `/luoluo/rag/demo/chat/stream`

原因：

- 这两个接口一定需要 `app.rag.api-key`
- 如果没有真实 `SILICONFLOW_API_KEY`，会直接返回：
  - `siliconflow api key is not configured`

## 7. 当前最适合怎么理解这个模块

如果从“产品能力成熟度”角度看，当前模块更像下面这 3 层：

### 第一层：基础能力已具备

- 文件解析
- 模型调用
- 流式输出
- 向量化
- 相似度检索
- Milvus 接入

### 第二层：演示场景已具备

- 固定 demo chunks 的 Embedding 检索
- SiliconFlow 聊天演示
- 流式聊天演示
- Milvus 检索演示

### 第三层：产品闭环还没完成

当前还没有真正实现：

- 文档上传后自动切片
- 切片后的持久化管理
- 文档向量化入库任务
- 检索结果注入聊天 prompt
- 基于知识库的最终回答
- 多轮会话记忆
- rerank
- 引用来源展示
- 知识库管理后台

## 8. 当前如果你要给别人讲“RAG 流程”，推荐这样讲

可以拆成“当前已实现流程”和“尚未打通流程”两部分。

### 8.1 当前已实现流程

#### A. 文档解析流程

```text
上传文件 -> Tika 解析 -> 返回正文和 metadata
```

#### B. 检索 demo 流程

```text
输入 query -> 生成向量 -> 和内置 demo chunks 比相似度 -> 返回 topK
```

#### C. Milvus 检索 demo 流程

```text
输入 query -> 生成向量 -> upsert 内置 demo chunks 到 Milvus -> 在 Milvus 中检索 -> 返回 topK
```

#### D. 聊天 demo 流程

```text
输入 systemPrompt/message -> 调 SiliconFlow -> 返回完整答案或 SSE 增量答案
```

### 8.2 当前尚未打通的理想闭环

当前代码还没有形成下面这条完整链路：

```text
上传文档 -> 解析 -> 切块 -> embedding -> 向量库存储 -> query 检索 -> 检索结果拼接 prompt -> 大模型回答 -> 返回引用来源
```

## 9. 调用示例

## 9.1 健康检查

```text
GET /luoluo/rag/demo/health
```

## 9.2 文档解析

```text
POST /luoluo/rag/document/parse
```

表单字段：

- `file`

## 9.3 Embedding 检索

```json
{
  "query": "Can I still return something after a week?",
  "topK": 3
}
```

## 9.4 非流式聊天

```json
{
  "systemPrompt": "You are a helpful assistant.",
  "message": "What is your return policy?"
}
```

## 9.5 流式聊天

```json
{
  "message": "hello"
}
```

## 10. 一句话总结

当前 `luoluo-rag` 已经具备：

- 文档解析 demo
- 模型聊天 demo
- SSE 流式聊天 demo
- Embedding 检索 demo
- Milvus 检索 demo

但当前还不能说它已经是完整的知识库问答系统，因为最关键的一步还没打通：

- 检索结果没有真正进入聊天生成链路

所以如果你后面要继续推进这个模块，最自然的下一步一般会是：

1. 把 `document/parse` 输出接到 chunk 切分
2. 把 chunk 接到 embedding 和向量库存储
3. 在 `chat` 前增加 retrieval
4. 把命中 chunk 拼进 prompt
5. 返回答案时附带引用来源

这样才会从“RAG 相关 demo 集合”进入“真正可用的 RAG 问答闭环”。
