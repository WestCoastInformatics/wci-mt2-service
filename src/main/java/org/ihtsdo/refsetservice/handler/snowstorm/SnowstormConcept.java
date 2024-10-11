/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.handler.snowstorm;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.ResultListConcept;
import org.ihtsdo.refsetservice.model.SnowstormFhirCodeSystem;
import org.ihtsdo.refsetservice.model.UpgradeInactiveConcept;
import org.ihtsdo.refsetservice.model.UpgradeReplacementConcept;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.ihtsdo.refsetservice.terminologyservice.WorkflowService;
import org.ihtsdo.refsetservice.util.CachingUtility;
import org.ihtsdo.refsetservice.util.ConceptLookupParameters;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.ihtsdo.refsetservice.util.StringUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The Class SnowstormConcept.
 */
public class SnowstormConcept extends SnowstormAbstract {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SnowstormConcept.class);

    /** The Constant icpc2noCodeToName. */
    private static final Map<String, String> icpc2noCodeToName = new HashMap<>();

    // TODO: Get source and target information from MapProject?
    /** The Constant ICD10NO_NULL_20240723. */
    private static final String ICD10NO_NULL_20240723 = "icd10no_null_20240723";

    /** The code systems. */
    private static Map<String, SnowstormFhirCodeSystem> codeSystems = new HashMap<>();

    /** The Constant SNOWSTORM_CONCEPTS_CACHE. */
    private static final String SNOWSTORM_CONCEPTS_CACHE = "snowstorm_concepts";

    /** The Constant SNOWSTORM_TERMINOLOGY_CACHE. */
    private static final String SNOWSTORM_TERMINOLOGY_CACHE = "snowstorm_terminology";

    /**
     * Gets the concept.
     *
     * @param terminology the terminology
     * @param version the version
     * @param code the code
     * @return the concept
     * @throws Exception the exception
     */
    public static Concept getConcept(final String terminology, final String version, final String code) throws Exception {

        if (StringUtils.isBlank(code)) {
            return null;
        }

        if (StringUtils.isBlank(terminology)) {
            throw new Exception("terminology are required parameters. Must not be null or empty");
        }

        String newVersion = version;
        if (StringUtils.isAnyBlank(version)) {
            if (terminology.trim().toUpperCase().startsWith("SNOMEDCT")) {
                newVersion = "2024-04-15";
            } else if (terminology.trim().toUpperCase().startsWith("ICD-10-NO")) {
                newVersion = "20240723";
            }
        }

        final String conceptCacheKey = getConceptCacheKey(terminology, newVersion, code);

        final boolean exists = CachingUtility.containsObjects(SNOWSTORM_CONCEPTS_CACHE, conceptCacheKey);
        if (exists) {
            return CachingUtility.getObject(SNOWSTORM_CONCEPTS_CACHE, conceptCacheKey, Concept.class).get();
        }

        if (terminology.trim().toUpperCase().startsWith("SNOMEDCT")) {

            final Concept concept = getConceptFromSnowstorm(terminology, newVersion, code);
            if (concept != null) {
                CachingUtility.cacheObject(SNOWSTORM_CONCEPTS_CACHE, conceptCacheKey, Concept.class, concept);
            }
            return concept;

        } else {

            final Concept concept = getConceptByCodeFhirApi(terminology, newVersion, code);
            if (concept != null) {
                CachingUtility.cacheObject(SNOWSTORM_CONCEPTS_CACHE, conceptCacheKey, Concept.class, concept);
            }
            return concept;
        }

    }

    /**
     * Find concepts.
     *
     * @param terminology the terminology
     * @param version the version
     * @param searchParameters the search parameters
     * @return the result list concept
     * @throws Exception the exception
     */
    public static ResultListConcept findConcepts(final String terminology, final String version, final SearchParameters searchParameters) throws Exception {

        if (StringUtils.isAnyBlank(terminology, version)) {
            throw new Exception("terminology and version are required parameters. Must not be null or empty.");
        }

        if (searchParameters == null) {
            throw new Exception("searchParameters is required parameter. Must not be null.");
        }

        final String terminologyCacheKey = getTerminologyCacheKey(terminology, version);

        if (terminology.trim().toUpperCase().startsWith("SNOMEDCT")) {
            final boolean exists = CachingUtility.containsObjects(SNOWSTORM_TERMINOLOGY_CACHE, terminologyCacheKey);

            if (!exists) {
                cacheSnowstormConcepts(terminology, version);
            }
        } else {
            final boolean exists = CachingUtility.containsObjects(SNOWSTORM_TERMINOLOGY_CACHE, terminologyCacheKey);

            if (!exists) {
                getCodeSystemsFromFhir();
                final SnowstormFhirCodeSystem snowstormFhirCodeSystem = codeSystems.get(terminology + "_" + version);
                cacheSnowstormConceptsFhirApi(snowstormFhirCodeSystem);
            }
        }

        return findConceptsFromCache(terminologyCacheKey, searchParameters);
    }

    /**
     * Cache concepts.
     *
     * @param terminology the terminology
     * @param version the version
     * @throws Exception the exception
     */
    private static void cacheSnowstormConcepts(final String terminology, final String version) throws Exception {

        final long start = System.currentTimeMillis();

        boolean done = false;
        final int batchSizeLimit = 10000;
        String searchAfter = "";

        final String branch = "MAIN/" + terminology + "/" + version;
        final String terminologyCacheKey = getTerminologyCacheKey(terminology, version);
        final List<Concept> terminologyConcepts = new ArrayList<>();

        while (!done) {

            final String targetUri = SnowstormConnection.getBaseUrl() + branch + "/concepts?activeFilter=true&termActive=true&limit=" + batchSizeLimit
                + (StringUtils.isNotEmpty(searchAfter) ? "&searchAfter=" + searchAfter : "");

            LOG.info("cacheConcepts url: " + targetUri);

            try (final Response response = SnowstormConnection.getResponse(targetUri)) {

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new Exception(
                        "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
                }

                final ObjectMapper mapper = new ObjectMapper();
                final String resultString = response.readEntity(String.class);
                response.close();

                final JsonNode doc = mapper.readTree(resultString);
                final JsonNode conceptNodeBatch = doc.get("items");
                final Iterator<JsonNode> conceptIterator = conceptNodeBatch.iterator();

                // parse items to retrieve matching concept
                while (conceptIterator.hasNext()) {

                    final JsonNode conceptNode = conceptIterator.next();
                    final Concept concept = buildConcept(conceptNode);
                    final String cacheKey = getConceptCacheKey(terminology, version, concept.getCode());
                    CachingUtility.cacheObject(SNOWSTORM_CONCEPTS_CACHE, cacheKey, Concept.class, concept);
                    terminologyConcepts.add(concept);
                }

                if (conceptNodeBatch.size() < batchSizeLimit) {
                    done = true;
                } else {
                    searchAfter = (doc.has("searchAfter") ? doc.get("searchAfter").asText() : "");
                }
            }
        }
        CachingUtility.cacheObject(SNOWSTORM_TERMINOLOGY_CACHE, terminologyCacheKey, List.class, terminologyConcepts);
        LOG.info("cacheConcepts took: " + (System.currentTimeMillis() - start) + " ms for branch: " + branch + ", terminology: " + terminology + ", version: "
            + version);
    }

    /**
     * Gets the concept.
     *
     * @param terminology the terminology
     * @param version the version
     * @param code the code
     * @return the concept
     * @throws Exception the exception
     */
    private static Concept getConceptFromSnowstorm(final String terminology, final String version, final String code) throws Exception {

        if ("ICD-10-NO".equals(terminology)) {
            final Concept concept = getConceptByCodeFhirApi(terminology, version, code);
            return concept;

        } else if ("ICPC2NO".equals(terminology)) {

            // TEMPORARY
            final Concept concept = new Concept();
            concept.setId(code);
            concept.setName(getICPC2NOName(code));
            concept.setTerminology(terminology);
            concept.setVersion(version);
            return concept;
            // TEMPORARY
        }

        // Connect to snowstorm

        final String branch = "MAIN/" + terminology + "/" + version;
        final String targetUri =
            SnowstormConnection.getBaseUrl().concat(branch).concat("/concepts?activeFilter=true&includeLeafFlag=false&form=inferred&conceptIds=").concat(code);
        LOG.info("getSnowstormConcept url: " + targetUri);

        try (final Response response = SnowstormConnection.getResponse(targetUri)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                throw new Exception(
                    "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
            }

            final ObjectMapper mapper = new ObjectMapper();
            final String resultString = response.readEntity(String.class);
            response.close();

            final JsonNode doc = mapper.readTree(resultString);
            final JsonNode conceptNodeBatch = doc.get("items");
            final Iterator<JsonNode> itemIterator = conceptNodeBatch.iterator();

            // parse items to retrieve matching concept
            while (itemIterator.hasNext()) {

                final JsonNode conceptNode = itemIterator.next();
                final Concept concept = buildConcept(conceptNode);
                return concept;
            }
        }

        // If no concept with the specified terminology and code found, return null
        LOG.warn("No concept found for branch:{}, terminology: {}, version:{}, code: {}", branch, terminology, version, code);
        return null;
    }

    /**
     * Gets the module names.
     *
     * @param edition the edition
     * @return the module names
     * @throws Exception the exception
     */
    public static Map<String, String> getModuleNames(final Edition edition) throws Exception {

        // Create Snowstorm URL
        final String conceptSearchUrl = SnowstormConnection.getBaseUrl() + edition.getBranch() + "/concepts/search";
        final String bodyBase = "{\"limit\": 1000, ";
        String bodyConceptIds = "\"conceptIds\":[";

        final Map<String, String> moduleNames = new HashMap<>();

        for (final String moduleId : edition.getModules()) {
            bodyConceptIds += "\"" + moduleId + "\",";
        }

        bodyConceptIds = StringUtils.removeEnd(bodyConceptIds, ",") + "]";

        final String searchBody = bodyBase + bodyConceptIds + "}";
        LOG.debug("getModuleNames URL: " + conceptSearchUrl);
        LOG.debug("getModuleNames BODY: " + searchBody);

        try (final Response response = SnowstormConnection.postResponse(conceptSearchUrl, searchBody)) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                throw new Exception("call to url '" + conceptSearchUrl + "' for module name lookup wasn't successful. Status: " + response.getStatus()
                    + " Message: " + response.getStatusInfo().getReasonPhrase());
            }

            final ObjectMapper mapper = new ObjectMapper();
            final String resultString = response.readEntity(String.class);
            response.close();

            final JsonNode root = mapper.readTree(resultString.toString());
            final Iterator<JsonNode> iterator = root.get("items").iterator();

            while (iterator != null && iterator.hasNext()) {

                final JsonNode conceptNode = iterator.next();
                final String conceptId = conceptNode.get("conceptId").asText();
                String name = "";

                if (conceptNode.get("fsn") != null && conceptNode.get("fsn").get("term") != null) {
                    name = conceptNode.get("fsn").get("term").asText();
                }

                moduleNames.put(conceptId, name);
            }
        }

        return moduleNames;
    }

    /**
     * Builds the concept.
     *
     * @param conceptNode the concept node
     * @return the concept
     */
    public static Concept buildConcept(final JsonNode conceptNode) {

        final Concept concept = new Concept();
        concept.setActive(conceptNode.get("active").asBoolean());
        concept.setId(conceptNode.get("id").asText());
        concept.setCode(conceptNode.get("id").asText());
        concept.setDefined(!"PRIMITIVE".equals(conceptNode.get("definitionStatus").asText()));

        if (conceptNode.has("pt") && conceptNode.get("pt").has("term")) {
            concept.setName(conceptNode.get("pt").get("term").asText());
        }

        if (conceptNode.has("fsn") && conceptNode.get("fsn").has("term")) {
            concept.setFsn(conceptNode.get("fsn").get("term").asText());
        }

        return concept;
    }

    /**
     * Gets the refset concepts.
     *
     * @param service the service
     * @param branch the branch
     * @param areParentConcepts the are parent concepts
     * @return the refset concepts
     * @throws Exception the exception
     */
    public static ResultListConcept getRefsetConcepts(final TerminologyService service, final String branch, final boolean areParentConcepts) throws Exception {

        final ResultListConcept results = new ResultListConcept();
        final Set<String> existingRefsetIds = new HashSet<>();
        final String ecl = StringUtility.encodeValue(QueryParserBase.escape(areParentConcepts ? "<<" : "<" + RefsetService.SIMPLE_TYPE_REFERENCE_SET));
        final List<Edition> editions = RefsetService.getEditionForBranch(branch);

        final StringBuilder modules = new StringBuilder();

        if (!editions.isEmpty()) {
            for (final Edition edition : editions) {
                modules.append(edition.getModules().stream().collect(Collectors.joining(","))).append(", ");
            }
        }
        modules.append(RefsetService.SNOMED_CORE_MODULE_ID);

        final String url = SnowstormConnection.getBaseUrl() + branch + "/" + "concepts?ecl=" + ecl + "&limit=1000&module=" + modules.toString();

        LOG.debug("getRefsetConcepts URL: " + url);

        // If we are looking for concepts to represent a refset, then we are
        // filtering out those concept that are currently refsets
        if (!areParentConcepts) {

            // get all the existing refsets for latest branch version
            final String query = "(latestPublishedVersion: true AND hasVersionInDevelopment: false) OR versionStatus: (" + Refset.IN_DEVELOPMENT + ")";
            final ResultList<Refset> refsets = service.find(query, null, Refset.class, null);

            for (final Refset refset : refsets.getItems()) {
                if (!existingRefsetIds.contains(refset.getRefsetId())) {
                    existingRefsetIds.add(refset.getRefsetId());
                }
            }

            LOG.debug("getRefsetConcepts existingRefsetIds: " + existingRefsetIds);
        }

        final Set<String> excludeList = RefsetService.conceptsToRemove(!areParentConcepts);

        // update the concept with the new data
        try (final Response response = SnowstormConnection.getResponse(url)) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                throw new Exception("Unable to get reference set concepts. Status: " + Integer.toString(response.getStatus()) + ". Error: "
                    + response.getStatusInfo().getReasonPhrase());
            }

            final ObjectMapper mapper = new ObjectMapper();
            final String resultString = response.readEntity(String.class);
            response.close();
            final JsonNode root = mapper.readTree(resultString.toString());
            final Iterator<JsonNode> iterator = root.get("items").iterator();

            // loop thru the returned member details and inactivate it or add it to
            // the list to delete
            while (iterator != null && iterator.hasNext()) {

                final JsonNode conceptNode = iterator.next();
                final Concept concept = new Concept();
                final String conceptId = conceptNode.get("conceptId").asText();

                // if this isn't for a parent concept and the refset already exists then
                // skip it
                if ((!areParentConcepts && existingRefsetIds.contains(conceptId)) || excludeList.contains(conceptId)) {
                    continue;
                }

                concept.setCode(conceptId);
                concept.setName(conceptNode.get("pt").get("term").asText());
                concept.setTerminology("SNOMEDCT");

                results.getItems().add(concept);
            }

            // sort the results
            Collections.sort(results.getItems(), (o1, o2) -> (o1.getName().compareTo(o2.getName())));
        }

        return results;
    }

    /**
     * Update refset concept.
     *
     * @param refset the refset
     * @param active the active
     * @param moduleId the module id
     * @throws Exception the exception
     */
    public static void updateRefsetConcept(final Refset refset, final boolean active, final String moduleId) throws Exception {

        // first retrieve the concept so all fields will be present for the update
        final String refsetId = refset.getRefsetId();
        final String branch = refset.getBranchPath();
        final String url = SnowstormConnection.getBaseUrl() + "browser/" + branch + "/" + "concepts/" + refsetId;
        final ObjectMapper mapper = new ObjectMapper();
        ObjectNode memberBody = null;

        LOG.debug("updateRefsetConcept URL: " + url);

        try (final Response response = SnowstormConnection.getResponse(url)) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                throw new Exception("Unable to retrieve reference set concept: " + refsetId + " Status: " + Integer.toString(response.getStatus()) + ". Error: "
                    + response.getStatusInfo().getReasonPhrase());
            }

            // create the body entity for the update call from the retrieved concept
            final String resultString = response.readEntity(String.class);
            memberBody = (ObjectNode) mapper.readTree(resultString.toString()).deepCopy();
        }

        if (active != refset.isActive()) {

            LOG.info("Changing refset concept active status to: " + active);

            // set the concept status
            memberBody.put("active", active);

            // set the concept inactivation indicator
            memberBody.put("inactivationIndicator", (!active) ? "OUTDATED" : "");

            // loop thru the class axioms and set the status
            final Iterator<JsonNode> axiomIterator = memberBody.get("classAxioms").iterator();

            while (axiomIterator.hasNext()) {

                final ObjectNode axiomNode = (ObjectNode) axiomIterator.next();
                axiomNode.put("active", active);
            }

            // loop thru the relationships and set the status
            final Iterator<JsonNode> relationshipsIterator = memberBody.get("relationships").iterator();

            while (relationshipsIterator.hasNext()) {
                final ObjectNode relationshipsNode = (ObjectNode) relationshipsIterator.next();
                relationshipsNode.put("active", active);
            }
        }

        if (!moduleId.equals(refset.getModuleId())) {

            LOG.info("Changing Refset Concept Module ID from: " + refset.getModuleId() + " to: " + moduleId);
            memberBody.put("moduleId", moduleId);

            // loop thru the descriptions and set the moduleId
            final Iterator<JsonNode> descriptionsIterator = memberBody.get("descriptions").iterator();

            while (descriptionsIterator.hasNext()) {

                final ObjectNode descriptionNode = (ObjectNode) descriptionsIterator.next();
                descriptionNode.put("moduleId", moduleId);
            }

            // loop thru the class axioms and set the moduleId
            final Iterator<JsonNode> axiomIterator = memberBody.get("classAxioms").iterator();

            while (axiomIterator.hasNext()) {

                final ObjectNode axiomNode = (ObjectNode) axiomIterator.next();
                axiomNode.put("moduleId", moduleId);

                // loop thru the axiom relationships and set the moduleId
                final Iterator<JsonNode> relationshipsIterator = axiomNode.get("relationships").iterator();

                while (relationshipsIterator.hasNext()) {

                    final ObjectNode relationshipsNode = (ObjectNode) relationshipsIterator.next();
                    relationshipsNode.put("moduleId", moduleId);
                }
            }

            // loop thru the relationships and set the moduleId
            final Iterator<JsonNode> relationshipsIterator = memberBody.get("relationships").iterator();

            while (relationshipsIterator.hasNext()) {

                final ObjectNode relationshipsNode = (ObjectNode) relationshipsIterator.next();
                relationshipsNode.put("moduleId", moduleId);
            }
        }

        LOG.debug("updateRefsetConcept update concept URL body: " + memberBody.toString());

        // update the concept with the new data
        try (final Response response = SnowstormConnection.putResponse(url, memberBody.toString())) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                throw new Exception("Unable to update reference set concept: " + refsetId + ". Status: " + Integer.toString(response.getStatus()) + ". Error: "
                    + response.getStatusInfo().getReasonPhrase());
            }

            LOG.info("updateRefsetConcept refset concept: " + refsetId);
        }
    }

    /**
     * Populate concept leaf status.
     *
     * @param refset the refset
     * @param conceptsToProcess the concepts to process
     * @throws Exception the exception
     */
    public static void populateConceptLeafStatus(final Refset refset, final List<Concept> conceptsToProcess) throws Exception {

        final StringBuffer conceptIds = new StringBuffer();

        // Create Snowstorm URL
        final String url =
            SnowstormConnection.getBaseUrl() + RefsetMemberService.getBranchPath(refset) + "/concepts?limit=3000&includeLeafFlag=true&form=inferred";

        boolean firstTime = true;

        for (final Concept concept : conceptsToProcess) {

            if (firstTime) {
                firstTime = false;
            } else {
                conceptIds.append(",");
            }

            conceptIds.append(concept.getCode());
        }

        // Call Snowstorm
        // LOG.debug("Get Concept Leaf Status URL: " + url + "&conceptIds=" +
        // conceptIds);
        final String fullSnowstormUrl = url + "&conceptIds=" + conceptIds;
        try (final Response response = SnowstormConnection.getResponse(fullSnowstormUrl)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                throw new Exception("call to url '" + fullSnowstormUrl + "' wasn't successful. Status: " + response.getStatus() + " Message: "
                    + response.getStatusInfo().getReasonPhrase());
            }

            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

            final JsonNode allConceptNodes = root.get("items");
            final Iterator<JsonNode> conceptIterator = allConceptNodes.iterator();
            final HashMap<String, JsonNode> conceptNodes = new HashMap<>();

            // Map concept info
            while (conceptIterator.hasNext()) {

                final JsonNode conceptNode = conceptIterator.next();

                final String conceptId = conceptNode.get("conceptId").asText();
                conceptNodes.put(conceptId, conceptNode);
            }

            // Populate concept with child info
            for (final Concept concept : conceptsToProcess) {

                final JsonNode conceptNode = conceptNodes.get(concept.getCode());

                if (conceptNode == null || conceptNode.size() == 0) {

                    LOG.debug("Concept info not retrieved for concept " + concept.getCode());
                    continue;
                }

                if (conceptNode.has("isLeafInferred")) {

                    concept.setHasChildren(!conceptNode.get("isLeafInferred").asBoolean());
                }

            }

        } catch (final Exception ex) {

            LOG.error("Could not retrieve concept leaf info " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Gets the concept ancestors.
     *
     * @param refset the refset
     * @param conceptId the concept id
     * @return the concept ancestors
     * @throws Exception the exception
     */
    public static Concept getConceptAncestors(final Refset refset, final String conceptId) throws Exception {

        final ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
        lookupParameters.setNonDefaultPreferredTerms(RefsetMemberService.identifyNonDefaultPreferredTerms(refset.getEdition()));
        lookupParameters.setGetDescriptions(true);
        final String branchPath = RefsetMemberService.getBranchPath(refset);
        final String cacheString = refset.getRefsetId() + "-" + conceptId;
        final Map<String, Concept> branchCache = RefsetMemberService.getCacheForTaxonomySearchAncestors(branchPath);
        final Concept concept = new Concept();
        concept.setCode(conceptId);

        // check if the members call has been cached
        if (branchCache.containsKey(cacheString)) {

            LOG.debug("getConceptAncestors USING CACHE");
            return branchCache.get(cacheString);
        }

        // Create Snowstorm URL
        final String url = SnowstormConnection.getBaseUrl() + "browser/" + branchPath + "/concepts/ancestor-paths?conceptIds=" + conceptId;

        // Call Snowstorm
        LOG.debug("Get Concept Ancestors URL: " + url);

        try (final Response response = SnowstormConnection.getResponse(url)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                throw new Exception(
                    "call to url '" + url + "' wasn't successful. Status: " + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
            }

            // read the results of the call for ancestors for many concepts
            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());
            final Iterator<JsonNode> iterator = root.iterator();

            // loop thru each concept to get the ancestor path for it
            while (iterator.hasNext()) {

                final JsonNode conceptNode = iterator.next();
                // final String nodeConceptId = conceptNode.get("conceptId").asText();
                final JsonNode ancestorPathNode = conceptNode.get("ancestorPath");

                // get the ancestor list and reverse the order so the taxonomy
                // root is first
                final ResultListConcept ancestorList = RefsetMemberService.populateConcepts(ancestorPathNode, refset, lookupParameters);
                final List<Concept> parents = ancestorList.getItems();
                Collections.reverse(parents);

                concept.setParents(parents);
            }
        }

        branchCache.put(cacheString, concept);
        RefsetMemberService.TAXONOMY_SEARCH_ANCESTORS_CACHE.put(branchPath, branchCache);

        return concept;
    }

    /**
     * Search concepts.
     *
     * @param refset the refset
     * @param searchParameters the search parameters
     * @param searchMembersMode the search members mode
     * @param limitReturnNumber the limit return number
     * @return the concept result list
     * @throws Exception the exception
     */
    public static ResultListConcept searchConcepts(final Refset refset, final SearchParameters searchParameters, final String searchMembersMode,
        final int limitReturnNumber) throws Exception {

        final ResultListConcept returnConcepts = new ResultListConcept();
        final ObjectMapper mapper = new ObjectMapper();
        final String encodedCaret = "%5E";
        final String encodedSpace = "%20";
        // final String encodedLeftBrace = "%7B";
        // final String encodedRightBrace = "%7D";
        boolean searchOnlyRefsetMembers = false;
        boolean limitToNonMembers = false;
        int limit = RefsetMemberService.ELASTICSEARCH_MAX_RECORD_LENGTH - 1;

        if (limitReturnNumber > 0) {

            limit = limitReturnNumber;
        }

        if (searchMembersMode.equals("members")) {

            searchOnlyRefsetMembers = true;

        } else if (searchMembersMode.equals("non members")) {

            limitToNonMembers = true;
        }

        // Create Snowstorm URL
        String url = SnowstormConnection.getBaseUrl() + RefsetMemberService.getBranchPath(refset) + "/concepts?&offset=0&limit=" + limit;

        // if this search is for editing then get the concept leaf information
        if (searchParameters.getEditing()) {

            url += "&includeLeafFlag=true&form=inferred";
        }

        boolean searchEcl = false;

        // if the query is not an ID then see if it passes ECL syntax
        if (!limitToNonMembers && searchParameters.getQuery() != null && !searchParameters.getQuery().matches("\\d*")) {

            final String eclUrl = SnowstormConnection.getBaseUrl() + "util/ecl-string-to-model";
            final String body = StringUtility.encodeValue(searchParameters.getQuery());

            LOG.debug("searchConcepts ECL Parse URL: " + eclUrl + "; body: " + body);

            try (final Response response = SnowstormConnection.postResponse(eclUrl, body)) {

                // if the query parses as ECL then search by ecl
                if (response.getStatusInfo().getFamily() == Family.SUCCESSFUL) {

                    searchEcl = true;
                }

            }

        }

        final String membersEcl = encodedCaret + refset.getRefsetId();
        // final String activeConceptsEcl = encodedLeftBrace + encodedLeftBrace +
        // "C" +
        // encodedSpace + "active=1" + encodedRightBrace + encodedRightBrace;
        // final String inactiveConceptsEcl = encodedLeftBrace + encodedLeftBrace +
        // "C"
        // + encodedSpace + "active=0" + encodedRightBrace + encodedRightBrace;
        // final String activeAndInactiveMembersEcl =
        // "(" + membersEcl + encodedSpace + activeConceptsEcl + encodedSpace + "OR"
        // +
        // encodedSpace + membersEcl + encodedSpace + inactiveConceptsEcl + ")";

        // set the appropriate way to search
        if (!searchEcl) {

            url += "&term=" + StringUtility.encodeValue(searchParameters.getQuery());

            if (searchOnlyRefsetMembers) {

                url += "&ecl=" + membersEcl;
            }

        } else {

            url += "&ecl=" + StringUtility.encodeValue(searchParameters.getQuery());

            if (searchOnlyRefsetMembers) {

                url += encodedSpace + "AND" + encodedSpace + "(" + membersEcl + ")";
            }

        }

        if (searchParameters.getActiveOnly() != null && searchParameters.getActiveOnly()) {

            url += "&activeFilter=true";
        }

        String searchAfter = "";
        boolean hasMorePages = true;
        final long start = System.currentTimeMillis();

        while (hasMorePages) {

            // Call Snowstorm
            final String fullSnowstormUrl = url + searchAfter;
            LOG.debug("searchConcepts URL: " + fullSnowstormUrl);

            try (final Response response = SnowstormConnection.getResponse(fullSnowstormUrl)) {

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                    hasMorePages = false;
                    throw new Exception("Call to URL '" + fullSnowstormUrl + "' wasn't successful. Status: " + response.getStatus() + " Message: "
                        + formatErrorMessage(response));
                }

                final String resultString = response.readEntity(String.class);
                final JsonNode root = mapper.readTree(resultString.toString());
                final JsonNode conceptNodeBatch = root.get("items");

                if (limitReturnNumber < 0 && root.get("searchAfter") != null) {

                    searchAfter = "&searchAfter=" + root.get("searchAfter").asText();
                }

                // if the search returned results set the total
                if (!returnConcepts.isTotalKnown()) {

                    returnConcepts.setTotal(root.get("total").asInt());
                    returnConcepts.setTotalKnown(true);
                }

                if (limitReturnNumber > 0 || conceptNodeBatch.size() == 0
                    || conceptNodeBatch.size() + returnConcepts.getItems().size() >= returnConcepts.getTotal()) {

                    hasMorePages = false;
                }

                if (System.currentTimeMillis() - start > RefsetMemberService.TIMEOUT_MILLISECOND_THRESHOLD) {

                    hasMorePages = false;
                }

                if (conceptNodeBatch.size() != 0 && !conceptNodeBatch.get(0).has("error")) {

                    final Iterator<JsonNode> itemIterator = conceptNodeBatch.iterator();
                    final ArrayList<Concept> conceptBatch = new ArrayList<>();

                    // parse items to retrieve matching concepts
                    while (itemIterator.hasNext()) {

                        final JsonNode conceptNode = itemIterator.next();

                        final Concept concept = new Concept();
                        concept.setActive(conceptNode.get("active").asBoolean());
                        concept.setId(conceptNode.get("id").asText());
                        concept.setCode(conceptNode.get("id").asText());

                        if (!conceptNode.get("definitionStatus").asText().equals("PRIMITIVE")) {

                            concept.setDefined(true);
                        } else {

                            concept.setDefined(false);
                        }

                        if (conceptNode.get("pt") != null) {

                            concept.setName(conceptNode.get("pt").get("term").asText());
                        }

                        if (conceptNode.get("fsn") != null && conceptNode.get("fsn").get("term") != null) {

                            concept.setFsn(conceptNode.get("fsn").get("term").asText());
                        }

                        if (conceptNode.has("isLeafInferred")) {

                            concept.setHasChildren(!conceptNode.get("isLeafInferred").asBoolean());
                        }

                        RefsetMemberService.setConceptPermissions(concept);
                        concept.setMemberOfRefset(searchOnlyRefsetMembers);
                        RefsetMemberService.processIntensionalDefinitionException(refset, concept);
                        conceptBatch.add(concept);
                    }

                    if (searchParameters.getEditing() || limitToNonMembers) {

                        SnowstormRefsetMember.populateMembershipInformation(refset, conceptBatch);
                    }

                    returnConcepts.getItems().addAll(conceptBatch);
                }

            } catch (final Exception ex) {

                LOG.error("searchConcepts Could not retrieve concepts matching term: " + ex.getMessage());
                ex.printStackTrace();
                break;
            }

        }

        if (limitToNonMembers) {

            final List<Concept> conceptsToInclude = new ArrayList<>();

            for (final Concept concept : returnConcepts.getItems()) {

                if (concept.isMemberOfRefset()) {

                    continue;
                }

                conceptsToInclude.add(concept);

                if (conceptsToInclude.size() == searchParameters.getLimit()) {

                    break;
                }

            }

            returnConcepts.setItems(conceptsToInclude);
            returnConcepts.setTotal(conceptsToInclude.size());
        }

        return returnConcepts;
    }

    /**
     * Gets the concept details.
     *
     * @param conceptId the concept id
     * @param refset the refset
     * @return the concept details
     * @throws Exception the exception
     */
    public static Concept getConceptDetails(final String conceptId, final Refset refset) throws Exception {

        final String branchPath = RefsetMemberService.getBranchPath(refset);
        final String cacheString = refset.getRefsetId() + conceptId;
        final Map<String, Concept> branchCache = RefsetMemberService.getCacheForConceptDetails(branchPath);

        // check if the members call has been cached
        if (branchCache.containsKey(cacheString)) {

            LOG.debug("getConceptDetails USING CACHE");
            return branchCache.get(cacheString);
        }

        // 3 Snowstorm calls: 1) on concept, 2) parents, and 3) children
        try {

            final String url = SnowstormConnection.getBaseUrl() + "browser/" + RefsetMemberService.getBranchPath(refset) + "/" + "concepts/" + conceptId;

            LOG.debug("Get Concept Details URL: " + url);

            final ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
            lookupParameters.setGetDescriptions(true);
            lookupParameters.setGetRoleGroups(true);
            lookupParameters.setSingleConceptRequest(true);

            final ResultListConcept conceptResultList = getConceptsFromSnowstorm(url, refset, lookupParameters, null);

            if (conceptResultList.size() != 1) {

                throw new Exception("Unexpected number of concepts found (" + conceptResultList.size() + ") in getConceptDetails");
            }

            final Concept concept = conceptResultList.getItems().iterator().next();
            branchCache.put(cacheString, concept);
            RefsetMemberService.CONCEPT_DETAILS_CACHE.put(branchPath, branchCache);

            return concept;

        } catch (final RestException ex) {

            // throw new RestException(false, HttpStatus.NOT_FOUND, "Not Found",
            // message);
            throw new RestException(false, ex.getError().getStatus(), ex.getMessage(), "Could not get Reference Set children for concept " + conceptId + ".");

        } catch (final Exception ex) {

            throw new Exception("Could not get Reference Set children for concept " + conceptId + " from snowstorm: " + ex.getMessage(), ex);

        }
    }

    /**
     * Gets the parents.
     *
     * @param conceptId the concept id
     * @param refset the refset
     * @param language the language
     * @return the parents
     * @throws Exception the exception
     */
    public static ResultListConcept getParents(final String conceptId, final Refset refset, final String language) throws Exception {

        try {

            final String url = SnowstormConnection.getBaseUrl() + "browser/" + RefsetMemberService.getBranchPath(refset) + "/" + "concepts/" + conceptId
                + "/parents?form=inferred";

            LOG.debug("Get Parents URL: " + url);
            final ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
            lookupParameters.setGetFsn(true);

            final ResultListConcept results = getConceptsFromSnowstorm(url, refset, lookupParameters, language);

            // sort the results
            Collections.sort(results.getItems(), (object1, object2) -> (object1.compareTo(object2)));

            return results;

        } catch (final Exception ex) {

            if ("400".equals(ex.getMessage())) {

                throw ex;
            }

            throw new Exception("Could not get Reference Set parents for concept " + conceptId + " from snowstorm: " + ex.getMessage(), ex);
        }
    }

    /**
     * Gets the children.
     *
     * @param conceptId the concept id
     * @param refset the refset
     * @param language the language
     * @return the children
     * @throws Exception the exception
     */
    public static ResultListConcept getChildren(final String conceptId, final Refset refset, final String language) throws Exception {

        try {

            final String branchPath = RefsetMemberService.getBranchPath(refset);
            final String url = SnowstormConnection.getBaseUrl() + "browser/" + branchPath + "/" + "concepts/" + conceptId + "/children?form=inferred";

            LOG.debug("Get Children URL: " + url);
            final ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
            lookupParameters.setGetFsn(true);

            return getConceptsFromSnowstorm(url, refset, lookupParameters, language);

        } catch (final Exception ex) {

            if ("400".equals(ex.getMessage())) {

                throw ex;
            }

            throw new Exception("Could not get Reference Set children for concept " + conceptId + " from snowstorm: " + ex.getMessage(), ex);
        }
    }

    /**
     * Gets the concepts from snowstorm.
     *
     * @param url the url
     * @param refset the refset
     * @param lookupParameters the lookup parameters
     * @param language the language
     * @return the concepts from snowstorm
     * @throws Exception the exception
     */
    public static ResultListConcept getConceptsFromSnowstorm(final String url, final Refset refset, final ConceptLookupParameters lookupParameters,
        final String language) throws Exception {

        final String acceptLanguage = (language != null) ? language : SnowstormConnection.DEFAULT_ACCECPT_LANGUAGES;
        try (final Response response = SnowstormConnection.getResponse(url, acceptLanguage)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                LOG.error(
                    "Call to url '" + url + "' wasn't successful. Status: " + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
                throw new RestException(false, response.getStatusInfo().getStatusCode(), "Message: " + response.getStatusInfo().getReasonPhrase(),
                    "Error looking up concept(s).");
            }

            final String resultString = response.readEntity(String.class);

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                throw new Exception(Integer.toString(response.getStatus()));
            }

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

            return RefsetMemberService.populateConcepts(root, refset, lookupParameters);
        }
    }

    /**
     * Gets the concept ids from ecl.
     *
     * @param branch the branch
     * @param ecl the ecl
     * @return the concept ids from ecl
     * @throws Exception the exception
     */
    public static List<String> getConceptIdsFromEcl(final String branch, final String ecl) throws Exception {

        final List<String> concepts = new ArrayList<>();
        final ObjectMapper mapper = new ObjectMapper();
        final String url = SnowstormConnection.getBaseUrl() + branch + "/" + "concepts?ecl=" + StringUtility.encodeValue(ecl) + "&limit="
            + RefsetMemberService.ELASTICSEARCH_MAX_RECORD_LENGTH;
        boolean keepSearching = true;
        int total = 0;
        int totalReturned = 0;
        String searchAfter = "";

        while (keepSearching) {

            LOG.debug("getConceptIdsFromEcl URL: " + url + searchAfter);

            // update the concept with the new data
            try (final Response response = SnowstormConnection.getResponse(url + searchAfter)) {

                // Only process payload if Rest call is successful
                if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                    throw new Exception("Unable to get Reference Set concepts. Status: " + Integer.toString(response.getStatus()) + ". Error: "
                        + response.getStatusInfo().getReasonPhrase());
                }

                final String resultString = response.readEntity(String.class);
                final JsonNode root = mapper.readTree(resultString.toString());
                final JsonNode items = root.get("items");
                final Iterator<JsonNode> iterator = items.iterator();
                totalReturned += items.size();

                if (total == 0) {

                    total = root.get("total").asInt();
                }

                LOG.debug("getConceptIdsFromEcl total: " + total);
                LOG.debug("getConceptIdsFromEcl totalReturned: " + totalReturned);

                if (totalReturned == 0 || totalReturned >= total) {

                    keepSearching = false;
                } else {

                    searchAfter = "&searchAfter=" + root.get("searchAfter").asText();
                }

                LOG.debug("getConceptIdsFromEcl totalReturned so far: " + totalReturned);

                // loop thru the returned concepts add them to the list
                while (iterator != null && iterator.hasNext()) {

                    final JsonNode conceptNode = iterator.next();
                    final String conceptId = conceptNode.get("conceptId").asText();

                    if (concepts.contains(conceptId)) {

                        continue;
                    }

                    concepts.add(conceptId);
                }

                LOG.debug("getConceptIdsFromEcl concepts.size(): " + concepts.size());
            }

        }

        return concepts;
    }

    /**
     * Compile upgrade data.
     *
     * @param service the service
     * @param user the user
     * @param refsetInternalId the refset internal id
     * @return the string
     * @throws Exception the exception
     */
    public static String compileUpgradeData(final TerminologyService service, final User user, final String refsetInternalId) throws Exception {

        final String status = "Upgrade data compiled";
        Refset tempRefset = RefsetService.getRefset(service, user, refsetInternalId);

        WorkflowService.canUserPerformWorkflowAction(user, tempRefset, WorkflowService.UPGRADE);

        if (tempRefset.getWorkflowStatus().equals(WorkflowService.PUBLISHED) && tempRefset.getAvailableActions().contains(WorkflowService.UPGRADE)) {

            final String newRefsetInternalId = RefsetService.createNewRefsetVersion(service, user, tempRefset.getId(), false);
            tempRefset = service.get(newRefsetInternalId, Refset.class);
        }

        if (!tempRefset.getWorkflowStatus().equals(WorkflowService.READY_FOR_EDIT)) {

            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Reference Set is in the wrong status to be Upgraded");
        }

        final Refset upgradeRefset = tempRefset;

        // set the refset into IN_UPGRADE status
        WorkflowService.setWorkflowStatusByAction(service, user, WorkflowService.UPGRADE, upgradeRefset, "");

        final List<Concept> inactiveMemberList = new ArrayList<>();
        final List<String> activeMemberList = new ArrayList<>();
        final LinkedHashMap<String, UpgradeInactiveConcept> inactiveData = new LinkedHashMap<>();
        final String branchPath = RefsetMemberService.getBranchPath(upgradeRefset);
        final String refsetId = upgradeRefset.getRefsetId();
        final ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
        lookupParameters.setGetMembershipInformation(true);
        String searchAfter = "";
        boolean hasMorePages = true;
        final String acceptLanguage = SnowstormConnection.DEFAULT_ACCECPT_LANGUAGES;
        int memberTotal = 0;
        boolean memberTotalKnown = false;
        String inactiveConceptIds = "";
        final ObjectMapper mapper = new ObjectMapper();
        final List<String> nonDefaultPreferredTerms = RefsetMemberService.identifyNonDefaultPreferredTerms(upgradeRefset.getEdition());
        @SuppressWarnings("unused")
        int replacementCount = 0;
        final String conceptSearchUrl = SnowstormConnection.getBaseUrl() + branchPath + "/concepts/search";
        final String bodyBase =
            "{\"limit\": " + RefsetMemberService.ELASTICSEARCH_MAX_RECORD_LENGTH + ", \"eclFilter\": \"^" + upgradeRefset.getRefsetId() + "\", ";

        // when searching for members we only want concepts whose membership is
        // active
        // (though the concept itself can be inactive)
        final String url = SnowstormConnection.getBaseUrl() + branchPath + "/members?referenceSet=" + refsetId + "&active=true&offset=0&limit="
            + RefsetMemberService.ELASTICSEARCH_MAX_RECORD_LENGTH;

        // use members call to get members
        while (hasMorePages) {

            final String fullSnowstormUrl = url + searchAfter;
            LOG.debug("compileUpgradeData Member list URL: " + fullSnowstormUrl);

            try (final Response response = SnowstormConnection.getResponse(fullSnowstormUrl, acceptLanguage)) {

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                    hasMorePages = false;
                    throw new Exception("call to url '" + fullSnowstormUrl + "' wasn't successful. Status: " + response.getStatus() + " Message: "
                        + response.getStatusInfo().getReasonPhrase());
                }

                final String resultString = response.readEntity(String.class);

                // Only process payload if Rest call is successful
                if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                    throw new Exception(Integer.toString(response.getStatus()));
                }

                final JsonNode root = mapper.readTree(resultString.toString());
                final JsonNode conceptNodeBatch = root.get("items");

                // if the search returned results set the total
                if (!memberTotalKnown) {

                    memberTotal = root.get("total").asInt();
                    memberTotalKnown = true;
                }

                if (root.get("searchAfter") != null) {

                    searchAfter = "&searchAfter=" + root.get("searchAfter").asText();
                }

                if (conceptNodeBatch.size() == 0 || conceptNodeBatch.size() + inactiveMemberList.size() >= memberTotal) {

                    hasMorePages = false;
                }

                final ResultListConcept currentMemberBatch = RefsetMemberService.populateConcepts(root, upgradeRefset, lookupParameters);

                // filter for inactive concepts
                for (final Concept concept : currentMemberBatch.getItems()) {

                    if (concept.isActive()) {

                        activeMemberList.add(concept.getCode());
                    } else {

                        inactiveMemberList.add(concept);
                        inactiveConceptIds += "\"" + concept.getCode() + "\", ";
                    }

                }

            }

        }

        inactiveConceptIds = StringUtils.removeEnd(inactiveConceptIds, ", ");
        final String conceptDetailsBaseUrl = SnowstormConnection.getBaseUrl() + "browser/" + branchPath + "/concepts?conceptIds=";
        boolean searchAgain = true;
        int searchIndex = 0;

        if (inactiveMemberList.size() == 0) {

            return status;
        }

        // Change the numbers to '1's to avoid threading
        final ExecutorService executor =
            new ThreadPoolExecutor(30, 30, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(30), new ThreadPoolExecutor.CallerRunsPolicy());

        // use /browser/MAIN/concepts/bulk-load POST call to get the inactive
        // concept
        // details including descriptions and reasons (root.associationTargets)
        while (searchAgain) {

            searchAgain = false;
            String bodyConceptIds = "";

            for (; searchIndex < inactiveMemberList.size(); searchIndex++) {

                final Concept member = inactiveMemberList.get(searchIndex);
                final String conceptId = member.getCode();
                final UpgradeInactiveConcept inactiveConcept = new UpgradeInactiveConcept();
                inactiveConcept.setRefsetId(refsetId);
                inactiveConcept.setCode(member.getCode());
                inactiveConcept.setMemberId(member.getMemberId());
                inactiveConcept.setStillMember(true);
                inactiveData.put(conceptId, inactiveConcept);
                bodyConceptIds += conceptId + ",";

                if (conceptDetailsBaseUrl.length() + bodyConceptIds.length() >= RefsetMemberService.URL_MAX_CHAR_LENGTH) {

                    searchAgain = true;
                    break;
                }

            }

            bodyConceptIds = StringUtils.removeEnd(bodyConceptIds, ",");

            final String memberDetailsUrl = conceptDetailsBaseUrl + bodyConceptIds;
            Iterator<JsonNode> iterator = null;

            LOG.debug("compileUpgradeData member details URL: " + memberDetailsUrl);

            try (final Response response = SnowstormConnection.getResponse(memberDetailsUrl)) {

                final String resultString = response.readEntity(String.class);

                // Only process payload if Rest call is successful
                if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                    searchAgain = false;
                    throw new Exception("call to url '" + memberDetailsUrl + "' wasn't successful. Status: " + response.getStatus() + " Message: "
                        + response.getStatusInfo().getReasonPhrase());
                }

                final JsonNode root = mapper.readTree(resultString.toString());
                iterator = root.get("items").iterator();

                // loop thru the returned member details and process the descriptions
                // and
                // replacement concepts
                while (iterator != null && iterator.hasNext()) {

                    final JsonNode conceptNode = iterator.next();
                    final UpgradeInactiveConcept inactiveConcept = inactiveData.get(conceptNode.get("conceptId").asText());

                    if (conceptNode.get("descriptions") != null) {

                        final List<Map<String, String>> descriptions = RefsetMemberService.populateDescriptions(inactiveConcept.getCode(),
                            conceptNode.get("descriptions"), upgradeRefset, nonDefaultPreferredTerms);
                        inactiveConcept.setDescriptions(ModelUtility.toJson(descriptions));
                    }

                    final JsonNode inactivationIndicatorNode = conceptNode.get("inactivationIndicator");

                    if (inactivationIndicatorNode != null) {

                        inactiveConcept.setInactivationReason(inactivationIndicatorNode.asText());
                    } else {

                        inactiveConcept.setInactivationReason("NOT SPECIFIED");
                    }

                    final JsonNode associationTargets = conceptNode.get("associationTargets");

                    if (associationTargets != null && associationTargets.size() != 0 && associationTargets.fields() != null) {

                        LOG.debug("compileUpgradeData associationTargets: " + ModelUtility.toJson(associationTargets));
                        final Map<String, String> reasonMap = new HashMap<>();
                        final Entry<String, JsonNode> entry = associationTargets.fields().next();
                        final List<Concept> replacementConceptsToLookup = new ArrayList<>();
                        final String reason = entry.getKey();
                        String replacementConceptIds = entry.getValue().toString();
                        String replacementBodyConceptIds = "\"conceptIds\":[";

                        if (replacementConceptIds.contains("[")) {

                            replacementConceptIds = replacementConceptIds.substring(1, replacementConceptIds.length() - 1);
                        }

                        replacementConceptIds = replacementConceptIds.replaceAll("\"", "");

                        for (final String replacementConceptId : replacementConceptIds.split(",")) {

                            replacementCount++;
                            reasonMap.put(replacementConceptId, reason);
                            replacementConceptsToLookup.add(new Concept(replacementConceptId));
                            replacementBodyConceptIds += "\"" + replacementConceptId + "\",";
                        }

                        final String threadReplacementBodyConceptIds = StringUtils.removeEnd(replacementBodyConceptIds, ",") + "]";

                        LOG.debug("compileUpgradeData replacementConceptsToLookup: " + replacementConceptsToLookup);

                        // process descriptions of any replacement concepts
                        if (replacementConceptsToLookup.size() > 0) {

                            executor.submit(new Runnable() {

                                /* see superclass */
                                @Override
                                public void run() {

                                    try (final TerminologyService threadService = new TerminologyService()) {

                                        threadService.setModifiedBy(user.getUserName());
                                        threadService.setModifiedFlag(true);

                                        final List<String> replacementThatAreMembers = new ArrayList<>();
                                        final String memberSearchBody = bodyBase + threadReplacementBodyConceptIds + "}";
                                        LOG.debug("compileUpgradeData replacement member search url: " + conceptSearchUrl);
                                        LOG.debug("compileUpgradeData replacement member search body: " + memberSearchBody);

                                        try (final Response response = SnowstormConnection.postResponse(conceptSearchUrl, memberSearchBody)) {

                                            final String resultString = response.readEntity(String.class);

                                            // Only process payload if Rest call is successful
                                            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                                                throw new Exception("call to url '" + conceptSearchUrl + "' for member search wasn't successful. Status: "
                                                    + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
                                            }

                                            final JsonNode root = mapper.readTree(resultString.toString());
                                            final Iterator<JsonNode> iterator = root.get("items").iterator();

                                            // loop thru the returned member details to mark
                                            // replacements that are
                                            // already members
                                            while (iterator != null && iterator.hasNext()) {

                                                final JsonNode conceptNode = iterator.next();
                                                replacementThatAreMembers.add(conceptNode.get("conceptId").asText());
                                            }

                                        }

                                        final List<Concept> conceptsToProcess = new ArrayList<>();
                                        int i = 0;

                                        for (final Concept concept : replacementConceptsToLookup) {

                                            conceptsToProcess.add(concept);
                                            i++;

                                            if (conceptsToProcess.size() == RefsetMemberService.CONCEPT_DESCRIPTIONS_PER_CALL
                                                || i == replacementConceptsToLookup.size()) {

                                                SnowstormDescription.populateAllLanguageDescriptions(upgradeRefset, conceptsToProcess);
                                                conceptsToProcess.clear();
                                            }

                                        }

                                        for (final Concept replacementConcept : replacementConceptsToLookup) {

                                            final UpgradeReplacementConcept upgradeReplacementConcept = new UpgradeReplacementConcept();
                                            upgradeReplacementConcept.setCode(replacementConcept.getCode());
                                            upgradeReplacementConcept.setReason(reasonMap.get(replacementConcept.getCode()));
                                            upgradeReplacementConcept.setActive(replacementConcept.isActive());

                                            if (replacementThatAreMembers.contains(replacementConcept.getCode())) {

                                                upgradeReplacementConcept.setExistingMember(true);
                                            }

                                            if (conceptNode.get("descriptions") != null) {

                                                upgradeReplacementConcept.setDescriptions(ModelUtility.toJson(replacementConcept.getDescriptions()));
                                            }

                                            threadService.add(upgradeReplacementConcept);
                                            LOG.debug("compileUpgradeData added Replacement Concept: " + upgradeReplacementConcept);
                                            inactiveConcept.getReplacementConcepts().add(upgradeReplacementConcept);
                                        }

                                        threadService.add(inactiveConcept);
                                        LOG.debug("compileUpgradeData added inactive Concept: " + inactiveConcept);
                                    } catch (final Exception e) {

                                        throw new RuntimeException(e);
                                    }

                                }
                            });

                        } else {

                            service.add(inactiveConcept);
                            LOG.debug("compileUpgradeData added inactive Concept with no replacement: " + inactiveConcept);
                        }

                    }

                }

            }

        }

        executor.shutdown();
        executor.awaitTermination(600, TimeUnit.SECONDS);

        return status;
    }

    /**
     * Modify upgrade concept.
     *
     * @param service the service
     * @param user the user
     * @param refset the refset
     * @param inactiveConceptId the inactive concept id
     * @param replacementConceptId the replacement concept id
     * @param manualReplacementConcept the manual replacement concept
     * @param changed the changed
     * @return the string
     * @throws Exception the exception
     */
    public static String modifyUpgradeConcept(final TerminologyService service, final User user, final Refset refset, final String inactiveConceptId,
        final String replacementConceptId, final UpgradeReplacementConcept manualReplacementConcept, final String changed) throws Exception {

        try {

            RefsetMemberService.REFSETS_BEING_UPDATED.add(refset.getId());

            List<String> unchangedConcepts = new ArrayList<>();
            final List<String> replacementChangeStatuses =
                Arrays.asList(RefsetMemberService.REPLACEMENT_REMOVED, RefsetMemberService.REPLACEMENT_ADDED, RefsetMemberService.REMOVED_MANUAL_REPLACEMENT);
            boolean add = true;
            String changeText = "added";
            boolean memberChange = true;
            boolean removeInactiveAlso = false;
            final UpgradeInactiveConcept upgradeInactiveConcept = RefsetMemberService.getUpgradeConcept(service, user, refset, inactiveConceptId);
            UpgradeReplacementConcept upgradeReplacementConcept = null;
            String conceptIdToChange = inactiveConceptId;

            if (changed.contains("REMOVED")) {

                add = false;
                changeText = "removed";
            }

            // if the operation needs it get the stored replacement concept
            if (replacementChangeStatuses.contains(changed)) {

                for (final UpgradeReplacementConcept replacementConcept : upgradeInactiveConcept.getReplacementConcepts()) {

                    if (replacementConcept.getCode().equals(replacementConceptId)) {

                        upgradeReplacementConcept = replacementConcept;
                        conceptIdToChange = replacementConceptId;

                        if (upgradeInactiveConcept.isStillMember() && changed.equals(RefsetMemberService.REPLACEMENT_ADDED)) {

                            removeInactiveAlso = true;

                        } else if (changed.equals(RefsetMemberService.REMOVED_MANUAL_REPLACEMENT) && !upgradeReplacementConcept.isAdded()) {

                            memberChange = false;
                        }

                    }

                }

            } else if (changed.equals(RefsetMemberService.NEW_MANUAL_REPLACEMENT)) {

                upgradeReplacementConcept = manualReplacementConcept;
                memberChange = false;
                conceptIdToChange = upgradeReplacementConcept.getCode();

                // save the replacement concept and add it to the inactive concept
                service.add(upgradeReplacementConcept);
                upgradeInactiveConcept.getReplacementConcepts().add(upgradeReplacementConcept);
            }

            LOG.debug("modifyUpgradeConcept: member change: " + conceptIdToChange);

            // add or remove the concept as a member to the refset
            if (memberChange) {

                RefsetMemberService.REFSETS_UPDATED_MEMBERS.put(refset.getId(), new HashMap<>());

                if (add) {

                    unchangedConcepts = RefsetMemberService.addRefsetMembers(service, user, refset, new ArrayList<>(Arrays.asList(conceptIdToChange)));

                    if (removeInactiveAlso && !unchangedConcepts.isEmpty()) {

                        removeInactiveAlso = false;

                    } else if (removeInactiveAlso && unchangedConcepts.isEmpty()) {

                        unchangedConcepts.addAll(RefsetMemberService.removeRefsetMembers(service, user, refset, inactiveConceptId));
                    }

                } else {

                    unchangedConcepts = RefsetMemberService.removeRefsetMembers(service, user, refset, conceptIdToChange);
                }

                // see if there the concept was unable to be changed and craft the error
                // message
                if (unchangedConcepts.size() > 0) {

                    return "The concept " + unchangedConcepts.get(0) + " was unable to be " + changeText;
                }

            }

            if (changed.equals(RefsetMemberService.INACTIVE_REMOVED)) {

                upgradeInactiveConcept.setStillMember(false);

            } else if (replacementChangeStatuses.contains(changed)) {

                if (upgradeReplacementConcept == null) {

                    throw new Exception("Unable to find replacement concept code");
                }

                if (memberChange) {

                    if (add) {

                        upgradeInactiveConcept.setReplaced(add);
                        LOG.debug("modifyUpgradeConcept: inactive concept marked as replaced = " + add);
                    } else {

                        boolean isStillReplaced = false;

                        for (final UpgradeReplacementConcept replacementData : upgradeInactiveConcept.getReplacementConcepts()) {

                            if (upgradeReplacementConcept.getId().equals(replacementData.getId())) {

                                continue;
                            }

                            if (replacementData.isAdded() || replacementData.isExistingMember()) {

                                isStillReplaced = true;
                            }

                        }

                        if (!isStillReplaced) {

                            upgradeInactiveConcept.setReplaced(add);
                            LOG.debug("modifyUpgradeConcept: inactive concept marked as replaced = " + add);
                        }

                    }

                    if (removeInactiveAlso && !unchangedConcepts.contains(inactiveConceptId)) {

                        upgradeInactiveConcept.setStillMember(false);
                    }

                    // If a replacement was successfully added for the first time then
                    // populate the
                    // member ID
                    if (changed.equals(RefsetMemberService.REPLACEMENT_ADDED) && unchangedConcepts.size() == 0) {

                        // when searching for members we only want concepts whose membership
                        // is active
                        // (though the concept itself can be inactive)
                        final String url = SnowstormConnection.getBaseUrl() + refset.getBranchPath() + "/members?referenceSet=" + refset.getRefsetId()
                            + "&active=true&referencedComponentId=" + conceptIdToChange;

                        LOG.debug("modifyUpgradeConcept Member list URL: " + url);

                        try (final Response response = SnowstormConnection.getResponse(url, SnowstormConnection.DEFAULT_ACCECPT_LANGUAGES)) {

                            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                                throw new Exception("call to url '" + url + "' wasn't successful. Status: " + response.getStatus() + " Message: "
                                    + response.getStatusInfo().getReasonPhrase());
                            }

                            final ObjectMapper mapper = new ObjectMapper();
                            final String resultString = response.readEntity(String.class);

                            // Only process payload if Rest call is successful
                            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                                throw new Exception(Integer.toString(response.getStatus()));
                            }

                            final JsonNode root = mapper.readTree(resultString.toString());
                            final Iterator<JsonNode> iterator = root.get("items").iterator();

                            if (iterator.hasNext()) {

                                final JsonNode conceptNode = iterator.next();

                                if (conceptNode.get("referencedComponentId").asText().equals(conceptIdToChange)) {

                                    upgradeReplacementConcept.setMemberId(conceptNode.get("memberId").asText());
                                } else {

                                    throw new Exception("There was a problem getting the member ID");
                                }

                            }

                        }

                    }

                }

                // save or remove the replacement concept
                if (changed.equals(RefsetMemberService.REMOVED_MANUAL_REPLACEMENT)) {

                    upgradeInactiveConcept.getReplacementConcepts().remove(upgradeReplacementConcept);
                    service.remove(upgradeReplacementConcept);
                    LOG.debug("modifyUpgradeConcept: removed the replacement concept: " + conceptIdToChange);
                } else {

                    upgradeReplacementConcept.setAdded(add);
                    service.update(upgradeReplacementConcept);
                    LOG.debug("modifyUpgradeConcept: updated the replacement concept: " + conceptIdToChange);
                }

            } else if (changed.equals(RefsetMemberService.INACTIVE_ADDED)) {

                upgradeInactiveConcept.setStillMember(true);
            }

            // save the inactive concept
            service.update(upgradeInactiveConcept);

            // if this is adding or removing a replacement concept make the same
            // changes to
            // any duplicate concepts in the upgrade data
            if (Arrays.asList(RefsetMemberService.REPLACEMENT_REMOVED, RefsetMemberService.REPLACEMENT_ADDED).contains(changed)) {

                final ResultList<UpgradeInactiveConcept> upgradeData = RefsetMemberService.getUpgradeData(service, user, refset);

                for (final UpgradeInactiveConcept inactiveData : upgradeData.getItems()) {

                    if (upgradeInactiveConcept.getId().equals(inactiveData.getId())) {

                        continue;
                    }

                    boolean isStillReplaced = false;
                    boolean isReplacementChanging = false;

                    for (final UpgradeReplacementConcept replacementData : inactiveData.getReplacementConcepts()) {

                        // if this is already in the state it would be changed to then skip
                        // to the next
                        // inactive concept
                        if (add == replacementData.isAdded()) {

                            break;
                        }

                        // if this is the same concept as the main replacement being worked
                        // on change it
                        // as well
                        if (upgradeReplacementConcept != null && upgradeReplacementConcept.getCode().equals(replacementData.getCode())) {

                            isReplacementChanging = true;

                            replacementData.setAdded(upgradeReplacementConcept.isAdded());
                            replacementData.setMemberId(upgradeReplacementConcept.getMemberId());
                            service.update(replacementData);
                            LOG.debug("modifyUpgradeConcept: duplicate replacement marked as added = " + add);

                            continue;
                        }

                        if (replacementData.isAdded() || replacementData.isExistingMember()) {

                            isStillReplaced = true;
                        }

                    }

                    // if a replacement is being added or the replacement is being removed
                    // and there
                    // are no other added replacements
                    if ((add && isReplacementChanging) || (!add && isReplacementChanging && !isStillReplaced)) {

                        inactiveData.setReplaced(add);
                        inactiveData.setReplaced(inactiveData.isReplaced());
                        service.update(inactiveData);
                        LOG.debug("modifyUpgradeConcept: inactive concept marked as replaced = " + add);
                    }

                }

            }

            return "All changes made successfully";

        } catch (final Exception e) {

            throw new Exception(e);
        }

        finally {

            RefsetMemberService.REFSETS_BEING_UPDATED.remove(refset.getId());
        }
    }

    // // TEMPORARY//
    // private static String getICD10NOName(String code) throws Exception {
    // if (icd10noCodeToName.isEmpty()) {
    // cacheICD10NONames();
    // }
    // String ICD10NOName = icd10noCodeToName.get(code);
    // if (ICD10NOName == null || ICD10NOName.isBlank()) {
    // ICD10NOName = code + " CONCEPT NOT FOUND";
    // }
    // return ICD10NOName;
    // }
    //
    // // TEMPORARY//
    // private static void cacheICD10NONames() throws Exception {
    //
    // String dataDir = PropertyUtility.getProperty("terminology.handler.SNOMED_SNOWSTORM.dir");
    //
    // final File f = new File(dataDir + "/ICD10NO_concepts.txt");
    // if (!f.exists()) {
    // LOG.error("ICD10NO file doesn't exist: " + f.getPath());
    // return;
    // }
    //
    // try (BufferedReader br = new BufferedReader(new FileReader(f.getPath()))) {
    // String line;
    // while ((line = br.readLine()) != null) {
    // String[] parts = line.split("\\|", 2); // Split the line into two parts
    // // at the first occurrence of '|'
    // if (parts.length >= 2) {
    // String key = parts[0].trim();
    // String value = parts[1].trim();
    // icd10noCodeToName.put(key, value);
    // } else {
    // System.out.println("Ignoring malformed line: " + line);
    // }
    // }
    // } catch (IOException e) {
    // e.printStackTrace();
    // }
    // }

    /**
     * Gets the ICPC 2 NO name.
     *
     * @param code the code
     * @return the ICPC 2 NO name
     * @throws Exception the exception
     */
    // TEMPORARY//
    private static String getICPC2NOName(String code) throws Exception {

        if (icpc2noCodeToName.isEmpty()) {
            cacheICPC2NONames();
        }
        String ICPC2NOName = icpc2noCodeToName.get(code);
        if (ICPC2NOName == null || ICPC2NOName.isBlank()) {
            ICPC2NOName = code + " CONCEPT NOT FOUND";
        }
        return ICPC2NOName;
    }

    /**
     * Cache ICPC 2 NO names.
     *
     * @throws Exception the exception
     */
    // TEMPORARY//
    private static void cacheICPC2NONames() throws Exception {

        String dataDir = PropertyUtility.getProperty("terminology.handler.SNOMED_SNOWSTORM.dir");

        final File f = new File(dataDir + "/ICPC2NO_concepts.txt");
        if (!f.exists()) {
            LOG.error("ICPC2NO file doesn't exist: " + f.getPath());
            return;
        }

        try (BufferedReader br = new BufferedReader(new FileReader(f.getPath()))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split("\\|", 2); // Split the line into two parts
                                                       // at the first occurrence of '|'
                if (parts.length >= 2) {
                    String key = parts[0].trim();
                    String value = parts[1].trim();
                    icpc2noCodeToName.put(key, value);
                } else {
                    System.out.println("Ignoring malformed line: " + line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Gets the concept by code fhir api.
     *
     * @param terminology the terminology
     * @param version the version
     * @param code the code
     * @return the concept by code fhir api
     * @throws Exception the exception
     */
    public static Concept getConceptByCodeFhirApi(final String terminology, final String version, final String code) throws Exception {

        if (StringUtils.isAnyBlank(terminology, version, code)) {
            throw new Exception("Terminology, version and code are required parameters. Must not be null or empty.");
        }

        getCodeSystemsFromFhir();

        final String terminologyCacheKey = getTerminologyCacheKey(terminology, version);

        final boolean exists = CachingUtility.containsObjects(SNOWSTORM_TERMINOLOGY_CACHE, terminologyCacheKey);

        if (exists) {

            @SuppressWarnings("rawtypes")
            final Optional<List> list = CachingUtility.getObject(SNOWSTORM_TERMINOLOGY_CACHE, terminologyCacheKey, List.class);
            @SuppressWarnings("unchecked")
            final List<Concept> terminologyConcepts = list.get();

            return terminologyConcepts.stream().filter(concept -> concept.getCode().equals(code)).findFirst().orElse(null);

        }

        return null;
    }

    /**
     * Find concepts fhir api.
     *
     * @param terminology the terminology
     * @param version the version
     * @param searchParameters the search parameters
     * @return the result list concept
     * @throws Exception the exception
     */
    private static ResultListConcept findConceptsFhirApi(final String terminology, final String version, final SearchParameters searchParameters)
        throws Exception {

        if (StringUtils.isAnyBlank(terminology, version)) {
            throw new Exception("Terminology and version are required parameters. Must not be null or empty.");
        }

        if (searchParameters == null) {
            throw new Exception("searchParameters is required parameter. Must not be null.");
        }

        // get concept list from cache, used fhircodesystem as key
        getCodeSystemsFromFhir();
        final SnowstormFhirCodeSystem snowstormFhirCodeSystem = codeSystems.get(terminology + "_" + version);
        final String terminologyCacheKey = getTerminologyCacheKey(terminology, version);

        final boolean exists = CachingUtility.containsObjects(SNOWSTORM_TERMINOLOGY_CACHE, terminologyCacheKey);

        if (!exists) {
            cacheSnowstormConceptsFhirApi(snowstormFhirCodeSystem);
        }

        return findConceptsFromCache(terminologyCacheKey, searchParameters);

    }

    /**
     * Find concepts.
     *
     * @param cacheKey the cache key
     * @param searchParameters the search parameters
     * @return the result list concept
     */
    private static ResultListConcept findConceptsFromCache(final String cacheKey, final SearchParameters searchParameters) {

        final long start = System.currentTimeMillis();
        final String query = searchParameters.getQuery();

        @SuppressWarnings("rawtypes")
        final Optional<List> list = CachingUtility.getObject(SNOWSTORM_TERMINOLOGY_CACHE, cacheKey, List.class);
        @SuppressWarnings("unchecked")
        final List<Concept> terminologyConcepts = list.get();
        LOG.debug("findConcepts: terminologyCacheKey: {}, list size:", cacheKey, terminologyConcepts.size());

        List<Concept> matchingConcepts = null;
        if (query.toLowerCase().contains("code:")) {
            final String code = query.replace("code:", "").trim();
            matchingConcepts = terminologyConcepts.stream().filter(concept -> concept.getCode().startsWith(code)).collect(Collectors.toList());
            matchingConcepts.sort(Comparator.comparing(Concept::getCode));

        } else if (query.toLowerCase().contains("name:")) {
            final String name = query.replace("name:", "").trim();
            matchingConcepts = terminologyConcepts.stream()
                .filter(concept -> (concept.getName() != null && concept.getName().toLowerCase().contains(name.toLowerCase()))).collect(Collectors.toList());
            matchingConcepts.sort(Comparator.comparing(Concept::getName));
        } else {
            matchingConcepts = terminologyConcepts.stream().filter(
                concept -> (concept.getName() != null && concept.getName().toLowerCase().contains(query.toLowerCase())) || concept.getCode().startsWith(query))
                .collect(Collectors.toList());
            matchingConcepts.sort(Comparator.comparing(Concept::getName));
        }

        // apply offset and limit from search parameters
        final List<Concept> matchingConceptsPage = new ArrayList<>();
        if (searchParameters.getOffset() != null) {
            for (; searchParameters.getOffset() < matchingConcepts.size() && matchingConceptsPage.size() < searchParameters.getLimit(); searchParameters
                .setOffset(searchParameters.getOffset() + 1)) {
                matchingConceptsPage.add(matchingConcepts.get(searchParameters.getOffset()));
            }
        }

        final ResultListConcept results = new ResultListConcept();
        results.setItems(matchingConceptsPage);
        results.setParameters(searchParameters);
        results.setTotal(matchingConcepts.size());
        results.setOffset(searchParameters.getOffset());

        LOG.info("findConcepts took: " + (System.currentTimeMillis() - start) + " ms for cacheKey: " + cacheKey + ", query: " + query);
        return results;

    }

    /**
     * Gets the all concepts by code system fhir api.
     *
     * @param codeSystem the code system
     * @return the all concepts by code system fhir api
     * @throws Exception the exception
     */
    private static void cacheSnowstormConceptsFhirApi(final SnowstormFhirCodeSystem codeSystem) throws Exception {

        if (codeSystem == null) {
            throw new Exception("SnowstormFhirCodeSystem is required parameter. Must not be null or empty.");
        }

        final long start = System.currentTimeMillis();

        final String terminology = codeSystem.getName();
        final String version = codeSystem.getVersion();

        final List<Concept> concepts = new ArrayList<>();
        final int fetchSize = 10000;
        int offset = 0;
        boolean moreToFetch = true;

        final String terminologyCacheKey = getTerminologyCacheKey(terminology, version);

        // example: https://host:port/fhir/ValueSet/$expand?filter=Annen&offset=0&count=10
        // &url=https%3A%2F%2Ffat.terminologi.ehelse.no%2Findex.html%23%2Ficd10no%3Ffhir_vs&_format=json
        final String encodedUrl = URLEncoder.encode(codeSystem.getUrl().concat("?fhir_vs"), StandardCharsets.UTF_8);

        while (moreToFetch) {

            final String targetUri = SnowstormConnection.getBaseUrl().concat("fhir/ValueSet/$expand?").concat("offset=").concat(String.valueOf(offset))
                .concat("&count=").concat(String.valueOf(fetchSize)).concat("&url=").concat(encodedUrl).concat("&_format=json");

            LOG.info("getFhirConceptByName url: " + targetUri);
            try (final Response response = SnowstormConnection.getResponse(targetUri)) {

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                    throw new Exception(
                        "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
                }

                final String resultString = response.readEntity(String.class);
                response.close();

                final ObjectMapper mapper = new ObjectMapper();
                final JsonNode root = mapper.readTree(resultString);
                final JsonNode expansionNode = root.get("expansion");

                final int totalConcepts = expansionNode.get("total").asInt();

                if (totalConcepts == 0) {
                    moreToFetch = false;
                }

                final JsonNode containsNode = expansionNode.get("contains");

                for (final JsonNode conceptNode : containsNode) {

                    final Concept concept = new Concept();
                    concept.setId(conceptNode.get("code").asText());
                    concept.setCode(conceptNode.get("code").asText());
                    concept.setName(conceptNode.get("display").asText());
                    concept.setTerminology(terminology);
                    concept.setVersion(version);
                    concepts.add(concept);
                    final String cacheKey = getConceptCacheKey(terminology, version, concept.getCode());
                    CachingUtility.cacheObject(SNOWSTORM_CONCEPTS_CACHE, cacheKey, Concept.class, concept);
                }

                offset += fetchSize;
                moreToFetch = containsNode.size() == fetchSize;
            }
        }

        CachingUtility.cacheObject(SNOWSTORM_TERMINOLOGY_CACHE, terminologyCacheKey, List.class, concepts);
        LOG.info("cacheConcepts took: " + (System.currentTimeMillis() - start) + " ms for terminology: " + terminology + ", version: " + version);

    }

    /**
     * Gets the concept by name.
     *
     * @param fhirCodeSystem the fhir code system
     * @param name the name
     * @return the concept by name
     * @throws Exception the exception
     */
    public static List<Concept> getConceptsByNameFhirApi(final String fhirCodeSystem, final String name) throws Exception {

        if (StringUtils.isBlank(name)) {
            throw new Exception("Name is required parameter. Must not be null or empty.");
        }

        getCodeSystemsFromFhir();
        final SnowstormFhirCodeSystem codeSystem = codeSystems.get(fhirCodeSystem);
        final String terminology = codeSystem.getName();
        final String version = codeSystem.getVersion();

        final List<Concept> concepts = new ArrayList<>();
        final int fetchSize = 1000;
        int offset = 0;
        boolean moreToFetch = true;

        // example: https://host:port/fhir/ValueSet/$expand?filter=Annen&offset=0&count=10
        // &url=https%3A%2F%2Ffat.terminologi.ehelse.no%2Findex.html%23%2Ficd10no%3Ffhir_vs&_format=json
        final String encodedUrl = URLEncoder.encode(codeSystem.getUrl() + "?fhir_vs", StandardCharsets.UTF_8);

        while (moreToFetch) {

            final String targetUri = SnowstormConnection.getBaseUrl() + "fhir/ValueSet/$expand?filter=" + URLEncoder.encode(name, StandardCharsets.UTF_8)
                + "&offset=" + offset + "&count=" + fetchSize + "&url=" + encodedUrl + "&_format=json";

            LOG.info("getFhirConceptByName url: " + targetUri);
            try (final Response response = SnowstormConnection.getResponse(targetUri)) {

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                    throw new Exception(
                        "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
                }

                final String resultString = response.readEntity(String.class);
                response.close();

                final ObjectMapper mapper = new ObjectMapper();
                final JsonNode root = mapper.readTree(resultString);
                final JsonNode expansionNode = root.get("expansion");

                final int totalConcepts = expansionNode.get("total").asInt();

                if (totalConcepts == 0) {
                    moreToFetch = false;
                    return concepts;
                }

                final JsonNode containsNode = expansionNode.get("contains");

                for (final JsonNode conceptNode : containsNode) {

                    final Concept concept = new Concept();
                    concept.setId(conceptNode.get("code").asText());
                    concept.setCode(conceptNode.get("code").asText());
                    concept.setName(conceptNode.get("display").asText());
                    concept.setTerminology(terminology);
                    concept.setVersion(version);
                    concepts.add(concept);
                }

                offset += fetchSize;
                moreToFetch = containsNode.size() == fetchSize;
            }
        }
        return concepts;
    }

    /**
     * Gets the code systems. Should be called before any other methods.
     *
     * @return the code systems
     * @throws Exception the exception
     */
    private static void getCodeSystemsFromFhir() throws Exception {

        if (codeSystems != null && !codeSystems.isEmpty()) {
            return;
        }
        final String resultString = getCodeSystemsFromFhirApi();
        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode root = mapper.readTree(resultString);
        final JsonNode entryNode = root.get("entry");

        if (entryNode.isArray()) {

            codeSystems.clear();

            for (final JsonNode node : entryNode) {

                final SnowstormFhirCodeSystem codeSystem = new SnowstormFhirCodeSystem();
                codeSystem.setFullUrl(node.get("fullUrl").asText());

                final JsonNode resourceNode = node.get("resource");
                if (resourceNode.has("resourceType"))
                    codeSystem.setResourceType(resourceNode.get("resourceType").asText());

                if (resourceNode.has("id"))
                    codeSystem.setId(resourceNode.get("id").asText());

                if (resourceNode.has("url"))
                    codeSystem.setUrl(resourceNode.get("url").asText());

                if (resourceNode.has("version"))
                    codeSystem.setVersion(resourceNode.get("version").asText());

                if (resourceNode.has("name"))
                    codeSystem.setName(resourceNode.get("name").asText());

                if (resourceNode.has("status"))
                    codeSystem.setStatus(resourceNode.get("status").asText());

                if (resourceNode.has("publisher"))
                    codeSystem.setPublisher(resourceNode.get("publisher").asText());

                if (resourceNode.has("hierarchyMeaning"))
                    codeSystem.setHierarchyMeaning(resourceNode.get("hierarchyMeaning").asText());

                if (resourceNode.has("compositional"))
                    codeSystem.setCompositional(resourceNode.get("compositional").asBoolean());

                if (resourceNode.has("content"))
                    codeSystem.setContent(resourceNode.get("content").asText());

                codeSystems.put(codeSystem.getName() + "_" + codeSystem.getVersion(), codeSystem);
            }
        }
    }

    /**
     * Gets the code systems from FHIR API.
     *
     * @return the code systems
     * @throws Exception the exception
     */
    private static String getCodeSystemsFromFhirApi() throws Exception {

        // https://host:port/fhir/CodeSystem
        final String targetUri = SnowstormConnection.getBaseUrl() + "fhir/CodeSystem?_format=json";
        String resultString = "";

        LOG.info("getCodeSystemsFromApi url: " + targetUri);
        try (final Response response = SnowstormConnection.getResponse(targetUri)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                throw new Exception(
                    "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
            }

            resultString = response.readEntity(String.class);
            response.close();

        }

        return resultString;
    }

    /**
     * Gets the concept cache key.
     *
     * @param terminology the terminology
     * @param version the version
     * @param code the code
     * @return the concept cache key
     */
    private static String getConceptCacheKey(final String terminology, final String version, final String code) {

        return getTerminologyCacheKey(terminology, version) + "_" + code.trim();
    }

    /**
     * Gets the terminology cache key.
     *
     * @param terminology the terminology
     * @param version the version
     * @return the terminology cache key
     */
    private static String getTerminologyCacheKey(final String terminology, final String version) {

        return terminology.trim() + "_" + version.trim();
    }

}
