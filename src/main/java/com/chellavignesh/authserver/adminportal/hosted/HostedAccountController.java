package com.chellavignesh.authserver.adminportal.hosted;

import com.chellavignesh.authserver.adminportal.user.dto.*;

import jakarta.servlet.http.*;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Controller
public class HostedAccountController {
    private static final String VALIDATED_RETURN =
            HostedAccountController.class.getName() + ".returnTo";
    private static final Set<String> LAUNCH =
            Set.of("clientId", "expectedLoginId", "returnTo", "x-request-datetime");
    private final HostedAccountAccessPolicy policy;
    private final HostedAccountNonceStore nonces;
    private final HostedAccountMutationService mutations;

    public HostedAccountController(
            HostedAccountAccessPolicy policy,
            HostedAccountNonceStore nonces,
            HostedAccountMutationService mutations) {
        this.policy = policy;
        this.nonces = nonces;
        this.mutations = mutations;
    }

    @GetMapping("/portal/account-action")
    public String transition(
            HttpServletRequest request, HttpServletResponse response, Model model) {
        headers(response);
        var allowed = new HashSet<>(LAUNCH);
        allowed.add("action");
        exact(request, allowed);
        var launch = launch(request, HostedAccountAction.parse(value(request, "action")));
        model.addAttribute(
                "continueUrl",
                policy.issuer()
                        + "/portal/account/"
                        + launch.action().wire()
                        + "?"
                        + query(launch));
        model.addAttribute("returnUrl", launch.returnTo().toString());
        return "pages/portal/account-transition";
    }

    @GetMapping({"/portal/account/profile", "/portal/account/password"})
    public String form(HttpServletRequest request, HttpServletResponse response, Model model) {
        headers(response);
        exact(request, LAUNCH);
        var action = action(request);
        var launch = launch(request, action);
        model.addAttribute("returnUrl", launch.returnTo().toString());
        request.setAttribute(VALIDATED_RETURN, launch.returnTo());
        var actor = policy.requireActor(request, launch, value(request, "x-request-datetime"));
        shared(
                model,
                launch,
                nonces.issue(request.getSession().getId(), actor, action, launch.returnTo()));
        model.addAttribute("username", actor.user().username());
        if (action == HostedAccountAction.PROFILE) {
            model.addAttribute("firstName", actor.user().firstName());
            model.addAttribute("lastName", actor.user().lastName());
            model.addAttribute("email", actor.user().email());
            model.addAttribute("phoneNumber", actor.user().phoneNumber());
            model.addAttribute("secondaryPhoneNumber", actor.user().secondaryPhoneNumber());
        }
        return "pages/portal/account-" + action.wire();
    }

    @PostMapping({"/portal/account/profile", "/portal/account/password"})
    public String submit(HttpServletRequest request, HttpServletResponse response, Model model)
            throws Exception {
        headers(response);
        var action = action(request);
        var allowed = new HashSet<>(LAUNCH);
        allowed.addAll(Set.of("nonce", "_csrf"));
        allowed.addAll(
                action == HostedAccountAction.PROFILE
                        ? Set.of(
                                "firstName",
                                "lastName",
                                "email",
                                "confirmEmail",
                                "phoneNumber",
                                "secondaryPhoneNumber")
                        : Set.of("currentPassword", "newPassword", "confirmPassword"));
        exact(request, allowed);
        var launch = launch(request, action);
        model.addAttribute("returnUrl", launch.returnTo().toString());
        request.setAttribute(VALIDATED_RETURN, launch.returnTo());
        var actor = policy.requireActor(request, launch, value(request, "x-request-datetime"));
        var session = request.getSession(false);
        if (session == null) throw HostedAccountFailure.denied();
        nonces.consume(session.getId(), actor, action, launch.returnTo(), value(request, "nonce"));
        if (action == HostedAccountAction.PROFILE) {
            var dto = new ChangeProfileDto();
            dto.setFirstName(value(request, "firstName"));
            dto.setLastName(value(request, "lastName"));
            dto.setEmail(value(request, "email"));
            dto.setConfirmEmail(value(request, "confirmEmail"));
            dto.setPhoneNumber(value(request, "phoneNumber"));
            dto.setSecondaryPhoneNumber(value(request, "secondaryPhoneNumber"));
            mutations.updateProfile(actor, dto);
            response.setStatus(303);
            response.setHeader(
                    HttpHeaders.LOCATION,
                    launch.callback().toString()
                            + "?p="
                            + URLEncoder.encode(
                                    Base64.getEncoder()
                                            .encodeToString(
                                                    ("surl="
                                                                    + URLEncoder.encode(
                                                                            launch.returnTo()
                                                                                    .toString(),
                                                                            StandardCharsets.UTF_8))
                                                            .getBytes(StandardCharsets.UTF_8)),
                                    StandardCharsets.UTF_8));
            return null;
        }
        var dto = new ChangePasswordDto();
        dto.setCurrentPassword(value(request, "currentPassword"));
        dto.setNewPassword(value(request, "newPassword"));
        dto.setConfirmPassword(value(request, "confirmPassword"));
        mutations.changePasswordAndRevoke(actor, dto);
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
        session.invalidate();
        Cookie cookie = new Cookie("ags-idp-session", "");
        cookie.setHttpOnly(true);
        cookie.setSecure("https".equals(URI.create(policy.issuer()).getScheme()));
        cookie.setPath(URI.create(policy.issuer()).getPath());
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
        model.addAttribute("returnUrl", launch.returnTo().toString());
        return "pages/portal/account-confirmation";
    }

    private HostedAccountAccessPolicy.ValidatedLaunch launch(
            HttpServletRequest request, HostedAccountAction action) {
        try {
            String id = value(request, "expectedLoginId");
            UUID login = UUID.fromString(id);
            if (!login.toString().equalsIgnoreCase(id)) throw HostedAccountFailure.invalid();
            return policy.validateLaunch(
                    action.wire(),
                    value(request, "clientId"),
                    login,
                    URI.create(value(request, "returnTo")));
        } catch (IllegalArgumentException e) {
            throw HostedAccountFailure.invalid();
        }
    }

    private static HostedAccountAction action(HttpServletRequest request) {
        return HostedAccountAction.parse(
                request.getServletPath().substring("/portal/account/".length()));
    }

    private static String value(HttpServletRequest request, String key) {
        String value = request.getParameter(key);
        if (value == null || value.length() > 4096) throw HostedAccountFailure.invalid();
        return value;
    }

    private static void exact(HttpServletRequest request, Set<String> allowed) {
        if (!request.getParameterMap().keySet().equals(allowed)
                || request.getParameterMap().values().stream().anyMatch(v -> v.length != 1))
            throw HostedAccountFailure.invalid();
        if (request.getQueryString() != null && request.getQueryString().length() > 4096)
            throw HostedAccountFailure.invalid();
    }

    private static void headers(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "origin");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }

    private static String query(HostedAccountAccessPolicy.ValidatedLaunch launch) {
        return "clientId="
                + URLEncoder.encode(launch.clientId(), StandardCharsets.UTF_8)
                + "&expectedLoginId="
                + launch.expectedLoginId()
                + "&returnTo="
                + URLEncoder.encode(launch.returnTo().toString(), StandardCharsets.UTF_8)
                + "&x-request-datetime="
                + URLEncoder.encode(Instant.now().toString(), StandardCharsets.UTF_8);
    }

    private static void shared(
            Model model, HostedAccountAccessPolicy.ValidatedLaunch launch, String nonce) {
        model.addAttribute("clientId", launch.clientId());
        model.addAttribute("expectedLoginId", launch.expectedLoginId().toString());
        model.addAttribute("returnUrl", launch.returnTo().toString());
        model.addAttribute("nonce", nonce);
        model.addAttribute("datetime", Instant.now().toString());
    }

    @ExceptionHandler(HostedAccountFailure.class)
    public String safeFailure(
            HostedAccountFailure failure,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        headers(response);
        response.setStatus(failure.status());
        if (failure.status() == 503) response.setHeader("Retry-After", "5");
        if (request.getAttribute(VALIDATED_RETURN) instanceof URI target)
            model.addAttribute("returnUrl", target.toString());
        model.addAttribute("message", failure.getMessage());
        return "pages/portal/account-error";
    }

    @ExceptionHandler(Exception.class)
    public String unexpectedFailure(
            Exception failure,
            HttpServletRequest request,
            HttpServletResponse response,
            Model model) {
        return safeFailure(HostedAccountFailure.unavailable(), request, response, model);
    }
}
