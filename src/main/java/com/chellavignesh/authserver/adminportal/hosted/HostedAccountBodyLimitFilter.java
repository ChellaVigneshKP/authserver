package com.chellavignesh.authserver.adminportal.hosted;

import jakarta.servlet.*;
import jakarta.servlet.http.*;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

/**
 * Parse bounded additive forms before Spring CSRF/Tomcat parameter binding; all other routes pass
 * through.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class HostedAccountBodyLimitFilter extends OncePerRequestFilter {
    private static final int LIMIT = 65536;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod())
                || !Set.of("/portal/account/profile", "/portal/account/password")
                        .contains(request.getServletPath());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
        if (request.getQueryString() != null) {
            reject(response, 400);
            return;
        }
        String type = request.getContentType();
        if (type == null
                || !type.toLowerCase(Locale.ROOT)
                        .matches("application/x-www-form-urlencoded(?:;\\s*charset=utf-8)?")) {
            reject(response, 415);
            return;
        }
        if (request.getContentLengthLong() > LIMIT) {
            reject(response, 413);
            return;
        }
        byte[] body = request.getInputStream().readNBytes(LIMIT + 1);
        if (body.length > LIMIT) {
            reject(response, 413);
            return;
        }
        Map<String, String[]> parameters = new LinkedHashMap<>();
        try {
            String text =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(body))
                            .toString();
            for (String field : text.split("&", -1)) {
                String[] pair = field.split("=", 2);
                if (pair.length != 2 || parameters.size() >= 20)
                    throw new IllegalArgumentException();
                String key = decode(pair[0]), value = decode(pair[1]);
                int maximum =
                        switch (key) {
                            case "firstName", "lastName", "email", "confirmEmail" -> 255;
                            case "phoneNumber", "secondaryPhoneNumber" -> 128;
                            case "nonce" -> 43;
                            case "clientId" -> 256;
                            case "expectedLoginId" -> 36;
                            case "returnTo" -> 2048;
                            case "x-request-datetime" -> 64;
                            case "_csrf" -> 512;
                            case "currentPassword", "newPassword", "confirmPassword" -> 4096;
                            default -> 0;
                        };
                if (key.isEmpty()
                        || key.length() > 64
                        || value.length() > maximum
                        || maximum == 0
                        || parameters.putIfAbsent(key, new String[] {value}) != null
                        || value.indexOf('\0') >= 0) throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException | CharacterCodingException invalid) {
            reject(response, 400);
            return;
        }
        chain.doFilter(
                new HttpServletRequestWrapper(request) {
                    @Override
                    public String getParameter(String name) {
                        var values = parameters.get(name);
                        return values == null ? null : values[0];
                    }

                    @Override
                    public String[] getParameterValues(String name) {
                        var values = parameters.get(name);
                        return values == null ? null : values.clone();
                    }

                    @Override
                    public Enumeration<String> getParameterNames() {
                        return Collections.enumeration(parameters.keySet());
                    }

                    @Override
                    public Map<String, String[]> getParameterMap() {
                        var copy = new LinkedHashMap<String, String[]>();
                        parameters.forEach((k, v) -> copy.put(k, v.clone()));
                        return Collections.unmodifiableMap(copy);
                    }

                    @Override
                    public ServletInputStream getInputStream() {
                        var bytes = new ByteArrayInputStream(body);
                        return new ServletInputStream() {
                            public boolean isFinished() {
                                return bytes.available() == 0;
                            }

                            public boolean isReady() {
                                return true;
                            }

                            public void setReadListener(ReadListener listener) {
                                throw new UnsupportedOperationException();
                            }

                            public int read() {
                                return bytes.read();
                            }
                        };
                    }

                    @Override
                    public BufferedReader getReader() {
                        return new BufferedReader(
                                new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
                    }
                },
                response);
    }

    private static String decode(String wire) throws CharacterCodingException {
        byte[] encoded = wire.getBytes(StandardCharsets.UTF_8);
        var bytes = new ByteArrayOutputStream(encoded.length);
        for (int index = 0; index < encoded.length; index++) {
            int value = Byte.toUnsignedInt(encoded[index]);
            if (value == '%') {
                if (index + 2 >= encoded.length) throw new IllegalArgumentException();
                int high = Character.digit((char) encoded[++index], 16),
                        low = Character.digit((char) encoded[++index], 16);
                if (high < 0 || low < 0) throw new IllegalArgumentException();
                bytes.write((high << 4) | low);
            } else bytes.write(value == '+' ? ' ' : value);
        }
        return StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes.toByteArray()))
                .toString();
    }

    private static void reject(HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("text/plain;charset=UTF-8");
        response.getWriter()
                .write(
                        status == 413
                                ? "This form exceeds the 64 KiB limit."
                                : "This form request is invalid. Return to your account and start"
                                      + " again.");
    }
}
