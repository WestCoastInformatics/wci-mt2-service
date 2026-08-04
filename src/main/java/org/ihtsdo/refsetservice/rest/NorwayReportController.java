/*
 * Copyright 2026 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.app.RecordMetric;
import org.ihtsdo.refsetservice.terminologyservice.norway.NorwayReplacementReportDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * REST endpoints to kick off Norway replacement reports (email results asynchronously).
 *
 * <p>
 * UI contract:
 * <ul>
 * <li>{@code POST /report/norway/replacement-map} — start map report; returns 202</li>
 * <li>{@code POST /report/norway/replacement-translation} — start translation report; returns 202</li>
 * </ul>
 * Both require an authenticated session. Recipients come from env-backed properties (not committed):
 * {@code NORWAY_REPLACEMENT_MAP_REPORT_USERS} /
 * {@code NORWAY_REPLACEMENT_TRANSLATION_REPORT_USERS}. Response body is JSON
 * {@code { "status": "STARTED", "report": "..." }}. Concurrent duplicate starts return 409.
 */
@RestController
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class NorwayReportController extends BaseController {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(NorwayReportController.class);

    /** The request. */
    @Autowired
    private HttpServletRequest request;

    /**
     * Starts the Norway replacement map report asynchronously and emails the zip when complete.
     *
     * @return 202 Accepted
     * @throws Exception the exception
     */
    @PostMapping(value = "/report/norway/replacement-map", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Start Norway replacement map report (async; emails zip when done).", tags = {
        "report"
    }, responses = {
        @ApiResponse(responseCode = "202", description = "Report started"), @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "409", description = "Report already running"),
        @ApiResponse(responseCode = "417", description = "Recipients not configured")
    })
    @RecordMetric
    public ResponseEntity<Map<String, String>> startReplacementMapReport() throws Exception {

        try {
            authorizeUser(request);
            LOG.info("Starting Norway replacement map report");
            NorwayReplacementReportDispatcher.startMapReport();
            return accepted("replacement-map");
        } catch (final Exception e) {
            handleException(e);
            throw e;
        }
    }

    /**
     * Starts the Norway replacement translation report asynchronously and emails the zip when complete.
     *
     * @return 202 Accepted
     * @throws Exception the exception
     */
    @PostMapping(value = "/report/norway/replacement-translation", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Start Norway replacement translation report (async; emails zip when done).", tags = {
        "report"
    }, responses = {
        @ApiResponse(responseCode = "202", description = "Report started"), @ApiResponse(responseCode = "401", description = "Unauthorized"),
        @ApiResponse(responseCode = "409", description = "Report already running"),
        @ApiResponse(responseCode = "417", description = "Recipients not configured")
    })
    @RecordMetric
    public ResponseEntity<Map<String, String>> startReplacementTranslationReport() throws Exception {

        try {
            authorizeUser(request);
            LOG.info("Starting Norway replacement translation report");
            NorwayReplacementReportDispatcher.startTranslationReport();
            return accepted("replacement-translation");
        } catch (final Exception e) {
            handleException(e);
            throw e;
        }
    }

    /**
     * Builds a 202 response body.
     *
     * @param report the report key
     * @return response entity
     */
    private static ResponseEntity<Map<String, String>> accepted(final String report) {

        final Map<String, String> body = new HashMap<>();
        body.put("status", "STARTED");
        body.put("report", report);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }
}
