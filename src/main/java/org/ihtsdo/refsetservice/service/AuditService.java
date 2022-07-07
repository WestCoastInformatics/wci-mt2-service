/*
 * Copyright 2022 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.service;

import org.ihtsdo.refsetservice.model.AuditEntry;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service class to handle creating and getting audit entries.
 */
public class AuditService {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(AuditService.class);

    /**
     * Instantiates an empty {@link AuditService}.
     */
    public AuditService() {
        
    }
    
    /**
     * Returns the audit.
     *
     * @param id the id
     * @return the audit
     * @throws Exception the exception
     */
    public static AuditEntry getAudit(final String id) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            return service.get(id, AuditEntry.class);

        } catch (final Exception e) {
            logger.error("Error searching audit entries. Id: {}", id, e);
            throw e;
        }

    }

    /**
     * Search audit entries.
     *
     * @param searchParameters the search parameters
     * @return the result list ResultList of audit entries
     * @throws Exception the exception
     */
    public static ResultList<AuditEntry> searchAuditEntry(final SearchParameters searchParameters) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final PfsParameter pfs = new PfsParameter();
            pfs.setAscending(false);

            final ResultList<AuditEntry> results = service.find(searchParameters.getQuery(), pfs, AuditEntry.class, null);

            return results;

        } catch (final Exception e) {
            logger.error("Error searching audit entries. Search Parameters: {}", searchParameters.toString(), e);
            throw e;
        }

    }

    /**
     * Adds the audit entry.
     *
     * @param user the user
     * @param auditEntry the audit entry
     * @throws Exception the exception
     */
    public static void addAuditEntry(final User user, final AuditEntry auditEntry) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getId());
            service.setTransactionPerOperation(false);
            service.beginTransaction();

            service.add(auditEntry);
            service.commit();

        } catch (final Exception e) {
            logger.error("Error adding audit entry.  AuditEntry: {}", auditEntry.toString(), e);
            throw e;
        }
    }

}
