package com.az.accounts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.az.accounts.client.LimitedOperation;
import com.az.accounts.client.LimitsCheckRequest;
import com.az.accounts.client.LimitsCheckResponse;
import com.az.accounts.client.ResilientLimitsClient;
import com.az.accounts.config.LimitsClientProperties;
import com.az.accounts.domain.Account;
import com.az.accounts.exception.AccountNotFoundException;
import com.az.accounts.exception.LimitExceededException;
import com.az.accounts.exception.LimitsUnavailableException;
import com.az.accounts.repository.AccountRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LimitsGuardTest {

    private static final BigDecimal AMOUNT = new BigDecimal("100.00");

    @Mock private ResilientLimitsClient client;
    @Mock private AccountRepository accounts;

    private LimitsGuard guard;

    @BeforeEach
    void setUp() {
        guard =
                new LimitsGuard(
                        client, accounts, new LimitsClientProperties(true, null, null, null));
    }

    private void accountExistsInEur() {
        Account account = mock(Account.class);
        when(account.getCurrency()).thenReturn("EUR");
        when(accounts.findById(1L)).thenReturn(Optional.of(account));
    }

    @Test
    void allowedMeansNothingHappens() {
        accountExistsInEur();
        when(client.check(any())).thenReturn(new LimitsCheckResponse(true, null));

        guard.requireAllowed(LimitedOperation.DEPOSIT, 1L, AMOUNT);
    }

    @Test
    void asksWithTheAccountsCurrencyAndTheOperation() {
        accountExistsInEur();
        when(client.check(any())).thenReturn(new LimitsCheckResponse(true, null));

        guard.requireAllowed(LimitedOperation.TRANSFER, 1L, AMOUNT);

        ArgumentCaptor<LimitsCheckRequest> sent = ArgumentCaptor.forClass(LimitsCheckRequest.class);
        verify(client).check(sent.capture());
        assertThat(sent.getValue())
                .isEqualTo(new LimitsCheckRequest(1L, "EUR", AMOUNT, LimitedOperation.TRANSFER));
    }

    @Test
    void deniedBecomesALimitExceededWithTheReason() {
        accountExistsInEur();
        when(client.check(any()))
                .thenReturn(new LimitsCheckResponse(false, "WITHDRAW above the limit of 2000.00"));

        assertThatThrownBy(() -> guard.requireAllowed(LimitedOperation.WITHDRAW, 1L, AMOUNT))
                .isInstanceOf(LimitExceededException.class)
                .hasMessage("WITHDRAW above the limit of 2000.00");
    }

    @Test
    void noAnswerFromTheClientStaysUnavailable() {
        accountExistsInEur();
        when(client.check(any())).thenThrow(new LimitsUnavailableException("down"));

        assertThatThrownBy(() -> guard.requireAllowed(LimitedOperation.DEPOSIT, 1L, AMOUNT))
                .isInstanceOf(LimitsUnavailableException.class);
    }

    @Test
    void unknownAccountIsA404BeforeAnyRemoteCall() {
        when(accounts.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireAllowed(LimitedOperation.DEPOSIT, 1L, AMOUNT))
                .isInstanceOf(AccountNotFoundException.class);
        verifyNoInteractions(client);
    }

    @Test
    void disabledSkipsEverything() {
        LimitsGuard off =
                new LimitsGuard(
                        client, accounts, new LimitsClientProperties(false, null, null, null));

        off.requireAllowed(LimitedOperation.DEPOSIT, 1L, AMOUNT);

        verifyNoInteractions(client, accounts);
    }
}
