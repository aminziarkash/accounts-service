package com.az.accounts.api;

import com.az.accounts.api.dto.CreateCustomerRequest;
import com.az.accounts.api.dto.CustomerResponse;
import com.az.accounts.api.mapper.AccountMapper;
import com.az.accounts.domain.Customer;
import com.az.accounts.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@Tag(name = "Customers", description = "Create and read customers")
@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final AccountMapper mapper;

    public CustomerController(CustomerService customerService, AccountMapper mapper) {
        this.customerService = customerService;
        this.mapper = mapper;
    }

    @Operation(summary = "Create a customer")
    @ApiResponse(
            responseCode = "201",
            description = "Customer created, Location header points to it")
    @ApiResponse(responseCode = "400", description = "Invalid fields")
    @ApiResponse(responseCode = "409", description = "A customer with this email already exists")
    @PostMapping
    public ResponseEntity<CustomerResponse> create(
            @Valid @RequestBody CreateCustomerRequest request) {
        Customer created =
                customerService.create(request.email(), request.firstName(), request.lastName());
        URI location =
                ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(created.getId())
                        .toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(created));
    }

    @Operation(summary = "Get a customer with their accounts")
    @ApiResponse(responseCode = "200", description = "The customer")
    @ApiResponse(responseCode = "404", description = "Customer not found")
    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable Long id) {
        return mapper.toResponse(customerService.get(id));
    }
}
