/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest.client;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.MediaType;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration to Atlassian's Crowd API.
 *
 */
public class CrowdAPIClient extends CrowdClientAbstract {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(CrowdAPIClient.class);

    /** Group name prefix for RT2 application. */
    private static final String APP_PREFIX = "rt2-";

    // USER
    /** Get user GET. */
    private static final String GET_USER = "/rest/usermanagement/1/user";

    /** Find user by email GET. */
    private static final String FIND_USER = "/rest/usermanagement/1/search?entity-type=user&restriction=email=";

    /** Get avatar for user EXPERIMENTAL GET. */
    private static final String GET_AVATAR_FOR_USER = "/rest/usermanagement/1/user/avatar?username=";

    /** Get direct groups GET. */
    private static final String GET_DIRECT_GROUPS = "/rest/usermanagement/1/user/group/direct?username=";

    // GROUP
    // /** Get group GET. */
    // private static final String GET_GROUP = "/rest/usermanagement/1/group?groupname=";

    /** Add group POST. */
    private static final String ADD_GROUP = "/rest/usermanagement/1/group";

    // MEMBERSHIP
    /** Add a user to a group. */
    private static final String ADD_USER_TO_GROUP = "/rest/usermanagement/1/group/user/direct?groupname=";

    /** Remove user from group DELETE. */
    private static final String REMOVE_USER_FROM_GROUP = "/rest/usermanagement/1/user/group/direct";

    /**
     * Returns the user from Crowd.
     *
     * @param userName the user name
     * @return the user
     * @throws Exception the exception
     */
    public static User getUser(final String userName) throws Exception {

        LOG.debug("Get information for user {}", userName);
        if (StringUtils.isEmpty(userName)) {
            throw new Exception("User name cannot be empty or null. Received username: " + userName);
        }

        final HttpClient httpClient = HttpClient.newBuilder().build();
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create(getBaseUrl() + GET_USER + "?username=" + userName)).GET()
            .header("Accept", MediaType.APPLICATION_JSON).header("Authorization", getBasicAuthHeader()).build();
        final HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

        // 200 OK.
        // 404 the user could not be found.
        if (response.statusCode() == 200) {

            final String jsonString = response.body();
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(jsonString);

            final User user = new User();
            user.setName(root.get("display-name").asText());
            user.setEmail(root.get("email").asText());
            user.setUserName(userName);

            return user;

        } else if (response.statusCode() == 400) {
            throw new Exception("The user " + userName + " could not be found.");
        } else {
            throw new Exception("The user " + userName + " could not be found. Received HTTP " + response.statusCode() + " from the API server.");
        }

        /*
         * final User user = new User(); try (final Response response = get(baseUrl + GET_USER + "?username=" + userName);) {
         * 
         * // 200 OK. // 404 the user could not be found. if (response.getStatus() == 200) {
         * 
         * final String jsonString = response.readEntity(String.class); final ObjectMapper mapper = new ObjectMapper(); final JsonNode root =
         * mapper.readTree(jsonString);
         * 
         * user.setName(root.get("display-name").asText()); user.setEmail(root.get("email").asText()); user.setUserName(userName);
         * 
         * return user;
         * 
         * } else if (response.getStatus() == 400) { throw new Exception("The user " + userName + " could not be found."); } else { throw new
         * Exception("The user " + userName + " could not be found. Received HTTP " + response.getStatus() + " from the API server."); } }
         */

    }

    /**
     * Add all groups with roles e.g. rt2-no-abc-author. - rt2 is the application - no is the two letter code for the organization (country) - abc is the
     * acronym of the group name - author is the role (admin, author, reviewer and viewer are the others)
     *
     * @param organization the organization
     * @param projectName the project name
     * @param projectDescription the project description
     * @param generateProjectName the generate project name
     * @param adminOnly to add the all-admin permission for organization administrators
     * @throws Exception the exception
     */
    public static void addGroup(final String organization, final String projectName, final String projectDescription, final boolean generateProjectName,
        final boolean adminOnly) throws Exception {

        LOG.info("Add group {} to organization {} with description of {}", projectName, organization, projectDescription);

        if (StringUtils.isBlank(organization)) {
            throw new Exception("Organization name cannot be empty or null. Received organization: " + organization);
        }

        if (StringUtils.isEmpty(projectName)) {
            throw new Exception("Project name cannot be empty or null. Received project: " + projectName);
        }

        final String description = (!StringUtils.isEmpty(projectDescription)) ? projectDescription.trim() : projectName.trim();

        /*
         * {"name": "rt2-test-all-author", "description": "test crowd client", "type": "GROUP" }
         */
        final Set<String> rolesToAdd = new HashSet<>();
        if (adminOnly) {
            rolesToAdd.add("admin");
        } else {
            rolesToAdd.addAll(ROLES);
        }

        for (final String role : rolesToAdd) {

            final String groupName = generateProjectName ? CrowdGroupNameAlgorithm.generateCrowdGroupName(organization, projectName, role)
                : CrowdGroupNameAlgorithm.buildCrowdGroupName(organization, projectName, role);

            LOG.info("CALL CROWD API url:" + getBaseUrl() + ADD_GROUP);
            final String entity = "{\"name\": \"" + groupName + "\", \"description\": \"" + description + "\", \"type\": \"GROUP\" }";

            LOG.info("CALL CROWD API payload: " + entity);
            final int statusCode = post(getBaseUrl() + ADD_GROUP, entity);

            // 201 Returned if the group is successfully created.
            // 400 Returned if the group already exists.
            // 403 Returned if the application is not allowed to create a new group.
            if (statusCode == 201) {

                // expected 201 status, error occurred.
                LOG.info("Added group {}.", groupName);

            } else if (statusCode == 400) {

                LOG.info("Group already exists {}.", groupName);

            } else if (statusCode == 403) {

                LOG.error("The group " + groupName + " could not be created. Not allowed.");
                throw new Exception("The group " + groupName + " could not be created. Not allowed.");

            } else {
                LOG.error("The group " + groupName + " could not be created. Received HTTP " + statusCode + " from the API server.");
                throw new Exception("The group " + groupName + " could not be created. Received HTTP " + statusCode + " from the API server.");
            }
        }
    }

    /**
     * Add admin group for an organization.
     *
     * @param organization the organization
     * @param description the description
     * @return the string
     * @throws Exception the exception
     */
    public static String addAdminGroup(final String organization, final String description) throws Exception {

        LOG.info("Add group {} to organization {} with description of {}", "all", organization, description);

        if (StringUtils.isBlank(organization)) {
            throw new Exception("Organization name cannot be empty or null. Received organization: " + organization);
        }

        /*
         * {"name": "rt2-test-all-admin", "description": "admin for organization", "type": "GROUP" }
         */
        final String groupName = CrowdGroupNameAlgorithm.generateCrowdGroupName(organization, "all", "admin", true);

        LOG.info("CALL CROWD API url:" + getBaseUrl() + ADD_GROUP);
        final String entity = "{\"name\": \"" + groupName + "\", \"description\": \"" + description + "\", \"type\": \"GROUP\" }";

        LOG.info("CALL CROWD API payload: " + entity);
        final int statusCode = post(getBaseUrl() + ADD_GROUP, entity);

        // 201 Returned if the group is successfully created.
        // 400 Returned if the group already exists.
        // 403 Returned if the application is not allowed to create a new group.
        if (statusCode == 201) {

            // expected 201 status, error occurred.
            LOG.info("Added group {}", groupName);
            return groupName;

        }

        if (statusCode == 400) {

            // ignore 400 and continue?
            LOG.error("The group " + groupName + " already exists");
            // throw new Exception("The group " + groupName + " already exists");
            return groupName;
        }

        if (statusCode == 403) {

            LOG.error("The group " + groupName + " could not be created. Not allowed.");
            throw new Exception("The group " + groupName + " could not be created. Not allowed.");

        }

        LOG.error("The group " + groupName + " could not be created. Received HTTP " + statusCode + " from the API server.");
        throw new Exception("The group " + groupName + " could not be created. Received HTTP " + statusCode + " from the API server.");

    }

    /**
     * Get URL to a user's avatar.
     * 
     * @param username The user's username.
     * @return String - URL for user's avatar.
     * @throws Exception the exception.
     */
    public static String getUserAvatar(final String username) throws Exception {

        LOG.debug("Get avatar for username {}", username);
        if (StringUtils.isBlank(username)) {
            throw new Exception("User name cannot be empty or null. Received username: " + username);
        }
        final String url = getBaseUrl() + GET_AVATAR_FOR_USER + username;
        final HttpClient httpClient = HttpClient.newBuilder().build();
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().header("Accept", MediaType.APPLICATION_JSON)
            .header("Authorization", getBasicAuthHeader()).build();

        LOG.debug("CROWD API GET Url: {}", url);

        final HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

        // 303 - The uri for the user's avatar (in the location header)
        // 404 - The user doesn't exist, or doesn't have an avatar defined
        if (response.statusCode() == 303) {

            LOG.debug("Found avatar for username {}", username);
            return response.headers().firstValue("location").orElse("");

        } else if (response.statusCode() == 404) {

            LOG.debug("Did not find avatar for username {}", username);
            return null;

        } else {

            LOG.debug("Did not find avatar for username {}", username);
            return "Did not find avatar for username " + username;

        }

    }

    /**
     * Get list of user's group memberships.
     *
     * @param username the username
     * @return Set<String> List of user's groups.
     * @throws Exception the exception.
     */
    public static Set<String> getMembershipsForUser(final String username) throws Exception {

        LOG.debug("Get memberships for user {}", username);
        if (StringUtils.isBlank(username)) {
            throw new Exception("User name cannot be empty or null. Received username: " + username);
        }

        final Set<String> userGroups = new HashSet<>();
        final String jsonString = get(getBaseUrl() + GET_DIRECT_GROUPS + username);

        // 200 OK.
        // 404 the user could not be found or the user is not a direct member of the
        // specified group.

        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode root = mapper.readTree(jsonString);
        final JsonNode groups = root.get("groups");
        if (groups != null && !groups.isEmpty()) {
            groups.forEach(groupName -> {
                final String name = groupName.findValue("name").asText();
                if (name.startsWith(APP_PREFIX)) {
                    userGroups.add(name);
                }
            });
        }
        return userGroups;
        // } else if (response.getStatus() == 400) {
        // throw new Exception("The user " + username.trim() + " could not be found or the user is not a member of a group.");
        // } else {
        // throw new Exception("The user " + username.trim() + " could not be found or the user is not a member of a group. Received HTTP "
        // + response.getStatus() + " from the API server.");
        // }
        // }
    }

    /**
     * Get list of all users in a group.
     *
     * @param groupname The name of the group.
     * @return the memberships for group
     * @throws Exception the exception
     */
    public static Set<String> getMembershipsForGroup(final String groupname) throws Exception {

        if (StringUtils.isBlank(groupname)) {
            throw new Exception("Group name cannot be empty or null. Received groupname: " + groupname);
        }

        final Set<String> users = new HashSet<>();

        // try (final Response response = get(baseUrl);) {
        return users;
        // }

    }

    /**
     * Add a user to a group.
     * 
     * @param groupname Name of the group from which the user membership will be added.
     * @param username Name of the user to have their membership added.
     * @throws Exception the exception.
     */
    public static void addMembership(final String groupname, final String username) throws Exception {

        LOG.info("Add user {} to group {}", username, groupname);
        if (StringUtils.isBlank(groupname)) {
            throw new Exception("Group name cannot be empty or null. Received groupname: " + groupname);
        }
        if (StringUtils.isBlank(username)) {
            throw new Exception("User name cannot be empty or null. Received username: " + username);
        }

        final String body = "{ \"name\":\"" + username.trim() + "\" }";
        final int statusCode = post(getBaseUrl() + ADD_USER_TO_GROUP + groupname.trim(), body);

        // 201 Returned if the user is successfully added as a member of the group.
        // 400 Returned if the user could not be found or groupName is not specified or
        // user has no name.
        // 404 Returned if the group could not be found.
        // 409 Returned if the user is already a direct member of the group.
        if (statusCode == 201) {

            // was previously added.
            LOG.info("User {} already is a member of {}.", username, groupname);

        } else if (statusCode == 400) {

            throw new Exception("Failed to add " + username.trim() + " to group " + groupname.trim() + ". "
                + "User could not be found or groupName is not specified or user has no name.");

        } else if (statusCode == 404) {

            throw new Exception("Failed to add username " + username.trim() + " to group " + groupname.trim() + ". Group could not be found.");

        } else if (statusCode == 409) {

            // throw new Exception("Failed to add username " + username.trim() + " to group
            // " + groupname.trim() + ". User is already a direct member of the group.");
            LOG.warn("Failed to add username " + username.trim() + " to group " + groupname.trim() + ". User is already a direct member of the group.");

        } else {
            throw new Exception(
                "Failed to add username " + username.trim() + " to group " + groupname.trim() + ". Received HTTP " + statusCode + " from the API server.");
        }
    }

    /**
     * Remove user's membership from a group.
     * 
     * @param groupname Name of the group from which the user membership will be removed.
     * @param username Name of the user to have their membership removed.
     * @throws Exception the exception
     */
    public static void deleteMembership(final String groupname, final String username) throws Exception {

        LOG.info("Remove user {} from group {}", username, groupname);
        if (StringUtils.isBlank(groupname)) {
            throw new Exception("Group name cannot be empty or null. Received groupname: " + groupname);
        }
        if (StringUtils.isBlank(username)) {
            throw new Exception("User name cannot be empty or null. Received username: " + username);
        }
        final int statusCode = delete(getBaseUrl() + REMOVE_USER_FROM_GROUP + "?groupname=" + groupname.trim() + "&username=" + username.trim());

        // 204 Returned if the user membership is successfully deleted.
        // 404 Returned if the user or group could not be found.
        if (statusCode == 204) {

            LOG.info("User {} removed from group {}.", username, groupname);

        } else if (statusCode == 404) {

            // throw new Exception("Failed to remove username " + username.trim() + " from
            // group " + groupname.trim() + ". Group could not be found.");
            LOG.info("Failed to remove username " + username.trim() + " from group " + groupname.trim() + ". Group could not be found.");

        } else {

            throw new Exception(
                "Failed to remove username " + username.trim() + " from group " + groupname.trim() + ". Received HTTP " + statusCode + " from the API server.");

        }
    }

    /**
     * Find user by email.
     *
     * @param email the email
     * @return the user
     * @throws Exception the exception
     */
    public static User findUserByEmail(final String email) throws Exception {

        LOG.info("Find user by email: {} ", email);

        if (StringUtils.isBlank(email)) {
            LOG.warn("Email is blank or empty. Received email: " + email);
            return null;
        }

        final String jsonString = get(getBaseUrl() + FIND_USER + urlEncode(email));
        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode root = mapper.readTree(jsonString);
        final JsonNode users = root.get("users");
        if (users == null || (users.isArray() && users.isEmpty())) {
            throw new RestException(false, HttpStatus.NOT_FOUND, "Not found", "Could not find user with email of " + email + ".");
        }
        if (users.isArray() && users.size() > 1) {
            throw new RestException(false, HttpStatus.CONFLICT, "Found multiple",
                "Found multiple users with email of " + email + ". Can't determine which user to create.");
        }

        final String name = users.get(0).findValue("name").asText();
        return (StringUtils.isNotBlank(name)) ? getUser(name) : null;

    }
}
