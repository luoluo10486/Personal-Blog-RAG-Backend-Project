-- ============================================================
-- 脚本类型：回滚脚本
-- 版本：R20260322_01
-- 模块：member
-- 说明：回滚平台化认证基础设施重构
-- 注意：由于明文令牌和验证码已转换为摘要，回滚后仅保留摘要值，无法恢复原始明文。
-- 日期：2026-03-22
-- ============================================================

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
    verify_code     varchar(64) not null comment '验证码占位值（摘要）',
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

insert into sys_user_verify_code (
    verify_code_id,
    biz_type,
    biz_id,
    user_type,
    target_type,
    target_value,
    message_channel,
    template_id,
    provider,
    request_id,
    verify_code,
    expires_at,
    used,
    used_at,
    deleted,
    created_at,
    remark
)
select
    record_id,
    biz_type,
    biz_id,
    'USER',
    target_type,
    target_value,
    channel,
    template_id,
    provider,
    request_id,
    code_digest,
    expires_at,
    used,
    used_at,
    deleted,
    created_at,
    remark
from sys_verify_code_record
where not exists (
    select 1 from sys_user_verify_code
);

drop table if exists sys_verify_code_record;

create table if not exists sys_user_login_session (
    session_id      bigint not null auto_increment comment '会话主键',
    user_id         bigint not null comment '用户ID',
    token           varchar(64) not null comment '令牌占位值（摘要）',
    grant_type      varchar(16) not null comment '授权类型：password/sms/email',
    expires_at      datetime not null comment '过期时间',
    revoked         tinyint not null default 0 comment '撤销标记：0 否，1 是',
    deleted         tinyint not null default 0 comment '逻辑删除标记：0 否，1 是',
    created_at      datetime not null default current_timestamp comment '创建时间',
    primary key (session_id),
    unique key uk_sys_user_login_session_token_deleted (token, deleted),
    key idx_sys_user_login_session_user_id_deleted (user_id, deleted)
) engine=innodb comment='系统用户登录会话表';

insert into sys_user_login_session (
    session_id,
    user_id,
    token,
    grant_type,
    expires_at,
    revoked,
    deleted,
    created_at
)
select
    session_id,
    subject_id,
    token_digest,
    lower(login_type),
    expires_at,
    revoked,
    deleted,
    created_at
from sys_auth_session
where not exists (
    select 1 from sys_user_login_session
);

drop table if exists sys_auth_session;
