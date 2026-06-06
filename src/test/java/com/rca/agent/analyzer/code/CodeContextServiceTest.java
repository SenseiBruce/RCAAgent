package com.rca.agent.analyzer.code;

import com.rca.agent.analyzer.log.LogEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CodeContextServiceTest {

    private CodeContextService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new CodeContextService();
    }

    @Test
    void extractFileReferences_javaStackTrace_extractsFileAndLine() {
        List<LogEntry> entries = List.of(
                entry("java.lang.NullPointerException at com.rca.service.UserService.login(UserService.java:42)")
        );

        List<FileReference> refs = service.extractFileReferences(entries);

        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).filePath()).isEqualTo("UserService.java");
        assertThat(refs.get(0).lineNumber()).isEqualTo(42);
    }

    @Test
    void extractFileReferences_simpleFileColonLine_extracts() {
        List<LogEntry> entries = List.of(
                entry("Error in PaymentService.java:87 - null payment method")
        );

        List<FileReference> refs = service.extractFileReferences(entries);

        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).filePath()).isEqualTo("PaymentService.java");
        assertThat(refs.get(0).lineNumber()).isEqualTo(87);
    }

    @Test
    void extractFileReferences_multipleReferences_extractsAll() {
        List<LogEntry> entries = List.of(
                entry("NullPointerException at com.app.UserService.login(UserService.java:42)"),
                entry("Caused by: at com.app.AuthProvider.validate(AuthProvider.java:15)")
        );

        List<FileReference> refs = service.extractFileReferences(entries);

        assertThat(refs).hasSize(2);
    }

    @Test
    void extractFileReferences_duplicates_deduped() {
        List<LogEntry> entries = List.of(
                entry("Error at com.app.Foo.bar(Foo.java:10)"),
                entry("Again at com.app.Foo.baz(Foo.java:10)")
        );

        List<FileReference> refs = service.extractFileReferences(entries);

        assertThat(refs).hasSize(1);
    }

    @Test
    void extractFileReferences_noReferences_returnsEmpty() {
        List<LogEntry> entries = List.of(
                entry("Something went wrong without file info")
        );

        List<FileReference> refs = service.extractFileReferences(entries);

        assertThat(refs).isEmpty();
    }

    @Test
    void extractFileReferences_pathWithSlashes_extracts() {
        List<LogEntry> entries = List.of(
                entry("Error at src/main/Service.java:55")
        );

        List<FileReference> refs = service.extractFileReferences(entries);

        assertThat(refs).isNotEmpty();
        assertThat(refs).anyMatch(r -> r.filePath().contains("Service.java") && r.lineNumber() == 55);
    }

    @Test
    void getCodeContext_fileExists_readsAroundLine() throws IOException {
        Path srcDir = tempDir.resolve("src");
        Files.createDirectories(srcDir);
        StringBuilder content = new StringBuilder();
        for (int i = 1; i <= 30; i++) {
            content.append("line ").append(i).append(" of code\n");
        }
        Files.writeString(srcDir.resolve("MyService.java"), content.toString());

        List<FileReference> refs = List.of(new FileReference("MyService.java", 15));
        Map<FileReference, String> context = service.getCodeContext(tempDir.toString(), refs);

        assertThat(context).hasSize(1);
        String snippet = context.get(refs.get(0));
        assertThat(snippet).contains(">>> ");
        assertThat(snippet).contains("line 15 of code");
        assertThat(snippet).contains("line 5 of code");
        assertThat(snippet).contains("line 25 of code");
    }

    @Test
    void getCodeContext_fileNotFound_skips() {
        List<FileReference> refs = List.of(new FileReference("NonExistent.java", 10));
        Map<FileReference, String> context = service.getCodeContext(tempDir.toString(), refs);

        assertThat(context).isEmpty();
    }

    @Test
    void getCodeContext_lineAtStart_handlesGracefully() throws IOException {
        Files.writeString(tempDir.resolve("Start.java"), "line 1\nline 2\nline 3\n");

        List<FileReference> refs = List.of(new FileReference("Start.java", 1));
        Map<FileReference, String> context = service.getCodeContext(tempDir.toString(), refs);

        assertThat(context).hasSize(1);
        assertThat(context.get(refs.get(0))).contains("line 1");
    }

    @Test
    void getCodeContext_lineAtEnd_handlesGracefully() throws IOException {
        Files.writeString(tempDir.resolve("End.java"), "line 1\nline 2\nline 3\n");

        List<FileReference> refs = List.of(new FileReference("End.java", 3));
        Map<FileReference, String> context = service.getCodeContext(tempDir.toString(), refs);

        assertThat(context).hasSize(1);
        assertThat(context.get(refs.get(0))).contains("line 3");
    }

    @Test
    void summarizeForLlm_withContext_formatsCorrectly() {
        Map<FileReference, String> context = Map.of(
                new FileReference("Service.java", 42), "     41 | code before\n >>>  42 | the buggy line\n     43 | code after\n"
        );

        String summary = service.summarizeForLlm(context);

        assertThat(summary).contains("SOURCE CODE CONTEXT:");
        assertThat(summary).contains("Service.java:42");
        assertThat(summary).contains("the buggy line");
    }

    @Test
    void summarizeForLlm_emptyContext_returnsEmpty() {
        String summary = service.summarizeForLlm(Map.of());
        assertThat(summary).isEmpty();
    }

    @Test
    void extractFileReferences_pythonFile_extracts() {
        List<LogEntry> entries = List.of(entry("File handler.py:23 raised exception"));

        List<FileReference> refs = service.extractFileReferences(entries);

        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).filePath()).isEqualTo("handler.py");
    }

    @Test
    void extractFileReferences_typescriptFile_extracts() {
        List<LogEntry> entries = List.of(entry("Error in app.ts:100"));

        List<FileReference> refs = service.extractFileReferences(entries);

        assertThat(refs).hasSize(1);
        assertThat(refs.get(0).filePath()).isEqualTo("app.ts");
    }

    private LogEntry entry(String message) {
        return new LogEntry(Instant.now(), "ERROR", message, "", Map.of());
    }
}
