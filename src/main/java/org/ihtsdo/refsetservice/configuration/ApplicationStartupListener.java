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

import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.MapSetService;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Runs startup tasks after Spring Boot is ready, using the same classloader as request handling.
 */
@Component
public class ApplicationStartupListener {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(ApplicationStartupListener.class);

    /**
     * Clear sessions and optionally prewarm caches once the application is ready.
     *
     * @throws Exception the exception
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() throws Exception {

        TerminologyService.disableStartupReindex();

        try (final TerminologyService service = new TerminologyService()) {
            service.clearUserSessions();

            final String cacheConceptsEnabled = PropertyUtility.getProperty("cache.terminology.prewarm.enabled");
            final boolean shouldCacheConcepts = cacheConceptsEnabled == null || "true".equalsIgnoreCase(cacheConceptsEnabled);
            if (shouldCacheConcepts) {
                MapSetService.cacheConceptsForActiveMapSets(service);
            } else {
                LOG.info("Concept caching skipped (cache.terminology.prewarm.enabled=false)");
            }
        }
    }
}
