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
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.MapWorkflowStatus;
import org.ihtsdo.refsetservice.model.enums.MappingWorkflowAction;
import org.ihtsdo.refsetservice.util.FieldedStringTokenizer;
import org.springframework.core.io.ClassPathResource;

/**
 * Per-mapping workflow transition rules loaded from {@code workflow/mappingWorkflowPermutations.txt}.
 */
public final class MappingWorkflowService {

    /** The file that contains mapping workflow actions by role and phase. */
    private static final String WORKFLOW_PERMUTATIONS_FILE_NAME = "workflow/mappingWorkflowPermutations.txt";

    /** Map&lt;role, Map&lt;currentStatus, Map&lt;action, resultingStatus&gt;&gt;&gt;. */
    private static final Map<String, Map<MapWorkflowStatus, Map<MappingWorkflowAction, MapWorkflowStatus>>> WORKFLOW_PERMUTATIONS = new HashMap<>();

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
     * @param role the project role (e.g. SPECIALIST, LEAD, ADMIN)
     * @param status the current workflow phase
     * @param action the requested action
     * @return the resulting phase, or null if no row exists
     */
    public static MapWorkflowStatus resolveTransition(final String role, final MapWorkflowStatus status, final MappingWorkflowAction action) {

        if (role == null || status == null || action == null) {
            return null;
        }

        final Map<MapWorkflowStatus, Map<MappingWorkflowAction, MapWorkflowStatus>> byStatus = WORKFLOW_PERMUTATIONS.get(role.toUpperCase().strip());
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

                final String role = tokens[0].toUpperCase().strip();
                final MapWorkflowStatus currentStatus = MapWorkflowStatus.fromString(tokens[1].toUpperCase().strip());
                final MappingWorkflowAction workflowAction = MappingWorkflowAction.fromString(tokens[2].toUpperCase().strip());
                final MapWorkflowStatus resultingStatus = MapWorkflowStatus.fromString(tokens[3].toUpperCase().strip());

                WORKFLOW_PERMUTATIONS.computeIfAbsent(role, key -> new HashMap<>())
                    .computeIfAbsent(currentStatus, key -> new HashMap<>())
                    .put(workflowAction, resultingStatus);
            }
        }
    }
}
