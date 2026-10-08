/*
 * Copyright 2026 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.handler.snowstorm.SnowstormConcept;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.GenerateAutomapsRequest;
import org.ihtsdo.refsetservice.handler.snowstorm.SnowstormMapping;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapWorkflowStatus;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.model.MappingWorkflow;
import org.ihtsdo.refsetservice.model.ResultListMapping;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ThreadLocalMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Generates unsaved map records from the automap service.
 */
public final class AutomapService {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(AutomapService.class);

    /** Default minimum confidence, matching the automap sample request. */
    static final double DEFAULT_MIN_CONFIDENCE = 0.8d;

    /** Default entity type, matching the automap sample request. */
    static final String DEFAULT_ENTITY_TYPE = "condition";

    /** Concept ids sent to Snowstorm in one search. */
    private static final int CONCEPT_LOOKUP_BATCH_SIZE = 1000;

    /** Maximum pages read for one concept-id batch. */
    private static final int MAX_CONCEPT_PAGES = 20;

    /**
     * Instantiates a new automap service.
     */
    private AutomapService() {

        // n/a
    }

    /**
     * Looks up source terms, asks automap for targets, and returns unsaved map records. Nothing is written to Snowstorm.
     *
     * @param service the terminology service
     * @param mapSetInternalId the map set internal id
     * @param request the request
     * @return the map records for codes that resolved, plus any concept codes that did not
     * @throws Exception the exception
     */
    public static ResultListMapping generateAutomaps(final TerminologyService service, final String mapSetInternalId,
        final GenerateAutomapsRequest request) throws Exception {

        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
        }
        final String mapSetId = StringUtils.trimToEmpty(mapSetInternalId);
        final List<String> conceptCodes = AutomapMappingBuilder.distinctCodes(request.getConceptCodes());
        if (StringUtils.isBlank(mapSetId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mapSetInternalId is required.");
        }
        if (conceptCodes.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conceptCodes is required.");
        }

        final MapSet mapSet = loadMapSet(service, mapSetId);
        final String branch = mapSetBranch(service, mapSet);
        final String fromTerminology = fromTerminology(mapSet);
        if (StringUtils.isBlank(fromTerminology)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Map set " + mapSetId + " does not have a source terminology.");
        }

        final double minConfidence = resolveMinConfidence(request.getMinConfidence());
        final String entityType = resolveEntityType(request.getEntityType());
        final Map<String, String> termsByCode = lookupTerms(branch, fromTerminology, fromVersion(mapSet), conceptCodes);
        final List<String> validCodes = new ArrayList<>();
        final List<String> invalidConceptIds = new ArrayList<>();
        for (final String conceptCode : conceptCodes) {
            if (StringUtils.isBlank(termsByCode.get(conceptCode))) {
                invalidConceptIds.add(conceptCode);
            } else {
                validCodes.add(conceptCode);
            }
        }

        final List<Mapping> mappings;
        if (validCodes.isEmpty()) {
            mappings = new ArrayList<>();
        } else {
            final List<AutomapTerm> automapTerms = new ArrayList<>();
            for (final String conceptCode : validCodes) {
                automapTerms.add(new AutomapTerm(termsByCode.get(conceptCode), entityType));
            }
            LOG.info("generateAutomaps branch={} mapSet={} concepts={} unresolved={} toTerminology={} entityType={} minConfidence={}", branch,
                mapSetId, validCodes.size(), invalidConceptIds.size(), AutomapTerm.TO_TERMINOLOGY, entityType, minConfidence);
            final JsonNode automapTask = AutomapClient.mapTerms(automapTerms, minConfidence);
            mappings = AutomapMappingBuilder.buildMappings(mapSet, validCodes, termsByCode, automapTask);
            SnowstormMapping.attachDescriptions(branch, mappings, fromTerminology, AutomapTerm.TO_TERMINOLOGY);
            MapNoteService.attachNotes(service, mapSet, mappings);
            attachReviewNeeded(mapSet, mappings);
        }

        final ResultListMapping result = new ResultListMapping(mappings);
        result.setTotalKnown(true);
        if (!invalidConceptIds.isEmpty()) {
            result.setInvalidConceptIds(invalidConceptIds);
            LOG.info("generateAutomaps unresolved concepts mapSet={} codes={}", mapSetId, invalidConceptIds);
        }
        LOG.info("generateAutomaps complete mapSet={} returned={} unresolved={}", mapSetId, mappings.size(), invalidConceptIds.size());
        return result;
    }

    /**
     * Resolves the minimum confidence.
     *
     * @param requested the requested value, or null for the default
     * @return the minimum confidence
     */
    static double resolveMinConfidence(final Double requested) {

        final double value;
        if (requested != null) {
            value = requested;
        } else {
            final String configured = AutomapClient.configured("automap.minConfidence");
            if (configured == null) {
                value = DEFAULT_MIN_CONFIDENCE;
            } else {
                try {
                    value = Double.parseDouble(configured);
                } catch (final NumberFormatException e) {
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "automap.minConfidence is not a number.");
                }
            }
        }
        if (value < 0d || value > 1d) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minConfidence must be between 0 and 1.");
        }
        return value;
    }

    /**
     * Resolves the automap entity type.
     *
     * @param requested the requested value, or blank for the default
     * @return the entity type
     */
    static String resolveEntityType(final String requested) {

        if (StringUtils.isNotBlank(requested)) {
            return requested.trim();
        }
        final String configured = AutomapClient.configured("automap.entityType");
        return configured == null ? DEFAULT_ENTITY_TYPE : configured;
    }

    /**
     * Attaches an unsaved workflow with status {@link MapWorkflowStatus#REVIEW_NEEDED}. No workflow row is written.
     *
     * @param mapSet the map set
     * @param mappings the mappings
     */
    private static void attachReviewNeeded(final MapSet mapSet, final List<Mapping> mappings) {

        for (final Mapping mapping : mappings) {
            if (mapping == null || StringUtils.isBlank(mapping.getCode())) {
                continue;
            }
            final MappingWorkflow workflow = new MappingWorkflow();
            workflow.setSourceConceptCode(mapping.getCode());
            workflow.setWorkflowStatus(MapWorkflowStatus.REVIEW_NEEDED);
            workflow.setSpecialistSlot(1);
            workflow.setMapSet(mapSet);
            mapping.setMappingWorkflow(workflow);
        }
    }

    /**
     * Loads the map set by internal id.
     *
     * @param service the terminology service
     * @param mapSetInternalId the map set internal id
     * @return the map set
     */
    private static MapSet loadMapSet(final TerminologyService service, final String mapSetInternalId) {

        try {
            return MapSetService.getMapSet(service, mapSetInternalId);
        } catch (final ResponseStatusException e) {
            throw e;
        } catch (final Exception e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Map set not found for mapSetInternalId " + mapSetInternalId + ".", e);
        }
    }

    /**
     * Returns the Snowstorm branch used to read this map set, matching map-set mapping lookup.
     *
     * @param service the terminology service
     * @param mapSet the map set
     * @return the branch
     * @throws Exception the exception
     */
    private static String mapSetBranch(final TerminologyService service, final MapSet mapSet) throws Exception {

        String branch = BranchService.getMapSetBranchPath(mapSet);
        if (StringUtils.isBlank(branch)) {
            branch = MapSetService.resolveBranchFromMapSets(service, mapSet.getRefSetCode());
        }
        if (StringUtils.isBlank(branch) || "empty".equals(branch) || "none".equals(branch)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "map_sets.branchPath is required for map set " + mapSet.getRefSetCode()
                + ". Database is missing required path data.");
        }
        return branch;
    }

    /**
     * Returns the source terminology.
     *
     * @param mapSet the map set
     * @return the source terminology
     */
    private static String fromTerminology(final MapSet mapSet) {

        if (StringUtils.isNotBlank(mapSet.getFromTerminology())) {
            return mapSet.getFromTerminology().trim();
        }
        final MapProject mapProject = mapSet.getMapProject();
        return mapProject == null ? "" : StringUtils.trimToEmpty(mapProject.getSourceTerminology());
    }

    /**
     * Returns the source terminology version.
     *
     * @param mapSet the map set
     * @return the source version
     */
    private static String fromVersion(final MapSet mapSet) {

        if (StringUtils.isNotBlank(mapSet.getFromVersion())) {
            return mapSet.getFromVersion().trim();
        }
        final MapProject mapProject = mapSet.getMapProject();
        return mapProject == null ? "" : StringUtils.trimToEmpty(mapProject.getSourceTerminologyVersion());
    }

    /**
     * Looks up a preferred term for each concept code.
     *
     * @param branch the branch
     * @param fromTerminology the source terminology
     * @param fromVersion the source version
     * @param conceptCodes the concept codes
     * @return term by concept code
     * @throws Exception the exception
     */
    private static Map<String, String> lookupTerms(final String branch, final String fromTerminology, final String fromVersion,
        final List<String> conceptCodes) throws Exception {

        final Map<String, String> termsByCode =
            isSnomed(fromTerminology) ? lookupSnomedTerms(branch, conceptCodes) : lookupOtherTerms(fromTerminology, fromVersion, conceptCodes);
        return termsByCode;
    }

    /**
     * True when the terminology is SNOMED CT.
     *
     * @param terminology the terminology
     * @return true when the terminology is SNOMED CT
     */
    private static boolean isSnomed(final String terminology) {

        return StringUtils.isNotBlank(terminology) && terminology.trim().toUpperCase().startsWith("SNOMED");
    }

    /**
     * Looks up SNOMED CT preferred terms on the supplied branch.
     *
     * @param branch the branch
     * @param conceptCodes the concept codes
     * @return term by concept code
     * @throws Exception the exception
     */
    private static Map<String, String> lookupSnomedTerms(final String branch, final List<String> conceptCodes) throws Exception {

        final Map<String, String> termsByCode = new LinkedHashMap<>();
        for (int start = 0; start < conceptCodes.size(); start += CONCEPT_LOOKUP_BATCH_SIZE) {
            final List<String> batch = conceptCodes.subList(start, Math.min(start + CONCEPT_LOOKUP_BATCH_SIZE, conceptCodes.size()));
            String searchAfter = null;
            for (int page = 0; page < MAX_CONCEPT_PAGES; page++) {
                final JsonNode result = searchConcepts(branch, batch, searchAfter);
                final JsonNode items = result.get("items");
                final int returned = items == null || !items.isArray() ? 0 : items.size();
                if (returned == 0) {
                    break;
                }
                for (final JsonNode item : items) {
                    final String code = AutomapMappingBuilder.conceptCode(item);
                    final String term = AutomapMappingBuilder.termFromConceptNode(item);
                    if (StringUtils.isNoneBlank(code, term)) {
                        termsByCode.put(code, term);
                    }
                }
                searchAfter = result.hasNonNull("searchAfter") ? result.get("searchAfter").asText() : "";
                if (StringUtils.isBlank(searchAfter) || termsByCode.keySet().containsAll(batch)) {
                    break;
                }
            }
        }
        return termsByCode;
    }

    /**
     * Looks up non-SNOMED terms through the terminology concept API.
     *
     * @param terminology the terminology
     * @param version the version
     * @param conceptCodes the concept codes
     * @return term by concept code
     * @throws Exception the exception
     */
    private static Map<String, String> lookupOtherTerms(final String terminology, final String version, final List<String> conceptCodes) throws Exception {

        if (StringUtils.isBlank(version)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Source terminology version is required to look up terms for " + terminology + ".");
        }
        final Map<String, String> termsByCode = new LinkedHashMap<>();
        for (final String conceptCode : conceptCodes) {
            final Concept concept = SnowstormConcept.getConcept(terminology, version, conceptCode);
            if (concept == null) {
                continue;
            }
            final String term = StringUtils.isNotBlank(concept.getName()) ? concept.getName() : concept.getFsn();
            if (StringUtils.isNotBlank(term)) {
                termsByCode.put(conceptCode, term.trim());
            }
        }
        return termsByCode;
    }

    /**
     * Searches Snowstorm concepts by id.
     *
     * @param branch the branch
     * @param conceptCodes the concept codes
     * @param searchAfter the search after token, or null
     * @return the search page
     * @throws Exception the exception
     */
    private static JsonNode searchConcepts(final String branch, final List<String> conceptCodes, final String searchAfter) throws Exception {

        final ObjectNode body = ThreadLocalMapper.get().createObjectNode();
        final ArrayNode ids = body.putArray("conceptIds");
        for (final String conceptCode : conceptCodes) {
            ids.add(conceptCode);
        }
        body.put("limit", conceptCodes.size());
        if (StringUtils.isNotBlank(searchAfter)) {
            body.put("searchAfter", searchAfter);
        }
        final String url = conceptsSearchUrl(branch);
        LOG.info("automap concept lookup POST {} concepts={}", url, conceptCodes.size());
        try (final Response response = SnowstormConnection.postResponse(url, body.toString())) {
            final String result = SnowstormConnection.readEntityAsString(response);
            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Concept lookup failed for branch " + branch + ". Status " + response.getStatus() + ".");
            }
            return ThreadLocalMapper.get().readTree(result);
        }
    }

    /**
     * Returns the Snowstorm concepts search URL for a branch.
     *
     * @param branch the branch
     * @return the URL
     */
    private static String conceptsSearchUrl(final String branch) {

        String base = SnowstormConnection.getRestBaseUrl();
        if (StringUtils.isBlank(base) || "none".equalsIgnoreCase(base.trim())) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Snowstorm REST base URL is not configured.");
        }
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        final String normalizedBranch = branch.startsWith("/") ? branch.substring(1) : branch;
        return base + normalizedBranch + "/concepts/search";
    }
}
