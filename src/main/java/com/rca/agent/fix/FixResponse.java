package com.rca.agent.fix;

import java.util.List;

/**
 * Response from the auto-fix agent.
 *
 * @param pullRequestUrl URL of the created pull request
 * @param branchName     name of the fix branch
 * @param filesChanged   list of files modified by the fix
 * @param summary        summary of changes made
 */
public record FixResponse(
        String pullRequestUrl,
        String branchName,
        List<String> filesChanged,
        String summary
) {}
