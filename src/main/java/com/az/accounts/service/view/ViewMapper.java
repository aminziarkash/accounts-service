package com.az.accounts.service.view;

import com.az.accounts.domain.Account;
import com.az.accounts.domain.Customer;
import com.az.accounts.domain.LedgerEntry;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface ViewMapper {

    @Mapping(source = "customer.id", target = "customerId")
    AccountView toView(Account account);

    LedgerEntryView toView(LedgerEntry entry);

    CustomerView toView(Customer customer);
}
