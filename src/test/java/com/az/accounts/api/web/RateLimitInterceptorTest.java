package com.az.accounts.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.az.accounts.exception.RateLimitExceededException;
import com.az.accounts.service.RateLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class RateLimitInterceptorTest {

    private final RateLimiter limiter = mock(RateLimiter.class);
    private final RateLimitInterceptor interceptor = new RateLimitInterceptor(limiter);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext(); // the context is per thread: never leave it behind
    }

    private void loginAs(String subject) {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    private boolean call(String method) {
        return interceptor.preHandle(
                new MockHttpServletRequest(method, "/api/v1/transfers"),
                new MockHttpServletResponse(),
                new Object());
    }

    @Test
    void checksTheCallerOnAWrite() {
        loginAs("alice-id");

        assertThat(call("POST")).isTrue();

        verify(limiter).check("alice-id");
    }

    @Test
    void doesNotLimitReads() {
        loginAs("alice-id");

        assertThat(call("GET")).isTrue();

        verifyNoInteractions(limiter);
    }

    @Test
    void leavesRequestsWithoutAJwtToSecurity() {
        assertThat(call("POST")).isTrue();

        verifyNoInteractions(limiter);
    }

    @Test
    void lets429ThroughToTheErrorHandler() {
        loginAs("alice-id");
        doThrow(new RateLimitExceededException(3)).when(limiter).check("alice-id");

        assertThatThrownBy(() -> call("POST")).isInstanceOf(RateLimitExceededException.class);
    }
}
