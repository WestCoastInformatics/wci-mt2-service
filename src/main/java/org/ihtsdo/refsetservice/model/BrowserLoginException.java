/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model;

/**
 * Expected browser-login failure mapped to an {@code auth_error=...} redirect query value.
 */
public class BrowserLoginException extends Exception {

    /** Serial id. */
    private static final long serialVersionUID = 1L;

    /** Stable error code for the UI redirect (e.g. {@code state_mismatch}, {@code token_exchange}). */
    private final String errorCode;

    /**
     * Instantiates a new browser login exception.
     *
     * @param errorCode the redirect error code (must not be blank)
     */
    public BrowserLoginException(final String errorCode) {

        super(errorCode);
        this.errorCode = errorCode;
    }

    /**
     * Instantiates a new browser login exception with cause.
     *
     * @param errorCode the redirect error code
     * @param cause the cause
     */
    public BrowserLoginException(final String errorCode, final Throwable cause) {

        super(errorCode, cause);
        this.errorCode = errorCode;
    }

    /**
     * Returns the redirect error code.
     *
     * @return the error code
     */
    public String getErrorCode() {

        return errorCode;
    }
}
