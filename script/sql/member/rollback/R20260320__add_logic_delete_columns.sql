-- ============================================================
-- 脚本类型：回滚脚本
-- 版本：R20260320
-- 模块：member
-- 说明：回滚会员认证相关表的逻辑删除字段
-- 日期：2026-03-20
-- ============================================================

alter table sys_user_login_session drop column if exists deleted;
alter table sys_user_verify_code drop column if exists deleted;
alter table sys_user drop column if exists deleted;
