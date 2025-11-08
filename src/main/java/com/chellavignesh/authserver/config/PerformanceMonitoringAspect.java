package com.chellavignesh.authserver.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Aspect
@Component
@ConditionalOnProperty(name = "performance.monitoring.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class PerformanceMonitoringAspect {
    private static final long SLOW_METHOD_THRESHOLD_MS = 500;
    private static final long VERY_SLOW_METHOD_THRESHOLD_MS = 2000;

    @Around("execution(* com.chellavignesh.authserver..service..*(..)) || execution(* com.chellavignesh.authserver..repository..*(..)) || execution(* com.chellavignesh.authserver.session.LibCryptoPasswordEncoder.matches(..))")
    public Object monitorPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        try {
            return joinPoint.proceed();
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            if (duration >= VERY_SLOW_METHOD_THRESHOLD_MS) {
                log.error("Very slow method execution: {}.{}() took {} ms", className, methodName, duration);
            } else if (duration >= SLOW_METHOD_THRESHOLD_MS) {
                log.warn("Slow method execution: {}.{}() took {} ms", className, methodName, duration);
            } else {
                log.debug("Method execution: {}.{}() took {} ms", className, methodName, duration);
            }
        }
    }
}
