package com.az.accounts.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void generatesAnIdWhenNoneIsSent() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInside = new AtomicReference<>();

        filter.doFilter(
                new MockHttpServletRequest(),
                response,
                (req, res) -> seenInside.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(seenInside.get()).isNotBlank();
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo(seenInside.get());
    }

    @Test
    void reusesTheIdSentByTheCaller() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seenInside = new AtomicReference<>();

        filter.doFilter(
                request,
                response,
                (req, res) -> seenInside.set(MDC.get(CorrelationIdFilter.MDC_KEY)));

        assertThat(seenInside.get()).isEqualTo("abc-123");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("abc-123");
    }

    @Test
    void replacesAnUnsafeIdInsteadOfLoggingIt() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.HEADER, "bad id\nFAKE LOG LINE");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(CorrelationIdFilter.HEADER))
                .doesNotContain("FAKE")
                .matches("[A-Za-z0-9-]{1,64}");
    }

    @Test
    void clearsTheMdcAfterwardsEvenWhenTheRequestFails() {
        assertThatThrownBy(() ->
                        filter.doFilter(
                                new MockHttpServletRequest(),
                                new MockHttpServletResponse(),
                                (req, res) -> {
                                    throw new ServletException("boom");
                                }))
                .isInstanceOf(ServletException.class);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
