# Personal-Blog-RAG-Backend-Project

Spring Boot 3 多模块后端。当前正式 RAG 主链路放在 `luoluo-knowledge`，`luoluo-rag` 仅保留为 Demo / Playground。

## 模块

- `luoluo-admin`：统一启动入口
- `luoluo-common`：公共基础设施
- `luoluo-infra-ai`：AI 基础能力
- `luoluo-knowledge`：正式 RAG 主链路
- `luoluo-rag-mcp`：基于正式知识库链路的 MCP 服务
- `luoluo-rag`：保留的 Demo 模块，不再参与正式运行依赖

## 正式 RAG

优先接口：

- `GET /luoluo/knowledge/health`
- `POST /luoluo/knowledge/ask`
- `POST /luoluo/knowledge/document/parse`
- `POST /luoluo/knowledge/document/chunk`
- `POST /luoluo/knowledge/document/ingest`

兼容别名：

- `GET /luoluo/rag/health`
- `POST /luoluo/rag/ask`
- `POST /luoluo/rag/document/parse`
- `POST /luoluo/rag/document/chunk`
- `POST /luoluo/rag/document/ingest`

## 配置

- `app.ai.*`：通用 AI 配置
- `app.knowledge.*`：正式知识库链路配置
- `app.rag.*`：旧 Demo 配置，仅保留兼容

## 启动

```powershell
$env:JAVA_HOME='你的 JDK 21 路径'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\ragent\mvnw.cmd -f pom.xml -pl luoluo-admin -am clean package
```
