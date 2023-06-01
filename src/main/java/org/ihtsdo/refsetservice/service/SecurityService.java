/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.handler.SecurityServiceHandler;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.UserProjectRole;
import org.ihtsdo.refsetservice.terminologyservice.EditionService;
import org.ihtsdo.refsetservice.terminologyservice.OrganizationService;
import org.ihtsdo.refsetservice.util.HandlerUtility;
import org.ihtsdo.refsetservice.util.LocalException;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reference implementation of the {@link SecurityService}.
 */
public class SecurityService implements AutoCloseable {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SecurityService.class);

    /** The token userName . */
    private static Map<String, String> tokenUsernameMap = Collections.synchronizedMap(new HashMap<String, String>());

    /** The token login time . */
    private static Map<String, Date> tokenTimeoutMap = Collections.synchronizedMap(new HashMap<String, Date>());

    /** a place to store temporary user data in memory . */
    private static Map<String, Map<String, Object>> userInMemoryStorage = Collections.synchronizedMap(new HashMap<String, Map<String, Object>>());

    /** The handler. */
    private static SecurityServiceHandler handler = null;

    /** The session key for the user object. */
    public static final String SESSION_USER_OBJECT_KEY = "RT2_USER_OBJECT";

    /** The session key for the list of user projects. */
    public static final String SESSION_USER_PROJECTS = "RT2_USER_PROJECTS";

    /** The handler. */
    public static final String GUEST_USERNAME = "nonLoggedInUser";

    /** The timeout. */
    private static int timeout;

    /**
     * Instantiates an empty {@link SecurityServiceJpa}.
     *
     * @throws Exception the exception
     */
    public SecurityService() throws Exception {

        super();
    }

    /**
     * Get a user for application level changes that has full permissions.
     *
     * @return the user from the session or null
     * @throws Exception the exception
     */
    public static User getApplicationAdminUser() throws Exception {

        final User user = new User();
        user.setName("RT2 Internal Application Admin");
        user.setUserName("RT2_Internal_Application_Admin");
        user.getRoles().add("all-all-all");

        return user;
    }

    /**
     * Get the user from the session.
     *
     * @return the user from the session or null
     * @throws Exception the exception
     */
    public static User getUserFromSession() throws Exception {

        final Object object = getFromSession(SESSION_USER_OBJECT_KEY);

        if (object != null) {

            LOG.debug("getUserFromSession SESSION USER: " + ModelUtility.toJson(object));
            return (User) object;
        }

        // TODO - Find a better solution for unit tests
        if (PropertyUtility.getProperty("springProfiles").toLowerCase().contains("test")) {

            final User testUser = new User("unitTestUser", "Unit Test User", "", "", "", new HashSet<String>());
            testUser.getRoles().add("all-all-author");
            testUser.getRoles().add("all-all-reviewer");
            testUser.getRoles().add("all-all-admin");
            LOG.debug("getUserFromSession SESSION USER: " + ModelUtility.toJson(testUser));
            return testUser;
        }

        final User nonLoggedInUser = new User(GUEST_USERNAME, "Non Logged In User", "", "", "", new HashSet<String>());
        LOG.debug("getUserFromSession SESSION USER: " + ModelUtility.toJson(nonLoggedInUser));

        final ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (requestAttributes == null || requestAttributes.getRequest() == null) {

            return nonLoggedInUser;
        }

        final HttpServletResponse response = requestAttributes.getResponse();
        final Cookie imsCookie = getImsCookie();

        if (imsCookie != null) {

            final Cookie cookie = new Cookie(imsCookie.getName(), null);
            cookie.setPath("/");
            cookie.setDomain(".ihtsdotools.org");
            cookie.setHttpOnly(imsCookie.isHttpOnly());
            cookie.setMaxAge(0);
            response.addCookie(cookie);
        }

        return nonLoggedInUser;
    }

    /**
     * Returns the ims cookie.
     *
     * @return the ims cookie
     * @throws Exception the exception
     */
    public static Cookie getImsCookie() throws Exception {

        final ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (requestAttributes == null || requestAttributes.getRequest() == null) {
            return null;
        }

        Cookie imsCookie = null;
        final Cookie[] cookies = requestAttributes.getRequest().getCookies();

        if (cookies != null) {

            for (int i = 0; i < cookies.length; i++) {

                if (cookies[i].getName().contains("ims-ihtsdo")) {

                    // LOG.debug("getImsCookie ims-ihtsdo cookie: " + ModelUtility.toJson(cookies[i]));
                    imsCookie = cookies[i];
                    break;
                }
            }
        }

        return imsCookie;
    }

    /**
     * Clear cookies.
     *
     * @throws Exception the exception
     */
    private static void clearCookies() throws Exception {

        final ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (requestAttributes != null && requestAttributes.getRequest() != null) {

            final Cookie[] cookies = requestAttributes.getRequest().getCookies();

            if (cookies != null) {

                final HttpServletResponse response = requestAttributes.getResponse();

                for (int i = 0; i < cookies.length; i++) {

                    if (cookies[i].getName().contains("ims-ihtsdo")) {

                        LOG.debug("clearCookies ims-ihtsdo cookie: " + ModelUtility.toJson(cookies[i]));
                        final Cookie cookie = new Cookie(cookies[i].getName(), null);
                        cookie.setPath("/");
                        cookie.setDomain(".ihtsdotools.org");
                        cookie.setHttpOnly(cookies[i].isHttpOnly());
                        cookie.setMaxAge(0);
                        response.addCookie(cookie);
                        break;
                    }

                }

            }

        }

    }

    /**
     * Get the something from the session.
     *
     * @param attributeName the session attribute name
     * @return the object from the session or null
     * @throws Exception the exception
     */
    public static Object getFromSession(final String attributeName) throws Exception {

        final ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (requestAttributes == null || requestAttributes.getRequest() == null) {

            return null;
        }

        final HttpSession session = requestAttributes.getRequest().getSession();

        if (session == null) {

            return null;
        }

        final Object object = session.getAttribute(attributeName);
        return object;
    }

    /**
     * Get the something from the session.
     *
     * @param attributeName the session attribute name
     * @param value the value to store in the session
     * @return true if the value was set in the session, otherwise false
     * @throws Exception the exception
     */
    public static boolean setInSession(final String attributeName, final String value) throws Exception {

        final ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (requestAttributes == null || requestAttributes.getRequest() == null) {

            return false;
        }

        final HttpSession session = requestAttributes.getRequest().getSession();

        if (session == null) {

            return false;
        }

        session.setAttribute(attributeName, value);
        return true;
    }

    /**
     * Remove the something from the session.
     *
     * @param attributeName the session attribute name
     * @throws Exception the exception
     */
    public static void removeFromSession(final String attributeName) throws Exception {

        final ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (requestAttributes == null || requestAttributes.getRequest() == null) {

            return;
        }

        final HttpSession session = requestAttributes.getRequest().getSession();

        if (session == null) {

            return;
        }

        session.removeAttribute(attributeName);
    }

    /**
     * Get something from the user specific in memory storage.
     *
     * @param attributeName the storage attribute name
     * @return the object from the storage or null
     * @throws Exception the exception
     */
    public static Object getFromInMemoryStorage(final String attributeName) throws Exception {

        final User user = getUserFromSession();
        Object returnObject = null;

        if (userInMemoryStorage.containsKey(user.getUserName())) {

            final Map<String, Object> storageMap = userInMemoryStorage.get(user.getUserName());

            if (storageMap.containsKey(attributeName)) {

                returnObject = storageMap.get(attributeName);
            }

        }

        return returnObject;
    }

    /**
     * Set something in the user specific in memory storage.
     *
     * @param attributeName the storage attribute name
     * @param value the value to store in the storage
     * @return true if the value was set in the storage, otherwise false
     * @throws Exception the exception
     */
    public static boolean setInMemoryStorage(final String attributeName, final Object value) throws Exception {

        final User user = getUserFromSession();

        if (userInMemoryStorage.containsKey(user.getUserName())) {

            final Map<String, Object> storageMap = userInMemoryStorage.get(user.getUserName());
            storageMap.put(attributeName, value);

        } else {

            final Map<String, Object> storageMap = new HashMap<>();
            storageMap.put(attributeName, value);
            userInMemoryStorage.put(user.getUserName(), storageMap);
        }

        return true;
    }

    /**
     * Remove the something from the in memory storage.
     *
     * @param attributeName the storage attribute name
     * @throws Exception the exception
     */
    public static void removeFromInMemoryStorage(final String attributeName) throws Exception {

        final User user = getUserFromSession();

        if (userInMemoryStorage.containsKey(user.getUserName())) {

            final Map<String, Object> storageMap = userInMemoryStorage.get(user.getUserName());
            storageMap.remove(attributeName);
        }

    }

    /**
     * Authenticate.
     *
     * @param userName the user name
     * @return the user
     * @throws Exception the exception
     */
    public User authenticate(final String userName) throws Exception {

        // Check userName and password are not null
        if (userName == null || userName.isEmpty()) {

            throw new LocalException("Invalid userName: null");
        }

        final Properties config = PropertyUtility.getProperties();

        if (handler == null) {

            timeout = (StringUtils.isNotBlank(config.getProperty("spring.session.timeout.seconds")))
                ? Integer.valueOf(config.getProperty("spring.session.timeout.seconds")) : 900000;

            final String handlerName = (StringUtils.isNotBlank(config.getProperty("security.handler"))) ? config.getProperty("security.handler")
                : "org.ihtsdo.refsetservice.handler.ImsSecurityServiceHandler";

            handler = HandlerUtility.newStandardHandlerInstanceWithConfiguration("security.handler", handlerName, SecurityServiceHandler.class);

        }

        //
        // Call the security service
        //
        final User authUser = handler.authenticate(userName);
        LOG.info("Authenticated user is {}", authUser);
        return authHelper(authUser);
    }

    /**
     * Auth helper.
     *
     * @param authUser the auth user
     * @return the user
     * @throws Exception the exception
     */
    private User authHelper(final User authUser) throws Exception {

        if (authUser == null) {
            return null;
        }

        // check if authenticated user exists
        final User userFound = getUserFromUserName(authUser.getUserName());

        // if user was found, update to match settings
        String userId = null;

        if (userFound != null) {
            // handleLazyInit(userFound);

            LOG.info("update user {}", authUser);
            userFound.setEmail(authUser.getEmail());
            userFound.setName(authUser.getName());
            userFound.setUserName(authUser.getUserName());
            userFound.setRoles(authUser.getRoles());
            updateUser(userFound);
            userId = userFound.getId();

        } else if (("rt2-dev-admin".equals(authUser.getUserName()) || "rt2-uat-admin".equals(authUser.getUserName())
            || "rt2-prod-admin".equals(authUser.getUserName())) && userFound == null) {

            LOG.info("add admin user {}", authUser);
            User newUser = new User();
            newUser.setEmail(authUser.getEmail());
            newUser.setName(authUser.getName());
            newUser.setUserName(authUser.getUserName());
            newUser.setRoles(authUser.getRoles());
            newUser = addUser(newUser);
            userId = newUser.getId();

        } else {
            // if user not found, return not
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "You are not a member of an organization.  You can still browse public reference sets.");
        }

        // Generate application-managed token
        final String token = handler.computeTokenForUser(authUser.getUserName());
        tokenUsernameMap.put(token, authUser.getUserName());
        tokenTimeoutMap.put(token, new Date(new Date().getTime() + timeout));

        LOG.debug("User = " + authUser.getUserName() + ", " + authUser);

        // Reload the user to populate UserPreferences
        final User finalUser = getUser(userId);
        finalUser.setAuthToken(token);
        finalUser.setRoles(authUser.getRoles());
        return finalUser;

    }

    /**
     * Adds the user to organization if entered in Crowd but not a member in RT2's organization.
     *
     * @param user the user
     * @throws Exception the exception
     */
    private void checkAndAddUserToOrganization(final User user) throws Exception {

        // break down org-project-role
        if (user == null || user.getRoles() == null || user.getRoles().isEmpty()) {

            return;
        }

        try (final TerminologyService service = new TerminologyService()) {

            final User appAdminUser = getApplicationAdminUser();

            service.setModifiedBy(appAdminUser.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            final Set<String> permissionEditionAbbreviations = new HashSet<>();

            for (final String groupName : user.getRoles()) {

                final UserProjectRole userProjectRole = new UserProjectRole(groupName);
                permissionEditionAbbreviations.add(userProjectRole.getOrganization());
            }

            final ResultList<Edition> editions = EditionService.searchEditions(new SearchParameters());
            final Map<String, Organization> organizationMap = new HashMap<>();

            for (final Edition edition : editions.getItems()) {

                organizationMap.put(edition.getAbbreviation(), edition.getOrganization());
            }

            for (final String permissionEditionAbbreviation : permissionEditionAbbreviations) {

                for (final Edition possibleEdition : new ArrayList<Edition>(editions.getItems())) {

                    Edition edition = null;
                    boolean isMember = false;

                    if (possibleEdition.getAbbreviation().equals(permissionEditionAbbreviation)) {

                        edition = possibleEdition;
                        editions.getItems().remove(possibleEdition);

                    } else {

                        continue;
                    }

                    OrganizationService.setRoles(appAdminUser, edition.getOrganization(), edition.getOrganization().getRoles());

                    for (final User member : edition.getOrganization().getMembers()) {

                        if (member.getId().equals(user.getId())) {

                            isMember = true;
                            break;
                        }

                    }

                    if (!isMember) {

                        LOG.debug("Add user " + user.getUserName() + " to organization " + edition.getOrganization().getName());
                        OrganizationService.addUserToOrganization(service, appAdminUser, edition.getOrganization().getId(), user.getEmail());
                    }

                }

            }

            service.commit();
        }

    }

    /* see superclass */
    /**
     * Logout.
     *
     * @param userName the user name
     * @throws Exception the exception
     */
    // @Override
    public void logout(final String userName) throws Exception {

        final User user = getUserFromSession();

        if (!user.getUserName().equals(userName)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "This user name supplied is not authenticated.");
        }

        tokenUsernameMap.remove(userName);
        tokenTimeoutMap.remove(userName);
        removeFromSession(SESSION_USER_OBJECT_KEY);
        clearCookies();
    }

    /**
     * Returns the user.
     *
     * @param id the id
     * @return the user
     * @throws Exception the exception
     */
    public User getUser(final String id) throws Exception {

        User user = null;

        try (final TerminologyService service = new TerminologyService()) {

            user = service.get(id, User.class);
        }

        return user;
    }

    /**
     * Returns the user from user name.
     *
     * @param userName the user name
     * @return the user from user name
     * @throws Exception the exception
     */
    public static User getUserFromUserName(final String userName) throws Exception {

        User user = null;

        try (final TerminologyService service = new TerminologyService()) {

            // Note: When testing POSTMAN, hard code userName to your userName and relaunch server
            user = service.findSingle("userName:" + userName, User.class, null);
        }

        return user;
    }

    /**
     * Adds the user.
     *
     * @param user the user
     * @return the user
     * @throws Exception the exception
     */
    public User addUser(User user) throws Exception {

        LOG.debug("Security Service - add user {}", user);

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            user = service.addHasLastModified(user);
        }

        return user;
    }

    /**
     * Removes the user.
     *
     * @param user the user
     * @throws Exception the exception
     */
    public void removeUser(final User user) throws Exception {

        LOG.debug("Security Service - remove user {}", user);

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            service.remove(user);
        }

    }

    /**
     * Update user.
     *
     * @param user the user
     * @throws Exception the exception
     */
    public void updateUser(final User user) throws Exception {

        LOG.debug("Security Service - update user {}", user);

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            service.updateHasLastModified(user);
        }

    }

    /* see superclass */
    @Override
    public void close() throws Exception {

        // n/a

    }

}
