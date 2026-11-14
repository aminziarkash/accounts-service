package com.az.accounts.api;

import com.az.accounts.api.dto.AccountResponse;
import com.az.accounts.api.dto.AmountRequest;
import com.az.accounts.api.dto.LedgerEntryResponse;
import com.az.accounts.api.dto.OpenAccountRequest;
import com.az.accounts.api.dto.PageResponse;
import com.az.accounts.api.mapper.AccountMapper;
import com.az.accounts.domain.Account;
import com.az.accounts.service.AccountService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private static final Set<String> LEDGER_SORT_FIELDS = Set.of("createdAt", "amount");
    private final AccountService accountService;
    private final AccountMapper mapper;

    public AccountController(AccountService accountService, AccountMapper mapper) {
        this.accountService = accountService;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        Account account = accountService.openAccount(request.customerId(), request.currency());
        URI location =
                ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(account.getId())
                        .toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(account));
    }

    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable Long id) {
        return mapper.toResponse(accountService.getAccount(id));
    }

    @PostMapping("/{id}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerEntryResponse deposit(
            @PathVariable Long id, @Valid @RequestBody AmountRequest request) {
        return mapper.toResponse(accountService.deposit(id, request.amount()));
    }

    @PostMapping("/{id}/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    public LedgerEntryResponse withdraw(
            @PathVariable Long id, @Valid @RequestBody AmountRequest request) {
        return mapper.toResponse(accountService.withdraw(id, request.amount()));
    }

    @GetMapping("/{id}/ledger")
    public PageResponse<LedgerEntryResponse> ledger(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {

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

        return PageResponse.from(
                accountService
                        .getLedger(id, PageRequest.of(page, size, order))
                        .map(entry -> mapper.toResponse(entry)));
    }
}
