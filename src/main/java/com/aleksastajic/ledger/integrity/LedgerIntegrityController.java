package com.aleksastajic.ledger.integrity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ledger")
@Tag(name = "Ledger")
public class LedgerIntegrityController {

    private final LedgerIntegrityService ledgerIntegrityService;

    public LedgerIntegrityController(LedgerIntegrityService ledgerIntegrityService) {
        this.ledgerIntegrityService = ledgerIntegrityService;
    }

    @GetMapping("/integrity")
    @Operation(summary = "Verify ledger hash chain integrity")
    public ResponseEntity<LedgerIntegrityReport> verify() {
        LedgerIntegrityReport report = ledgerIntegrityService.verify();
        if ("FAILED".equalsIgnoreCase(report.status())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(report);
        }
        return ResponseEntity.ok(report);
    }
}
