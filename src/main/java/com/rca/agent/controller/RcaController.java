package com.rca.agent.controller;

import com.rca.agent.model.RcaRequest;
import com.rca.agent.model.RcaResponse;
import com.rca.agent.service.RcaService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rca")
public class RcaController {

    private static final Logger log = LoggerFactory.getLogger(RcaController.class);
    private final RcaService rcaService;

    public RcaController(RcaService rcaService) {
        this.rcaService = rcaService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<RcaResponse> analyze(@Valid @RequestBody RcaRequest request) {
        log.info("Received RCA request for issue: {}", request.issueDescription());
        RcaResponse response = rcaService.analyze(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("RCA Agent is running");
    }
}
