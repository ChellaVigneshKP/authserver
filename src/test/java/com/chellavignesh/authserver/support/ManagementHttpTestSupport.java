package com.chellavignesh.authserver.support;

import com.chellavignesh.authserver.adminportal.application.ApplicationRepository;
import com.chellavignesh.authserver.adminportal.application.RedirectUriRepository;
import com.chellavignesh.authserver.adminportal.application.dto.CreateApplicationDto;
import com.chellavignesh.authserver.adminportal.application.dto.UpdateTokenSettingsDto;
import com.chellavignesh.authserver.adminportal.application.entity.Application;
import com.chellavignesh.authserver.adminportal.forgotusername.ForgotUsernameSetting;
import com.chellavignesh.authserver.adminportal.organization.OrganizationService;
import com.chellavignesh.authserver.adminportal.organization.dto.CreateOrganizationDto;
import com.chellavignesh.authserver.adminportal.organization.entity.Organization;
import com.chellavignesh.authserver.adminportal.user.UserRepository;
import com.chellavignesh.authserver.adminportal.user.dto.CreateUserDto;
import com.chellavignesh.authserver.adminportal.user.entity.User;
import com.chellavignesh.authserver.enums.entity.*;
import com.chellavignesh.authserver.session.fingerprint.ClientFingerprint;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.sqlserver.jdbc.SQLServerDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;

/** Shared real HTTP fixtures; every subclass explicitly owns a distinct disposable database. */
public abstract class ManagementHttpTestSupport {
    protected static final class OwnedDatabase {
        public OwnedDatabase() {}
        public final String name="AuthserverManagement_"+UUID.randomUUID().toString().replace("-","");
        public final String namespace="authserver:management:"+UUID.randomUUID()+":";
        private JdbcTemplate admin;
        private boolean owned;
        public void register(DynamicPropertyRegistry registry) {
            String url=System.getenv("AUTH_DB_IT_URL");
            if (url==null || !url.matches("jdbc:sqlserver://(?:127\\.0\\.0\\.1|localhost):15433;encrypt=true;trustServerCertificate=true"))
                throw new IllegalStateException("Management tests require the approved loopback SQL Server");
            SQLServerDataSource ds=new SQLServerDataSource(); ds.setURL(url);
            ds.setUser(System.getenv("AUTH_DB_IT_USER")); ds.setPassword(System.getenv("AUTH_DB_IT_PASSWORD"));
            admin=new JdbcTemplate(ds); admin.execute("CREATE DATABASE ["+name+"]"); owned=true;
            Runtime.getRuntime().addShutdownHook(new Thread(this::drop));
            registry.add("spring.datasource.url",()->url+";databaseName="+name);
            registry.add("spring.session.redis.namespace",()->namespace+"session");
            registry.add("spring.cache.redis.key-prefix",()->namespace+"cache:");
        }
        public synchronized void drop() {
            if (owned) {
                admin.execute("ALTER DATABASE ["+name+"] SET SINGLE_USER WITH ROLLBACK IMMEDIATE");
                admin.execute("DROP DATABASE ["+name+"]"); owned=false;
            }
        }
    }
    @Autowired protected JdbcTemplate sql;
    @Autowired protected OrganizationService organizations;
    @Autowired protected ApplicationRepository applications;
    @Autowired protected UserRepository users;
    @Autowired protected RedirectUriRepository redirects;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected com.chellavignesh.authserver.adminportal.application.ApplicationService registeredApplications;
    @Autowired protected org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository registeredClients;
    @Autowired @Qualifier("redisTemplate") protected RedisTemplate<String,Object> sessionRedis;
    @Autowired @Qualifier("cacheRedisTemplate") protected RedisTemplate<String,Object> cacheRedis;
    @LocalServerPort protected int port;
    protected Organization orgA,orgB;
    protected Application app;
    protected final HttpClient http=HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    protected record Fixture(String value,byte[] key) { public Fixture {} }
    protected Organization organization(String name) throws Exception {var dto=new CreateOrganizationDto();dto.setName(name);dto.setDescription("Disposable credential fixture");return organizations.create(dto);}
    protected int group(Organization org,String name,List<String> permissions) {
        int id=sql.queryForObject("INSERT Partner.OrganizationGroup(OrganizationId,GroupName) OUTPUT INSERTED.OrganizationGroupId VALUES(?,?)",Integer.class,org.getId(),name);
        for (String key:permissions) sql.update("INSERT Partner.OrganizationGroupPermission(OrganizationGroupId,OrganizationId,PermissionName,PermissionKey) VALUES(?,?,?,?)",id,org.getId(),key,key);
        return id;
    }
    protected User user(Organization org,int group,String name) throws Exception {
        var dto=new CreateUserDto(); dto.setOrgId(org.getId());dto.setFirstName("Policy");dto.setLastName(name);
        dto.setUsername(name);dto.setEmail(name+"@example.test");dto.setPhoneNumber("+19999999999");dto.setSyncFlag(false);
        dto.setMetaData(Map.of("marker",name));
        return users.create(dto,"0011223344556677",1,group,UUID.fromString("11111111-1111-1111-1111-111111111111"),12);
    }
    protected Application application(Organization org) throws Exception {
        var dto=new CreateApplicationDto();dto.setName("Policy app");dto.setDescription("Fixture");dto.setType(ApplicationTypeEnum.WEB);dto.setAuthMethod(AuthFlowEnum.PKCE);dto.setUsernameType(UsernameTypeEnum.USERNAME);dto.setAllowForgotUsername(false);
        var settings=new UpdateTokenSettingsDto();settings.setAuthCodeTimeToLive(120);settings.setAccessTokenTimeToLive(600);settings.setRefreshTokenTimeToLive(3600);settings.setDeviceCodeTimeToLive(120);settings.setMaxRequestTransitTime(30);settings.setReuseRefreshTokens(false);
        return applications.create(org.getId(),dto,settings,new ForgotUsernameSetting(List.of()),AccessTokenFormatEnum.REFERENCE);
    }
    protected Fixture token(User user) throws Exception {
        String username=users.getByGuid(user.getRowGuid()).orElseThrow().username();
        return token(app,username,"openid metadata idp-admin");
    }
    protected Fixture token(Application client,String subject,String scope) throws Exception {
        byte[] fingerprint=new ClientFingerprint(ZoneOffset.UTC,"en-US","PolicyIntegration/1.0",null).getBytes();
        var logins=sql.queryForList("SELECT CONVERT(VARCHAR(36),c.RowGuid) FROM Person.Credential c JOIN dbo.ExternalSource e ON e.SourceId=c.ExternalId WHERE c.UserName=? AND e.SourceCode='idp'",String.class,subject);
        String login=logins.size()==1?logins.getFirst():null;
        int session=sql.queryForObject("EXEC Token.CreateAuthSession ?,?,?,?,?,?,?,?",Integer.class,client.getId(),subject,scope,client.getAuthFlow().getValue(),fingerprint,client.getClientId(),"idp",login);
        String sessionId=sql.queryForObject("SELECT CONVERT(VARCHAR(36),SessionId) FROM Token.AuthSession WHERE AuthSessionId=?",String.class,session);
        String value="policy-"+UUID.randomUUID();byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);
        sql.queryForObject("EXEC Token.CreateTokenWithHash ?,?,?,?,?,?,?,?,?",Integer.class,TokenTypeEnum.ACCESS_TOKEN.getValue(),client.getId(),subject,sessionId,true,value,key,600,MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        if (login == null && client.getType() == ApplicationTypeEnum.SERVER && subject.equals(client.getClientId())) {
            var grant=org.springframework.security.oauth2.server.authorization.OAuth2Authorization.withRegisteredClient(registeredApplications.getRegisteredClientById(client.getId()))
                    .principalName(client.getClientId()).authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .attribute(com.chellavignesh.authserver.config.JdbcOAuth2AuthorizationService.SESSION_ID,sessionId)
                    .accessToken(new org.springframework.security.oauth2.core.OAuth2AccessToken(org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER,value,Instant.now(),Instant.now().plusSeconds(600))).build();
            new org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService(sql,registeredClients).save(grant);
        }
        observationActors.put(value, com.chellavignesh.authserver.support.ManagementObservations.actor(sql, subject));
        return new Fixture(value,key);
    }
    private volatile String observationScenario = "fixture-setup";
    private final Map<String,String> observationActors = new java.util.concurrent.ConcurrentHashMap<>();
    @org.junit.jupiter.api.BeforeEach
    void observationScenario(org.junit.jupiter.api.TestInfo info) {
        observationScenario = getClass().getSimpleName()+"#"+info.getTestMethod().orElseThrow().getName();
    }
    protected HttpResponse<String> call(Fixture token,String method,String path,String body) throws Exception {
        var b=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/services"+path)).timeout(Duration.ofSeconds(20))
                .header("User-Agent","PolicyIntegration/1.0").header("Accept-Language","en-US")
                .header("x-request-datetime",OffsetDateTime.now(ZoneOffset.UTC).toString());
        if(token!=null) {var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(token.key(),"HmacSHA256"));b.header("Authorization","Bearer "+token.value()).header("x-signature",Base64.getEncoder().encodeToString(mac.doFinal((body==null?"":body).getBytes(StandardCharsets.UTF_8))));}
        b.header("x-request-id", "fixture-request-123");
        if(body!=null)b.header("Content-Type","application/json");
        return com.chellavignesh.authserver.support.ManagementObservations.capture(sql, mapper, observationScenario,
                token == null ? "unauthenticated" : observationActors.getOrDefault(token.value(), "unregistered_bearer"), method, path,
                () -> http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString()));
    }

    protected HttpResponse<String> observed(String actor, String method, String path,
                                            ManagementObservations.Request request) throws Exception {
        return ManagementObservations.capture(sql, mapper, observationScenario, actor, method, path, request);
    }

    protected void cleanup(OwnedDatabase database) {
        for (RedisTemplate<String,Object> redis:List.of(sessionRedis,cacheRedis)) {
            Set<String> keys=redis.keys(database.namespace+"*");
            if(keys!=null && !keys.isEmpty()) redis.delete(keys);
        }
        database.drop();
    }
}
