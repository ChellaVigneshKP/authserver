package com.chellavignesh.authserver.security;

import com.chellavignesh.authserver.adminportal.user.UserService;
import com.chellavignesh.authserver.biometric.BiometricAuthenticationFilter;
import com.chellavignesh.authserver.biometric.BiometricAuthenticationProvider;
import com.chellavignesh.authserver.config.CustomAuthenticationFailureHandler;
import com.chellavignesh.authserver.config.CustomAuthenticationSuccessHandler;
import com.chellavignesh.authserver.config.authorization.PreAuthorizationFilter;
import com.chellavignesh.authserver.config.introspect.TokenIntrospector;
import com.chellavignesh.authserver.filter.StaticResourceSessionFilter;
import com.chellavignesh.authserver.session.*;
import com.chellavignesh.authserver.unite.UniteMSCServiceClient;
import com.chellavignesh.libcrypto.service.impl.CryptoWebClientImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.annotation.web.configurers.HttpBasicConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SpringSecurityConfig {

    private final TokenIntrospector tokenIntrospector;
    private final CustomPermissionEvaluator permissionEvaluator;
    private final CryptoWebClientImpl cryptoWebClient;
    private final HasherConfig hasherConfig;
    private final RequestClientFingerprintFilter requestClientFingerprintFilter;

    private final BrandingRequestBodyFilter brandingRequestBodyFilter;
    private final CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler;
    private final CustomAuthenticationFailureHandler customAuthenticationFailureHandler;
    private final CustomUserDetailsService customUserDetailsService;
    private final RequestBodySignatureFilter requestBodySignatureFilter;
    private final ResponseBodySignatureFilter responseBodySignatureFilter;
    private final RequestDatetimeValidationFilter requestDatetimeValidationFilter;
    private final UserService userService;
    private final UniteMSCServiceClient uniteServiceClient;
    private final PreAuthorizationFilter preAuthorizationFilter;
    private final StaticResourceSessionFilter staticResourceSessionFilter;

    public SpringSecurityConfig(TokenIntrospector tokenIntrospector, CustomPermissionEvaluator permissionEvaluator, CryptoWebClientImpl cryptoWebClient, HasherConfig hasherConfig, RequestClientFingerprintFilter requestClientFingerprintFilter, BrandingRequestBodyFilter brandingRequestBodyFilter, CustomAuthenticationSuccessHandler customAuthenticationSuccessHandler, CustomAuthenticationFailureHandler customAuthenticationFailureHandler, CustomUserDetailsService customUserDetailsService, RequestBodySignatureFilter requestBodySignatureFilter, ResponseBodySignatureFilter responseBodySignatureFilter, RequestDatetimeValidationFilter requestDatetimeValidationFilter, UserService userService, UniteMSCServiceClient uniteServiceClient, PreAuthorizationFilter preAuthorizationFilter, StaticResourceSessionFilter staticResourceSessionFilter) {
        this.tokenIntrospector = tokenIntrospector;
        this.permissionEvaluator = permissionEvaluator;
        this.cryptoWebClient = cryptoWebClient;
        this.hasherConfig = hasherConfig;
        this.requestClientFingerprintFilter = requestClientFingerprintFilter;
        this.brandingRequestBodyFilter = brandingRequestBodyFilter;
        this.customAuthenticationSuccessHandler = customAuthenticationSuccessHandler;
        this.customAuthenticationFailureHandler = customAuthenticationFailureHandler;
        this.customUserDetailsService = customUserDetailsService;
        this.requestBodySignatureFilter = requestBodySignatureFilter;
        this.responseBodySignatureFilter = responseBodySignatureFilter;
        this.requestDatetimeValidationFilter = requestDatetimeValidationFilter;
        this.userService = userService;
        this.uniteServiceClient = uniteServiceClient;
        this.preAuthorizationFilter = preAuthorizationFilter;
        this.staticResourceSessionFilter = staticResourceSessionFilter;
    }

    @Bean
    @Order(3)
    public SecurityFilterChain configureSecurityFilterChain(HttpSecurity http, BiometricAuthenticationFilter biometricAuthenticationFilter) throws Exception {

        http.cors(Customizer.withDefaults())
                .authorizeHttpRequests(authz -> authz.requestMatchers(HttpMethod.OPTIONS).permitAll()
                        .requestMatchers("/add-token-cookie").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .requestMatchers("/css/**", "/images/**", "/js/**").permitAll()
                        .requestMatchers("/oauth2/signature/**").authenticated()
                        .requestMatchers("/oauth2/**").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/login/**").permitAll()
                        .requestMatchers("/login-biometric").permitAll()
                        .requestMatchers("/activate-account/**").permitAll()
                        .requestMatchers("/forgot-password/**").permitAll()
                        .requestMatchers("/mobile-forgot-password/**").permitAll()
                        .requestMatchers("/forgot-username/**").permitAll()
                        .requestMatchers("/mobile-forgot-username/**").permitAll()
                        .requestMatchers("/mfa/**").permitAll()
                        .requestMatchers("/error/**").permitAll()
                        .requestMatchers("/user/**").permitAll())

                // static resources
                .addFilterBefore(staticResourceSessionFilter, UsernamePasswordAuthenticationFilter.class)

                // branding + authentication filters
                .addFilterBefore(brandingRequestBodyFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(biometricAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

                // request validation filters
                .addFilterAfter(requestDatetimeValidationFilter, AuthorizationFilter.class)
                .addFilterAfter(requestClientFingerprintFilter, RequestDatetimeValidationFilter.class)
                .addFilterAfter(requestBodySignatureFilter, RequestDatetimeValidationFilter.class)
                .addFilterAfter(responseBodySignatureFilter, RequestBodySignatureFilter.class)

                // OAuth2 resource server
                .oauth2ResourceServer(oauth2 -> oauth2.opaqueToken(opaque -> opaque.introspector(tokenIntrospector)))

                .csrf(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .httpBasic(HttpBasicConfigurer::disable)

                .formLogin(login -> login.loginPage("/login")
                        .loginProcessingUrl("/login")
                        .permitAll().successHandler(customAuthenticationSuccessHandler).failureHandler(customAuthenticationFailureHandler))

                .addFilterAfter(preAuthorizationFilter, UsernamePasswordAuthenticationFilter.class)

                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable));

        return http.build();
    }

    /**
     * SecurityFilterChain for static resources to skip session tracking
     * Prevents LAST_ACCESS_TIME updates for JS, CSS, images
     */
    @Bean
    @Order(1)
    public SecurityFilterChain staticResourcesSecurityFilterChain(HttpSecurity http) throws Exception {

        http.securityMatcher("/css/**", "/js/**", "/images/**", "/static/**", "/favicon.ico", "/robots.txt")
                .authorizeHttpRequests(authz -> authz.anyRequest().permitAll())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(staticResourceSessionFilter, UsernamePasswordAuthenticationFilter.class)
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(HeadersConfigurer.FrameOptionsConfig::disable));
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(DaoAuthenticationProvider daoAuthenticationProvider, BiometricAuthenticationProvider biometricAuthenticationProvider) {
        ProviderManager manager = new ProviderManager(daoAuthenticationProvider, biometricAuthenticationProvider);
        manager.setEraseCredentialsAfterAuthentication(false);
        return manager;
    }

    @Bean
    public BiometricAuthenticationFilter biometricAuthenticationFilter(AuthenticationManager authenticationManager) {
        BiometricAuthenticationFilter filter = new BiometricAuthenticationFilter(authenticationManager);
        filter.setAuthenticationSuccessHandler(customAuthenticationSuccessHandler);
        filter.setAuthenticationFailureHandler(customAuthenticationFailureHandler);
        return filter;
    }

    @Bean
    public BiometricAuthenticationProvider biometricAuthenticationProvider() {
        return new BiometricAuthenticationProvider(userService, uniteServiceClient);
    }

    @Bean
    public AuthenticationManager biometricAuthenticationManager(BiometricAuthenticationProvider biometricAuthenticationProvider) {
        ProviderManager manager = new ProviderManager(biometricAuthenticationProvider);
        manager.setEraseCredentialsAfterAuthentication(false);
        return manager;
    }

    @Bean
    public MethodSecurityExpressionHandler expressionHandler() {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(permissionEvaluator);
        return handler;
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(customUserDetailsService);
        provider.setPasswordEncoder(delegatingPasswordEncoder());
        return provider;
    }

    @Bean
    public PasswordEncoder delegatingPasswordEncoder() {
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put(LibCryptoPasswordEncoder.ENCODER_ID.toString(), new LibCryptoPasswordEncoder(cryptoWebClient));
        encoders.put(PasswordEncoderFactory.currentVersion.toString(), new KPCVPasswordEncoder(hasherConfig));
        return new DelegatingPasswordEncoder(PasswordEncoderFactory.currentVersion.toString(), encoders);
    }
}