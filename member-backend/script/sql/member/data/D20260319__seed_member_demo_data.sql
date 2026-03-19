-- ============================================================
-- Script Type: DATA
-- Version: D20260319
-- Module: member
-- Description: Seed demo member user for local verification
-- Date: 2026-03-19
-- ============================================================

insert into member_user (username, password_hash, phone, email, display_name, status)
select 'demo_user', '123456', '13800000000', 'demo@example.com', 'Demo User', 'ACTIVE'
where not exists (
    select 1 from member_user where username = 'demo_user'
);

insert into member_verify_code (target_type, target_value, verify_code, expires_at, used)
select 'sms', '13800000000', '123456', date_add(now(), interval 2 hour), 0
where not exists (
    select 1
    from member_verify_code
    where target_type = 'sms'
      and target_value = '13800000000'
      and used = 0
);

insert into member_verify_code (target_type, target_value, verify_code, expires_at, used)
select 'email', 'demo@example.com', '123456', date_add(now(), interval 2 hour), 0
where not exists (
    select 1
    from member_verify_code
    where target_type = 'email'
      and target_value = 'demo@example.com'
      and used = 0
);
