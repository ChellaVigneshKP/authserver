package com.chellavignesh.authserver.recovery;

import com.chellavignesh.authserver.adminportal.application.*;
import com.chellavignesh.authserver.adminportal.application.dto.*;
import com.chellavignesh.authserver.adminportal.forgotusername.ForgotUsernameSetting;
import com.chellavignesh.authserver.adminportal.credential.*;
import com.chellavignesh.authserver.adminportal.credential.dto.CreateCredentialRequestDto;
import com.chellavignesh.authserver.enums.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;

/** Uses real SQL, Redis, encrypted client credentials and HTTP; opt-in development infrastructure only. */
@EnabledIfEnvironmentVariable(named="AUTH_APP_IT", matches="true")
@ActiveProfiles("local")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties={"server.port=9081","server.base-path=http://localhost:9081/services"})
class ConfidentialOAuthIntegrationTest {
    @Autowired ApplicationRepository applications;
    @Autowired RedirectUriRepository redirects;
    @Autowired CredentialService credentials;
    @Autowired ApplicationService clients;
    @Autowired ObjectMapper mapper;
    @LocalServerPort int port;
    @Value("${app.local.admin.username}") String username;
    @Value("${app.local.admin.password}") String password;
    @Value("${server.base-path}") String issuer;
    private final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private String base() {return "http://localhost:"+port+"/services";}
    private static String form(Map<String,String> fields) {
        return fields.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
    }
    private HttpResponse<String> request(String uri, Map<String,String> fields) throws Exception {
        var builder=HttpRequest.newBuilder(URI.create(uri)).timeout(Duration.ofSeconds(20))
                .header("User-Agent","AuthserverIntegration/1.0").header("Accept-Language","en-US")
                .header("x-request-datetime",OffsetDateTime.now(ZoneOffset.UTC).toString());
        if(fields!=null) builder.header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form(fields)));
        return http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
    }
    private String assertion(String clientId,String secret) throws Exception {
        Instant now=Instant.now();
        var jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.HS256),new JWTClaimsSet.Builder()
                .issuer(clientId).subject(clientId).audience(issuer+"/oauth2/token")
                .issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(60))).jwtID(UUID.randomUUID().toString()).build());
        jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
    private Map<String,String> authenticated(String clientId,String secret,Map<String,String> fields) throws Exception {
        var result=new HashMap<>(fields);result.put("client_id",clientId);
        result.put("client_assertion_type","urn:ietf:params:oauth:client-assertion-type:jwt-bearer");
        result.put("client_assertion",assertion(clientId,secret));return result;
    }
    @Test void confidentialCodeExchangeRefreshRotationAndReplay() throws Exception {
        var discoveryResponse=request(base()+"/.well-known/openid-configuration",null);
        assertThat(discoveryResponse.statusCode()).isEqualTo(200);
        var discovery=mapper.readTree(discoveryResponse.body());
        assertThat(discovery.path("issuer").asText()).isEqualTo(base());
        assertThat(discovery.path("token_endpoint").asText()).isEqualTo(base()+"/oauth2/token");
        var publicKeys=request(discovery.path("jwks_uri").asText(),null);
        assertThat(publicKeys.statusCode()).isEqualTo(200);
        var jwks=com.nimbusds.jose.jwk.JWKSet.parse(publicKeys.body());
        var appDto=new CreateApplicationDto();appDto.setName("Contract confidential "+UUID.randomUUID());
        appDto.setDescription("Disposable HTTP integration fixture");appDto.setType(ApplicationTypeEnum.WEB);
        appDto.setAuthMethod(AuthFlowEnum.CLIENT_SECRET_JWT);appDto.setUsernameType(UsernameTypeEnum.USERNAME);appDto.setAllowForgotUsername(false);
        var settings=new UpdateTokenSettingsDto();settings.setAuthCodeTimeToLive(120);settings.setAccessTokenTimeToLive(600);
        settings.setDeviceCodeTimeToLive(120);settings.setRefreshTokenTimeToLive(3600);settings.setReuseRefreshTokens(false);settings.setMaxRequestTransitTime(30);
        var app=applications.create(1,appDto,settings,new ForgotUsernameSetting(List.of()),AccessTokenFormatEnum.REFERENCE);
        try {
            String callback=base()+"/local/callback";
            redirects.createRedirectUris(1,app.getId(),List.of(callback));
            assertThat(clients.getRegisteredClientByClientId(app.getClientId())).isNull();
            var dto=new CreateCredentialRequestDto();dto.setOrgId(1);dto.setAppId(app.getId());dto.setName("HTTP fixture secret");
            dto.setDescription("HTTP fixture");dto.setAlgorithmEnum(AlgorithmEnum.HS256);dto.setAuthFlow(AuthFlowEnum.CLIENT_SECRET_JWT);
            dto.setCredentialStatus(CredentialStatus.Active);dto.setExpireOn(Date.from(Instant.now().plusSeconds(86400)));
            String secret=credentials.createSharedSecretCredential(dto).getSecretValue();
            assertThat(clients.getRegisteredClientByClientId(app.getClientId()).getClientSecret()).isEqualTo(secret);
            var authorize=request(base()+"/oauth2/authorize?"+form(Map.of("response_type","code","client_id",app.getClientId(),
                    "redirect_uri",callback,"scope","openid profile","branding","idp","state","contract-state",
                    "x-request-datetime",OffsetDateTime.now(ZoneOffset.UTC).toString())),null);
            assertThat(authorize.statusCode()).isEqualTo(302);
            var page=request(authorize.headers().firstValue("Location").orElseThrow(),null);
            assertThat(page.statusCode()).isEqualTo(200);
            var csrf=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.body());assertThat(csrf.find()).isTrue();
            var login=request(base()+"/login",Map.of("username",username,"password",Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)),"_csrf",csrf.group(1)));
            assertThat(login.statusCode()).isEqualTo(302);
            var continuation=request(login.headers().firstValue("Location").orElseThrow(),null);
            assertThat(continuation.statusCode()).isEqualTo(302);
            String location=continuation.headers().firstValue("Location").orElseThrow();
            var codeMatcher=Pattern.compile("[?&]code=([^&]+)").matcher(location);assertThat(codeMatcher.find()).isTrue();
            String code=URLDecoder.decode(codeMatcher.group(1),StandardCharsets.UTF_8);
            var exchange=request(base()+"/oauth2/token",authenticated(app.getClientId(),secret,Map.of("grant_type","authorization_code","code",code,"redirect_uri",callback)));
            assertThat(exchange.statusCode()).as("Code exchange status").isEqualTo(200);
            var tokens=mapper.readTree(exchange.body());assertThat(tokens.hasNonNull("refresh_token")).isTrue();
            var idToken=SignedJWT.parse(tokens.path("id_token").asText());
            var signingKey=(com.nimbusds.jose.jwk.RSAKey)jwks.getKeyByKeyId(idToken.getHeader().getKeyID());
            assertThat(signingKey).isNotNull();
            assertThat(idToken.verify(new com.nimbusds.jose.crypto.RSASSAVerifier(signingKey))).isTrue();
            String refresh=tokens.path("refresh_token").asText();
            var renewed=request(base()+"/oauth2/token",authenticated(app.getClientId(),secret,Map.of("grant_type","refresh_token","refresh_token",refresh)));
            assertThat(renewed.statusCode()).as("Refresh status").isEqualTo(200);
            assertThat(mapper.readTree(renewed.body()).path("refresh_token").asText()).isNotEqualTo(refresh);
            var replay=request(base()+"/oauth2/token",authenticated(app.getClientId(),secret,Map.of("grant_type","refresh_token","refresh_token",refresh)));
            assertThat(replay.statusCode()).isEqualTo(400);
            var codeReplay=request(base()+"/oauth2/token",authenticated(app.getClientId(),secret,Map.of("grant_type","authorization_code","code",code,"redirect_uri",callback)));
            assertThat(codeReplay.statusCode()).isEqualTo(400);
        } finally {
            applications.inactivateApplication(1,app.getId(),false);
        }
    }
}
