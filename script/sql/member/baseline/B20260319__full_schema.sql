-- ============================================================
-- 脚本类型：基线脚本
-- 版本：B20260319
-- 模块：member
-- 说明：会员认证中心完整表结构（平台化认证版本）
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

create table if not exists sys_verify_code_record (
    record_id       bigint not null auto_increment comment '记录主键',
    biz_type        varchar(64) not null comment '业务类型',
    biz_id          varchar(64) default null comment '业务ID',
    subject_type    varchar(32) default null comment '主体类型，如 SYS_USER',
    subject_id      bigint default null comment '主体ID',
    target_type     varchar(16) not null comment '目标类型：sms/email',
    target_value    varchar(128) not null comment '手机号或邮箱',
    channel         varchar(16) not null default 'SMS' comment '发送渠道：SMS/EMAIL',
    template_id     varchar(100) default null comment '模板ID',
    provider        varchar(64) default null comment '服务提供商',
    request_id      varchar(128) default null comment '请求ID',
    code_digest     varchar(64) not null comment '验证码摘要',
    expires_at      datetime not null comment '过期时间',
    used            tinyint not null default 0 comment '使用标记：0 否，1 是',
    used_at         datetime default null comment '使用时间',
    deleted         tinyint not null default 0 comment '逻辑删除标记：0 否，1 是',
    created_at      datetime not null default current_timestamp comment '创建时间',
    remark          varchar(500) default null comment '备注',
    primary key (record_id),
    key idx_sys_verify_code_record_lookup (biz_type, target_type, target_value, used, deleted),
    key idx_sys_verify_code_record_subject (subject_type, subject_id, deleted),
    key idx_sys_verify_code_record_request_id (request_id)
) engine=innodb comment='验证码记录表';

create table if not exists sys_auth_session (
    session_id      bigint not null auto_increment comment '会话主键',
    subject_id      bigint not null comment '主体ID',
    subject_type    varchar(32) not null comment '主体类型，如 SYS_USER',
    login_type      varchar(32) not null comment '登录方式：PASSWORD/SMS/EMAIL',
    token_digest    varchar(64) not null comment '访问令牌摘要',
    expires_at      datetime not null comment '过期时间',
    revoked         tinyint not null default 0 comment '撤销标记：0 否，1 是',
    deleted         tinyint not null default 0 comment '逻辑删除标记：0 否，1 是',
    created_at      datetime not null default current_timestamp comment '创建时间',
    last_active_at  datetime not null default current_timestamp comment '最后活跃时间',
    device_type     varchar(32) default null comment '设备类型',
    client_ip       varchar(64) default null comment '客户端IP',
    primary key (session_id),
    unique key uk_sys_auth_session_token_digest_deleted (token_digest, deleted),
    key idx_sys_auth_session_subject_deleted (subject_type, subject_id, deleted),
    key idx_sys_auth_session_expires_at (expires_at)
) engine=innodb comment='认证会话表';
