package com.familyfood.scoring.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * A transcribed points band table — the {@code {"type":"BANDS", ...}} shape of
 * {@code score_component.thresholds} under {@code POINTS_NET_V2} (V16__scoring_v2_engine.sql):
 *
 * <pre>
 * {"type":"BANDS",
 *  "bands":[{"gt":80,"points":1},{"gt":160,"points":2}, …],
 *  "cap":{"baselineLte":20,"within":10,"above":5}}        // MODIFYING rows only
 * </pre>
 *
 * <p>A step function, exactly as the cited standard prints it: the highest band whose
 * {@code gt} the value exceeds sets the points; a value at or below the first {@code gt}
 * scores 0. Nothing is interpolated. Every number is read from the row — this class holds
 * no default band of its own.
 */
public final class BandTable {

    public record Band(BigDecimal gt, int points) {
    }

    /** The standard's capping rule for a modifying factor (evidence/source-002, Table 4). */
    public record Cap(BigDecimal baselineLte, int within, int above) {
        public int limitFor(int baselinePoints) {
            return BigDecimal.valueOf(baselinePoints).compareTo(baselineLte) <= 0 ? within : above;
        }
    }

    public record Placement(int points, BigDecimal threshold) {
    }

    private final List<Band> bands;
    private final Cap cap;
    private final int maxPoints;

    private BandTable(List<Band> bands, Cap cap, int maxPoints) {
        this.bands = bands;
        this.cap = cap;
        this.maxPoints = maxPoints;
    }

    public static boolean isBandShaped(Map<String, Object> thresholds) {
        return thresholds != null && "BANDS".equals(thresholds.get("type"));
    }

    @SuppressWarnings("unchecked")
    public static BandTable from(Map<String, Object> thresholds) {
        if (!isBandShaped(thresholds)) {
            throw new IllegalStateException("score_component.thresholds is not a BANDS table: " + thresholds);
        }
        Object rawBands = thresholds.get("bands");
        if (!(rawBands instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalStateException("BANDS table has no bands: " + thresholds);
        }
        List<Band> parsed = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> band = (Map<String, Object>) item;
            parsed.add(new Band(toBigDecimal(band.get("gt")), toInt(band.get("points"))));
        }
        parsed.sort(Comparator.comparing(Band::gt));

        Cap cap = null;
        if (thresholds.get("cap") instanceof Map<?, ?> rawCap) {
            Map<String, Object> capMap = (Map<String, Object>) rawCap;
            cap = new Cap(toBigDecimal(capMap.get("baselineLte")),
                    toInt(capMap.get("within")), toInt(capMap.get("above")));
        }

        int max = thresholds.get("maxPoints") == null
                ? parsed.stream().mapToInt(Band::points).max().orElse(0)
                : toInt(thresholds.get("maxPoints"));
        return new BandTable(List.copyOf(parsed), cap, max);
    }

    /** The points for a value and the band edge it crossed (null when it sat in band 0). */
    public Placement place(BigDecimal value) {
        Band matched = null;
        for (Band band : bands) {
            if (value.compareTo(band.gt()) > 0) {
                matched = band;
            } else {
                break;
            }
        }
        return matched == null ? new Placement(0, null) : new Placement(matched.points(), matched.gt());
    }

    public Cap cap() {
        return cap;
    }

    public int maxPoints() {
        return maxPoints;
    }

    public List<Band> bands() {
        return bands;
    }

    static BigDecimal toBigDecimal(Object raw) {
        if (raw == null) {
            throw new IllegalStateException("BANDS table value is null");
        }
        if (raw instanceof BigDecimal bd) {
            return bd;
        }
        return new BigDecimal(raw.toString());
    }

    static int toInt(Object raw) {
        return toBigDecimal(raw).intValueExact();
    }
}
