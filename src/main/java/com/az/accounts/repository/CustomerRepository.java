package com.az.accounts.repository;

import com.az.accounts.domain.Customer;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    // the pitfall: collection fetch join with paging
    @Query(
            value = "select c from Customer c join fetch c.accounts order by c.id",
            countQuery = "select count(c) from Customer c")
    Page<Customer> findPageWithAccounts(Pageable pageable);

    // the fix, step 1: page only the ids (a real LIMIT)
    @Query("select c.id from Customer c order by c.id")
    Page<Long> findCustomerIds(Pageable pageable);

    // the fix, step 2: load those customers with their accounts
    @Query(
            "select distinct c from Customer c join fetch c.accounts where c.id in :ids order by c.id")
    List<Customer> findAllWithAccountsByIdIn(@Param("ids") List<Long> ids);

    @EntityGraph(attributePaths = "accounts")
    Optional<Customer> findWithAccountsById(Long id);

    Optional<Customer> findByEmail(String email);
}
