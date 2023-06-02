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

import java.util.Properties;
import java.util.Set;

import javax.servlet.http.Cookie;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.rest.client.CrowdAPIClient;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Implements a security handler that authorizes via IHTSDO authentication.
 */
public class ImsSecurityServiceHandler implements SecurityServiceHandler {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(DefaultSearchHandler.class);

    /** The Constant LOG. */
    private static final String RT2_ROLE_PREFIX = "rt2-";

    /** The properties. */
    @SuppressWarnings("unused")
    private Properties properties;

    /* see superclass */
    @Override
    public User authenticate(final String userName) throws Exception {

        final boolean authenticated = checkImsLogin(userName);

        if (userName == null || !authenticated) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "This user is not authenticated with IMS.");
        }

        // This is for IMS login
        final User user = CrowdAPIClient.getUser(userName);
        final Set<String> groupMemberships = CrowdAPIClient.getMembershipsForUser(userName);
        LOG.debug("Memberships {}", groupMemberships);

        for (final String role : groupMemberships) {
            if (role.startsWith(RT2_ROLE_PREFIX)) {
                user.getRoles().add(role.substring(RT2_ROLE_PREFIX.length()));
            }
        }

        if (userName.equals("twhalen")) {

            user.getRoles().clear();
            user.getRoles().add("snomedctus-all-viewer");
            user.getRoles().add("snomedctse-inrp-reviewer");
            user.getRoles().add("snomedctse-inrp-author");
        }

        user.setModifiedBy(user.getUserName());

        LOG.debug("authenticate user is: " + user);
        return user;
    }

    /**
     * Calls an IMS endpoint to make sure user is authenticated.
     *
     * @param userName The userName passed in to the authenticate call
     * @return the response
     * @throws Exception the exception
     */
    protected boolean checkImsLogin(final String userName) throws Exception {

        final String url = getAuthenticateUrl() + "account";
        boolean authenticated = false;
        final Cookie imsCookie = SecurityService.getImsCookie();

        if (imsCookie == null) {
            return false;
        }

        final Client client = ClientBuilder.newClient();
        final WebTarget target = client.target(url);
        final javax.ws.rs.core.Cookie newCookie = new javax.ws.rs.core.Cookie(imsCookie.getName(), imsCookie.getValue());

        try (Response response = target.request(MediaType.APPLICATION_JSON).cookie(newCookie).get()) {

            if (response.getStatus() == Response.Status.OK.getStatusCode()) {

                final String resultString = response.readEntity(String.class);
                final ObjectMapper mapper = new ObjectMapper();
                final JsonNode root = mapper.readTree(resultString.toString());
                final String imsUserName = root.get("login").asText();

                // make sure that the passed in user name is the same as what IMS has authenticated
                if (imsUserName.equals(userName)) {
                    authenticated = true;
                }
            }

        } catch (final Exception e) {
            LOG.error("IMS Authentication error: {} ", url, e);
            throw e;
        }

        return authenticated;
    }

    /* see superclass */
    @Override
    public boolean timeoutUser(final String user) {

        // Never timeout user
        return false;
    }

    /* see superclass */
    @Override
    public String computeTokenForUser(final String user) {

        return user;
    }

    /* see superclass */
    @Override
    public void setProperties(final Properties properties) {

        this.properties = properties;
    }

    /* see superclass */
    @Override
    public String getName() {

        return "IHTSDO Identity Management Service handler";
    }

    /* see superclass */
    @Override
    public String getAuthenticateUrl() throws Exception {

        return PropertyUtility.getProperty("security.handler.IMS.url");
    }

    /* see superclass */
    @Override
    public String getLogoutUrl() throws Exception {

        return PropertyUtility.getProperty("security.handler.IMS.url.logout");
    }

}
