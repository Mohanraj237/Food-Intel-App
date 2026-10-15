package com.familyfood.scoring.application;

import com.familyfood.scoring.domain.FoodType;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The net-points → star mapping from {@code rule_set.algorithm_config} (V16):
 *
 * <pre>
 * {"starBandsSolid":  [{"lte":-11,"stars":5}, {"gte":-10,"lte":-7,"stars":4.5}, …, {"gte":25,"stars":0.5}],
 *  "starBandsLiquid": [ … ]}
 * </pre>
 *
 * <p>Bands are matched in list order and the first match wins — which is how the one
 * printed overlap in the cited liquid table (18 points listed under both 1.5★ and 1★,
 * evidence/source-002 Table 6 [sic]) resolves to the earlier row unless the reviewer
 * orders the migration differently. Nothing here holds a default band.
 *
 * <p>{@link #scoreFor} is the 0–100 <em>display convention</em> (scoring-methodology.md
 * §2.6), not part of the cited algorithm: a linear rescale between the table's own two
 * anchor points — the net-points edge of the top star band (→ 100) and of the bottom star
 * band (→ 0) — clamped to {@code [0,100]}. It never changes which star band a product is in.
 */
public final class StarBands {

    public record Band(BigDecimal gte, BigDecimal lte, BigDecimal stars) {
        boolean matches(int net) {
            BigDecimal n = BigDecimal.valueOf(net);
            return (gte == null || n.compareTo(gte) >= 0) && (lte == null || n.compareTo(lte) <= 0);
        }
    }

    private final List<Band> bands;
    private final BigDecimal topEdge;     // net points at (or below) which the top band starts
    private final BigDecimal bottomEdge;  // net points at (or above) which the bottom band starts

    private StarBands(List<Band> bands) {
        this.bands = bands;
        Band top = bands.stream().max((a, b) -> a.stars().compareTo(b.stars())).orElseThrow();
        Band bottom = bands.stream().min((a, b) -> a.stars().compareTo(b.stars())).orElseThrow();
        if (top.lte() == null || bottom.gte() == null) {
            throw new IllegalStateException("star bands need an 'lte' on the top band and a 'gte' on the bottom band");
        }
        this.topEdge = top.lte();
        this.bottomEdge = bottom.gte();
    }

    @SuppressWarnings("unchecked")
    public static StarBands from(Map<String, Object> algorithmConfig, FoodType foodType) {
        String key = switch (foodType) {
            case SOLID -> "starBandsSolid";
            case LIQUID -> "starBandsLiquid";
            case FAT_OIL -> throw new IllegalStateException("no star bands exist for FAT_OIL (Schedule-IV exempt)");
        };
        Object raw = algorithmConfig == null ? null : algorithmConfig.get(key);
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalStateException("rule_set.algorithm_config lacks " + key);
        }
        List<Band> parsed = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> band = (Map<String, Object>) item;
            parsed.add(new Band(optional(band.get("gte")), optional(band.get("lte")),
                    BandTable.toBigDecimal(band.get("stars"))));
        }
        return new StarBands(List.copyOf(parsed));
    }

    public BigDecimal starsFor(int netPoints) {
        return bands.stream()
                .filter(b -> b.matches(netPoints))
                .findFirst()
                .map(Band::stars)
                .orElseThrow(() -> new IllegalStateException("no star band covers net points " + netPoints));
    }

    /** Display convention: 100 at the top band's edge, 0 at the bottom band's edge, linear between. */
    public int scoreFor(int netPoints) {
        BigDecimal range = bottomEdge.subtract(topEdge);
        if (range.signum() <= 0) {
            throw new IllegalStateException("star band anchors are not ordered: top " + topEdge + ", bottom " + bottomEdge);
        }
        BigDecimal fraction = bottomEdge.subtract(BigDecimal.valueOf(netPoints)).divide(range, MathContext.DECIMAL64);
        int score = fraction.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
        return Math.max(0, Math.min(100, score));
    }

    private static BigDecimal optional(Object raw) {
        return raw == null ? null : BandTable.toBigDecimal(raw);
    }
}
