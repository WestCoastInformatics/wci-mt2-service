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
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.Query;
import javax.persistence.TypedQuery;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.hibernate.Hibernate;
import org.ihtsdo.refsetservice.handler.EntraMapBootstrap;
import org.ihtsdo.refsetservice.helpers.WorkflowType;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapUser;
import org.ihtsdo.refsetservice.model.MapWorkflowStatus;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.model.MappingWorkflow;
import org.ihtsdo.refsetservice.model.MappingWorkflowBulkItemResult;
import org.ihtsdo.refsetservice.model.MappingWorkflowBulkResult;
import org.ihtsdo.refsetservice.model.MappingWorkflowHistory;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.ResultListMapping;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.enums.MappingWorkflowAction;
import org.ihtsdo.refsetservice.model.enums.MappingWorkflowRole;
import org.ihtsdo.refsetservice.model.enums.VersionStatus;
import org.ihtsdo.refsetservice.model.enums.WorkflowAction;
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

    /** Property key to enable Snowstorm concept sub-branch side effects. */
    private static final String CONCEPT_BRANCH_ENABLED_PROPERTY = "mapping.workflow.concept.branch.enabled";

    /** Actions that create, merge, or delete per-concept Snowstorm branches. */
    private static final List<MappingWorkflowAction> CONCEPT_BRANCH_ACTIONS = Arrays.asList(
        MappingWorkflowAction.ASSIGN,
        MappingWorkflowAction.FINISH_EDITING,
        MappingWorkflowAction.RELEASE,
        MappingWorkflowAction.FORCE_RELEASE
    );

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

    /** Claim actions: permutations decide which phases; runtime requires unassigned. */
    private static final List<MappingWorkflowAction> CLAIM_ACTIONS = Arrays.asList(
        MappingWorkflowAction.ASSIGN,
        MappingWorkflowAction.START_REVIEW,
        MappingWorkflowAction.START_CONFLICT_RESOLUTION
    );

    /** Holder actions: permutations decide which phases; runtime requires assigned_user = actor. */
    private static final List<MappingWorkflowAction> HOLDER_ACTIONS = Arrays.asList(
        MappingWorkflowAction.RELEASE,
        MappingWorkflowAction.FINISH_EDITING,
        MappingWorkflowAction.ACCEPT_REVIEW,
        MappingWorkflowAction.REJECT_REVIEW,
        MappingWorkflowAction.REQUEST_REVISION,
        MappingWorkflowAction.RESOLVE_CONFLICT
    );

    /** Mapping phases that block mapset FINISH_EDIT and REQUEST_PUBLICATION. */
    private static final List<MapWorkflowStatus> MAPSET_GATE_BLOCKING_STATUSES = Arrays.asList(
        MapWorkflowStatus.EDITING_IN_PROGRESS,
        MapWorkflowStatus.REVIEW_IN_PROGRESS,
        MapWorkflowStatus.CONFLICT_IN_PROGRESS
    );

    /** Mapping phases in which the assigned user may save mapping data. */
    private static final List<MapWorkflowStatus> MAPPING_EDITABLE_STATUSES = Arrays.asList(
        MapWorkflowStatus.EDITING_IN_PROGRESS,
        MapWorkflowStatus.REVIEW_IN_PROGRESS
    );

    /** Mapset actions gated on per-mapping workflow summary. */
    private static final List<WorkflowAction> MAPSET_GATE_ACTIONS = Arrays.asList(
        WorkflowAction.FINISH_EDIT,
        WorkflowAction.REQUEST_PUBLICATION
    );

    /** Actions that clear assignment fields on success. */
    private static final List<MappingWorkflowAction> CLEAR_ASSIGNMENT_ACTIONS = Arrays.asList(
        MappingWorkflowAction.RELEASE,
        MappingWorkflowAction.FINISH_EDITING,
        MappingWorkflowAction.FORCE_RELEASE,
        MappingWorkflowAction.ACCEPT_REVIEW,
        MappingWorkflowAction.REJECT_REVIEW,
        MappingWorkflowAction.REQUEST_REVISION,
        MappingWorkflowAction.RESOLVE_CONFLICT
    );

    /** Default page size for assigned-workflow queries. */
    private static final int ASSIGNED_WORKFLOWS_DEFAULT_LIMIT = 10;

    /** Maximum page size for assigned-workflow queries. */
    private static final int ASSIGNED_WORKFLOWS_MAX_LIMIT = 1000;

    /** Allowed JPQL sort fields for assigned-workflow queries. */
    private static final Set<String> ASSIGNED_WORKFLOWS_SORT_FIELDS =
        new HashSet<>(Arrays.asList("assignedAt", "modified", "workflowStatus", "sourceConceptCode", "leaseExpiresAt"));

    /** Default page size for recently-modified workflow queries. */
    private static final int RECENTLY_MODIFIED_WORKFLOWS_DEFAULT_LIMIT = 10;

    /** Maximum page size for recently-modified workflow queries. */
    private static final int RECENTLY_MODIFIED_WORKFLOWS_MAX_LIMIT = 100;

    /** Allowed JPQL sort fields for recently-modified workflow queries. */
    private static final Set<String> RECENTLY_MODIFIED_WORKFLOWS_SORT_FIELDS =
        new HashSet<>(Arrays.asList("modified", "assignedAt", "workflowStatus", "sourceConceptCode"));

    /** Delegates per-concept Snowstorm branch operations (overridable in unit tests). */
    private static ConceptBranchOperations conceptBranchOperations = new DefaultConceptBranchOperations();

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
            throw conflictTransition(workflow, action);
        }

        nextStatus = adjustResultStatusForWorkflowType(mapProject, action, nextStatus);
        nextStatus = adjustResultStatusForPreviousWorkflowStatus(workflow, action, nextStatus);

        applyConceptBranchSideEffects(action, mapSet, workflow);

        applyAssignmentSideEffects(user, workflow, action, assignToUser);
        workflow.setWorkflowStatus(nextStatus);

        service.update(workflow);
        addWorkflowHistory(service, user, action, workflow, notes);

        if (mapProject.getWorkflowType() == WorkflowType.CONFLICT_PROJECT && action == MappingWorkflowAction.FINISH_EDITING) {
            applyConflictJoinAfterFinish(service, mapSet, workflow);
        }

        syncConflictSiblingIfNeeded(service, user, action, workflow, mapSet, mapProject, notes);

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
     * Find currently assigned mapping workflow rows for a user across map sets / projects.
     *
     * <p>
     * Only rows whose {@code assignedUser} matches are returned (current assignments). Historical assignments are not included once assignment fields are
     * cleared.
     *
     * @param service the terminology service
     * @param assignedUser the assigned user name (session user)
     * @param mapProjectId optional map project id filter
     * @param mapSetId optional map set id filter
     * @param workflowStatus optional workflow status filter
     * @param searchParameters optional paging/sorting ({@code limit} default 10, max 1000; {@code sort} default {@code assignedAt}; {@code sortAscending}
     *            default false)
     * @return the assigned workflow rows
     * @throws Exception the exception
     */
    public static ResultList<MappingWorkflow> findAssignedWorkflows(final TerminologyService service, final String assignedUser, final String mapProjectId,
        final String mapSetId, final MapWorkflowStatus workflowStatus, final SearchParameters searchParameters) throws Exception {

        if (StringUtils.isBlank(assignedUser)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "assignedUser is required");
        }

        int limit = ASSIGNED_WORKFLOWS_DEFAULT_LIMIT;
        int offset = 0;
        String sort = "assignedAt";
        boolean ascending = false;

        if (searchParameters != null) {
            if (searchParameters.getLimit() != null) {
                if (searchParameters.getLimit() > ASSIGNED_WORKFLOWS_MAX_LIMIT) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Requested limit " + searchParameters.getLimit() + " exceeds maximum of " + ASSIGNED_WORKFLOWS_MAX_LIMIT);
                }
                if (searchParameters.getLimit() > 0) {
                    limit = searchParameters.getLimit();
                }
            }
            if (searchParameters.getOffset() != null && searchParameters.getOffset() >= 0) {
                offset = searchParameters.getOffset();
            }
            if (StringUtils.isNotBlank(searchParameters.getSort())) {
                sort = searchParameters.getSort();
            }
            if (searchParameters.getSortAscending() != null) {
                ascending = searchParameters.getSortAscending();
            }
        }

        if (!ASSIGNED_WORKFLOWS_SORT_FIELDS.contains(sort)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort field: " + sort);
        }

        final StringBuilder where = new StringBuilder("from MappingWorkflow mw where mw.active = true and mw.assignedUser = :assignedUser");
        if (StringUtils.isNotBlank(mapProjectId)) {
            where.append(" and mw.mapProject.id = :mapProjectId");
        }
        if (StringUtils.isNotBlank(mapSetId)) {
            where.append(" and mw.mapSet.id = :mapSetId");
        }
        if (workflowStatus != null) {
            where.append(" and mw.workflowStatus = :workflowStatus");
        }

        final TypedQuery<Long> countQuery = service.getEntityManager().createQuery("select count(mw) " + where, Long.class);
        applyAssignedWorkflowFilters(countQuery, assignedUser, mapProjectId, mapSetId, workflowStatus);
        final long totalCount = countQuery.getSingleResult();

        final String orderBy = " order by mw." + sort + (ascending ? " asc" : " desc");
        final TypedQuery<MappingWorkflow> dataQuery =
            service.getEntityManager().createQuery(where.toString() + orderBy, MappingWorkflow.class);
        applyAssignedWorkflowFilters(dataQuery, assignedUser, mapProjectId, mapSetId, workflowStatus);
        dataQuery.setFirstResult(offset);
        dataQuery.setMaxResults(limit);
        final List<MappingWorkflow> items = dataQuery.getResultList();

        final ResultList<MappingWorkflow> results = new ResultList<>();
        results.setItems(items);
        results.setTotal((int) totalCount);
        results.setTotalKnown(true);
        results.setLimit(limit);
        results.setOffset(offset);
        results.setParameters(searchParameters);
        return results;
    }

    /**
     * Bind shared filter parameters for assigned-workflow JPQL queries.
     *
     * @param query the query
     * @param assignedUser the assigned user
     * @param mapProjectId optional map project id
     * @param mapSetId optional map set id
     * @param workflowStatus optional workflow status
     */
    private static void applyAssignedWorkflowFilters(final Query query, final String assignedUser, final String mapProjectId,
        final String mapSetId, final MapWorkflowStatus workflowStatus) {

        query.setParameter("assignedUser", assignedUser);
        if (StringUtils.isNotBlank(mapProjectId)) {
            query.setParameter("mapProjectId", mapProjectId);
        }
        if (StringUtils.isNotBlank(mapSetId)) {
            query.setParameter("mapSetId", mapSetId);
        }
        if (workflowStatus != null) {
            query.setParameter("workflowStatus", workflowStatus);
        }
    }

    /**
     * Find mapping workflow rows most recently modified by a user across map sets / projects.
     *
     * <p>
     * "Modified" means {@link MappingWorkflow#getModifiedBy()} / {@link MappingWorkflow#getModified()} (workflow-row updates such as assignment and status
     * transitions), not Snowstorm mapping content edits and not {@code MappingWorkflowHistory} alone.
     *
     * @param service the terminology service
     * @param modifiedBy the modifying user name (session user)
     * @param mapProjectId optional map project id filter
     * @param mapSetId optional map set id filter
     * @param searchParameters optional paging/sorting ({@code limit} default 10, max 100; {@code sort} default {@code modified}; {@code sortAscending} default
     *            false)
     * @return the recently modified workflow rows
     * @throws Exception the exception
     */
    public static ResultList<MappingWorkflow> findRecentlyModifiedWorkflows(final TerminologyService service, final String modifiedBy,
        final String mapProjectId, final String mapSetId, final SearchParameters searchParameters) throws Exception {

        if (StringUtils.isBlank(modifiedBy)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "modifiedBy is required");
        }

        int limit = RECENTLY_MODIFIED_WORKFLOWS_DEFAULT_LIMIT;
        int offset = 0;
        String sort = "modified";
        boolean ascending = false;

        if (searchParameters != null) {
            if (searchParameters.getLimit() != null) {
                if (searchParameters.getLimit() > RECENTLY_MODIFIED_WORKFLOWS_MAX_LIMIT) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Requested limit " + searchParameters.getLimit() + " exceeds maximum of " + RECENTLY_MODIFIED_WORKFLOWS_MAX_LIMIT);
                }
                if (searchParameters.getLimit() > 0) {
                    limit = searchParameters.getLimit();
                }
            }
            if (searchParameters.getOffset() != null && searchParameters.getOffset() >= 0) {
                offset = searchParameters.getOffset();
            }
            if (StringUtils.isNotBlank(searchParameters.getSort())) {
                sort = searchParameters.getSort();
            }
            if (searchParameters.getSortAscending() != null) {
                ascending = searchParameters.getSortAscending();
            }
        }

        if (!RECENTLY_MODIFIED_WORKFLOWS_SORT_FIELDS.contains(sort)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported sort field: " + sort);
        }

        final StringBuilder where = new StringBuilder("from MappingWorkflow mw where mw.active = true and mw.modifiedBy = :modifiedBy");
        if (StringUtils.isNotBlank(mapProjectId)) {
            where.append(" and mw.mapProject.id = :mapProjectId");
        }
        if (StringUtils.isNotBlank(mapSetId)) {
            where.append(" and mw.mapSet.id = :mapSetId");
        }

        final TypedQuery<Long> countQuery = service.getEntityManager().createQuery("select count(mw) " + where, Long.class);
        applyRecentlyModifiedWorkflowFilters(countQuery, modifiedBy, mapProjectId, mapSetId);
        final long totalCount = countQuery.getSingleResult();

        final String orderBy = " order by mw." + sort + (ascending ? " asc" : " desc");
        final TypedQuery<MappingWorkflow> dataQuery = service.getEntityManager().createQuery(where.toString() + orderBy, MappingWorkflow.class);
        applyRecentlyModifiedWorkflowFilters(dataQuery, modifiedBy, mapProjectId, mapSetId);
        dataQuery.setFirstResult(offset);
        dataQuery.setMaxResults(limit);
        final List<MappingWorkflow> items = dataQuery.getResultList();

        final ResultList<MappingWorkflow> results = new ResultList<>();
        results.setItems(items);
        results.setTotal((int) totalCount);
        results.setTotalKnown(true);
        results.setLimit(limit);
        results.setOffset(offset);
        results.setParameters(searchParameters);
        return results;
    }

    /**
     * Bind shared filter parameters for recently-modified workflow JPQL queries.
     *
     * @param query the query
     * @param modifiedBy the modifying user
     * @param mapProjectId optional map project id
     * @param mapSetId optional map set id
     */
    private static void applyRecentlyModifiedWorkflowFilters(final Query query, final String modifiedBy, final String mapProjectId, final String mapSetId) {

        query.setParameter("modifiedBy", modifiedBy);
        if (StringUtils.isNotBlank(mapProjectId)) {
            query.setParameter("mapProjectId", mapProjectId);
        }
        if (StringUtils.isNotBlank(mapSetId)) {
            query.setParameter("mapSetId", mapSetId);
        }
    }

    /** Maximum concept codes accepted in one bulk workflow request. */
    private static final int BULK_WORKFLOW_MAX_CONCEPT_CODES = 1000;

    /**
     * Find mapping workflow rows for many source concepts, returning them in request order.
     *
     * <p>
     * Does not create missing rows. Concepts in the mapset with no workflow row receive a non-persisted
     * {@link MapWorkflowStatus#PUBLISHED} placeholder. Throws {@link ResponseStatusException} with 404 if any
     * concept is not in the mapset and has no workflow row.
     *
     * @param service the terminology service
     * @param mapSet the map set
     * @param conceptCodes source concept codes
     * @return workflow rows in the same order as {@code conceptCodes}
     * @throws Exception the exception
     */
    public static List<MappingWorkflow> findWorkflowsForConcepts(final TerminologyService service, final MapSet mapSet, final List<String> conceptCodes)
        throws Exception {

        validateBulkConceptCodes(mapSet, conceptCodes);

        final List<String> trimmedCodes = new ArrayList<>(conceptCodes.size());
        for (final String rawConceptCode : conceptCodes) {
            final String conceptCode = rawConceptCode == null ? null : rawConceptCode.trim();
            if (StringUtils.isBlank(conceptCode)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conceptCode is required");
            }
            trimmedCodes.add(conceptCode);
        }

        final List<MappingWorkflow> found = service.getEntityManager()
            .createQuery("from MappingWorkflow mw where mw.mapSet.id = :mapSetId and mw.sourceConceptCode in :codes"
                + " and mw.specialistSlot = 1 and mw.active = true", MappingWorkflow.class)
            .setParameter("mapSetId", mapSet.getId())
            .setParameter("codes", new HashSet<>(trimmedCodes))
            .getResultList();

        final Map<String, MappingWorkflow> byCode = new HashMap<>();
        for (final MappingWorkflow workflow : found) {
            if (workflow != null && StringUtils.isNotBlank(workflow.getSourceConceptCode())) {
                byCode.putIfAbsent(workflow.getSourceConceptCode(), workflow);
            }
        }

        final List<String> missingCodes = new ArrayList<>();
        for (final String conceptCode : trimmedCodes) {
            if (!byCode.containsKey(conceptCode)) {
                missingCodes.add(conceptCode);
            }
        }
        final Set<String> inMapSet = findConceptCodesInMapSet(mapSet, missingCodes);

        final List<MappingWorkflow> workflows = new ArrayList<>(trimmedCodes.size());
        for (final String conceptCode : trimmedCodes) {
            MappingWorkflow workflow = byCode.get(conceptCode);
            if (workflow == null) {
                if (!inMapSet.contains(conceptCode)) {
                    throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mapping workflow not found for concept " + conceptCode);
                }
                workflow = newPublishedWorkflow(mapSet, conceptCode);
            }
            workflows.add(workflow);
        }
        return workflows;
    }

    /**
     * Apply the same workflow action to many source concepts in one map set.
     *
     * <p>
     * Each concept is processed independently so partial failures are reported without rolling back successful transitions. Specialist "request review" is
     * {@link MappingWorkflowAction#FINISH_EDITING}; lead "start review" is {@link MappingWorkflowAction#START_REVIEW}.
     *
     * @param service the terminology service
     * @param user the acting user
     * @param action the workflow action
     * @param mapSet the map set
     * @param mapProject the map project
     * @param conceptCodes source concept codes to update
     * @param notes optional notes applied to each transition
     * @param assignToUser target user for REASSIGN; ignored for other actions
     * @return per-concept results in request order
     * @throws Exception the exception
     */
    public static MappingWorkflowBulkResult setWorkflowStatusByActionBulk(final TerminologyService service, final User user, final MappingWorkflowAction action,
        final MapSet mapSet, final MapProject mapProject, final List<String> conceptCodes, final String notes, final String assignToUser) throws Exception {

        if (action == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "action is required");
        }
        if (mapProject == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Map set or map project not found");
        }
        validateBulkConceptCodes(mapSet, conceptCodes);

        final boolean previousTransactionPerOperation = service.getTransactionPerOperation();
        service.setTransactionPerOperation(true);

        final MappingWorkflowBulkResult result = new MappingWorkflowBulkResult();
        try {
            for (final String rawConceptCode : conceptCodes) {
                final String conceptCode = rawConceptCode == null ? null : rawConceptCode.trim();
                if (StringUtils.isBlank(conceptCode)) {
                    result.addItem(MappingWorkflowBulkItemResult.failure(rawConceptCode, HttpStatus.BAD_REQUEST.value(), "conceptCode is required"));
                    continue;
                }

                try {
                    final MappingWorkflow workflow = ensureWorkflowForConcept(service, mapSet, conceptCode);
                    final MappingWorkflow updated = setWorkflowStatusByAction(service, user, action, workflow, mapSet, mapProject, notes, assignToUser);
                    result.addItem(MappingWorkflowBulkItemResult.success(conceptCode, updated));
                } catch (final ResponseStatusException ex) {
                    final String message = StringUtils.isNotBlank(ex.getReason()) ? ex.getReason() : ex.getMessage();
                    result.addItem(MappingWorkflowBulkItemResult.failure(conceptCode, ex.getStatus().value(), message));
                } catch (final Exception ex) {
                    final ResponseStatusException nested = findNestedResponseStatusException(ex);
                    if (nested != null) {
                        final String message = StringUtils.isNotBlank(nested.getReason()) ? nested.getReason() : nested.getMessage();
                        result.addItem(MappingWorkflowBulkItemResult.failure(conceptCode, nested.getStatus().value(), message));
                    } else {
                        LOG.warn("Bulk mapping workflow action failed for conceptCode={}: {}", conceptCode, ex.getMessage(), ex);
                        result.addItem(MappingWorkflowBulkItemResult.failure(conceptCode, HttpStatus.INTERNAL_SERVER_ERROR.value(),
                            ex.getMessage() != null ? ex.getMessage() : "Unexpected error"));
                    }
                }
            }
        } finally {
            service.setTransactionPerOperation(previousTransactionPerOperation);
        }

        return result;
    }

    /**
     * Validate map set and concept code list for bulk workflow operations.
     *
     * @param mapSet the map set
     * @param conceptCodes the concept codes
     */
    private static void validateBulkConceptCodes(final MapSet mapSet, final List<String> conceptCodes) {

        if (mapSet == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Map set or map project not found");
        }
        if (conceptCodes == null || conceptCodes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conceptCodes is required and must not be empty");
        }
        if (conceptCodes.size() > BULK_WORKFLOW_MAX_CONCEPT_CODES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Requested conceptCodes size " + conceptCodes.size() + " exceeds maximum of " + BULK_WORKFLOW_MAX_CONCEPT_CODES);
        }
    }

    /**
     * Find a nested {@link ResponseStatusException}.
     *
     * @param exception the exception
     * @return the response status exception, or null
     */
    private static ResponseStatusException findNestedResponseStatusException(final Throwable exception) {

        for (Throwable current = exception; current != null; current = current.getCause()) {
            if (current instanceof ResponseStatusException) {
                return (ResponseStatusException) current;
            }
        }
        return null;
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

        if (user == null || workflow == null || mapSet == null || mapProject == null || workflow.getWorkflowStatus() == null || action == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User does not have permission to perform this mapping workflow action.");
        }

        if (action == MappingWorkflowAction.REASSIGN && StringUtils.isBlank(assignToUser)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "assignToUser is required for REASSIGN");
        }

        final List<MappingWorkflowRole> roles = resolveProjectRoles(user, mapProject);
        if (roles.isEmpty()) {
            throw forbidden(workflow, action);
        }

        boolean roleDefinesAction = false;
        boolean hasTransition = false;
        for (final MappingWorkflowRole role : roles) {
            if (roleDefinesAction(role, action)) {
                roleDefinesAction = true;
            }
            if (resolveTransition(role, workflow.getWorkflowStatus(), action) != null) {
                hasTransition = true;
            }
        }

        if (!roleDefinesAction) {
            throw forbidden(workflow, action);
        }

        if (!hasTransition) {
            LOG.error("Unsuccessful attempt to update mapping workflow for concept {} from status {} with action {}",
                workflow.getSourceConceptCode(), workflow.getWorkflowStatus(), action);
            throw conflictTransition(workflow, action);
        }

        final boolean mapsetInEdit = isMapsetInEdit(mapSet);
        if (MAPSET_IN_EDIT_ACTIONS.contains(action) && !mapsetInEdit) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Reference Set is not in edit; cannot perform mapping workflow action " + action + ".");
        }

        if (!passesActionGates(user, workflow, action, mapsetInEdit)) {
            LOG.error("Unsuccessful attempt to update mapping workflow for concept {} from status {} with action {}",
                workflow.getSourceConceptCode(), workflow.getWorkflowStatus(), action);
            throw forbidden(workflow, action);
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
        if (user == null) {
            return roles;
        }

        if (mapProject != null) {
            final String userName = user.getUserName();
            addRoleFromMembership(roles, userName, mapProject.getMapSpecialists(), MappingWorkflowRole.SPECIALIST);
            addRoleFromMembership(roles, userName, mapProject.getMapLeads(), MappingWorkflowRole.LEAD);
        }

        for (final MappingWorkflowRole entraRole : EntraMapBootstrap.toWorkflowRoles(user.getRoles())) {
            if (!roles.contains(entraRole)) {
                roles.add(entraRole);
            }
        }

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

        final Map<MappingWorkflowRole, Map<MapWorkflowStatus, Map<MappingWorkflowAction, MapWorkflowStatus>>> loaded =
            new HashMap<>();

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

                loaded.computeIfAbsent(role, key -> new HashMap<>())
                    .computeIfAbsent(currentStatus, key -> new HashMap<>())
                    .put(workflowAction, resultingStatus);
            }
        }

        WORKFLOW_PERMUTATIONS.clear();
        WORKFLOW_PERMUTATIONS.putAll(loaded);
    }

    /**
     * Apply assignment side effects.
     *
     * @param user the user
     * @param workflow the workflow
     * @param action the action
     * @param assignToUser the assign to user
     */
    private static void applyAssignmentSideEffects(final User user, final MappingWorkflow workflow, final MappingWorkflowAction action,
        final String assignToUser) {

        if (action == MappingWorkflowAction.ASSIGN) {
            final Date assignedAt = new Date();
            workflow.setPreviousWorkflowStatus(workflow.getWorkflowStatus());
            workflow.setAssignedUser(user.getUserName());
            workflow.setAssignedAt(assignedAt);
            workflow.setLeaseExpiresAt(new Date(assignedAt.getTime() + getLeaseDurationMs()));
        } else if (action == MappingWorkflowAction.START_REVIEW || action == MappingWorkflowAction.START_CONFLICT_RESOLUTION) {
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
            workflow.setPreviousWorkflowStatus(null);
        }
    }

    /**
     * Checks if is action permitted.
     *
     * @param user the user
     * @param workflow the workflow
     * @param mapSet the map set
     * @param mapProject the map project
     * @param action the action
     * @param assignToUser the assign to user
     * @return true, if is action permitted
     */
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

    /**
     * Runtime constraints the permutation file cannot express: mapset edit state and assignment holder.
     * Allowed role+status+action combinations come only from {@link #resolveTransition}.
     *
     * @param user the user
     * @param workflow the workflow
     * @param action the action
     * @param mapsetInEdit the mapset in edit
     * @return true, if successful
     */
    private static boolean passesActionGates(final User user, final MappingWorkflow workflow, final MappingWorkflowAction action,
        final boolean mapsetInEdit) {

        if (MAPSET_IN_EDIT_ACTIONS.contains(action) && !mapsetInEdit) {
            return false;
        }

        if (CLAIM_ACTIONS.contains(action)) {
            return workflow.getAssignedUser() == null;
        }

        if (HOLDER_ACTIONS.contains(action)) {
            return user.getUserName().equals(workflow.getAssignedUser());
        }

        return true;
    }

    /**
     * Adjust result status for workflow type.
     *
     * @param mapProject the map project
     * @param action the action
     * @param nextStatus the next status
     * @return the map workflow status
     */
    private static MapWorkflowStatus adjustResultStatusForWorkflowType(final MapProject mapProject, final MappingWorkflowAction action,
        final MapWorkflowStatus nextStatus) {

        if (mapProject == null || mapProject.getWorkflowType() != WorkflowType.REVIEW_PROJECT) {
            return nextStatus;
        }
        if (action == MappingWorkflowAction.FINISH_EDITING && nextStatus == MapWorkflowStatus.EDITING_DONE) {
            return MapWorkflowStatus.REVIEW_NEEDED;
        }
        return nextStatus;
    }

    /**
     * Restore the workflow phase captured on {@code ASSIGN} when giving up an editing assignment.
     *
     * @param workflow the workflow
     * @param action the action
     * @param nextStatus the permutation result
     * @return the previous workflow status, or {@code nextStatus} when it is unset
     */
    private static MapWorkflowStatus adjustResultStatusForPreviousWorkflowStatus(final MappingWorkflow workflow, final MappingWorkflowAction action,
        final MapWorkflowStatus nextStatus) {

        if (workflow == null || workflow.getPreviousWorkflowStatus() == null) {
            return nextStatus;
        }
        if (action == MappingWorkflowAction.RELEASE || action == MappingWorkflowAction.FORCE_RELEASE) {
            return workflow.getPreviousWorkflowStatus();
        }
        return nextStatus;
    }

    /**
     * Checks if is mapset in edit.
     *
     * @param mapSet the map set
     * @return true, if is mapset in edit
     */
    private static boolean isMapsetInEdit(final MapSet mapSet) {

        return mapSet.getVersionStatus() == VersionStatus.IN_DEVELOPMENT && mapSet.getWorkflowStatus() == WorkflowStatus.IN_EDIT;
    }

    /**
     * Gets the lease duration ms.
     *
     * @return the lease duration ms
     */
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

    /**
     * Checks if is dev bypass enabled.
     *
     * @return true, if is dev bypass enabled
     */
    private static boolean isDevBypassEnabled() {

        final String bypass = PropertyUtility.getProperty("auth.dev.bypass");
        if ("true".equalsIgnoreCase(bypass)) {
            return true;
        }
        final String profiles = PropertyUtility.getProperty("springProfiles");
        return profiles != null && profiles.toLowerCase().contains("dev");
    }

    /**
     * Forbidden — authenticated user lacks permission for this workflow action.
     *
     * @param workflow the workflow
     * @param action the action
     * @return the response status exception
     */
    private static ResponseStatusException forbidden(final MappingWorkflow workflow, final MappingWorkflowAction action) {

        return new ResponseStatusException(HttpStatus.FORBIDDEN,
            "User does not have permission to update mapping workflow for concept " + workflow.getSourceConceptCode()
                + " from status " + workflow.getWorkflowStatus() + " with action " + action);
    }

    /**
     * Conflict — action is not a valid transition from the current workflow status.
     *
     * @param workflow the workflow
     * @param action the action
     * @return the response status exception
     */
    private static ResponseStatusException conflictTransition(final MappingWorkflow workflow, final MappingWorkflowAction action) {

        return new ResponseStatusException(HttpStatus.CONFLICT,
            "Unsuccessful attempt to update mapping workflow for concept " + workflow.getSourceConceptCode()
                + " from status " + workflow.getWorkflowStatus() + " with action " + action);
    }

    /**
     * Whether the role has any permutation that uses the given action.
     *
     * @param role the workflow role
     * @param action the action
     * @return true if the role defines the action for at least one status
     */
    private static boolean roleDefinesAction(final MappingWorkflowRole role, final MappingWorkflowAction action) {

        final Map<MapWorkflowStatus, Map<MappingWorkflowAction, MapWorkflowStatus>> byStatus = WORKFLOW_PERMUTATIONS.get(role);
        if (byStatus == null) {
            return false;
        }
        for (final Map<MappingWorkflowAction, MapWorkflowStatus> byAction : byStatus.values()) {
            if (byAction != null && byAction.containsKey(action)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Find the mapping workflow row for a source concept on a map set (specialist slot 1).
     *
     * @param service the terminology service
     * @param mapSet the map set
     * @param sourceConceptCode the source concept code
     * @return the workflow row, or null if none exists
     * @throws Exception the exception
     */
    public static MappingWorkflow findWorkflowForConcept(final TerminologyService service, final MapSet mapSet, final String sourceConceptCode)
        throws Exception {

        if (mapSet == null || StringUtils.isBlank(sourceConceptCode)) {
            return null;
        }

        final List<MappingWorkflow> results = service.getEntityManager()
            .createQuery("from MappingWorkflow mw where mw.mapSet.id = :mapSetId and mw.sourceConceptCode = :sourceConceptCode"
                + " and mw.specialistSlot = 1 and mw.active = true",
                MappingWorkflow.class)
            .setParameter("mapSetId", mapSet.getId())
            .setParameter("sourceConceptCode", sourceConceptCode)
            .setMaxResults(1)
            .getResultList();

        if (results.isEmpty()) {
            return null;
        }
        return results.get(0);
    }

    /**
     * Find the mapping workflow for a source concept, or a non-persisted {@link MapWorkflowStatus#PUBLISHED}
     * placeholder when the concept is in the mapset but has no workflow row.
     *
     * <p>
     * Does not create database rows. Returns {@code null} when the concept is not in the mapset.
     *
     * @param service the terminology service
     * @param mapSet the map set
     * @param sourceConceptCode the source concept code
     * @return the workflow row, transient placeholder, or null
     * @throws Exception the exception
     */
    public static MappingWorkflow getWorkflowForConcept(final TerminologyService service, final MapSet mapSet, final String sourceConceptCode)
        throws Exception {

        final MappingWorkflow existing = findWorkflowForConcept(service, mapSet, sourceConceptCode);
        if (existing != null) {
            return existing;
        }
        if (!isConceptInMapSet(mapSet, sourceConceptCode)) {
            return null;
        }
        return newPublishedWorkflow(mapSet, sourceConceptCode);
    }

    /**
     * Find the mapping workflow row for a source concept and specialist slot.
     *
     * @param service the terminology service
     * @param mapSet the map set
     * @param sourceConceptCode the source concept code
     * @param specialistSlot the specialist slot (1 or 2)
     * @return the workflow row, or null if none exists
     * @throws Exception the exception
     */
    public static MappingWorkflow findWorkflowForConceptAndSlot(final TerminologyService service, final MapSet mapSet,
        final String sourceConceptCode, final int specialistSlot) throws Exception {

        if (mapSet == null || StringUtils.isBlank(sourceConceptCode) || specialistSlot < 1) {
            return null;
        }

        final List<MappingWorkflow> results = service.getEntityManager()
            .createQuery("from MappingWorkflow mw where mw.mapSet.id = :mapSetId and mw.sourceConceptCode = :sourceConceptCode"
                + " and mw.specialistSlot = :specialistSlot and mw.active = true",
                MappingWorkflow.class)
            .setParameter("mapSetId", mapSet.getId())
            .setParameter("sourceConceptCode", sourceConceptCode)
            .setParameter("specialistSlot", specialistSlot)
            .setMaxResults(1)
            .getResultList();

        if (results.isEmpty()) {
            return null;
        }
        return results.get(0);
    }

    /**
     * Hydrates {@link Mapping#getMappingWorkflow()} for a page of mappings in one query.
     *
     * <p>
     * Attaches an existing specialist-slot-1 workflow row when present; otherwise attaches a non-persisted
     * {@link MapWorkflowStatus#PUBLISHED} placeholder. Missing rows mean the concept already existed in the
     * mapset with no workflow history (initial load or prior-version carryover), not that it was added this
     * version ({@link MapWorkflowStatus#NEW}). Does not create database rows.
     *
     * @param service the terminology service
     * @param mapSet the map set
     * @param mappings the mappings result list
     * @throws Exception the exception
     */
    public static void attachWorkflows(final TerminologyService service, final MapSet mapSet, final ResultListMapping mappings) throws Exception {

        if (mappings == null || mappings.getItems() == null) {
            return;
        }
        attachWorkflows(service, mapSet, mappings.getItems());
    }

    /**
     * Hydrates {@link Mapping#getMappingWorkflow()} for a collection of mappings in one query.
     *
     * <p>
     * Attaches an existing specialist-slot-1 workflow row when present; otherwise attaches a non-persisted
     * {@link MapWorkflowStatus#PUBLISHED} placeholder. Missing rows mean the concept already existed in the
     * mapset with no workflow history (initial load or prior-version carryover), not that it was added this
     * version ({@link MapWorkflowStatus#NEW}). Does not create database rows.
     *
     * @param service the terminology service
     * @param mapSet the map set
     * @param mappings the mappings
     * @throws Exception the exception
     */
    public static void attachWorkflows(final TerminologyService service, final MapSet mapSet, final Collection<Mapping> mappings) throws Exception {

        if (mapSet == null || mappings == null || mappings.isEmpty()) {
            return;
        }

        final Set<String> conceptCodes = new HashSet<>();
        for (final Mapping mapping : mappings) {
            if (mapping != null && StringUtils.isNotBlank(mapping.getCode())) {
                conceptCodes.add(mapping.getCode());
            }
        }
        if (conceptCodes.isEmpty()) {
            return;
        }

        final List<MappingWorkflow> workflows = service.getEntityManager()
            .createQuery("from MappingWorkflow mw where mw.mapSet.id = :mapSetId and mw.sourceConceptCode in :codes"
                + " and mw.specialistSlot = 1 and mw.active = true", MappingWorkflow.class)
            .setParameter("mapSetId", mapSet.getId())
            .setParameter("codes", conceptCodes)
            .getResultList();

        final Map<String, MappingWorkflow> workflowByConcept = new HashMap<>();
        for (final MappingWorkflow workflow : workflows) {
            if (workflow != null && StringUtils.isNotBlank(workflow.getSourceConceptCode())) {
                workflowByConcept.putIfAbsent(workflow.getSourceConceptCode(), workflow);
            }
        }

        for (final Mapping mapping : mappings) {
            if (mapping == null || StringUtils.isBlank(mapping.getCode())) {
                continue;
            }
            final MappingWorkflow existing = workflowByConcept.get(mapping.getCode());
            mapping.setMappingWorkflow(existing != null ? existing : newPublishedWorkflow(mapSet, mapping.getCode()));
        }
    }

    /**
     * Builds a non-persisted specialist-slot-1 workflow with {@link MapWorkflowStatus#PUBLISHED}.
     *
     * @param mapSet the map set
     * @param sourceConceptCode the source concept code
     * @return the transient workflow
     */
    private static MappingWorkflow newPublishedWorkflow(final MapSet mapSet, final String sourceConceptCode) {

        final MappingWorkflow workflow = new MappingWorkflow();
        workflow.setSourceConceptCode(sourceConceptCode);
        workflow.setWorkflowStatus(MapWorkflowStatus.PUBLISHED);
        workflow.setSpecialistSlot(1);
        workflow.setMapSet(mapSet);
        return workflow;
    }

    /**
     * Returns whether the source concept has a mapping in the mapset.
     *
     * @param mapSet the map set
     * @param sourceConceptCode the source concept code
     * @return true if the concept is in the mapset
     * @throws Exception the exception
     */
    private static boolean isConceptInMapSet(final MapSet mapSet, final String sourceConceptCode) throws Exception {

        if (StringUtils.isBlank(sourceConceptCode)) {
            return false;
        }
        return findConceptCodesInMapSet(mapSet, Arrays.asList(sourceConceptCode)).contains(sourceConceptCode);
    }

    /**
     * Returns the subset of {@code conceptCodes} that exist as mappings in the mapset.
     *
     * @param mapSet the map set
     * @param conceptCodes source concept codes
     * @return concept codes present in the mapset
     * @throws Exception the exception
     */
    private static Set<String> findConceptCodesInMapSet(final MapSet mapSet, final List<String> conceptCodes) throws Exception {

        if (mapSet == null || conceptCodes == null || conceptCodes.isEmpty()) {
            return Collections.emptySet();
        }
        final String branch = BranchService.getMapSetBranchPath(mapSet);
        if (StringUtils.isBlank(branch) || "empty".equals(branch) || "none".equals(branch)) {
            return Collections.emptySet();
        }
        final SearchParameters searchParameters = new SearchParameters();
        searchParameters.setLimit(conceptCodes.size());
        final ResultListMapping mappings = MappingService.getMappings(branch, mapSet, searchParameters, StringUtils.EMPTY, true, conceptCodes);
        if (mappings == null || mappings.getItems() == null) {
            return Collections.emptySet();
        }
        final Set<String> found = new HashSet<>();
        for (final Mapping mapping : mappings.getItems()) {
            if (mapping != null && StringUtils.isNotBlank(mapping.getCode())) {
                found.add(mapping.getCode());
            }
        }
        return found;
    }

    /**
     * Ensure a mapping workflow row exists for a source concept (lazy init).
     *
     * @param service the terminology service
     * @param mapSet the map set
     * @param sourceConceptCode the source concept code
     * @return the existing or newly created workflow row for specialist slot 1
     * @throws Exception the exception
     */
    public static MappingWorkflow ensureWorkflowForConcept(final TerminologyService service, final MapSet mapSet, final String sourceConceptCode)
        throws Exception {

        return ensureWorkflowForConcept(service, mapSet, sourceConceptCode, 1);
    }

    /**
     * Ensure a mapping workflow row exists for a source concept and specialist slot (lazy init).
     *
     * @param service the terminology service
     * @param mapSet the map set
     * @param sourceConceptCode the source concept code
     * @param specialistSlot the specialist slot (1 or 2)
     * @return the existing or newly created workflow row
     * @throws Exception the exception
     */
    public static MappingWorkflow ensureWorkflowForConcept(final TerminologyService service, final MapSet mapSet, final String sourceConceptCode,
        final int specialistSlot) throws Exception {

        final MappingWorkflow existing = findWorkflowForConceptAndSlot(service, mapSet, sourceConceptCode, specialistSlot);
        if (existing != null) {
            return existing;
        }

        final MapProject mapProject = loadMapProject(service, mapSet);
        final MappingWorkflow workflow = new MappingWorkflow();
        workflow.setSourceConceptCode(sourceConceptCode);
        workflow.setWorkflowStatus(MapWorkflowStatus.PUBLISHED);
        workflow.setSpecialistSlot(specialistSlot);
        workflow.setMapSet(mapSet);
        workflow.setMapProject(mapProject);
        return service.add(workflow);
    }

    /**
     * Count active mapping workflow rows for a map set, concept, and specialist slot.
     *
     * @param service the terminology service
     * @param mapSet the map set
     * @param sourceConceptCode the source concept code
     * @param specialistSlot the specialist slot
     * @return the row count
     * @throws Exception the exception
     */
    public static int countWorkflowRows(final TerminologyService service, final MapSet mapSet, final String sourceConceptCode, final int specialistSlot)
        throws Exception {

        if (mapSet == null || StringUtils.isBlank(sourceConceptCode) || specialistSlot < 1) {
            return 0;
        }

        final Long count = service.getEntityManager()
            .createQuery("select count(mw) from MappingWorkflow mw where mw.mapSet.id = :mapSetId and mw.sourceConceptCode = :sourceConceptCode"
                + " and mw.specialistSlot = :specialistSlot and mw.active = true", Long.class)
            .setParameter("mapSetId", mapSet.getId())
            .setParameter("sourceConceptCode", sourceConceptCode)
            .setParameter("specialistSlot", specialistSlot)
            .getSingleResult();
        return count == null ? 0 : count.intValue();
    }

    /**
     * Verify no per-mapping workflow rows block the requested mapset transition.
     *
     * @param service the terminology service
     * @param mapSet the map set
     * @param action the mapset workflow action
     * @throws Exception the exception
     */
    public static void assertMapsetTransitionAllowed(final TerminologyService service, final MapSet mapSet, final WorkflowAction action) throws Exception {

        if (mapSet == null || mapSet.getMapProject() == null || action == null || !MAPSET_GATE_ACTIONS.contains(action)) {
            return;
        }

        final List<MappingWorkflow> blockingMappings = findBlockingMappings(service, mapSet);
        if (blockingMappings.isEmpty()) {
            return;
        }

        final StringBuilder message = new StringBuilder("Mapset transition blocked by mapping(s) in progress: ");
        for (int index = 0; index < blockingMappings.size(); index++) {
            if (index > 0) {
                message.append(", ");
            }
            final MappingWorkflow workflow = blockingMappings.get(index);
            message.append(workflow.getSourceConceptCode()).append(" (").append(workflow.getWorkflowStatus()).append(")");
        }
        throw new ResponseStatusException(HttpStatus.CONFLICT, message.toString());
    }

    /**
     * Find mapping workflow rows that block mapset finish or publication requests.
     *
     * @param service the terminology service
     * @param mapSet the map set
     * @return blocking mapping workflow rows
     * @throws Exception the exception
     */
    public static List<MappingWorkflow> findBlockingMappings(final TerminologyService service, final MapSet mapSet) throws Exception {

        if (mapSet == null || StringUtils.isBlank(mapSet.getId())) {
            return List.of();
        }

        final List<MappingWorkflow> results = service.getEntityManager()
            .createQuery("from MappingWorkflow mw where mw.mapSet.id = :mapSetId and mw.active = true"
                + " and mw.workflowStatus in :blockingStatuses",
                MappingWorkflow.class)
            .setParameter("mapSetId", mapSet.getId())
            .setParameter("blockingStatuses", MAPSET_GATE_BLOCKING_STATUSES)
            .getResultList();
        return results;
    }

    /**
     * Load the map project for a map set via primary key lookup.
     *
     * @param service the terminology service
     * @param mapSet the map set
     * @return the map project with membership collections initialized
     * @throws Exception the exception
     */
    public static MapProject loadMapProject(final TerminologyService service, final MapSet mapSet) throws Exception {

        if (mapSet == null || mapSet.getMapProject() == null || StringUtils.isBlank(mapSet.getMapProject().getId())) {
            return null;
        }
        return prepareMapProject(service.get(mapSet.getMapProject().getId(), MapProject.class));
    }

    /**
     * Initialize lazy map-project membership collections needed for role resolution.
     *
     * @param mapProject the map project
     * @return the same map project instance with collections initialized
     */
    public static MapProject prepareMapProject(final MapProject mapProject) {

        if (mapProject != null) {
            Hibernate.initialize(mapProject.getMapSpecialists());
            Hibernate.initialize(mapProject.getMapLeads());
        }
        return mapProject;
    }

    /**
     * Verify the user may edit mapping data for a source concept.
     *
     * <p>
     * Allowed when the mapset is in edit, the mapping is {@code EDITING_IN_PROGRESS} or
     * {@code REVIEW_IN_PROGRESS}, and the acting user holds the assignment.
     *
     * @param user the acting user
     * @param workflow the mapping workflow row
     * @param mapSet the map set
     * @throws Exception the exception
     */
    public static void canUserEditMapping(final User user, final MappingWorkflow workflow, final MapSet mapSet) throws Exception {

        if (!isMapsetInEdit(mapSet)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Reference Set is not in edit; mapping cannot be edited.");
        }

        if (workflow == null || !MAPPING_EDITABLE_STATUSES.contains(workflow.getWorkflowStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Mapping is not assigned for editing; mapping cannot be edited.");
        }

        if (workflow.getAssignedUser() == null || !workflow.getAssignedUser().equals(user.getUserName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Mapping assignment does not match the acting user; user does not have permission to edit this mapping.");
        }
    }

    /**
     * Enforce edit permission for each mapping in an update payload.
     *
     * @param user the acting user
     * @param mapSet the map set
     * @param mappings the mappings to update
     * @param service the terminology service
     * @throws Exception the exception
     */
    public static void canUserEditMappings(final User user, final MapSet mapSet, final List<Mapping> mappings, final TerminologyService service)
        throws Exception {

        if (mappings == null) {
            return;
        }

        for (final Mapping mapping : mappings) {
            if (mapping == null || StringUtils.isBlank(mapping.getCode())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mapping source concept code is required.");
            }
            final MappingWorkflow workflow = findWorkflowForConcept(service, mapSet, mapping.getCode());
            canUserEditMapping(user, workflow, mapSet);
        }
    }

    /**
     * Replace concept-branch operations (for unit tests only).
     *
     * @param operations the operations delegate, or null to restore the default
     */
    static void setConceptBranchOperationsForTests(final ConceptBranchOperations operations) {

        conceptBranchOperations = operations != null ? operations : new DefaultConceptBranchOperations();
    }

    /**
     * Apply concept branch side effects.
     *
     * @param action the action
     * @param mapSet the map set
     * @param workflow the workflow
     * @throws Exception the exception
     */
    private static void applyConceptBranchSideEffects(final MappingWorkflowAction action, final MapSet mapSet, final MappingWorkflow workflow)
        throws Exception {

        if (!isConceptBranchSideEffectsEnabled() || !CONCEPT_BRANCH_ACTIONS.contains(action)) {
            return;
        }

        final String conceptCode = workflow.getSourceConceptCode();
        try {
            switch (action) {
                case ASSIGN:
                    conceptBranchOperations.createConceptBranch(mapSet, conceptCode);
                    break;
                case FINISH_EDITING:
                    conceptBranchOperations.mergeConceptToEdit(mapSet, conceptCode);
                    break;
                case RELEASE:
                case FORCE_RELEASE:
                    conceptBranchOperations.deleteConceptBranch(mapSet, conceptCode);
                    break;
                default:
                    break;
            }
        } catch (final Exception e) {
            LOG.error("Concept branch side effect failed for concept {} with action {}", conceptCode, action, e);
            if (action == MappingWorkflowAction.RELEASE || action == MappingWorkflowAction.FORCE_RELEASE) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to delete concept branch for " + conceptCode + ": " + e.getMessage(), e);
            }
            throw e;
        }
    }

    /**
     * Checks if is concept branch side effects enabled.
     *
     * @return true, if is concept branch side effects enabled
     */
    private static boolean isConceptBranchSideEffectsEnabled() {

        final String enabled = PropertyUtility.getProperty(CONCEPT_BRANCH_ENABLED_PROPERTY);
        return !"false".equalsIgnoreCase(enabled);
    }

    /**
     * Apply conflict join after finish.
     *
     * @param service the service
     * @param mapSet the map set
     * @param workflow the workflow
     * @throws Exception the exception
     */
    private static void applyConflictJoinAfterFinish(final TerminologyService service, final MapSet mapSet, final MappingWorkflow workflow)
        throws Exception {

        if (workflow.getWorkflowStatus() != MapWorkflowStatus.EDITING_DONE) {
            return;
        }

        final MappingWorkflow sibling = findSiblingWorkflow(service, mapSet, workflow);
        if (sibling == null || sibling.getWorkflowStatus() != MapWorkflowStatus.EDITING_DONE) {
            return;
        }

        final MappingWorkflow slot1Workflow = workflow.getSpecialistSlot() == 1 ? workflow : sibling;
        final MappingWorkflow slot2Workflow = workflow.getSpecialistSlot() == 2 ? workflow : sibling;
        final String slot1Key = getFinishComparisonKey(service, slot1Workflow);
        final String slot2Key = getFinishComparisonKey(service, slot2Workflow);
        final MapWorkflowStatus joinStatus = slot1Key.equals(slot2Key) ? MapWorkflowStatus.REVIEW_NEEDED : MapWorkflowStatus.CONFLICT_DETECTED;

        workflow.setWorkflowStatus(joinStatus);
        workflow.setAssignedUser(null);
        workflow.setAssignedAt(null);
        workflow.setLeaseExpiresAt(null);
        sibling.setWorkflowStatus(joinStatus);
        sibling.setAssignedUser(null);
        sibling.setAssignedAt(null);
        sibling.setLeaseExpiresAt(null);
        service.update(workflow);
        service.update(sibling);
    }

    /**
     * Sync conflict sibling if needed.
     *
     * @param service the service
     * @param user the user
     * @param action the action
     * @param workflow the workflow
     * @param mapSet the map set
     * @param mapProject the map project
     * @param notes the notes
     * @throws Exception the exception
     */
    private static void syncConflictSiblingIfNeeded(final TerminologyService service, final User user, final MappingWorkflowAction action,
        final MappingWorkflow workflow, final MapSet mapSet, final MapProject mapProject, final String notes) throws Exception {

        if (mapProject == null || mapProject.getWorkflowType() != WorkflowType.CONFLICT_PROJECT) {
            return;
        }
        if (action != MappingWorkflowAction.START_CONFLICT_RESOLUTION && action != MappingWorkflowAction.RESOLVE_CONFLICT) {
            return;
        }

        final MappingWorkflow sibling = findSiblingWorkflow(service, mapSet, workflow);
        if (sibling == null) {
            return;
        }

        sibling.setWorkflowStatus(workflow.getWorkflowStatus());
        sibling.setAssignedUser(workflow.getAssignedUser());
        sibling.setAssignedAt(workflow.getAssignedAt());
        sibling.setLeaseExpiresAt(workflow.getLeaseExpiresAt());
        service.update(sibling);
        addWorkflowHistory(service, user, action, sibling, notes);
    }

    /**
     * Find sibling workflow.
     *
     * @param service the service
     * @param mapSet the map set
     * @param workflow the workflow
     * @return the mapping workflow
     * @throws Exception the exception
     */
    private static MappingWorkflow findSiblingWorkflow(final TerminologyService service, final MapSet mapSet, final MappingWorkflow workflow)
        throws Exception {

        if (workflow == null || workflow.getSpecialistSlot() != 1 && workflow.getSpecialistSlot() != 2) {
            return null;
        }
        final int siblingSlot = workflow.getSpecialistSlot() == 1 ? 2 : 1;
        return findWorkflowForConceptAndSlot(service, mapSet, workflow.getSourceConceptCode(), siblingSlot);
    }

    /**
     * Gets the finish comparison key.
     *
     * @param service the service
     * @param workflow the workflow
     * @return the finish comparison key
     * @throws Exception the exception
     */
    private static String getFinishComparisonKey(final TerminologyService service, final MappingWorkflow workflow) throws Exception {

        final SearchParameters searchParameters = new SearchParameters();
        searchParameters.setSort("modified");
        searchParameters.setSortAscending(false);
        searchParameters.setLimit(50);
        final ResultList<MappingWorkflowHistory> history = getWorkflowHistory(service, workflow, searchParameters);
        for (final MappingWorkflowHistory row : history.getItems()) {
            if (row.getWorkflowAction() == MappingWorkflowAction.FINISH_EDITING) {
                return StringUtils.defaultString(row.getNotes());
            }
        }
        return "";
    }

    /**
     * Per-concept Snowstorm branch side effects.
     */
    interface ConceptBranchOperations {

        /**
         * Create a concept sub-branch under the mapset edit branch.
         *
         * @param mapSet the map set
         * @param conceptCode the source concept code
         * @return the concept branch path
         * @throws Exception the exception
         */
        String createConceptBranch(MapSet mapSet, String conceptCode) throws Exception;

        /**
         * Merge a concept sub-branch into the shared edit branch.
         *
         * @param mapSet the map set
         * @param conceptCode the source concept code
         * @throws Exception the exception
         */
        void mergeConceptToEdit(MapSet mapSet, String conceptCode) throws Exception;

        /**
         * Delete a concept sub-branch.
         *
         * @param mapSet the map set
         * @param conceptCode the source concept code
         * @throws Exception the exception
         */
        void deleteConceptBranch(MapSet mapSet, String conceptCode) throws Exception;
    }

    /**
     * The Class DefaultConceptBranchOperations.
     */
    private static final class DefaultConceptBranchOperations implements ConceptBranchOperations {

        /**
         * Creates the concept branch.
         *
         * @param mapSet the map set
         * @param conceptCode the concept code
         * @return the string
         * @throws Exception the exception
         */
        @Override
        public String createConceptBranch(final MapSet mapSet, final String conceptCode) throws Exception {

            return BranchService.createConceptBranch(mapSet, conceptCode);
        }

        /**
         * Merge concept to edit.
         *
         * @param mapSet the map set
         * @param conceptCode the concept code
         * @throws Exception the exception
         */
        @Override
        public void mergeConceptToEdit(final MapSet mapSet, final String conceptCode) throws Exception {

            BranchService.mergeConceptToEdit(mapSet, conceptCode);
        }

        /**
         * Delete concept branch.
         *
         * @param mapSet the map set
         * @param conceptCode the concept code
         * @throws Exception the exception
         */
        @Override
        public void deleteConceptBranch(final MapSet mapSet, final String conceptCode) throws Exception {

            BranchService.deleteConceptBranch(mapSet, conceptCode);
        }
    }
}
