package com.st6.wc;

/**
 * Throwaway build-skeleton marker (task 0.2). Exists only so JaCoCo has a class to measure on the
 * otherwise-empty {@code :shared} module while the build gates are stood up. Real typed code
 * (enums, base entities, DTOs per Appendix A) replaces this in task 0.3 — do not build on it.
 */
public final class BuildSkeletonMarker {

  private final String module;

  public BuildSkeletonMarker(String module) {
    this.module = module;
  }

  /**
   * @param verbose when true, returns a descriptive label; otherwise the bare module name.
   * @return a marker string identifying the owning module.
   */
  public String describe(boolean verbose) {
    if (verbose) {
      return "wc-api:" + module + " (build skeleton)";
    }
    return module;
  }
}
