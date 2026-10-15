package com.familyfood.catalog.application;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * Remembers barcodes that were scanned but matched no product (V15 {@code unknown_barcode}),
 * so the catalogue can be filled where people are actually looking. Stores the GTIN, when it
 * was first and last seen, and how often — nothing about who scanned it. Only well-formed
 * GTINs (8–14 digits) are recorded; anything else is ignored rather than stored.
 */
@Service
public class UnknownBarcodeService {

    private static final Pattern GTIN = Pattern.compile("\\d{8,14}");

    private final JdbcTemplate jdbc;

    public UnknownBarcodeService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Idempotent upsert; its own transaction so a read-only lookup can record a miss. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordMiss(String gtin) {
        if (gtin == null || !GTIN.matcher(gtin).matches()) {
            return;
        }
        jdbc.update("""
                INSERT INTO unknown_barcode (gtin) VALUES (?)
                ON CONFLICT (gtin) DO UPDATE
                   SET hit_count = unknown_barcode.hit_count + 1, last_seen = now()
                """, gtin);
    }
}
