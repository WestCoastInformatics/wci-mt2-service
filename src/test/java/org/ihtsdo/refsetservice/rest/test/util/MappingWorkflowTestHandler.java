package org.ihtsdo.refsetservice.rest.test.util;

import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.Mapping;

/**
 * Terminology handler for mapping workflow API tests. Stubs Snowstorm mapping updates.
 */
public class MappingWorkflowTestHandler extends MapSetWorkflowTestHandler {

    /** Whether the last updateMappings call reached the handler. */
    private static boolean updateCalled;

    /** The mappings from the last updateMappings call. */
    private static List<Mapping> lastUpdatedMappings = new ArrayList<>();

    /**
     * Reset handler state between tests.
     */
    public static void reset() {

        updateCalled = false;
        lastUpdatedMappings = new ArrayList<>();
    }

    /**
     * Returns whether updateMappings was invoked.
     *
     * @return true if updateMappings was called
     */
    public static boolean wasUpdateCalled() {

        return updateCalled;
    }

    /**
     * Returns the mappings passed to the last updateMappings call.
     *
     * @return the last updated mappings
     */
    public static List<Mapping> getLastUpdatedMappings() {

        return lastUpdatedMappings;
    }

    @Override
    public List<Mapping> updateMappings(final MapProject mapProject, final String branch, final String mapSetCode, final List<Mapping> mappings,
        final MapSet mapSet) {

        updateCalled = true;
        lastUpdatedMappings = mappings == null ? new ArrayList<>() : new ArrayList<>(mappings);
        return lastUpdatedMappings;
    }
}
