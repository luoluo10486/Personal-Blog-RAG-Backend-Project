-- ============================================================
-- Script Type: migration
-- Version: M20260327
-- Module: member
-- Description: restore key authentication indexes
-- Date: 2026-03-27
-- ============================================================

alter table sys_user
    add unique index uk_sys_user_username_deleted (username, deleted),
    add unique index uk_sys_user_phone_deleted (phone, deleted),
    add unique index uk_sys_user_email_deleted (email, deleted),
    add index idx_sys_user_status_deleted (status, deleted),
    add index idx_sys_user_user_type_deleted (user_type, deleted);

alter table sys_verify_code_record
    add index idx_sys_verify_code_record_lookup (biz_type, target_type, target_value, code_value, used, deleted, expires_at),
    add index idx_sys_verify_code_record_subject (subject_type, subject_id, deleted),
    add index idx_sys_verify_code_record_request_id (request_id);

alter table sys_auth_session
    add unique index uk_sys_auth_session_token_digest_deleted (token_digest, deleted),
    add index idx_sys_auth_session_subject_deleted (subject_id, subject_type, revoked, deleted),
    add index idx_sys_auth_session_expires_at (expires_at);
