package com.st6.wc.worker;

/**
 * Throwaway build-skeleton marker (task 0.2) exercising the :worker JaCoCo gate. Self-contained as
 * of 0.3 (the :shared {@code BuildSkeletonMarker} it used was replaced by real typed code); the
 * {@code shared<-worker} edge stays declared via {@code implementation project(':shared')} in
 * build.gradle. Replaced by the real sync-worker app wiring in task 0.5.
 */
public final class WorkerBuildSkeletonMarker {

  /**
   * @param verbose when true, a descriptive label; otherwise the bare module name.
   * @return a marker string for the worker module.
   */
  public String label(boolean verbose) {
    if (verbose) {
      return "wc-api:worker (build skeleton)";
    }
    return "worker";
  }
}
