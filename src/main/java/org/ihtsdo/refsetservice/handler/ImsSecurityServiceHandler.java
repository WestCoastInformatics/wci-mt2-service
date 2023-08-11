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

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.servlet.http.Cookie;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.rest.client.CrowdAPIClient;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Implements a security handler that authorizes via IHTSDO authentication.
 */
public class ImsSecurityServiceHandler implements SecurityServiceHandler {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(ImsSecurityServiceHandler.class);

    /** The Constant LOG. */
    private static final String RT2_ROLE_PREFIX = "rt2-";
    
    /** TODO - REMOVE AFTER PERMISSIONS CONVERTED. */
    private static final Set<String> PERMISSION_CONVERT_ADDED_GROUPS = new HashSet<>();
    private static int PERMISSION_CONVERT_NUMBER_USERS_CONVERTED = 0;
    private static int PERMISSION_CONVERT_NUMBER_USERS_PREVIOUSLY_CONVERTED = 0;
    private static int PERMISSION_CONVERT_NUMBER_MEMBERSHIPS_ADDED = 0;
    private static int PERMISSION_CONVERT_NUMBER_MEMBERSHIPS_REMOVED = 0;
    private static int PERMISSION_CONVERT_NUMBER_MEMBERSHIPS_REMOVED_WITHOUT_ADD = 0;

    /**  The properties. */
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
        
        // TODO - REMOVE AFTER PERMISSIONS CONVERTED
        convertRoles(user, false);
        // END

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
     * TODO - REMOVE AFTER PERMISSIONS CONVERTED.
     *
     * @param user The user
     * @return the response
     * @throws Exception the exception
     */
    public String convertRolesForAllUsers() throws Exception {
        
        PERMISSION_CONVERT_ADDED_GROUPS.clear();
        PERMISSION_CONVERT_NUMBER_USERS_CONVERTED = 0;
        PERMISSION_CONVERT_NUMBER_USERS_PREVIOUSLY_CONVERTED = 0;
        PERMISSION_CONVERT_NUMBER_MEMBERSHIPS_ADDED = 0;
        PERMISSION_CONVERT_NUMBER_MEMBERSHIPS_REMOVED = 0;
        PERMISSION_CONVERT_NUMBER_MEMBERSHIPS_REMOVED_WITHOUT_ADD = 0;
        
        final String baseUrl = StringUtils.trim(PropertyUtility.getProperty("crowd.baseUrl"));
        final String crowdUsername = StringUtils.trim(PropertyUtility.getProperty("crowd.username"));
        final String password = StringUtils.trim(PropertyUtility.getProperty("crowd.password"));
        final String auth = crowdUsername + ":" + password;
        final byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(StandardCharsets.UTF_8));
        final String authHeader = "Basic " + new String(encodedAuth, StandardCharsets.UTF_8);
        final String appPrefix = "rt2-";
        final Set<String> usernames = new HashSet<>();
        //final Set<String> groups = CrowdAPIClient.getAllGroups();
        
        final HttpClient httpClient = HttpClient.newBuilder().build();
        final HttpRequest request = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/rest/usermanagement/1/group/membership")).GET()
            .header("Authorization", authHeader).header("Accept", MediaType.APPLICATION_XML).build();
        final HttpResponse<String> response = httpClient.send(request, BodyHandlers.ofString());

        if (response.statusCode() == 200) {

            final String xmlString = response.body();
            
            try (final ByteArrayInputStream input = new ByteArrayInputStream(xmlString.toString().getBytes("UTF-8"));) {

                // Load the input XML document, parse it and return an instance of the Document class.
                final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                final DocumentBuilder builder = factory.newDocumentBuilder();

                final Document document = builder.parse(input);

                final NodeList groupList = document.getDocumentElement().getChildNodes();
                final int groupListSize = groupList.getLength();

                // look thru membership groups
                for (int i = 0; i < groupListSize; i++) {

                    final Node groupNode = groupList.item(i);

                    if (groupNode.getNodeType() == Node.ELEMENT_NODE) {

                        // Get the value of the group name attribute.
                        final String groupName = groupNode.getAttributes().getNamedItem("group").getNodeValue();

                        // if the group is an RT2 group
                        if (groupName.startsWith(appPrefix)) {
                            
                            final NodeList usersNodeList = groupNode.getChildNodes();
                            final int usersNodeListSize = usersNodeList.getLength();
                            
                            // get the users node
                            for (int j = 0; j < usersNodeListSize; j++) {
                                
                                if (usersNodeList.item(j).getNodeName().equals("users")) {
                                    
                                    final NodeList groupUsersList = usersNodeList.item(j).getChildNodes();
                                    final int groupUsersListSize = groupUsersList.getLength();
                                    
                                    // loop through the users and collect the user names
                                    for (int k = 0; k < groupUsersListSize; k++) {
        
                                        if (groupUsersList.item(k).getNodeName().equals("user")) {
                                            
                                            final Node groupUserNode = groupUsersList.item(k);
                                            final String groupUserName = groupUserNode.getAttributes().getNamedItem("name").getNodeValue();
                                            usernames.add(groupUserName);
                                        }
                                    }
                                }
                            }
                        }

                    } else {
                        groupNode.getNodeType();
                    }
                }
            }
            
            // go thru all users and convert their roles
            for (final String username : usernames) {
                
                final User user = new User();
                user.setUserName(username);
                
                final Set<String> groupMemberships = CrowdAPIClient.getMembershipsForUser(username);
                
                for (final String role : groupMemberships) {
                    if (role.startsWith(RT2_ROLE_PREFIX)) {
                        user.getRoles().add(role.substring(RT2_ROLE_PREFIX.length()));
                    }
                }
                
                convertRoles(user, true);
            }
            
            LOG.info("PERMISSION_CONVERT_ADDED_GROUPS: " + PERMISSION_CONVERT_ADDED_GROUPS.size());
            LOG.info("PERMISSION_CONVERT_NUMBER_USERS_CONVERTED: " + PERMISSION_CONVERT_NUMBER_USERS_CONVERTED);
            LOG.info("PERMISSION_CONVERT_NUMBER_USERS_PREVIOUSLY_CONVERTED: " + PERMISSION_CONVERT_NUMBER_USERS_PREVIOUSLY_CONVERTED);
            LOG.info("PERMISSION_CONVERT_NUMBER_MEMBERSHIPS_ADDED: " + PERMISSION_CONVERT_NUMBER_MEMBERSHIPS_ADDED);
            LOG.info("PERMISSION_CONVERT_NUMBER_MEMBERSHIPS_REMOVED: " + PERMISSION_CONVERT_NUMBER_MEMBERSHIPS_REMOVED);
            LOG.info("PERMISSION_CONVERT_NUMBER_MEMBERSHIPS_REMOVED_WITHOUT_ADD: " + PERMISSION_CONVERT_NUMBER_MEMBERSHIPS_REMOVED_WITHOUT_ADD);
            
            final List<String> results = new ArrayList<>();
            results.add("PERMISSION_CONVERT_ADDED_GROUPS: " + PERMISSION_CONVERT_ADDED_GROUPS.size());
            results.add("PERMISSION_CONVERT_NUMBER_USERS_CONVERTED: " + PERMISSION_CONVERT_NUMBER_USERS_CONVERTED);
            results.add("PERMISSION_CONVERT_NUMBER_USERS_PREVIOUSLY_CONVERTED: " + PERMISSION_CONVERT_NUMBER_USERS_PREVIOUSLY_CONVERTED);
            results.add("PERMISSION_CONVERT_NUMBER_MEMBERSHIPS_ADDED: " + PERMISSION_CONVERT_NUMBER_MEMBERSHIPS_ADDED);
            results.add("PERMISSION_CONVERT_NUMBER_MEMBERSHIPS_REMOVED: " + PERMISSION_CONVERT_NUMBER_MEMBERSHIPS_REMOVED);
            results.add("PERMISSION_CONVERT_NUMBER_MEMBERSHIPS_REMOVED_WITHOUT_ADD: " + PERMISSION_CONVERT_NUMBER_MEMBERSHIPS_REMOVED_WITHOUT_ADD);
            
            return ModelUtility.toJson(results);
        } else {
            throw new Exception("There was a problem: " + response.statusCode());
        }
    }
        
        /**
         * TODO - REMOVE AFTER PERMISSIONS CONVERTED.
         *
         * @param user The user
         * @param removeOldMembeships should old permission style memberships be removed
         * @throws Exception the exception
         */
        private void convertRoles(final User user, final boolean removeOldMembeships) throws Exception {

        boolean needToConvert = true;
            
        for (final String role : user.getRoles()) {

            if (role.split("-").length == 4) {
                
                PERMISSION_CONVERT_NUMBER_USERS_PREVIOUSLY_CONVERTED++;
                needToConvert = false;
                break;
            }
        }
        
        if (needToConvert) {
            
            PERMISSION_CONVERT_NUMBER_USERS_CONVERTED++;
            LOG.info("PERMISSION CLEANUP - Converting user: " + user.getUserName());
        }
        
        try (final TerminologyService service = new TerminologyService()) {
            
            final Set<String> originalRoles = new HashSet<>(user.getRoles());
            
            for (final String role : originalRoles) {

                final String[] originalRoleParts = role.split("-");
                
                if (originalRoleParts.length < 4) {
                    
                    // convert old roles into the new role format
                    if (needToConvert) {
                        
                        String organizationName = "";
                        String groupDescription = "Organization Administrators";
                        
                        // if this is an application admin role the org name is 'all'
                        if (originalRoleParts[0].equals("all")) {
                            
                            organizationName = "all";    
                            groupDescription = "Application Administrators";
                            
                        } else {
                            
                            String reconsitutedEditionShortName = originalRoleParts[0];
                            
                            // turn the edition part of the role back to a valid edition short name so it can be searched
                            if (reconsitutedEditionShortName.length() > 8) {
                                reconsitutedEditionShortName = "snomedct" + "-" + reconsitutedEditionShortName.substring(8);
                            }
                            
                            final ResultList<Edition> results = service.find("shortName:" + reconsitutedEditionShortName.toUpperCase(), new PfsParameter(), Edition.class, null);
                            
                            if (results.getItems().size() == 1) {
                                
                                final Edition edition = results.getItems().get(0);
                                organizationName = edition.getOrganizationName();
                            }
                        }
                        
                        // add the group and permission to crowd as long as an org name is there
                        if (!organizationName.isEmpty()) {
                            
                            // if this is a project role get the project description
                            if (!originalRoleParts[1].equals("all")) {
                                
                                final Project project = service.findSingle("projectCrowdId:" + originalRoleParts[1], Project.class, null);
                                
                                if (project != null) {
                                    groupDescription = project.getDescription();
                                }
                            }
                            
                            final String crowdOrganizationName = CrowdGroupNameAlgorithm.getOrganizationString(organizationName);
                            final String newGroupName = crowdOrganizationName + "-" + originalRoleParts[0] + "-" + originalRoleParts[1];
                            
                            if (!PERMISSION_CONVERT_ADDED_GROUPS.contains(newGroupName)) {
                                
                                LOG.info("    PERMISSION CLEANUP - Adding Group: rt2-" + crowdOrganizationName + "-" + originalRoleParts[0] + "-" + originalRoleParts[1]);
                                CrowdAPIClient.addGroup(organizationName, originalRoleParts[0], originalRoleParts[1], groupDescription, false, false);
                                PERMISSION_CONVERT_ADDED_GROUPS.add(newGroupName);
                            }
                            
                            final String groupName = CrowdGroupNameAlgorithm.buildCrowdGroupName(organizationName, originalRoleParts[0], originalRoleParts[1], originalRoleParts[2]);
                            LOG.info("    PERMISSION CLEANUP - Adding membership: " + groupName);
                            CrowdAPIClient.addMembership(groupName, user.getUserName());
                            PERMISSION_CONVERT_NUMBER_MEMBERSHIPS_ADDED++;
                            
                            // add the new role to the user object
                            user.getRoles().add(crowdOrganizationName + "-" + role);
                            
                        } else {
                            PERMISSION_CONVERT_NUMBER_MEMBERSHIPS_REMOVED_WITHOUT_ADD++;
                        }
                        
                        // if we are removing old memberships do it now
                        if (removeOldMembeships) {
                            
                            LOG.info("    PERMISSION CLEANUP - removing membership: rt2-" + role);
                            CrowdAPIClient.deleteMembership("rt2-" + role, user.getUserName());
                            PERMISSION_CONVERT_NUMBER_MEMBERSHIPS_REMOVED++;
                        }
                    }
                    
                    // remove the old role string from the user object
                    user.getRoles().remove(role);
                }
            }
        }
        
        LOG.info("PERMISSION CLEANUP - **** USER: " + user.getUserName() + " NUMBER ROLES: " + user.getRoles().size());
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
