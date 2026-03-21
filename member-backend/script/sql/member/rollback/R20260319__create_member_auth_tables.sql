-- ============================================================
-- 脚本类型：回滚脚本
-- 版本：R20260319
-- 模块：member
-- 说明：回滚会员认证相关表
-- 日期：2026-03-19
-- ============================================================

drop table if exists sys_user_login_session;
drop table if exists sys_user_verify_code;
drop table if exists sys_user;
