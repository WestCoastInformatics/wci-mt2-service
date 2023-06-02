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

import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.EntityManager;

import org.ihtsdo.refsetservice.model.Configurable;
import org.ihtsdo.refsetservice.model.HasId;
import org.ihtsdo.refsetservice.model.PfsParameter;

/**
 * Generically represents an algorithm for object searching.
 */
public interface SearchHandler extends Configurable {

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
    public <T extends HasId> List<T> getQueryResults(String query, Map<String, String> fieldedClauses, Set<String> additionalClauses, Class<T> clazz,
        PfsParameter pfs, int[] totalCt, EntityManager manager) throws Exception;

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
    public <T extends HasId> int countQueryResults(String query, Map<String, String> fieldedClauses, Set<String> additionalClauses, Class<T> clazz,
        PfsParameter pfs, EntityManager manager) throws Exception;

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
    public <T> List<String> getIdResults(String query, Map<String, String> fieldedClauses, Set<String> additionalClauses, Class<T> clazz, PfsParameter pfs,
        int[] totalCt, EntityManager manager) throws Exception;

    /**
     * Returns the score map for the most recent call to getQueryResults. NOTE: this is NOT thread safe.
     *
     * @return the score map
     */
    public Map<String, Float> getScoreMap();
}
