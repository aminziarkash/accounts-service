package com.az.accounts.api.web;

import com.az.accounts.service.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Counts every write request against the caller's token bucket. Runs after the security filters, so
 * the caller is already known. When the bucket is empty, the RateLimitExceededException it throws
 * is turned into a 429 by the GlobalExceptionHandler.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    // reads are never limited
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    private final RateLimiter rateLimiter;

    public RateLimitInterceptor(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (SAFE_METHODS.contains(request.getMethod())) {
            return true;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return true; // no token: that is the security layer's job (401), not ours
        }
        rateLimiter.check(jwt.getSubject()); // throws when the caller is out of tokens
        return true;
    }
}
