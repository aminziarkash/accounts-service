package com.az.accounts.domain;

import com.az.accounts.exception.InsufficientFundsException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    protected Account() {}

    public Account(String currency) {
        Objects.requireNonNull(currency, "currency");
        try {
            this.currency = Currency.getInstance(currency).getCurrencyCode();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown currency code: " + currency, e);
        }
        this.balance = BigDecimal.ZERO;
    }

    private static void requirePositiveAmount(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than 0: " + amount);
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException(
                    "Amount cannot have more than 2 decimal places: " + amount);
        }
    }

    public LedgerEntry deposit(BigDecimal amount) {
        requirePositiveAmount(amount);
        BigDecimal newBalance = balance.add(amount);
        LedgerEntry entry =
                new LedgerEntry(this, LedgerEntryType.DEPOSIT, amount, newBalance, null);
        balance = newBalance;
        return entry;
    }

    public LedgerEntry withdraw(BigDecimal amount) {
        requirePositiveAmount(amount);
        if (amount.compareTo(balance) > 0) {
            throw new InsufficientFundsException(id, balance, amount);
        }
        BigDecimal newBalance = balance.subtract(amount);
        LedgerEntry entry =
                new LedgerEntry(this, LedgerEntryType.WITHDRAWAL, amount, newBalance, null);
        balance = newBalance;
        return entry;
    }

    public Long getId() {
        return id;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Customer getCustomer() {
        return customer;
    }

    void setCustomer(Customer customer) {
        this.customer = customer;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Account other)) {
            return false;
        }
        return id != null && id.equals(other.getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
