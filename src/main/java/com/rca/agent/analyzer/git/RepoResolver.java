package com.rca.agent.analyzer.git;

import jakarta.annotation.PreDestroy;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Resolves repository references to local paths with caching.
 * <p>
 * Remote repos are cloned once and cached for the lifetime of the application.
 * Subsequent requests for the same repo pull latest changes instead of re-cloning.
 * All cached repos are cleaned up on application shutdown.
 */
@Service
public class RepoResolver {

    private static final Logger log = LoggerFactory.getLogger(RepoResolver.class);

    private final ConcurrentMap<String, ResolvedRepo> cache = new ConcurrentHashMap<>();

    /**
     * Resolves a repo path or URL to a local directory.
     * Remote repos are cached — subsequent calls pull latest changes.
     *
     * @param repoPathOrUrl local path or remote git URL
     * @param branch        branch to clone/pull (null for default)
     * @return resolved repo with local path
     * @throws Exception if cloning/pulling fails
     */
    public ResolvedRepo resolve(String repoPathOrUrl, String branch) throws Exception {
        if (!isRemoteUrl(repoPathOrUrl)) {
            return new ResolvedRepo(repoPathOrUrl, false);
        }

        ResolvedRepo cached = cache.get(repoPathOrUrl);
        if (cached != null && Files.exists(Path.of(cached.localPath()))) {
            pullLatest(cached.localPath(), branch);
            return cached;
        }

        ResolvedRepo cloned = cloneRemote(repoPathOrUrl, branch);
        cache.put(repoPathOrUrl, cloned);
        return cloned;
    }

    /**
     * Cleans up a resolved repo. Only temporary repos that are NOT cached get deleted.
     * Cached repos persist until server shutdown.
     *
     * @param repo the resolved repo (no-op for cached or local repos)
     */
    public void cleanup(ResolvedRepo repo) {
        // Cached repos are cleaned up on shutdown, not per-request
    }

    /**
     * Cleans up all cached repos on application shutdown.
     */
    @PreDestroy
    public void shutdownCleanup() {
        log.info("Cleaning up {} cached repositories", cache.size());
        cache.values().forEach(repo -> deletePath(Path.of(repo.localPath())));
        cache.clear();
    }

    private boolean isRemoteUrl(String path) {
        return path.startsWith("https://") || path.startsWith("http://")
                || path.startsWith("git@") || path.startsWith("ssh://")
                || path.startsWith("file://");
    }

    private ResolvedRepo cloneRemote(String url, String branch) throws Exception {
        Path tempDir = Files.createTempDirectory("rca-repo-");
        log.info("Cloning remote repo {} to {}", url, tempDir);

        var cloneCommand = Git.cloneRepository()
                .setURI(url)
                .setDirectory(tempDir.toFile())
                .setDepth(50);

        if (branch != null && !branch.isBlank()) {
            cloneCommand.setBranch(branch);
        }

        cloneCommand.call().close();
        log.info("Clone complete: {}", tempDir);
        return new ResolvedRepo(tempDir.toString(), true);
    }

    private void pullLatest(String repoPath, String branch) {
        try (Git git = Git.open(Path.of(repoPath).toFile())) {
            log.info("Pulling latest changes for cached repo: {}", repoPath);
            git.pull().call();
        } catch (Exception e) {
            log.warn("Pull failed for {}, will use existing state: {}", repoPath, e.getMessage());
        }
    }

    private void deletePath(Path path) {
        try {
            if (Files.exists(path)) {
                Files.walk(path)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
                log.debug("Deleted cached repo: {}", path);
            }
        } catch (IOException e) {
            log.warn("Failed to delete cached repo: {}", path, e);
        }
    }

    /**
     * Holds a resolved local repo path and whether it's a temporary clone.
     *
     * @param localPath   absolute path to the local repo directory
     * @param isTemporary true if this is a cloned repo (managed by cache)
     */
    public record ResolvedRepo(String localPath, boolean isTemporary) {}
}
