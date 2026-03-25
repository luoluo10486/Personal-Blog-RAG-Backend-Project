-- ============================================================
-- 脚本类型：迁移脚本
-- 版本：M20260325
-- 模块：member
-- 说明：验证码改为明文存储，并移除认证相关表的二级索引
-- 日期：2026-03-25
-- ============================================================

alter table sys_verify_code_record
    change column code_digest code_value varchar(64) not null comment '验证码明文';

alter table sys_user
    drop index uk_sys_user_username_deleted,
    drop index uk_sys_user_phone_deleted,
    drop index uk_sys_user_email_deleted,
    drop index idx_sys_user_status_deleted,
    drop index idx_sys_user_user_type_deleted;

alter table sys_verify_code_record
    drop index idx_sys_verify_code_record_lookup,
    drop index idx_sys_verify_code_record_subject,
    drop index idx_sys_verify_code_record_request_id;

alter table sys_auth_session
    drop index uk_sys_auth_session_token_digest_deleted,
    drop index idx_sys_auth_session_subject_deleted,
    drop index idx_sys_auth_session_expires_at;
