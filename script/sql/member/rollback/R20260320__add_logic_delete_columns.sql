-- ============================================================
-- Script Type: ROLLBACK
-- Version: R20260320
-- Module: member
-- Description: Rollback logical delete columns for member auth tables
-- Date: 2026-03-20
-- ============================================================

alter table member_login_session drop column if exists deleted;
alter table member_verify_code drop column if exists deleted;
alter table member_user drop column if exists deleted;
