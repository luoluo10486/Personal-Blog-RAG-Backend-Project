# Personal-Blog-RAG-Backend-Project

Spring Boot 3 多模块后端，RAG 主链路已对齐 `ragent`，登录模块保持原样。

## 模块

- `luoluo-admin`：统一启动入口
- `luoluo-common`：公共基础设施
- `luoluo-infra-ai`：AI 基础能力
- `luoluo-knowledge`：RAG 主链路
- `luoluo-rag-mcp`：MCP 服务
- `luoluo-member`：会员与认证
- `luoluo-system`：公共系统接口

## RAG 接口

- `GET /knowledge/health`
- `POST /knowledge/ask`
- `GET /rag/settings`
- `POST /rag/v3/chat`
- `POST /rag/v3/stop`
- `GET /conversations`
- `PUT /conversations/{conversationId}`
- `DELETE /conversations/{conversationId}`
- `GET /conversations/{conversationId}/messages`
- `GET /knowledge-base`
- `POST /knowledge-base`
- `GET /knowledge-base/{kbId}/docs`
- `POST /knowledge-base/{kbId}/docs/upload`
- `GET /knowledge-base/docs/{docId}`
- `POST /knowledge-base/docs/{docId}/chunk`
- `DELETE /knowledge-base/docs/{docId}`

## 配置

- `ai.*`：AI 模型与提供商配置
- `rag.*`：RAG 主链路配置
- `app.knowledge.*`：旧配置兼容

## 启动

```powershell
$env:JAVA_HOME='你的 JDK 21 路径'
mvn -f pom.xml -pl luoluo-admin -am clean package
```
