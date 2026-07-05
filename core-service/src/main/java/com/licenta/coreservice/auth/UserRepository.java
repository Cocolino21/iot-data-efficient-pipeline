package com.licenta.coreservice.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {

    private final JdbcTemplate jdbc;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record UserRow(UUID uuid, String name, String email) {}

    public Optional<UserRow> findByEmail(String email) {
        return jdbc.query(
                "SELECT uuid, name, email FROM \"user\" WHERE email = ?",
                rs -> rs.next()
                        ? Optional.of(new UserRow(
                            (UUID) rs.getObject("uuid"),
                            rs.getString("name"),
                            rs.getString("email")))
                        : Optional.empty(),
                email);
    }

    public UUID insert(String name, String email, String provider, String providerId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO \"user\" (uuid, name, email, oauth_provider, oauth_provider_id) VALUES (?, ?, ?, ?, ?)",
                id, name, email, provider, providerId);
        return id;
    }

    public void updateProviderId(UUID userId, String provider, String providerId) {
        jdbc.update(
                "UPDATE \"user\" SET oauth_provider = ?, oauth_provider_id = ? WHERE uuid = ?",
                provider, providerId, userId);
    }

    public void upsertSeed(UUID uuid, String name, String email, String provider) {
        jdbc.update(
                "INSERT INTO \"user\" (uuid, name, email, oauth_provider) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT (email) DO NOTHING",
                uuid, name, email, provider);
    }
}
