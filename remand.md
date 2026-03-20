# remand.md（后端）

## 当前目标

- 维护 `common` 独立模块
- `member-backend` 通过 Maven 依赖导入 common
- SQL 脚本统一放在仓库根 `script/sql/member`

## 模块关系

- `common`：共享能力，不直接暴露业务接口
- `member-backend`：登录认证、会话、个人中心接口
- `java-backend`：RAG 相关接口

## 构建约定

```bash
mvn -pl member-backend -am clean package
```

## 数据库约定

- 逻辑删除字段：`deleted`
- JDBC 查询默认加 `deleted = 0`
- SQL 由开发者手动执行，不依赖自动初始化
