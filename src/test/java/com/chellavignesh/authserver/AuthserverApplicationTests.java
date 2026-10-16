package com.chellavignesh.authserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="AUTH_APP_IT", matches="true")
@org.springframework.test.context.ActiveProfiles("local")
@SpringBootTest
class AuthserverApplicationTests {

    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.data.redis.core.StringRedisTemplate redis;

    @Test void recoveryProofIsBoundToBrowserSqlSessionAndFlow() {
        var service = new com.chellavignesh.authserver.session.RecoveryVerificationService(
                redis, "authserver:recovery-test:" + java.util.UUID.randomUUID());
        org.assertj.core.api.Assertions.assertThat(service.consume("browser", "sql-session", "forgot-password")).isFalse();
        service.verified("browser", "sql-session", "forgot-password");
        org.assertj.core.api.Assertions.assertThat(service.isVerified("other-browser", "sql-session", "forgot-password")).isFalse();
        org.assertj.core.api.Assertions.assertThat(service.isVerified("browser", "other-session", "forgot-password")).isFalse();
        org.assertj.core.api.Assertions.assertThat(service.isVerified("browser", "sql-session", "activate-account")).isFalse();
        org.assertj.core.api.Assertions.assertThat(service.consume("browser", "sql-session", "forgot-password")).isTrue();
        org.assertj.core.api.Assertions.assertThat(service.consume("browser", "sql-session", "forgot-password")).isFalse();
    }

    @Test void concurrentRecoveryRequestsCanConsumeOnlyOneProof() throws Exception {
        var service = new com.chellavignesh.authserver.session.RecoveryVerificationService(
                redis, "authserver:recovery-test:" + java.util.UUID.randomUUID());
        service.verified("browser", "sql-session", "forgot-password");
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var start = new java.util.concurrent.CountDownLatch(1);
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 12; i++) {
                futures.add(executor.submit(() -> {start.await(); return service.consume("browser", "sql-session", "forgot-password");}));
            }
            start.countDown();
            int successes = 0;
            for (var result : futures) if (result.get()) successes++;
            org.assertj.core.api.Assertions.assertThat(successes).isEqualTo(1);
        }
    }

	@Test
	void contextLoads() {
	}

}
