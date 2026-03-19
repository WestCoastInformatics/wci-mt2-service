/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.MapSetService;
import org.ihtsdo.refsetservice.util.CachingUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * Cache endpoints.
 */
@RestController
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
@Tag(name = "cache", description = "Cache management endpoints")
public class CacheController extends BaseController {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(CacheController.class);

    /** Prevent multiple concurrent concept cache refreshes. */
    private static final Object CONCEPTS_CACHE_REFRESH_LOCK = new Object();

    private static final String SNOWSTORM_CONCEPTS_CACHE = "snowstorm_concepts";
    private static final String SNOWSTORM_CONCEPT_REF_CACHE = "snowstorm_concept_refs";
    private static final String SNOWSTORM_TERMINOLOGY_CACHE = "snowstorm_terminology";

    @PostMapping(value = "/cache/concepts/refresh")
    @Operation(summary = "Force refresh Snowstorm concepts cache", tags = { "cache" }, responses = {
        @ApiResponse(responseCode = "200", description = "Concept caches were cleared and rebuilt"),
        @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "500", description = "Unexpected error refreshing caches") })
    public ResponseEntity<Map<String, Object>> refreshConceptsCache(final HttpServletRequest request) throws Exception {

        final long startedAt = System.currentTimeMillis();

        try {
            final User authUser = authorizeUser(request);
            if (authUser == null || !authUser.checkPermission(User.ROLE_ADMIN, "all", null, null)) {
                throw new RestException(false, HttpStatus.UNAUTHORIZED, "Unauthorized", "Insufficient permissions");
            }

            final Map<String, Object> response = new HashMap<>();

            synchronized (CONCEPTS_CACHE_REFRESH_LOCK) {
                LOG.info("Refreshing Snowstorm concept caches (clearing then prewarming active map set concepts)");

                CachingUtility.clearCaches(SNOWSTORM_CONCEPTS_CACHE, SNOWSTORM_CONCEPT_REF_CACHE, SNOWSTORM_TERMINOLOGY_CACHE);

                try (final TerminologyService service = new TerminologyService()) {
                    final Map<String, String> terminologyToVersion = MapSetService.getTerminologyVersionsFromMapSets(service);
                    response.put("terminologyToVersion", terminologyToVersion);

                    if (terminologyToVersion != null && !terminologyToVersion.isEmpty()) {
                        MapSetService.cacheConceptsForActiveMapSets(service);
                    } else {
                        response.put("message", "No active map sets found; caches cleared but not prewarmed");
                    }
                }
            }

            response.put("status", "completed");
            response.put("durationMs", System.currentTimeMillis() - startedAt);
            return ResponseEntity.ok(response);
        } catch (final Exception e) {
            handleException(e);
            return null;
        }
    }
}

