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

import java.util.Arrays;
import java.util.Set;

import javax.ws.rs.ForbiddenException;
import javax.ws.rs.NotFoundException;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.ResultListUser;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.UserRole;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.ihtsdo.refsetservice.util.StringUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * The Class TeamService.
 */
public class TeamService extends BaseService {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(TeamService.class);
    
    /** The name prefix for organization level teams. */
    public static String organizationLevelTeamPrefix = "Application users which can administrator organization ";

    /**
     * Creates the team.
     *
     * @param user the user
     * @param team the team
     * @return the team
     * @throws Exception the exception
     */
    public static Team createTeam(final User user, final Team team) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final Team newTeam = new Team(team);
            newTeam.getRoles().clear();
            checkEditPermissions(user, newTeam);
            validateTeamData(service, newTeam, true);
           
            service.setModifiedBy(user.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            service.add(team);
            service.add(AuditEntryHelper.newTeamEntry(team));
            service.commit();

            return team;
        }
    }
    
    /**
     * Validate team data.
     *
     * @param user the user
     * @param team the team
     * @return the team
     * @throws Exception the exception
     */
    public static void validateTeamData(final TerminologyService service, final Team team, final boolean isNew) throws Exception {

        final boolean isOrganizationTeam = isOrganizationTeam(team);
        
        if (!StringUtility.isEmpty(team.getName())) {
            
            String query = "(name: " + QueryParserBase.escape(team.getName()) + ") AND organizationId: " + team.getOrganizationId();
            
            if (!isNew) {
                query += " AND !(id: " + team.getId() + ")";
            }
            
            final ResultList<Team> results = service.find(query, null, Team.class, null);
            
            if (results.getTotal() > 0) {
                
                final String message = "There is already a team with that name in this Organization";
                logger.error(message);
                throw new RestException(false, HttpStatus.EXPECTATION_FAILED, "Expectation Failed", message); 
            }
        }
        
        if (team.getRoles().isEmpty() && isNew) {
            
            final String message = "A new team must have at least one role associated with it";
            logger.error(message);
            throw new RestException(false, HttpStatus.EXPECTATION_FAILED, "Expectation Failed", message); 
            
        } else if (!team.getRoles().isEmpty()) {
            
            if (isOrganizationTeam && !team.getRoles().contains(User.ROLE_ADMIN)) {
                
                logger.warn("An organization level team must include the admin role, adding it to team");
                team.getRoles().add(User.ROLE_ADMIN);
            }
        }
        
        if (team.getMembers().isEmpty() && !isNew && isOrganizationTeam) {
            
            final String message = "This team must have at least one member assigned to it";
            logger.error(message);
            throw new RestException(false, HttpStatus.EXPECTATION_FAILED, "Expectation Failed", message); 
        }
    }

    /**
     * Returns the team.
     *
     * @param id the id
     * @param includeMembers the include members
     * @return the team
     * @throws Exception the exception
     */
    public static Team getTeam(final String id, final boolean includeMembers) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {
            final Team team = service.findSingle("id: " + id + " AND active:true", Team.class, null);

            if (team == null) {
                logger.info("Unable to find team for id {}.", id);
                throw new NotFoundException();
            }

            if (includeMembers) {
                for (final String userId : team.getMembers()) {
                    ResultList<User> users = service.find("id:" + userId, null, User.class, null);
                    if (users != null && users.getItems() != null) {
                        for (final User user : users.getItems()) {

                            final SearchParameters sp = new SearchParameters();
                            sp.setQuery("members:" + user.getId());
                            final ResultList<Team> teamsResultList = TeamService.searchTeams(user, sp);
                            if (teamsResultList != null && teamsResultList.getItems() != null) {
                                user.getTeams().addAll(teamsResultList.getItems());
                            }
                        }
                        team.getMemberList().addAll(users.getItems());
                    }
                }
            }

            return team;
        }
    }

    /**
     * Update team.
     *
     * @param user the user
     * @param team the team
     * @return the team
     * @throws Exception the exception
     */
    public static Team updateTeam(final User user, final Team team) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final Team existingTeam = getTeam(team.getId(), true);

            checkEditPermissions(user, team);
            validateTeamData(service, team, false);

            existingTeam.patchFrom(team);

            service.setModifiedBy(user.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            service.update(existingTeam);
            service.add(AuditEntryHelper.updateTeamEntry(existingTeam));
            service.commit();

            return existingTeam;
        }
    }

    /**
     * Inactivate team.
     *
     * @param user the user
     * @param teamId the team id
     * @return the list
     * @throws Exception the exception
     */
    public static void inactivateTeam(final User user, final String teamId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            // Find the object
            final Team team = getTeam(teamId, true);

            checkEditPermissions(user, team);
            
            if (isOrganizationTeam(team)) {
                
                final String message = "You can not inactivate this team.";
                logger.error(message);
                throw new RestException(false, HttpStatus.NOT_ACCEPTABLE, "Not Acceptable", message);
            }
            
            if (!team.getMembers().isEmpty()) {
                team.getMembers().clear();
            }

            service.setModifiedBy(user.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            team.setActive(false);
            service.update(team);
            service.add(AuditEntryHelper.inactivateTeamEntry(team));
            service.commit();
        }
    }
    
    /**
     * Search Teams.
     *
     * @param user the user
     * @param searchParameters the search parameters
     * @return the list of projects
     * @throws Exception the exception
     */
    public static ResultList<Team> searchTeams(final User user, final SearchParameters searchParameters) throws Exception {

        return searchTeams(user, searchParameters, false);
    }

    /**
     * Search Teams.
     *
     * @param user the user
     * @param searchParameters the search parameters
     * @param includeMembers the include members
     * @return the list of projects
     * @throws Exception the exception
     */
    public static ResultList<Team> searchTeams(final User user, final SearchParameters searchParameters, final boolean includeMembers) throws Exception {

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
            } else {
                pfs.setSort("name");
            }

            if (query != null && !query.equals("")) {
                query = IndexUtility.addWildcardsToQuery(query, Team.class);
            }

            final ResultList<Team> results = service.find(query, pfs, Team.class, null);
            final ResultList<Team> resultsToReturn = new ResultList<>();

            for (final Team team : results.getItems()) {
                
                if (!canUserViewTeam(user, team)) { 
                    continue;
                }
                
                if (includeMembers) {
                    
                    for (final String userId : team.getMembers()) {
                        
                        ResultList<User> users = service.find("id:" + userId, null, User.class, null);
                        
                        if (users != null && users.getItems() != null) {
                            team.getMemberList().addAll(users.getItems());
                        }
                    }
                }
                
                resultsToReturn.getItems().add(team);
            }
            
            resultsToReturn.setTimeTaken(System.currentTimeMillis() - start);
            resultsToReturn.setTotalKnown(true);
            resultsToReturn.setTotal(resultsToReturn.getItems().size());
            
            logger.debug("TEAM SEARCH resultsToReturn: " + resultsToReturn);

            return resultsToReturn;
        }
    }

    /**
     * Adds the user to team.
     *
     * @param user the user
     * @param teamId the team id
     * @param email the email
     * @return the team
     * @throws Exception the exception
     */
    public static Team addUserToTeam(final User user, final String teamId, final String email) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final Team team = getTeam(teamId, true);

            checkEditPermissions(user, team);

            final User userToAdd = service.findSingle("email:" + email, User.class, null);
            if (userToAdd == null) {
                final String message = "User with " + email + " does not exist.";
                logger.error(message);
                throw new NotFoundException(message);
            }

            final Organization organization = team.getOrganization();
            final Set<User> organizationMembers = organization.getMembers();

            if (!organizationMembers.contains(userToAdd)) {

                final String message = "User with " + email + " is not a member of organization " + organization.getName() + ".";
                logger.error(message);
                throw new RestException(false, HttpStatus.EXPECTATION_FAILED, "Expectation Failed", message);
            }

            if (team.getMembers() != null && team.getMembers().contains(user.getId())) {

                final String message = "User with " + email + " is already a member of team " + team.getName() + ".";
                logger.error(message);
                throw new RestException(false, HttpStatus.CONFLICT, "Conflict", message);
            }

            team.getMembers().add(userToAdd.getId());

            service.setModifiedBy(user.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            service.update(team);
            service.add(AuditEntryHelper.addUserToTeamEntry(team, userToAdd));
            service.commit();

            return team;
        }
    }

    /**
     * Removes the user from team.
     *
     * @param user the user
     * @param teamId the team id
     * @param userId the user id
     * @return the team
     * @throws Exception the exception
     */
    public static Team removeUserFromTeam(final User user, final String teamId, final String userId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            // find team
            final Team team = service.get(teamId, Team.class);
            if (team == null) {
                final String message = "Unable to find team for id " + teamId + ".";
                logger.error(message);
                throw new NotFoundException(message);
            }

            final User userToRemove = service.get(userId, User.class);
            if (userToRemove == null) {
                final String message = "Unable to find user for id " + userId + ".";
                logger.error(message);
                throw new NotFoundException(message);
            }

            if (team.getMembers() != null) {
                if (team.getMembers().contains(userId)) {
                    team.getMembers().remove(userId);
                } else {
                    final String message = "User " + userId + " is not a member of team " + teamId + ".";
                    logger.error(message);
                    throw new RestException(false, HttpStatus.EXPECTATION_FAILED, "Expectation Failed", message);
                }
            }
            
            validateTeamData(service, team, false);

            service.setModifiedBy(user.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            service.update(team);
            service.add(AuditEntryHelper.removeUserFromTeamEntry(team, userToRemove));
            service.commit();

            return team;
        }
    }

    /**
     * Adds the role to team.
     *
     * @param user the user
     * @param teamId the team id
     * @param role the role
     * @throws Exception the exception
     */
    public static void addRoleToTeam(final User user, final String teamId, final String role) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            // find team
            final Team team = getTeam(teamId, true);

            checkEditPermissions(user, team);

            if (StringUtils.isBlank(role) && !UserRole.getAllRoles().contains(UserRole.valueOf(role))) {
                final String message = "Role " + role + " does not exist.";
                logger.error(message);
                throw new RestException(false, HttpStatus.EXPECTATION_FAILED, "Expectation Failed", message);
            }

            if (team.getRoles().contains(role.toUpperCase())) {
                final String message = "Role " + role + " is already a exists for team " + teamId + ".";
                logger.info(message);
                throw new RestException(false, HttpStatus.CONFLICT, "Conflict", message);
            }

            team.getRoles().add(UserRole.valueOf(role).toString());

            service.setModifiedBy(user.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            service.update(team);
            service.add(AuditEntryHelper.addRoleToTeamEntry(team, role));
            service.commit();
        }
    }

    /**
     * Removes the role from team.
     *
     * @param user the user
     * @param teamId the team id
     * @param role the role
     * @throws Exception the exception
     */
    public static void removeRoleFromTeam(final User user, final String teamId, final String role) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final Team team = getTeam(teamId, true);

            checkEditPermissions(user, team);
            
            if (team == null) {
                
                final String message = "Unable to find team for id " + teamId + ".";
                logger.error(message);
                throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", message);
            }

            if (StringUtils.isBlank(role) && !Arrays.asList(UserRole.values()).contains(role.toUpperCase())) {
                
                final String message = "Role " + role + " does not exist.";
                logger.error(message);
                throw new RestException(false, HttpStatus.NOT_FOUND, "Not found", message);
            }

            if (!team.getRoles().contains(role.toUpperCase())) {
                
                final String message = "Role " + role + " does not exist for team " + teamId + ".";
                logger.error(message);
                throw new RestException(false, HttpStatus.NOT_FOUND, "Not found", message);
            }

            team.getRoles().remove(UserRole.valueOf(role).toString());
            validateTeamData(service, team, false);

            service.setModifiedBy(user.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            service.update(team);
            service.add(AuditEntryHelper.removeRoleFromTeamEntry(team, role));
            service.commit();

        }
    }

    /**
     * Returns the team users.
     *
     * @param user the user
     * @param teamId the team id
     * @return the team users
     * @throws Exception the exception
     */
    public static ResultListUser getTeamUsers(final User user, final String teamId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final Team team = service.get(teamId, Team.class);
            final ResultListUser users = new ResultListUser();

            if (team == null) {
                
                final String message = "Unable to find team for id " + teamId + ".";
                logger.error(message);
                throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found", message);
            }

            for (final String userId : team.getMembers()) {

                final User u = service.get(userId, User.class);
                users.getItems().add(u);
            }

            users.setTotal(users.getItems().size());

            return users;
        }
    }
    
    /**
     * Check if this is a special organization level team
     *
     * @param user the user
     * @param team the team
     * @return is this a special organization level team
     * @throws Exception the exception
     */
    public static boolean isOrganizationTeam(final Team team) throws Exception {
        
        if (team.getName().equals(organizationLevelTeamPrefix + team.getOrganization().getName())) {
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Throw an exception if a user can't edit a team.
     *
     * @param user the user
     * @param team the team
     * @throws Exception the exception
     */
    public static void checkEditPermissions(final User user, final Team team) throws Exception {
        
        if (!canUserEditTeam(user, team)) {
            
            final String message = "User does not have permission to edit this team.";
            logger.error(message);
            throw new RestException(false, HttpStatus.UNAUTHORIZED, "Not Authorized", message);
        }
        
    }
    
    /**
     * Check if a user can edit a team.
     *
     * @param user the user
     * @param team the team
     * @return can the user edit the team
     * @throws Exception the exception
     */
    public static boolean canUserEditTeam(final User user, final Team team) throws Exception {
        
        final Organization organization = team.getOrganization();
        final boolean isOrganizationAdmin = user.doesUserHavePermission(User.ROLE_ADMIN, organization);
        
        if (isOrganizationAdmin || (team.getRoles().contains(User.ROLE_ADMIN) && team.getMembers().contains(user.getUserName()))){
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Check if a user can view a team.
     *
     * @param user the user
     * @param team the team
     * @return can the user view the team
     * @throws Exception the exception
     */
    public static boolean canUserViewTeam(final User user, final Team team) throws Exception {
        
        final Organization organization = team.getOrganization();
        final boolean isOrganizationAdmin = user.doesUserHavePermission(User.ROLE_ADMIN, organization);
        
        if (isOrganizationAdmin || team.getMembers().contains(user.getId())) {
            return true;
        } else {
            return false;
        }
    }

}
