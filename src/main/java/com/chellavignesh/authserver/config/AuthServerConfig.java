package com.chellavignesh.authserver.config;


import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.config.authorization.CustomOAuth2AuthorizationCodeRequestAuthenticationConverter;
import com.chellavignesh.authserver.config.authorization.OAuth2AuthorizationErrorResponseHandler;
import com.chellavignesh.authserver.config.authorization.PreAuthorizationFilter;
import com.chellavignesh.authserver.config.introspect.IntrospectionAuthenticationConverter;
import com.chellavignesh.authserver.config.introspect.IntrospectionAuthenticationProvider;
import com.chellavignesh.authserver.config.introspect.IntrospectionSuccessHandler;
import com.chellavignesh.authserver.config.introspect.TokenIntrospector;
import com.chellavignesh.authserver.config.token.CustomTokenResponseSuccessHandler;
import com.chellavignesh.authserver.config.token.TokenEndpointBodySignatureFilter;
import com.chellavignesh.authserver.config.userinfo.OidcAuthenticationConverter;
import com.chellavignesh.authserver.config.userinfo.UserInfoSuccessHandler;
import com.chellavignesh.authserver.jwk.ClientSecretJWTAuthenticationProvider;
import com.chellavignesh.authserver.jwk.PrivateJWTAuthenticationProvider;
import com.chellavignesh.authserver.security.MultiRouteAuthenticationEntryPoint;
import com.chellavignesh.authserver.security.RequestDatetimeValidationFilter;
import com.chellavignesh.authserver.session.AuthSessionService;
import com.chellavignesh.authserver.token.SignatureService;
import com.chellavignesh.authserver.token.TokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationContext;
import org.springframework.security.oauth2.server.authorization.oidc.authentication.OidcUserInfoAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.GenericFilterBean;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

@Configuration
public class AuthServerConfig {

    private static final Logger log = LoggerFactory.getLogger(AuthServerConfig.class);

    @Value("${server.base-path}")
    private String serverBasePath;

    @Value("${toggles.fingerprinting.enabled}")
    private boolean isFingerprintingEnabled;

    private final ApplicationService applicationService;
    private final JdbcRegisteredClientRepository jdbcRegisteredClientRepository;
    private final CustomOAuth2AuthorizationCodeRequestAuthenticationConverter authorizationCodeRequestAuthenticationConverter;
    private final AuthenticationProvider authenticationProvider;
    private final PrivateJWTAuthenticationProvider privateJWTAuthenticationProvider;
    private final ClientSecretJWTAuthenticationProvider clientSecretJWTAuthenticationProvider;
    private final TokenEndpointBodySignatureFilter tokenEndpointBodySignatureFilter;
    private final OAuth2ResponseBodySignatureFilter oAuth2ResponseBodySignatureFilter;
    private final RequestDatetimeValidationFilter requestDatetimeValidationFilter;
    private final PreAuthorizationFilter preAuthorizationFilter;
    private final SignatureService signatureService;
    private final TokenService tokenService;
    private final AuthSessionService authSessionService;
    private final IntrospectionAuthenticationProvider introspectionAuthenticationProvider;
    private final MultiRouteAuthenticationEntryPoint multiRouteAuthenticationEntryPoint;
    private final OAuth2AuthorizationErrorResponseHandler oauth2AuthorizationErrorResponseHandler;

    @Autowired
    public AuthServerConfig(ApplicationService applicationService, JdbcRegisteredClientRepository jdbcRegisteredClientRepository, CustomOAuth2AuthorizationCodeRequestAuthenticationConverter authorizationCodeRequestAuthenticationConverter, AuthenticationProvider authenticationProvider, PrivateJWTAuthenticationProvider privateJWTAuthenticationProvider, ClientSecretJWTAuthenticationProvider clientSecretJWTAuthenticationProvider, TokenEndpointBodySignatureFilter tokenEndpointBodySignatureFilter, OAuth2ResponseBodySignatureFilter oAuth2ResponseBodySignatureFilter, RequestDatetimeValidationFilter requestDatetimeValidationFilter, PreAuthorizationFilter preAuthorizationFilter, SignatureService signatureService, TokenService tokenService, AuthSessionService authSessionService, IntrospectionAuthenticationProvider introspectionAuthenticationProvider, MultiRouteAuthenticationEntryPoint multiRouteAuthenticationEntryPoint, OAuth2AuthorizationErrorResponseHandler oauth2AuthorizationErrorResponseHandler) {
        this.applicationService = applicationService;
        this.jdbcRegisteredClientRepository = jdbcRegisteredClientRepository;
        this.authorizationCodeRequestAuthenticationConverter = authorizationCodeRequestAuthenticationConverter;
        this.authenticationProvider = authenticationProvider;
        this.privateJWTAuthenticationProvider = privateJWTAuthenticationProvider;
        this.clientSecretJWTAuthenticationProvider = clientSecretJWTAuthenticationProvider;
        this.tokenEndpointBodySignatureFilter = tokenEndpointBodySignatureFilter;
        this.oAuth2ResponseBodySignatureFilter = oAuth2ResponseBodySignatureFilter;
        this.requestDatetimeValidationFilter = requestDatetimeValidationFilter;
        this.preAuthorizationFilter = preAuthorizationFilter;
        this.signatureService = signatureService;
        this.tokenService = tokenService;
        this.authSessionService = authSessionService;
        this.introspectionAuthenticationProvider = introspectionAuthenticationProvider;
        this.multiRouteAuthenticationEntryPoint = multiRouteAuthenticationEntryPoint;
        this.oauth2AuthorizationErrorResponseHandler = oauth2AuthorizationErrorResponseHandler;
    }

    // ------------------------------------------------------
    // LOGOUT FILTER CHAIN
    // ------------------------------------------------------
    @Bean
    @Order(1)
    public SecurityFilterChain logoutSecurityFilterChain(HttpSecurity http) throws Exception {

        http.securityMatcher("/oauth2/connect/logout").authorizeHttpRequests(auth -> auth.anyRequest().permitAll()).csrf(AbstractHttpConfigurer::disable);

        log.info("Logout endpoint configured to permitAll (no Bearer token required)");
        return http.build();
    }

    // ------------------------------------------------------
    // AUTHORIZATION SERVER FILTER CHAIN
    // ------------------------------------------------------
    @Bean
    @Order(2)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {

        http.cors(Customizer.withDefaults());
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

        http.addFilterBefore(tokenEndpointBodySignatureFilter, CsrfFilter.class);
        http.addFilterBefore(preAuthorizationFilter, TokenEndpointBodySignatureFilter.class);
        http.addFilterAfter(oAuth2ResponseBodySignatureFilter, TokenEndpointBodySignatureFilter.class);
        http.addFilterAfter(requestDatetimeValidationFilter, OAuth2ResponseBodySignatureFilter.class);

        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .registeredClientRepository(jdbcRegisteredClientRepository)
                .oidc(oidc -> oidc.userInfoEndpoint(userInfo ->
                        userInfo.userInfoRequestConverter(new OidcAuthenticationConverter(signatureService))
                                .userInfoMapper(userInfoMapper)
                                .authenticationProvider(authenticationProvider)
                                .userInfoResponseHandler(new UserInfoSuccessHandler())
                ))
                .clientAuthentication(clientAuth -> clientAuth.authenticationProviders(providers -> {
                    providers.add(authenticationProvider);
                    providers.add(privateJWTAuthenticationProvider);
                    providers.add(clientSecretJWTAuthenticationProvider);
                }))
                .tokenIntrospectionEndpoint(introspection -> {
                    introspection.introspectionRequestConverter(
                            new IntrospectionAuthenticationConverter(signatureService)
                    );
                    introspection.authenticationProviders(providers -> {
                        providers.clear();
                        providers.add(introspectionAuthenticationProvider);
                    });
                    introspection.introspectionResponseHandler(
                            new IntrospectionSuccessHandler(
                                    applicationService,
                                    tokenService,
                                    authSessionService,
                                    isFingerprintingEnabled
                            )
                    );
                })
                .authorizationEndpoint(endpoint -> {
                    endpoint.authorizationRequestConverter(
                            authorizationCodeRequestAuthenticationConverter
                    );
                    endpoint.errorResponseHandler(oauth2AuthorizationErrorResponseHandler);
                })
                .tokenEndpoint(tokenEndpoint ->
                        tokenEndpoint.accessTokenResponseHandler(
                                new CustomTokenResponseSuccessHandler(tokenService)
                        )
                );

        http.exceptionHandling(exceptions -> {
            exceptions.authenticationEntryPoint(multiRouteAuthenticationEntryPoint);
        });

        http.sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
        );

        http.addFilterBefore(
                new OidcWellKnownOverwriteFilter(),
                AbstractPreAuthenticatedProcessingFilter.class
        );

        return http.build();
    }

    // ------------------------------------------------------
    // CORS CONFIGURATION
    // ------------------------------------------------------
    @Bean
    @Order(1)
    CorsConfigurationSource corsConfigurationSource(@Value("${endpoints.web.cors.allowed-origins}") List<String> allowedOrigins) {

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList(HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.DELETE.name(), HttpMethod.PUT.name(), HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(Arrays.asList("Accept", "Accept-Encoding", "Authorization", "Content-Type", "x-signature", "x-request-datetime"));
        configuration.setExposedHeaders(Arrays.asList("x-signature", "Location"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    // ------------------------------------------------------
    // REQUEST WRAPPING FILTER
    // ------------------------------------------------------
    @Component
    @Order(1)
    public static class EncodingFilter extends GenericFilterBean {

        @Override
        public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

            final HttpServletRequest req = (HttpServletRequest) request;
            HttpServletRequest wrapped = new ContentCachingRequestWrapper(req);

            chain.doFilter(wrapped, response);
        }
    }

    // ------------------------------------------------------
    // AUTH SERVER SETTINGS
    // ------------------------------------------------------
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        String issuer = serverBasePath + "/oauth2";
        log.debug("Configuring AuthorizationServerSettings with issuer: {}", issuer);

        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    // ------------------------------------------------------
    // CLIENT SETTINGS
    // ------------------------------------------------------
    @Bean
    public ClientSettings clientSettings() {
        return ClientSettings.builder().requireAuthorizationConsent(false).requireProofKey(false).build();
    }

    // ------------------------------------------------------
    // JWT DECODER
    // ------------------------------------------------------
    @Bean
    public JwtDecoderFactory<RegisteredClient> jwtDecoderFactory() {
        return context -> {
            SecretKey secretKey = new SecretKeySpec(context.getClientSecret().getBytes(), "HmacSHA256");
            return NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(org.springframework.security.oauth2.jose.jws.MacAlgorithm.HS256).build();
        };
    }

    // ------------------------------------------------------
    // USER INFO MAPPER
    // ------------------------------------------------------
    Function<OidcUserInfoAuthenticationContext, OidcUserInfo> userInfoMapper = context -> {
        OidcUserInfoAuthenticationToken authentication = context.getAuthentication();
        BearerTokenAuthentication principal = (BearerTokenAuthentication) authentication.getPrincipal();

        return new OidcUserInfo(principal.getTokenAttributes());
    };
}
