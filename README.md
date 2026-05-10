# Personal-Blog-RAG-Backend-Project

Spring Boot 3 多模块后端。当前正式 RAG 主链路位于 `luoluo-knowledge`，MCP 服务位于 `luoluo-rag-mcp`。

## 模块

- `luoluo-admin`：统一启动入口
- `luoluo-common`：公共基础设施
- `luoluo-infra-ai`：AI 基础能力
- `luoluo-knowledge`：正式 RAG 主链路
- `luoluo-rag-mcp`：基于正式知识库链路的 MCP 服务
- `luoluo-member`：会员与认证能力
- `luoluo-system`：公共系统接口

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

## 启动

```powershell
$env:JAVA_HOME='你的 JDK 21 路径'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\ragent\mvnw.cmd -f pom.xml -pl luoluo-admin -am clean package
```
