package com.personalblog.ragbackend.common.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalblog.ragbackend.common.auth.domain.AuthSession;

/**
 * 认证会话映射层，负责 `sys_auth_session` 表的基础持久化。
 */
public interface AuthSessionMapper extends BaseMapper<AuthSession> {
}
