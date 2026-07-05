package com.licenta.coreservice.device;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DeviceRepository {

    private final JdbcTemplate jdbc;

    public DeviceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record DeviceRow(
            UUID uuid,
            String name,
            String description,
            Double latitude,
            Double longitude,
            Long lastSeenAtMs) {}

    private static final RowMapper<DeviceRow> MAPPER = (rs, i) -> {
        Timestamp ls = rs.getTimestamp("last_seen_at");
        return new DeviceRow(
                (UUID) rs.getObject("uuid"),
                rs.getString("name"),
                rs.getString("description"),
                (Double) rs.getObject("latitude"),
                (Double) rs.getObject("longitude"),
                ls != null ? ls.getTime() : null);
    };

    public List<DeviceRow> listByOwner(UUID userId) {
        return jdbc.query(
                "SELECT uuid, name, description, latitude, longitude, last_seen_at " +
                "FROM thing WHERE user_id = ? ORDER BY created_at",
                MAPPER, userId);
    }

    public Optional<DeviceRow> findByIdForOwner(UUID id, UUID userId) {
        List<DeviceRow> rows = jdbc.query(
                "SELECT uuid, name, description, latitude, longitude, last_seen_at " +
                "FROM thing WHERE uuid = ? AND user_id = ?",
                MAPPER, id, userId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public UUID insert(UUID userId, String name, String description, Double latitude, Double longitude) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO thing (uuid, user_id, name, description, latitude, longitude, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'active')",
                id, userId, name, description, latitude, longitude);
        return id;
    }

    public int deleteForOwner(UUID id, UUID userId) {
        return jdbc.update("DELETE FROM thing WHERE uuid = ? AND user_id = ?", id, userId);
    }

    public void insertSeed(UUID id, UUID userId, String name, String description, Double latitude, Double longitude) {
        jdbc.update(
                "INSERT INTO thing (uuid, user_id, name, description, latitude, longitude, status) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'active') " +
                "ON CONFLICT (uuid) DO NOTHING",
                id, userId, name, description, latitude, longitude);
    }
}
