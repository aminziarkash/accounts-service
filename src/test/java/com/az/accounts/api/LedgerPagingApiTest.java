package com.az.accounts.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.az.accounts.api.mapper.AccountMapperImpl;
import com.az.accounts.service.AccountService;
import com.az.accounts.service.CustomerService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@Import(AccountMapperImpl.class)
class LedgerPagingApiTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private CustomerService customerService;
    @MockitoBean private AccountService accountService;

    private void stubEmptyPage() {
        when(accountService.getLedger(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
    }

    @Test
    void defaultsToTwentyEntriesNewestFirstWithIdAsTieBreaker() throws Exception {
        stubEmptyPage();

        mockMvc.perform(get("/api/v1/accounts/1/ledger"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(20));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(accountService).getLedger(eq(1L), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(20);
        assertThat(captor.getValue().getSort())
                .isEqualTo(
                        Sort.by(Sort.Direction.DESC, "createdAt")
                                .and(Sort.by(Sort.Direction.DESC, "id")));
    }

    @Test
    void acceptsTheMaximumPageSize() throws Exception {
        when(accountService.getLedger(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        mockMvc.perform(get("/api/v1/accounts/1/ledger?size=100")).andExpect(status().isOk());
    }

    @Test
    void rejectsPageSizeAboveTheCap() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/1/ledger?size=101"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsZeroSizeAndNegativePage() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/1/ledger?size=0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/accounts/1/ledger?page=-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsUnknownSortField() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/1/ledger?sort=balanceAfter"))
                .andExpect(status().isBadRequest())
                .andExpect(
                        jsonPath("$.detail")
                                .value(org.hamcrest.Matchers.containsString("Cannot sort by")));
    }

    @Test
    void rejectsUnknownDirection() throws Exception {
        mockMvc.perform(get("/api/v1/accounts/1/ledger?direction=sideways"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sortsByAmountAscendingWhenAsked() throws Exception {
        stubEmptyPage();

        mockMvc.perform(get("/api/v1/accounts/1/ledger?sort=amount&direction=asc"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(accountService).getLedger(eq(1L), captor.capture());
        assertThat(captor.getValue().getSort())
                .isEqualTo(
                        Sort.by(Sort.Direction.ASC, "amount")
                                .and(Sort.by(Sort.Direction.ASC, "id")));
    }
}
