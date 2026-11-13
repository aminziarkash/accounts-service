package com.az.accounts.repository;

import com.az.accounts.domain.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    @EntityGraph(attributePaths = "account")
    @Query("select e from LedgerEntry e")
    List<LedgerEntry> findAllWithAccount();

    Page<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(Long accountId, Pageable pageable);
}
