/*
 * Copyright 2022 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest.client;
/*
 * Copyright 2022 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration to Atlassian's Crowd API.
 *
 */
public class CrowdAPIClient extends CrowdClientAbstract {

    /** The logger. */
    private static final Logger logger = LoggerFactory.getLogger(CrowdAPIClient.class);

    /** Group name prefix for RT2 application. */
    private static final String appPrefix = "rt2-";

    // USER
    /** Get user GET. */
    private static final String GET_USER = "/rest/usermanagement/1/user";

    /** Get avatar for user EXPERIMENTAL GET. */
    private static final String GET_AVATAR_FOR_USER = "/rest/usermanagement/1/user/avatar?username=";

    /** Get direct groups GET. */
    private static final String GET_DIRECT_GROUPS = "/rest/usermanagement/1/user/group/direct?username=";

    // GROUP
    /** Get group GET. */
    private static final String GET_GROUP = "/rest/usermanagement/1/group?groupname=";

    /** Add group POST. */
    private static final String ADD_GROUP = "/rest/usermanagement/1/group";

    // MEMBERSHIP
    /** Add a user to a group. */
    private static final String ADD_USER_TO_GROUP = "/rest/usermanagement/1/group/user/direct?groupname=";

    /** Remove user from group DELETE. */
    private static final String REMOVE_USER_FROM_GROUP = "/rest/usermanagement/1/user/group/direct";

    /**
     * Add all groups with roles e.g. rt2-no-abc-author. - rt2 is the application - no is the two letter code for the organization (country) - abc is the acronym of the group
     * name - author is the role (admin, author, reviewer and viewer are the others)
     *
     * @param organization the organization
     * @param projectName the project name
     * @param projectDescription the project description
     * @throws Exception the exception
     */
    public static void addGroup(final String organization, final String projectName, final String projectDescription) throws Exception {

        logger.info("Add group {} to organization {} with description of {}", projectName, organization, projectDescription);

        if (StringUtils.isBlank(organization)) {
            throw new Exception("Organization name cannot be empty or null. Received organization: " + organization);
        }

        if (StringUtils.isEmpty(projectName)) {
            throw new Exception("Project name cannot be empty or null. Received project: " + projectName);
        }

        final String description = (!StringUtils.isEmpty(projectDescription)) ? projectDescription.trim() : projectName.trim();

        /* {"name": "rt2-test-test-author", "description": "test crowd client", "type": "GROUP" } */
        for (String role : ROLES) {

            final String groupName = CrowdGroupNameAlgorithm.generateName(organization, projectName, role);

            logger.info("CALL CROWD API url:" + BASE_URL + ADD_GROUP);
            final String entity = "{\"name\": \"" + groupName + "\", \"description\": \"" + description + "\", \"type\": \"GROUP\" }";

            logger.info("CALL CROWD API payload: " + entity);
            final Response response = post(BASE_URL + ADD_GROUP, entity);

            // 201 Returned if the group is successfully created.
            // 400 Returned if the group already exists.
            // 403 Returned if the application is not allowed to create a new group.
            if (response.getStatus() == 201) {
                // expected 201 status, error occurred.
                logger.info("Added group {}", groupName);
            } else if (response.getStatus() == 400) {
                // ignore 400 and continue?
                logger.error("The group " + groupName + " already exists");
                throw new Exception("The group " + groupName + " already exists");
            } else if (response.getStatus() == 403) {
                logger.error("The group " + groupName + " could not be created. Not allowed.");
                throw new Exception("The group " + groupName + " could not be created. Not allowed.");
            } else {
                logger.error("The group " + groupName + " could not be created. Received HTTP " + response.getStatus() + " from the API server.");
                throw new Exception("The group " + groupName + " could not be created. Received HTTP " + response.getStatus() + " from the API server.");
            }
        }
    }

    /**
     * Get URL to a user's avatar.
     * 
     * @param username The user's username.
     * @return String - URL for user's avatar.
     * @throws Exception the exception.
     */
    public static String getUserAvatar(String username) throws Exception {

        logger.debug("Get avatar for username {}", username);
        if (StringUtils.isEmpty(username)) {
            throw new Exception("User name cannot be empty or null. Received username: " + username);
        }

        final Response response = get(String.format(BASE_URL + GET_AVATAR_FOR_USER + username));

        // 303 - The uri for the user's avatar (in the location header)
        // 404 - The user doesn't exist, or doesn't have an avatar defined
        if (response.getStatus() == 303) {
            logger.debug("Found avatar for username {}", username);
            return response.getHeaderString("location");
        } else if (response.getStatus() == 404) {
            logger.debug("Did not find avatar for username {}", username);
            return null;
        } else {
            logger.debug("Did NOT find avatar for username {}", username);
            return "Did NOT find avatar for username " + username;
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

        logger.debug("Get memberships for user {}", username);
        if (StringUtils.isEmpty(username)) {
            throw new Exception("User name cannot be empty or null. Received username: " + username);
        }

        final Set<String> userGroups = new HashSet<>();
        final Response response = get(BASE_URL + GET_DIRECT_GROUPS + username);

        // 200 OK.
        // 404 the user could not be found or the user is not a direct member of the specified group.
        if (response.getStatus() == 200) {

            final String jsonString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(jsonString);
            final JsonNode groups = root.get("groups");
            if (groups != null && !groups.isEmpty()) {
                groups.forEach(groupName -> {
                    final String name = groupName.findValue("name").asText();
                    if (name.startsWith(appPrefix)) {
                        userGroups.add(name);
                    }
                });
            }
            return userGroups;
        } else if (response.getStatus() == 400) {
            throw new Exception("The user " + username.trim() + " could not be found or the user is not a member of a group.");
        } else {
            throw new Exception("The user " + username.trim() + " could not be found or the user is not a member of a group. Received HTTP " + response.getStatus() + " from the API server.");
        }
    }

    /**
     * Get list of all users in a group.
     *
     * @param groupname The name of the group.
     * @return the memberships for group
     * @throws Exception the exception
     */
    public static Set<String> getMembershipsForGroup(String groupname) throws Exception {

        if (StringUtils.isBlank(groupname)) {
            throw new Exception("Group name cannot be empty or null. Received groupname: " + groupname);
        }

        final Set<String> users = new HashSet<>();

        final Response response = get(BASE_URL);

        return users;

    }

    /**
     * Add a user to a group.
     * 
     * @param groupname Name of the group from which the user membership will be added.
     * @param username Name of the user to have their membership added.
     * @throws Exception the exception.
     */
    public static void addMembership(final String groupname, final String username) throws Exception {

        logger.info("Add user {} to group {}", username, groupname);
        if (StringUtils.isBlank(groupname)) {
            throw new Exception("Group name cannot be empty or null. Received groupname: " + groupname);
        }
        if (StringUtils.isEmpty(username)) {
            throw new Exception("User name cannot be empty or null. Received username: " + username);
        }

        final String body = "{ \"name\":\"" + username.trim() + "\" }";
        final Response response = post(BASE_URL + ADD_USER_TO_GROUP + groupname.trim(), body);

        // 201 Returned if the user is successfully added as a member of the group.
        // 400 Returned if the user could not be found or groupName is not specified or user has no name.
        // 404 Returned if the group could not be found.
        // 409 Returned if the user is already a direct member of the group.
        if (response.getStatus() == 201) {
            // return true or something?
        } else if (response.getStatus() == 400) {
            throw new Exception("Failed to add " + username.trim() + " to group " + groupname.trim() + ". " + "User could not be found or groupName is not specified or user has no name.");
        } else if (response.getStatus() == 404) {
            throw new Exception("Failed to add username " + username.trim() + " to group " + groupname.trim() + ". Group could not be found.");
        } else if (response.getStatus() == 409) {
            throw new Exception("Failed to add username " + username.trim() + " to group " + groupname.trim() + ". User is already a direct member of the group.");
        } else {
            throw new Exception("Failed to add username " + username.trim() + " to group " + groupname.trim() + ". Received HTTP " + response.getStatus() + " from the API server.");
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

        logger.info("Remove user {} from group {}", username, groupname);
        if (StringUtils.isBlank(groupname)) {
            throw new Exception("Group name cannot be empty or null. Received groupname: " + groupname);
        }
        if (StringUtils.isEmpty(username)) {
            throw new Exception("User name cannot be empty or null. Received username: " + username);
        }

        final Response response = delete(BASE_URL + REMOVE_USER_FROM_GROUP + "?groupname=" + groupname.trim() + "&username=" + username.trim());

        // 204 Returned if the user membership is successfully deleted.
        // 404 Returned if the user or group could not be found.
        if (response.getStatus() == 204) {
            // return true or something?
        } else if (response.getStatus() == 404) {
            //throw new Exception("Failed to remove username " + username.trim() + " from group " + groupname.trim() + ". Group could not be found.");
            logger.info("Failed to remove username " + username.trim() + " from group " + groupname.trim() + ". Group could not be found.");
        } else {
            throw new Exception("Failed to remove username " + username.trim() + " from group " + groupname.trim() + ". Received HTTP " + response.getStatus() + " from the API server.");
        }
    }

    /**
     * Application entry point.
     *
     * @param args the command line arguments
     * @throws Exception the exception
     */
    /* for testing */
    public static void main(String[] args) throws Exception {

        Set<String> memberships = getMembershipsForUser("nmarques");
        System.out.println("Memberships ->" + memberships);

        getUserAvatar("nmarques");

    }
}