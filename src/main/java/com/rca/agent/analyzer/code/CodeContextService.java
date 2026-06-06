package com.rca.agent.analyzer.code;

import com.rca.agent.analyzer.log.LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Extracts source code context from a repository based on file/line references found in error logs.
 * <p>
 * This service:
 * <ol>
 *   <li>Parses log entries to find file:line references (e.g., "UserService.java:42")</li>
 *   <li>Locates matching files within the repository</li>
 *   <li>Reads surrounding lines of code for context</li>
 *   <li>Produces a summary suitable for LLM analysis</li>
 * </ol>
 */
@Service
public class CodeContextService {

    private static final Logger log = LoggerFactory.getLogger(CodeContextService.class);

    private static final int CONTEXT_LINES = 10;

    private static final List<Pattern> FILE_LINE_PATTERNS = List.of(
            Pattern.compile("at\\s+([\\w.]+)\\.\\w+\\(([\\w]+\\.java):(\\d+)\\)"),
            Pattern.compile("([\\w/]+\\.(?:java|py|js|ts|go|rb|rs|kt|scala)):(\\d+)"),
            Pattern.compile("([\\w]+\\.(?:java|py|js|ts|go|rb|rs|kt|scala)):(\\d+)"),
            Pattern.compile("in\\s+([\\w.]+)\\.\\w+\\s*\\(.*?:?(\\d+)\\)")
    );

    /**
     * Extracts file and line number references from log entries.
     *
     * @param entries parsed log entries to scan
     * @return unique file references found in error messages and stack traces
     */
    public List<FileReference> extractFileReferences(List<LogEntry> entries) {
        Set<String> seen = new HashSet<>();
        List<FileReference> refs = new ArrayList<>();

        for (LogEntry entry : entries) {
            String message = entry.message();
            for (Pattern pattern : FILE_LINE_PATTERNS) {
                Matcher matcher = pattern.matcher(message);
                while (matcher.find()) {
                    String file;
                    int line;
                    if (matcher.groupCount() == 3) {
                        file = matcher.group(2);
                        line = Integer.parseInt(matcher.group(3));
                    } else {
                        file = matcher.group(1);
                        line = Integer.parseInt(matcher.group(2));
                    }
                    String key = file + ":" + line;
                    if (seen.add(key)) {
                        refs.add(new FileReference(file, line));
                    }
                }
            }
        }
        log.info("Extracted {} file references from log entries", refs.size());
        return refs;
    }

    /**
     * Reads source code context around the referenced lines from the repository.
     *
     * @param repoPath absolute path to the git repository
     * @param refs     file references to look up
     * @return map of file reference to the surrounding source code snippet
     */
    public Map<FileReference, String> getCodeContext(String repoPath, List<FileReference> refs) {
        Map<FileReference, String> context = new LinkedHashMap<>();
        Path repo = Path.of(repoPath);

        for (FileReference ref : refs) {
            try {
                Path filePath = findFile(repo, ref.filePath());
                if (filePath == null) {
                    log.debug("File not found in repo: {}", ref.filePath());
                    continue;
                }
                String snippet = readLinesAround(filePath, ref.lineNumber(), CONTEXT_LINES);
                context.put(ref, snippet);
            } catch (IOException e) {
                log.debug("Failed to read file {}: {}", ref.filePath(), e.getMessage());
            }
        }
        log.info("Retrieved code context for {} of {} file references", context.size(), refs.size());
        return context;
    }

    /**
     * Formats extracted code context into a text summary for LLM consumption.
     *
     * @param context map of file references to their source code snippets
     * @return formatted text with file paths, line numbers, and code
     */
    public String summarizeForLlm(Map<FileReference, String> context) {
        if (context.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("SOURCE CODE CONTEXT:\n");
        sb.append("Files referenced in error logs:\n\n");

        context.forEach((ref, code) -> {
            sb.append("--- ").append(ref.filePath()).append(":").append(ref.lineNumber()).append(" ---\n");
            sb.append(code).append("\n\n");
        });

        return sb.toString();
    }

    private Path findFile(Path repoRoot, String fileName) throws IOException {
        try (Stream<Path> stream = Files.walk(repoRoot)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !p.toString().contains(".git"))
                    .filter(p -> p.getFileName().toString().equals(fileName)
                            || p.toString().replace(repoRoot.toString() + "/", "").equals(fileName))
                    .findFirst()
                    .orElse(null);
        }
    }

    private String readLinesAround(Path file, int targetLine, int contextLines) throws IOException {
        List<String> allLines = Files.readAllLines(file);
        int start = Math.max(0, targetLine - contextLines - 1);
        int end = Math.min(allLines.size(), targetLine + contextLines);

        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            String marker = (i == targetLine - 1) ? " >>> " : "     ";
            sb.append(String.format("%s%4d | %s%n", marker, i + 1, allLines.get(i)));
        }
        return sb.toString();
    }
}
