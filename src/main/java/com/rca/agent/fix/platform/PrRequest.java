package com.rca.agent.fix.platform;

/**
 * Platform-agnostic request to create a pull request / merge request.
 *
 * @param repoUrl    full repository URL
 * @param headBranch the branch with the fix
 * @param baseBranch the target branch to merge into
 * @param title      PR/MR title
 * @param body       PR/MR description body (markdown)
 * @param token      authentication token for the platform API
 */
public record PrRequest(
        String repoUrl,
        String headBranch,
        String baseBranch,
        String title,
        String body,
        String token
) {}
