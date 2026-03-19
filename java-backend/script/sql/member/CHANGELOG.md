# member 数据库变更日志

## [B20260319] - 2026-03-19

### Baseline
- `B20260319__full_schema.sql`: 初始化会员中心认证所需完整表结构

## [V20260319] - 2026-03-19

### Added
- `V20260319__create_member_auth_tables.sql`: 新增会员登录用户表、验证码表、会话表

### Rollback
- `R20260319__create_member_auth_tables.sql`: 回退会员认证相关表
