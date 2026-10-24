package com.chellavignesh.authserver.adminportal.hosted;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.chellavignesh.authserver.session.entity.AuthSession;

import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.net.URI;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

class HostedAccountNonceStoreTest {
    LettuceConnectionFactory connection;
    StringRedisTemplate redis;
    HostedAccountNonceStore store;
    final String namespace = "authserver:hosted-test:" + UUID.randomUUID();
    final AtomicReference<Instant> now = new AtomicReference<>(Instant.now());
    final Clock clock =
            new Clock() {
                public ZoneId getZone() {
                    return ZoneOffset.UTC;
                }

                public Clock withZone(ZoneId zone) {
                    return this;
                }

                public Instant instant() {
                    return now.get();
                }
            };
    final URI target = URI.create("http://127.0.0.1:3100/account");
    HostedAccountActor actor;

    @BeforeEach
    void setup() {
        connection = new LettuceConnectionFactory("127.0.0.1", 16379);
        connection.afterPropertiesSet();
        connection.start();
        redis = new StringRedisTemplate(connection);
        redis.afterPropertiesSet();
        store = new HostedAccountNonceStore(redis, namespace, clock, new SecureRandom());
        var session = new AuthSession();
        session.setSessionId(UUID.randomUUID());
        session.setApplicationId(7);
        session.setSubjectId("owned");
        actor =
                new HostedAccountActor(
                        session,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "idp",
                        URI.create("http://127.0.0.1:3100/auth/callback"),
                        target,
                        null);
    }

    @AfterEach
    void cleanup() {
        var keys = redis.keys(namespace + ":hosted-account:*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
        store.close();
        connection.destroy();
    }

    @Test
    void expiresAtFiveMinutes() {
        String proof = store.issue("browser", actor, HostedAccountAction.PROFILE, target);
        now.set(now.get().plusSeconds(300));
        assertThatThrownBy(
                        () ->
                                store.consume(
                                        "browser",
                                        actor,
                                        HostedAccountAction.PROFILE,
                                        target,
                                        proof))
                .isInstanceOf(HostedAccountFailure.class);
    }

    @Test
    void wrongNonceDoesNotConsumeValidSlot() {
        String proof = store.issue("browser", actor, HostedAccountAction.PROFILE, target);
        assertThatThrownBy(
                        () ->
                                store.consume(
                                        "browser",
                                        actor,
                                        HostedAccountAction.PROFILE,
                                        target,
                                        "A".repeat(43)))
                .isInstanceOf(HostedAccountFailure.class);
        assertThatCode(
                        () ->
                                store.consume(
                                        "browser",
                                        actor,
                                        HostedAccountAction.PROFILE,
                                        target,
                                        proof))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsCrossSessionAndCrossAction() {
        String proof = store.issue("browser", actor, HostedAccountAction.PROFILE, target);
        assertThatThrownBy(
                        () ->
                                store.consume(
                                        "other", actor, HostedAccountAction.PROFILE, target, proof))
                .isInstanceOf(HostedAccountFailure.class);
        assertThatThrownBy(
                        () ->
                                store.consume(
                                        "browser",
                                        actor,
                                        HostedAccountAction.PASSWORD,
                                        target,
                                        proof))
                .isInstanceOf(HostedAccountFailure.class);
        assertThatCode(
                        () ->
                                store.consume(
                                        "browser",
                                        actor,
                                        HostedAccountAction.PROFILE,
                                        target,
                                        proof))
                .doesNotThrowAnyException();
    }

    @Test
    void exactlyOneConcurrentConsumeSucceeds() throws Exception {
        String proof = store.issue("browser", actor, HostedAccountAction.PROFILE, target);
        try (var tasks = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 8; i++)
                futures.add(
                        tasks.submit(
                                () -> {
                                    try {
                                        store.consume(
                                                "browser",
                                                actor,
                                                HostedAccountAction.PROFILE,
                                                target,
                                                proof);
                                        return true;
                                    } catch (HostedAccountFailure e) {
                                        return false;
                                    }
                                }));
            int accepted = 0;
            for (var f : futures) if (f.get()) accepted++;
            assertThat(accepted).isEqualTo(1);
        }
    }

    @Test
    void newGetReplacesSameActionOnly() {
        String old = store.issue("browser", actor, HostedAccountAction.PROFILE, target);
        String password = store.issue("browser", actor, HostedAccountAction.PASSWORD, target);
        String fresh = store.issue("browser", actor, HostedAccountAction.PROFILE, target);
        assertThatThrownBy(
                        () ->
                                store.consume(
                                        "browser", actor, HostedAccountAction.PROFILE, target, old))
                .isInstanceOf(HostedAccountFailure.class);
        assertThatCode(
                        () ->
                                store.consume(
                                        "browser",
                                        actor,
                                        HostedAccountAction.PROFILE,
                                        target,
                                        fresh))
                .doesNotThrowAnyException();
        assertThatCode(
                        () ->
                                store.consume(
                                        "browser",
                                        actor,
                                        HostedAccountAction.PASSWORD,
                                        target,
                                        password))
                .doesNotThrowAnyException();
    }

    @Test
    void bindingsCannotBeChangedOrReplayed() {
        String proof = store.issue("browser", actor, HostedAccountAction.PROFILE, target);
        var other =
                new HostedAccountActor(
                        actor.authSession(),
                        UUID.randomUUID(),
                        actor.profileId(),
                        actor.branding(),
                        actor.callback(),
                        target,
                        null);
        assertThatThrownBy(
                        () ->
                                store.consume(
                                        "browser",
                                        other,
                                        HostedAccountAction.PROFILE,
                                        target,
                                        proof))
                .isInstanceOf(HostedAccountFailure.class);
        assertThatThrownBy(
                        () ->
                                store.consume(
                                        "browser",
                                        actor,
                                        HostedAccountAction.PROFILE,
                                        URI.create("http://127.0.0.1:3100/account/security"),
                                        proof))
                .isInstanceOf(HostedAccountFailure.class);
        store.consume("browser", actor, HostedAccountAction.PROFILE, target, proof);
        assertThatThrownBy(
                        () ->
                                store.consume(
                                        "browser",
                                        actor,
                                        HostedAccountAction.PROFILE,
                                        target,
                                        proof))
                .isInstanceOf(HostedAccountFailure.class);
    }

    @Test
    void redisFailureIsBoundedAndFailsClosed() {
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        when(broken.opsForValue())
                .thenAnswer(
                        call -> {
                            Thread.sleep(10000);
                            throw new IllegalStateException("private upstream details");
                        });
        try (var blocked =
                new HostedAccountNonceStore(broken, namespace, clock, new SecureRandom())) {
            long start = System.nanoTime();
            assertThatThrownBy(
                            () ->
                                    blocked.issue(
                                            "browser", actor, HostedAccountAction.PROFILE, target))
                    .isInstanceOf(HostedAccountFailure.class)
                    .hasMessage(
                            "The account service is temporarily unavailable. Return to your account"
                                + " and try again.");
            assertThat(Duration.ofNanos(System.nanoTime() - start))
                    .isLessThan(Duration.ofSeconds(3));
        }
    }

    @Test
    void actualStalledRedisSocketIsBoundedAndFailsClosed() throws Exception {
        var clients = new java.util.concurrent.ConcurrentLinkedQueue<java.net.Socket>();
        try (var blackhole =
                new java.net.ServerSocket(0, 10, java.net.InetAddress.getByName("127.0.0.1"))) {
            var accepting =
                    Thread.ofPlatform()
                            .daemon()
                            .start(
                                    () -> {
                                        try {
                                            while (!blackhole.isClosed())
                                                clients.add(blackhole.accept());
                                        } catch (java.io.IOException ignored) {
                                        }
                                    });
            var config =
                    org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
                            .builder()
                            .commandTimeout(Duration.ofSeconds(10))
                            .shutdownTimeout(Duration.ofMillis(100))
                            .build();
            var factory =
                    new LettuceConnectionFactory(
                            new org.springframework.data.redis.connection
                                    .RedisStandaloneConfiguration(
                                    "127.0.0.1", blackhole.getLocalPort()),
                            config);
            factory.afterPropertiesSet();
            factory.start();
            try (var bounded =
                    new HostedAccountNonceStore(
                            new StringRedisTemplate(factory),
                            namespace,
                            clock,
                            new SecureRandom())) {
                long start = System.nanoTime();
                var failure =
                        catchThrowableOfType(
                                HostedAccountFailure.class,
                                () ->
                                        bounded.issue(
                                                "browser",
                                                actor,
                                                HostedAccountAction.PROFILE,
                                                target));
                assertThat(failure.status()).isEqualTo(503);
                assertThat(Duration.ofNanos(System.nanoTime() - start))
                        .isLessThan(Duration.ofSeconds(3));
                assertThat(clients.isEmpty()).isFalse();
            } finally {
                for (var socket : clients) socket.close();
                factory.destroy();
            }
        }
    }
}
