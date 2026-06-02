package com.st6.wc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Covers {@link BuildSkeletonMarker} fully (both branches) so the :shared JaCoCo gate is green. */
class BuildSkeletonMarkerTest {

  @Test
  void describe_terse_returnsBareModule() {
    assertEquals("shared", new BuildSkeletonMarker("shared").describe(false));
  }

  @Test
  void describe_verbose_includesSkeletonLabel() {
    assertTrue(new BuildSkeletonMarker("shared").describe(true).contains("build skeleton"));
  }
}
