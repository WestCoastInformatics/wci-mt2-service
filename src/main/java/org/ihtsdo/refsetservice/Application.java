/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice;

import javax.persistence.PersistenceException;

import org.ihtsdo.refsetservice.service.TerminologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Entry point for webapp.
 */
@SpringBootApplication(exclude = { FlywayAutoConfiguration.class })
@EnableCaching
@EnableScheduling
@EnableAsync
@OpenAPIDefinition(info = @Info(title = "SNOMED Refset Service API", version = "1.0.0", 
description = "Endponts for accessing and interacting with the refset service."), tags = {
		@Tag(name = "artifact", description = "Artifact service endpoints"),
		@Tag(name = "audit", description = "Audit service endpoints"),
		@Tag(name = "discussion", description = "Discussion service endpoints"),
		@Tag(name = "edition", description = "Edition service endpoints"),
		@Tag(name = "invite", description = "Invite/Request service endpoints"),
		@Tag(name = "organization", description = "Organization service endpoints"),
		@Tag(name = "project", description = "Project service endpoints"),
		@Tag(name = "refset", description = "Refset service endpoints"),
		@Tag(name = "security", description = "Security/auth service endpoints"),
		@Tag(name = "Team", description = "Team service endpoints"),
		@Tag(name = "usr", description = "User service endpoints") }, servers = {
				@Server(description = "Current Instance", url = "/") })
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

			SpringApplication.run(Application.class, args);

			try (final TerminologyService service = new TerminologyService()) {
				// just kicking off the lucene reindexing

				// also delete user sessions on application startup
				service.clearUserSessions();

			}

		} catch (final PersistenceException e) {

			LOG.error("Elasticsearch error", e);
			System.exit(1);
		}

		LOG.debug("REFSET SERVICE MAIN APPLICATION START");

		// Removed. Method did nothing but log.
		// RefsetMemberService.cacheAllMemberAncestors() was commented out.
		// init();
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
