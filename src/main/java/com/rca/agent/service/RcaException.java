package com.rca.agent.service;

/**
 * Typed failures from root-cause analysis. Callers (HTTP layer, metrics) can switch on the nested
 * type instead of inspecting {@code null} or a generic message.
 */
public sealed class RcaException extends RuntimeException {

  public RcaException(String message) {
    super(message);
  }

  public RcaException(String message, Throwable cause) {
    super(message, cause);
  }

  /** Repository clone or local path resolution failed. */
  public static final class RepoResolutionFailed extends RcaException {
    public RepoResolutionFailed(String repo, Throwable cause) {
      super("Failed to resolve repository: " + repo, cause);
    }
  }
}
