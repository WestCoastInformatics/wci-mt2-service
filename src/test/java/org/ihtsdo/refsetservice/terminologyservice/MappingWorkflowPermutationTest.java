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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.MapWorkflowStatus;
import org.ihtsdo.refsetservice.model.enums.MappingWorkflowAction;
import org.ihtsdo.refsetservice.model.enums.MappingWorkflowRole;
import org.ihtsdo.refsetservice.util.FieldedStringTokenizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

/**
 * Unit tests for {@link MappingWorkflowService} permutation loading.
 */
public class MappingWorkflowPermutationTest {

    private static final String WORKFLOW_PERMUTATIONS_FILE_NAME = "workflow/mappingWorkflowPermutations.txt";

    /** Expected rows from the permutations file: role -> status -> action -> result. */
    private Map<MappingWorkflowRole, Map<MapWorkflowStatus, Map<MappingWorkflowAction, MapWorkflowStatus>>> permissiblePaths;

    /**
     * Reload permutations from the classpath file before each test.
     *
     * @throws Exception the exception
     */
    @BeforeEach
    public void setUp() throws Exception {

        permissiblePaths = readPermutationsFromClasspath();
        try (final BufferedReader bufferedReader = new BufferedReader(
            new InputStreamReader(new ClassPathResource(WORKFLOW_PERMUTATIONS_FILE_NAME).getInputStream()))) {
            MappingWorkflowService.loadPermutations(bufferedReader, WORKFLOW_PERMUTATIONS_FILE_NAME);
        }
    }

    @Test
    public void testAllRolesAndInitialStates() {

        for (final MappingWorkflowRole role : MappingWorkflowRole.values()) {
            for (final MapWorkflowStatus status : MapWorkflowStatus.values()) {
                for (final MappingWorkflowAction action : MappingWorkflowAction.values()) {
                    final MapWorkflowStatus expected = getExpectedResult(role, status, action);
                    final MapWorkflowStatus actual = MappingWorkflowService.resolveTransition(role, status, action);
                    if (expected != null) {
                        assertEquals(expected, actual, role + "," + status + "," + action);
                    } else {
                        assertNull(actual, role + "," + status + "," + action);
                    }
                }
            }
        }
    }

    @Test
    public void testSimplePathRowsPresent() {

        assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS,
            MappingWorkflowService.resolveTransition(MappingWorkflowRole.SPECIALIST, MapWorkflowStatus.NEW, MappingWorkflowAction.ASSIGN));
        assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS,
            MappingWorkflowService.resolveTransition(MappingWorkflowRole.SPECIALIST, MapWorkflowStatus.PUBLISHED, MappingWorkflowAction.ASSIGN));
        assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS,
            MappingWorkflowService.resolveTransition(MappingWorkflowRole.SPECIALIST, MapWorkflowStatus.READY_FOR_PUBLICATION, MappingWorkflowAction.ASSIGN));
        assertEquals(MapWorkflowStatus.NEW,
            MappingWorkflowService.resolveTransition(MappingWorkflowRole.SPECIALIST, MapWorkflowStatus.EDITING_IN_PROGRESS, MappingWorkflowAction.RELEASE));
        assertEquals(MapWorkflowStatus.EDITING_DONE,
            MappingWorkflowService.resolveTransition(MappingWorkflowRole.SPECIALIST, MapWorkflowStatus.EDITING_IN_PROGRESS, MappingWorkflowAction.FINISH_EDITING));
        assertEquals(MapWorkflowStatus.READY_FOR_PUBLICATION,
            MappingWorkflowService.resolveTransition(MappingWorkflowRole.LEAD, MapWorkflowStatus.EDITING_DONE, MappingWorkflowAction.APPROVE_FOR_PUBLICATION));
        assertEquals(MapWorkflowStatus.REVIEW_NEEDED,
            MappingWorkflowService.resolveTransition(MappingWorkflowRole.SPECIALIST, MapWorkflowStatus.EDITING_DONE, MappingWorkflowAction.REQUEST_REVIEW));
        assertEquals(MapWorkflowStatus.REVIEW_NEEDED,
            MappingWorkflowService.resolveTransition(MappingWorkflowRole.LEAD, MapWorkflowStatus.EDITING_DONE, MappingWorkflowAction.REQUEST_REVIEW));
    }

    @Test
    public void testReviewProjectRowsPresent() {

        assertEquals(MapWorkflowStatus.REVIEW_IN_PROGRESS,
            MappingWorkflowService.resolveTransition(MappingWorkflowRole.LEAD, MapWorkflowStatus.REVIEW_NEEDED, MappingWorkflowAction.START_REVIEW));
        assertEquals(MapWorkflowStatus.READY_FOR_PUBLICATION,
            MappingWorkflowService.resolveTransition(MappingWorkflowRole.LEAD, MapWorkflowStatus.REVIEW_IN_PROGRESS, MappingWorkflowAction.ACCEPT_REVIEW));
        assertEquals(MapWorkflowStatus.NEW,
            MappingWorkflowService.resolveTransition(MappingWorkflowRole.LEAD, MapWorkflowStatus.REVIEW_IN_PROGRESS, MappingWorkflowAction.REJECT_REVIEW));
        assertEquals(MapWorkflowStatus.READY_FOR_PUBLICATION,
            MappingWorkflowService.resolveTransition(MappingWorkflowRole.LEAD, MapWorkflowStatus.REVIEW_RESOLVED, MappingWorkflowAction.APPROVE_FOR_PUBLICATION));
    }

    @Test
    public void testMalformedFileFailsFast() throws Exception {

        final Exception thrown = assertThrows(Exception.class, () -> MappingWorkflowService.loadPermutations(
            new StringReader("SPECIALIST,NEW,ASSIGN\n"), "malformed.txt"));
        assertTrue(thrown.getMessage().contains("does not have 4 items"));
    }

    private MapWorkflowStatus getExpectedResult(final MappingWorkflowRole role, final MapWorkflowStatus status, final MappingWorkflowAction action) {

        if (!permissiblePaths.containsKey(role) || !permissiblePaths.get(role).containsKey(status)) {
            return null;
        }
        return permissiblePaths.get(role).get(status).get(action);
    }

    private static Map<MappingWorkflowRole, Map<MapWorkflowStatus, Map<MappingWorkflowAction, MapWorkflowStatus>>> readPermutationsFromClasspath()
        throws Exception {

        final Map<MappingWorkflowRole, Map<MapWorkflowStatus, Map<MappingWorkflowAction, MapWorkflowStatus>>> paths = new HashMap<>();

        try (final BufferedReader bufferedReader = new BufferedReader(
            new InputStreamReader(new ClassPathResource(WORKFLOW_PERMUTATIONS_FILE_NAME).getInputStream()))) {

            String line;
            while ((line = bufferedReader.readLine()) != null) {

                if (line.startsWith("#") || StringUtils.isBlank(line)) {
                    continue;
                }

                final String[] tokens = FieldedStringTokenizer.split(line, ",");
                final MappingWorkflowRole role = MappingWorkflowRole.fromString(tokens[0].toUpperCase().strip());
                final MapWorkflowStatus currentStatus = MapWorkflowStatus.fromString(tokens[1].toUpperCase().strip());
                final MappingWorkflowAction workflowAction = MappingWorkflowAction.fromString(tokens[2].toUpperCase().strip());
                final MapWorkflowStatus resultingStatus = MapWorkflowStatus.fromString(tokens[3].toUpperCase().strip());

                paths.computeIfAbsent(role, key -> new HashMap<>())
                    .computeIfAbsent(currentStatus, key -> new HashMap<>())
                    .put(workflowAction, resultingStatus);
            }
        }

        return paths;
    }
}
