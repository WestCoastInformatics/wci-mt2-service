/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.util;

import java.util.Date;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.AdditionalMapEntryInfo;
import org.ihtsdo.refsetservice.model.AuditEntry;
import org.ihtsdo.refsetservice.model.DiscussionThread;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.HasModified;
import org.ihtsdo.refsetservice.model.MapAdvice;
import org.ihtsdo.refsetservice.model.MapEntry;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapRelation;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.WorkflowHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The Class AuditEntryHelper.
 */
public final class AuditEntryHelper {

    /**
     * The Enum EntityType.
     */
    public static enum EntityType {

        /** The edition. */
        EDITION,
        /** The organization. */
        ORGANIZATION,
        /** The project. */
        PROJECT,
        /** The team. */
        TEAM,
        /** The refset. */
        REFSET,
        /** The user. */
        USER,
        /** The discussion. */
        DISCUSSION,
        /** The sync. */
        SYNC,
        /** The Map Project. */
        MAP_PROJECT,
        /** The map adivce. */
        MAP_ADVICE,
        /** The map rule. */
        MAP_RULE,
        /** The map relation. */
        MAP_RELATION,
        /** The map entry. */
        MAP_ENTRY
    }

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(AuditEntryHelper.class);

    /** The object mapper. */
    private static ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Log.
     *
     * @param entry the entry
     */
    private static void log(final AuditEntry entry) {

        LOG.info(entry.toLogString());
    }

    /**
     * Instantiates an empty {@link AuditEntryHelper}.
     */
    private AuditEntryHelper() {

        // n/a
    }

    /**
     * New edition entry.
     *
     * @param edition the edition
     * @return the audit entry
     */
    public static AuditEntry addEditionEntry(final Edition edition) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.EDITION.toString());
        entry.setEntityId(edition.getId());
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
        entry.setEntityType(EntityType.EDITION.toString());
        entry.setEntityId(edition.getId());
        entry.setMessage("UPDATE Edition");
        entry.setDetails(edition.getName());
        log(entry);
        return entry;
    }

    /**
     * Change edition entry.
     *
     * @param edition the edition
     * @return the audit entry
     */
    public static AuditEntry changeEditionStatusEntry(final Edition edition) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.EDITION.toString());
        entry.setEntityId(edition.getId());

        if (edition.isActive()) {
            entry.setMessage("REACTIVATE Edition");
        } else {
            entry.setMessage("INACTIVATE Edition");
        }

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
    public static AuditEntry addOrganizationEntry(final Organization organization) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.ORGANIZATION.toString());
        entry.setEntityId(organization.getId());
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
        entry.setEntityType(EntityType.ORGANIZATION.toString());
        entry.setEntityId(organization.getId());
        entry.setMessage("UPDATE Organization");
        entry.setDetails(organization.getName());
        log(entry);
        return entry;
    }

    /**
     * Change organization status entry.
     *
     * @param organization the organization
     * @return the audit entry
     */
    public static AuditEntry changeOrganizationStatusEntry(final Organization organization) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.ORGANIZATION.toString());
        entry.setEntityId(organization.getId());

        if (organization.isActive()) {
            entry.setMessage("REACTIVATE Organization");
        } else {
            entry.setMessage("INACTIVATE Organization");
        }

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
        entry.setEntityType(EntityType.ORGANIZATION.toString());
        entry.setEntityId(organization.getId());
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
        entry.setEntityType(EntityType.ORGANIZATION.toString());
        entry.setEntityId(organization.getId());
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
        entry.setEntityType(EntityType.ORGANIZATION.toString());
        entry.setEntityId(organization.getId());
        entry.setMessage("UPDATE Organization");
        entry.setDetails("Change icon for organization " + organization.getName() + " to " + fileName + ".");
        log(entry);
        return entry;
    }

    /**
     * Send organization invite.
     *
     * @param organization the organization
     * @param requester the requester
     * @param recipientEmail the recipient email
     * @return the audit entry
     */
    public static AuditEntry sendOrganizationInvite(final Organization organization, final User requester, final String recipientEmail) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.REFSET.toString());
        entry.setEntityId(organization.getId());
        entry.setMessage("INVITE Organization");
        entry.setDetails("User " + requester.getUserName() + " sent request for " + recipientEmail + " to join organization " + organization.getId());
        log(entry);
        return entry;
    }

    /**
     * Response for refset invite.
     *
     * @param organization the organization
     * @param requester the requester
     * @param recipientEmail the recipient email
     * @param acceptance the acceptance
     * @return the audit entry
     */
    public static AuditEntry responseForOrganizationInvite(final Organization organization, final User requester, final String recipientEmail,
        final boolean acceptance) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.REFSET.toString());
        entry.setEntityId(organization.getId());
        entry.setMessage("INVITE Organization Response");
        entry.setDetails("Recipient " + recipientEmail + " " + (acceptance ? " accepted " : " decline ") + " user's " + requester.getUserName()
            + " request to join organization " + organization.getId());
        log(entry);
        return entry;
    }

    // /**
    // * Update icon for organization entry.
    // *
    // * @param organization the organization
    // * @param fileName the file name
    // * @return the audit entry
    // */
    // public static AuditEntry emailOrganizationEntry(final Organization organization, final String action, final String email) {
    //
    // final AuditEntry entry = new AuditEntry();
    // entry.setEntityType(EntityType.ORGANIZATION.toString());
    // entry.setEntityId(organization.getId());
    // entry.setMessage("INVITE Organization");
    // entry.setDetails(action + " " + email + " to organization " + organization.getName() + ".");
    // log(entry);
    // return entry;
    // }

    /**
     * New project entry.
     *
     * @param project the project
     * @return the audit entry
     */
    // Project
    public static AuditEntry addProjectEntry(final Project project) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.PROJECT.toString());
        entry.setEntityId(project.getId());
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
        entry.setEntityType(EntityType.PROJECT.toString());
        entry.setEntityId(project.getId());
        entry.setMessage("UPDATE Project");
        entry.setDetails(project.getName());
        log(entry);
        return entry;
    }

    /**
     * Change project status entry.
     *
     * @param project the project
     * @return the audit entry
     */
    public static AuditEntry changeProjectStatusEntry(final Project project) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.PROJECT.toString());
        entry.setEntityId(project.getId());

        if (project.isActive()) {
            entry.setMessage("REACTIVATE Project");
        } else {
            entry.setMessage("INACTIVATE Project");
        }

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
    public static AuditEntry addTeamEntry(final Team team) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.TEAM.toString());
        entry.setEntityId(team.getId());
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
        entry.setEntityType(EntityType.TEAM.toString());
        entry.setEntityId(team.getId());
        entry.setMessage("UPDATE Team");
        entry.setDetails(team.getName());
        log(entry);
        return entry;
    }

    /**
     * Change team status entry.
     *
     * @param team the team
     * @return the audit entry
     */
    public static AuditEntry changeTeamStatusEntry(final Team team) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.TEAM.toString());
        entry.setEntityId(team.getId());

        if (team.isActive()) {
            entry.setMessage("REACTIVATE Team");
        } else {
            entry.setMessage("INACTIVATE Team");
        }

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
        entry.setEntityType(EntityType.TEAM.toString());
        entry.setEntityId(team.getId());
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
        entry.setEntityType(EntityType.TEAM.toString());
        entry.setEntityId(team.getId());
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
        entry.setEntityType(EntityType.TEAM.toString());
        entry.setEntityId(team.getId());
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
        entry.setEntityType(EntityType.TEAM.toString());
        entry.setEntityId(team.getId());
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
    public static AuditEntry addUserEntry(final User user) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.USER.toString());
        entry.setEntityId(user.getId());
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
        entry.setEntityType(EntityType.USER.toString());
        entry.setEntityId(user.getId());
        entry.setMessage("UPDATE User");
        entry.setDetails(user.getName());
        log(entry);
        return entry;
    }

    /**
     * Change user status entry.
     *
     * @param user the user
     * @return the audit entry
     */
    public static AuditEntry changeUserStatusEntry(final User user) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.USER.toString());
        entry.setEntityId(user.getId());

        if (user.isActive()) {
            entry.setMessage("REACTIVATE User");
        } else {
            entry.setMessage("INACTIVATE User");
        }

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
    public static AuditEntry addRefsetVersionEntry(final Refset refset) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.REFSET.toString());
        entry.setEntityId(refset.getId());
        entry.setMessage("NEW Refset");
        entry.setDetails(refset.getName());
        log(entry);
        return entry;
    }

    /**
     * Update multiple refset versions entry.
     *
     * @param refsetList the refset list
     * @return the checks for modified
     */
    public static HasModified updateMultipleRefsetVersionsEntry(final String refsetList) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.REFSET.toString());
        entry.setEntityId("Multiple refsets updated");
        entry.setMessage("UPDATE Multiple Refsets");
        entry.setDetails(refsetList);
        log(entry);
        return entry;
    }

    /**
     * Update refset entry.
     *
     * @param refset the refset
     * @return the audit entry
     */
    public static AuditEntry updateRefsetVersionEntry(final Refset refset) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.REFSET.toString());
        entry.setEntityId(refset.getId());
        entry.setMessage("UPDATE Refset");
        entry.setDetails(refset.getName());
        log(entry);
        return entry;
    }

    /**
     * Change refset status entry.
     *
     * @param refset the refset
     * @return the audit entry
     */
    public static AuditEntry changeRefsetStatusEntry(final Refset refset) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.REFSET.toString());
        entry.setEntityId(refset.getId());

        if (refset.isActive()) {
            entry.setMessage("REACTIVATE Refset");
        } else {
            entry.setMessage("INACTIVATE Refset");
        }

        entry.setDetails(refset.getName());
        log(entry);
        return entry;
    }

    /**
     * Reset refset entry.
     *
     * @param refset the refset
     * @return the audit entry
     */
    public static AuditEntry resetRefsetEntry(final Refset refset) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.REFSET.toString());
        entry.setEntityId(refset.getRefsetId());
        entry.setMessage("RESET all versions of Refset");
        entry.setDetails(refset.getName());
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
        entry.setEntityType(EntityType.REFSET.toString());
        entry.setEntityId(refset.getId());
        entry.setMessage("UPDATE Refset");
        entry.setDetails("Complete Publication on refset " + refset.getRefsetId());
        log(entry);
        return entry;
    }

    /**
     * Update refset entry.
     *
     * @param refset the refset
     * @param eclUpdated the ecl updated
     * @return the audit entry
     */
    public static AuditEntry updateRefsetMetadataEntry(final Refset refset, final boolean eclUpdated) {

        final String eclInfo = (eclUpdated) ? "including" : "not including";

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.REFSET.toString());
        entry.setEntityId(refset.getId());
        entry.setMessage("UPDATE Refset");
        entry.setDetails("Refset " + refset.getRefsetId() + " metadata changed " + eclInfo + " the ECL definition");
        log(entry);
        return entry;
    }

    /**
     * Add Refset Members entry.
     *
     * @param refset the refset
     * @param additionalInformation the additional information
     * @param conceptIds the concept ids
     * @return the audit entry
     */
    public static AuditEntry addMembersEntry(final Refset refset, final String additionalInformation, final String conceptIds) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.REFSET.toString());
        entry.setEntityId(refset.getId());
        entry.setMessage("UPDATE Refset");
        entry.setDetails("Refset " + refset.getRefsetId() + " modified by adding " + additionalInformation + ": " + conceptIds);
        log(entry);
        return entry;
    }

    /**
     * Remove Refset Members entry.
     *
     * @param refset the refset
     * @param additionalInformation the additional information
     * @param conceptIds the concept ids
     * @return the audit entry
     */
    public static AuditEntry removeMembersEntry(final Refset refset, final String additionalInformation, final String conceptIds) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.REFSET.toString());
        entry.setEntityId(refset.getId());
        entry.setMessage("UPDATE Refset");
        entry.setDetails("Refset " + refset.getRefsetId() + " modified by removing " + additionalInformation + ": " + conceptIds);
        log(entry);

        return entry;
    }

    /**
     * Adds editing cycle entry.
     *
     * @param refset the refset
     * @param isSaving the is saving
     * @return the audit entry
     */
    public static AuditEntry addEditingCycleEntry(final Refset refset, final boolean isSaving) {

        final String type = (isSaving) ? "saved" : " canceled";

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.REFSET.toString());
        entry.setEntityId(refset.getId());
        entry.setMessage("UPDATE Refset");
        entry.setDetails("Refset " + refset.getRefsetId() + " changes since refset was put IN_EDIT have been " + type);
        log(entry);

        return entry;
    }

    /**
     * Send refset invite.
     *
     * @param refset the refset
     * @param requester the requester
     * @param recipientEmail the recipient email
     * @return the audit entry
     */
    public static AuditEntry sendRefsetInvite(final Refset refset, final User requester, final String recipientEmail) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.REFSET.toString());
        entry.setEntityId(refset.getId());
        entry.setMessage("INVITE Refset");
        entry.setDetails("User " + requester.getUserName() + " sent request for " + recipientEmail + " to join refset " + refset.getRefsetId());
        log(entry);

        return entry;
    }

    /**
     * Response for refset invite.
     *
     * @param refset the refset
     * @param requester the requester
     * @param recipientEmail the recipient email
     * @param acceptance the acceptance
     * @return the audit entry
     */
    public static AuditEntry responseForRefsetInvite(final Refset refset, final User requester, final String recipientEmail, final boolean acceptance) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.REFSET.toString());
        entry.setEntityId(refset.getId());
        entry.setMessage("INVITE Refset Response");
        entry.setDetails("Recipient " + recipientEmail + " " + (acceptance ? " accepted " : " decline ") + " user's " + requester.getUserName()
            + " request to join refset " + refset.getRefsetId());
        log(entry);

        return entry;
    }

    /**
     * Adds the workflow history entry.
     *
     * @param workflowHistory the workflow history
     * @param refset the refset
     * @param newState the new state
     * @return the audit entry
     */
    public static AuditEntry addWorkflowHistoryEntry(final WorkflowHistory workflowHistory, final Refset refset, final String newState) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.REFSET.toString());
        entry.setEntityId(refset.getId());
        entry.setMessage("NEW Workflow History");
        entry.setDetails("Refset " + refset.getRefsetId() + " has advanced workflow to " + newState);
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
        entry.setEntityType(EntityType.REFSET.toString());
        entry.setEntityId(refset.getId());
        entry.setMessage("NEW Workflow History");
        entry.setDetails("Note for workflow history entry with status " + refset.getWorkflowStatus() + " updated for refset " + refset.getRefsetId() + ".");
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
        entry.setEntityType(EntityType.DISCUSSION.toString());
        entry.setEntityId(discussionThread.getId());
        entry.setMessage("NEW Collaboration Thread");
        entry.setDetails(discussionThread.getSubject());
        log(entry);
        return entry;
    }

    /**
     * Convert to extensional refset entry.
     *
     * @param refset the refset
     * @return the checks for modified
     */
    public static HasModified convertToExtensionalRefsetEntry(final Refset refset) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.REFSET.toString());
        entry.setEntityId(refset.getId());
        entry.setMessage("Convert Intensional to Extensional");
        entry.setDetails("Note for converting intensional to extensional refset for refset " + refset.getRefsetId() + ".");
        log(entry);
        return entry;
    }

    /**
     * Send communication email entry.
     *
     * @param refset the refset
     * @param action the action
     * @param from the from
     * @param to the to
     * @return the checks for modified
     */
    public static HasModified sendCommunicationEmailEntry(final Refset refset, final String action, final String from, final String to) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.REFSET.toString());
        entry.setEntityId(refset.getId());
        entry.setMessage(action);
        entry.setDetails(action + " from: " + from + " to " + to + " for refset " + refset.getRefsetId() + ".");
        log(entry);
        return entry;
    }

    /**
     * Sync begin entry.
     *
     * @param date the date
     * @return the checks for modified
     */
    public static HasModified syncBeginEntry(final Date date) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.SYNC.toString());
        entry.setEntityId("");
        entry.setMessage("Sync starting");
        entry.setDetails("Start date is " + date.getTime());
        log(entry);
        return entry;
    }

    /**
     * Sync finish entry.
     *
     * @param date the date
     * @param processingMinutes the processing minutes
     * @return the checks for modified
     */
    public static HasModified syncFinishEntry(final Date date, final long processingMinutes) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.SYNC.toString());
        entry.setEntityId("");
        entry.setMessage("Sync completed successfully in " + processingMinutes + " minutes");
        entry.setDetails("Finish date is " + date.getTime());
        log(entry);
        return entry;
    }

    // MapAdvice
    /**
     * Adds the map advice entry.
     *
     * @param mapAdvice the map advice
     * @return the audit entry
     */
    public static AuditEntry addMapAdviceEntry(final MapAdvice mapAdvice) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.MAP_ADVICE.toString());
        entry.setEntityId(mapAdvice.getId());
        entry.setMessage("ADD MapAdvice");
        entry.setDetails(mapAdvice.toString());
        log(entry);
        return entry;
    }

    /**
     * Update map advice entry.
     *
     * @param mapAdvice the map advice
     * @return the audit entry
     */
    public static AuditEntry updateMapAdviceEntry(final MapAdvice mapAdvice) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.MAP_ADVICE.toString());
        entry.setEntityId(mapAdvice.getId());
        entry.setMessage("UPDATE MapAdvice");
        entry.setDetails(mapAdvice.toString());
        log(entry);
        return entry;
    }

    /**
     * Delete map advice entry.
     *
     * @param mapAdvice the map advice
     * @return the audit entry
     */
    public static AuditEntry deleteMapAdviceEntry(final MapAdvice mapAdvice) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.MAP_ADVICE.toString());
        entry.setEntityId(mapAdvice.getId());
        entry.setMessage("DELETE MapAdvice");
        entry.setDetails(mapAdvice.getName());
        log(entry);
        return entry;
    }

    // MapProject
    /**
     * Adds the map project entry.
     *
     * @param mapProject the map project
     * @return the audit entry
     */
    public static AuditEntry addMapProjectEntry(final MapProject mapProject) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.MAP_PROJECT.toString());
        entry.setEntityId(mapProject.getId());
        entry.setMessage("ADD MapProject");
        entry.setDetails(mapProject.toString());
        log(entry);
        return entry;
    }

    /**
     * Update map advice entry.
     *
     * @param mapProject the map project
     * @return the audit entry
     */
    public static AuditEntry updateMapProjectEntry(final MapProject mapProject) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.MAP_PROJECT.toString());
        entry.setEntityId(mapProject.getId());
        entry.setMessage("UPDATE MapProject");
        entry.setDetails(mapProject.toString());
        log(entry);
        return entry;
    }

    /**
     * Change map project status entry.
     *
     * @param mapProject the map project
     * @return the audit entry
     */
    public static AuditEntry changeMapProjectStatusEntry(final MapProject mapProject) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.PROJECT.toString());
        entry.setEntityId(mapProject.getId());

        if (mapProject.isActive()) {
            entry.setMessage("REACTIVATE Project");
        } else {
            entry.setMessage("INACTIVATE Project");
        }

        entry.setDetails(mapProject.getName());
        log(entry);
        return entry;
    }

    /**
     * Delete map advice entry.
     *
     * @param mapProject the map project
     * @return the audit entry
     */
    public static AuditEntry deleteMapProjectEntry(final MapProject mapProject) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.MAP_PROJECT.toString());
        entry.setEntityId(mapProject.getId());
        entry.setMessage("DELETE MapProject");
        entry.setDetails(mapProject.getName());
        log(entry);
        return entry;
    }

    // ############ Mappings
    // Map Entry
    /**
     * Adds the mapping entry.
     *
     * @param refsetId the refset id
     * @param mapping the mapping
     * @param mapEntry the map entry
     * @return the audit entry
     */

    public static AuditEntry addMappingEntry(final String refsetId, final Mapping mapping, final MapEntry mapEntry) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.REFSET.toString());
        entry.setEntityId(refsetId);
        entry.setMessage("ADD MapEntry");
        entry.setDetails("Add map entry from concept " + mapping.getCode() + " to "
            + (StringUtils.isNotBlank(mapEntry.getToCode()) ? mapEntry.getToCode() : "NO TARGET") + " ");
        log(entry);
        return entry;
    }

    /**
     * Update mapping entry.
     *
     * @param refsetId the refset id
     * @param mapping the mapping
     * @param updatedMapEntry the updated map entry
     * @param oldMapEntry the old map entry
     * @return the audit entry
     */
    public static AuditEntry updateMappingEntry(final String refsetId, final Mapping mapping, final MapEntry updatedMapEntry, final MapEntry oldMapEntry) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.REFSET.toString());
        entry.setEntityId(refsetId);
        entry.setMessage("UPDATE MapEntry");

        try {
            final ArrayNode differences = computeDetailsMapEntry(updatedMapEntry, oldMapEntry);

            if (differences.size() > 0) {
                final ObjectNode detailsObject = objectMapper.createObjectNode();
                detailsObject.put("concept", mapping.getCode());
                detailsObject.set("changes", differences);
                entry.setDetails(detailsObject.toString());
                log(entry);
            }

        } catch (Exception e) {
            LOG.error("Error computing differences for map entry: " + e.getMessage());
        }

        return entry;

    }

    /**
     * Removes the mapping entry.
     *
     * @param refsetId the refset id
     * @param mapping the mapping
     * @param mapEntry the map entry
     * @return the audit entry
     */
    public static AuditEntry statusChangeMappingEntry(final String refsetId, final Mapping mapping, final MapEntry mapEntry) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.REFSET.toString());
        entry.setEntityId(refsetId);
        entry.setMessage("UPDATE MapEntry");
        entry.setDetails("Map entry for concept " + mapping.getCode() + ((mapEntry.isActive()) ? " activated." : " inactivated."));
        log(entry);
        return entry;
    }

    /**
     * Removes the mapping entry.
     *
     * @param refsetId the refset id
     * @param mapping the mapping
     * @param mapEntry the map entry
     * @return the audit entry
     */
    public static AuditEntry deleteMappingEntry(final String refsetId, final Mapping mapping, final MapEntry mapEntry) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.REFSET.toString());
        entry.setEntityId(refsetId);
        entry.setMessage("DELETE MapEntry");
        entry.setDetails("Delete map entry for concept " + mapping.getCode() + " mapped to "
            + (StringUtils.isNotBlank(mapEntry.getToCode()) ? mapEntry.getToCode() : "NO TARGET") + ".");
        log(entry);
        return entry;
    }

    /**
     * Compute log string map entries.
     *
     * @param newEntry the new entry
     * @param oldEntry the old entry
     * @return the string
     * @throws Exception the exception
     */
    private static ArrayNode computeDetailsMapEntry(final MapEntry newEntry, final MapEntry oldEntry) throws Exception {

        final ArrayNode changesArray = objectMapper.createArrayNode();

        if (!Objects.equals(oldEntry.getToCode(), newEntry.getToCode())) {
            changesArray.add(createChangeObject("ToCode", oldEntry.getToCode(), newEntry.getToCode()));
        }
        if (!Objects.equals(oldEntry.getToName(), newEntry.getToName())) {
            changesArray.add(createChangeObject("ToName", oldEntry.getToName(), newEntry.getToName()));
        }
        if (!Objects.equals(oldEntry.getRelation(), newEntry.getRelation())) {
            changesArray.add(createChangeObject("Relation", oldEntry.getRelation(), newEntry.getRelation()));
        }
        if (!Objects.equals(oldEntry.getRelationCode(), newEntry.getRelationCode())) {
            changesArray.add(createChangeObject("RelationCode", oldEntry.getRelationCode(), newEntry.getRelationCode()));
        }
        if (!Objects.equals(oldEntry.getRule(), newEntry.getRule())) {
            changesArray.add(createChangeObject("Rule", oldEntry.getRule(), newEntry.getRule()));
        }
        if (oldEntry.getPriority() != newEntry.getPriority()) {
            changesArray.add(createChangeObject("Priority", String.valueOf(oldEntry.getPriority()), String.valueOf(newEntry.getPriority())));
        }
        if (oldEntry.getBlock() != newEntry.getBlock()) {
            changesArray.add(createChangeObject("Block", String.valueOf(oldEntry.getBlock()), String.valueOf(newEntry.getBlock())));
        }
        if (oldEntry.getGroup() != newEntry.getGroup()) {
            changesArray.add(createChangeObject("Group", String.valueOf(oldEntry.getGroup()), String.valueOf(newEntry.getGroup())));
        }
        if (!Objects.equals(oldEntry.getModuleId(), newEntry.getModuleId())) {
            changesArray.add(createChangeObject("ModuleId", oldEntry.getModuleId(), newEntry.getModuleId()));
        }
        if (oldEntry.isReleased() != newEntry.isReleased()) {
            changesArray.add(createChangeObject("Released", String.valueOf(oldEntry.isReleased()), String.valueOf(newEntry.isReleased())));
        }
        if (!Objects.equals(oldEntry.getAdvices(), newEntry.getAdvices())) {
            changesArray.add(createChangeObject("Advices", oldEntry.getAdvices().toString(), newEntry.getAdvices().toString()));
        }
        if (!Objects.equals(oldEntry.getAdditionalMapEntryInfos(), newEntry.getAdditionalMapEntryInfos())) {
            ArrayNode oldInfos = objectMapper.valueToTree(oldEntry.getAdditionalMapEntryInfos());
            ArrayNode newInfos = objectMapper.valueToTree(newEntry.getAdditionalMapEntryInfos());
            changesArray.add(createChangeObject("AdditionalMapEntryInfos", oldInfos, newInfos));
        }

        return changesArray;
    }

    /**
     * Creates the change object.
     *
     * @param fieldName the field name
     * @param oldValue the old value
     * @param newValue the new value
     * @return the JSON object
     * @throws Exception the exception
     */
    private static ObjectNode createChangeObject(final String fieldName, final String oldValue, final String newValue) throws Exception {

        final ObjectNode changeObject = objectMapper.createObjectNode();
        changeObject.put("fieldName", fieldName);
        changeObject.put("oldValue", oldValue);
        changeObject.put("newValue", newValue);
        return changeObject;
    }
    
    /**
     * Creates the change object.
     *
     * @param fieldName the field name
     * @param oldValue the old value
     * @param newValue the new value
     * @return the object node
     * @throws Exception the exception
     */
    private static ObjectNode createChangeObject(final String fieldName, final ArrayNode oldValue, final ArrayNode newValue) throws Exception {

        final ObjectNode changeObject = objectMapper.createObjectNode();
        changeObject.put("fieldName", fieldName);
        changeObject.set("oldValue", oldValue);
        changeObject.set("newValue", newValue);
        return changeObject;
    }

    // Map Relation
    /**
     * Adds the map relation entry.
     *
     * @param mapRelation the map relation
     * @return the audit entry
     */
    public static AuditEntry addMapRelationEntry(final MapRelation mapRelation) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.MAP_RELATION.toString());
        entry.setEntityId(mapRelation.getId());
        entry.setMessage("ADD MapRelation");
        entry.setDetails(mapRelation.toString());
        log(entry);
        return entry;
    }

    /**
     * Update map relation entry.
     *
     * @param mapRelation the map relation
     * @return the audit entry
     */
    public static AuditEntry updateMapRelationEntry(final MapRelation mapRelation) {

        final AuditEntry entry = new AuditEntry();
        entry.setEntityType(EntityType.MAP_RELATION.toString());
        entry.setEntityId(mapRelation.getId());
        entry.setMessage("UPDATE MapRelation");
        entry.setDetails(mapRelation.toString());
        log(entry);
        return entry;
    }

}
