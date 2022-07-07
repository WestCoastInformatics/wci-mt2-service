/*
 * Copyright 2022 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
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

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(UserService.class);

    /**
     * Returns the user.
     *
     * @param userId the user id
     * @param includeTeams the include teams
     * @return the user
     * @throws Exception the exception
     */
    public static User getUser(final String userId, boolean includeTeams) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final User user = service.get(userId, User.class);

            if (user == null) {
                logger.info("Unable to find user for id {}.", userId);
                throw new NotFoundException();
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
    }

    /**
     * Search Users.
     *
     * @param searchParameters the search parameters
     * @return the list of projects
     * @throws Exception the exception
     */
    public static ResultList<User> searchUsers(final SearchParameters searchParameters) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

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
        }

    }

    /**
     * Update user.
     *
     * @param user the user
     * @param updateUser the update user
     * @return the user
     * @throws Exception the exception
     */
    public static User updateUser(final User user, final User updateUser) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            // Find the user
            final User original = service.get(updateUser.getId(), User.class);

            if (original == null) {
                logger.info("Unable to find user for id {}.", updateUser.getId());
                throw new NotFoundException();
            }

            service.setModifiedBy(user.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            // Apply changes
            original.patchFrom(updateUser);

            // Update
            service.update(original);
            service.commit();

            return original;
        }
    }
}
