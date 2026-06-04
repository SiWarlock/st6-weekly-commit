package com.st6.wc.manager.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * B.20 — the pinned paginated response envelope (E13/E14-drilldown/comments). A custom record over
 * Spring Data {@code Page<T>} because the stock {@code PagedModel} serialization omits the {@code
 * sort} array B.20 requires (and Boot 3.x deprecated direct {@code Page} serialization). Shape:
 * {@code { content:[T], page:{number,size,totalElements,totalPages}, sort:[{property,direction}] }
 * }.
 */
public record PageEnvelope<T>(List<T> content, PageMeta page, List<SortOrder> sort) {

  /** Defensive immutable copies of the collections (no EI/EI2 exposure — LESSONS §22). */
  public PageEnvelope {
    content = List.copyOf(content);
    sort = List.copyOf(sort);
  }

  public record PageMeta(int number, int size, long totalElements, int totalPages) {}

  /**
   * {@code direction} is the Spring {@code Sort.Direction} name — {@code "ASC"} / {@code "DESC"}.
   */
  public record SortOrder(String property, String direction) {}

  /** Build the envelope from a {@code Page<T>}, preserving its page metadata + sort orders. */
  public static <T> PageEnvelope<T> of(Page<T> page) {
    PageMeta meta =
        new PageMeta(
            page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    List<SortOrder> sort =
        page.getSort().stream()
            .map(o -> new SortOrder(o.getProperty(), o.getDirection().name()))
            .toList();
    return new PageEnvelope<>(page.getContent(), meta, sort);
  }
}
