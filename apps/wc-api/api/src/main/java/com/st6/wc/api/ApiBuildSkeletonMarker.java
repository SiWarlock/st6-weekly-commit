package com.st6.wc.api;

import com.st6.wc.BuildSkeletonMarker;

/**
 * Throwaway build-skeleton marker (task 0.2). Besides exercising the :api JaCoCo gate, it
 * references a {@code :shared} symbol so the {@code shared<-api} dependency edge is proven at
 * compile time. Replaced by the real Spring Boot app wiring in task 0.4.
 */
public final class ApiBuildSkeletonMarker {

  /**
   * @param verbose forwarded to the shared marker.
   * @return the :shared marker string for the api module.
   */
  public String label(boolean verbose) {
    BuildSkeletonMarker shared = new BuildSkeletonMarker("api");
    if (verbose) {
      return shared.describe(true);
    }
    return shared.describe(false);
  }
}
