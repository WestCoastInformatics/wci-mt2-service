/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.handler;

import java.util.Set;

import org.ihtsdo.refsetservice.model.Configurable;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.BrowserLoginCallback;
import org.ihtsdo.refsetservice.model.BrowserLoginException;

/**
 * Generically represents a handler that can authenticate a user.
 */
public interface SecurityServiceHandler extends Configurable {

    /**
     * Authenticate.
     *
     * @param user the user
     * @return the user
     * @throws Exception the exception
     */
    public User authenticate(String user) throws Exception;

    /**
     * Returns the authenticate url.
     *
     * @return the authenticate url
     * @throws Exception the exception
     */
    public String getAuthenticateUrl() throws Exception;

    /**
     * Returns the logout url (full browser redirect target for federated logout when configured).
     *
     * @return the logout url
     * @throws Exception the exception
     */
    public String getLogoutUrl() throws Exception;

    /**
     * Indicates whether or not the user should be timed out.
     *
     * @param user the user
     * @return true, if successful
     */
    public boolean timeoutUser(String user);

    /**
     * Computes token for user. For example, a UUID or an MD5 or a counter. Each login requires yields a potentially different token, even for the same user.
     *
     * @param user the user
     * @return the string
     */
    public String computeTokenForUser(String user);

    /**
     * Gets the system admin user names.
     *
     * @return the system admin user names
     * @throws Exception the exception
     */
    public Set<String> getSystemAdminUserNames() throws Exception;

    /**
     * Gets the system author user names.
     *
     * @return the system author user names
     * @throws Exception the exception
     */
    public Set<String> getSystemAuthorUserNames() throws Exception;

    /**
     * Gets the system reviewer user names.
     *
     * @return the system reviewer user names
     * @throws Exception the exception
     */
    public Set<String> getSystemReviewerUserNames() throws Exception;

    /**
     * Whether {@link #buildBrowserLoginUrl(String)} requires a non-blank OAuth {@code state} stored in the HTTP session.
     *
     * @return true when the handler uses OAuth state (e.g. Entra authorization code flow)
     */
    default boolean requiresBrowserLoginState() {

        return false;
    }

    /**
     * Whether {@link #completeBrowserLogin(BrowserLoginCallback)} is supported for {@code GET /authenticate/callback}.
     *
     * @return true when the handler completes a browser OAuth (or similar) callback
     */
    default boolean supportsBrowserCallback() {

        return false;
    }

    /**
     * Builds the full identity-provider URL for {@code GET /authenticate/login}.
     *
     * @param oauthState CSRF state to embed when {@link #requiresBrowserLoginState()} is true; may be null otherwise
     * @return absolute redirect URL
     * @throws Exception if required configuration is missing
     */
    default String buildBrowserLoginUrl(final String oauthState) throws Exception {

        return getAuthenticateUrl();
    }

    /**
     * Base URL for post-login browser redirects after {@code /authenticate/callback} (no trailing slash preferred).
     *
     * @return non-blank URL, or {@code "/"} as last resort
     * @throws Exception if property resolution fails
     */
    default String getPostLoginRedirectUri() throws Exception {

        return "/";
    }

    /**
     * Completes a browser identity-provider callback and returns the IdP-authenticated user (before MT2 {@code authHelper}).
     *
     * @param callback query and session inputs
     * @return authenticated user from the identity provider
     * @throws BrowserLoginException for expected failures mapped to {@code auth_error}
     * @throws Exception for unexpected failures
     */
    default User completeBrowserLogin(final BrowserLoginCallback callback) throws Exception {

        throw new BrowserLoginException("callback_disabled");
    }
}
