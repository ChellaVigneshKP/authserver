package com.chellavignesh.authserver.adminportal.hosted;

import static org.assertj.core.api.Assertions.*;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

class HostedAccountBodyLimitFilterTest {
    MockHttpServletRequest request(byte[] bytes, String type) {
        var r = new MockHttpServletRequest("POST", "/portal/account/profile");
        r.setServletPath("/portal/account/profile");
        r.setContentType(type);
        r.setContent(bytes);
        return r;
    }

    @Test
    void rejectsPercentEncodedInvalidUtf8() throws Exception {
        var request = new MockHttpServletRequest("POST", "/portal/account/profile");
        request.setServletPath("/portal/account/profile");
        request.setContentType("application/x-www-form-urlencoded");
        request.setContent("firstName=%FF".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();
        new HostedAccountBodyLimitFilter()
                .doFilter(
                        request,
                        response,
                        (req, res) ->
                                ((jakarta.servlet.http.HttpServletResponse) res).setStatus(204));
        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    void oversizeAndChunkedBodiesStopBeforeSecurityBinding() throws Exception {
        for (boolean chunked : new boolean[] {false, true}) {
            var r =
                    chunked
                            ? new MockHttpServletRequest("POST", "/portal/account/profile") {
                                @Override
                                public long getContentLengthLong() {
                                    return -1;
                                }
                            }
                            : request(new byte[65537], "application/x-www-form-urlencoded");
            r.setServletPath("/portal/account/profile");
            r.setContentType("application/x-www-form-urlencoded");
            r.setContent(new byte[65537]);
            var response = new MockHttpServletResponse();
            var reached = new AtomicBoolean();
            new HostedAccountBodyLimitFilter()
                    .doFilter(r, response, (req, res) -> reached.set(true));
            assertThat(response.getStatus()).isEqualTo(413);
            assertThat(reached.get()).isFalse();
        }
    }

    @Test
    void rejectsWrongTypeInvalidEncodingAndDuplicateFields() throws Exception {
        for (var r :
                new MockHttpServletRequest[] {
                    request("{}".getBytes(), "application/json"),
                    request("firstName=%ZZ".getBytes(), "application/x-www-form-urlencoded"),
                    request("nonce=a&nonce=b".getBytes(), "application/x-www-form-urlencoded")
                }) {
            var response = new MockHttpServletResponse();
            var reached = new AtomicBoolean();
            new HostedAccountBodyLimitFilter()
                    .doFilter(r, response, (req, res) -> reached.set(true));
            assertThat(response.getStatus()).isIn(400, 415);
            assertThat(reached.get()).isFalse();
        }
    }

    @Test
    void exposesBoundedUtf8FormToCsrfAndLeavesOtherRoutesUnchanged() throws Exception {
        var r =
                request(
                        "_csrf=proof&firstName=Jos%C3%A9".getBytes(StandardCharsets.UTF_8),
                        "application/x-www-form-urlencoded;charset=UTF-8");
        var response = new MockHttpServletResponse();
        new HostedAccountBodyLimitFilter()
                .doFilter(
                        r,
                        response,
                        (req, res) -> {
                            assertThat(((HttpServletRequest) req).getParameter("_csrf"))
                                    .isEqualTo("proof");
                            assertThat(((HttpServletRequest) req).getParameter("firstName"))
                                    .isEqualTo("José");
                        });
        r.setServletPath("/api/v1/users");
        r.setContentType("application/json");
        r.setContent(new byte[65537]);
        var reached = new AtomicBoolean();
        new HostedAccountBodyLimitFilter()
                .doFilter(r, new MockHttpServletResponse(), (req, res) -> reached.set(true));
        assertThat(reached.get()).isTrue();
    }

    @Test
    void fieldLengthsAreBoundedBeforeBinding() throws Exception {
        var response = new MockHttpServletResponse();
        var reached = new AtomicBoolean();
        new HostedAccountBodyLimitFilter()
                .doFilter(
                        request(
                                ("email=" + "a".repeat(256)).getBytes(),
                                "application/x-www-form-urlencoded"),
                        response,
                        (req, res) -> reached.set(true));
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(reached.get()).isFalse();
    }
}
