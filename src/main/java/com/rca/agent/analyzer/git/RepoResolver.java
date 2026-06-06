package com.rca.agent.analyzer.git;

import com.rca.agent.config.RcaProperties;
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
    private static final int MAX_CACHED_REPOS = 10;

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

        if (cache.size() >= MAX_CACHED_REPOS && !cache.containsKey(repoPathOrUrl)) {
            evictOldest();
        }

        ResolvedRepo existing = cache.get(repoPathOrUrl);
        if (existing != null && Files.exists(Path.of(existing.localPath()))) {
            pullLatest(existing.localPath(), branch);
            return existing;
        }

        // Prevent duplicate clones for the same URL
        synchronized (repoPathOrUrl.intern()) {
            existing = cache.get(repoPathOrUrl);
            if (existing != null && Files.exists(Path.of(existing.localPath()))) {
                pullLatest(existing.localPath(), branch);
                return existing;
            }
            ResolvedRepo cloned = cloneRemote(repoPathOrUrl, branch);
            cache.put(repoPathOrUrl, cloned);
            return cloned;
        }
    }

    /**
     * No-op for cached repos. Cached repos persist until server shutdown.
     *
     * @param repo the resolved repo
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

    boolean isRemoteUrl(String path) {
        return path.startsWith("https://") || path.startsWith("http://")
                || path.startsWith("git@") || path.startsWith("ssh://");
    }

    private ResolvedRepo cloneRemote(String url, String branch) throws Exception {
        Path tempDir = Files.createTempDirectory("rca-repo-");
        log.info("Cloning remote repo {} to {}", url, tempDir);

        var cloneCommand = Git.cloneRepository()
                .setURI(url)
                .setDirectory(tempDir.toFile())
                .setDepth(50)
                .setTimeout(60);

        if (branch != null && !branch.isBlank()) {
            cloneCommand.setBranch(branch);
        }

        cloneCommand.call().close();
        log.info("Clone complete: {}", tempDir);
        return new ResolvedRepo(tempDir.toString(), true);
    }

    private void pullLatest(String repoPath, String branch) {
        try (Git git = Git.open(Path.of(repoPath).toFile())) {
            if (branch != null && !branch.isBlank()) {
                String currentBranch = git.getRepository().getBranch();
                if (!branch.equals(currentBranch)) {
                    git.checkout().setName(branch).call();
                }
            }
            log.info("Pulling latest changes for cached repo: {}", repoPath);
            git.pull().setTimeout(30).call();
        } catch (Exception e) {
            log.warn("Pull failed for {}, will use existing state: {}", repoPath, e.getMessage());
        }
    }

    private void evictOldest() {
        cache.entrySet().stream().findFirst().ifPresent(entry -> {
            log.info("Evicting cached repo: {}", entry.getKey());
            deletePath(Path.of(entry.getValue().localPath()));
            cache.remove(entry.getKey());
        });
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
