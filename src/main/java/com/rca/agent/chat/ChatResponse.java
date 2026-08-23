package com.rca.agent.chat;

import com.rca.agent.model.RcaResponse;
import java.util.List;

public record ChatResponse(
    String message, String sessionId, String action, List<String> quickReplies, RcaCard rca) {

  public static ChatResponse reply(String message, String sessionId) {
    return new ChatResponse(message, sessionId, null, List.of(), null);
  }

  public static ChatResponse reply(String message, String sessionId, List<String> quickReplies) {
    return new ChatResponse(message, sessionId, null, quickReplies, null);
  }

  public static ChatResponse withAction(String message, String sessionId, String action) {
    return new ChatResponse(message, sessionId, action, List.of(), null);
  }

  public static ChatResponse withAction(
      String message, String sessionId, String action, List<String> quickReplies) {
    return new ChatResponse(message, sessionId, action, quickReplies, null);
  }

  public static ChatResponse withRca(
      String message, String sessionId, String action, List<String> quickReplies, RcaCard rca) {
    return new ChatResponse(message, sessionId, action, quickReplies, rca);
  }

  /** Structured RCA payload for rich UI cards (optional; null for normal chat). */
  public record RcaCard(
      String rootCause,
      String severity,
      List<String> evidence,
      List<Snippet> snippets,
      List<String> recommendations,
      List<CommitRef> commits) {

    public static RcaCard from(RcaResponse response) {
      List<Snippet> snippets =
          response.codeSnippets() == null
              ? List.of()
              : response.codeSnippets().stream()
                  .map(s -> new Snippet(s.filePath(), s.lineNumber(), s.snippet()))
                  .toList();
      List<CommitRef> commits =
          response.relatedCommits() == null
              ? List.of()
              : response.relatedCommits().stream()
                  .limit(5)
                  .map(c -> new CommitRef(c.commitId(), c.author(), c.message()))
                  .toList();
      return new RcaCard(
          response.rootCause(),
          response.severity(),
          response.evidenceFromLogs() == null ? List.of() : response.evidenceFromLogs(),
          snippets,
          response.recommendations() == null ? List.of() : response.recommendations(),
          commits);
    }

    public record Snippet(String filePath, int lineNumber, String snippet) {}

    public record CommitRef(String commitId, String author, String message) {}
  }
}
