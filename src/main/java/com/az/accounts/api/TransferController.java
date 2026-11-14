package com.az.accounts.api;

import com.az.accounts.api.dto.TransferRequest;
import com.az.accounts.api.dto.TransferResponse;
import com.az.accounts.service.IdempotentTransferService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

    private final IdempotentTransferService transferService;

    public TransferController(IdempotentTransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse transfer(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return new TransferResponse(
                transferService.transfer(
                        idempotencyKey,
                        request.fromAccountId(),
                        request.toAccountId(),
                        request.amount()));
    }
}
