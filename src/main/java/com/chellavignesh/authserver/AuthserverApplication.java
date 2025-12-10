package com.chellavignesh.authserver;

import com.chellavignesh.libcrypto.CryptoLibBasePackageMarker;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackageClasses = {AuthserverApplication.class, CryptoLibBasePackageMarker.class})
@EnableCaching
@EnableScheduling
@ConfigurationPropertiesScan
public class AuthserverApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthserverApplication.class, args);
    }

}
