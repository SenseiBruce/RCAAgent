package com.rca.agent.controller;

import com.rca.agent.fix.AutoFixService;
import com.rca.agent.fix.FixRequest;
import com.rca.agent.fix.FixResponse;
import com.rca.agent.model.RcaRequest;
import com.rca.agent.model.RcaResponse;
import com.rca.agent.service.RcaService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller exposing the RCA (Root Cause Analysis) API endpoints.
 * <p>
 * Provides:
 * <ul>
 *   <li>{@code POST /api/v1/rca/analyze} — Submit an issue for root cause analysis</li>
 *   <li>{@code GET /api/v1/rca/health} — Health check endpoint</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/rca")
public class RcaController {

    private static final Logger log = LoggerFactory.getLogger(RcaController.class);
    private final RcaService rcaService;
    private final AutoFixService autoFixService;

    public RcaController(RcaService rcaService, AutoFixService autoFixService) {
        this.rcaService = rcaService;
        this.autoFixService = autoFixService;
    }

    /**
     * Performs root cause analysis based on the provided issue details, logs, and git context.
     *
     * @param request validated request containing issue description and optional log/git data
     * @return analysis results including root cause, severity, evidence, and recommendations
     */
    @PostMapping("/analyze")
    public ResponseEntity<RcaResponse> analyze(@Valid @RequestBody RcaRequest request) {
        log.info("Received RCA request for issue: {}", request.issueDescription());
        RcaResponse response = rcaService.analyze(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns application health status.
     *
     * @return "RCA Agent is running" if the service is healthy
     */
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("RCA Agent is running");
    }

    /**
     * Auto-fixes the issue by generating code changes, pushing to a branch, and creating a PR.
     *
     * @param request fix request with RCA context
     * @param githubToken GitHub token passed via header
     * @return fix response with PR URL
     */
    @PostMapping("/fix")
    public ResponseEntity<FixResponse> fix(
            @Valid @RequestBody FixRequest request,
            @RequestHeader("X-GitHub-Token") String githubToken) {
        log.info("Received auto-fix request for: {}", request.rootCause());
        FixResponse response = autoFixService.fix(request, githubToken);
        return ResponseEntity.ok(response);
    }
}
