-- ============================================================
-- 脚本类型：迁移脚本
-- 版本：V20260322
-- 模块：member
-- 说明：增强旧验证码表字段，为平台化重构前的数据迁移补足上下文
-- 日期：2026-03-22
-- ============================================================

alter table sys_user_verify_code
    add column if not exists biz_type varchar(64) default null comment '业务类型' after verify_code_id,
    add column if not exists biz_id varchar(64) default null comment '业务ID' after biz_type,
    add column if not exists user_type varchar(16) default 'USER' comment '用户类型：USER/ADMIN/SUPER_ADMIN' after biz_id,
    add column if not exists message_channel varchar(16) not null default 'SMS' comment '发送渠道：SMS/EMAIL' after target_value,
    add column if not exists template_id varchar(100) default null comment '模板ID' after message_channel,
    add column if not exists provider varchar(64) default null comment '服务提供商' after template_id,
    add column if not exists request_id varchar(128) default null comment '请求ID' after provider,
    add column if not exists used_at datetime default null comment '使用时间' after used,
    add column if not exists remark varchar(500) default null comment '备注' after created_at;

create index idx_sys_user_verify_code_biz_type
    on sys_user_verify_code (biz_type, deleted);

create index idx_sys_user_verify_code_request_id
    on sys_user_verify_code (request_id);
