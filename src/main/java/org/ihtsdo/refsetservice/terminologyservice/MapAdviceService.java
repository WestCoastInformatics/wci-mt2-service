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
import org.ihtsdo.refsetservice.model.MapAdvice;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: Auto-generated Javadoc
/**
 * Service for search and retrieval of mapAdvice information.
 */
public class MapAdviceService extends BaseService {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(MapAdviceService.class);

    /**
     * Creates the mapAdvice.
     *
     * @param service the service
     * @param user the user
     * @param mapAdvice the mapAdvice
     * @return the mapAdvice
     * @throws Exception the exception
     */
    public static MapAdvice createMapAdvice(final TerminologyService service, final User user, final MapAdvice mapAdvice) throws Exception {

        final MapAdvice newMapAdvice = new MapAdvice();
        newMapAdvice.populateFrom(mapAdvice);

        service.add(newMapAdvice);
        service.add(AuditEntryHelper.addMapAdviceEntry(newMapAdvice));

        return newMapAdvice;
    }

    /**
     * Returns the mapAdvice.
     *
     * @param service the service
     * @param mapAdviceId the mapAdvice id
     * @return the mapAdvice
     * @throws Exception the exception
     */
    public static MapAdvice getMapAdvice(final TerminologyService service, final String mapAdviceId) throws Exception {

        final MapAdvice mapAdvice = service.get(mapAdviceId, MapAdvice.class);
        return mapAdvice;
    }

    /**
     * Returns the mapAdvices.
     *
     * @param service the service
     * @param searchParameters the search parameters
     * @return the mapAdvices
     * @throws Exception the exception
     */
    public static ResultList<MapAdvice> getMapAdvices(final TerminologyService service, final SearchParameters searchParameters) throws Exception {

        final ResultList<MapAdvice> results = searchMapAdvices(service, searchParameters);

        return results;
    }

    /**
     * Update map advice.
     *
     * @param service the service
     * @param user the user
     * @param mapAdvice the map advice
     * @return the map advice
     * @throws Exception the exception
     */
    public static MapAdvice updateMapAdvice(final TerminologyService service, final User user, final MapAdvice mapAdvice) throws Exception {

        final MapAdvice updatedMapAdvice = service.update(mapAdvice);
        service.add(AuditEntryHelper.updateMapAdviceEntry(updatedMapAdvice));

        return updatedMapAdvice;

    }

    /**
     * Returns the mapAdvices.
     *
     * @param service the service
     * @return the mapAdvices
     * @throws Exception the exception
     */
    public static ResultList<MapAdvice> getMapAdvices(final TerminologyService service) throws Exception {

        final ResultList<MapAdvice> results = searchMapAdvices(service, new SearchParameters());

        return results;
    }

    /**
     * Search MapAdvices.
     *
     * @param service the service
     * @param searchParameters the search parameters
     * @return the list of projects
     * @throws Exception the exception
     */
    public static ResultList<MapAdvice> searchMapAdvices(final TerminologyService service, final SearchParameters searchParameters) throws Exception {

        LOG.info("Searching for MapAdvice with parameters: [{}]", searchParameters);

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
            query = IndexUtility.addWildcardsToQuery(query, MapAdvice.class);
        }

        LOG.info("Searching for MapAdvices with query: [{}]", query);

        final ResultList<MapAdvice> results = service.find(query, pfs, MapAdvice.class, null);
        results.setTimeTaken(System.currentTimeMillis() - start);
        results.setTotalKnown(true);

        return results;
    }

}
