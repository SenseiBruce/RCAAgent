package com.rca.agent.fix.platform;

/**
 * Abstraction for git hosting platforms (GitHub, GitLab, etc.).
 * <p>
 * Implementations handle platform-specific API calls for creating
 * pull requests / merge requests.
 */
public interface GitPlatform {

    /**
     * Determines if this platform handles the given repository URL.
     *
     * @param repoUrl the repository URL
     * @return true if this platform can handle the URL
     */
    boolean supports(String repoUrl);

    /**
     * Creates a pull request / merge request on the platform.
     *
     * @param request the PR creation request details
     * @return URL of the created PR/MR
     */
    String createPullRequest(PrRequest request);

    /**
     * Returns the platform name.
     *
     * @return platform identifier (e.g., "github", "gitlab")
     */
    String name();
}
