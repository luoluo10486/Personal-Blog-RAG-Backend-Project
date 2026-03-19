# Personal-Blog-RAG-Backend-Project

个人博客后端项目（Java / Spring Boot）。

当前项目拆分为两个业务域：

- `member`：个人中心与登录认证（密码/短信/邮箱，策略模式）
- `rag`：RAG 问答编排（检索 API + 生成 API）

## 1. 项目结构

```text
java-backend
├─ pom.xml
├─ script/sql/member                      # member 模块 SQL（按 cde-base 风格）
│  ├─ baseline/
│  ├─ migration/
│  ├─ rollback/
│  ├─ data/
│  └─ CHANGELOG.md
├─ src/main/java/com/personalblog/ragbackend
│  ├─ config
│  ├─ controller                          # 通用接口（health/posts/rag）
│  ├─ member                              # 个人中心模块（与 rag 分开）
│  │  ├─ controller
│  │  ├─ dto
│  │  ├─ model
│  │  ├─ repository
│  │  └─ service/auth/strategy
│  └─ service
└─ src/main/resources
   ├─ application.yml
   ├─ schema.sql                          # 本地 H2 启动建表
   └─ data.sql                            # 本地 H2 演示数据
```

## 2. member 登录接口（策略模式）

登录入口：`POST /api/v1/member/auth/login`

通过 `grantType` 路由到不同策略：

- `password` -> `PasswordLoginStrategy`
- `sms` -> `SmsLoginStrategy`
- `email` -> `EmailLoginStrategy`

请求示例（密码登录）：

```json
{
  "grantType": "password",
  "username": "demo_user",
  "password": "123456"
}
```

请求示例（短信登录）：

```json
{
  "grantType": "sms",
  "phone": "13800000000",
  "smsCode": "123456"
}
```

请求示例（邮箱登录）：

```json
{
  "grantType": "email",
  "email": "demo@example.com",
  "emailCode": "123456"
}
```

响应包含 `accessToken`，可用于个人中心接口：

- `GET /api/v1/member/profile/me`
- Header: `Authorization: Bearer <accessToken>`

## 3. RAG 接口（独立域）

- `POST /api/v1/rag/query`

示例请求：

```json
{
  "question": "How to use FastAPI for backend?"
}
```

## 4. SQL 管理（对齐 cde-base 风格）

member 模块 SQL 在：

- `java-backend/script/sql/member/baseline`
- `java-backend/script/sql/member/migration`
- `java-backend/script/sql/member/rollback`
- `java-backend/script/sql/member/data`
- `java-backend/script/sql/member/CHANGELOG.md`

## 5. 配置说明

`application.yml` 支持以下关键配置：

- 数据库：`spring.datasource.*`
- member 登录：
  - `app.member.auth.session-ttl-seconds`
  - `app.member.auth.allow-mock-verify-code`
  - `app.member.auth.mock-verify-code`
  - `app.member.auth.allow-plain-password`（仅建议本地联调）
- RAG：`app.rag.*`

示例环境变量见：

- `.env.example`
- `java-backend/.env.example`

## 6. 启动

```bash
cd java-backend
mvn spring-boot:run
```

> 当前环境如果没有 `mvn`，请先安装 Maven，或使用 IDEA 的 Maven 插件运行。
