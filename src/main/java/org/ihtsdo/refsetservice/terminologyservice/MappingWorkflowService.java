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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapUser;
import org.ihtsdo.refsetservice.model.MapWorkflowStatus;
import org.ihtsdo.refsetservice.model.MappingWorkflow;
import org.ihtsdo.refsetservice.model.MappingWorkflowHistory;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.enums.MappingWorkflowAction;
import org.ihtsdo.refsetservice.model.enums.MappingWorkflowRole;
import org.ihtsdo.refsetservice.model.enums.VersionStatus;
import org.ihtsdo.refsetservice.model.enums.WorkflowStatus;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.FieldedStringTokenizer;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Per-mapping workflow transition rules loaded from {@code workflow/mappingWorkflowPermutations.txt}.
 */
public final class MappingWorkflowService {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(MappingWorkflowService.class);

    /** The file that contains mapping workflow actions by role and phase. */
    private static final String WORKFLOW_PERMUTATIONS_FILE_NAME = "workflow/mappingWorkflowPermutations.txt";

    /** Default assignment lease duration in milliseconds (8 hours). */
    private static final long DEFAULT_LEASE_DURATION_MS = 8L * 60L * 60L * 1000L;

    /** Property key for assignment lease duration. */
    private static final String LEASE_DURATION_PROPERTY = "mapping.workflow.lease.duration.ms";

    /** Map&lt;role, Map&lt;currentStatus, Map&lt;action, resultingStatus&gt;&gt;&gt;. */
    private static final Map<MappingWorkflowRole, Map<MapWorkflowStatus, Map<MappingWorkflowAction, MapWorkflowStatus>>> WORKFLOW_PERMUTATIONS =
        new HashMap<>();

    /** Actions that require the mapset to be in edit. */
    private static final List<MappingWorkflowAction> MAPSET_IN_EDIT_ACTIONS = Arrays.asList(
        MappingWorkflowAction.ASSIGN,
        MappingWorkflowAction.RELEASE,
        MappingWorkflowAction.FINISH_EDITING,
        MappingWorkflowAction.FORCE_RELEASE,
        MappingWorkflowAction.REASSIGN
    );

    /** Actions that clear assignment fields on success. */
    private static final List<MappingWorkflowAction> CLEAR_ASSIGNMENT_ACTIONS = Arrays.asList(
        MappingWorkflowAction.RELEASE,
        MappingWorkflowAction.FINISH_EDITING,
        MappingWorkflowAction.FORCE_RELEASE
    );

    static {
        try {
            final ClassPathResource workflowPermutationsResource = new ClassPathResource(WORKFLOW_PERMUTATIONS_FILE_NAME);
            try (final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(workflowPermutationsResource.getInputStream()))) {
                loadPermutations(bufferedReader, WORKFLOW_PERMUTATIONS_FILE_NAME);
            }
        } catch (final Exception e) {
            throw new RuntimeException("Unable to read mapping workflow file: " + e.getMessage(), e);
        }
    }

    /**
     * Instantiates an empty {@link MappingWorkflowService}.
     */
    private MappingWorkflowService() {

        // n/a
    }

    /**
     * Resolve the resulting workflow phase for a role, current phase, and action.
     *
     * @param role the project role
     * @param status the current workflow phase
     * @param action the requested action
     * @return the resulting phase, or null if no row exists
     */
    public static MapWorkflowStatus resolveTransition(final MappingWorkflowRole role, final MapWorkflowStatus status, final MappingWorkflowAction action) {

        if (role == null || status == null || action == null) {
            return null;
        }

        final Map<MapWorkflowStatus, Map<MappingWorkflowAction, MapWorkflowStatus>> byStatus = WORKFLOW_PERMUTATIONS.get(role);
        if (byStatus == null || !byStatus.containsKey(status)) {
            return null;
        }

        final Map<MappingWorkflowAction, MapWorkflowStatus> byAction = byStatus.get(status);
        if (byAction == null) {
            return null;
        }

        return byAction.get(action);
    }

    /**
     * Set mapping workflow status based on the action the user took.
     *
     * @param service the terminology service
     * @param user the acting user
     * @param action the workflow action
     * @param workflow the mapping workflow row
     * @param mapSet the map set
     * @param mapProject the map project
     * @param notes transition notes
     * @param assignToUser target user for REASSIGN; ignored for other actions
     * @return the updated mapping workflow
     * @throws Exception the exception
     */
    public static MappingWorkflow setWorkflowStatusByAction(final TerminologyService service, final User user, final MappingWorkflowAction action,
        final MappingWorkflow workflow, final MapSet mapSet, final MapProject mapProject, final String notes, final String assignToUser) throws Exception {

        canUserPerformWorkflowAction(user, workflow, mapSet, mapProject, action, assignToUser);

        final List<MappingWorkflowRole> roles = resolveProjectRoles(user, mapProject);
        MapWorkflowStatus nextStatus = null;

        for (final MappingWorkflowRole role : roles) {
            final MapWorkflowStatus possibleStatus = resolveTransition(role, workflow.getWorkflowStatus(), action);
            if (possibleStatus != null) {
                nextStatus = possibleStatus;
                break;
            }
        }

        if (nextStatus == null) {
            LOG.warn("Mapping workflow action requested but no permutation: workflowId={}, sourceConceptCode={}, workflowStatus={}, action={}, user={}, roles={}",
                workflow.getId(), workflow.getSourceConceptCode(), workflow.getWorkflowStatus(), action, user.getUserName(), roles);
            throw unauthorized(workflow, action);
        }

        applyAssignmentSideEffects(user, workflow, action, assignToUser);
        workflow.setWorkflowStatus(nextStatus);

        service.update(workflow);
        addWorkflowHistory(service, user, action, workflow, notes);

        return workflow;
    }

    /**
     * Add a mapping workflow history row.
     *
     * @param service the terminology service
     * @param user the acting user
     * @param action the workflow action taken
     * @param workflow the mapping workflow row (after status update)
     * @param notes transition notes
     * @throws Exception the exception
     */
    public static void addWorkflowHistory(final TerminologyService service, final User user, final MappingWorkflowAction action,
        final MappingWorkflow workflow, final String notes) throws Exception {

        final MappingWorkflowHistory history = new MappingWorkflowHistory();
        history.setUserName(user.getUserName());
        history.setWorkflowStatus(workflow.getWorkflowStatus());
        history.setWorkflowAction(action);
        history.setNotes(notes);
        history.setMappingWorkflow(workflow);
        service.add(history);
    }

    /**
     * Get workflow history for a mapping workflow row.
     *
     * @param service the terminology service
     * @param workflow the mapping workflow row
     * @param searchParameters optional paging/sorting
     * @return the history rows
     * @throws Exception the exception
     */
    public static ResultList<MappingWorkflowHistory> getWorkflowHistory(final TerminologyService service, final MappingWorkflow workflow,
        final SearchParameters searchParameters) throws Exception {

        final PfsParameter pfs = new PfsParameter();
        String query = "";

        if (searchParameters != null) {
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
            if (searchParameters.getQuery() != null) {
                query = " AND " + IndexUtility.addWildcardsToQuery(searchParameters.getQuery(), MappingWorkflowHistory.class);
            }
        }

        return service.find("mappingWorkflowId:" + QueryParserBase.escape(workflow.getId()) + query, pfs, MappingWorkflowHistory.class, null);
    }

    /**
     * Get workflow actions allowed for the current user and mapping state.
     *
     * @param user the acting user
     * @param workflow the mapping workflow row
     * @param mapSet the map set
     * @param mapProject the map project
     * @return allowed actions
     * @throws Exception the exception
     */
    public static Set<MappingWorkflowAction> getAllowedActions(final User user, final MappingWorkflow workflow, final MapSet mapSet,
        final MapProject mapProject) throws Exception {

        final Set<MappingWorkflowAction> allowedActions = new HashSet<>();

        if (user == null || workflow == null || mapSet == null || mapProject == null || workflow.getWorkflowStatus() == null) {
            return allowedActions;
        }

        final List<MappingWorkflowRole> roles = resolveProjectRoles(user, mapProject);
        if (roles.isEmpty()) {
            return allowedActions;
        }

        final boolean mapsetInEdit = isMapsetInEdit(mapSet);

        for (final MappingWorkflowRole role : roles) {
            for (final MappingWorkflowAction action : MappingWorkflowAction.values()) {
                if (resolveTransition(role, workflow.getWorkflowStatus(), action) != null
                    && passesActionGates(user, workflow, action, mapsetInEdit)) {
                    allowedActions.add(action);
                }
            }
        }

        return allowedActions;
    }

    /**
     * Verify the user may perform the requested workflow action.
     *
     * @param user the acting user
     * @param workflow the mapping workflow row
     * @param mapSet the map set
     * @param mapProject the map project
     * @param action the workflow action
     * @param assignToUser target user for REASSIGN
     * @throws Exception the exception
     */
    public static void canUserPerformWorkflowAction(final User user, final MappingWorkflow workflow, final MapSet mapSet, final MapProject mapProject,
        final MappingWorkflowAction action, final String assignToUser) throws Exception {

        if (isDevBypassEnabled()) {
            return;
        }

        if (!isActionPermitted(user, workflow, mapSet, mapProject, action, assignToUser)) {
            LOG.error("Unsuccessful attempt to update mapping workflow for concept {} from status {} with action {}",
                workflow.getSourceConceptCode(), workflow.getWorkflowStatus(), action);
            throw unauthorized(workflow, action);
        }
    }

    /**
     * Resolve map-project workflow roles for a user based on project membership.
     *
     * @param user the acting user
     * @param mapProject the map project
     * @return the workflow roles the user holds on the project
     */
    static List<MappingWorkflowRole> resolveProjectRoles(final User user, final MapProject mapProject) {

        final List<MappingWorkflowRole> roles = new ArrayList<>();
        if (user == null || mapProject == null) {
            return roles;
        }

        final String userName = user.getUserName();
        addRoleFromMembership(roles, userName, mapProject.getMapSpecialists(), MappingWorkflowRole.SPECIALIST);
        addRoleFromMembership(roles, userName, mapProject.getMapLeads(), MappingWorkflowRole.LEAD);

        return roles;
    }

    /**
     * Add the base role plus an {@code ADMIN} role if the matching member is an administrator.
     *
     * @param roles the roles being accumulated
     * @param userName the acting user name
     * @param members the project members to search
     * @param baseRole the role granted by membership in this list
     */
    private static void addRoleFromMembership(final List<MappingWorkflowRole> roles, final String userName, final Set<MapUser> members,
        final MappingWorkflowRole baseRole) {

        if (members == null) {
            return;
        }

        for (final MapUser mapUser : members) {
            if (userName.equals(mapUser.getUserName())) {
                if (!roles.contains(baseRole)) {
                    roles.add(baseRole);
                }
                final MappingWorkflowRole mappedRole = MappingWorkflowRole.fromMapUserRole(mapUser.getApplicationRole());
                if (mappedRole == MappingWorkflowRole.ADMIN && !roles.contains(MappingWorkflowRole.ADMIN)) {
                    roles.add(MappingWorkflowRole.ADMIN);
                }
                break;
            }
        }
    }

    /**
     * Load mapping workflow permutations from a reader. Visible for unit tests in this package.
     *
     * @param reader the reader
     * @param fileName the file name (for error messages)
     * @throws Exception the exception
     */
    static void loadPermutations(final Reader reader, final String fileName) throws Exception {

        WORKFLOW_PERMUTATIONS.clear();

        try (final BufferedReader bufferedReader = reader instanceof BufferedReader ? (BufferedReader) reader : new BufferedReader(reader)) {

            String line;
            while ((line = bufferedReader.readLine()) != null) {

                if (line.startsWith("#") || StringUtils.isBlank(line)) {
                    continue;
                }

                final String[] tokens = FieldedStringTokenizer.split(line, ",");

                if (tokens.length != 4) {
                    throw new Exception(fileName + " does not have 4 items per line");
                }

                final MappingWorkflowRole role = MappingWorkflowRole.fromString(tokens[0].toUpperCase().strip());
                final MapWorkflowStatus currentStatus = MapWorkflowStatus.fromString(tokens[1].toUpperCase().strip());
                final MappingWorkflowAction workflowAction = MappingWorkflowAction.fromString(tokens[2].toUpperCase().strip());
                final MapWorkflowStatus resultingStatus = MapWorkflowStatus.fromString(tokens[3].toUpperCase().strip());

                WORKFLOW_PERMUTATIONS.computeIfAbsent(role, key -> new HashMap<>())
                    .computeIfAbsent(currentStatus, key -> new HashMap<>())
                    .put(workflowAction, resultingStatus);
            }
        }
    }

    private static void applyAssignmentSideEffects(final User user, final MappingWorkflow workflow, final MappingWorkflowAction action,
        final String assignToUser) {

        if (action == MappingWorkflowAction.ASSIGN) {
            final Date assignedAt = new Date();
            workflow.setAssignedUser(user.getUserName());
            workflow.setAssignedAt(assignedAt);
            workflow.setLeaseExpiresAt(new Date(assignedAt.getTime() + getLeaseDurationMs()));
        } else if (action == MappingWorkflowAction.REASSIGN) {
            final Date assignedAt = new Date();
            workflow.setAssignedUser(assignToUser);
            workflow.setAssignedAt(assignedAt);
            workflow.setLeaseExpiresAt(new Date(assignedAt.getTime() + getLeaseDurationMs()));
        } else if (CLEAR_ASSIGNMENT_ACTIONS.contains(action)) {
            workflow.setAssignedUser(null);
            workflow.setAssignedAt(null);
            workflow.setLeaseExpiresAt(null);
        }
    }

    private static boolean isActionPermitted(final User user, final MappingWorkflow workflow, final MapSet mapSet, final MapProject mapProject,
        final MappingWorkflowAction action, final String assignToUser) {

        if (user == null || workflow == null || mapSet == null || mapProject == null || workflow.getWorkflowStatus() == null) {
            return false;
        }

        if (action == MappingWorkflowAction.REASSIGN && StringUtils.isBlank(assignToUser)) {
            return false;
        }

        final List<MappingWorkflowRole> roles = resolveProjectRoles(user, mapProject);
        if (roles.isEmpty()) {
            return false;
        }

        final boolean mapsetInEdit = isMapsetInEdit(mapSet);
        for (final MappingWorkflowRole role : roles) {
            if (resolveTransition(role, workflow.getWorkflowStatus(), action) != null
                && passesActionGates(user, workflow, action, mapsetInEdit)) {
                return true;
            }
        }
        return false;
    }

    private static boolean passesActionGates(final User user, final MappingWorkflow workflow, final MappingWorkflowAction action,
        final boolean mapsetInEdit) {

        if (MAPSET_IN_EDIT_ACTIONS.contains(action) && !mapsetInEdit) {
            return false;
        }

        switch (action) {
            case ASSIGN:
                return workflow.getWorkflowStatus() == MapWorkflowStatus.NEW && workflow.getAssignedUser() == null;
            case RELEASE:
            case FINISH_EDITING:
                return user.getUserName().equals(workflow.getAssignedUser());
            default:
                return true;
        }
    }

    private static boolean isMapsetInEdit(final MapSet mapSet) {

        return mapSet.getVersionStatus() == VersionStatus.IN_DEVELOPMENT && mapSet.getWorkflowStatus() == WorkflowStatus.IN_EDIT;
    }

    private static long getLeaseDurationMs() {

        final String configured = PropertyUtility.getProperty(LEASE_DURATION_PROPERTY);
        if (StringUtils.isNotBlank(configured)) {
            try {
                return Long.parseLong(configured.trim());
            } catch (final NumberFormatException e) {
                LOG.warn("Invalid {} value '{}', using default", LEASE_DURATION_PROPERTY, configured);
            }
        }
        return DEFAULT_LEASE_DURATION_MS;
    }

    private static boolean isDevBypassEnabled() {

        final String bypass = PropertyUtility.getProperty("auth.dev.bypass");
        if ("true".equalsIgnoreCase(bypass)) {
            return true;
        }
        final String profiles = PropertyUtility.getProperty("springProfiles");
        return profiles != null && profiles.toLowerCase().contains("dev");
    }

    private static ResponseStatusException unauthorized(final MappingWorkflow workflow, final MappingWorkflowAction action) {

        return new ResponseStatusException(HttpStatus.UNAUTHORIZED,
            "Unsuccessful attempt to update mapping workflow for concept " + workflow.getSourceConceptCode()
                + " from status " + workflow.getWorkflowStatus() + " with action " + action);
    }
}
