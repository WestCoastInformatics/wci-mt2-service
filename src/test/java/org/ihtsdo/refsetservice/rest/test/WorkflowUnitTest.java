/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.ihtsdo.refsetservice.model.enums.WorkflowAction;
import org.ihtsdo.refsetservice.model.enums.WorkflowStatus;
import org.ihtsdo.refsetservice.rest.test.util.WorkflowUnitTestUtilities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for workflow permissible paths (no HTTP, no refsets, no DB).
 */
public class WorkflowUnitTest {

    /** The AUTHOR_USER workflow user . */
    private static final String AUTHOR_USER = "AUTHOR_USER";

    /** The REVIEWER_USER workflow user . */
    private static final String REVIEWER_USER = "REVIEWER_USER";

    /** The ADMIN_USER workflow user . */
    private static final String ADMIN_USER = "ADMIN_USER";

    /** The statuses . */
    private static final List<WorkflowStatus> statuses = WorkflowStatus.getValues();

    /** The users . */
    private static final List<String> users = new ArrayList<>(Arrays.asList(AUTHOR_USER, REVIEWER_USER, ADMIN_USER));

    private WorkflowUnitTestUtilities workflowUtil;

    @BeforeEach
    public void setUp() throws Exception {
        final String refsetFilePath = Paths.get(getClass().getClassLoader()
            .getResource("refsetService/workflowPermutationsToFinalAction.txt").toURI())
            .getParent().toString() + "/";
        workflowUtil = new WorkflowUnitTestUtilities(null, null, refsetFilePath);
    }

    /**
     * For each role/initial-state pair, ensure that each possible action results in the expected state. If the action is available to that pair, then verify
     * the final-state is as expected. If the action is not available to that pair, verify that the final-state is null
     *
     * @throws Exception the exception
     */
    @Test
    public void testAllRolesAndInitialStates() throws Exception {

        for (final String user : users) {

            final String userRole = workflowUtil.getUserRole(user);

            for (final WorkflowStatus initialStatus : statuses) {

                final Map<WorkflowAction, String> results = workflowUtil.getWorkflowActionPaths(user, initialStatus);

                for (final WorkflowAction action : results.keySet()) {

                    if (workflowUtil.getPermissiblePaths().containsKey(userRole)
                        && workflowUtil.getPermissiblePaths().get(userRole).containsKey(initialStatus)
                        && workflowUtil.getPermissiblePaths().get(userRole).get(initialStatus).containsKey(action)) {

                        assertThat(results.get(action)).isEqualTo(workflowUtil.getPermissiblePaths().get(userRole).get(initialStatus).get(action));
                    } else {

                        assertThat(results.get(action)).isNull();
                    }

                }

            }

        }

    }

}
