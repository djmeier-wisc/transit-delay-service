package com.doug.projects.transitdelayservice.config;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;

import java.util.Arrays;

@Configuration
public class CorsConfiguration {

    private static final String ALLOWED_HEADERS =
            "x-requested-with, authorization, Content-Type, Authorization, credential, X-XSRF-TOKEN";
    private static final String ALLOWED_METHODS = "GET, PUT, POST, DELETE, OPTIONS";
    private static final String[] ALLOWED_ORIGIN =
            new String[]{"my-precious-time.com", "localhost", "dougmeier.dev"};
    private static final String MAX_AGE = "3600";

    @Bean
    public Filter corsFilter() {
        return (req, res, chain) -> {

            HttpServletRequest request = (HttpServletRequest) req;
            HttpServletResponse response = (HttpServletResponse) res;

            String requestOrigin = request.getHeader("Origin");

            // Your logic: Check if origin matches any in the ALLOWED_ORIGIN list
            if (requestOrigin != null && isAllowed(requestOrigin)) {
                response.setHeader("Access-Control-Allow-Origin", requestOrigin);
                response.setHeader("Access-Control-Allow-Methods", ALLOWED_METHODS);
                response.setHeader("Access-Control-Max-Age", MAX_AGE);
                response.setHeader("Access-Control-Allow-Headers", ALLOWED_HEADERS);
            }

            // Handle Preflight
            if (HttpMethod.OPTIONS.name().equalsIgnoreCase(request.getMethod())) {
                response.setStatus(HttpServletResponse.SC_OK);
                return;
            }

            chain.doFilter(req, res);
        };
    }

    private boolean isAllowed(String origin) {
        return Arrays.stream(ALLOWED_ORIGIN).anyMatch(origin::contains);
    }
}