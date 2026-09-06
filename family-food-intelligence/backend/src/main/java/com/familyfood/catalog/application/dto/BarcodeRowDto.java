package com.familyfood.catalog.application.dto;

/** Mirrors the inline barcode row shape on {@code ProductDetail} in frontend/src/lib/catalog.ts. */
public record BarcodeRowDto(String gtin, String gtinType, boolean isPrimary) {
}
