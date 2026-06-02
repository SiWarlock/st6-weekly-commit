package com.st6.wc.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Covers {@link ApiBuildSkeletonMarker} fully (both branches) so the :api JaCoCo gate is green. */
class ApiBuildSkeletonMarkerTest {

  @Test
  void label_terse_isBareModule() {
    assertEquals("api", new ApiBuildSkeletonMarker().label(false));
  }

  @Test
  void label_verbose_includesSkeletonLabel() {
    assertTrue(new ApiBuildSkeletonMarker().label(true).contains("build skeleton"));
  }
}
