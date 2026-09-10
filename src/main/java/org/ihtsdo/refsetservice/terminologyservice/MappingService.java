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
import java.util.List;

import org.ihtsdo.refsetservice.handler.TerminologyServerHandler;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.model.MappingExportRequest;
import org.ihtsdo.refsetservice.model.ResultListMapping;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.util.HandlerUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

/**
 * Service class to get refset member concept information from a terminology
 * service.
 */
/**
 * @author jesseefron
 *
 */
public final class MappingService {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(MappingService.class);

    /** The terminology handler. */
    private static TerminologyServerHandler terminologyHandler;

    static {

        // Instantiate terminology handler
        try {
            String key = "terminology.handler";
            String handlerName = PropertyUtility.getProperty(key);
            if (handlerName.isEmpty()) {
                throw new Exception("terminology.handler expected and does not exist.");
            }

            terminologyHandler = HandlerUtility.newStandardHandlerInstanceWithConfiguration(key, handlerName, TerminologyServerHandler.class);

        } catch (Exception e) {
            LOG.error("Failed to initialize terminology.handler - serious error", e);
            terminologyHandler = null;
        }
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
        return terminologyHandler.getMappings(branch, mapSet, searchParameters, filter, showOverriddenEntries, conceptCodes, restrictToConceptCodes,
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
    public static Mapping getMapping(final String branch, final String conceptCode, final boolean showOverriddenEntries,
        final MapSet mapSet) throws Exception {

        return terminologyHandler.getMapping(branch, conceptCode, showOverriddenEntries, mapSet);
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

        return terminologyHandler.createMappings(mapProject, branch, mapSetCode, mappings);
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

        return terminologyHandler.updateMappings(mapProject, branch, mapSetCode, mapping, mapSet, user);
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
        return terminologyHandler.exportMappings(branch, mapSetCode, mappingExportRequest, mapSet);
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
	public static List<Mapping> importMappings(MapProject mapProject, String branch, MultipartFile mappingFile, MapSet mapSet, User user) throws Exception {

		return terminologyHandler.importMappings(mapProject, branch, mappingFile, mapSet, user);
	}
    
}
