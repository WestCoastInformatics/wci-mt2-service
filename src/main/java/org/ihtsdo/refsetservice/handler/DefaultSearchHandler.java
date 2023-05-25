/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.handler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;

import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.hibernate.search.engine.search.query.SearchResult;
import org.ihtsdo.refsetservice.model.HasId;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.LocalException;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.StringUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation a search handler. This provides an algorithm to aide in lucene searches.
 */
// @Component
public class DefaultSearchHandler implements SearchHandler {

    /** The Constant LOG. */
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(DefaultSearchHandler.class);

    /** The score map. */
    private Map<String, Float> scoreMap = new HashMap<>();

    /** The handler properties. */
    private Properties handlerProperties = new Properties();

    /**
     * Instantiates a handler.
     *
     */
    public DefaultSearchHandler() {

    }

    /**
     * Returns the query results.
     *
     * @param <T> the
     * @param query the query
     * @param fieldedClauses the fielded clauses
     * @param additionalClauses the additional clauses
     * @param clazz the class to search on
     * @param pfs the pfs
     * @param totalCt a container for the total number of results (for making a List class)
     * @param manager the entity manager
     * @return the query results
     * @throws Exception the exception
     */
    @SuppressWarnings("unused")
    @Override
    public <T extends HasId> List<T> getQueryResults(final String query, final Map<String, String> fieldedClauses, final Set<String> additionalClauses,
        final Class<T> clazz, final PfsParameter pfs, final int[] totalCt, final EntityManager manager) throws Exception {

        final SearchResult<T> searchResult = helper(query, fieldedClauses, additionalClauses, clazz, pfs, manager, Arrays.asList("score", "entity"));

        totalCt[0] = Math.toIntExact(searchResult.total().hitCount());

        final List<T> classes = new ArrayList<>();
        final List<T> results = searchResult.hits();

        for (final T result : results) {
            final Object score = result;
            final T t = result;

            // skip any bad entries from the index.
            if (t == null) {
                continue;
            }

            classes.add(t);

            // normalize results to a "good match" (lucene score of 5.0+)
            // Double normScore = Math.log(Math.max(5, scoreMap.get(sr.getId()))
            // /
            // Math.log(5));

            // cap the score to a maximum of 5.0 and normalize to the range
            // [0,1]
            /*
             * TODO: Resolve this section final Double normScore = Math.min(5, Double.valueOf(score.toString())) / 5; t.setConfidence(normScore);
             * 
             * // store the score scoreMap.put(t.getId(), normScore.floatValue());
             */
        }

        return classes;
    }

    /**
     * Count query results.
     *
     * @param <T> the
     * @param query the query
     * @param fieldedClauses the fielded clauses
     * @param additionalClauses the additional clauses
     * @param clazz the clazz
     * @param pfs the pfs
     * @param manager the manager
     * @return the list
     * @throws Exception the exception
     */
    @Override
    public <T extends HasId> int countQueryResults(final String query, final Map<String, String> fieldedClauses, final Set<String> additionalClauses,
        final Class<T> clazz, final PfsParameter pfs, final EntityManager manager) throws Exception {

        final SearchResult<T> searchResult = helper(query, fieldedClauses, additionalClauses, clazz, pfs, manager, new ArrayList<String>());

        return Math.toIntExact(searchResult.total().hitCount());

    }

    /**
     * Returns the ids for the query results.
     *
     * @param <T> the
     * @param query the query
     * @param fieldedClauses the fielded clauses
     * @param additionalClauses the additional clauses
     * @param clazz the clazz
     * @param pfs the pfs
     * @param totalCt the total ct
     * @param manager the manager
     * @return the id results
     * @throws Exception the exception
     */
    @Override
    public <T> List<String> getIdResults(final String query, final Map<String, String> fieldedClauses, final Set<String> additionalClauses,
        final Class<T> clazz, final PfsParameter pfs, final int[] totalCt, final EntityManager manager) throws Exception {

        final SearchResult<T> searchResult = helper(query, fieldedClauses, additionalClauses, clazz, pfs, manager, Arrays.asList("id"));

        totalCt[0] = Math.toIntExact(searchResult.total().hitCount());

        final List<String> ids = new ArrayList<>();
        final List<T> results = searchResult.hits();

        for (final T result : results) {
            final String id = ((HasId) result).getId();
            ids.add(id);
        }

        return ids;
    }

    /**
     * Helper.
     *
     * @param <T> the
     * @param query the query
     * @param fieldedClauses the fielded clauses
     * @param additionalClauses the additional clauses
     * @param clazz the clazz
     * @param pfs the pfs
     * @param manager the manager
     * @param projections the names of projections to use
     * @return the full text query
     * @throws Exception the exception
     */
    public <T> SearchResult<T> helper(final String query, final Map<String, String> fieldedClauses, final Set<String> additionalClauses, final Class<T> clazz,
        final PfsParameter pfs, final EntityManager manager, final List<String> projections) throws Exception {
        // Default Search Handler algorithm: run the query "as-is"
        // with fielded or additional clauses

        String escapedQuery = query;
        if (query != null && query.startsWith("\"") && query.endsWith("\"")) {
            escapedQuery = escapedQuery.substring(1);
            escapedQuery = escapedQuery.substring(0, query.length() - 2);
        } else {
            escapedQuery = query == null ? "" : escapedQuery;
        }
        escapedQuery = "\"" + QueryParserBase.escape(escapedQuery) + "\"";

        // 1. fielded clauses
        final String part1 = fieldedClauses == null ? null : StringUtility.composeQuery("AND",
            fieldedClauses.entrySet().stream().map(e -> e.getKey() + ":" + QueryParserBase.escape(e.getValue())).collect(Collectors.toList()));
        // LOG.debug(" part1 = " + part1);
        // 2. additional clauses
        final String part2 = additionalClauses == null ? null : StringUtility.composeQuery("AND", new ArrayList<>(additionalClauses));
        // LOG.debug(" part2 = " + part2);

        // 3. (query OR escapedQuery^10.0)
        String part3 = null;
        if (StringUtility.isEmpty(query)) {
            part3 = null;
        } else {
            part3 = query;
        }
        // LOG.debug(" part3 = " + part3);

        // Assemble query - text, then fields, then additional
        final String finalQuery = StringUtility.composeQuery("AND", part3, part1, part2);

        SearchResult<T> searchResult = null;
        try {
            searchResult = IndexUtility.applyPfsToLuceneQuery(clazz, finalQuery.toString(), pfs, manager, projections);
        } catch (ParseException | IllegalArgumentException | LocalException e) {
            // If a "local parse exception", just try again
            if (!(e instanceof LocalException) || !(e.getCause() instanceof ParseException)) {
                e.printStackTrace();
            }
            // If there's a parse exception, try the literal query
            searchResult = IndexUtility.applyPfsToLuceneQuery(clazz, escapedQuery, pfs, manager, projections);
        }

        return searchResult;

    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    @Override
    public String getName() {

        return ModelUtility.getNameFromClass(DefaultSearchHandler.class);
    }

    /**
     * Returns the score map for the most recent call to getQueryResults. NOTE: this is NOT thread safe.
     *
     * @return the score map
     */
    @Override
    public Map<String, Float> getScoreMap() {

        return scoreMap;
    }

    /**
     * New instance.
     *
     * @return the t
     */
    public DefaultSearchHandler newInstance() {

        return new DefaultSearchHandler();
    }

    /* see superclass */
    @Override
    public void setProperties(final Properties properties) throws Exception {

        handlerProperties.putAll(properties);
    }
}
