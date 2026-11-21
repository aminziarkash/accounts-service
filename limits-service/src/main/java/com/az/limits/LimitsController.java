package com.az.limits;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/limits")
public class LimitsController {

    private final LimitsService service;

    public LimitsController(LimitsService service) {
        this.service = service;
    }

    /**
     * A "no" is a normal answer: it comes back as 200 with allowed=false, not as an error.
     *
     * @param request The request body
     * @return CheckResponse
     */
    @PostMapping("/check")
    public CheckResponse check(@Valid @RequestBody CheckRequest request) {
        return service.check(request);
    }
}