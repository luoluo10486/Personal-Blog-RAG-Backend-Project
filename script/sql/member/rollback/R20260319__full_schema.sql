-- ============================================================
-- 脚本类型：回滚脚本
-- 版本：R20260319
-- 模块：member
-- 说明：回滚当前会员认证中心完整表结构
-- 日期：2026-03-23
-- ============================================================

drop table if exists sys_auth_session;
drop table if exists sys_verify_code_record;
drop table if exists sys_user;