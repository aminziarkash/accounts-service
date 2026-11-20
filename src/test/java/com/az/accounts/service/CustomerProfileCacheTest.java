package com.az.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.az.accounts.TestcontainersConfiguration;
import com.az.accounts.config.CacheConfig;
import com.az.accounts.domain.Customer;
import com.az.accounts.exception.CustomerNotFoundException;
import com.az.accounts.repository.AccountRepository;
import com.az.accounts.repository.CustomerRepository;
import com.az.accounts.repository.LedgerEntryRepository;
import com.az.accounts.service.view.CustomerProfile;
import com.az.accounts.service.view.CustomerView;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CustomerProfileCacheTest {

    @Autowired private CustomerService customerService;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;
    @Autowired private CacheManager cacheManager;

    private CaffeineCache cache;

    @BeforeEach
    void setUp() {
        cache = (CaffeineCache) cacheManager.getCache(CacheConfig.PROFILES);
        cache.clear();
    }

    @AfterEach
    void cleanUp() {
        ledgerEntryRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
    }

    private long hits() {
        return cache.getNativeCache().stats().hitCount();
    }

    private long misses() {
        return cache.getNativeCache().stats().missCount();
    }

    private static String newEmail() {
        return UUID.randomUUID() + "@test.com";
    }

    @Test
    void secondReadComesFromTheCache() {
        CustomerView customer = customerService.create("owner-1", newEmail(), "Jane", "Doe");
        long hitsBefore = hits();
        long missesBefore = misses();

        CustomerProfile first = customerService.getProfile(customer.id());
        CustomerProfile second = customerService.getProfile(customer.id());

        assertThat(second).isEqualTo(first);
        assertThat(misses() - missesBefore).isEqualTo(1); // the first read went to the database
        assertThat(hits() - hitsBefore).isEqualTo(1); // the second read did not
    }

    @Test
    void updateEvictsSoTheNextReadSeesTheNewName() {
        CustomerView customer = customerService.create("owner-1", newEmail(), "Jane", "Doe");
        customerService.getProfile(customer.id()); // now cached
        long missesBefore = misses();

        customerService.updateName(customer.id(), "Janet", "Smith");
        CustomerProfile afterUpdate = customerService.getProfile(customer.id());

        assertThat(afterUpdate.firstName()).isEqualTo("Janet");
        assertThat(afterUpdate.lastName()).isEqualTo("Smith");
        assertThat(misses() - missesBefore).isEqualTo(1); // evicted, so it was loaded again
    }

    @Test
    void changeBehindTheCacheIsNotSeenUntilTheEntryIsEvicted() {
        CustomerView customer = customerService.create("owner-1", newEmail(), "Jane", "Doe");
        customerService.getProfile(customer.id()); // now cached

        // change the row without going through the service, like a script or another instance
        Customer row = customerRepository.findById(customer.id()).orElseThrow();
        row.changeName("Jane", "Sneaky");
        customerRepository.save(row);

        assertThat(customerService.getProfile(customer.id()).lastName())
                .as("still the old value: this is the stale window")
                .isEqualTo("Doe");

        cache.evict(customer.id());

        assertThat(customerService.getProfile(customer.id()).lastName()).isEqualTo("Sneaky");
    }

    @Test
    void aFailedLookupIsNotCached() {
        long hitsBefore = hits();
        long missesBefore = misses();

        assertThatThrownBy(() -> customerService.getProfile(-1L))
                .isInstanceOf(CustomerNotFoundException.class);
        assertThatThrownBy(() -> customerService.getProfile(-1L))
                .isInstanceOf(CustomerNotFoundException.class);

        assertThat(misses() - missesBefore).isEqualTo(2); // both went to the database
        assertThat(hits() - hitsBefore).isZero();
        assertThat(cache.get(-1L)).isNull();
    }
}
