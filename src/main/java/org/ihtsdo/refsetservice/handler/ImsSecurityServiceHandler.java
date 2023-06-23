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

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.servlet.http.Cookie;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.rest.client.CrowdAPIClient;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
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
        
        convertRoles(user);

        if (userName.equals("twhalen")) {

//            user.getRoles().clear();
//            user.getRoles().add("snomedinternational-snomedctus-all-viewer");
//            user.getRoles().add("swedishedition-snomedctse-inrp-reviewer");
//            user.getRoles().add("swedishedition-snomedctse-inrp-author");
        }

        user.setModifiedBy(user.getUserName());

        LOG.debug("authenticate user is: " + user);
        return user;
    }

    /**
     * Calls an IMS endpoint to make sure user is authenticated.
     *
     * @param user The user
     * @return the response
     * @throws Exception the exception
     */
    private void convertRoles(final User user) throws Exception {

        boolean needToConvert = true;
            
        for (final String role : user.getRoles()) {

            if (role.split("-").length == 4) {
                
                needToConvert = false;
                break;
            }
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
                            
                            CrowdAPIClient.addGroup(organizationName, originalRoleParts[0], originalRoleParts[1], groupDescription, false, false);
                            
                            final String groupName = CrowdGroupNameAlgorithm.buildCrowdGroupName(organizationName, originalRoleParts[0], originalRoleParts[1], originalRoleParts[2]);
                            CrowdAPIClient.addMembership(groupName, user.getUserName());
                            
                            // add the new role to the user object
                            user.getRoles().add(CrowdGroupNameAlgorithm.getOrganizationString(organizationName) + "-" + role);
                        }
                    }
                    
                    // remove the old role string from the user object
                    user.getRoles().remove(role);
                }
            }
        }
        
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
