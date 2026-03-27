# member database changelog

## [M20260327] - 2026-03-27

### Migration

- `M20260327__restore_auth_indexes.sql`: restore key auth-related indexes for login, verify code consumption, and auth session lookups.

## [M20260325] - 2026-03-25

### Migration

- `M20260325__plain_verify_code_and_drop_indexes.sql`: rename `sys_verify_code_record.code_digest` to `code_value` and remove secondary indexes from `sys_user`, `sys_verify_code_record`, and `sys_auth_session`.

## [B20260319] - 2026-03-23

### Baseline

- `B20260319__full_schema.sql`: full baseline schema for the member authentication module.

### Rollback

- `R20260319__full_schema.sql`: rollback script for the current full schema.

## Directory Conventions

- `baseline`: scripts that can initialize the latest schema directly.
- `rollback`: scripts that rollback the current schema.
- `migration`: incremental scripts for existing databases.
