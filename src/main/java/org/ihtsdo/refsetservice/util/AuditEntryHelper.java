/*
 * Copyright 2022 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.util;

import org.ihtsdo.refsetservice.model.AuditEntry;
import org.ihtsdo.refsetservice.model.DiscussionThread;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.WorkflowHistory;
import org.ihtsdo.refsetservice.rest.TeamController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class AuditEntryHelper.
 */
public class AuditEntryHelper {

    /** Logger. */
    private static Logger logger = LoggerFactory.getLogger(TeamController.class);

    /**
     * Log.
     *
     * @param entry the entry
     */
    private static void log(final AuditEntry entry) {

        logger.info("AUDIT " + entry.toLogString());
    }

    /**
     * New edition entry.
     *
     * @param edition the edition
     * @return the audit entry
     */
    // Edition
    public static AuditEntry newEditionEntry(final Edition edition) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("ADD Edition");
        entry.setDetails(edition.getName());
        log(entry);
        return entry;
    }

    /**
     * Update edition entry.
     *
     * @param edition the edition
     * @return the audit entry
     */
    public static AuditEntry updateEditionEntry(final Edition edition) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("UPDATE Edition");
        entry.setDetails(edition.getName());
        log(entry);
        return entry;
    }

    /**
     * Inactivate edition entry.
     *
     * @param edition the edition
     * @return the audit entry
     */
    public static AuditEntry inactivateEditionEntry(final Edition edition) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("INACTIVATE Edition");
        entry.setDetails(edition.getName());
        log(entry);
        return entry;
    }

    /**
     * New organization entry.
     *
     * @param organization the organization
     * @return the audit entry
     */
    // Organization
    public static AuditEntry newOrganizationEntry(final Organization organization) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("NEW Organization");
        entry.setDetails(organization.getName());
        log(entry);
        return entry;
    }

    /**
     * Update organization entry.
     *
     * @param organization the organization
     * @return the audit entry
     */
    public static AuditEntry updateOrganizationEntry(final Organization organization) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("UPDATE Organization");
        entry.setDetails(organization.getName());
        log(entry);
        return entry;
    }

    /**
     * Inactivate organization entry.
     *
     * @param organization the organization
     * @return the audit entry
     */
    public static AuditEntry inactivateOrganizationEntry(final Organization organization) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("INACTIVATE Organization");
        entry.setDetails(organization.getName());
        log(entry);
        return entry;
    }

    /**
     * Adds the user to organization entry.
     *
     * @param organization the organization
     * @param user the user
     * @return the audit entry
     */
    public static AuditEntry addUserToOrganizationEntry(final Organization organization, final User user) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("UPDATE Organization");
        entry.setDetails("Add user " + user.getName() + " to organization " + organization.getName() + ".");
        log(entry);
        return entry;
    }

    /**
     * Removes the user from organization entry.
     *
     * @param organization the organization
     * @param user the user
     * @return the audit entry
     */
    public static AuditEntry removeUserFromOrganizationEntry(final Organization organization, final User user) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("UPDATE Organization");
        entry.setDetails("Remove user " + user.getName() + " form organization " + organization.getName() + ".");
        log(entry);
        return entry;
    }

    /**
     * Update icon for organization entry.
     *
     * @param organization the organization
     * @param fileName the file name
     * @return the audit entry
     */
    public static AuditEntry updateIconForOrganizationEntry(final Organization organization, final String fileName) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("UPDATE Organization");
        entry.setDetails("Change icon for organization " + organization.getName() + " to " + fileName + ".");
        log(entry);
        return entry;
    }

    /**
     * New project entry.
     *
     * @param project the project
     * @return the audit entry
     */
    // Project
    public static AuditEntry newProjectEntry(final Project project) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("NEW Project");
        entry.setDetails(project.getName());
        log(entry);
        return entry;
    }

    /**
     * Update project entry.
     *
     * @param project the project
     * @return the audit entry
     */
    public static AuditEntry updateProjectEntry(final Project project) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("UPDATE Project");
        entry.setDetails(project.getName());
        log(entry);
        return entry;
    }

    /**
     * Inactivate project entry.
     *
     * @param project the project
     * @return the audit entry
     */
    public static AuditEntry inactivateProjectEntry(final Project project) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("INACTIVATE Project");
        entry.setDetails(project.getName());
        log(entry);
        return entry;
    }

    /**
     * New team entry.
     *
     * @param team the team
     * @return the audit entry
     */
    // Team
    public static AuditEntry newTeamEntry(final Team team) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("NEW Team");
        entry.setDetails(team.getName());
        log(entry);
        return entry;
    }

    /**
     * Update team entry.
     *
     * @param team the team
     * @return the audit entry
     */
    public static AuditEntry updateTeamEntry(final Team team) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("UPDATE Team");
        entry.setDetails(team.getName());
        log(entry);
        return entry;
    }

    /**
     * Inactivate team entry.
     *
     * @param team the team
     * @return the audit entry
     */
    public static AuditEntry inactivateTeamEntry(final Team team) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("INACTIVATE Team");
        entry.setDetails(team.getName());
        log(entry);
        return entry;
    }

    /**
     * Adds the role to team entry.
     *
     * @param team the team
     * @param role the role
     * @return the audit entry
     */
    public static AuditEntry addRoleToTeamEntry(final Team team, final String role) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("UPDATE Team");
        entry.setDetails("Add role " + role + " to team " + team.getName());
        log(entry);
        return entry;
    }

    /**
     * Removes the role from team entry.
     *
     * @param team the team
     * @param role the role
     * @return the audit entry
     */
    public static AuditEntry removeRoleFromTeamEntry(final Team team, final String role) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("UPDATE Team");
        entry.setDetails("Remove role " + role + " from team " + team.getName());
        log(entry);
        return entry;
    }

    /**
     * Adds the user to team entry.
     *
     * @param team the team
     * @param user the user
     * @return the audit entry
     */
    public static AuditEntry addUserToTeamEntry(final Team team, final User user) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("UPDATE Team");
        entry.setDetails("Add user " + user.getName() + " to team " + team.getName());
        log(entry);
        return entry;
    }

    /**
     * Removes the user from team entry.
     *
     * @param team the team
     * @param user the user
     * @return the audit entry
     */
    public static AuditEntry removeUserFromTeamEntry(final Team team, final User user) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("UPDATE Team");
        entry.setDetails("Remove user " + user.getName() + " from team " + team.getName());
        log(entry);
        return entry;
    }

    /**
     * New user entry.
     *
     * @param user the user
     * @return the audit entry
     */
    // User
    public static AuditEntry newUserEntry(final User user) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("NEW User");
        entry.setDetails(user.getName());
        log(entry);
        return entry;
    }

    /**
     * Update user entry.
     *
     * @param user the user
     * @return the audit entry
     */
    public static AuditEntry updateUserEntry(final User user) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("UPDATE User");
        entry.setDetails(user.getName());
        log(entry);
        return entry;
    }

    /**
     * Inactivate user entry.
     *
     * @param user the user
     * @return the audit entry
     */
    public static AuditEntry inactivateUserEntry(final User user) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("INACTIVATE User");
        entry.setDetails(user.getName());
        log(entry);
        return entry;
    }

    /**
     * New refset entry.
     *
     * @param refset the refset
     * @return the audit entry
     */
    // Refset
    public static AuditEntry newRefsetEntry(final Refset refset) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("NEW Refset");
        entry.setDetails(refset.getName());
        log(entry);
        return entry;
    }

    /**
     * Update refset entry.
     *
     * @param refset the refset
     * @return the audit entry
     */
    public static AuditEntry updateRefsetEntry(final Refset refset) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("UPDATE Refset");
        entry.setDetails(refset.getName());
        log(entry);
        return entry;
    }

    /**
     * Inactivate refset entry.
     *
     * @param refset the refset
     * @return the audit entry
     */
    public static AuditEntry inactivateRefsetEntry(final Refset refset) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("INACTIVATE Refset");
        entry.setDetails(refset.getName());
        log(entry);
        return entry;
    }

    /**
     * Post discussion thread entry.
     *
     * @param discussionThread the discussion thread
     * @return the audit entry
     */
    // Discussion/Collaboration
    public static AuditEntry postDiscussionThreadEntry(final DiscussionThread discussionThread) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("NEW Collaboration Thread");
        entry.setDetails(discussionThread.getSubject());
        log(entry);
        return entry;
    }

    /**
     * Complete refset publication entry.
     *
     * @param refset the refset
     * @return the audit entry
     */
    // Workflow
    public static AuditEntry completeRefsetPublicationEntry(final Refset refset) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("UPDATE Refset");
        entry.setDetails("Complete Publication on refset " + refset.getRefsetId());
        log(entry);
        return entry;
    }

    /**
     * Status update refset entry.
     *
     * @param refset the refset
     * @return the audit entry
     */
    public static AuditEntry statusUpdateRefsetEntry(final Refset refset) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("UPDATE Refset");
        entry.setDetails("Refset workflow status set to " + refset.getVersionStatus() + " for refset " + refset.getRefsetId() + ".");
        log(entry);
        return entry;
    }

    /**
     * Adds the workflow history entry.
     *
     * @param workflowHistory the workflow history
     * @param refset the refset
     * @return the audit entry
     */
    public static AuditEntry addWorkflowHistoryEntry(final WorkflowHistory workflowHistory, final Refset refset) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("NEW Workflow History");
        entry.setDetails("New workflow history entry with status " + refset.getWorkflowStatus() + " added for refset " + refset.getRefsetId() + ".");
        log(entry);
        return entry;
    }

    /**
     * Update workflow note entry.
     *
     * @param workflowHistory the workflow history
     * @param refset the refset
     * @return the audit entry
     */
    public static AuditEntry updateWorkflowNoteEntry(final WorkflowHistory workflowHistory, final Refset refset) {

        final AuditEntry entry = new AuditEntry();
        entry.setMessage("NEW Workflow History");
        entry.setDetails("Note for workflow history entry with status " + refset.getWorkflowStatus() + " updated for refset " + refset.getRefsetId() + ".");
        log(entry);
        return entry;
    }

}
