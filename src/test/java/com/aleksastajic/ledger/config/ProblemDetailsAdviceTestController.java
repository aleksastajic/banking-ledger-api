package com.aleksastajic.ledger.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/_test")
@Validated
public class ProblemDetailsAdviceTestController {

    @PostMapping("/validate")
    public void validate(@Valid @RequestBody TestRequest request) {
    }

    @GetMapping("/ok")
    public String ok() {
        return "ok";
    }

    public record TestRequest(
            @NotBlank String name,
            @Min(1) int count
    ) {
    }
}
