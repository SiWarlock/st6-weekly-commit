package com.st6.wc.manager.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * {@link PageEnvelope} unit proof (task 6.5a) — the B.20 shape over a Spring {@code Page<T>}:
 * {@code content} + {@code page{number,size,totalElements,totalPages}} + {@code
 * sort[{property,direction}]} (the {@code sort} array stock {@code PagedModel} omits).
 */
class PageEnvelopeTest {

  @Test
  void of_buildsB20Shape_fromPage() {
    Sort sort = Sort.by(Sort.Order.desc("weekStartDate"), Sort.Order.asc("employeeDisplayName"));
    // size 2 (page 0 of 3) so PageImpl keeps total=6 (it self-corrects total when size>total).
    PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(0, 2, sort), 6);

    PageEnvelope<String> env = PageEnvelope.of(page);

    assertThat(env.content()).containsExactly("a", "b");
    assertThat(env.page().number()).isZero();
    assertThat(env.page().size()).isEqualTo(2);
    assertThat(env.page().totalElements()).isEqualTo(6);
    assertThat(env.page().totalPages()).isEqualTo(3);
    assertThat(env.sort())
        .containsExactly(
            new PageEnvelope.SortOrder("weekStartDate", "DESC"),
            new PageEnvelope.SortOrder("employeeDisplayName", "ASC"));
  }

  @Test
  void of_emptyUnsorted_hasEmptySortArray() {
    PageImpl<String> page = new PageImpl<>(List.of(), PageRequest.of(0, 25), 0);

    PageEnvelope<String> env = PageEnvelope.of(page);

    assertThat(env.content()).isEmpty();
    assertThat(env.page().totalElements()).isZero();
    assertThat(env.sort()).isEmpty();
  }
}
