/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice;

import javax.persistence.PersistenceException;

import org.ihtsdo.refsetservice.handler.snowstorm.SnowstormConcept;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.util.UrlPathHelper;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Entry point for webapp.
 */
@SpringBootApplication(exclude = {
    FlywayAutoConfiguration.class, ElasticsearchRestClientAutoConfiguration.class
})
@EnableScheduling
@EnableAsync
@OpenAPIDefinition(
    info = @Info(title = "SNOMED Mapping Service API", version = "1.0.0", description = "Endponts for accessing and interacting with the refset service."),
    tags = {
        @Tag(name = "artifact", description = "Artifact service endpoints"), @Tag(name = "audit", description = "Audit service endpoints"),
        @Tag(name = "concept", description = "Concept service endpoints"), @Tag(name = "discussion", description = "Discussion service endpoints"),
        @Tag(name = "edition", description = "Edition service endpoints"), @Tag(name = "invite", description = "Invite/Request service endpoints"),
        @Tag(name = "mapset", description = "Mapping service endpoints"), @Tag(name = "organization", description = "Organization service endpoints"),
        @Tag(name = "project", description = "Project service endpoints"), @Tag(name = "refset", description = "Refset service endpoints"),
        @Tag(name = "security", description = "Security/auth service endpoints"), @Tag(name = "team", description = "Team service endpoints"),
    }, servers = {
        @Server(description = "Current Instance", url = "/refsetservice")
    })
public class Application extends SpringBootServletInitializer {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(Application.class);

    /**
     * Configure.
     *
     * @param application the application
     * @return the spring application builder
     */
    @Override
    protected SpringApplicationBuilder configure(final SpringApplicationBuilder application) {

        setSystemProperties();

        // TODO: I don't think this ever gets called..
        LOG.debug("************ Configure method called");
        return application.sources(Application.class);
    }

    /**
     * Application entry point.
     *
     * @param args the command line arguments
     * @throws Exception the exception
     */
    @SuppressWarnings("resource")
    public static void main(final String[] args) throws Exception {

        try {
            setSystemProperties();

            SpringApplication.run(Application.class, args);

            try (final TerminologyService service = new TerminologyService()) {
                // just kicking off the lucene reindexing

                // also delete user sessions on application startup
                service.clearUserSessions();

                // prewarm the cache
                // SnowstormConcept.findConcepts("SNOMEDCT-NO", "2024-04-15");
                SnowstormConcept.cacheConcepts("ICD-10-NO", "20240723");

            }

        } catch (final PersistenceException e) {

            LOG.error("Elasticsearch error", e);
            System.exit(1);
        }

        LOG.debug("MAPPING SERVICE MAIN APPLICATION START");

        // Removed. Method did nothing but log.
        // RefsetMemberService.cacheAllMemberAncestors() was commented out.
        // init();
    }

    /**
     * Sets the system properties.
     */
    private static void setSystemProperties() {

        System.setProperty("org.apache.tomcat.util.buf.UDecoder.ALLOW_ENCODED_SLASH", "true");
        System.setProperty("org.apache.catalina.connector.CoyoteAdapter.ALLOW_ENCODED_SLASH", "true");
    }

    // /**
    // * Initialize the application once it is started.
    // *
    // * @throws Exception the exception
    // */
    // private static void init() throws Exception {
    //
    // // don't run this method during tests
    // if
    // (!PropertyUtility.getProperty("springProfiles").toLowerCase().contains("test"))
    // {
    // // RefsetMemberService.cacheAllMemberAncestors();
    // } else {
    // LOG.debug("Not caching all members during tests.");
    // }
    // }
}
