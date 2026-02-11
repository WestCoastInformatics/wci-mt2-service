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

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.handler.TerminologyServerHandler;
import org.ihtsdo.refsetservice.handler.snowstorm.SnomedConstants;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.QueryParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.snowstorm.CodeSystem;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.ihtsdo.refsetservice.util.HandlerUtility;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.ihtsdo.refsetservice.util.StringUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service for search and retrieval of edition information.
 */
public class EditionService extends BaseService {

	/** The Constant LOG. */
	private static final Logger LOG = LoggerFactory.getLogger(EditionService.class);

    /** The Constant SNOMEDCT_CORE_MODULE. */
    private static final String SNOMEDCT_CORE_MODULE = "900000000000207008";

    /** The Constant MODULE_DEPENDENCY_REFSET_SCT_ID. */
    private static final String MODULE_DEPENDENCY_REFSET_SCT_ID = "900000000000534007";
	
    /** The Constant dependencyModuleNameCache. */
    // Module Id to map of releaseDate to Dependent Module Name
    private static final Map<String, Map<Long, String>> DEPENDENCY_MODULE_CACHE = new HashMap<>();
    
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
     * @param service the service
	 * @param editionId the edition id
	 * @return the edition
	 * @throws Exception the exception
	 */
    public static Edition getEdition(final TerminologyService service, final String editionId) throws Exception {

			final Edition edition = service.get(editionId, Edition.class);
			return edition;
		}


	/**
	 * Returns the editions.
	 *
	 * @return the editions
	 * @throws Exception the exception
	 */
	public static ResultList<Edition> getEditions(final TerminologyService service) throws Exception {

			final ResultList<Edition> results = searchEditions(service, new SearchParameters());
			return results;
		}

	/**
	 * Search Editions.
	 *
	 * @param searchParameters the search parameters
	 * @return the list of projects
	 * @throws Exception the exception
	 */
    public static ResultList<Edition> searchEditions(final TerminologyService service, final SearchParameters searchParameters) throws Exception {

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
	
    /**
     * Returns the edition dependent version.
     *
     * @param shortName the short name
     * @param user the user
     * @return the edition dependent version
     * @throws Exception the exception
     */
	// TODO: Move to SnowstormEdtion since this does a REST API call to Snowstorm
    public static String getEditionDependentVersion(final String shortName, final User user) throws Exception {

        String dependentVersion = "";

        final String url = SnowstormConnection.getBaseUrl() + "codesystems/" + shortName;
        LOG.info("getEditionDependentVersion url: " + url);

        try (final Response response = SnowstormConnection.getResponse(url)) {

            final String resultString = response.readEntity(String.class);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode codeSystemJsonRootNode = mapper.readTree(resultString);

            if (codeSystemJsonRootNode.has("dependantVersionEffectiveTime")) {
                dependentVersion = codeSystemJsonRootNode.get("dependantVersionEffectiveTime").asText();
            }

        } catch (final Exception e) {
            LOG.error("Error getting dependent version for edition: " + shortName, e);
        }

        return dependentVersion;
    }
    
    /**
     * Returns the edition dependent version.
     *
     * @param shortName the short name
     * @param user the user
     * @return the edition dependent version
     * @throws Exception the exception
     */
 // TODO: Move to SnowstormEdtion since this does a REST API call to Snowstorm
    public static CodeSystem getCodeSystem(final String shortName) throws Exception {

        CodeSystem codeSystem = null;
        final String url = SnowstormConnection.getBaseUrl() + "codesystems/" + shortName;
        LOG.info("getCodeSystem url: {}", url);

        try (final Response response = SnowstormConnection.getResponse(url)) {

            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                throw new Exception("Unable to get edition code system. Status: " + Integer.toString(response.getStatus()) + ". Error: "
                    + formatErrorMessage(response));
            }
            
            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode codeSystemJsonRootNode = mapper.readTree(resultString);

            // Map the JSON node to a CodeSystem object
            codeSystem = mapper.treeToValue(codeSystemJsonRootNode, CodeSystem.class);

        } catch (final Exception e) {
            LOG.error("Error getting code system for edition: " + shortName, e);
            throw e;
        }

        return codeSystem;
    }

    /**
     * Delete edition.
     *
     * @param service the service
     * @param edition the edition
     * @param user the user
     * @return the edition
     * @throws Exception the exception
     */
    public static Edition deleteEdition(final TerminologyService service, final Edition edition, final User user) throws Exception {
        // TODO: Remove affiliates based on the edition as well?

        try {

            // Identify the edition's projects, delete each project's refsets, then delete the project itself.
            final ResultList<Project> orgProjects = EditionService.getEditionProjects(service, edition.getId());
            for (final Project project : orgProjects.getItems()) {

                // Find project
                final ResultList<Refset> projectRefsets = service.find("projectId:" + project.getId(), null, Refset.class, null);

                for (final Refset refset : projectRefsets.getItems()) {
                    // Delete refset
                    RefsetService.deleteRefset(service, user, refset);
                }

                // Delete project
                service.remove(project);
            }

            // Delete edition
            service.remove(edition);

            return edition;

        } catch (final NotFoundException nfe) {
            throw new NotFoundException("Error getting edition to deleted. Associated Edition shortName " + edition.getShortName() + " not found.");
        }
    }

    /**
     * Returns the organization projects.
     *
     * @param service the Terminology Service
     * @param editionId the edition id
     * @return the organization projects
     * @throws Exception the exception
     */
    public static ResultList<Project> getEditionProjects(final TerminologyService service, final String editionId) throws Exception {

        final PfsParameter pfs = new PfsParameter();
        final QueryParameter query = new QueryParameter();
        query.setQuery("editionId:" + editionId);

        return service.find(query, pfs, Project.class, null);
    }

    /**
     * Update the Edition and its components with the status provided.
     *
     * @param service the service
     * @param user the user
     * @param edition the edition
     * @param newStatus the new status
     * @throws Exception the exception
     */
    public static void updateEditionStatus(final TerminologyService service, final User user, Edition edition, boolean newStatus) throws Exception {

        // TODO: Unit test edition & org status changes
        OrganizationService.checkEditPermissions(user, edition.getOrganization());

        if (!newStatus && edition.getOrganization().isActive()) {
            // TODO: Do we inactivate org if last edition that is active is about to be inactivated? I think complicates matters, so leaving TODO and place
            // holder
        }

        // Update the edition and it's components only
        final ResultList<Project> editionProjects = service.find("editionId:" + edition.getId(), null, Project.class, null);

        if (editionProjects.getItems() != null && !editionProjects.getItems().isEmpty()) {

            for (final Project project : editionProjects.getItems()) {

                project.setActive(newStatus);

                if (project.getTeams() != null) {

                    for (final String teamId : project.getTeams()) {

                        final Team team = service.get(teamId, Team.class);

                        if (team != null && !team.getMembers().isEmpty()) {

                            team.setActive(newStatus);
                            service.update(team);
                        }

                    }

                }

                service.update(project);

                final ResultList<Refset> projRefsets = service.find("projectId:" + project.getId() + " AND active:" + !newStatus, null, Refset.class, null);

                if (projRefsets.getItems() != null && !projRefsets.getItems().isEmpty()) {

                    for (final Refset refset : projRefsets.getItems()) {

                        if (refset != null && !projRefsets.getItems().isEmpty()) {

                            refset.setActive(newStatus);
                            service.update(refset);
                        }

                    }

                }

            }

        }

        edition.setActive(newStatus);

        service.update(edition);
    }

    /**
     * Returns the dependency module name.
     *
     * @param shortName the short name
     * @param moduleId the module id
     * @param version the version
     * @param branchPath the branch path
     * @return the dependency module name
     * @throws Exception the exception
     */
    public static String getDependencyModuleNameFromBranchMetadata(final String shortName, final String moduleId, final Long version, final String branchPath)
        throws Exception {

        if (!DEPENDENCY_MODULE_CACHE.containsKey(moduleId)) {
            DEPENDENCY_MODULE_CACHE.put(moduleId, new HashMap<>());
        }

        if (version == null || !DEPENDENCY_MODULE_CACHE.get(moduleId).containsKey(version)) {

            // Populate cache for release date
            // SnowstormConnection.checkConnection();

            // https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/branches/MAIN/SNOMEDCT-NL/NLREFSETS/metadata?includeInheritedMetadata=true
            String url = SnowstormConnection.getBaseUrl() + "branches/" + branchPath + "/metadata?includeInheritedMetadata=true";
            LOG.info("getRefsetDependencyModule url: " + url);

            try (final Response response = SnowstormConnection.getResponse(url)) {

                final String resultString = response.readEntity(String.class);

                final ObjectMapper mapper = new ObjectMapper();
                final JsonNode root = mapper.readTree(resultString);

                final String dependentModuleVersion = root.get("dependencyRelease").asText();
                final String targetVersionString =
                    SnomedConstants.BRANCH_DATE_FORMAT.format(SnomedConstants.BRANCH_DATE_FORMAT_ONLY_NUMBERS.parse(dependentModuleVersion));
                final String snomedCoreName = targetVersionString + " SNOMED CT core";

                DEPENDENCY_MODULE_CACHE.get(moduleId).put(version, snomedCoreName);
            }
        }

        return DEPENDENCY_MODULE_CACHE.get(moduleId).get(version);
    }

    /**
     * Returns the dependency module name from module dependency.
     *
     * @param refset the refset
     * @return the dependency module name from module dependency
     * @throws Exception the exception
     */
    public static String getDependencyModuleNameFromModuleDependency(final Refset refset) throws Exception {

        if (!DEPENDENCY_MODULE_CACHE.containsKey(refset.getModuleId())) {
            DEPENDENCY_MODULE_CACHE.put(refset.getModuleId(), new HashMap<>());
        }

        if (refset.getVersionDate() == null || !DEPENDENCY_MODULE_CACHE.get(refset.getModuleId()).containsKey(refset.getVersionDate())) {

            // Populate cache for release date
            // SnowstormConnection.checkConnection();

            // Get refset members of moduleDependcy refset for refset's moduleId
            // https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/MAIN/SNOMEDCT-US/2023-03-01/members?referenceSet=900000000000534007&module=731000124108&offset=0&limit=50
            String url = SnowstormConnection.getBaseUrl() + refset.getBranchPath() + "/members?referenceSet=" + MODULE_DEPENDENCY_REFSET_SCT_ID + "&module="
                + refset.getModuleId();
            LOG.info("getRefsetDependencyModule url: " + url);

            try (final Response response = SnowstormConnection.getResponse(url)) {

                final String resultString = response.readEntity(String.class);

                final ObjectMapper mapper = new ObjectMapper();
                final Iterator<JsonNode> moduleDependencyIterator = mapper.readTree(resultString).get("items").iterator();

                while (moduleDependencyIterator.hasNext()) {
                    final JsonNode dependency = moduleDependencyIterator.next();

                    if (dependency.has("referencedComponentId")) {
                        final String moduleConceptId = dependency.get("referencedComponentId").asText();

                        if (SNOMEDCT_CORE_MODULE.equals(moduleConceptId)) {
                            final String targetVersion = dependency.get("additionalFields").get("targetEffectiveTime").asText();
                            final String targetVersionString =
                                targetVersion.substring(0, 4) + "-" + targetVersion.substring(4, 6) + "-" + targetVersion.substring(6);
                            final String snomedCoreName = targetVersionString + " SNOMED CT core";

                            DEPENDENCY_MODULE_CACHE.get(refset.getModuleId()).put(refset.getVersionDate().getTime(), snomedCoreName);
                            break;
                        }

                    }
                }
            }
        }

        return DEPENDENCY_MODULE_CACHE.get(refset.getModuleId()).get(refset.getVersionDate());
    }

    /**
     * Clear dependency module caches.
     *
     * @param moduleId the module id
     */
    public static void clearModuleDependencyCaches(String moduleId) {

        DEPENDENCY_MODULE_CACHE.remove(moduleId);
    }

    /**
     * Clear dependency module caches.
     */
    public static void clearFullModuleDependencyCache() {

        DEPENDENCY_MODULE_CACHE.clear();
    }
    
    /**
     * Format error message.
     *
     * @param response the response
     * @return the string
     */
    private static String formatErrorMessage(final Response response) {

        String snowstormErrorMessage = response.readEntity(String.class);
        if (StringUtils.isEmpty(snowstormErrorMessage)) {
            return "";
        }
        if (StringUtility.isJson(snowstormErrorMessage)) {
            final ObjectMapper mapper = new ObjectMapper();
            try {
                final JsonNode json = mapper.readTree(snowstormErrorMessage);
                snowstormErrorMessage = json.has("message") ? json.get("message").asText() : "";
            } catch (final Exception e) {
                LOG.error("formatErrorMessage snowstormErrorMessage:{}", snowstormErrorMessage, e);
            }
        }
        return snowstormErrorMessage.replaceAll("[\\r\\n]+", " ");
    }
}
