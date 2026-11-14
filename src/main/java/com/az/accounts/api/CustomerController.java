package com.az.accounts.api;

import com.az.accounts.api.dto.CreateCustomerRequest;
import com.az.accounts.service.AccessPolicy;
import com.az.accounts.service.CurrentUser;
import com.az.accounts.service.CustomerService;
import com.az.accounts.service.view.CustomerView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
    private final AccessPolicy accessPolicy;

    public CustomerController(CustomerService customerService, AccessPolicy accessPolicy) {
        this.customerService = customerService;
        this.accessPolicy = accessPolicy;
    }

    @Operation(summary = "Create a customer")
    @ApiResponse(
            responseCode = "201",
            description = "Customer created, Location header points to it")
    @ApiResponse(responseCode = "400", description = "Invalid fields")
    @ApiResponse(responseCode = "409", description = "A customer with this email already exists")
    @PostMapping
    public ResponseEntity<CustomerView> create(
            @Valid @RequestBody CreateCustomerRequest request, Authentication authentication) {
        // the owner is always the caller, never something the client sends
        CustomerView created =
                customerService.create(
                        CurrentUser.from(authentication).subject(),
                        request.email(),
                        request.firstName(),
                        request.lastName());
        URI location =
                ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(created.id())
                        .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @Operation(summary = "Get a customer with their accounts")
    @ApiResponse(responseCode = "200", description = "The customer")
    @ApiResponse(
            responseCode = "404",
            description = "Customer not found, or it belongs to someone else")
    @GetMapping("/{id}")
    public CustomerView get(@PathVariable Long id, Authentication authentication) {
        accessPolicy.requireCustomer(CurrentUser.from(authentication), id);
        return customerService.get(id);
    }
}
