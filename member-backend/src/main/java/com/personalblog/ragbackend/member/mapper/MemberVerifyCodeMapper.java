package com.personalblog.ragbackend.member.mapper;

import com.personalblog.ragbackend.member.domain.MemberVerifyCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * MemberVerifyCodeMapper 映射器，负责验证码表访问。
 */
@Mapper
public interface MemberVerifyCodeMapper {

    @Select("""
            select verify_code_id as id, target_type as targetType, target_value as targetValue,
                   verify_code as verifyCode, expires_at as expiresAt, used, created_at as createdAt
            from sys_user_verify_code
            where target_type = #{targetType}
              and target_value = #{targetValue}
              and used = 0
              and deleted = 0
              and expires_at > #{now}
            order by created_at desc
            limit 1
            """)
    MemberVerifyCode selectLatestAvailable(
            @Param("targetType") String targetType,
            @Param("targetValue") String targetValue,
            @Param("now") LocalDateTime now
    );

    @Update("""
            update sys_user_verify_code
            set used = 1
            where verify_code_id = #{verifyCodeId}
              and deleted = 0
            """)
    int markUsed(@Param("verifyCodeId") Long verifyCodeId);
}

