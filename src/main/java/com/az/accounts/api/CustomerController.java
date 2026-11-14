package com.az.accounts.api;

import com.az.accounts.api.dto.CreateCustomerRequest;
import com.az.accounts.api.dto.CustomerResponse;
import com.az.accounts.api.mapper.AccountMapper;
import com.az.accounts.domain.Customer;
import com.az.accounts.service.CustomerService;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;
    private final AccountMapper mapper;

    public CustomerController(CustomerService customerService, AccountMapper mapper) {
        this.customerService = customerService;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> create(@RequestBody CreateCustomerRequest request) {
        Customer created =
                customerService.create(request.email(), request.firstName(), request.lastName());
        URI location =
                ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(created.getId())
                        .toUri();
        return ResponseEntity.created(location).body(mapper.toResponse(created));
    }

    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable Long id) {
        return mapper.toResponse(customerService.get(id));
    }
}
