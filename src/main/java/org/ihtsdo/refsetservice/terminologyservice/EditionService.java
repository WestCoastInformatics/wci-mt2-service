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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.ws.rs.core.Response;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.sync.SyncCodeSystemAgent.ReasonEditionSkipped;
import org.ihtsdo.refsetservice.sync.util.SyncDatabaseHandler;
import org.ihtsdo.refsetservice.sync.util.SyncUtilities;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The Class EditionService.
 */
public class EditionService extends BaseService {

     /** The Constant LOG. */
     private static final Logger LOG = LoggerFactory.getLogger(EditionService.class);

    /**
     * Creates the edition.
     *
     * @param user the user
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
     * Returns the edition for organization.
     *
     * @param organizationId the organization id
     * @return the edition for organization
     * @throws Exception the exception
     */
    public static Edition getEditionForOrganization(final String organizationId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final Edition edition = service.findSingle("organizationId:" + organizationId, Edition.class, null);

            return edition;
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
                query = IndexUtility.addWildcardsToQuery(query, Refset.class);
            }

            final ResultList<Edition> results = service.find(query, pfs, Edition.class, null);
            results.setTimeTaken(System.currentTimeMillis() - start);
            results.setTotalKnown(true);

            return results;
        }
    }
    
    /**
     * Populate new editions based on affiliated code systems from the terminology server.
     *
     * @return a list of new editions based on affiliated code systems
     * @throws Exception the exception
     */
    public static List<Edition> getAffiliateEditionList() throws Exception {

        final List<Edition> editionList = new ArrayList<>();
        final String url = SnowstormConnection.getBaseUrl() + "codesystems";
        LOG.info("getSnowstormCodeSystems url: " + url);

        try (final Response response = SnowstormConnection.getResponse(url)) {

            final String resultString = response.readEntity(String.class);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode organizationJsonRootNode = mapper.readTree(resultString.toString());
            final Iterator<JsonNode> organizationIterator = organizationJsonRootNode.iterator();
            final SyncUtilities syncUtilities = new SyncUtilities(new SyncDatabaseHandler(null));
            
            while (organizationIterator.hasNext()) {

                final Iterator<JsonNode> codeSystems = organizationIterator.next().iterator();

                while (codeSystems.hasNext()) {

                    final JsonNode codeSystem = codeSystems.next();

                    // Check for invalid or ignored code systems
                    if (!codeSystem.has("shortName")) {
                        continue;
                    }

                    final String editionShortName = codeSystem.get("shortName").asText();
                    final String maintainerType = syncUtilities.determineMaintainerType(codeSystem, editionShortName);

                    // Skip inactive code systems
                    if (codeSystem.has("active") && !codeSystem.get("active").asBoolean()) {
                        continue;
                    } 
                    
                    // Code System has been defined as to-be-ignored (either by specifying name or shortname).
                    else if (syncUtilities.getPropertyReader().getCodeSystemsToIgnore().contains(editionShortName)) {
                        continue;
                    } 
                    
                    // deal only with Type-3 (non-Managed Service)
//                    else if (!maintainerType.equalsIgnoreCase("Managed Service")) {
//                        continue;
//                    } 
                    
                    // deal only with official affiliate code systems
                    else if (!editionShortName.toLowerCase().contains("-affiliate")) {
                        continue;
                    } 
                        
                    final String editionName = codeSystem.get("name").asText();
                    final String branch = codeSystem.get("branchPath").asText();
                    final String defaultLanguageCode = syncUtilities.identifyDefaultLanguageCode(codeSystem, editionName);
                    final Set<String> defaultLanguageRefsets = syncUtilities.identifyDefaultLanguageRefsets(codeSystem, editionShortName);
                    final Set<String> editionModules = syncUtilities.identifyModules(editionShortName, editionName, branch, codeSystem);
                    final Edition edition = new Edition();

                    edition.setShortName(editionShortName);
                    edition.setName(editionName);
                    edition.setBranch(branch);
                    edition.setDefaultLanguageRefsets(defaultLanguageRefsets);
                    edition.setModules(editionModules);
                    edition.setDefaultLanguageCode(defaultLanguageCode);
                    edition.setMaintainerType(maintainerType);
                    edition.setActive(true);

                    editionList.add(edition);
                }
            }
        }

        return editionList;
    }
}
