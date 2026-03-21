package com.personalblog.ragbackend.member.mapper;

import com.personalblog.ragbackend.member.domain.MemberSession;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;

/**
 * MemberSessionMapper 映射器，负责登录会话表访问。
 */
@Mapper
public interface MemberSessionMapper {

    @Insert("""
            insert into sys_user_login_session
            (user_id, token, grant_type, expires_at, revoked, deleted, created_at)
            values (#{userId}, #{token}, #{grantType}, #{expiresAt}, #{revoked}, 0, #{createdAt})
            """)
    int insertSession(
            @Param("userId") Long userId,
            @Param("token") String token,
            @Param("grantType") String grantType,
            @Param("expiresAt") LocalDateTime expiresAt,
            @Param("revoked") boolean revoked,
            @Param("createdAt") LocalDateTime createdAt
    );

    @Select("""
            select session_id as id, user_id as userId, token, grant_type as grantType,
                   expires_at as expiresAt, revoked, created_at as createdAt
            from sys_user_login_session
            where token = #{token}
              and revoked = 0
              and deleted = 0
              and expires_at > current_timestamp
            order by session_id desc
            limit 1
            """)
    MemberSession selectValidByToken(@Param("token") String token);
}

