package com.familyfood.insights.api;

import com.familyfood.insights.application.InsightService;
import com.familyfood.insights.domain.Insight;
import com.familyfood.insights.domain.InsightWindow;
import com.familyfood.platform.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Derived statements about recorded data (Phase 2 foundation).
 *
 * <p>Read-only by construction: there is no endpoint to create, edit or dismiss an insight,
 * because insights are computed from observations on every request and never stored
 * (`wellness-platform-architecture.md` §6). A stored insight would outlive the data behind
 * it and keep asserting something a later correction had already disproved.
 */
@RestController
@RequestMapping("/api/v1/insights")
@Tag(name = "Insights")
public class InsightController {

    private final InsightService insights;
    private final CurrentUser currentUser;

    public InsightController(InsightService insights, CurrentUser currentUser) {
        this.insights = insights;
        this.currentUser = currentUser;
    }

    @GetMapping("/members/{memberId}")
    @Operation(summary = "Insights for one member in a window",
               description = "Only insights with enough evidence behind them are returned; "
                           + "where there is too little data the response says so rather than "
                           + "computing a trend over a couple of points. Every number carries "
                           + "the evidence it came from.")
    public List<Insight> forMember(@PathVariable UUID memberId,
                                   @RequestParam(defaultValue = "WEEK") InsightWindow window) {
        return insights.forMember(currentUser.require().accountId(), memberId, window);
    }
}
