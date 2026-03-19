-- ============================================================
-- Script Type: ROLLBACK
-- Version: R20260319
-- Module: member
-- Description: Rollback member auth tables
-- Date: 2026-03-19
-- ============================================================

drop table if exists member_login_session;
drop table if exists member_verify_code;
drop table if exists member_user;
