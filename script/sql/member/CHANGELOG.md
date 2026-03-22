# member 数据库变更日志

## [B20260319] - 2026-03-19

### Baseline

- `B20260319__full_schema.sql`：初始化会员认证相关完整表结构（平台化认证版本）

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

## [V20260322] - 2026-03-22

### Migration

- `V20260322__add_user_type_to_sys_user.sql`：为 `sys_user` 表新增 `user_type` 字段并补充索引
- `V20260322__enhance_sys_user_verify_code.sql`：增强旧验证码表，补充业务类型与发送上下文字段
- `V20260322_01__refactor_auth_platform_infrastructure.sql`：将登录会话和验证码能力重构为平台化认证基础设施，迁移到 `sys_auth_session` 与 `sys_verify_code_record`

### Rollback

- `R20260322__add_user_type_to_sys_user.sql`：回滚 `user_type` 字段与索引
- `R20260322__enhance_sys_user_verify_code.sql`：回滚旧验证码表增强字段
- `R20260322_01__refactor_auth_platform_infrastructure.sql`：回滚平台化认证基础设施重构（仅保留摘要值，无法恢复原始明文）
