package com.personalblog.ragbackend.member.mapper;

import com.personalblog.ragbackend.member.domain.MemberUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * MemberUserMapper 映射器，负责用户表访问。
 */
@Mapper
public interface MemberUserMapper {

    @Select("""
            select user_id as id, username, password_hash as passwordHash, phone, email, display_name as displayName,
                   status, created_at as createdAt, updated_at as updatedAt
            from sys_user
            where username = #{username}
              and status = 'ACTIVE'
              and deleted = 0
            limit 1
            """)
    MemberUser selectActiveByUsername(@Param("username") String username);

    @Select("""
            select user_id as id, username, password_hash as passwordHash, phone, email, display_name as displayName,
                   status, created_at as createdAt, updated_at as updatedAt
            from sys_user
            where phone = #{phone}
              and status = 'ACTIVE'
              and deleted = 0
            limit 1
            """)
    MemberUser selectActiveByPhone(@Param("phone") String phone);

    @Select("""
            select user_id as id, username, password_hash as passwordHash, phone, email, display_name as displayName,
                   status, created_at as createdAt, updated_at as updatedAt
            from sys_user
            where email = #{email}
              and status = 'ACTIVE'
              and deleted = 0
            limit 1
            """)
    MemberUser selectActiveByEmail(@Param("email") String email);

    @Select("""
            select user_id as id, username, password_hash as passwordHash, phone, email, display_name as displayName,
                   status, created_at as createdAt, updated_at as updatedAt
            from sys_user
            where user_id = #{userId}
              and deleted = 0
            limit 1
            """)
    MemberUser selectById(@Param("userId") Long userId);
}

