-- ============================================================
-- 脚本类型：基线脚本
-- 版本：B20260319
-- 模块：member
-- 说明：会员认证中心完整表结构
-- 日期：2026-03-19
-- ============================================================

create table if not exists sys_user (
    user_id         bigint not null auto_increment comment '用户主键',
    username        varchar(64) not null comment '用户名',
    password_hash   varchar(255) not null comment '密码哈希',
    phone           varchar(32) default null comment '手机号',
    email           varchar(128) default null comment '邮箱',
    display_name    varchar(64) default null comment '显示名称',
    status          varchar(16) not null default 'ACTIVE' comment '状态：ACTIVE/LOCKED/DISABLED',
    deleted         tinyint not null default 0 comment '逻辑删除标记：0 否，1 是',
    created_at      datetime not null default current_timestamp comment '创建时间',
    updated_at      datetime not null default current_timestamp on update current_timestamp comment '更新时间',
    primary key (user_id),
    unique key uk_sys_user_username_deleted (username, deleted),
    unique key uk_sys_user_phone_deleted (phone, deleted),
    unique key uk_sys_user_email_deleted (email, deleted),
    key idx_sys_user_status_deleted (status, deleted)
) engine=innodb comment='会员中心用户表';

create table if not exists sys_user_verify_code (
    verify_code_id  bigint not null auto_increment comment '验证码主键',
    target_type     varchar(16) not null comment '目标类型：sms/email',
    target_value    varchar(128) not null comment '手机号或邮箱',
    verify_code     varchar(32) not null comment '验证码',
    expires_at      datetime not null comment '过期时间',
    used            tinyint not null default 0 comment '使用标记：0 否，1 是',
    deleted         tinyint not null default 0 comment '逻辑删除标记：0 否，1 是',
    created_at      datetime not null default current_timestamp comment '创建时间',
    primary key (verify_code_id),
    key idx_sys_user_verify_code_target (target_type, target_value, used, deleted)
) engine=innodb comment='会员中心验证码表';

create table if not exists sys_user_login_session (
    session_id      bigint not null auto_increment comment '会话主键',
    user_id         bigint not null comment '用户ID',
    token           varchar(128) not null comment '访问令牌',
    grant_type      varchar(16) not null comment '授权类型：password/sms/email',
    expires_at      datetime not null comment '过期时间',
    revoked         tinyint not null default 0 comment '撤销标记：0 否，1 是',
    deleted         tinyint not null default 0 comment '逻辑删除标记：0 否，1 是',
    created_at      datetime not null default current_timestamp comment '创建时间',
    primary key (session_id),
    unique key uk_sys_user_login_session_token_deleted (token, deleted),
    key idx_sys_user_login_session_user_id_deleted (user_id, deleted)
) engine=innodb comment='会员中心登录会话表';
