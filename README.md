# Personal-Blog-RAG-Backend-Project

个人博客 RAG 后端项目。

当前已提供 Java 版后端骨架（Spring Boot）：

- 目录：`java-backend`
- 技术栈：Java 21 + Spring Boot 3
- 设计：后端只做业务编排，通过 HTTP 调用外部 RAG API（检索 / 生成）

## 1. 项目结构

```text
java-backend
├─ pom.xml
├─ src/main/java/com/personalblog/ragbackend
│  ├─ config          # 配置
│  ├─ controller      # API 接口层
│  ├─ client          # 外部 RAG API 调用
│  ├─ repository      # 数据访问（当前内存版）
│  ├─ service         # 业务层
│  └─ model/dto       # 模型与请求响应
└─ src/main/resources/application.yml
```

## 2. 已有接口

- `GET /api/v1/health` 健康检查
- `GET /api/v1/posts` 文章列表
- `GET /api/v1/posts/{slug}` 文章详情
- `POST /api/v1/rag/query` RAG 问答入口

示例请求：

```json
{
  "question": "How to use FastAPI for backend?"
}
```

## 3. 配置外部 RAG API

在 `java-backend/src/main/resources/application.yml` 中配置：

- `app.rag.enabled=true`
- `app.rag.retrieval-url`：检索服务接口
- `app.rag.llm-url`：生成服务接口
- `app.rag.api-key`：鉴权密钥（可选）

当未配置外部 API 时，项目会自动回退到本地简易检索与回答，方便先联调前后端。

## 4. 启动方式

先进入目录：

```bash
cd java-backend
```

使用 Maven 启动：

```bash
mvn spring-boot:run
```

> 当前环境若没有安装 `mvn`，请先安装 Maven，或使用 IDE 的 Maven 插件运行。
