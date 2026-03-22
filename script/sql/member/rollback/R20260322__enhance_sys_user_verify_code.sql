-- ============================================================
-- 脚本类型：回滚脚本
-- 版本：R20260322
-- 模块：member
-- 说明：回滚旧验证码表增强字段
-- 日期：2026-03-22
-- ============================================================

drop index idx_sys_user_verify_code_biz_type on sys_user_verify_code;
drop index idx_sys_user_verify_code_request_id on sys_user_verify_code;

alter table sys_user_verify_code
    drop column remark,
    drop column used_at,
    drop column request_id,
    drop column provider,
    drop column template_id,
    drop column message_channel,
    drop column user_type,
    drop column biz_id,
    drop column biz_type;
