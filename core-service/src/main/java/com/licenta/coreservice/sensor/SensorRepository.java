package com.licenta.coreservice.sensor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.licenta.coreservice.sensor.dto.SensorDto;
import org.postgresql.util.PGobject;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class SensorRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();

    public SensorRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record SensorRowWithDevice(UUID deviceId, SensorDto sensor) {}

    public record DatastreamRefs(UUID datastreamId, UUID thingId, UUID sensorId, UUID observedPropertyId, String externalId) {}

    private static final RowMapper<SensorRowWithDevice> WITH_DEVICE = (rs, i) -> new SensorRowWithDevice(
            (UUID) rs.getObject("thing_id"),
            new SensorDto(
                    (UUID) rs.getObject("ds_uuid"),
                    rs.getString("ds_name"),
                    rs.getString("ds_type"),
                    rs.getString("observed"),
                    rs.getString("unit"),
                    rs.getString("symbol"),
                    rs.getBoolean("ds_is_active"),
                    rs.getString("external_id")));

    public List<SensorRowWithDevice> findForDevices(Collection<UUID> deviceIds) {
        if (deviceIds.isEmpty()) return Collections.emptyList();
        UUID[] ids = deviceIds.toArray(new UUID[0]);
        return jdbc.query(
                "SELECT d.uuid AS ds_uuid, d.thing_id, d.name AS ds_name, d.observation_type AS ds_type, " +
                "       d.is_active AS ds_is_active, s.metadata->>'external_id' AS external_id, " +
                "       op.name AS observed, op.unit_of_measurement AS unit, op.symbol AS symbol " +
                "FROM datastream d " +
                "JOIN sensor s ON s.uuid = d.sensor_id " +
                "JOIN observed_property op ON op.uuid = d.observed_property_id " +
                "WHERE d.thing_id = ANY (?) " +
                "ORDER BY d.created_at",
                ps -> ps.setArray(1, ps.getConnection().createArrayOf("uuid", ids)),
                WITH_DEVICE);
    }

    public UUID insertSensor(String name, String description, Map<String, Object> metadata) {
        UUID id = UUID.randomUUID();
        PGobject meta = jsonb(metadata);
        jdbc.update(
                "INSERT INTO sensor (uuid, name, description, metadata) VALUES (?, ?, ?, ?)",
                id, name, description, meta);
        return id;
    }

    public UUID insertObservedProperty(String name, String definition, String unit, String symbol) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO observed_property (uuid, name, definition, unit_of_measurement, symbol) " +
                "VALUES (?, ?, ?, ?, ?)",
                id, name, definition, unit, symbol);
        return id;
    }

    public UUID insertDatastream(UUID thingId, UUID sensorId, UUID observedPropertyId,
                                 String name, String observationType, boolean isActive, String externalId) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO datastream (uuid, thing_id, sensor_id, observed_property_id, name, observation_type, is_active, datastream_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                id, thingId, sensorId, observedPropertyId, name, observationType, isActive, externalId);
        return id;
    }

    public int updateDatastreamActive(UUID datastreamId, boolean isActive) {
        return jdbc.update("UPDATE datastream SET is_active = ? WHERE uuid = ?", isActive, datastreamId);
    }

    public Optional<UUID> findOwnerOfDevice(UUID thingId) {
        List<UUID> rows = jdbc.query(
                "SELECT user_id FROM thing WHERE uuid = ?",
                (rs, i) -> (UUID) rs.getObject(1),
                thingId);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    }

    public Optional<UUID> findOwnerOfDatastream(UUID datastreamId) {
        List<UUID> rows = jdbc.query(
                "SELECT t.user_id FROM datastream d " +
                "JOIN thing t ON t.uuid = d.thing_id " +
                "WHERE d.uuid = ?",
                (rs, i) -> (UUID) rs.getObject(1),
                datastreamId);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    }

    public Optional<DatastreamRefs> findRefsByDatastreamId(UUID datastreamId) {
        List<DatastreamRefs> rows = jdbc.query(
                "SELECT d.uuid AS ds_uuid, d.thing_id, d.sensor_id, d.observed_property_id, " +
                "       s.metadata->>'external_id' AS external_id " +
                "FROM datastream d JOIN sensor s ON s.uuid = d.sensor_id " +
                "WHERE d.uuid = ?",
                (rs, i) -> new DatastreamRefs(
                        (UUID) rs.getObject("ds_uuid"),
                        (UUID) rs.getObject("thing_id"),
                        (UUID) rs.getObject("sensor_id"),
                        (UUID) rs.getObject("observed_property_id"),
                        rs.getString("external_id")),
                datastreamId);
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
    }

    public void deleteDatastream(UUID datastreamId) {
        jdbc.update("DELETE FROM datastream WHERE uuid = ?", datastreamId);
    }

    public void deleteSensor(UUID sensorId) {
        jdbc.update("DELETE FROM sensor WHERE uuid = ?", sensorId);
    }

    public void deleteObservedProperty(UUID opId) {
        jdbc.update("DELETE FROM observed_property WHERE uuid = ?", opId);
    }

    public void deleteDatastreamsForThing(UUID thingId) {
        // returns (sensor_id, observed_property_id) of deleted rows so service can clean orphans
        List<UUID[]> deleted = jdbc.query(
                "DELETE FROM datastream WHERE thing_id = ? RETURNING sensor_id, observed_property_id",
                (rs, i) -> new UUID[] { (UUID) rs.getObject(1), (UUID) rs.getObject(2) },
                thingId);
        for (UUID[] pair : deleted) {
            deleteSensor(pair[0]);
            deleteObservedProperty(pair[1]);
        }
    }

    private PGobject jsonb(Map<String, Object> value) {
        try {
            PGobject obj = new PGobject();
            obj.setType("jsonb");
            obj.setValue(json.writeValueAsString(value));
            return obj;
        } catch (JsonProcessingException | SQLException e) {
            throw new DataAccessException("failed to encode jsonb", e) {};
        }
    }
}
