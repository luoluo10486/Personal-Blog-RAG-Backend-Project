-- ============================================================
-- 脚本类型：迁移脚本
-- 版本：V20260322
-- 模块：member
-- 说明：为系统用户表补充 user_type 字段
-- 日期：2026-03-22
-- ============================================================

alter table sys_user
    add column if not exists user_type varchar(16) not null default 'USER' comment '用户类型：USER/ADMIN/SUPER_ADMIN' after display_name;

create index idx_sys_user_user_type_deleted
    on sys_user (user_type, deleted);
