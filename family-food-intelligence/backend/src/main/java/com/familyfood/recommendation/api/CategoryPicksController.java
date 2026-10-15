package com.familyfood.recommendation.api;

import com.familyfood.catalog.application.dto.ProductSummaryDto;
import com.familyfood.platform.security.CurrentUser;
import com.familyfood.recommendation.application.CategoryPicksService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Household picks in a category (D-060). Signed in; caller-scoped; thin. */
@RestController
@Tag(name = "Recommendation")
public class CategoryPicksController {

    private final CategoryPicksService picksService;
    private final CurrentUser currentUser;

    public CategoryPicksController(CategoryPicksService picksService, CurrentUser currentUser) {
        this.picksService = picksService;
        this.currentUser = currentUser;
    }

    public record PickDto(ProductSummaryDto product, BigDecimal stars, List<CompareController.MemberFit> members) {
    }

    @GetMapping("/api/v1/households/{householdId}/categories/{code}/picks")
    @Operation(summary = "Best-rated products in a category that suit the caller's household",
               description = "Allergen conflicts for anyone the caller may see are removed; ordered by the "
                           + "least severe verdict among those members, then rating. Empty until products are rated.")
    public List<PickDto> picks(@PathVariable UUID householdId, @PathVariable String code) {
        UUID caller = currentUser.require().accountId();
        return picksService.picks(caller, householdId, code).stream()
                .map(p -> new PickDto(p.product(), p.stars(),
                        p.members().stream().map(CompareController.MemberFit::from).toList()))
                .toList();
    }
}
