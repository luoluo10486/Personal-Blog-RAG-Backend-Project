-- ============================================================
-- 脚本类型：回滚脚本
-- 版本：R20260322
-- 模块：member
-- 说明：回滚系统用户表 user_type 字段
-- 日期：2026-03-22
-- ============================================================

drop index idx_sys_user_user_type_deleted on sys_user;

alter table sys_user
    drop column user_type;
