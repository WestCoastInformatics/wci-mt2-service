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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.ihtsdo.refsetservice.model.Error;
import org.ihtsdo.refsetservice.model.RestException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for {@link RestException} message exposure and {@link RestExceptionHandler} status mapping.
 */
public class RestExceptionHandlerUnitTest {

    /**
     * RestException constructors put the payload message on the throwable.
     */
    @Test
    public void testRestExceptionGetMessage() {

        final RestException exception = new RestException(false, 401, "Unauthorized", "Unauthorized");
        assertEquals("Unauthorized", exception.getMessage());
        assertEquals(401, exception.getError().getStatus());
    }

    /**
     * Handler returns the nested Error with the intended HTTP status.
     */
    @Test
    public void testHandleRestExceptionReturnsUnauthorized() {

        final RestExceptionHandler handler = new RestExceptionHandler();
        final RestException exception = new RestException(false, 401, "Unauthorized", "Unauthorized");

        final ResponseEntity<Error> response = handler.handleRestException(exception);

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals(401, response.getBody().getStatus());
        assertEquals("Unauthorized", response.getBody().getError());
        assertEquals("Unauthorized", response.getBody().getMessage());
    }

    /**
     * Empty RestException maps to 500 with no body.
     */
    @Test
    public void testHandleRestExceptionNullError() {

        final RestExceptionHandler handler = new RestExceptionHandler();
        final ResponseEntity<Error> response = handler.handleRestException(new RestException());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNull(response.getBody());
    }
}
