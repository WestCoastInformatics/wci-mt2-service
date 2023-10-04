/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.util.List;

import org.ihtsdo.refsetservice.handler.TerminologyServerHandler;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.ihtsdo.refsetservice.util.HandlerUtility;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for search and retrieval of edition information.
 */
public class EditionService extends BaseService {

	/** The Constant LOG. */
	private static final Logger LOG = LoggerFactory.getLogger(EditionService.class);

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

			terminologyHandler = HandlerUtility.newStandardHandlerInstanceWithConfiguration(key, handlerName,
					TerminologyServerHandler.class);

		} catch (Exception e) {
			LOG.error("Failed to initialize terminology.handler - serious error", e);
			terminologyHandler = null;
		}
	}

	/**
	 * Creates the edition.
	 *
	 * @param user    the user
	 * @param edition the edition
	 * @return the edition
	 * @throws Exception the exception
	 */
	public static Edition createEdition(final User user, final Edition edition) throws Exception {

		try (final TerminologyService service = new TerminologyService()) {

			final Edition newEdition = new Edition();
			newEdition.populateFrom(edition);

			service.setModifiedBy(user.getUserName());
			service.setTransactionPerOperation(false);
			service.beginTransaction();

			service.add(newEdition);
			service.add(AuditEntryHelper.addEditionEntry(newEdition));
			service.commit();

			return newEdition;
		}
	}

	/**
	 * Returns the edition.
	 *
	 * @param editionId the edition id
	 * @return the edition
	 * @throws Exception the exception
	 */
	public static Edition getEdition(final String editionId) throws Exception {

		try (final TerminologyService service = new TerminologyService()) {
			final Edition edition = service.get(editionId, Edition.class);
			return edition;
		}
	}

	/**
	 * Returns the editions.
	 *
	 * @return the editions
	 * @throws Exception the exception
	 */
	public static ResultList<Edition> getEditions() throws Exception {

		try (final TerminologyService service = new TerminologyService()) {

			final ResultList<Edition> results = searchEditions(new SearchParameters());

			return results;
		}
	}

	/**
	 * Search Editions.
	 *
	 * @param searchParameters the search parameters
	 * @return the list of projects
	 * @throws Exception the exception
	 */
	public static ResultList<Edition> searchEditions(final SearchParameters searchParameters) throws Exception {

		try (final TerminologyService service = new TerminologyService()) {

			final long start = System.currentTimeMillis();
			String query = getQueryForActiveOnly(searchParameters);
			final PfsParameter pfs = new PfsParameter();

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
				query = IndexUtility.addWildcardsToQuery(query, Edition.class);
			}

			final ResultList<Edition> results = service.find(query, pfs, Edition.class, null);
			results.setTimeTaken(System.currentTimeMillis() - start);
			results.setTotalKnown(true);

			return results;
		}
	}

	/**
	 * Populate new editions based on affiliated code systems from the terminology
	 * server.
	 *
	 * @return a list of new editions based on affiliated code systems
	 * @throws Exception the exception
	 */
	public static List<Edition> getAffiliateEditionList() throws Exception {
		return terminologyHandler.getAffiliateEditionList();
	}
}
