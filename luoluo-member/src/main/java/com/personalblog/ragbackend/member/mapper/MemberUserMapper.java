package com.personalblog.ragbackend.member.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.personalblog.ragbackend.member.domain.MemberUser;

/**
 * 用户实体映射层，负责 `sys_user` 表的基础持久化。
 */
public interface MemberUserMapper extends BaseMapper<MemberUser> {
}
