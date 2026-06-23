package com.ahealth.backend.auth;

import com.ahealth.backend.security.AuthenticatedUser;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AuthRepository {
  private final JdbcTemplate jdbcTemplate;

  private final RowMapper<UserRow> userRowMapper = (rs, rowNum) -> mapUserRow(rs);

  public AuthRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<UserRow> findUserByEmail(String email) {
    try {
      return Optional.ofNullable(jdbcTemplate.queryForObject(
          """
          SELECT id, name, email, password_hash, created_at
          FROM auth_users
          WHERE email = ?
          LIMIT 1
          """,
          userRowMapper,
          email
      ));
    } catch (EmptyResultDataAccessException exception) {
      return Optional.empty();
    }
  }

  public Optional<AuthDtos.AuthUserView> findAuthUserView(long userId) {
    try {
      return Optional.ofNullable(jdbcTemplate.queryForObject(
          """
          SELECT up.id, up.name, up.email, up.avatar_url
          FROM user_profiles up
          WHERE up.id = ?
          LIMIT 1
          """,
          (rs, rowNum) -> new AuthDtos.AuthUserView(
              String.valueOf(rs.getLong("id")),
              rs.getString("name"),
              rs.getString("email"),
              rs.getString("avatar_url")
          ),
          userId
      ));
    } catch (EmptyResultDataAccessException exception) {
      return Optional.empty();
    }
  }

  public long createUser(String name, String email, String passwordHash) {
    jdbcTemplate.update(
        """
        INSERT INTO auth_users (name, email, password_hash, created_at)
        VALUES (?, ?, ?, NOW())
        """,
        name,
        email,
        passwordHash
    );
    Long id = jdbcTemplate.queryForObject("SELECT id FROM auth_users WHERE email = ?", Long.class, email);
    return id == null ? 0L : id;
  }

  public void ensureProfile(long userId, String name, String email) {
    jdbcTemplate.update(
        """
        INSERT INTO user_profiles (id, name, email, avatar_url, risk_score, risk_level)
        VALUES (?, ?, ?, NULL, 18, '低风险')
        ON DUPLICATE KEY UPDATE
          name = VALUES(name),
          email = VALUES(email)
        """,
        userId,
        name,
        email
    );

    jdbcTemplate.update(
        """
        INSERT INTO user_settings (user_id, age, gender, height, weight, focus, goals_json, daily_summary, risk_alert, rehab_reminder)
        VALUES (?, 28, 'other', 170, 60, '改善日常健康节律', '["恢复放松"]', 1, 1, 1)
        ON DUPLICATE KEY UPDATE user_id = user_id
        """,
        userId
    );
  }

  public void createSession(String token, long userId) {
    jdbcTemplate.update(
        """
        INSERT INTO auth_sessions (token, user_id, created_at, last_active)
        VALUES (?, ?, NOW(), NOW())
        """,
        token,
        userId
    );
  }

  public void deleteSession(String token) {
    jdbcTemplate.update("DELETE FROM auth_sessions WHERE token = ?", token);
  }

  public Optional<AuthenticatedUser> findSessionUser(String token) {
    try {
      return Optional.ofNullable(jdbcTemplate.queryForObject(
          """
          SELECT s.token, s.user_id, p.name, p.email
          FROM auth_sessions s
          JOIN user_profiles p ON p.id = s.user_id
          WHERE s.token = ?
          LIMIT 1
          """,
          (rs, rowNum) -> new AuthenticatedUser(
              rs.getLong("user_id"),
              rs.getString("token"),
              rs.getString("name"),
              rs.getString("email")
          ),
          token
      ));
    } catch (EmptyResultDataAccessException exception) {
      return Optional.empty();
    }
  }

  public void touchSession(String token) {
    jdbcTemplate.update(
        "UPDATE auth_sessions SET last_active = NOW() WHERE token = ?",
        token
    );
  }

  private UserRow mapUserRow(ResultSet rs) throws SQLException {
    return new UserRow(
        rs.getLong("id"),
        rs.getString("name"),
        rs.getString("email"),
        rs.getString("password_hash"),
        rs.getObject("created_at", LocalDateTime.class)
    );
  }

  public record UserRow(
      long id,
      String name,
      String email,
      String passwordHash,
      LocalDateTime createdAt
  ) {}
}
