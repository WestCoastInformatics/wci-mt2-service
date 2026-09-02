package org.ihtsdo.refsetservice.rest.test.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.model.ResultListMapping;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.util.SearchParameters;

/**
 * Terminology handler for mapping workflow API tests. Stubs Snowstorm mapping updates.
 */
public class MappingWorkflowTestHandler extends MapSetWorkflowTestHandler {

    /** Whether the last updateMappings call reached the handler. */
    private static boolean updateCalled;

    /** The mappings from the last updateMappings call. */
    private static List<Mapping> lastUpdatedMappings = new ArrayList<>();

    /** Concept codes treated as not present in the mapset. */
    private static final Set<String> conceptsNotInMapSet = new HashSet<>();

    /**
     * Reset handler state between tests.
     */
    public static void reset() {

        updateCalled = false;
        lastUpdatedMappings = new ArrayList<>();
        conceptsNotInMapSet.clear();
    }

    /**
     * Treat the concept as missing from the mapset for subsequent getMapping/getMappings calls.
     *
     * @param conceptCode the source concept code
     */
    public static void markConceptMissingFromMapSet(final String conceptCode) {

        if (StringUtils.isNotBlank(conceptCode)) {
            conceptsNotInMapSet.add(conceptCode);
        }
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
        final MapSet mapSet, final User user) {

        return recordUpdate(mappings);
    }

    private static List<Mapping> recordUpdate(final List<Mapping> mappings) {

        updateCalled = true;
        lastUpdatedMappings = mappings == null ? new ArrayList<>() : new ArrayList<>(mappings);
        return lastUpdatedMappings;
    }

    @Override
    public Mapping getMapping(final String branch, final String conceptCode, final boolean showOverriddenEntries, final MapSet mapSet) {

        if (!isInMapSet(conceptCode)) {
            return new Mapping();
        }
        final Mapping mapping = new Mapping();
        mapping.setCode(conceptCode);
        return mapping;
    }

    @Override
    public ResultListMapping getMappings(final String branch, final MapSet mapSet, final SearchParameters searchParameters, final String filter,
        final boolean showOverriddenEntries, final List<String> conceptCodes) {

        return getMappings(branch, mapSet, searchParameters, filter, showOverriddenEntries, conceptCodes, null);
    }

    @Override
    public ResultListMapping getMappings(final String branch, final MapSet mapSet, final SearchParameters searchParameters, final String filter,
        final boolean showOverriddenEntries, final List<String> conceptCodes, final Collection<String> restrictToConceptCodes) {

        final ResultListMapping result = new ResultListMapping();
        final List<String> codes = new ArrayList<>();
        if (conceptCodes != null && !conceptCodes.isEmpty()) {
            codes.addAll(conceptCodes);
            if (restrictToConceptCodes != null) {
                codes.retainAll(new HashSet<>(restrictToConceptCodes));
            }
        } else if (restrictToConceptCodes != null) {
            for (final String conceptCode : restrictToConceptCodes) {
                if (StringUtils.isNotBlank(conceptCode)) {
                    codes.add(conceptCode);
                }
            }
        }
        for (final String conceptCode : codes) {
            if (!isInMapSet(conceptCode)) {
                continue;
            }
            final Mapping mapping = new Mapping();
            mapping.setCode(conceptCode);
            result.getItems().add(mapping);
        }
        result.setTotal(result.getItems().size());
        result.setTotalKnown(true);
        if (searchParameters != null) {
            result.setLimit(searchParameters.getLimit() != null ? searchParameters.getLimit() : 0);
            result.setOffset(searchParameters.getOffset() != null ? searchParameters.getOffset() : 0);
        }
        return result;
    }

    private static boolean isInMapSet(final String conceptCode) {

        return StringUtils.isNotBlank(conceptCode) && !conceptsNotInMapSet.contains(conceptCode);
    }
}
