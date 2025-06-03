/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.handler;

import org.ihtsdo.refsetservice.model.Configurable;
import org.ihtsdo.refsetservice.model.User;

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
     * Returns the logout url.
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
}
