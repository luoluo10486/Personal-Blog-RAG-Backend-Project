insert into sys_user (username, password_hash, phone, email, display_name, user_type, status, deleted)
select 'demo_user', '123456', '13800000000', 'demo@example.com', 'Demo User', 'USER', 'ACTIVE', 0
where not exists (
    select 1 from sys_user where username = 'demo_user' and deleted = 0
);

insert into sys_verify_code_record (
    biz_type,
    biz_id,
    subject_type,
    subject_id,
    target_type,
    target_value,
    channel,
    template_id,
    provider,
    request_id,
    code_digest,
    expires_at,
    used,
    deleted,
    remark
)
select 'LOGIN', null, 'SYS_USER', null, 'sms', '13800000000', 'SMS', null, 'mock-provider', 'REQ-SMS-DEMO', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', dateadd('HOUR', 2, current_timestamp), false, 0, '演示短信登录验证码记录'
where not exists (
    select 1
    from sys_verify_code_record
    where biz_type = 'LOGIN'
      and target_type = 'sms'
      and target_value = '13800000000'
      and deleted = 0
);

insert into sys_verify_code_record (
    biz_type,
    biz_id,
    subject_type,
    subject_id,
    target_type,
    target_value,
    channel,
    template_id,
    provider,
    request_id,
    code_digest,
    expires_at,
    used,
    deleted,
    remark
)
select 'LOGIN', null, 'SYS_USER', null, 'email', 'demo@example.com', 'EMAIL', null, 'mock-provider', 'REQ-EMAIL-DEMO', '8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92', dateadd('HOUR', 2, current_timestamp), false, 0, '演示邮箱登录验证码记录'
where not exists (
    select 1
    from sys_verify_code_record
    where biz_type = 'LOGIN'
      and target_type = 'email'
      and target_value = 'demo@example.com'
      and deleted = 0
);
