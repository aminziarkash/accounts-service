package com.az.accounts.repository;

import com.az.accounts.domain.Account;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<List<Account>> findByCustomerId(Long customerId);
}
