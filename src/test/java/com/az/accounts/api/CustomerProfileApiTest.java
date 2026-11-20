package com.az.accounts.api;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.az.accounts.TestcontainersConfiguration;
import com.az.accounts.config.CacheConfig;
import com.az.accounts.domain.Customer;
import com.az.accounts.repository.AccountRepository;
import com.az.accounts.repository.CustomerRepository;
import com.az.accounts.repository.LedgerEntryRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** The cached profile read and the update, through the real security rules. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CustomerProfileApiTest {

    // their own subjects, so the rate limiter buckets of other test classes are not shared
    private static final RequestPostProcessor ALICE = jwt().jwt(j -> j.subject("profile-alice"));
    private static final RequestPostProcessor BOB = jwt().jwt(j -> j.subject("profile-bob"));
    private static final RequestPostProcessor ADMIN =
            jwt().jwt(j -> j.subject("profile-admin"))
                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));

    @Autowired private MockMvc mockMvc;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;
    @Autowired private CacheManager cacheManager;

    private long aliceCustomer;

    @BeforeEach
    void setUp() {
        cleanUp();
        cacheManager.getCache(CacheConfig.PROFILES).clear();
        // created through the repository on purpose: setup must not use the rate limiter
        aliceCustomer =
                customerRepository
                        .save(
                                new Customer(
                                        "profile-alice",
                                        UUID.randomUUID() + "@test.com",
                                        "Jane",
                                        "Doe"))
                        .getId();
    }

    @AfterEach
    void cleanUp() {
        ledgerEntryRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
    }

    private static final String NEW_NAME = "{\"firstName\":\"Janet\",\"lastName\":\"Smith\"}";

    @Test
    void profileShowsNameAndEmailButNoAccounts() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{id}/profile", aliceCustomer).with(ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.email").exists())
                .andExpect(jsonPath("$.accounts").doesNotExist());
    }

    @Test
    void someoneElsesProfileIsNotFoundEvenWhenItIsCached() throws Exception {
        // alice reads it, so it is in the cache now
        mockMvc.perform(get("/api/v1/customers/{id}/profile", aliceCustomer).with(ALICE))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/customers/{id}/profile", aliceCustomer).with(BOB))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/customers/{id}/profile", aliceCustomer).with(ADMIN))
                .andExpect(status().isOk());
    }

    @Test
    void updateChangesTheNameAndTheNextReadSeesIt() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{id}/profile", aliceCustomer).with(ALICE))
                .andExpect(status().isOk()); // cached with the old name

        mockMvc.perform(
                        put("/api/v1/customers/{id}/profile", aliceCustomer)
                                .with(ALICE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(NEW_NAME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Smith"));

        mockMvc.perform(get("/api/v1/customers/{id}/profile", aliceCustomer).with(ALICE))
                .andExpect(jsonPath("$.firstName").value("Janet"))
                .andExpect(jsonPath("$.lastName").value("Smith"));
    }

    @Test
    void someoneElseCannotUpdateTheProfile() throws Exception {
        mockMvc.perform(
                        put("/api/v1/customers/{id}/profile", aliceCustomer)
                                .with(BOB)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(NEW_NAME))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/customers/{id}/profile", aliceCustomer).with(ALICE))
                .andExpect(jsonPath("$.lastName").value("Doe"));
    }

    @Test
    void updateWithBlankNameIsRejected() throws Exception {
        mockMvc.perform(
                        put("/api/v1/customers/{id}/profile", aliceCustomer)
                                .with(ALICE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"firstName\":\" \",\"lastName\":\"Smith\"}"))
                .andExpect(status().isBadRequest());
    }
}
