package com.chellavignesh.authserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named="AUTH_APP_IT", matches="true")
@org.springframework.test.context.ActiveProfiles("local")
@SpringBootTest
class AuthserverApplicationTests {

	@Test
	void contextLoads() {
	}

}
