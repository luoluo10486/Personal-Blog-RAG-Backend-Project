# remand.md（后端）

## 当前目标

- 维护 `luoluo-common` 独立模块
- `luoluo-member` 通过 Maven 依赖导入 `luoluo-common`
- SQL 脚本统一放在仓库根 `script/sql/member`

## 模块关系

- `luoluo-admin`：统一启动入口，负责装配所有业务模块
- `luoluo-common`：共享能力，不直接暴露业务接口
- `luoluo-member`：登录认证、会话、个人中心能力
- `luoluo-rag`：RAG 相关能力
- `luoluo-system`：公共系统接口能力

## 构建约定

```bash
mvn -pl luoluo-admin -am clean package
```

## 数据库约定

- 逻辑删除字段：`deleted`
- JDBC 查询默认加 `deleted = 0`
- SQL 由开发者手动执行，不依赖自动初始化
