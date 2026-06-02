package com.st6.wc.api;

/**
 * Throwaway build-skeleton marker (task 0.2) exercising the :api JaCoCo gate. Self-contained as of
 * 0.3 (the :shared {@code BuildSkeletonMarker} it used was replaced by real typed code); the {@code
 * shared<-api} edge stays declared via {@code implementation project(':shared')} in build.gradle.
 * Replaced by the real Spring Boot app wiring in task 0.4.
 */
public final class ApiBuildSkeletonMarker {

  /**
   * @param verbose when true, a descriptive label; otherwise the bare module name.
   * @return a marker string for the api module.
   */
  public String label(boolean verbose) {
    if (verbose) {
      return "wc-api:api (build skeleton)";
    }
    return "api";
  }
}
