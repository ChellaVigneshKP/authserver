package com.chellavignesh.authserver.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Set;

public class StaticResourceSessionFilter implements Filter {
    private static final Logger logger = LoggerFactory.getLogger(StaticResourceSessionFilter.class);

    private static final Set<String> STATIC_PATHS = Set.of("/favicon.ico", "/css/", "/js/", "/images/", "/robots.tx", "/static/");

    private static final Set<String> STATIC_EXTENSIONS = Set.of(".ico", ".css", ".js", ".png", ".jpg", ".jpeg", ".gif", ".svg", ".txt", ".xml", ".json", ".html", ".woff2", ".woff", ".eot", ".ttf");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String requestURI = httpRequest.getRequestURI();
        if (isStaticResource(requestURI)) {
            logger.debug("Static resource request detected: {}. Skipping session creation.", requestURI);
            HttpServletRequest wrappedRequest = new NoSessionHttpServletRequest(httpRequest);
            chain.doFilter(wrappedRequest, response);
        } else {
            chain.doFilter(request, response);
        }
    }

    private boolean isStaticResource(String requestURI) {
        for (String path : STATIC_PATHS) {
            if (requestURI.startsWith(path) || requestURI.equals(path.replace("/", ""))) {
                return true;
            }
        }

        for (String ext : STATIC_EXTENSIONS) {
            if (requestURI.endsWith(ext)) {
                return true;
            }
        }

        return false;
    }

    private static class NoSessionHttpServletRequest extends HttpServletRequestWrapper {
        public NoSessionHttpServletRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public HttpSession getSession() {
            return null;
        }

        @Override
        public HttpSession getSession(boolean create) {
            return null;
        }

        @Override
        public String getRequestedSessionId() {
            return null;
        }

        @Override
        public boolean isRequestedSessionIdValid() {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromCookie() {
            return false;
        }

        @Override
        public boolean isRequestedSessionIdFromURL() {
            return false;
        }
    }
}