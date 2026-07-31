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

import org.ihtsdo.refsetservice.handler.SecurityServiceHandler;

/**
 * Query/session inputs for {@link SecurityServiceHandler#completeBrowserLogin(BrowserLoginCallback)}.
 */
public final class BrowserLoginCallback {

    /** Authorization code from the identity provider (may be blank when {@link #error} is set). */
    private final String code;

    /** Identity-provider error code, if authorization failed. */
    private final String error;

    /** Optional human-readable error detail from the identity provider. */
    private final String errorDescription;

    /** OAuth2 {@code state} from the callback query. */
    private final String state;

    /** Expected OAuth2 {@code state} from the HTTP session (may be null). */
    private final String expectedState;

    /**
     * Instantiates a new browser login callback.
     *
     * @param code the authorization code
     * @param error the IdP error code
     * @param errorDescription the IdP error description
     * @param state the callback state
     * @param expectedState the session state
     */
    public BrowserLoginCallback(final String code, final String error, final String errorDescription, final String state, final String expectedState) {

        this.code = code;
        this.error = error;
        this.errorDescription = errorDescription;
        this.state = state;
        this.expectedState = expectedState;
    }

    /**
     * Returns the authorization code.
     *
     * @return the code
     */
    public String getCode() {

        return code;
    }

    /**
     * Returns the IdP error code.
     *
     * @return the error
     */
    public String getError() {

        return error;
    }

    /**
     * Returns the IdP error description.
     *
     * @return the error description
     */
    public String getErrorDescription() {

        return errorDescription;
    }

    /**
     * Returns the callback state.
     *
     * @return the state
     */
    public String getState() {

        return state;
    }

    /**
     * Returns the expected session state.
     *
     * @return the expected state
     */
    public String getExpectedState() {

        return expectedState;
    }
}
