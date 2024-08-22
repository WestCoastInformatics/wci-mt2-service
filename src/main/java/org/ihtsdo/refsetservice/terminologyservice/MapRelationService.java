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

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.MapRelation;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for search and retrieval of mapRelation information.
 */
public class MapRelationService extends BaseService {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(MapRelationService.class);

    /**
     * Creates the mapRelation.
     *
     * @param service the service
     * @param user the user
     * @param mapRelation the mapRelation
     * @return the mapRelation
     * @throws Exception the exception
     */
    public static MapRelation createMapRelation(final TerminologyService service, final User user, final MapRelation mapRelation) throws Exception {

        final MapRelation newMapRelation = new MapRelation();
        newMapRelation.populateFrom(mapRelation);

        service.add(newMapRelation);
        service.add(AuditEntryHelper.addMapRelationEntry(newMapRelation));

        return newMapRelation;
    }

    /**
     * Returns the mapRelation.
     *
     * @param service the service
     * @param mapRelationId the mapRelation id
     * @return the mapRelation
     * @throws Exception the exception
     */
    public static MapRelation getMapRelation(final TerminologyService service, final String mapRelationId) throws Exception {

        final MapRelation mapRelation = service.get(mapRelationId, MapRelation.class);
        return mapRelation;
    }

    /**
     * Returns the mapRelations.
     *
     * @param service the service
     * @return the mapRelations
     * @throws Exception the exception
     */
    public static ResultList<MapRelation> getMapRelations(final TerminologyService service) throws Exception {

        final ResultList<MapRelation> results = searchMapRelations(service, new SearchParameters());
        return results;
    }

    /**
     * Returns the mapRelations.
     *
     * @param service the service
     * @param searchParameters the search parameters
     * @return the mapRelations
     * @throws Exception the exception
     */
    public static ResultList<MapRelation> getMapRelations(final TerminologyService service, final SearchParameters searchParameters) throws Exception {

        final ResultList<MapRelation> results = searchMapRelations(service, searchParameters);
        return results;
    }

    /**
     * Update map relation.
     *
     * @param service the service
     * @param user the user
     * @param mapRelation the map relation
     * @return the map relation
     * @throws Exception the exception
     */
    public static MapRelation updateMapRelation(final TerminologyService service, final User user, final MapRelation mapRelation) throws Exception {

        final MapRelation updatedMapRelation = service.update(mapRelation);
        service.add(AuditEntryHelper.updateMapRelationEntry(updatedMapRelation));

        return updatedMapRelation;

    }

    /**
     * Search MapRelations.
     *
     * @param service the service
     * @param searchParameters the search parameters
     * @return the list of projects
     * @throws Exception the exception
     */
    public static ResultList<MapRelation> searchMapRelations(final TerminologyService service, final SearchParameters searchParameters) throws Exception {

        LOG.info("Searching for MapRelations with parameters: [{}]", searchParameters);

        final long start = System.currentTimeMillis();
        String query = (searchParameters != null && StringUtils.isNotBlank(searchParameters.getQuery())) ? searchParameters.getQuery() : "";

        final PfsParameter pfs = new PfsParameter();
        if (searchParameters.getActiveOnly() != null && searchParameters.getActiveOnly()) {
            query = (query.equals("")) ? "active:true" : query + " AND active:true";
        }

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

        if (query != null && !query.equals("")) {
            query = IndexUtility.addWildcardsToQuery(query, MapRelation.class);
        }

        LOG.info("Searching for MapRelations with query: [{}]", query);

        final ResultList<MapRelation> results = service.find(query, pfs, MapRelation.class, null);
        results.setTimeTaken(System.currentTimeMillis() - start);
        results.setTotalKnown(true);

        return results;
    }

}
