package com.az.accounts.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.az.accounts.TestcontainersConfiguration;
import com.az.accounts.domain.Account;
import com.az.accounts.domain.Customer;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@DataJpaTest(
        properties =
                "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                        + "com.az.accounts.repository.SqlCapture")
@Import(TestcontainersConfiguration.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CustomerRepositoryTest {

    @Autowired private EntityManager entityManager;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private AccountRepository accountRepository;

    @BeforeEach
    void setUp() {
        // 5 customers with 2 accounts each
        for (int i = 1; i <= 5; i++) {
            Customer customer =
                    customerRepository.save(
                            new Customer("page" + i + "@test.com", "Page", "N" + i));
            for (int j = 0; j < 2; j++) {
                Account account = new Account("USD");
                customer.addAccount(account);
                accountRepository.save(account);
            }
        }
        entityManager.flush();
        entityManager.clear();
        SqlCapture.SQL.clear();
    }

    @Test
    void hibernate7PagesTheCustomersInASubqueryEvenWithACollectionFetchJoin() {
        Page<Customer> page = customerRepository.findPageWithAccounts(PageRequest.of(0, 2));

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent()).allSatisfy(c -> assertThat(c.getAccounts()).hasSize(2));

        String joinQuery =
                SqlCapture.SQL.stream()
                        .filter(sql -> sql.contains("join"))
                        .findFirst()
                        .orElseThrow();
        // the page size is applied in the database, on the customers, not in memory
        assertThat(joinQuery).contains("fetch first");
    }

    @Test
    void pagingTheIdsFirstUsesARealLimit() {
        Page<Long> ids = customerRepository.findCustomerIds(PageRequest.of(0, 2));
        List<Customer> customers = customerRepository.findAllWithAccountsByIdIn(ids.getContent());

        assertThat(ids.getTotalElements()).isEqualTo(5);
        assertThat(customers).hasSize(2);
        assertThat(customers).allSatisfy(c -> assertThat(c.getAccounts()).hasSize(2));

        String idQuery =
                SqlCapture.SQL.stream()
                        .filter(sql -> sql.contains("fetch first") || sql.contains("limit"))
                        .findFirst()
                        .orElseThrow();
        // the id query is paged by the database, so only 2 ids come back
        assertThat(idQuery).doesNotContain("join");
    }
}
