package com.az.accounts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.az.accounts.exception.InsufficientFundsException;
import com.az.accounts.exception.InvalidOperationException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AccountTest {

    private final Account account = new Account("USD");

    @Test
    public void newAccountStartsWithZeroBalance() {
        assertThat(account.getBalance()).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    public void depositIncreasesBalance() {
        BigDecimal amount = new BigDecimal(25.50);
        account.deposit(amount);
        assertThat(account.getBalance()).isEqualTo(amount);
    }

    @Test
    public void depositReturnsLedgerEntryWithBalanceAfter() {
        account.deposit(new BigDecimal("100.00"));
        LedgerEntry ledgerEntry = account.deposit(new BigDecimal("55.30"));

        assertThat(ledgerEntry.getType()).isEqualTo(LedgerEntryType.DEPOSIT);
        assertThat(ledgerEntry.getAmount()).isEqualTo(new BigDecimal("55.30"));
        assertThat(ledgerEntry.getBalanceAfter()).isEqualTo(new BigDecimal("155.30"));
        assertThat(ledgerEntry.getTransferReference()).isNull();
        assertThat(ledgerEntry.getAccount()).isSameAs(account);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "0.00", "-1", "-0.01", "1.005", "10.123"})
    void depositRejectsInvalidAmounts(String amount) {
        assertThatThrownBy(() -> account.deposit(new BigDecimal(amount)))
                .isInstanceOf(InvalidOperationException.class);

        assertThat(account.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void depositRejectsNullAmount() {
        assertThatThrownBy(() -> account.deposit(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("amount");

        assertThat(account.getBalance()).isEqualByComparingTo("0");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "1.5", "1.50", "1.500", "100"})
    void depositAcceptsValidAmounts(String amount) {
        account.deposit(new BigDecimal(amount));

        assertThat(account.getBalance()).isEqualByComparingTo(amount);
    }

    @Test
    void withdrawReducesBalance() {
        Account funded = accountWithBalance("100.00");

        funded.withdraw(new BigDecimal("30.25"));

        assertThat(funded.getBalance()).isEqualByComparingTo("69.75");
    }

    @Test
    void withdrawReturnsLedgerEntryWithBalanceAfter() {
        Account funded = accountWithBalance("100.00");

        LedgerEntry entry = funded.withdraw(new BigDecimal("30.00"));

        assertThat(entry.getType()).isEqualTo(LedgerEntryType.WITHDRAWAL);
        assertThat(entry.getAmount()).isEqualByComparingTo("30.00");
        assertThat(entry.getBalanceAfter()).isEqualByComparingTo("70.00");
        assertThat(entry.getTransferReference()).isNull();
    }

    @Test
    void withdrawingTheEntireBalanceLeavesZero() {
        Account funded = accountWithBalance("50.00");

        funded.withdraw(new BigDecimal("50.00"));

        assertThat(funded.getBalance()).isEqualByComparingTo("0");
    }

    @Test
    void withdrawMoreThanBalanceFailsAndLeavesBalanceUnchanged() {
        Account funded = accountWithBalance("50.00");

        assertThatThrownBy(() -> funded.withdraw(new BigDecimal("50.01")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("50.00")
                .hasMessageContaining("50.01");

        assertThat(funded.getBalance()).isEqualByComparingTo("50.00");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "-0.01", "1.005"})
    void withdrawRejectsInvalidAmounts(String amount) {
        Account funded = accountWithBalance("50.00");

        assertThatThrownBy(() -> funded.withdraw(new BigDecimal(amount)))
                .isInstanceOf(InvalidOperationException.class);

        assertThat(funded.getBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    void withdrawRejectsNullAmount() {
        Account funded = accountWithBalance("50.00");

        assertThatThrownBy(() -> funded.withdraw(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void accountKeepsTheIsoCurrencyCode() {
        assertThat(new Account("USD").getCurrency()).isEqualTo("USD");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ZZZ", "EU", "eur", ""})
    void accountRejectsUnknownCurrencyCodes(String code) {
        assertThatThrownBy(() -> new Account(code))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("Unknown currency code");
    }

    @Test
    void accountRejectsNullCurrency() {
        assertThatThrownBy(() -> new Account(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("currency");
    }

    private Account accountWithBalance(String balance) {
        Account funded = new Account("EUR");
        funded.deposit(new BigDecimal(balance));
        return funded;
    }
}
