package com.licenta.coreservice.auth;

import com.licenta.coreservice.device.DeviceRepository;
import com.licenta.coreservice.sensor.SensorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(value = "app.demo.seed", havingValue = "true", matchIfMissing = false)
public class DemoSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);

    private static final UUID USER_1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_2 = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private final JdbcTemplate jdbc;
    private final UserRepository userRepo;
    private final DeviceRepository deviceRepo;
    private final SensorRepository sensorRepo;

    public DemoSeeder(JdbcTemplate jdbc,
                      UserRepository userRepo,
                      DeviceRepository deviceRepo,
                      SensorRepository sensorRepo) {
        this.jdbc = jdbc;
        this.userRepo = userRepo;
        this.deviceRepo = deviceRepo;
        this.sensorRepo = sensorRepo;
    }

    @Override
    @Transactional
    public void run(String... args) {
        userRepo.upsertSeed(USER_1, "Mihnea Bostina", "mihneabostina5@gmail.com", "google");
        userRepo.upsertSeed(USER_2, "Mihnea Bostina 2", "mihneabostina8@gmail.com", "google");

        if (deviceCount(USER_1) == 0) {
            seedLivingRoom(USER_1);
            seedBedroom(USER_1);
            log.info("Demo devices seeded for {}", USER_1);
        } else {
            log.info("Demo user {} already has devices; skipping device seed", USER_1);
        }
    }

    private int deviceCount(UUID userId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM thing WHERE user_id = ?", Integer.class, userId);
        return n == null ? 0 : n;
    }

    private void seedLivingRoom(UUID userId) {
        UUID deviceId = deviceRepo.insert(userId, "Living Room Hub",
                "Main hub in the living room, central monitoring point", 44.4268, 26.1025);
        sensorTriple(deviceId, "Temperature Sensor", "temperature", "Temperature",        "C",   "C",   "temperature-1");
        sensorTriple(deviceId, "Humidity Sensor",    "humidity",    "Relative Humidity",  "%RH", "%RH", "humidity-1");
        sensorTriple(deviceId, "Light Sensor",       "light",       "Luminous Intensity", "lux", "lux", "light-1");
        sensorTriple(deviceId, "Power Meter",        "energy",      "Power Consumption",  "kWh", "kWh", "power-1");
    }

    private void seedBedroom(UUID userId) {
        UUID deviceId = deviceRepo.insert(userId, "Bedroom Station",
                "Secondary station in the master bedroom", 44.4268, 26.1025);
        sensorTriple(deviceId, "Temperature Sensor", "temperature", "Temperature",        "C",   "C",   "temperature-2");
        sensorTriple(deviceId, "Humidity Sensor",    "humidity",    "Relative Humidity",  "%RH", "%RH", "humidity-2");
    }

    private void sensorTriple(UUID deviceId, String name, String type, String observed,
                              String unit, String symbol, String externalId) {
        UUID sensorId = sensorRepo.insertSensor(name, null, Map.of("type", type, "external_id", externalId));
        UUID opId    = sensorRepo.insertObservedProperty(observed, null, unit, symbol);
        sensorRepo.insertDatastream(deviceId, sensorId, opId, name, type, true, externalId);
    }
}
