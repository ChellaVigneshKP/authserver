package com.chellavignesh.authserver.recovery;

import com.chellavignesh.authserver.jose.KeyGenerator;
import com.nimbusds.jose.jwk.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class SigningIdentityTest {
    @TempDir Path directory;
    @Test void signingKeyAndKeyIdSurviveReloadAndWrongPasswordFailsClosed() throws Exception {
        Path store=directory.resolve("signing.p12");
        var process=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin","keytool").toString(),
                "-genkeypair","-alias","authserver-signing","-keyalg","RSA","-keysize","2048","-dname","CN=Test",
                "-validity","1","-storetype","PKCS12","-keystore",store.toString(),"-storepass:env","SIGNING_TEST_PASSWORD")
                .redirectErrorStream(true);
        process.environment().put("SIGNING_TEST_PASSWORD","test-password-only");
        var running=process.start();running.getInputStream().readAllBytes();assertThat(running.waitFor()).isZero();
        var config=new KeyGenerator();var loader=new DefaultResourceLoader();
        var selector=new JWKSelector(new JWKMatcher.Builder().keyType(KeyType.RSA).build());
        var first=config.jwkSource(loader,store.toUri().toString(),"test-password-only","authserver-signing").get(selector,null).getFirst();
        var second=config.jwkSource(loader,store.toUri().toString(),"test-password-only","authserver-signing").get(selector,null).getFirst();
        assertThat(first.getKeyID()).isEqualTo(second.getKeyID());
        assertThat(first.toPublicJWK()).isEqualTo(second.toPublicJWK());
        assertThat(first.isPrivate()).isTrue();
        assertThatThrownBy(()->config.jwkSource(loader,store.toUri().toString(),"wrong","authserver-signing")).isInstanceOf(Exception.class);
    }
}
