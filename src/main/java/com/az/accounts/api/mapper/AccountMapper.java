package com.az.accounts.api.mapper;

import com.az.accounts.api.dto.AccountResponse;
import com.az.accounts.api.dto.CustomerResponse;
import com.az.accounts.api.dto.LedgerEntryResponse;
import com.az.accounts.domain.Account;
import com.az.accounts.domain.Customer;
import com.az.accounts.domain.LedgerEntry;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface AccountMapper {

    @Mapping(source = "customer.id", target = "customerId")
    AccountResponse toResponse(Account account);

    LedgerEntryResponse toResponse(LedgerEntry entry);

    CustomerResponse toResponse(Customer customer);
}
