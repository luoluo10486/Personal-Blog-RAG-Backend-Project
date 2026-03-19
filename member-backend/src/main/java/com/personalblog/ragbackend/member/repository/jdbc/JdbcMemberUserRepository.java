package com.personalblog.ragbackend.member.repository.jdbc;

import com.personalblog.ragbackend.member.model.MemberUser;
import com.personalblog.ragbackend.member.repository.MemberUserRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;

@Repository
public class JdbcMemberUserRepository implements MemberUserRepository {
    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<MemberUser> ROW_MAPPER = (rs, rowNum) -> new MemberUser(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("phone"),
            rs.getString("email"),
            rs.getString("display_name"),
            rs.getString("status"),
            toLocalDateTime(rs.getTimestamp("created_at")),
            toLocalDateTime(rs.getTimestamp("updated_at"))
    );

    public JdbcMemberUserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<MemberUser> findActiveByUsername(String username) {
        return findOne(
                """
                select id, username, password_hash, phone, email, display_name, status, created_at, updated_at
                from member_user
                where username = ? and status = 'ACTIVE'
                limit 1
                """,
                username
        );
    }

    @Override
    public Optional<MemberUser> findActiveByPhone(String phone) {
        return findOne(
                """
                select id, username, password_hash, phone, email, display_name, status, created_at, updated_at
                from member_user
                where phone = ? and status = 'ACTIVE'
                limit 1
                """,
                phone
        );
    }

    @Override
    public Optional<MemberUser> findActiveByEmail(String email) {
        return findOne(
                """
                select id, username, password_hash, phone, email, display_name, status, created_at, updated_at
                from member_user
                where email = ? and status = 'ACTIVE'
                limit 1
                """,
                email
        );
    }

    @Override
    public Optional<MemberUser> findById(Long id) {
        return findOne(
                """
                select id, username, password_hash, phone, email, display_name, status, created_at, updated_at
                from member_user
                where id = ?
                limit 1
                """,
                id
        );
    }

    private Optional<MemberUser> findOne(String sql, Object... args) {
        try {
            MemberUser user = jdbcTemplate.queryForObject(sql, ROW_MAPPER, args);
            return Optional.ofNullable(user);
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    private static java.time.LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
