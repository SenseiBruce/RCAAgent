package com.rca.agent.analyzer.git;

import com.rca.agent.config.RcaProperties;
import com.rca.agent.model.RcaResponse.GitChange;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.BlameCommand;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.StreamSupport;

/**
 * Service for analyzing git repositories using JGit.
 * <p>
 * Provides capabilities for:
 * <ul>
 *   <li>Retrieving recent commit history</li>
 *   <li>File-level blame annotations</li>
 *   <li>Commit diff generation</li>
 *   <li>Summarizing git context for LLM consumption</li>
 * </ul>
 */
@Service
public class GitAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(GitAnalyzerService.class);
    private final RcaProperties properties;

    public GitAnalyzerService(RcaProperties properties) {
        this.properties = properties;
    }

    /**
     * Retrieves recent commits from the specified repository and branch.
     *
     * @param repoPath absolute path to the local git repository
     * @param branch   branch name to analyze (uses default if null)
     * @return list of recent git changes, ordered newest first
     * @throws Exception if the repository cannot be opened or read
     */
    public List<GitChange> getRecentCommits(String repoPath, String branch) throws Exception {
        try (Git git = Git.open(new File(repoPath))) {
            String targetBranch = branch != null ? branch : properties.getGit().getDefaultBranch();
            int maxCommits = properties.getGit().getMaxCommits();

            Iterable<RevCommit> commits = git.log()
                    .add(git.getRepository().resolve(targetBranch))
                    .setMaxCount(maxCommits)
                    .call();

            return StreamSupport.stream(commits.spliterator(), false)
                    .map(commit -> new GitChange(
                            commit.getName().substring(0, 8),
                            commit.getAuthorIdent().getName(),
                            commit.getShortMessage(),
                            Instant.ofEpochSecond(commit.getCommitTime()),
                            getChangedFiles(git.getRepository(), commit)
                    ))
                    .toList();
        }
    }

    /**
     * Returns blame information for a specific file, showing which commit last modified each line.
     *
     * @param repoPath absolute path to the local git repository
     * @param filePath relative path to the file within the repository
     * @return formatted blame output with commit hash, author, line number, and content
     * @throws Exception if the repository cannot be opened
     */
    public String blameFile(String repoPath, String filePath) throws Exception {
        try (Git git = Git.open(new File(repoPath))) {
            BlameCommand blameCommand = git.blame().setFilePath(filePath);
            BlameResult result = blameCommand.call();
            if (result == null) return "File not found in repository: " + filePath;

            StringBuilder sb = new StringBuilder();
            int lines = result.getResultContents().size();
            for (int i = 0; i < lines; i++) {
                RevCommit commit = result.getSourceCommit(i);
                sb.append(String.format("%s (%s) L%d: %s%n",
                        commit.getName().substring(0, 8),
                        commit.getAuthorIdent().getName(),
                        i + 1,
                        result.getResultContents().getString(i)));
            }
            return sb.toString();
        }
    }

    /**
     * Generates the diff (patch) for a specific commit against its parent.
     *
     * @param repoPath absolute path to the local git repository
     * @param commitId full or abbreviated commit hash
     * @return unified diff output, or a message if it's an initial commit
     * @throws Exception if the commit cannot be resolved
     */
    public String getDiffForCommit(String repoPath, String commitId) throws Exception {
        try (Git git = Git.open(new File(repoPath))) {
            Repository repository = git.getRepository();
            RevCommit commit = repository.parseCommit(repository.resolve(commitId));

            if (commit.getParentCount() == 0) return "Initial commit — no diff available.";

            RevCommit parent = repository.parseCommit(commit.getParent(0));
            try (ObjectReader reader = repository.newObjectReader();
                 ByteArrayOutputStream out = new ByteArrayOutputStream();
                 DiffFormatter formatter = new DiffFormatter(out)) {

                formatter.setRepository(repository);
                CanonicalTreeParser oldTree = new CanonicalTreeParser();
                oldTree.reset(reader, parent.getTree());
                CanonicalTreeParser newTree = new CanonicalTreeParser();
                newTree.reset(reader, commit.getTree());

                List<DiffEntry> diffs = formatter.scan(oldTree, newTree);
                for (DiffEntry diff : diffs) {
                    formatter.format(diff);
                }
                return out.toString();
            }
        }
    }

    /**
     * Formats git changes into a text summary suitable for LLM context.
     *
     * @param changes list of git changes to summarize
     * @return formatted text summary of commits with timestamps, authors, and files
     */
    public String summarizeForLlm(List<GitChange> changes) {
        StringBuilder sb = new StringBuilder("GIT ANALYSIS:\n");
        sb.append("Recent commits (").append(changes.size()).append("):\n\n");
        changes.forEach(c -> sb.append(String.format("[%s] %s - %s (%s) | Files: %s%n",
                c.commitId(), c.timestamp(), c.author(), c.message(),
                String.join(", ", c.filesChanged()))));
        return sb.toString();
    }

    private List<String> getChangedFiles(Repository repository, RevCommit commit) {
        try {
            if (commit.getParentCount() == 0) return List.of("(initial commit)");

            RevCommit parent = commit.getParent(0);
            try (ObjectReader reader = repository.newObjectReader();
                 DiffFormatter formatter = new DiffFormatter(new ByteArrayOutputStream())) {

                formatter.setRepository(repository);
                CanonicalTreeParser oldTree = new CanonicalTreeParser();
                oldTree.reset(reader, repository.parseCommit(parent).getTree());
                CanonicalTreeParser newTree = new CanonicalTreeParser();
                newTree.reset(reader, commit.getTree());

                return formatter.scan(oldTree, newTree).stream()
                        .map(DiffEntry::getNewPath)
                        .toList();
            }
        } catch (IOException e) {
            log.debug("Could not get changed files for {}", commit.getName());
            return List.of();
        }
    }
}
