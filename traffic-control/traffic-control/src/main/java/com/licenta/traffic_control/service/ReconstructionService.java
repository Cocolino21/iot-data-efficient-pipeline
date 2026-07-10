package com.licenta.traffic_control.service;

import com.licenta.traffic_control.config.ReconstructionSettings;
import com.licenta.traffic_control.dto.Point;
import com.licenta.traffic_control.repository.DatastreamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReconstructionService {

    private final DatastreamRepository datastreamRepository;
    private final ReconstructionSettings settings;

    public Map<String, Object> reconstruct(String datastreamId, long fromMs, long toMs) {
        List<Point> measured = datastreamRepository.findObservations(datastreamId, fromMs, toMs).stream()
                .map(o -> new Point(o.timestamp(), o.value(), false))
                .toList();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("points", fillGaps(measured));
        out.put("method", "interpolation");
        return out;
    }

    private List<Point> fillGaps(List<Point> measured) {
        long gapMs = settings.getGapSeconds() * 1000L;
        List<Point> out = new ArrayList<>(measured.size());
        for (int i = 0; i < measured.size(); i++) {
            Point p = measured.get(i);
            out.add(p);
            if (i + 1 >= measured.size()) break;
            Point next = measured.get(i + 1);
            long gap = next.timestamp() - p.timestamp();
            if (gap <= gapMs) continue;

            long step = Math.max(1000, gap / settings.getMaxPointsPerGap());
            for (long t = p.timestamp() + step; t < next.timestamp(); t += step) {
                double alpha = (double) (t - p.timestamp()) / gap;
                out.add(new Point(t, p.value() + alpha * (next.value() - p.value()), true));
            }
        }
        return out;
    }
}
