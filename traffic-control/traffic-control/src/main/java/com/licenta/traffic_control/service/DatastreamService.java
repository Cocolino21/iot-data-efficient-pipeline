package com.licenta.traffic_control.service;

import com.licenta.traffic_control.config.ReconstructionSettings;
import com.licenta.traffic_control.dto.Observation;
import com.licenta.traffic_control.repository.DatastreamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DatastreamService {

    private final DatastreamRepository repo;
    private final ReconstructionService reconstructionService;
    private final ReconstructionSettings reconstructionSettings;

    private static final Map<String, String> TIER_VIEWS = Map.of(
            "hourly", "energy_hourly",
            "daily", "energy_daily",
            "weekly", "energy_weekly",
            "monthly", "energy_monthly");

    private static final long DEFAULT_RAW_WINDOW_MS = 15 * 60_000L;
    private static final long DEFAULT_AGG_WINDOW_MS = 7L * 24 * 60 * 60 * 1000;

    public Map<String, Object> list(int page, int size, String q) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        int safePage = Math.max(page, 0);
        String like = "%" + q.trim() + "%";
        List<Map<String, Object>> items = repo.listStreams(like, safeSize, safePage * safeSize);
        long total = repo.countStreams(like);
        return Map.of("items", items, "total", total, "page", safePage, "size", safeSize);
    }

    public List<Map<String, Object>> baseline(String id) {
        return repo.findBaseline(id);
    }

    public List<Observation> raw(String id, Long from, Long to) {
        long toMs = to != null ? to : System.currentTimeMillis();
        long fromMs = from != null ? from : toMs - DEFAULT_RAW_WINDOW_MS;
        return repo.findObservations(id, fromMs, toMs);
    }

    public Map<String, Object> reconstructed(String id, Long from, Long to) {
        long toMs = to != null ? to : System.currentTimeMillis();
        long fromMs = from != null ? from : toMs - DEFAULT_RAW_WINDOW_MS;
        return reconstructionService.reconstruct(id, fromMs, toMs);
    }

    public List<Map<String, Object>> aggregates(String id, String tier, Long from, Long to) {
        String view = TIER_VIEWS.get(tier);
        if (view == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "tier must be one of " + TIER_VIEWS.keySet());
        }
        long toMs = to != null ? to : System.currentTimeMillis();
        long fromMs = from != null ? from : toMs - DEFAULT_AGG_WINDOW_MS;

        if ("hourly".equals(tier)) {
            return repo.findHourlyAdjusted(id, fromMs, toMs, reconstructionSettings.getSamplesPerHour());
        }
        return repo.findAggregates(view, id, fromMs, toMs);
    }
}
