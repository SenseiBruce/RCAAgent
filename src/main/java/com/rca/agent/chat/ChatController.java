package com.rca.agent.chat;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/v1/rca/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);
    private final ChatService chatService;
    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        ChatResponse response = chatService.chat(request);
        return ResponseEntity.ok(response);
    }

    /**
     * SSE streaming endpoint — sends progress events during analysis.
     * Events: "thinking", "analyzing", "result"
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@Valid @RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);

        executor.submit(() -> {
            try {
                // Send thinking event immediately
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data("{\"phase\": \"thinking\", \"message\": \"Processing your request...\"}"));

                ChatResponse response = chatService.chat(request);

                // Send the result
                emitter.send(SseEmitter.event()
                        .name("result")
                        .data(response));

                emitter.complete();
            } catch (Exception e) {
                log.error("SSE stream error", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"message\": \"" + e.getMessage().replace("\"", "'") + "\"}"));
                } catch (IOException ignored) {}
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    /**
     * Upload a log file for analysis.
     */
    @PostMapping("/upload")
    public ResponseEntity<ChatResponse> uploadLog(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "sessionId", required = false) String sessionId) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "uploaded.log";
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);

            log.info("Received log upload: {} ({} bytes)", filename, content.length());
            ChatResponse response = chatService.handleLogUpload(sessionId, filename, content);
            return ResponseEntity.ok(response);
        } catch (IOException e) {
            log.error("Failed to read uploaded file", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
