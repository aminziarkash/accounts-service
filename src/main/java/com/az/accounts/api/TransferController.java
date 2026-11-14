package com.az.accounts.api;

import com.az.accounts.api.dto.TransferRequest;
import com.az.accounts.api.dto.TransferResponse;
import com.az.accounts.service.AccessPolicy;
import com.az.accounts.service.CurrentUser;
import com.az.accounts.service.IdempotentTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Transfers", description = "Idempotent money transfers between accounts")
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final IdempotentTransferService transferService;
    private final AccessPolicy accessPolicy;

    public TransferController(
            IdempotentTransferService transferService, AccessPolicy accessPolicy) {
        this.transferService = transferService;
        this.accessPolicy = accessPolicy;
    }

    @Operation(
            summary = "Transfer money between two accounts",
            description =
                    "Idempotent: repeating the same Idempotency-Key with the same body returns the"
                            + " original result and moves money once.")
    @ApiResponse(responseCode = "201", description = "Transfer completed (or replayed)")
    @ApiResponse(responseCode = "400", description = "Invalid body or missing Idempotency-Key")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @ApiResponse(
            responseCode = "422",
            description = "Insufficient funds, or key reused with a different body")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(
            @Parameter(description = "Unique key per logical transfer, for example a UUID")
                    @RequestHeader("Idempotency-Key")
                    @NotBlank
                    @Size(max = 100)
                    String idempotencyKey,
            @Valid @RequestBody TransferRequest request,
            Authentication authentication) {
        // you may only move money out of your own account; the target can be anyone's
        accessPolicy.requireAccount(CurrentUser.from(authentication), request.fromAccountId());
        return new TransferResponse(
                transferService.transfer(
                        idempotencyKey,
                        request.fromAccountId(),
                        request.toAccountId(),
                        request.amount()));
    }
}
