-- ============================================================
-- 脚本类型：迁移脚本
-- 版本：V20260320
-- 模块：member
-- 说明：为会员认证相关表新增逻辑删除字段
-- 日期：2026-03-20
-- ============================================================

alter table sys_user
    add column if not exists deleted tinyint not null default 0 comment '逻辑删除标记：0 否，1 是' after status;

alter table sys_user_verify_code
    add column if not exists deleted tinyint not null default 0 comment '逻辑删除标记：0 否，1 是' after used;

alter table sys_user_login_session
    add column if not exists deleted tinyint not null default 0 comment '逻辑删除标记：0 否，1 是' after revoked;
