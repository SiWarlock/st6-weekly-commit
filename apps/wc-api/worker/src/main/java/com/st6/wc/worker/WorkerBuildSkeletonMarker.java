package com.st6.wc.worker;

import com.st6.wc.BuildSkeletonMarker;

/**
 * Throwaway build-skeleton marker (task 0.2). Exercises the :worker JaCoCo gate and references a
 * {@code :shared} symbol so the {@code shared<-worker} dependency edge is proven at compile time.
 * Replaced by the real sync-worker app wiring in task 0.5.
 */
public final class WorkerBuildSkeletonMarker {

  /**
   * @param verbose forwarded to the shared marker.
   * @return the :shared marker string for the worker module.
   */
  public String label(boolean verbose) {
    BuildSkeletonMarker shared = new BuildSkeletonMarker("worker");
    if (verbose) {
      return shared.describe(true);
    }
    return shared.describe(false);
  }
}
