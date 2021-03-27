
package org.ihtsdo.refsetservice.handler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;

import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.hibernate.search.engine.ProjectionConstants;
import org.hibernate.search.jpa.FullTextQuery;
import org.ihtsdo.refsetservice.model.HasId;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.LocalException;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.StringUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default implementation a search handler. This provides an algorithm to aide
 * in lucene searches.
 */
// @Component
public class DefaultSearchHandler implements SearchHandler {

    /** The logger. */
    @SuppressWarnings("unused")
    private static Logger logger = LoggerFactory.getLogger(DefaultSearchHandler.class);

    /** The score map. */
    private Map<String, Float> scoreMap = new HashMap<>();

    /** The handler properties. */
    private Properties handlerProperties = new Properties();

    /**
     * Instantiates a handler
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
     * @param totalCt a container for the total number of results (for making a
     *            List class)
     * @param manager the entity manager
     * @return the query results
     * @throws Exception the exception
     */
    @SuppressWarnings("unused")
    @Override
    public <T extends HasId> List<T> getQueryResults(final String query,
        final Map<String, String> fieldedClauses, final Set<String> additionalClauses,
        final Class<T> clazz, final PfsParameter pfs, final int[] totalCt,
        final EntityManager manager) throws Exception {

        final FullTextQuery fullTextQuery =
                helper(query, fieldedClauses, additionalClauses, clazz, pfs, manager);
        // Perform the final query and save score values
        fullTextQuery.setProjection(ProjectionConstants.SCORE, ProjectionConstants.THIS);
        totalCt[0] = fullTextQuery.getResultSize();

        final List<T> classes = new ArrayList<>();

        @SuppressWarnings("unchecked")
        final List<Object[]> results = fullTextQuery.getResultList();
        for (final Object[] result : results) {
            final Object score = result[0];
            @SuppressWarnings("unchecked")
            final T t = (T) result[1];

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
             * TODO: Resolve this section final Double normScore = Math.min(5,
             * Double.valueOf(score.toString())) / 5;
             * t.setConfidence(normScore);
             * 
             * // store the score scoreMap.put(t.getId(),
             * normScore.floatValue());
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
    public <T extends HasId> int countQueryResults(final String query,
        final Map<String, String> fieldedClauses, final Set<String> additionalClauses,
        final Class<T> clazz, final PfsParameter pfs, final EntityManager manager)
        throws Exception {

        final FullTextQuery fullTextQuery =
                helper(query, fieldedClauses, additionalClauses, clazz, pfs, manager);
        return fullTextQuery.getResultSize();

    }

    /**
     * Returns the ids for the query results.
     *
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
    public List<String> getIdResults(final String query, final Map<String, String> fieldedClauses,
        final Set<String> additionalClauses, final Class<?> clazz, final PfsParameter pfs,
        final int[] totalCt, final EntityManager manager) throws Exception {

        final FullTextQuery fullTextQuery =
                helper(query, fieldedClauses, additionalClauses, clazz, pfs, manager);
        totalCt[0] = fullTextQuery.getResultSize();

        // Perform the final query and save score values
        fullTextQuery.setProjection(ProjectionConstants.ID);
        final List<String> ids = new ArrayList<>();
        @SuppressWarnings("unchecked")
        final List<Object[]> results = fullTextQuery.getResultList();
        for (final Object[] result : results) {
            final String id = (String) result[0];
            ids.add(id);
        }

        return ids;
    }

    /**
     * Helper.
     *
     * @param query the query
     * @param fieldedClauses the fielded clauses
     * @param additionalClauses the additional clauses
     * @param clazz the clazz
     * @param pfs the pfs
     * @param manager the manager
     * @return the full text query
     * @throws Exception the exception
     */
    @SuppressWarnings("null")
    public FullTextQuery helper(final String query, final Map<String, String> fieldedClauses,
        final Set<String> additionalClauses, final Class<?> clazz, final PfsParameter pfs,
        final EntityManager manager) throws Exception {
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
        final String part1 =
                fieldedClauses == null ? null
                        : StringUtility.composeQuery("AND", fieldedClauses.entrySet().stream()
                                .map(e -> e.getKey() + ":" + QueryParserBase.escape(e.getValue()))
                                .collect(Collectors.toList()));
        // logger.debug(" part1 = " + part1);
        // 2. additional clauses
        final String part2 = additionalClauses == null ? null
                : StringUtility.composeQuery("AND", new ArrayList<>(additionalClauses));
        // logger.debug(" part2 = " + part2);

        // 3. (query OR escapedQuery^10.0)
        String part3 = null;
        if (StringUtility.isEmpty(query)) {
            part3 = null;
        } else {
            part3 = query;
        }
        // logger.debug(" part3 = " + part3);

        // Assemble query - text, then fields, then additional
        final String finalQuery = StringUtility.composeQuery("AND", part3, part1, part2);

        FullTextQuery fullTextQuery = null;
        try {
            fullTextQuery =
                    IndexUtility.applyPfsToLuceneQuery(clazz, finalQuery.toString(), pfs, manager);
        } catch (ParseException | IllegalArgumentException | LocalException e) {
            // If a "local parse exception", just try again
            if (!(e instanceof LocalException) || !(e.getCause() instanceof ParseException)) {
                e.printStackTrace();
            }
            // If there's a parse exception, try the literal query
            fullTextQuery = IndexUtility.applyPfsToLuceneQuery(clazz, escapedQuery, pfs, manager);
        }

        return fullTextQuery;

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
     * Returns the score map for the most recent call to getQueryResults. NOTE:
     * this is NOT thread safe.
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

    @Override
    public void setProperties(Properties properties) throws Exception {
        handlerProperties.putAll(properties);
    }
}
