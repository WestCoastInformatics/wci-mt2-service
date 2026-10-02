/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest;

import java.time.Instant;

import jakarta.ws.rs.core.MediaType;

import org.ihtsdo.refsetservice.model.ApplicationVersionInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * The Class VersionController.
 */
@RestController
@RequestMapping(value = "/", produces = MediaType.APPLICATION_JSON)
@Tag(name = "version", description = "Build version endpoints")
public class VersionController {

    /** The build properties. */
    @Autowired(required = false)
    private BuildProperties buildProperties;

    /**
     * Returns the builds the information.
     *
     * @return the builds the information
     */
    @RequestMapping(value = "/version", method = RequestMethod.GET, produces = MediaType.APPLICATION_JSON)
    @Operation(summary = "Software build version and timestamp.", tags = {
        "version"
    }, responses = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved the requested information")
    })
    public @ResponseBody ResponseEntity<ApplicationVersionInfo> getBuildInformation() {

        if (buildProperties == null) {
            throw new IllegalStateException("Build properties are not present.");
        }

        final Instant buildTime = buildProperties.getTime();
        if (buildTime == null) {
            throw new IllegalStateException("Build time is not present.");
        }
        return new ResponseEntity<>(new ApplicationVersionInfo(buildProperties.getVersion(), buildTime.toString()), HttpStatus.OK);
    }
}
