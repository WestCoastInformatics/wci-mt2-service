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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.NotFoundException;

import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.ihtsdo.refsetservice.util.CrowdGroupNameAlgorithm;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class ProjectService.
 */
public class ProjectService extends BaseService {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(ProjectService.class);

    /**
     * Adds the project.
     *
     * @param user the user
     * @param project the project
     * @return the project
     * @throws Exception the exception
     */
    public static Project addProject(final User user, final Project project) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final Project localProject = (Project) project;

            localProject.setCrowdProjectId(CrowdGroupNameAlgorithm.getProjectString(localProject.getName()));
            service.setModifiedBy(user.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            service.add(localProject);
            service.add(AuditEntryHelper.newProjectEntry(localProject));
            service.commit();

            // Return the response
            return localProject;
        }
    }

    /**
     * Returns the project if active.
     *
     * @param projectId the project id
     * @param includeMembers the include members
     * @return the project
     * @throws Exception the exception
     */
    public static Project getProject(final String projectId, final boolean includeMembers) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final Project project = service.findSingle("id: " + projectId + " AND active:true", Project.class, null);

            if (project == null) {
                final String errorMessage = "Unable to find project for id " + projectId + "."; 
                logger.info(errorMessage);
                throw new NotFoundException(errorMessage);
            }

            if (includeMembers) {
                final Set<User> members = new HashSet<>();
                for (final String teamId : project.getTeams()) {
                    final Team team = service.get(teamId, Team.class);
                    if (team != null && team.getMembers() != null) {
                        for (final String userId : team.getMembers()) {
                            final User member = service.get(userId, User.class);
                            members.add(member);
                        }
                    }
                }
                project.getMemberList().addAll(members);
            }

            return project;
        }
    }

    /**
     * Search Projects.
     *
     * @param user the user
     * @param searchParameters the search parameters
     * @return the list of projects
     * @throws Exception the exception
     */
    public static ResultList<Project> searchProjects(final User user, final SearchParameters searchParameters) throws Exception {

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
                query = IndexUtility.addWildcardsToQuery(query, Refset.class);
            }

            final ResultList<Project> results = service.find(query, pfs, Project.class, null);
            results.setTimeTaken(System.currentTimeMillis() - start);
            results.setTotalKnown(true);

            final List<Project> projectList = new ArrayList<>(results.getItems());

            for (Project project : projectList) {
                project = setProjectPermissions(user, project);
                if (!project.getRoles().contains(User.ROLE_VIEWER)) {
                    results.getItems().remove(project);
                }
            }

            return results;
        }
    }

    /**
     * Update projects.
     *
     * @param user the user
     * @param projectId the project id
     * @param project the project
     * @return the project
     * @throws Exception the exception
     */
    public static Project updateProjects(final User user, final String projectId, final Project project) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            // Find the project
            final Project original = service.get(projectId, Project.class);

            if (original == null) {
                logger.info("Unable to find project for id {}.", projectId);
                throw new NotFoundException();
            }

            service.setModifiedBy(user.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            // Apply changes
            original.patchFrom(project);

            // Update
            service.update(original);
            service.add(AuditEntryHelper.updateProjectEntry(original));
            service.commit();

            return original;
        }
    }

    /**
     * Inactivate project. Also inactivates teams and refsets associated with the project.
     *
     * @param user the user
     * @param projectId the project id
     * @throws Exception the exception
     */
    public static void inactivateProject(final User user, final String projectId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            // Find the object
            final Project project = service.get(projectId, Project.class);
            if (project == null) {
                logger.info("Unable to find project for id {}.", projectId);
                throw new NotFoundException();
            }

            service.setModifiedBy(user.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            // inactivate projects, clear teams, and inactivate refsets
            project.setActive(false);

            if (project.getTeams() != null && !project.getTeams().isEmpty()) {
                for (String teamId : project.getTeams()) {
                    final Team team = service.get(teamId, Team.class);
                    if (team != null && !team.getMembers().isEmpty()) {
                        team.getMembers().clear();
                        service.update(team);
                    }
                }
                project.getTeams().clear();
            }

            // also inactivate refsets
            final ResultList<Refset> projRefsets = service.find("projectId:" + project.getId() + " AND active:true", null, Refset.class, null);
            if (projRefsets.getItems() != null && !projRefsets.getItems().isEmpty()) {
                for (Refset refset : projRefsets.getItems()) {
                    if (refset != null && !projRefsets.getItems().isEmpty()) {
                        refset.setActive(false);
                        service.update(refset);
                        service.add(AuditEntryHelper.inactivateRefsetEntry(refset));
                    }
                }
            }

            service.update(project);
            service.add(AuditEntryHelper.inactivateProjectEntry(project));
            service.commit();
        }
    }

    /**
     * Set the user permissions for a refset.
     *
     * @param user the user
     * @param project the project
     * @return the refset with permissions
     * @throws Exception the exception
     */
    public static Project setProjectPermissions(final User user, final Project project) throws Exception {

        final List<String> roles = project.getRoles();
        setRoles(user, project, roles);
        project.setRoles(roles);

        return project;
    }

    /**
     * set the list of roles a user has for a project.
     *
     * @param user the user
     * @param project the project
     * @param roles the role list to populate
     * @return the list of roles for the project
     * @throws Exception the exception
     */
    private static List<String> setRoles(final User user, final Project project, final List<String> roles) throws Exception {

        boolean giveViewerRole = false;

        if (user.doesUserHavePermission(User.ROLE_AUTHOR, project)) {

            roles.add(User.ROLE_AUTHOR);
            giveViewerRole = true;
        }

        if (user.doesUserHavePermission(User.ROLE_REVIEWER, project)) {

            roles.add(User.ROLE_REVIEWER);
            giveViewerRole = true;
        }

        if (user.doesUserHavePermission(User.ROLE_ADMIN, project)) {

            roles.add(User.ROLE_ADMIN);
            giveViewerRole = true;
        }

        if (user.doesUserHavePermission(User.ROLE_VIEWER, project) || giveViewerRole) {

            roles.add(User.ROLE_VIEWER);
        }

        return roles;
    }

}
