/*
 * Copyright 2022 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import org.ihtsdo.refsetservice.model.Artifact;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service class to handle creating and getting artifact entries.
 */
public class ArtifactService {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(ArtifactService.class);

    /**
     * Instantiates an empty {@link ArtifactService}.
     */
    public ArtifactService() {

    }

    /**
     * Returns the artifact.
     *
     * @param id the id
     * @return the artifact
     * @throws Exception the exception
     */
    public static Artifact getAudit(final String id) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            return service.get(id, Artifact.class);

        } catch (final Exception e) {
            logger.error("Error searching artifact entries. Id: {}", id, e);
            throw e;
        }

    }

    /**
     * Search artifact entries.
     *
     * @param searchParameters the search parameters
     * @return the result list ResultList of artifact entries
     * @throws Exception the exception
     */
    public static ResultList<Artifact> searchArtifact(final SearchParameters searchParameters) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final PfsParameter pfs = new PfsParameter();
            pfs.setAscending(false);

            final ResultList<Artifact> results = service.find(searchParameters.getQuery(), pfs, Artifact.class, null);

            return results;

        } catch (final Exception e) {
            logger.error("Error searching artifact entries. Search Parameters: {}", searchParameters.toString(), e);
            throw e;
        }

    }

    /**
     * Adds the artifact.
     *
     * @param user the user
     * @param artifact the artifact
     * @return the artifact
     * @throws Exception the exception
     */
    public static Artifact addArtifact(final User user, final Artifact artifact) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getId());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            service.add(artifact);
            service.commit();

            return artifact;

        } catch (final Exception e) {
            logger.error("Error adding artifact.  Artifact: {}", artifact.toString(), e);
            throw e;
        }
    }

}
