package com.chellavignesh.authserver.recovery;

import com.chellavignesh.authserver.session.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import javax.crypto.KeyGenerator;
import java.nio.file.*;
import java.security.KeyStore;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class PasswordRoundTripTest {
    @TempDir Path directory;
    @Test void actualDelegatingEncoderAndFactoryPreserveStoredHash() throws Exception {
        String alias = UUID.randomUUID().toString();
        char[] password = "test-store-password".toCharArray();
        var store = KeyStore.getInstance("JCEKS");
        store.load(null, password);
        var generator = KeyGenerator.getInstance("AES"); generator.init(256);
        store.setEntry(alias, new KeyStore.SecretKeyEntry(generator.generateKey()), new KeyStore.PasswordProtection(password));
        Path file = directory.resolve("password.jceks");
        try (var out = Files.newOutputStream(file)) { store.store(out, password); }
        var config = new HasherConfig("20000", alias, "JCEKS", file.toUri().toString(), new String(password));
        var encoder = new DelegatingPasswordEncoder("1", Map.of("1", new KPCVPasswordEncoder(config)));
        try (var context = new StaticApplicationContext()) {
            context.getBeanFactory().registerSingleton("passwordEncoder", encoder);
            var factory = new PasswordEncoderFactory();
            ReflectionTestUtils.setField(factory, "context", context);
            String raw = Base64.getEncoder().encodeToString("Password@12345".getBytes(StandardCharsets.UTF_8));
            String stored = factory.encode(raw);
            assertThat(stored).matches("[0-9a-fA-F]+");
            assertThat(factory.matches(raw, stored, 1)).isTrue();
            assertThat(factory.matches(Base64.getEncoder().encodeToString("wrong".getBytes(StandardCharsets.UTF_8)), stored, 1)).isFalse();
        }
    }
}
