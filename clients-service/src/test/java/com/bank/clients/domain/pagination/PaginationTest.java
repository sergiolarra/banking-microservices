package com.bank.clients.domain.pagination;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaginationTest {

    @Test
    void pageQueryUsesTheDefaultsWhenValuesAreMissing() {
        assertThat(PageQuery.of(null, null)).isEqualTo(new PageQuery(0, PageQuery.DEFAULT_SIZE));
        assertThat(PageQuery.of(3, 50)).isEqualTo(new PageQuery(3, 50));
    }

    @ParameterizedTest
    @CsvSource({"-1, 20", "0, 0", "0, 101"})
    void pageQueryRejectsValuesOutsideTheLimits(int page, int size) {
        assertThatThrownBy(() -> new PageQuery(page, size)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pageQueryAcceptsTheMaximumSize() {
        assertThat(new PageQuery(0, PageQuery.MAX_SIZE).size()).isEqualTo(100);
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "1, 1", "20, 1", "21, 2", "45, 3"})
    void pagedResultComputesTheTotalPages(long totalElements, int expectedPages) {
        assertThat(new PagedResult<>(List.of(), 0, 20, totalElements).totalPages()).isEqualTo(expectedPages);
    }

    @Test
    void pagedResultIsImmutableAndMapsItsItems() {
        List<Integer> source = new ArrayList<>(List.of(1, 2));
        PagedResult<Integer> result = new PagedResult<>(source, 1, 2, 5);
        source.add(3);

        PagedResult<String> mapped = result.map(String::valueOf);

        assertThat(result.items()).containsExactly(1, 2);
        assertThat(mapped).isEqualTo(new PagedResult<>(List.of("1", "2"), 1, 2, 5));
    }
}
