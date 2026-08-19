package com.rca.agent.fix;

/**
 * Typed auto-fix failures so HTTP handlers and tests can distinguish clone, apply, and platform
 * errors instead of a generic {@link FixResponse} message string.
 */
public sealed class AutoFixException extends RuntimeException {

  public AutoFixException(String message) {
    super(message);
  }

  public AutoFixException(String message, Throwable cause) {
    super(message, cause);
  }

  public static final class RepoResolutionFailed extends AutoFixException {
    public RepoResolutionFailed(String repoUrl, Throwable cause) {
      super("Failed to resolve repository for auto-fix: " + repoUrl, cause);
    }
  }

  public static final class ApplyFailed extends AutoFixException {
    public ApplyFailed(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static final class UnsupportedPlatform extends AutoFixException {
    public UnsupportedPlatform(String repoUrl) {
      super("Unsupported git platform for URL: " + repoUrl);
    }
  }
}
