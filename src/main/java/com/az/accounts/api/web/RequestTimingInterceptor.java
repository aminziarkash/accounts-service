package com.az.accounts.api.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class RequestTimingInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RequestTimingInterceptor.class);
    private static final String START = RequestTimingInterceptor.class.getName() + ".start";

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(START, System.nanoTime());
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {
        Object start = request.getAttribute(START);
        long millis = start == null ? -1 : (System.nanoTime() - (long) start) / 1_000_000;
        String target =
                handler instanceof HandlerMethod method
                        ? method.getBeanType().getSimpleName() + "." + method.getMethod().getName()
                        : "n/a";
        log.info(
                "{} {} -> {} by {} in {} ms",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                target,
                millis);
    }
}
