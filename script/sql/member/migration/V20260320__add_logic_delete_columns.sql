-- ============================================================
-- Script Type: MIGRATION
-- Version: V20260320
-- Module: member
-- Description: Add logical delete columns for member auth tables
-- Date: 2026-03-20
-- ============================================================

alter table member_user
    add column if not exists deleted tinyint not null default 0 comment 'Logical delete flag: 0 no, 1 yes' after status;

alter table member_verify_code
    add column if not exists deleted tinyint not null default 0 comment 'Logical delete flag: 0 no, 1 yes' after used;

alter table member_login_session
    add column if not exists deleted tinyint not null default 0 comment 'Logical delete flag: 0 no, 1 yes' after revoked;
