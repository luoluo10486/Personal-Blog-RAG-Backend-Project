# Personal-Blog-RAG-Backend-Project

个人博客后端项目，采用 Spring Boot 3 + Maven 多模块架构，当前主要包含会员认证、公共系统接口、RAG 能力与 MCP 扩展能力。

## 项目概览

本仓库以 `luoluo-admin` 作为统一启动入口，其余模块按职责拆分：

- `luoluo-admin`：统一启动模块，也是本地开发的主要运行入口
- `luoluo-common`：公共基础设施模块，封装 Redis、Sa-Token、MyBatis-Plus、邮件等通用能力
- `luoluo-member`：会员能力模块，负责登录、注册、验证码校验、会话管理、个人资料查询
- `luoluo-system`：公共系统接口模块，对外暴露图形验证码、公开认证接口等
- `luoluo-rag`：RAG 相关业务能力模块
- `luoluo-rag-mcp`：RAG MCP 扩展模块
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

## 目录结构

```text
Personal-Blog-RAG-Backend-Project
├─ luoluo-admin
├─ luoluo-common
├─ luoluo-member
├─ luoluo-system
├─ luoluo-rag
├─ luoluo-rag-mcp
├─ script
├─ docker
├─ README.md
├─ 当前登录流程说明.md
├─ 登录注册接口文档.md
└─ 当前RAG流程与Demo说明.md
```

## 环境要求

启动前建议先确认以下依赖已经准备好：

- JDK 21
- Maven 3.9+
- MySQL 8.x
- Redis

默认配置可参考：

- [application.yml](/D:/develop/Personal-Blog-RAG-Backend-Project/luoluo-admin/src/main/resources/application.yml)
- [.env.example](/D:/develop/Personal-Blog-RAG-Backend-Project/.env.example)

## 快速开始

### 1. 构建项目

在仓库根目录执行：

```bash
mvn -pl luoluo-admin -am clean package
```

### 2. 启动服务

推荐直接启动 `luoluo-admin`：

```bash
cd luoluo-admin
mvn spring-boot:run
```

默认端口：

```text
http://localhost:8080
```

## 配置说明

当前本地开发最常关注的配置包括：

- 数据源：`spring.datasource.*`
- Redis：`spring.data.redis.*`
- 认证模块：`app.member.auth.*`
- 邮件发送：`spring.mail.*`
- RAG 配置：`app.rag.*`

其中认证相关默认配置位于：

- [application.yml](/D:/develop/Personal-Blog-RAG-Backend-Project/luoluo-admin/src/main/resources/application.yml#L77)

值得注意的几个开关：

- `app.member.auth.image-captcha-enabled`
  说明是否启用图形验证码校验
- `app.member.auth.allow-mock-verify-code`
  说明是否允许使用 mock 验证码
- `app.member.auth.allow-plain-password`
  说明是否兼容明文密码登录

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
- RAG 能力模块与扩展模块基础结构

如果后续要继续完善，建议优先关注：

- README 与模块文档统一编码与可读性
- 前端登录注册参数和后端接口保持一致
- 数据库初始化和迁移脚本的落地规范

