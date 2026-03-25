# member 数据库变更日志

## [M20260325] - 2026-03-25

### Migration

- `M20260325__plain_verify_code_and_drop_indexes.sql`：将 `sys_verify_code_record.code_digest` 调整为 `code_value` 明文存储，并移除 `sys_user`、`sys_verify_code_record`、`sys_auth_session` 的二级索引。

## [B20260319] - 2026-03-23

### Baseline

- `B20260319__full_schema.sql`：会员认证中心的完整基线表结构。

### Rollback

- `R20260319__full_schema.sql`：回滚当前完整表结构。

## 当前目录约定

- `baseline`：保留可直接初始化最新结构的基线脚本
- `rollback`：保留回滚当前结构的脚本
- `migration`：保留对已存在数据库执行的增量迁移脚本
