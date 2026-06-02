package com.st6.wc.worker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Covers {@link WorkerBuildSkeletonMarker} fully (both branches) so the :worker JaCoCo gate is
 * green.
 */
class WorkerBuildSkeletonMarkerTest {

  @Test
  void label_terse_isBareModule() {
    assertEquals("worker", new WorkerBuildSkeletonMarker().label(false));
  }

  @Test
  void label_verbose_includesSkeletonLabel() {
    assertTrue(new WorkerBuildSkeletonMarker().label(true).contains("build skeleton"));
  }
}
