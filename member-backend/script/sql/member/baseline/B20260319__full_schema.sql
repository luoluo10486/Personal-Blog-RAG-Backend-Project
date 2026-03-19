-- ============================================================
-- Script Type: BASELINE
-- Version: B20260319
-- Module: member
-- Description: Full schema for member auth center
-- Date: 2026-03-19
-- ============================================================

create table if not exists member_user (
    id              bigint not null auto_increment comment 'Primary key',
    username        varchar(64) not null comment 'Username',
    password_hash   varchar(255) not null comment 'Password hash',
    phone           varchar(32) default null comment 'Phone number',
    email           varchar(128) default null comment 'Email',
    display_name    varchar(64) default null comment 'Display name',
    status          varchar(16) not null default 'ACTIVE' comment 'Status: ACTIVE/LOCKED/DISABLED',
    created_at      datetime not null default current_timestamp comment 'Created time',
    updated_at      datetime not null default current_timestamp on update current_timestamp comment 'Updated time',
    primary key (id),
    unique key uk_member_user_username (username),
    unique key uk_member_user_phone (phone),
    unique key uk_member_user_email (email)
) engine=innodb comment='Member center users';

create table if not exists member_verify_code (
    id              bigint not null auto_increment comment 'Primary key',
    target_type     varchar(16) not null comment 'Target type: sms/email',
    target_value    varchar(128) not null comment 'Phone or email',
    verify_code     varchar(32) not null comment 'Verification code',
    expires_at      datetime not null comment 'Expire time',
    used            tinyint not null default 0 comment 'Used flag: 0 no, 1 yes',
    created_at      datetime not null default current_timestamp comment 'Created time',
    primary key (id),
    key idx_member_verify_code_target (target_type, target_value, used)
) engine=innodb comment='Member center verification code';

create table if not exists member_login_session (
    id              bigint not null auto_increment comment 'Primary key',
    user_id         bigint not null comment 'User id',
    token           varchar(128) not null comment 'Access token',
    grant_type      varchar(16) not null comment 'Grant type: password/sms/email',
    expires_at      datetime not null comment 'Expire time',
    revoked         tinyint not null default 0 comment 'Revoked flag: 0 no, 1 yes',
    created_at      datetime not null default current_timestamp comment 'Created time',
    primary key (id),
    unique key uk_member_login_session_token (token),
    key idx_member_login_session_user_id (user_id)
) engine=innodb comment='Member center login session';
