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

import org.ihtsdo.refsetservice.model.Error;
import org.ihtsdo.refsetservice.model.RestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.swagger.v3.oas.annotations.Hidden;

/**
 * Maps {@link RestException} to the HTTP status and {@link Error} payload it already carries.
 */
@Hidden
@RestControllerAdvice
public class RestExceptionHandler {

    /**
     * Handle rest exception.
     *
     * @param exception the exception
     * @return the response entity
     */
    @ExceptionHandler(RestException.class)
    public ResponseEntity<Error> handleRestException(final RestException exception) {

        final Error error = exception.getError();
        if (error == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
        final int status = error.getStatus() > 0 ? error.getStatus() : HttpStatus.INTERNAL_SERVER_ERROR.value();
        return ResponseEntity.status(status).body(error);
    }
}
