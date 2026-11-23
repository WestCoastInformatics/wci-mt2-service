/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.ihtsdo.refsetservice.handler.TerminologyServerHandler;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.model.MappingExportRequest;
import org.ihtsdo.refsetservice.model.ResultListMapping;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.HandlerUtility;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service class to get refset member concept information from a terminology
 * service.
 *
 * @author jesseefron
 */
public final class MappingService {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(MappingService.class);

    /**
     *  The terminology handler.
     *
     * @return the terminology handler
     */
    private static TerminologyServerHandler getTerminologyHandler() {

        return HandlerUtility.getOrCreateTerminologyHandler();
    }

    /**
     * Instantiates an empty {@link MappingService}.
     */
    private MappingService() {

        // n/a
    }

    /**
     * Returns the mappings.
     *
     * @param branch the branch
     * @param mapSet the map set
     * @param searchParameters the search parameters
     * @param filter the filter
     * @param showOverriddenEntries the show overridden entries
     * @param conceptCodes the concept codes
     * @return the mappings
     * @throws Exception the exception
     */
    public static ResultListMapping getMappings(final String branch, final MapSet mapSet, final SearchParameters searchParameters, final String filter,
        final boolean showOverriddenEntries, final List<String> conceptCodes) throws Exception {

        return getMappings(branch, mapSet, searchParameters, filter, showOverriddenEntries, conceptCodes, null);
    }

    /**
     * Returns the mappings, optionally restricted to the given source concept codes after filter resolution.
     *
     * @param branch the branch
     * @param mapSet the map set
     * @param searchParameters the search parameters
     * @param filter the filter
     * @param showOverriddenEntries the show overridden entries
     * @param conceptCodes the concept codes
     * @param restrictToConceptCodes when non-null, only these source concept codes are returned
     * @return the mappings
     * @throws Exception the exception
     */
    public static ResultListMapping getMappings(final String branch, final MapSet mapSet, final SearchParameters searchParameters, final String filter,
        final boolean showOverriddenEntries, final List<String> conceptCodes, final Collection<String> restrictToConceptCodes) throws Exception {

        return getMappings(branch, mapSet, searchParameters, filter, showOverriddenEntries, conceptCodes, restrictToConceptCodes, null);
    }

    /**
     * Returns the mappings, optionally restricted to and/or excluding source concept codes after filter resolution.
     *
     * @param branch the branch
     * @param mapSet the map set
     * @param searchParameters the search parameters
     * @param filter the filter
     * @param showOverriddenEntries the show overridden entries
     * @param conceptCodes the concept codes
     * @param restrictToConceptCodes when non-null, only these source concept codes are returned
     * @param excludeConceptCodes when non-null, these source concept codes are omitted
     * @return the mappings
     * @throws Exception the exception
     */
    public static ResultListMapping getMappings(final String branch, final MapSet mapSet, final SearchParameters searchParameters, final String filter,
        final boolean showOverriddenEntries, final List<String> conceptCodes, final Collection<String> restrictToConceptCodes,
        final Collection<String> excludeConceptCodes) throws Exception {

        if (mapSet == null) {
            throw new IllegalArgumentException("MapSet from database is required for getMappings. No fallback.");
        }
        return getTerminologyHandler().getMappings(branch, mapSet, searchParameters, filter, showOverriddenEntries, conceptCodes, restrictToConceptCodes,
            excludeConceptCodes);
    }

    /**
     * Returns the mapping.
     *
     * @param branch the branch
     * @param conceptCode the concept code
     * @param showOverriddenEntries the show overridden entries
     * @param mapSet the map set
     * @return the mapping
     * @throws Exception the exception
     */
    public static Mapping getMapping(final String branch, final String conceptCode, final boolean showOverriddenEntries, final MapSet mapSet) throws Exception {

        return getMapping(branch, conceptCode, showOverriddenEntries, false, mapSet);
    }

    /**
     * Returns the mapping.
     *
     * @param branch the branch
     * @param conceptCode the concept code
     * @param showOverriddenEntries the show overridden entries
     * @param includeInactive when true, return active and inactive members from the International module and the edition module
     * @param mapSet the map set
     * @return the mapping
     * @throws Exception the exception
     */
    public static Mapping getMapping(final String branch, final String conceptCode, final boolean showOverriddenEntries, final boolean includeInactive,
        final MapSet mapSet) throws Exception {

        return getTerminologyHandler().getMapping(branch, conceptCode, showOverriddenEntries, includeInactive, mapSet);
    }

    /**
     * Creates the mapping.
     *
     * @param mapProject the map project
     * @param branch the branch
     * @param mapSetCode the map set code
     * @param mappings the mappings
     * @return the mapping
     * @throws Exception the exception
     */
    public static List<Mapping> createMappings(final MapProject mapProject, final String branch, final String mapSetCode, final List<Mapping> mappings)
        throws Exception {

        return getTerminologyHandler().createMappings(mapProject, branch, mapSetCode, mappings);
    }

    /**
     * Update mapping.
     *
     * @param mapProject the map project
     * @param branch the branch
     * @param mapSetCode the map set code
     * @param mapping the mapping
     * @param mapSet the map set
     * @param user the acting user
     * @return the list
     * @throws Exception the exception
     */
    public static List<Mapping> updateMappings(final MapProject mapProject, final String branch, final String mapSetCode, final List<Mapping> mapping,
        final MapSet mapSet, final User user) throws Exception {

        final Set<String> changedConceptCodes = new LinkedHashSet<>();
        MappingContentChanges.begin(changedConceptCodes);
        final List<Mapping> updated;
        try {
            updated = getTerminologyHandler().updateMappings(mapProject, branch, mapSetCode, mapping, mapSet, user);
        } finally {
            MappingContentChanges.end();
        }
        recordContentEdits(mapSet, user, changedConceptCodes);
        return updated;
    }

    /**
     * Stamp workflow rows for concepts whose mapping content was written, so they appear on the dashboard.
     *
     * @param mapSet the map set
     * @param user the acting user
     * @param changedConceptCodes source concept codes that were written
     * @throws Exception the exception
     */
    private static void recordContentEdits(final MapSet mapSet, final User user, final Set<String> changedConceptCodes) throws Exception {

        if (changedConceptCodes == null || changedConceptCodes.isEmpty()) {
            return;
        }
        try (final TerminologyService service = new TerminologyService()) {
            MappingWorkflowService.recordContentEdits(service, user, mapSet, changedConceptCodes);
        }
    }

    /**
     * Export mappings.
     *
     * @param branch the branch
     * @param mapSetCode the map set code
     * @param mappingExportRequest the mapping export request
     * @param mapSet the map set
     * @return the file
     * @throws Exception the exception
     */
    public static File exportMappings(final String branch, final String mapSetCode, final MappingExportRequest mappingExportRequest, final MapSet mapSet)
        throws Exception {

        if (mapSet == null) {
            throw new IllegalArgumentException("MapSet from database is required for exportMappings. No fallback.");
        }
        return getTerminologyHandler().exportMappings(branch, mapSetCode, mappingExportRequest, mapSet);
    }

    /**
     * Import mappings.
     *
     * @param mapProject the map project
     * @param branch the branch
     * @param mappingFile the RF2 file
     * @param mapSet the map set
     * @param user the acting user
     * @return the imported mappings
     * @throws Exception the exception
     */
    public static List<Mapping> importMappings(final MapProject mapProject, final String branch, final MultipartFile mappingFile, final MapSet mapSet,
        final User user) throws Exception {

        return getTerminologyHandler().importMappings(mapProject, branch, mappingFile, mapSet, user);
    }

}
