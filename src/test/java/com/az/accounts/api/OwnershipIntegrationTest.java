package com.az.accounts.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.az.accounts.TestcontainersConfiguration;
import com.az.accounts.domain.Customer;
import com.az.accounts.repository.AccountRepository;
import com.az.accounts.repository.CustomerRepository;
import com.az.accounts.repository.IdempotencyRecordRepository;
import com.az.accounts.repository.LedgerEntryRepository;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Real security rules, real services, real database: who may touch whose money. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OwnershipIntegrationTest {

    private static final RequestPostProcessor ALICE = jwt().jwt(j -> j.subject("alice-sub"));
    private static final RequestPostProcessor BOB = jwt().jwt(j -> j.subject("bob-sub"));
    private static final RequestPostProcessor ADMIN =
            jwt().jwt(j -> j.subject("admin-sub"))
                    .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));

    @Autowired private MockMvc mockMvc;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private LedgerEntryRepository ledgerEntryRepository;
    @Autowired private IdempotencyRecordRepository idempotencyRecordRepository;

    private long aliceCustomer;
    private long aliceAccount;
    private long bobCustomer;
    private long bobAccount;

    @BeforeEach
    void setUp() throws Exception {
        cleanUp();
        aliceCustomer = createCustomer(ALICE);
        aliceAccount = openAccount(ALICE, aliceCustomer);
        deposit(ALICE, aliceAccount, "50.00").andExpect(status().isCreated());
        bobCustomer = createCustomer(BOB);
        bobAccount = openAccount(BOB, bobCustomer);
        deposit(BOB, bobAccount, "20.00").andExpect(status().isCreated());
    }

    @AfterEach
    void cleanUp() {
        idempotencyRecordRepository.deleteAllInBatch();
        ledgerEntryRepository.deleteAllInBatch();
        accountRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
    }

    // helpers

    private long createCustomer(RequestPostProcessor user) throws Exception {
        String email = UUID.randomUUID() + "@test.com";
        String body =
                mockMvc.perform(
                                post("/api/v1/customers")
                                        .with(user)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"email\":\""
                                                        + email
                                                        + "\",\"firstName\":\"A\",\"lastName\":\"B\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private long openAccount(RequestPostProcessor user, long customerId) throws Exception {
        String body =
                mockMvc.perform(
                                post("/api/v1/accounts")
                                        .with(user)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"customerId\":"
                                                        + customerId
                                                        + ",\"currency\":\"USD\"}"))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    private org.springframework.test.web.servlet.ResultActions deposit(
            RequestPostProcessor user, long accountId, String amount) throws Exception {
        return mockMvc.perform(
                post("/api/v1/accounts/{id}/deposits", accountId)
                        .with(user)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":\"" + amount + "\"}"));
    }

    private org.springframework.test.web.servlet.ResultActions transfer(
            RequestPostProcessor user, long from, long to, String amount) throws Exception {
        return mockMvc.perform(
                post("/api/v1/transfers")
                        .with(user)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                "{\"fromAccountId\":"
                                        + from
                                        + ",\"toAccountId\":"
                                        + to
                                        + ",\"amount\":\""
                                        + amount
                                        + "\"}"));
    }

    // the tests

    @Test
    void theOwnerIsTheCallerNotSomethingTheClientSends() {
        Customer stored = customerRepository.findById(aliceCustomer).orElseThrow();

        assertThat(stored.getOwnerSubject()).isEqualTo("alice-sub");
    }

    @Test
    void ownersCanReadTheirOwnCustomerAndAccount() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{id}", aliceCustomer).with(ALICE))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/accounts/{id}", aliceAccount).with(ALICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(50.00));
        mockMvc.perform(get("/api/v1/accounts/{id}/ledger", aliceAccount).with(ALICE))
                .andExpect(status().isOk());
    }

    @Test
    void someoneElsesResourcesLookLikeTheyDoNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{id}", aliceCustomer).with(BOB))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/accounts/{id}", aliceAccount).with(BOB))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/accounts/{id}/ledger", aliceAccount).with(BOB))
                .andExpect(status().isNotFound());
    }

    @Test
    void theAnswerForSomeoneElsesAccountIsTheSameAsForAnUnknownOne() throws Exception {
        long unknownId = 999_999_999L;

        String someoneElses =
                mockMvc.perform(get("/api/v1/accounts/{id}", aliceAccount).with(BOB))
                        .andExpect(status().isNotFound())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String unknown =
                mockMvc.perform(get("/api/v1/accounts/{id}", unknownId).with(BOB))
                        .andExpect(status().isNotFound())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        // same status and same shape: nothing tells bob that alice's id is real
        assertThat((String) JsonPath.read(someoneElses, "$.title"))
                .isEqualTo(JsonPath.read(unknown, "$.title"));
    }

    @Test
    void cannotDepositOrWithdrawOnSomeoneElsesAccount() throws Exception {
        deposit(BOB, aliceAccount, "10.00").andExpect(status().isNotFound());
        mockMvc.perform(
                        post("/api/v1/accounts/{id}/withdrawals", aliceAccount)
                                .with(BOB)
                                .header("Idempotency-Key", UUID.randomUUID().toString())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"amount\":\"10.00\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/accounts/{id}", aliceAccount).with(ALICE))
                .andExpect(jsonPath("$.balance").value(50.00));
    }

    @Test
    void cannotOpenAnAccountForSomeoneElsesCustomer() throws Exception {
        mockMvc.perform(
                        post("/api/v1/accounts")
                                .with(BOB)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"customerId\":"
                                                + aliceCustomer
                                                + ",\"currency\":\"USD\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotMoveMoneyOutOfSomeoneElsesAccount() throws Exception {
        transfer(BOB, aliceAccount, bobAccount, "10.00").andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/accounts/{id}", aliceAccount).with(ALICE))
                .andExpect(jsonPath("$.balance").value(50.00));
    }

    @Test
    void canSendMoneyToSomeoneElsesAccount() throws Exception {
        transfer(BOB, bobAccount, aliceAccount, "5.00").andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/accounts/{id}", aliceAccount).with(ALICE))
                .andExpect(jsonPath("$.balance").value(55.00));
        mockMvc.perform(get("/api/v1/accounts/{id}", bobAccount).with(BOB))
                .andExpect(jsonPath("$.balance").value(15.00));
    }

    @Test
    void adminsCanReachEveryCustomerAndAccount() throws Exception {
        mockMvc.perform(get("/api/v1/customers/{id}", aliceCustomer).with(ADMIN))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/accounts/{id}", bobAccount).with(ADMIN))
                .andExpect(status().isOk());
        deposit(ADMIN, aliceAccount, "1.00").andExpect(status().isCreated());
    }

    @Test
    void adminsStillGetNotFoundForUnknownIds() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/{id}", 999_999_999L).with(ADMIN))
                .andExpect(status().isNotFound());
    }

    @Test
    void customersWithoutAnOwnerAreAdminOnly() throws Exception {
        // a row from before security existed
        Customer legacy =
                customerRepository.save(
                        new Customer(UUID.randomUUID() + "@legacy.com", "Old", "Row"));

        mockMvc.perform(get("/api/v1/customers/{id}", legacy.getId()).with(ALICE))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/customers/{id}", legacy.getId()).with(ADMIN))
                .andExpect(status().isOk());
    }
}
