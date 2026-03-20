# member 数据库变更日志

## [B20260319] - 2026-03-19

### Baseline

- `B20260319__full_schema.sql`：初始化会员认证相关完整表结构

## [V20260319] - 2026-03-19

### Migration

- `V20260319__create_member_auth_tables.sql`：创建用户表、验证码表、会话表

### Rollback

- `R20260319__create_member_auth_tables.sql`：回滚 `V20260319`

## [V20260320] - 2026-03-20

### Migration

- `V20260320__add_logic_delete_columns.sql`：为认证相关表新增逻辑删除字段 `deleted`

### Rollback

- `R20260320__add_logic_delete_columns.sql`：回滚逻辑删除字段
