package com.az.accounts.audit;

import java.util.Arrays;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuditAspect {

    static final String LOGGER_NAME = "AUDIT";
    private static final Logger log = LoggerFactory.getLogger(LOGGER_NAME);

    @Around("@annotation(audited)")
    public Object audit(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        String action = audited.value();
        try {
            Object result = joinPoint.proceed();
            log.info(
                    "action={} outcome=SUCCESS user=unknown args={}",
                    action,
                    Arrays.toString(joinPoint.getArgs()));
            return result;
        } catch (Throwable failure) {
            log.warn(
                    "action={} outcome=FAILURE user=unknown reason={} args={}",
                    action,
                    failure.getClass().getSimpleName(),
                    Arrays.toString(joinPoint.getArgs()));
            throw failure; // the aspect observes, it never swallows the error
        }
    }
}
