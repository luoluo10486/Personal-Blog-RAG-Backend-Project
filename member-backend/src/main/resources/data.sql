insert into sys_user (username, password_hash, phone, email, display_name, status, deleted)
select 'demo_user', '123456', '13800000000', 'demo@example.com', 'Demo User', 'ACTIVE', false
where not exists (
    select 1 from sys_user where username = 'demo_user' and deleted = false
);

insert into sys_user_verify_code (target_type, target_value, verify_code, expires_at, used, deleted)
select 'sms', '13800000000', '123456', dateadd('HOUR', 2, current_timestamp), false, false
where not exists (
    select 1
    from sys_user_verify_code
    where target_type = 'sms'
      and target_value = '13800000000'
      and used = false
      and deleted = false
);

insert into sys_user_verify_code (target_type, target_value, verify_code, expires_at, used, deleted)
select 'email', 'demo@example.com', '123456', dateadd('HOUR', 2, current_timestamp), false, false
where not exists (
    select 1
    from sys_user_verify_code
    where target_type = 'email'
      and target_value = 'demo@example.com'
      and used = false
      and deleted = false
);
