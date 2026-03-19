# Personal-Blog-RAG-Backend-Project

当前仓库拆分为两个同级后端项目：

- `java-backend`：RAG 服务（博客内容检索与问答编排）
- `member-backend`：个人中心服务（登录认证与 profile）

## 目录结构

```text
Personal-Blog-RAG-Backend-Project
├─ java-backend
└─ member-backend
```

## java-backend（RAG）

启动：

```bash
cd java-backend
mvn spring-boot:run
```

主要接口：

- `GET /api/v1/health`
- `GET /api/v1/posts`
- `GET /api/v1/posts/{slug}`
- `POST /api/v1/rag/query`

## member-backend（登录/个人中心）

启动：

```bash
cd member-backend
mvn spring-boot:run
```

主要接口：

- `POST /api/v1/member/auth/login`
- `GET /api/v1/member/profile/me`

登录策略（grantType）：

- `password`
- `sms`
- `email`

SQL（按 cde-base 风格）：

- `member-backend/script/sql/member/baseline`
- `member-backend/script/sql/member/migration`
- `member-backend/script/sql/member/rollback`
- `member-backend/script/sql/member/data`

## 说明

- 两个项目是独立服务，端口建议分别为 `8080`（rag）和 `8081`（member）。
- `member-backend` 默认内置 H2 初始化数据，可直接联调登录。
