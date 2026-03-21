package com.personalblog.ragbackend.member.repository.jdbc;

import com.personalblog.ragbackend.member.model.MemberVerifyCode;
import com.personalblog.ragbackend.member.repository.MemberVerifyCodeRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * JdbcMemberVerifyCodeRepository 是仓储层 JDBC 实现，负责数据库访问。
 */
@Repository
public class JdbcMemberVerifyCodeRepository implements MemberVerifyCodeRepository {
    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<MemberVerifyCode> ROW_MAPPER = (rs, rowNum) -> new MemberVerifyCode(
            rs.getLong("id"),
            rs.getString("target_type"),
            rs.getString("target_value"),
            rs.getString("verify_code"),
            toLocalDateTime(rs.getTimestamp("expires_at")),
            rs.getBoolean("used"),
            toLocalDateTime(rs.getTimestamp("created_at"))
    );

    public JdbcMemberVerifyCodeRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<MemberVerifyCode> findLatestAvailable(String targetType, String targetValue, LocalDateTime now) {
        try {
            MemberVerifyCode code = jdbcTemplate.queryForObject(
                    """
                    select verify_code_id as id, target_type, target_value, verify_code, expires_at, used, created_at
                    from sys_user_verify_code
                    where target_type = ?
                      and target_value = ?
                      and used = 0
                      and deleted = 0
                      and expires_at > ?
                    order by created_at desc
                    limit 1
                    """,
                    ROW_MAPPER,
                    targetType,
                    targetValue,
                    Timestamp.valueOf(now)
            );
            return Optional.ofNullable(code);
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public void markUsed(Long id) {
        jdbcTemplate.update(
                """
                update sys_user_verify_code
                set used = 1
                where verify_code_id = ? and deleted = 0
                """,
                id
        );
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}

