insert into t_user (username, password_hash, phone, email, display_name, user_type, status, deleted)
select 'demo_user', '123456', '13800000000', 'demo@example.com', 'Demo User', 'USER', 'ACTIVE', 0
where not exists (
    select 1 from t_user where username = 'demo_user' and deleted = 0
);

insert into t_verify_code_record (
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
    code_value,
    expires_at,
    used,
    deleted,
    remark
)
select 'LOGIN', null, 'SYS_USER', null, 'sms', '13800000000', 'SMS', null, 'mock-provider', 'REQ-SMS-DEMO', '123456', current_timestamp + interval '2 hours', false, 0, '婕旂ず鐭俊鐧诲綍楠岃瘉鐮佽褰?
where not exists (
    select 1
    from t_verify_code_record
    where biz_type = 'LOGIN'
      and target_type = 'sms'
      and target_value = '13800000000'
      and deleted = 0
);

insert into t_verify_code_record (
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
    code_value,
    expires_at,
    used,
    deleted,
    remark
)
select 'LOGIN', null, 'SYS_USER', null, 'email', 'demo@example.com', 'EMAIL', null, 'mock-provider', 'REQ-EMAIL-DEMO', '123456', current_timestamp + interval '2 hours', false, 0, '婕旂ず閭鐧诲綍楠岃瘉鐮佽褰?
where not exists (
    select 1
    from t_verify_code_record
    where biz_type = 'LOGIN'
      and target_type = 'email'
      and target_value = 'demo@example.com'
      and deleted = 0
);


