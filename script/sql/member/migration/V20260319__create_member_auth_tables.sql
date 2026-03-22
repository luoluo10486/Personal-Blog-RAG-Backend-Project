-- ============================================================
-- 脚本类型：迁移脚本
-- 版本：V20260319
-- 模块：member
-- 说明：创建会员认证相关基础表
-- 日期：2026-03-19
-- ============================================================

create table if not exists sys_user (
    user_id         bigint not null auto_increment comment '用户主键',
    username        varchar(64) not null comment '用户名',
    password_hash   varchar(255) not null comment '密码哈希',
    phone           varchar(32) default null comment '手机号',
    email           varchar(128) default null comment '邮箱',
    display_name    varchar(64) default null comment '显示名称',
    user_type       varchar(16) not null default 'USER' comment '用户类型：USER/ADMIN/SUPER_ADMIN',
    status          varchar(16) not null default 'ACTIVE' comment '状态：ACTIVE/LOCKED/DISABLED',
    deleted         tinyint not null default 0 comment '逻辑删除标记：0 否，1 是',
    created_at      datetime not null default current_timestamp comment '创建时间',
    updated_at      datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (user_id),
    unique key uk_sys_user_username_deleted (username, deleted),
    unique key uk_sys_user_phone_deleted (phone, deleted),
    unique key uk_sys_user_email_deleted (email, deleted),
    key idx_sys_user_status_deleted (status, deleted),
    key idx_sys_user_user_type_deleted (user_type, deleted)
) engine=innodb comment='系统用户表';

create table if not exists sys_user_verify_code (
    verify_code_id  bigint not null auto_increment comment '验证码主键',
    biz_type        varchar(64) default null comment '业务类型',
    biz_id          varchar(64) default null comment '业务ID',
    user_type       varchar(16) default 'USER' comment '用户类型：USER/ADMIN/SUPER_ADMIN',
    target_type     varchar(16) not null comment '目标类型：sms/email',
    target_value    varchar(128) not null comment '手机号或邮箱',
    message_channel varchar(16) not null default 'SMS' comment '发送渠道：SMS/EMAIL',
    template_id     varchar(100) default null comment '模板ID',
    provider        varchar(64) default null comment '服务提供商',
    request_id      varchar(128) default null comment '请求ID',
    verify_code     varchar(32) not null comment '验证码明文',
    expires_at      datetime not null comment '过期时间',
    used            tinyint not null default 0 comment '使用标记：0 否，1 是',
    used_at         datetime default null comment '使用时间',
    deleted         tinyint not null default 0 comment '逻辑删除标记：0 否，1 是',
    created_at      datetime not null default current_timestamp comment '创建时间',
    remark          varchar(500) default null comment '备注',
    primary key (verify_code_id),
    key idx_sys_user_verify_code_target (target_type, target_value, used, deleted),
    key idx_sys_user_verify_code_biz_type (biz_type, deleted),
    key idx_sys_user_verify_code_request_id (request_id)
) engine=innodb comment='系统用户验证码表';

create table if not exists sys_user_login_session (
    session_id      bigint not null auto_increment comment '会话主键',
    user_id         bigint not null comment '用户ID',
    token           varchar(128) not null comment '访问令牌明文',
    grant_type      varchar(16) not null comment '授权类型：password/sms/email',
    expires_at      datetime not null comment '过期时间',
    revoked         tinyint not null default 0 comment '撤销标记：0 否，1 是',
    deleted         tinyint not null default 0 comment '逻辑删除标记：0 否，1 是',
    created_at      datetime not null default current_timestamp comment '创建时间',
    primary key (session_id),
    unique key uk_sys_user_login_session_token_deleted (token, deleted),
    key idx_sys_user_login_session_user_id_deleted (user_id, deleted)
) engine=innodb comment='系统用户登录会话表';
