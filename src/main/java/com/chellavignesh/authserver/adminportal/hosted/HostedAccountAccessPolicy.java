package com.chellavignesh.authserver.adminportal.hosted;

import com.chellavignesh.authserver.adminportal.application.ApplicationService;
import com.chellavignesh.authserver.adminportal.externalsource.ExternalSourceRepository;
import com.chellavignesh.authserver.adminportal.user.UserService;
import com.chellavignesh.authserver.adminportal.user.UserStatus;
import com.chellavignesh.authserver.enums.entity.*;
import com.chellavignesh.authserver.security.RequestDatetimeValidator;
import com.chellavignesh.authserver.session.fingerprint.ClientFingerprint;
import com.chellavignesh.authserver.session.fingerprint.ClientFingerprintParser;
import com.chellavignesh.authserver.session.sso.SingleSignOnService;
import com.chellavignesh.authserver.token.TokenService;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.ZoneOffset;
import java.util.*;

@Service
public class HostedAccountAccessPolicy {
    public record ValidatedLaunch(
            HostedAccountAction action,
            String clientId,
            int applicationId,
            UUID expectedLoginId,
            URI returnTo,
            URI callback) {}

    private final ApplicationService applications;
    private final SingleSignOnService sso;
    private final UserService users;
    private final ExternalSourceRepository sources;
    private final URI issuer;
    private final RequestDatetimeValidator dates;
    private final TokenService tokens;

    public HostedAccountAccessPolicy(
            ApplicationService applications,
            SingleSignOnService sso,
            UserService users,
            ExternalSourceRepository sources,
            AuthorizationServerSettings settings,
            RequestDatetimeValidator dates,
            TokenService tokens) {
        this.applications = applications;
        this.sso = sso;
        this.users = users;
        this.sources = sources;
        this.issuer = URI.create(settings.getIssuer());
        this.dates = dates;
        this.tokens = tokens;
    }

    public String issuer() {
        return issuer.toString().replaceAll("/+$", "");
    }

    public String issuerOrigin() {
        return origin(issuer);
    }

    public ValidatedLaunch validateLaunch(
            String action, String clientId, UUID expectedLoginId, URI returnTo) {
        try {
            var parsed = HostedAccountAction.parse(action);
            if (clientId == null
                    || clientId.isBlank()
                    || clientId.length() > 256
                    || expectedLoginId == null
                    || returnTo == null
                    || returnTo.toString().length() > 2048) throw HostedAccountFailure.invalid();
            var app =
                    applications.getByClientId(clientId).orElseThrow(HostedAccountFailure::denied);
            if (!Boolean.TRUE.equals(app.getActive()) || app.getType() != ApplicationTypeEnum.WEB)
                throw HostedAccountFailure.denied();
            var client = applications.getRegisteredClientById(app.getId());
            if (client == null || !clientId.equals(client.getClientId()))
                throw HostedAccountFailure.denied();
            URI callback = null;
            for (String registered : client.getRedirectUris()) {
                URI candidate = URI.create(registered);
                if (portalCallback(candidate)
                        && (returnTo.toString().equals(origin(candidate) + "/account")
                                || returnTo.toString()
                                        .equals(origin(candidate) + "/account/security")))
                    callback = candidate;
            }
            if (callback == null) throw HostedAccountFailure.invalid();
            return new ValidatedLaunch(
                    parsed, clientId, app.getId(), expectedLoginId, returnTo, callback);
        } catch (HostedAccountFailure failure) {
            throw failure;
        } catch (IllegalArgumentException failure) {
            throw HostedAccountFailure.invalid();
        } catch (Exception failure) {
            throw HostedAccountFailure.unavailable();
        }
    }

    public HostedAccountActor requireActor(
            HttpServletRequest request, ValidatedLaunch launch, String datetime) {
        try {
            String path = request.getServletPath();
            if (!path.equals("/portal/account/" + launch.action().wire())
                    || !("GET".equals(request.getMethod()) || "POST".equals(request.getMethod())))
                throw HostedAccountFailure.denied();
            URI referer = URI.create(Objects.requireNonNull(request.getHeader("Referer")));
            if (referer.getRawUserInfo() != null
                    || referer.getRawFragment() != null
                    || !issuerOrigin().equals(origin(referer))) throw HostedAccountFailure.denied();
            if ("POST".equals(request.getMethod())
                    && !issuerOrigin().equals(request.getHeader("Origin")))
                throw HostedAccountFailure.denied();
            String cookie = null;
            if (request.getCookies() != null)
                for (var value : request.getCookies())
                    if ("ags-idp-session".equals(value.getName())) {
                        if (cookie != null) throw HostedAccountFailure.denied();
                        cookie = value.getValue();
                    }
            if (cookie == null || cookie.length() > 512) throw HostedAccountFailure.denied();
            byte[] decoded = Base64.getDecoder().decode(cookie);
            if (!Base64.getEncoder().encodeToString(decoded).equals(cookie))
                throw HostedAccountFailure.denied();
            var session =
                    sso.getAuthSessionFromCookie(decoded).orElseThrow(HostedAccountFailure::denied);
            var current =
                    validateLaunch(
                            launch.action().wire(),
                            launch.clientId(),
                            launch.expectedLoginId(),
                            launch.returnTo());
            var actor = resolveActor(session, current);
            if (!ZoneOffset.UTC.equals(ClientFingerprintParser.parseZoneOffset(datetime))
                    || !dates.validateRequestDatetime(datetime, current.applicationId()))
                throw HostedAccountFailure.denied();
            var actual =
                    new ClientFingerprint(
                                    ZoneOffset.UTC,
                                    request.getHeader("Accept-Language"),
                                    request.getHeader("User-Agent"),
                                    current.callback().getHost())
                            .getBytes();
            if (!Arrays.equals(session.getClientFingerprint(), actual))
                throw HostedAccountFailure.denied();
            return actor;
        } catch (HostedAccountFailure failure) {
            throw failure;
        } catch (
                com.chellavignesh.authserver.session.sso.exception
                                .InvalidSingleSignOnCookieException
                        failure) {
            throw HostedAccountFailure.denied();
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw HostedAccountFailure.denied();
        } catch (Exception failure) {
            throw HostedAccountFailure.unavailable();
        }
    }

    public HostedAccountActor revalidate(
            HostedAccountActor actor,
            com.chellavignesh.authserver.session.entity.AuthSession session) {
        var current =
                validateLaunch(
                        "profile",
                        applications
                                .getById(actor.authSession().getApplicationId())
                                .orElseThrow(HostedAccountFailure::denied)
                                .getClientId(),
                        actor.loginId(),
                        actor.returnTo());
        return resolveActor(session, current);
    }

    private HostedAccountActor resolveActor(
            com.chellavignesh.authserver.session.entity.AuthSession session,
            ValidatedLaunch current) {
        if (session.getAuthSessionStatus() != AuthSessionStatusEnum.ACTIVE
                || session.getAuthSessionId() == null
                || !Objects.equals(session.getApplicationId(), current.applicationId())
                || !current.callback().toString().equals(session.getRedirectUri()))
            throw HostedAccountFailure.denied();
        var user =
                users.getByUsernameAndBranding(session.getSubjectId(), session.getBranding())
                        .orElseThrow(HostedAccountFailure::denied);
        if (user.status() != UserStatus.Active
                || !current.expectedLoginId().equals(user.loginId())
                || !Objects.equals(user.username(), session.getSubjectId())
                || !Objects.equals(user.branding(), session.getBranding())
                || !users.isManagementIdentityActive(user.rowGuid(), user.orgId(), user.loginId()))
            throw HostedAccountFailure.denied();
        // An active SSO cookie is created before MFA. A completed current human grant is required.
        if (tokens.getAllActiveBySessionId(session.getSessionId()).stream()
                .noneMatch(
                        t ->
                                t.getTokenType() == TokenTypeEnum.ACCESS_TOKEN
                                        && tokens.isTokenActive(t, session)))
            throw HostedAccountFailure.denied();
        var source = sources.findAllByBrandingSourceCode(List.of(session.getBranding()));
        if (source.size() != 1
                || source.getFirst().getExternalType() == null
                || !"IDP".equals(source.getFirst().getExternalType().name())
                || !Boolean.FALSE.equals(user.credSyncFlag())
                || !Boolean.FALSE.equals(user.profileSyncFlag()))
            throw HostedAccountFailure.managed();
        return new HostedAccountActor(
                session,
                user.loginId(),
                user.rowGuid(),
                session.getBranding(),
                current.callback(),
                current.returnTo(),
                user);
    }

    private static boolean portalCallback(URI uri) {
        return uri.getRawUserInfo() == null
                && uri.getRawQuery() == null
                && uri.getRawFragment() == null
                && "/auth/callback".equals(uri.getRawPath())
                && validEndpoint(uri);
    }

    private static boolean validEndpoint(URI uri) {
        return uri.getHost() != null
                && ("https".equals(uri.getScheme())
                        || "http".equals(uri.getScheme())
                                && Set.of("127.0.0.1", "localhost", "[::1]")
                                        .contains(uri.getHost()));
    }

    private static String origin(URI uri) {
        if (!validEndpoint(uri)) throw HostedAccountFailure.invalid();
        int port = uri.getPort();
        return uri.getScheme()
                + "://"
                + uri.getHost()
                + (port == -1
                                || port == 80 && "http".equals(uri.getScheme())
                                || port == 443 && "https".equals(uri.getScheme())
                        ? ""
                        : ":" + port);
    }
}
