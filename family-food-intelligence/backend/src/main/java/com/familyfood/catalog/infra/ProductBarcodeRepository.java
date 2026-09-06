package com.familyfood.catalog.infra;

import com.familyfood.catalog.domain.ProductBarcode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProductBarcodeRepository extends JpaRepository<ProductBarcode, UUID> {

    /**
     * Every active mapping for a GTIN. Deliberately returns all matches rather than one:
     * the caller must inspect {@code conflictFlag} and the distinct product count before
     * trusting a single result (AC-4.4) — see {@code ProductQueryService.getProductByBarcode}.
     */
    List<ProductBarcode> findByGtinAndActiveTrue(String gtin);

    List<ProductBarcode> findByProductIdAndActiveTrue(UUID productId);
}
