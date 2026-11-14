package com.az.accounts.api;

import com.az.accounts.api.dto.AmountRequest;
import com.az.accounts.api.dto.OpenAccountRequest;
import com.az.accounts.api.dto.PageResponse;
import com.az.accounts.service.AccountService;
import com.az.accounts.service.IdempotentAccountService;
import com.az.accounts.service.view.AccountView;
import com.az.accounts.service.view.LedgerEntryView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "Accounts", description = "Open accounts, deposit, withdraw and read the ledger")
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private static final Set<String> LEDGER_SORT_FIELDS = Set.of("createdAt", "amount");
    private final AccountService accountService;
    private final IdempotentAccountService idempotentAccountService;

    public AccountController(
            AccountService accountService, IdempotentAccountService idempotentAccountService) {
        this.accountService = accountService;
        this.idempotentAccountService = idempotentAccountService;
    }

    @Operation(summary = "Open an account for a customer")
    @ApiResponse(responseCode = "201", description = "Account opened, Location header points to it")
    @ApiResponse(responseCode = "400", description = "Invalid customer id or currency code")
    @ApiResponse(responseCode = "404", description = "Customer not found")
    @PostMapping
    public ResponseEntity<AccountView> open(@Valid @RequestBody OpenAccountRequest request) {
        AccountView account = accountService.openAccount(request.customerId(), request.currency());
        URI location =
                ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(account.id())
                        .toUri();
        return ResponseEntity.created(location).body(account);
    }

    @Operation(summary = "Get an account and its balance")
    @ApiResponse(responseCode = "200", description = "The account")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @GetMapping("/{id}")
    public AccountView get(@PathVariable Long id) {
        return accountService.getAccount(id);
    }

    @Operation(
            summary = "Deposit money",
            description =
                    "Adds the amount to the balance and writes a ledger entry in one transaction."
                            + " Idempotent: the same Idempotency-Key with the same body returns the"
                            + " original ledger entry and deposits once.")
    @ApiResponse(responseCode = "201", description = "The ledger entry (created or replayed)")
    @ApiResponse(
            responseCode = "400",
            description =
                    "Amount missing, not positive, or more than 2 decimals, or missing Idempotency-Key")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @ApiResponse(responseCode = "422", description = "Key reused with a different body")
    @PostMapping("/{id}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerEntryView deposit(
            @Parameter(description = "Unique key per logical operation, for example a UUID")
                    @RequestHeader("Idempotency-Key")
                    @NotBlank
                    @Size(max = 100)
                    String idempotencyKey,
            @PathVariable Long id,
            @Valid @RequestBody AmountRequest request) {
        return idempotentAccountService.deposit(idempotencyKey, id, request.amount());
    }

    @Operation(
            summary = "Withdraw money",
            description =
                    "Subtracts the amount from the balance and writes a ledger entry in one"
                            + " transaction. Idempotent: the same Idempotency-Key with the same body"
                            + " returns the original ledger entry and withdraws once.")
    @ApiResponse(responseCode = "201", description = "The ledger entry (created or replayed)")
    @ApiResponse(
            responseCode = "400",
            description =
                    "Amount missing, not positive, or more than 2 decimals, or missing Idempotency-Key")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @ApiResponse(
            responseCode = "422",
            description = "Insufficient funds, or key reused with a different body")
    @PostMapping("/{id}/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerEntryView withdraw(
            @Parameter(description = "Unique key per logical operation, for example a UUID")
                    @RequestHeader("Idempotency-Key")
                    @NotBlank
                    @Size(max = 100)
                    String idempotencyKey,
            @PathVariable Long id,
            @Valid @RequestBody AmountRequest request) {
        return idempotentAccountService.withdraw(idempotencyKey, id, request.amount());
    }

    @Operation(
            summary = "List ledger entries of an account",
            description =
                    "Paged. Newest first by default; the entry id is always the tie-breaker, so paging is stable.")
    @ApiResponse(responseCode = "200", description = "One page of ledger entries")
    @ApiResponse(
            responseCode = "400",
            description = "Page size above 100, unknown sort field or direction")
    @ApiResponse(responseCode = "404", description = "Account not found")
    @GetMapping("/{id}/ledger")
    public PageResponse<LedgerEntryView> ledger(
            @PathVariable Long id,
            @Parameter(description = "Zero-based page number")
                    @RequestParam(defaultValue = "0")
                    @Min(0)
                    int page,
            @Parameter(description = "Page size, 1 to 100")
                    @RequestParam(defaultValue = "20")
                    @Min(1)
                    @Max(100)
                    int size,
            @Parameter(description = "Sort field: createdAt or amount")
                    @RequestParam(defaultValue = "createdAt")
                    String sort,
            @Parameter(description = "Sort direction: asc or desc")
                    @RequestParam(defaultValue = "desc")
                    String direction) {

        if (!LEDGER_SORT_FIELDS.contains(sort)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Cannot sort by '" + sort + "'. Allowed: " + LEDGER_SORT_FIELDS);
        }
        Sort.Direction dir =
                Sort.Direction.fromOptionalString(direction)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.BAD_REQUEST, "Direction must be asc or desc"));

        // the id is the tie-breaker, so the order is always deterministic
        Sort order = Sort.by(dir, sort).and(Sort.by(dir, "id"));

        return PageResponse.from(accountService.getLedger(id, PageRequest.of(page, size, order)));
    }
}
