# Personal-Blog-RAG-Backend-Project

个人博客后端项目，采用 Spring Boot 3 + Maven 多模块架构，当前包含会员认证、公共系统接口、正式知识库 RAG 链路，以及 MCP 扩展能力。

## 当前模块定位

本仓库以 `luoluo-admin` 作为统一启动入口，其余模块按职责拆分：

- `luoluo-admin`：统一启动模块，也是本地开发的主要运行入口
- `luoluo-common`：公共基础设施模块，封装 Redis、Sa-Token、MyBatis-Plus、邮件等通用能力
- `luoluo-infra-ai`：AI 基础能力模块，封装聊天、Embedding、Rerank 等通用抽象
- `luoluo-knowledge`：正式 RAG 主链路模块，当前对外暴露 `/luoluo/knowledge/*`，并兼容 `/luoluo/rag/*`
- `luoluo-rag-mcp`：MCP 扩展模块，当前已切换为基于 `luoluo-knowledge` 的正式链路
- `luoluo-member`：会员能力模块，负责登录、注册、验证码校验、会话管理、个人资料查询
- `luoluo-system`：公共系统接口模块，对外暴露图形验证码、公开认证接口等
- `luoluo-rag`：保留在仓库里的 Demo / Playground 模块，文件不删，但已不再作为父工程聚合模块，也不是正式运行依赖
- `script`：SQL 基线、迁移、回滚等脚本目录
- `docker`：本地依赖环境相关配置

## 技术栈

- Java 21
- Spring Boot 3.3.5
- Maven 多模块
- MyBatis-Plus 3.5.14
- Sa-Token 1.44.0
- MySQL
- Redis
- Milvus（可选，本地向量环境）

## 正式 RAG 入口

当前正式链路位于 `luoluo-knowledge`，推荐优先使用以下接口：

- `GET /luoluo/knowledge/health`
- `POST /luoluo/knowledge/ask`
- `POST /luoluo/knowledge/document/parse`
- `POST /luoluo/knowledge/document/chunk`

兼容别名仍然可用：

- `GET /luoluo/rag/health`
- `POST /luoluo/rag/ask`
- `POST /luoluo/rag/document/parse`
- `POST /luoluo/rag/document/chunk`

需要注意：

- 现在 `luoluo-admin` 正式运行时暴露的 `/luoluo/rag/*` 已经是 `luoluo-knowledge` 的兼容路由，不再是旧 Demo 链路
- 旧的 `/luoluo/rag/demo/*` 只属于 `luoluo-rag` Demo 模块；该模块保留在仓库中，但默认不会被当前父工程构建或启动

## 目录概览

```text
Personal-Blog-RAG-Backend-Project
├─ luoluo-admin
├─ luoluo-common
├─ luoluo-infra-ai
├─ luoluo-knowledge
├─ luoluo-member
├─ luoluo-rag
├─ luoluo-rag-mcp
├─ luoluo-system
├─ script
├─ docker
├─ ragent
├─ README.md
├─ 当前登录流程说明.md
├─ 登录注册接口文档.md
└─ 当前RAG流程与Demo说明.md
```

## 环境要求

启动前建议先确认以下依赖已经准备好：

- JDK 21
- Maven 3.9+，或直接使用仓库内的 Maven Wrapper
- MySQL 8.x
- Redis

默认配置可参考：

- [application.yml](/D:/develop/Personal-Blog-RAG-Backend-Project/luoluo-admin/src/main/resources/application.yml)
- [.env.example](/D:/develop/Personal-Blog-RAG-Backend-Project/.env.example)

## 快速开始

### 1. 构建项目

推荐在仓库根目录使用 Wrapper 构建：

```powershell
$env:JAVA_HOME='你的 JDK 21 路径'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\ragent\mvnw.cmd -f pom.xml -pl luoluo-admin -am clean package
```

如果本机已经正确安装并配置了 Maven 3.9+ 与 JDK 21，也可以直接使用 `mvn`。

### 2. 启动服务

推荐直接启动 `luoluo-admin`：

```powershell
$env:JAVA_HOME='你的 JDK 21 路径'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\ragent\mvnw.cmd -f luoluo-admin\pom.xml spring-boot:run
```

默认端口：

```text
http://localhost:8080
```

## 配置说明

当前本地开发最常关注的配置包括：

- 数据源：`spring.datasource.*`
- Redis：`spring.data.redis.*`
- AI 基础能力：`app.ai.*`
- 正式知识库链路：`app.knowledge.*`
- Demo 兼容链路：`app.rag.*`
- 认证模块：`app.member.auth.*`
- 邮件发送：`spring.mail.*`

其中：

- `app.knowledge.*` 是当前正式 RAG 主链路使用的配置
- `app.knowledge.jdbc.enabled` 控制当前正式检索器是否启用
- `app.knowledge.vector.*` 当前主要用于知识库向量空间命名、Milvus 环境标识和后续扩展
- `app.rag.*` 仍然保留给 `luoluo-rag` Demo 模块，不再建议作为正式链路配置入口

认证相关默认配置位于：

- [application.yml](/D:/develop/Personal-Blog-RAG-Backend-Project/luoluo-admin/src/main/resources/application.yml)

值得注意的几个认证开关：

- `app.member.auth.image-captcha-enabled`：是否启用图形验证码校验
- `app.member.auth.allow-mock-verify-code`：是否允许使用 mock 验证码
- `app.member.auth.allow-plain-password`：是否兼容明文密码登录

## 认证接口说明

当前前后端联调时，优先使用公开接口：

- 图形验证码：`GET /luoluo/system/public/captcha/image`
- 发送验证码：`POST /luoluo/system/public/member/auth/code/send`
- 登录：`POST /luoluo/system/public/member/auth/login`
- 注册：`POST /luoluo/system/public/member/auth/register`

会员模块内部接口还包括：

- 登录：`POST /luoluo/member/auth/login`
- 注册：`POST /luoluo/member/auth/register`
- 登出：`POST /luoluo/member/auth/logout`
- 发送验证码：`POST /luoluo/member/auth/send-code`
- 当前用户资料：`GET /luoluo/member/profile/me`

认证接口里的 `grantType` 含义如下：

- `password`：账号密码方式
- `sms`：短信验证码方式
- `email`：邮箱验证码方式

注意：

- 当前后端登录必须传 `grantType`
- 当前后端注册也必须传 `grantType`
- 当前代码不识别 `registerType`

更完整的联调说明请查看：

- [登录注册接口文档.md](/D:/develop/Personal-Blog-RAG-Backend-Project/登录注册接口文档.md)
- [当前登录流程说明.md](/D:/develop/Personal-Blog-RAG-Backend-Project/当前登录流程说明.md)

## RAG 相关文档

RAG 相关流程与 Demo 说明可查看：

- [当前RAG流程与Demo说明.md](/D:/develop/Personal-Blog-RAG-Backend-Project/当前RAG流程与Demo说明.md)
- [RAG学习记录总结.md](/D:/develop/Personal-Blog-RAG-Backend-Project/RAG学习记录总结.md)

## SQL 脚本

认证与会员相关 SQL 脚本位于：

- `script/sql/member/baseline`
- `script/sql/member/migration`
- `script/sql/member/rollback`

说明：

- 当前仓库中的 SQL 更偏向手动执行和版本留档
- 默认配置下不会自动初始化正式数据库结构

## 推荐阅读顺序

如果你是第一次接手这个项目，建议按下面顺序看：

1. 本文档
2. [登录注册接口文档.md](/D:/develop/Personal-Blog-RAG-Backend-Project/登录注册接口文档.md)
3. [当前登录流程说明.md](/D:/develop/Personal-Blog-RAG-Backend-Project/当前登录流程说明.md)
4. [当前RAG流程与Demo说明.md](/D:/develop/Personal-Blog-RAG-Backend-Project/当前RAG流程与Demo说明.md)

## 当前状态说明

目前项目已经具备这些能力：

- 多模块后端基础架构
- 会员登录、注册、验证码发送
- 图形验证码校验
- Sa-Token 会话管理
- 正式知识库 RAG 主链路
- 基于正式知识库链路的 MCP 扩展

如果后续要继续完善，建议优先关注：

- 正式知识库的文档入库、Embedding、向量召回闭环
- `luoluo-knowledge` 中向量检索链路的进一步实装
- Demo 模块与正式模块的文档持续隔离，避免口径混淆
