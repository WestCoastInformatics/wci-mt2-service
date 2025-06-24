/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

import org.apache.commons.io.IOUtils;
import org.ihtsdo.refsetservice.model.ApplicationMetadata;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/**
 * The Class SystemController.
 */
@RestController
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
public class SystemController extends BaseController {

    /** The Constant LOG. */
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(SystemController.class);

    /**
     * Gets the metadata.
     *
     * @param request the request
     * @return the metadata
     * @throws Exception the exception
     */
    @RequestMapping(method = RequestMethod.GET, value = "/metadata", produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Get application metadata.", tags = {
        "application metadata"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information"),
        @ApiResponse(responseCode = "404", description = "Resource not found")
    })
    public @ResponseBody ResponseEntity<ApplicationMetadata> getMetadata(final HttpServletRequest request) throws Exception {

        try {
            final ApplicationMetadata applicationMetadata = ModelUtility
                .fromJson(IOUtils.toString(getClass().getClassLoader().getResourceAsStream("ApplicationMetadata.json"), "UTF-8"), ApplicationMetadata.class);

            return ResponseEntity.ok(applicationMetadata);

        } catch (final Exception e) {

            handleException(e);
            return null;
        }

    }

}
