package org.ihtsdo.refsetservice.service;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.handler.SecurityServiceHandler;
import org.ihtsdo.refsetservice.model.User;
//import org.ihtsdo.refsetservice.model.UserRole;
import org.ihtsdo.refsetservice.util.HandlerUtility;
import org.ihtsdo.refsetservice.util.LocalException;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Reference implementation of the {@link SecurityService}.
 */
public class SecurityService implements AutoCloseable {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(SecurityService.class);

    /** The token userName . */
    private static Map<String, String> tokenUsernameMap = Collections.synchronizedMap(new HashMap<String, String>());

    /** The token login time . */
    private static Map<String, Date> tokenTimeoutMap = Collections.synchronizedMap(new HashMap<String, Date>());

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
     * Get the user from the session.
     *
     * @return the user from the session or null
     * @throws Exception the exception
     */
    public static User getUserFromSession() throws Exception {

        final Object object = getFromSession(SESSION_USER_OBJECT_KEY);

        if (object != null) {

            logger.debug("getUserFromSession SESSION USER: " + ModelUtility.toJson(object));
            return (User) object;
        }

        // TODO - Find a better solution for unit tests
        if (PropertyUtility.getProperty("springProfiles").toLowerCase().contains("test")) {

            final User testUser = new User("unitTestUser", "Unit Test User", "", new HashSet<String>());
            testUser.getRoles().add("all-all-author");
            testUser.getRoles().add("all-all-reviewer");
            testUser.getRoles().add("all-all-admin");
            logger.debug("getUserFromSession SESSION USER: " + ModelUtility.toJson(testUser));
            return testUser;
        }

        final User nonLoggedInUser = new User(GUEST_USERNAME, "Non Logged In User", "", new HashSet<String>());
        logger.debug("getUserFromSession SESSION USER: " + ModelUtility.toJson(nonLoggedInUser));

        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (requestAttributes == null || requestAttributes.getRequest() == null) {

            return nonLoggedInUser;
        }

        ServletUriComponentsBuilder builder = ServletUriComponentsBuilder.fromCurrentContextPath();

        Cookie[] cookies = requestAttributes.getRequest().getCookies();
        HttpServletResponse response = ((ServletRequestAttributes) requestAttributes).getResponse();
        logger.debug("getUserFromSession cookies: " + ModelUtility.toJson(cookies));
        logger.debug("getUserFromSession Builder Host: " + builder.build().toString());
        logger.debug("getUserFromSession getServerName: " + requestAttributes.getRequest().getServerName());
        logger.debug("getUserFromSession getRemoteHost: " + requestAttributes.getRequest().getRemoteHost());

        for (int i = 0; i < cookies.length; i++) {

            if (cookies[i].getName().contains("ims-ihtsdo")) {

                logger.debug("getUserFromSession ims-ihtsdo cookie: " + ModelUtility.toJson(cookies[i]));
                Cookie cookie = new Cookie(cookies[i].getName(), null);
                cookie.setPath("/"); // cookies[i].getPath()
                cookie.setDomain(".ihtsdotools.org"); // cookies[i].getDomain()
                cookie.setHttpOnly(cookies[i].isHttpOnly());
                cookie.setMaxAge(0);
                response.addCookie(cookie);
                break;

            } else if (cookies[i].getName().contains("rt2-auth")) {

                logger.debug("getUserFromSession rt2 auth cookie: " + ModelUtility.toJson(cookies[i]));
                Cookie cookie = new Cookie(cookies[i].getName(), null);
                cookie.setPath("/"); // cookies[i].getPath()
                cookie.setDomain(cookies[i].getDomain()); // cookies[i].getDomain()
                cookie.setHttpOnly(cookies[i].isHttpOnly());
                cookie.setMaxAge(0);
                response.addCookie(cookie);
            }

        }

        return nonLoggedInUser;
        // return null;
    }

    /**
     * Get the something from the session.
     *
     * @param attributeName the session attribute name
     * @return the object from the session or null
     * @throws Exception the exception
     */
    public static Object getFromSession(final String attributeName) throws Exception {

        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (requestAttributes == null || requestAttributes.getRequest() == null) {

            return null;
        }

        final HttpSession session = requestAttributes.getRequest().getSession();

        if (session == null) {

            return null;
        }

        Object object = session.getAttribute(attributeName);
        return object;
    }

    /**
     * Remove the something from the session.
     *
     * @param attributeName the session attribute name
     * @throws Exception the exception
     */
    public static void removeFromSession(final String attributeName) throws Exception {

        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

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
     * 
     * @param userName
     * @param password
     * @return
     * @throws Exception
     */
    public User authenticate(final String userName, final String password) throws Exception {

        // Check userName and password are not null
        if (userName == null || userName.isEmpty()) {

            throw new LocalException("Invalid userName: null");
        }

        if (password == null || password.isEmpty()) {

            throw new LocalException("Invalid password: null");
        }

        Properties config = PropertyUtility.getProperties();

        if (handler == null) {

            timeout = (StringUtils.isNotBlank(config.getProperty("security.timeout"))) ? Integer.valueOf(config.getProperty("security.timeout")) : 7200000;

            final String handlerName =
                (StringUtils.isNotBlank(config.getProperty("security.handler"))) ? config.getProperty("security.handler") : "org.ihtsdo.refsetservice.handler.ImsSecurityServiceHandler";

            handler = HandlerUtility.newStandardHandlerInstanceWithConfiguration("security.handler", handlerName, SecurityServiceHandler.class);

        }

        //
        // Call the security service
        //
        User authUser = handler.authenticate(userName, password);
        logger.info("Authenticated user is {}", authUser);
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

        if (authUser == null)
            return null;

        // check if authenticated user exists
        final User userFound = getUserFromUserName(authUser.getUserName());

        // if user was found, update to match settings
        String userId = null;

        if (userFound != null) {
            // handleLazyInit(userFound);

            logger.info("update user {}", authUser);
            userFound.setEmail(authUser.getEmail());
            userFound.setName(authUser.getName());
            userFound.setUserName(authUser.getUserName());
            userFound.setRoles(authUser.getRoles());
            updateUser(userFound);
            userId = userFound.getId();
        }
        // if User not found, create one for our use
        else {

            logger.info("add user {}", authUser);
            User newUser = new User();
            newUser.setEmail(authUser.getEmail());
            newUser.setName(authUser.getName());
            newUser.setUserName(authUser.getUserName());
            newUser.setRoles(authUser.getRoles());
            newUser = addUser(newUser);
            userId = newUser.getId();
        }
        // manager.clear();

        // Generate application-managed token
        final String token = handler.computeTokenForUser(authUser.getUserName());
        tokenUsernameMap.put(token, authUser.getUserName());
        tokenTimeoutMap.put(token, new Date(new Date().getTime() + timeout));

        logger.debug("User = " + authUser.getUserName() + ", " + authUser);

        // Reload the user to populate UserPreferences
        final User result = getUser(userId);
        result.setAuthToken(token);

        return result;
    }

    /* see superclass */
    // @Override
    public void logout(final String authToken) throws Exception {

        tokenUsernameMap.remove(authToken);
        tokenTimeoutMap.remove(authToken);
        removeFromSession(SESSION_USER_OBJECT_KEY);
    }

    /**
     * 
     * @param id
     * @return
     * @throws Exception
     */
    public User getUser(final String id) throws Exception {

        User user = null;

        try (final TerminologyService service = new TerminologyService()) {

            user = service.get(id, User.class);
        }

        return user;
    }

    /**
     * 
     * @param userName
     * @return
     * @throws Exception
     */
    public User getUserFromUserName(final String userName) throws Exception {

        User user = null;

        try (final TerminologyService service = new TerminologyService()) {

            user = service.findSingle("userName:" + userName, User.class, null);
        }

        return user;
    }

    /**
     * 
     * @param user
     * @return
     * @throws Exception
     */
    public User addUser(User user) throws Exception {

        logger.debug("Security Service - add user {}", user);

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            user = service.addHasLastModified(user);
        }

        return user;
    }

    /**
     * 
     * @param user
     * @throws Exception
     */
    public void removeUser(User user) throws Exception {

        logger.debug("Security Service - remove user {}", user);

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            service.remove(user);
        }

    }

    /**
     * 
     * @param user
     * @throws Exception
     */
    public void updateUser(User user) throws Exception {

        logger.debug("Security Service - update user {}", user);

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            service.updateHasLastModified(user);
        }

    }

    @Override
    public void close() throws Exception {
        // TODO Auto-generated method stub

    }

}