package com.nexusai.platform.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Lets the browser frontend call the REST API cross-origin. Only explicitly configured
 * origins are allowed (no wildcard), and no credentials are shared.
 */
@Configuration
class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    CorsConfig(@Value("${nexusai.cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("Content-Type", "Accept")
                .exposedHeaders("Location")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
