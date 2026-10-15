package com.chellavignesh.authserver.ui;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

class BrowserViewContractTest {
    @Test void everyControllerViewTargetIsPackaged() throws Exception {
        for (var view : new String[]{"login/login", "login-legacy", "mfa-legacy", "mfa/request-pin", "mfa/enter-pin", "account-access/identify-yourself", "account-access/update-password", "forgot-username/identify-yourself", "mobile-forgot-username/identify-yourself", "user/change-profile", "user/change-password", "404"}) {
            assertThat(new ClassPathResource("templates/pages/" + view + ".html").exists()).as(view).isTrue();
        }
    }
    @Test void thymeleafRendersEveryViewAndContextAwareFormActions() {
        var resolver = new org.thymeleaf.templateresolver.ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        var engine = new org.thymeleaf.spring6.SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        var servletContext = new org.springframework.mock.web.MockServletContext();
        var request = new org.springframework.mock.web.MockHttpServletRequest(servletContext);
        request.setContextPath("/services");
        request.setRequestURI("/services/login");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        var application = org.thymeleaf.web.servlet.JakartaServletWebApplication.buildApplication(servletContext);
        var exchange = application.buildExchange(request, response);
        var variables = new java.util.HashMap<String, Object>();
        variables.put("flow", "forgot-password");
        variables.put("error", "<script>alert(1)</script>");
        variables.put("options", java.util.List.of(new com.chellavignesh.authserver.mfa.dto.FactorOption("id-email", "Email to a***@example.com")));
        variables.put("usernameLookupFields", java.util.List.of(new com.chellavignesh.authserver.adminportal.application.dto.UsernameLookupField("email", "Email")));
        var context = new org.thymeleaf.context.WebContext(exchange, java.util.Locale.ENGLISH, variables);
        for (var view : new String[]{"login/login", "login-legacy", "mfa-legacy", "mfa/request-pin", "mfa/enter-pin", "account-access/identify-yourself", "account-access/update-password", "forgot-username/identify-yourself", "mobile-forgot-username/identify-yourself", "user/change-profile", "user/change-password", "404"}) {
            var html = engine.process("pages/" + view, context);
            assertThat(html).as(view).contains("/services/css/auth.css", "&lt;script&gt;").doesNotContain("<script>alert(1)</script>");
            if (view.equals("login/login")) assertThat(html).contains("action=\"/services/login\"");
            if (view.equals("account-access/update-password")) assertThat(html).contains("action=\"/services/forgot-password/update-password\"");
            if (view.equals("mfa/enter-pin")) assertThat(html).contains("name=\"pin\"", "action=\"/services/mfa/forgot-password/verify-pin\"");
            if (view.equals("user/change-profile")) assertThat(html).contains("name=\"firstName\"", "name=\"confirmEmail\"", "data-sso-url=\"/services/oauth2/sso\"");
        }
    }
    @Test void passwordFormsEncodeAtSubmitAndAssetsArePackaged() throws Exception {
        var script = new ClassPathResource("static/js/auth-forms.js");
        assertThat(script.exists()).isTrue();
        var js = script.getContentAsString(StandardCharsets.UTF_8);
        assertThat(js).contains("TextEncoder", "btoa", "signing_key", "HMAC", "JSON.stringify");
        var login = new ClassPathResource("templates/pages/login/login.html").getContentAsString(StandardCharsets.UTF_8);
        assertThat(login).contains("data-encode-password", "name=\"username\"", "name=\"password\"");
    }
}
