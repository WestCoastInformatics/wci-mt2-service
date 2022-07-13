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

import javax.ws.rs.ForbiddenException;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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

            RefsetService.setProjectPermissions(user, project);
            checkPermissions(user, project);

            project.setCrowdProjectId(CrowdGroupNameAlgorithm.getProjectString(project.getName()));
            service.setModifiedBy(user.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            service.add(project);
            service.add(AuditEntryHelper.newProjectEntry(project));
            service.commit();

            // Return the response
            return project;
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
     * Returns the project names for organization.
     *
     * @param organizationId the organization id
     * @return the project names for organization
     * @throws Exception the exception
     */
    public static Set<String> getProjectNamesForOrganization(final String organizationId) throws Exception {

        final Set<String> projectNames = new HashSet<>();

        try (final TerminologyService service = new TerminologyService()) {

            final ResultList<Project> projects = service.find("organization.id: " + organizationId + " AND active:true", null, Project.class, null);

            if (projects == null) {
                return projectNames;
            }

            projects.getItems().forEach(project -> {
                projectNames.add(project.getName());
            });

            return projectNames;
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
                project = RefsetService.setProjectPermissions(user, project);
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
            final Project existingProject = getProject(projectId, true);
            
            RefsetService.setProjectPermissions(user, existingProject);
            checkPermissions(user, project);

            service.setModifiedBy(user.getUserName());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            // Apply changes
            existingProject.patchFrom(project);

            // Update
            service.update(existingProject);
            service.add(AuditEntryHelper.updateProjectEntry(existingProject));
            service.commit();

            return existingProject;
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
            final Project project = getProject(projectId, true);
            
            RefsetService.setProjectPermissions(user, project);
            checkPermissions(user, project);

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
     * Check if a user can edit a project.
     *
     * @param user the user
     * @param project the project
     * @throws Exception the exception
     */
    public static void checkPermissions(final User user, final Project project) throws Exception {
        
        if (!project.getRoles().contains(User.ROLE_ADMIN)) {
            
            logger.error("User does not have permission to edit this project.");
            throw new ForbiddenException("User does not have permission to edit this project.");
        }
        
    }
}
