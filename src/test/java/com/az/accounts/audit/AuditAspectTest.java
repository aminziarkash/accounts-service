package com.az.accounts.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditAspectTest {

    public static class Target {

        @Audited("OK_ACTION")
        public String ok(Long id) {
            return "done " + id;
        }

        @Audited("FAILING_ACTION")
        public void failing() {
            throw new IllegalStateException("nope");
        }

        public String callsOkInternally() {
            return ok(7L); // this.ok(): the proxy is bypassed
        }
    }

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger auditLogger = (Logger) LoggerFactory.getLogger(AuditAspect.LOGGER_NAME);
    private Target proxy;

    @BeforeEach
    void setUp() {
        appender.start();
        auditLogger.addAppender(appender);

        AspectJProxyFactory factory = new AspectJProxyFactory(new Target());
        factory.addAspect(new AuditAspect());
        proxy = factory.getProxy();
    }

    @AfterEach
    void tearDown() {
        auditLogger.detachAppender(appender);
        SecurityContextHolder.clearContext();
    }

    @Test
    void logsTheLoggedInUser() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken("alice", "n/a", List.of()));

        proxy.ok(5L);

        assertThat(appender.list.get(0).getFormattedMessage()).contains("user=alice");
    }

    @Test
    void logsSystemWhenNobodyIsLoggedIn() {
        proxy.ok(5L);

        assertThat(appender.list.get(0).getFormattedMessage()).contains("user=system");
    }

    @Test
    void failuresNameTheUserToo() {
        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken("bob", "n/a", List.of()));

        assertThatThrownBy(() -> proxy.failing()).isInstanceOf(IllegalStateException.class);

        assertThat(appender.list.get(0).getFormattedMessage()).contains("user=bob");
    }

    @Test
    void logsSuccessAndReturnsTheResult() {
        assertThat(proxy.ok(5L)).isEqualTo("done 5");

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage())
                .contains("action=OK_ACTION")
                .contains("outcome=SUCCESS")
                .contains("[5]");
    }

    @Test
    void logsFailureAndRethrowsTheOriginalException() {
        assertThatThrownBy(() -> proxy.failing())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("nope");

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage())
                .contains("action=FAILING_ACTION")
                .contains("outcome=FAILURE")
                .contains("IllegalStateException");
    }

    @Test
    void selfInvocationBypassesTheAspect() {
        assertThat(proxy.callsOkInternally()).isEqualTo("done 7");

        assertThat(appender.list).isEmpty(); // ok() was called through "this", not the proxy
    }
}
