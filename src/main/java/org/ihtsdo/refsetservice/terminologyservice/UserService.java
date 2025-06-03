/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import javax.ws.rs.NotFoundException;

import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class UserService.
 */
public class UserService extends BaseService {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(UserService.class);

    /**
     * Returns the user.
     *
     * @param service the service
     * @param userId the user id
     * @param includeTeams the include teams
     * @return the user
     * @throws Exception the exception
     */
    public static User getUser(final TerminologyService service, final String userId, final boolean includeTeams) throws Exception {

        final User user = service.get(userId, User.class);

        if (user == null) {
            final String message = "Unable to find user for id " + userId + ".";
            LOG.error(message);
            throw new NotFoundException(message);
        }

        if (includeTeams) {
            final SearchParameters sp = new SearchParameters();
            sp.setQuery("members:" + user.getId());
            final ResultList<Team> teamsResultList = TeamService.searchTeams(user, sp);
            if (teamsResultList != null && teamsResultList.getItems() != null) {
                user.getTeams().addAll(teamsResultList.getItems());
            }
        }

        return user;
    }

    /**
     * Returns the user by email.
     *
     * @param service the service
     * @param email the email
     * @return the user
     * @throws Exception the exception
     */
    public static User getUserByEmail(final TerminologyService service, final String email) throws Exception {

        return getUserByEmail(service, email, false);
    }

    /**
     * Returns the user by email.
     *
     * @param service the service
     * @param email the email
     * @param includeTeams the include teams
     * @return the user
     * @throws Exception the exception
     */
    public static User getUserByEmail(final TerminologyService service, final String email, final boolean includeTeams) throws Exception {

        // try (final TerminologyService service = new TerminologyService()) {

        final User user = service.findSingle("email:" + email, User.class, null);
        if (user == null) {
            final String message = "User with " + email + " does not exist.";
            LOG.error(message);
            throw new NotFoundException(message);
        }

        if (includeTeams) {
            final SearchParameters sp = new SearchParameters();
            sp.setQuery("members:" + user.getId());
            final ResultList<Team> teamsResultList = TeamService.searchTeams(user, sp);
            if (teamsResultList != null && teamsResultList.getItems() != null) {
                user.getTeams().addAll(teamsResultList.getItems());
            }
        }

        return user;
        // }
    }

    /**
     * Returns the user by userName.
     *
     * @param service the service
     * @param userName the userName
     * @param includeTeams the include teams
     * @return the user
     * @throws Exception the exception
     */
    public static User getUserByUserName(final TerminologyService service, final String userName, final boolean includeTeams) throws Exception {

        // try (final TerminologyService service = new TerminologyService()) {

        final User user = service.findSingle("userName:" + userName, User.class, null);
        if (user == null) {
            final String message = "User with " + userName + " does not exist.";
            LOG.error(message);

            return null;
        }

        if (includeTeams) {
            final SearchParameters sp = new SearchParameters();
            sp.setQuery("members:" + user.getId());
            final ResultList<Team> teamsResultList = TeamService.searchTeams(user, sp);
            if (teamsResultList != null && teamsResultList.getItems() != null) {
                user.getTeams().addAll(teamsResultList.getItems());
            }
        }

        return user;
        // }
    }

    /**
     * Search Users.
     *
     * @param service the service
     * @param searchParameters the search parameters
     * @return the list of projects
     * @throws Exception the exception
     */
    public static ResultList<User> searchUsers(final TerminologyService service, final SearchParameters searchParameters) throws Exception {

        // try (final TerminologyService service = new TerminologyService()) {

        final long start = System.currentTimeMillis();
        String query = getQueryForActiveOnly(searchParameters);
        final PfsParameter pfs = new PfsParameter();

        if (searchParameters.getOffset() != null) {
            pfs.setOffset(searchParameters.getOffset());
        }

        if (searchParameters.getLimit() != null) {
            pfs.setLimit(searchParameters.getLimit());
        }

        if (searchParameters.getSortAscending() != null) {
            pfs.setAscending(searchParameters.getSortAscending());
        }

        if (searchParameters.getSort() != null) {
            pfs.setSort(searchParameters.getSort());
        }

        if (query != null && !query.equals("")) {
            query = IndexUtility.addWildcardsToQuery(query, Refset.class);
        }

        final ResultList<User> results = service.find(query, pfs, User.class, null);
        results.setTimeTaken(System.currentTimeMillis() - start);
        results.setTotalKnown(true);

        return results;
        // }

    }

    /**
     * Update user.
     *
     * @param service the service
     * @param user the user
     * @param updateUser the update user
     * @return the user
     * @throws Exception the exception
     */
    public static User updateUser(final TerminologyService service, final User user, final User updateUser) throws Exception {

        // Find the user
        final User original = service.get(updateUser.getId(), User.class);

        if (original == null) {
            LOG.info("Unable to find user for id {}.", updateUser.getId());
            throw new NotFoundException();
        }

        // Apply changes
        original.patchFrom(updateUser);

        // Update
        service.update(original);

        return original;
    }
}
