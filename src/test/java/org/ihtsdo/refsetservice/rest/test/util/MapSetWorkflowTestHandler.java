/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.rest.test.util;

import java.util.Collections;
import java.util.List;

import org.ihtsdo.refsetservice.handler.SNOMEDSnowstormTerminologyServerHandler;

/**
 * Terminology handler for MapSet workflow tests. Overrides getBranchVersions to return a dummy value so workflow transitions that do not require Snowstorm can
 * run without a Snowstorm server.
 */
public class MapSetWorkflowTestHandler extends SNOMEDSnowstormTerminologyServerHandler {

    @Override
    public List<String> getBranchVersions(final String editionPath) {

        return Collections.singletonList("2025-01-01");
    }
}
