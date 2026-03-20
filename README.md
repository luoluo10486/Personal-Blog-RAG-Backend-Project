# Personal-Blog-RAG-Backend-Project

当前仓库拆分为三个同级后端模块：

- `java-backend`：RAG 服务（博客内容检索与问答编排）
- `common`：公共组件库（Redis、Sa-Token、MyBatis-Plus 通用配置）
- `member-backend`：个人中心服务（登录认证与 profile）

## 目录结构

```text
Personal-Blog-RAG-Backend-Project-main
├─ java-backend
├─ common
├─ member-backend
└─ script
```

## 构建

推荐在仓库根目录聚合构建（会自动先构建 `common`）：

```bash
cd Personal-Blog-RAG-Backend-Project-main
mvn -pl member-backend -am clean package
```

## 启动

### java-backend

```bash
cd java-backend
mvn spring-boot:run
```

### member-backend

```bash
cd member-backend
mvn spring-boot:run
```

## SQL 目录（手动执行）

- `script/sql/member/baseline`
- `script/sql/member/migration`
- `script/sql/member/rollback`
- `script/sql/member/data`
- `script/sql/member/local`

> 说明：当前 SQL 作为备份/迁移脚本，默认不自动初始化数据库。
