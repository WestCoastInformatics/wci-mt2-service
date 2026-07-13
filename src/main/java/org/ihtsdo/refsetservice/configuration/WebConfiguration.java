/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.UrlPathHelper;

/**
 * Set up the web settings.
 */
@Configuration
public class WebConfiguration implements WebMvcConfigurer {

    /** The Constant LOG. */
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(WebConfiguration.class);

    @Value("${cors.allowed-origins:*}")
    private String[] allowedOrigins;

    /* see superclass */
    @Override
    public void addCorsMappings(final CorsRegistry registry) {

        LOG.info("allowedOrigins: {}", (Object[]) allowedOrigins);

        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders(HttpHeaders.CONTENT_DISPOSITION, HttpHeaders.CONTENT_TYPE, HttpHeaders.CONTENT_LENGTH)
                .allowCredentials(true)
                .maxAge(3600);
    }

    /* see superclass */
    @Override
    public void addViewControllers(final ViewControllerRegistry registry) {

        // Legacy Springfox path used by older bookmarks and docs.
        registry.addRedirectViewController("/swagger", "/swagger-ui/index.html");
        registry.addRedirectViewController("/swagger/", "/swagger-ui/index.html");
        registry.addRedirectViewController("/swagger/index.html", "/swagger-ui/index.html");
    }

    /* see superclass */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {

        UrlPathHelper urlPathHelper = new UrlPathHelper();
        // Don't decode the URL
        urlPathHelper.setUrlDecode(false);
        urlPathHelper.setRemoveSemicolonContent(false);
        configurer.setUrlPathHelper(urlPathHelper);
    }
}
