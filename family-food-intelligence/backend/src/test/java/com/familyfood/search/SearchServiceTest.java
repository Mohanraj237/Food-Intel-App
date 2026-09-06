package com.familyfood.search;

import com.familyfood.catalog.application.ProductQueryService;
import com.familyfood.search.application.SearchService;
import com.familyfood.search.application.dto.SearchResponseDto;
import com.familyfood.search.infra.ProductSearchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit coverage for the request-shaping logic that sits in front of the native
 * query: blank-to-null normalisation and page/size defaulting/clamping. The query
 * itself is covered end-to-end by {@code SearchControllerIT} against a real Postgres.
 */
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    private ProductSearchRepository searchRepository;

    @Mock
    private ProductQueryService productQueryService;

    @Test
    void blankQueryAndCategoryAreNormalisedToNull() {
        SearchService service = new SearchService(searchRepository, productQueryService);
        when(searchRepository.countSearch(isNull(), isNull())).thenReturn(0L);
        when(searchRepository.searchIds(isNull(), isNull(), anyInt(), anyLong())).thenReturn(List.of());
        when(productQueryService.getSummaries(any())).thenReturn(List.of());

        service.search("   ", "", 0, 20);

        verify(searchRepository).countSearch(isNull(), isNull());
        verify(searchRepository).searchIds(isNull(), isNull(), eq(20), eq(0L));
    }

    @Test
    void nullPageAndSizeDefaultToZeroAndTwenty() {
        SearchService service = new SearchService(searchRepository, productQueryService);
        when(searchRepository.countSearch(any(), any())).thenReturn(0L);
        when(searchRepository.searchIds(any(), any(), anyInt(), anyLong())).thenReturn(List.of());
        when(productQueryService.getSummaries(any())).thenReturn(List.of());

        SearchResponseDto response = service.search(null, null, null, null);

        assertThat(response.page()).isEqualTo(0);
        assertThat(response.size()).isEqualTo(20);
        verify(searchRepository).searchIds(isNull(), isNull(), eq(20), eq(0L));
    }

    @Test
    void oversizedPageSizeIsClampedToTheMaximum() {
        SearchService service = new SearchService(searchRepository, productQueryService);
        when(searchRepository.countSearch(any(), any())).thenReturn(0L);
        when(searchRepository.searchIds(any(), any(), anyInt(), anyLong())).thenReturn(List.of());
        when(productQueryService.getSummaries(any())).thenReturn(List.of());

        SearchResponseDto response = service.search("chips", "CHIPS", 2, 5000);

        assertThat(response.size()).isEqualTo(100);
        verify(searchRepository).searchIds(eq("chips"), eq("CHIPS"), eq(100), eq(200L));
    }

    @Test
    void negativePageIsTreatedAsZero() {
        SearchService service = new SearchService(searchRepository, productQueryService);
        when(searchRepository.countSearch(any(), any())).thenReturn(0L);
        when(searchRepository.searchIds(any(), any(), anyInt(), anyLong())).thenReturn(List.of());
        when(productQueryService.getSummaries(any())).thenReturn(List.of());

        SearchResponseDto response = service.search(null, null, -5, 10);

        assertThat(response.page()).isEqualTo(0);
        verify(searchRepository).searchIds(isNull(), isNull(), eq(10), eq(0L));
    }

    @Test
    void resultOrderFromTheSearchRepositoryIsPreserved() {
        SearchService service = new SearchService(searchRepository, productQueryService);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        when(searchRepository.countSearch(any(), any())).thenReturn(2L);
        when(searchRepository.searchIds(any(), any(), anyInt(), anyLong()))
                .thenReturn(List.of(first, second));

        ArgumentCaptor<List<UUID>> idsCaptor = ArgumentCaptor.forClass(List.class);
        when(productQueryService.getSummaries(idsCaptor.capture())).thenReturn(List.of());

        service.search("q", null, 0, 20);

        assertThat(idsCaptor.getValue()).containsExactly(first, second);
    }
}
