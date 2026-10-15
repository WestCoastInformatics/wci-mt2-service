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

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.servlet.http.Cookie;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.rest.client.CrowdAPIClient;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ThreadLocalMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.JsonNode;

/**
 * Implements a security handler that authorizes via IHTSDO authentication.
 */
public class ImsSecurityServiceHandler implements SecurityServiceHandler {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(ImsSecurityServiceHandler.class);

    /** Shared HTTP client for IMS account checks. */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

    /** The Constant LOG. */
    private static final String RT2_ROLE_PREFIX = "rt2-";

    /** The properties. */
    private static Properties properties;

    /**
     * Authenticate.
     *
     * @param userName the user name
     * @return the user
     * @throws Exception the exception
     */
    /* see superclass */
    @Override
    public User authenticate(final String userName) throws Exception {

        final boolean authenticated = checkImsLogin(userName);

        if (userName == null || !authenticated) {
            throw new RestException(false, 401, "Unauthorized", "This user is not authenticated with IMS.");
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

        final Map<String, Set<String>> configUsers = getDefaultUsersFromConfigFile();

        final Set<String> rolesToAdd = new HashSet<>();
        if (configUsers.containsKey("admin") && configUsers.get("admin").contains(user.getUserName())) {
            rolesToAdd.add("all-all-all-admin");
        }

        if (configUsers.containsKey("author") && configUsers.get("author").contains(user.getUserName())) {
            rolesToAdd.add("all-all-all-author");
        }

        if (configUsers.containsKey("reviewer") && configUsers.get("reviewer").contains(user.getUserName())) {
            rolesToAdd.add("all-all-all-reviewer");
        }

        if (!rolesToAdd.isEmpty()) {
            user.getRoles().clear();
            user.getRoles().add("all-all-all-reviewer");
        }

        user.setModifiedBy(user.getUserName());

        LOG.debug("authenticate user is: " + user);
        return user;
    }

    /**
     * Returns the admin users from config file.
     *
     * @return the admin users from config file
     */
    private Map<String, Set<String>> getDefaultUsersFromConfigFile() {

        final Map<String, Set<String>> userList = new HashMap<>();

        if (!properties.containsKey("users.admin")) {
            LOG.warn("Could not retrieve config parameter users.admin for security handler IMS");
        } else {
            final String adminUserList = properties.getProperty("users.admin");
            if (StringUtils.isNotBlank(adminUserList)) {
                final Set<String> admins = new HashSet<>(Arrays.asList(adminUserList.split(",")));
                userList.put("admin", admins);
            }
        }

        if (!properties.containsKey("users.author")) {
            LOG.warn("Could not retrieve config parameter users.author for security handler IMS");
        } else {
            final String authorUserList = properties.getProperty("users.author");
            if (StringUtils.isNotBlank(authorUserList)) {
                final Set<String> authors = new HashSet<>(Arrays.asList(authorUserList.split(",")));
                userList.put("author", authors);
            }
        }

        if (!properties.containsKey("users.reviewer")) {
            LOG.warn("Could not retrieve config parameter users.reviewer for security handler IMS");
        } else {
            final String reviewerUserList = properties.getProperty("users.reviewer");
            if (StringUtils.isNotBlank(reviewerUserList)) {
                final Set<String> reviewers = new HashSet<>(Arrays.asList(reviewerUserList.split(",")));
                userList.put("reviewer", reviewers);
            }
        }

        return userList;
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

        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).header("Accept", "application/json")
            .header("Cookie", imsCookie.getName() + "=" + imsCookie.getValue()).GET().build();

        try {
            final HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {

                final String resultString = response.body();
                final JsonNode root = ThreadLocalMapper.get().readTree(resultString);
                final String imsUserName = root.get("login").asString();

                // make sure that the passed in user name is the same as what IMS has
                // authenticated
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

    /**
     * Timeout user.
     *
     * @param user the user
     * @return true, if successful
     */
    /* see superclass */
    @Override
    public boolean timeoutUser(final String user) {

        // Never timeout user
        return false;
    }

    /**
     * Compute token for user.
     *
     * @param user the user
     * @return the string
     */
    /* see superclass */
    @Override
    public String computeTokenForUser(final String user) {

        return user;
    }

    /**
     * Sets the properties.
     *
     * @param properties the new properties
     */
    /* see superclass */
    @Override
    public void setProperties(final Properties properties) {

        this.properties = properties;
    }

    /**
     * Gets the name.
     *
     * @return the name
     */
    /* see superclass */
    @Override
    public String getName() {

        return "IHTSDO Identity Management Service handler";
    }

    /**
     * Gets the authenticate url.
     *
     * @return the authenticate url
     * @throws Exception the exception
     */
    /* see superclass */
    @Override
    public String getAuthenticateUrl() throws Exception {

        return PropertyUtility.getProperty("security.handler.IMS.url");
    }

    /**
     * Builds the browser login url.
     *
     * @param oauthState the oauth state
     * @return the string
     * @throws Exception the exception
     */
    /* see superclass */
    @Override
    public String buildBrowserLoginUrl(final String oauthState) throws Exception {

        final String logoutUrl = getLogoutUrl();
        if (StringUtils.isBlank(logoutUrl)) {
            return null;
        }
        String serviceReferer = PropertyUtility.getProperty("app.url.root");
        if (StringUtils.isBlank(serviceReferer) || "none".equalsIgnoreCase(serviceReferer)) {
            serviceReferer = "/";
        }
        final String refererParam = "serviceReferer=" + URLEncoder.encode(serviceReferer, StandardCharsets.UTF_8).replace("+", "%20");
        if (logoutUrl.contains("#/logout")) {
            return logoutUrl.replace("#/logout/", "#/login?" + refererParam).replace("#/logout", "#/login?" + refererParam);
        }
        return logoutUrl;
    }

    /**
     * Gets the post login redirect uri.
     *
     * @return the post login redirect uri
     * @throws Exception the exception
     */
    /* see superclass */
    @Override
    public String getPostLoginRedirectUri() throws Exception {

        String url = PropertyUtility.getProperty("app.url.root");
        if (StringUtils.isBlank(url) || "none".equalsIgnoreCase(url)) {
            return "/";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * Gets the logout url.
     *
     * @return the logout url
     * @throws Exception the exception
     */
    /* see superclass */
    @Override
    public String getLogoutUrl() throws Exception {

        return PropertyUtility.getProperty("security.handler.IMS.url.logout");
    }

    /**
     * Gets the system admin user names.
     *
     * @return the system admin user names
     * @throws Exception the exception
     */
    /* see superclass */
    @Override
    public Set<String> getSystemAdminUserNames() throws Exception {

        final String propertyValue = PropertyUtility.getProperty("security.handler.IMS.users.admin");

        return Stream.of(propertyValue.trim().split("\\s*,\\s*")).collect(Collectors.toSet());
    }

    /**
     * Gets the system author user names.
     *
     * @return the system author user names
     * @throws Exception the exception
     */
    /* see superclass */
    @Override
    public Set<String> getSystemAuthorUserNames() throws Exception {

        final String propertyValue = PropertyUtility.getProperty("security.handler.IMS.users.author");

        return Stream.of(propertyValue.trim().split("\\s*,\\s*")).collect(Collectors.toSet());
    }

    /**
     * Gets the system reviewer user names.
     *
     * @return the system reviewer user names
     * @throws Exception the exception
     */
    /* see superclass */
    @Override
    public Set<String> getSystemReviewerUserNames() throws Exception {

        final String propertyValue = PropertyUtility.getProperty("security.handler.IMS.users.reviewer");

        return Stream.of(propertyValue.trim().split("\\s*,\\s*")).collect(Collectors.toSet());
    }

}
