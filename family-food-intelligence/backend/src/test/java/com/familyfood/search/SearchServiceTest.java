package com.familyfood.search;

import com.familyfood.catalog.application.ProductQueryService;
import com.familyfood.catalog.application.ProductSuggestionService;
import com.familyfood.platform.error.AppException;
import com.familyfood.search.application.SearchFilters;
import com.familyfood.search.application.SearchService;
import com.familyfood.search.application.SearchSort;
import com.familyfood.search.application.dto.SearchResponseDto;
import com.familyfood.search.infra.SearchQueryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit coverage for the request shaping in front of the SQL: blank-to-null, page/size
 * defaulting and clamping, explicit-over-parsed precedence, validation. The SQL itself is
 * covered end to end by {@code SearchControllerIT} against a real Postgres.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchServiceTest {

    @Mock private SearchQueryBuilder queries;
    @Mock private ProductQueryService productQueryService;
    @Mock private ProductSuggestionService suggestions;

    private SearchService service;

    @BeforeEach
    void setUp() {
        service = new SearchService(queries, productQueryService, suggestions);
        when(productQueryService.categoryAliases()).thenReturn(Map.of("biscuits", "BISCUITS"));
        when(queries.search(any(), any(), any(), anyInt(), anyLong()))
                .thenReturn(new SearchQueryBuilder.SearchPage(List.of(), 0, 0));
        when(productQueryService.getSummaries(any())).thenReturn(List.of());
        when(queries.factsFor(any())).thenReturn(Map.of());
        when(suggestions.didYouMean(any())).thenReturn(Optional.empty());
    }

    @Test
    void blankQueryAndCategoryAreNormalisedToNull() {
        service.search("   ", "", 0, 20);

        ArgumentCaptor<SearchFilters> filters = ArgumentCaptor.forClass(SearchFilters.class);
        verify(queries).search(isNull(), filters.capture(), eq(SearchSort.RELEVANCE), eq(20), eq(0L));
        assertThat(filters.getValue().hasAny()).isFalse();
    }

    @Test
    void nullPageAndSizeDefaultToZeroAndTwenty() {
        SearchResponseDto response = service.search(null, null, null, null);

        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
        verify(queries).search(isNull(), any(), any(), eq(20), eq(0L));
    }

    @Test
    void oversizedPageSizeIsClampedToTheMaximum() {
        SearchResponseDto response = service.search("chips", "CHIPS", 2, 5000);

        assertThat(response.size()).isEqualTo(100);
        verify(queries).search(eq("chips"), any(), any(), eq(100), eq(200L));
    }

    @Test
    void negativePageIsTreatedAsZero() {
        SearchResponseDto response = service.search(null, null, -5, 10);

        assertThat(response.page()).isZero();
        verify(queries).search(isNull(), any(), any(), eq(10), eq(0L));
    }

    @Test
    void resultOrderFromTheQueryIsPreserved() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(queries.search(any(), any(), any(), anyInt(), anyLong()))
                .thenReturn(new SearchQueryBuilder.SearchPage(List.of(first, second), 2, 0));

        service.search("q", null, 0, 20);

        verify(productQueryService).getSummaries(List.of(first, second));
    }

    @Test
    void explicitFiltersAndSortWinOverTheWords() {
        SearchFilters explicit = new SearchFilters(null, null, new BigDecimal("4"), null, null, null, null, null,
                List.of("PEANUT"), null, null);

        SearchResponseDto response = service.search("biscuits under 10g sugar no milk", explicit, SearchSort.SODIUM_ASC, 0, 20);

        ArgumentCaptor<SearchFilters> filters = ArgumentCaptor.forClass(SearchFilters.class);
        verify(queries).search(isNull(), filters.capture(), eq(SearchSort.SODIUM_ASC), anyInt(), anyLong());
        assertThat(filters.getValue().maxTotalSugar()).isEqualByComparingTo("4");        // explicit 4 beats "under 10g"
        assertThat(filters.getValue().category()).isEqualTo("BISCUITS");                 // read from the words
        assertThat(filters.getValue().excludeAllergens()).containsExactlyInAnyOrder("MILK", "PEANUT"); // unioned
        assertThat(response.sort()).isEqualTo("SODIUM_ASC");
        assertThat(response.interpreted()).isNotEmpty();
    }

    @Test
    void invalidFiltersAreRejected() {
        SearchFilters negative = new SearchFilters(null, null, new BigDecimal("-1"), null, null, null, null, null,
                List.of(), null, null);
        SearchFilters badNova = new SearchFilters(null, null, null, null, null, null, null, null, List.of(), null, 7);
        SearchFilters badAllergen = new SearchFilters(null, null, null, null, null, null, null, null, List.of("CHEESE"), null, null);

        assertThatThrownBy(() -> service.search(null, negative, null, 0, 20)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> service.search(null, badNova, null, 0, 20)).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> service.search(null, badAllergen, null, 0, 20)).isInstanceOf(AppException.class);
    }

    @Test
    void didYouMeanOnlyWhenNothingMatched() {
        when(suggestions.didYouMean("britania")).thenReturn(Optional.of("Britannia"));

        assertThat(service.search("britania", null, 0, 20).didYouMean()).isEqualTo("Britannia");
    }
}
