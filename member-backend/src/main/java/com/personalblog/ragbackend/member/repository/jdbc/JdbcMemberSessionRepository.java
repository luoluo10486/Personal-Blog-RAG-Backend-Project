package com.personalblog.ragbackend.member.repository.jdbc;

import com.personalblog.ragbackend.member.model.MemberSession;
import com.personalblog.ragbackend.member.repository.MemberSessionRepository;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class JdbcMemberSessionRepository implements MemberSessionRepository {
    private final JdbcTemplate jdbcTemplate;

    private static final RowMapper<MemberSession> ROW_MAPPER = (rs, rowNum) -> new MemberSession(
            rs.getLong("id"),
            rs.getLong("user_id"),
            rs.getString("token"),
            rs.getString("grant_type"),
            toLocalDateTime(rs.getTimestamp("expires_at")),
            rs.getBoolean("revoked"),
            toLocalDateTime(rs.getTimestamp("created_at"))
    );

    public JdbcMemberSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(MemberSession session) {
        jdbcTemplate.update(
                """
                insert into member_login_session
                (user_id, token, grant_type, expires_at, revoked, created_at)
                values (?, ?, ?, ?, ?, ?)
                """,
                session.userId(),
                session.token(),
                session.grantType(),
                Timestamp.valueOf(session.expiresAt()),
                session.revoked() ? 1 : 0,
                Timestamp.valueOf(session.createdAt())
        );
    }

    @Override
    public Optional<MemberSession> findValidByToken(String token) {
        try {
            MemberSession session = jdbcTemplate.queryForObject(
                    """
                    select id, user_id, token, grant_type, expires_at, revoked, created_at
                    from member_login_session
                    where token = ?
                      and revoked = 0
                      and expires_at > current_timestamp
                    order by id desc
                    limit 1
                    """,
                    ROW_MAPPER,
                    token
            );
            return Optional.ofNullable(session);
        } catch (EmptyResultDataAccessException ignored) {
            return Optional.empty();
        }
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
