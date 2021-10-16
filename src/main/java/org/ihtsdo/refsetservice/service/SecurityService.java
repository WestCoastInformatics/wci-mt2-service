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

import javax.servlet.http.HttpSession;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.User;
//import org.ihtsdo.refsetservice.model.UserRole;
import org.ihtsdo.refsetservice.util.HandlerUtility;
import org.ihtsdo.refsetservice.util.LocalException;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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
	
	/** The handler. */
	private static final String SESSION_USER_OBJECT_KEY = "RT2_USER_OBJECT";

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
            return (User) object;
        }
        
        return new User("testUser", "Test User", "tuser@testuser.com", new HashSet<String>(Arrays.asList("rt-all-user", "rt-all-author", "rt-all-reviewer")));
        //return null;
    }
    
    /**
     * Get the something from the session.
     *
     * @param attributeName the session attribute name
     * @return the object from the session or null
     * @throws Exception the exception
     */
    public static Object getFromSession(final String attributeName) throws Exception {
        
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes)RequestContextHolder.getRequestAttributes();
        
        if (requestAttributes == null || requestAttributes.getRequest() == null ) {
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
	 * 
	 * @param userName
	 * @param password
	 * @return
	 * @throws Exception
	 */
	public User authenticate(final String userName, final String password) throws Exception {
		// Check userName and password are not null
		if (userName == null || userName.isEmpty())
			throw new LocalException("Invalid userName: null");
		if (password == null || password.isEmpty())
			throw new LocalException("Invalid password: null");

		Properties config = PropertyUtility.getProperties();

		if (handler == null) {
			timeout = (StringUtils.isNotBlank(config.getProperty("security.timeout")))
					? Integer.valueOf(config.getProperty("security.timeout"))
					: 7200000;
		
			final String handlerName = (StringUtils.isNotBlank(config.getProperty("security.handler")))
					? config.getProperty("security.handler") 
					: "org.ihtsdo.refsetservice.handler.ImsSecurityServiceHandler";
			
			handler = HandlerUtility.newStandardHandlerInstanceWithConfiguration("security.handler", handlerName,
							SecurityServiceHandler.class);

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
	//@Override
	public void logout(final String authToken) throws Exception {
		tokenUsernameMap.remove(authToken);
		tokenTimeoutMap.remove(authToken);
	}

	/* see superclass */
	//@Override
	public String getUsernameForToken(final String authToken) throws Exception {
		// use guest user for null auth token
		if (authToken == null)
			throw new LocalException(
					"Attempt to access a service without an AuthToken, the user is likely not logged in.");

		final boolean allowGuest = (StringUtils.isNotBlank(PropertyUtility.getProperties().getProperty("security.guest.disabled")))
				? "true".equals(PropertyUtility.getProperties().getProperty("security.guest.disabled"))
				: false;
		
		// handle guest user unless
		if (authToken.equals("guest") && allowGuest) {
			return "guest";
		}

		// Replace double quotes in auth token.
		final String parsedToken = authToken.replace("\"", "");

		// Check auth token against the userName map
		if (tokenUsernameMap.containsKey(parsedToken)) {
			String userName = tokenUsernameMap.get(parsedToken);

			// Validate that the user has not timed out.
			if (handler.timeoutUser(userName)) {

				if (tokenTimeoutMap.get(parsedToken) == null) {
					throw new LocalException("No login timeout set for authToken.");
				}

				if (tokenTimeoutMap.get(parsedToken).before(new Date())) {
					throw new LocalException("AuthToken has expired. Please reload and log in again.");
				}
				tokenTimeoutMap.put(parsedToken, new Date(new Date().getTime() + timeout));
			}
			return userName;
		} else {
			throw new LocalException("AuthToken does not have a valid userName.");
		}
	}

	public Set<String> getApplicationRoleForToken(final String authToken) throws Exception {
		if (authToken == null) {
			throw new LocalException(
					"Attempt to access a service without an AuthToken, the user is likely not logged in.");
		}
		
		final boolean allowGuest = (StringUtils.isNotBlank(PropertyUtility.getProperties().getProperty("security.guest.disabled")))
				? "true".equals(PropertyUtility.getProperties().getProperty("security.guest.disabled"))
				: false;
				
		// Handle "guest" user
		if (authToken.equals("guest") && allowGuest) {
			return new HashSet<>(Arrays.asList(User.ROLE_USER));
		}

		final String parsedToken = authToken.replace("\"", "");
		final String userName = getUsernameForToken(parsedToken);

		// check for null userName
		if (userName == null) {
			throw new LocalException("Unable to find user for the AuthToken");
		}
		final User user = getUser(userName.toLowerCase());
		if (user == null) {
			return new HashSet<>(Arrays.asList(User.ROLE_USER));
			// throw new
			// LocalException("Unable to obtain user information for userName = " +
			// userName);
		}
		return user.getRoles();
	}

//	// TODO: fix if required
//	public List<String> getUserRoleForToken(final String authToken, final String projectId) throws Exception {
//		if (authToken == null) {
//			throw new LocalException(
//					"Attempt to access a service without an AuthToken, the user is likely not logged in.");
//		}
//		if (projectId == null) {
//			throw new Exception("Unexpected null project id");
//		}
//
//		final String userName = getUsernameForToken(authToken);
//		//final ProjectService service = new ProjectService();
//		String result = null;
//		try (final TerminologyService service = new TerminologyService();)
//		{
//			// result = service.getProject(projectId).getUserRoleMap().get(getUser(userName));
//			if (result == null) {
//				result = UserRole.VIEWER;
//			}
//		}
//		return result;
//	}

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

	// TODO: Fix if required
	// public UserList getUsers() {
	//		javax.persistence.Query query = manager.createQuery("select u from UserJpa u");
	//		final List<User> m = query.getResultList();
	//		final UserListJpa mapUserList = new UserListJpa();
	//		mapUserList.setObjects(m);
	//		mapUserList.setTotalCount(m.size());
	//		return mapUserList;
	// }

//	/* see superclass */
//	@SuppressWarnings("unchecked")
//	@Override
//	public UserList findUsersForQuery(String query, PfsParameter pfs) throws Exception {
//		logger.info("Security Service - find users " + query + ", pfs= " + pfs);
//
//		if (query == null || query.replace("*", "").length() < 3) {
//			try {
//				int[] totalCt = new int[1];
//				final List<User> list = (List<User>) getQueryResults(
//						query == null || query.isEmpty() ? "id:[* TO *]" : query, UserJpa.class, UserJpa.class, pfs,
//						totalCt);
//				final UserList result = new UserListJpa();
//				result.setTotalCount(totalCt[0]);
//				result.setObjects(list);
//				for (final User user : result.getObjects()) {
//					handleLazyInit(user);
//				}
//				return result;
//			} catch (ParseException e) {
//				// On parse error, return empty results
//				return new UserListJpa();
//			}
//		} else {
//			logger.info("Security Service - autocomplete users by name " + query);
//			return autocompleteHelper(query, pfs, UserJpa.class);
//		}
//	}

	@Override
	public void close() throws Exception {
		// TODO Auto-generated method stub
		
	}

	
	
	/**
	 * Authorize the users application role.
	 *
	 * @param authToken       the auth token
	 * @param perform         the perform
	 * @param requiredAppRole the auth role
	 * @return the username
	 * @throws Exception the exception
	 */
	public String authorizeApp(String authToken, String perform,
			String requiredAppRole) throws Exception {

		// Verify the user has the privileges of the required app role
		final Set<String> roles = getApplicationRoleForToken(authToken);

		boolean hasRole = false;

		if (roles.contains(User.ROLE_USER) && requiredAppRole == User.ROLE_USER)
			hasRole = true;
		else if (roles.contains(User.ROLE_AUTHOR)
				&& (requiredAppRole == User.ROLE_USER || requiredAppRole == User.ROLE_AUTHOR))
			hasRole = true;
		else if (roles.contains(User.ROLE_REVIEWER) && (requiredAppRole == User.ROLE_USER
				|| requiredAppRole == User.ROLE_AUTHOR || requiredAppRole == User.ROLE_REVIEWER))
			hasRole = true;
		else if (roles.contains(User.ROLE_LEAD)
				&& (requiredAppRole == User.ROLE_USER || requiredAppRole == User.ROLE_AUTHOR
						|| requiredAppRole == User.ROLE_REVIEWER || requiredAppRole == User.ROLE_LEAD))
			hasRole = true;
		else if (roles.contains(User.ROLE_ADMIN))
			hasRole = true;
		else
			hasRole = false;
		
		if (!hasRole) {
			throw new Exception("User does not have permissions to " + perform + ".");
		}
	
		final String userName = getUsernameForToken(authToken);
		return userName;
	}
	
	
	// /* see superclass */
	// @Override
	// public void removeUserPreferences(Long id) {
	// logger.debug("Security Service - remove user preferences " + id);
	// tx = manager.getTransaction();
	// // retrieve this user
	// final UserPreferences mu = manager.find(UserPreferencesJpa.class, id);
	// try {
	// if (getTransactionPerOperation()) {
	// tx.begin();
	// if (manager.contains(mu)) {
	// manager.remove(mu);
	// } else {
	// manager.remove(manager.merge(mu));
	// }
	// tx.commit();
	//
	// } else {
	// if (manager.contains(mu)) {
	// manager.remove(mu);
	// } else {
	// manager.remove(manager.merge(mu));
	// }
	// }
	// } catch (Exception e) {
	// if (tx.isActive()) {
	// tx.rollback();
	// }
	// throw e;
	// }
	//
	// }

	// /* see superclass */
	// @Override
	// public void updateUserPreferences(UserPreferences userPreferences) {
	// logger.debug("Security Service - update user preferences " +
	// userPreferences);
	// try {
	// if (getTransactionPerOperation()) {
	// tx = manager.getTransaction();
	// tx.begin();
	// manager.merge(userPreferences);
	// tx.commit();
	// } else {
	// manager.merge(userPreferences);
	// }
	// } catch (Exception e) {
	// if (tx.isActive()) {
	// tx.rollback();
	// }
	// throw e;
	// }
	// }

	// /**
	// * Handle lazy init.
	// *
	// * @param user the user
	// */
	// @Override
	// public void handleLazyInit(User user) {
	// if (user.getProjectRoleMap() != null) {
	// user.getProjectRoleMap().size();
	// }
	// if (user.getUserPreferences() != null) {
	// user.getUserPreferences().getLastProjectId();
	// }
	// if (user.getUserPreferences() != null &&
	// user.getUserPreferences().getLanguageDescriptionTypes() != null
	// && user.getUserPreferences().getLanguageDescriptionTypes().size() > 0) {
	// user.getUserPreferences().getLanguageDescriptionTypes().get(0).getDescriptionType().getName();
	// }
	// }

	// /* see superclass */
	// @Override
	// public UserList autocompleteUsersName(String name, PfsParameter pfs) throws
	// Exception {
	// logger.info("Security Service - autocomplete user's name " + name);
	// return autocompleteHelper(name, pfs, UserJpa.class);
	// }

	// /**
	// *
	// * @param <T>
	// * @param name
	// * @param clazz
	// * @return
	// */
	// private <T extends User> UserList autocompleteHelper(String name,
	// PfsParameter pfs, Class<T> clazz)
	// throws Exception {
	//
	// if (name == null) {
	// return new UserListJpa();
	// }
	//
	// final String EDGE_NGRAM_INDEX = "nameEdgeNGram";
	// final String NGRAM_INDEX = "nameNGram";
	//
	// final FullTextEntityManager fullTextEntityManager =
	// Search.getFullTextEntityManager(manager);
	//
	// final QueryBuilder queryBuilder =
	// fullTextEntityManager.getSearchFactory().buildQueryBuilder().forEntity(clazz)
	// .get();
	//
	// final Query query =
	// queryBuilder.phrase().withSlop(2).onField(NGRAM_INDEX).andField(EDGE_NGRAM_INDEX)
	// .boostedTo(0).andField("name").boostedTo(5).sentence(name.toLowerCase()).createQuery();
	//
	// final BooleanQuery.Builder booleanQuery = new BooleanQuery.Builder();
	// booleanQuery.add(query, BooleanClause.Occur.MUST);
	//
	// final FullTextQuery fullTextQuery = IndexUtility.applyPfsToLuceneQuery(clazz,
	// clazz,
	// booleanQuery.build().toString(), pfs, manager);
	//
	// @SuppressWarnings("unchecked")
	// final List<User> results = fullTextQuery.getResultList();
	//
	// final UserList list = new UserListJpa();
	// list.setTotalCount(fullTextQuery.getResultSize());
	// for (User user : results) {
	// handleLazyInit(user);
	// }
	// // exclude duplicates
	// list.getObjects().addAll(results.stream().distinct().collect(Collectors.toList()));
	//
	// return list;
	//
	// }


}