package com.az.accounts.api.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestTimingInterceptorTest {

    private final RequestTimingInterceptor interceptor = new RequestTimingInterceptor();
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(RequestTimingInterceptor.class);

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
    }

    @Test
    void logsMethodPathStatusAndDuration() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts/1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
        interceptor.afterCompletion(request, response, new Object(), null);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage())
                .contains("GET /api/v1/accounts/1 -> 200")
                .contains(" ms");
    }

    @Test
    void doesNotBreakWhenPreHandleNeverRan() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/x");

        interceptor.afterCompletion(request, new MockHttpServletResponse(), new Object(), null);

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage()).contains("-1 ms");
    }
}
