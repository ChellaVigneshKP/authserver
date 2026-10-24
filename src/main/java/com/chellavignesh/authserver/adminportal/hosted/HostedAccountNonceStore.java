package com.chellavignesh.authserver.adminportal.hosted;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** One opaque slot per browser/action; Redis stores hashes/bindings/expiry, never form data. */
@Service
public class HostedAccountNonceStore implements AutoCloseable {
    private static final DefaultRedisScript<Long> CONSUME =
            new DefaultRedisScript<>(
                    """
                    local value=redis.call('GET',KEYS[1])
                    if not value then return 0 end
                    local split=string.find(value,':')
                    if not split or string.sub(value,1,split-1)~=ARGV[1] then return 0 end
                    if tonumber(string.sub(value,split+1))<=tonumber(ARGV[2]) then
                      redis.call('DEL',KEYS[1]); return 0
                    end
                    redis.call('DEL',KEYS[1]); return 1
                    """,
                    Long.class);
    private final StringRedisTemplate redis;
    private final String namespace;
    private final Clock clock;
    private final SecureRandom random;
    // Dedicated bounded workers also bound connection acquisition; never alter global Redis
    // settings.
    private final ExecutorService operations =
            new ThreadPoolExecutor(
                    0,
                    4,
                    30,
                    TimeUnit.SECONDS,
                    new SynchronousQueue<>(),
                    r -> {
                        Thread t = new Thread(r, "hosted-account-redis");
                        t.setDaemon(true);
                        return t;
                    },
                    new ThreadPoolExecutor.AbortPolicy());

    @Autowired
    public HostedAccountNonceStore(
            StringRedisTemplate redis,
            @Value("${spring.session.redis.namespace:authserver:v2:session}") String namespace,
            Clock clock) {
        this(redis, namespace, clock, new SecureRandom());
    }

    public HostedAccountNonceStore(
            StringRedisTemplate redis, String namespace, Clock clock, SecureRandom random) {
        this.redis = redis;
        this.namespace = namespace;
        this.clock = clock;
        this.random = random;
    }

    public String issue(
            String browser, HostedAccountActor actor, HostedAccountAction action, URI returnTo) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String value =
                binding(browser, actor, action, returnTo, nonce)
                        + ":"
                        + clock.instant().plusSeconds(300).toEpochMilli();
        bounded(
                () -> {
                    redis.opsForValue().set(key(browser, action), value, Duration.ofMinutes(5));
                    return null;
                });
        return nonce;
    }

    public void consume(
            String browser,
            HostedAccountActor actor,
            HostedAccountAction action,
            URI returnTo,
            String nonce) {
        if (nonce == null || !nonce.matches("[A-Za-z0-9_-]{43}"))
            throw HostedAccountFailure.invalid();
        Long result =
                bounded(
                        () ->
                                redis.execute(
                                        CONSUME,
                                        List.of(key(browser, action)),
                                        binding(browser, actor, action, returnTo, nonce),
                                        Long.toString(clock.millis())));
        if (!Long.valueOf(1).equals(result)) throw HostedAccountFailure.invalid();
    }

    private String key(String browser, HostedAccountAction action) {
        if (browser == null || browser.isBlank() || action == null)
            throw HostedAccountFailure.invalid();
        return namespace + ":hosted-account:" + hash(browser + "|" + action.wire());
    }

    private String binding(
            String browser,
            HostedAccountActor actor,
            HostedAccountAction action,
            URI returnTo,
            String nonce) {
        return hash(
                String.join(
                        "|",
                        browser,
                        actor.authSession().getSessionId().toString(),
                        actor.loginId().toString(),
                        actor.profileId().toString(),
                        actor.branding(),
                        actor.authSession().getApplicationId().toString(),
                        actor.callback().toString(),
                        action.wire(),
                        returnTo.toString(),
                        nonce));
    }

    private static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private <T> T bounded(Callable<T> work) {
        Future<T> pending = null;
        try {
            pending = operations.submit(work);
            return pending.get(2, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw HostedAccountFailure.unavailable();
        } catch (ExecutionException | TimeoutException | RejectedExecutionException unavailable) {
            throw HostedAccountFailure.unavailable();
        } finally {
            if (pending != null && !pending.isDone()) pending.cancel(true);
        }
    }

    @Override
    @PreDestroy
    public void close() {
        operations.shutdownNow();
    }
}
