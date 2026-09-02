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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.AuditEntry;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.Description;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.MapEntry;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.model.MappingExportRequest;
import org.ihtsdo.refsetservice.model.ResultListMapping;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.enums.VersionStatus;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.ihtsdo.refsetservice.util.FileUtility;
import org.ihtsdo.refsetservice.util.LocalException;
import org.ihtsdo.refsetservice.util.MapEntryUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.ihtsdo.refsetservice.util.ThreadLocalMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The Class SnowstormMapping.
 */
public class SnowstormMapping extends SnowstormAbstract {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SnowstormMapping.class);

    /** The Constant DEFAULT_ACCEPT. */
    private static final String DEFAULT_ACCEPT = MediaType.APPLICATION_JSON;

    /** The client. */
    private static ThreadLocal<Client> clients = new ThreadLocal<>() {

        @Override
        public Client initialValue() {

            return SnowstormConnection.getClient();
        }
    };

    /**
     * Returns the clients.
     *
     * @return the clients
     */
    private static ThreadLocal<Client> getClients() {

        return clients;
    }

    /**
     * Gets the map sets.
     *
     * @param branch the branch
     * @return the map sets
     * @throws Exception the exception
     */
    public static List<MapSet> getMapSets(final String branch) throws Exception {

        final ArrayList<MapSet> mapSets = new ArrayList<>();

        // Connect to snowstorm
        final Client client = getClients().get();

        String searchAfter = null;

        final int limit = 50;

        final String targetUri =
            SnowstormConnection.getBaseUrl() + branch + "/concepts?activeFilter=true&ecl=%3C609331003&includeLeafFlag=false&form=inferred&offset=0&limit="
                + limit + (searchAfter != null ? "&searchAfter=" + searchAfter : "");
        LOG.info("getSnowstormMapsets url: " + targetUri);

        final WebTarget target = client.target(targetUri);
        final Response response = target.request(DEFAULT_ACCEPT)
            // .header("Cookie", ConfigUtility.getGenericUserCookie())
            .get();
        if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
            final String body = SnowstormConnection.readEntityAsStringSafe(response);
            final String message = StringUtils.isNotBlank(body) ? SnowstormAbstract.formatErrorMessageFromBody(body) : "(response body not available)";
            throw new Exception(
                "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + message);
        }
        final String resultString = SnowstormConnection.readEntityAsString(response);

        final JsonNode doc = ThreadLocalMapper.get().readTree(resultString);
        final JsonNode mappingsBatch = doc.get("items");

        final Iterator<JsonNode> itemIterator = mappingsBatch.iterator();

        // parse items to retrieve matching concept
        while (itemIterator.hasNext()) {

            final JsonNode mapSetNode = itemIterator.next();

            // TEMPORARY - only keep ICD10NO (447562003) and ICPC2NO (68101000202102)
            // maps//
            final String refsetId = mapSetNode.get("conceptId").asText();
            if (!(refsetId.equals("447562003") || refsetId.equals("68101000202102"))) {
                continue;
            }
            // TEMPORARY//

            final MapSet mapSet = new MapSet();
            mapSet.setRefSetCode(mapSetNode.get("conceptId").asText());
            mapSet.setModuleId(mapSetNode.get("moduleId").asText());

            // Set refset name to FSN if it exists, defaulting to PT if not.
            if (mapSetNode.has("pt")) {
                mapSet.setRefSetName(mapSetNode.get("pt").get("term").asText());
            }

            if (mapSetNode.has("fsn") && mapSetNode.get("fsn").has("term")) {
                mapSet.setRefSetName(mapSetNode.get("fsn").get("term").asText());
            }

            final JsonNode additionalFields = mapSetNode.get("additionalFields");
//            deriveBranchAndVersionFromBranch(mapSet, branch);
            mapSet.setVersionStatus(VersionStatus.PUBLISHED);
            mapSet.setVersion(mapSet.getFromVersion());
            // mapSet.setModified(new SimpleDateFormat("yyyy-MM-dd").parse(mapSet.getFromVersion()));
            if (mapSetNode.has("effectiveTime") && !mapSetNode.get("effectiveTime").isNull()) {
                mapSet.setModified(new SimpleDateFormat("yyyyMMdd").parse(mapSetNode.get("effectiveTime").asText()));
            }

            mapSets.add(mapSet);
        }

        return mapSets;
    }

    /**
     * Gets the map set.
     *
     * @param branch the branch
     * @param code the code
     * @return the map set
     * @throws Exception the exception
     */
    public static MapSet getMapSet(final String branch, final String code) throws Exception {

        final Client client = getClients().get();

        final SearchParameters searchParameters = new SearchParameters();
        searchParameters.setLimit(50);
        searchParameters.setSearchAfter(null);

        final String targetUri = SnowstormConnection.getBaseUrl() + branch + "/concepts?activeFilter=true&includeLeafFlag=false&form=inferred&conceptIds="
            + code + SnowstormApiPaging.getPagingQueryString(null);

        LOG.info("getSnowstormMapset url: {}", targetUri);

        final WebTarget target = client.target(targetUri);

        final Response response = target.request(DEFAULT_ACCEPT)
            // .header("Cookie", ConfigUtility.getGenericUserCookie())
            .get();
        final String resultString = SnowstormConnection.readEntityAsString(response);
        if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
            throw new Exception(
                "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
        }

        final JsonNode doc = ThreadLocalMapper.get().readTree(resultString);
        final JsonNode mapSetsBatch = doc.get("items");
        final Iterator<JsonNode> itemIterator = mapSetsBatch.iterator();

        // parse items to retrieve matching concept
        while (itemIterator.hasNext()) {

            final JsonNode mapSetNode = itemIterator.next();

            final MapSet mapSet = new MapSet();
            mapSet.setRefSetCode(mapSetNode.get("conceptId").asText());
            mapSet.setModuleId(mapSetNode.get("moduleId").asText());

            // Set refset name to FSN if it exists, defaulting to PT if not.
            if (mapSetNode.has("pt")) {
                mapSet.setRefSetName(mapSetNode.get("pt").get("term").asText());
            }

            if (mapSetNode.has("fsn") && mapSetNode.get("fsn").has("term")) {
                mapSet.setRefSetName(mapSetNode.get("fsn").get("term").asText());
            }

            final JsonNode additionalFields = mapSetNode.get("additionalFields");
//             deriveBranchAndVersionFromBranch(mapSet, branch);
            mapSet.setVersionStatus(VersionStatus.PUBLISHED);
            mapSet.setVersion(mapSet.getFromVersion());
            // mapSet.setModified(new SimpleDateFormat("yyyy-MM-dd").parse(mapSet.getFromVersion()));
            if (mapSetNode.has("effectiveTime") && !mapSetNode.get("effectiveTime").isNull()) {
                mapSet.setModified(new SimpleDateFormat("yyyyMMdd").parse(mapSetNode.get("effectiveTime").asText()));
            }

            return mapSet;
        }

        return null;
    }

    /**
     * Gets the mappings.
     *
     * @param branch the branch
     * @param mapSetCode the map set code
     * @param searchParameters the search parameters
     * @param filter the filter
     * @param showOverriddenEntries the show overridden entries
     * @param conceptCodes the concept codes
     * @return the mappings
     * @throws Exception the exception
     */
    public static ResultListMapping getMappings(final String branch, final MapSet mapSet, final SearchParameters searchParameters, final String filter,
        final boolean showOverriddenEntries, final List<String> conceptCodes) throws Exception {

        final long requestStartMs = System.currentTimeMillis();
        if (mapSet == null || StringUtils.isBlank(mapSet.getRefSetCode())) {
            throw new LocalException("Map set code is required.");
        }

        final SearchParameters paging = searchParameters != null ? searchParameters : new SearchParameters();

        LOG.info("getMappings start mapSet={} refSet={} branch={} filter='{}' limit={} offset={} searchAfter={} conceptCodes={}",
            mapSet.getId(), mapSet.getRefSetCode(), branch, filter,
            paging.getLimit(), paging.getOffset(), paging.getSearchAfter(),
            conceptCodes != null ? conceptCodes.size() : 0);

        final boolean explicitConceptCodes = conceptCodes != null && !conceptCodes.isEmpty();

        final LinkedHashSet<String> filteredConceptSet = new LinkedHashSet<>();

        if (explicitConceptCodes) {
            filteredConceptSet.addAll(conceptCodes);
        } else if (StringUtils.isNotBlank(filter)) {
            final String trimmedFilter = filter.trim();
            final long conceptSearchStartMs = System.currentTimeMillis();
            filteredConceptSet.addAll(resolveFilteredConceptIds(branch, mapSet.getRefSetCode(), trimmedFilter));
            LOG.info("getMappings phase=conceptSearch {}ms filter='{}' matchingConcepts={}",
                System.currentTimeMillis() - conceptSearchStartMs, trimmedFilter, filteredConceptSet.size());
        }

        final List<String> filteredConceptList = new ArrayList<>(filteredConceptSet);
        final boolean scopedTextFilter = !explicitConceptCodes && StringUtils.isNotBlank(filter);

        if (scopedTextFilter && filteredConceptList.isEmpty()) {
            final ResultListMapping empty = new ResultListMapping();
            empty.setTotal(0);
            empty.setTotalKnown(true);
            empty.setLimit(paging.getLimit() != null ? paging.getLimit() : 0);
            empty.setOffset(paging.getOffset() != null ? paging.getOffset() : 0);
            LOG.info("getMappings complete {}ms items=0 total=0 (no concepts matched filter)", System.currentTimeMillis() - requestStartMs);
            return empty;
        }

        final StringBuilder requestBody = new StringBuilder();
        requestBody.append("{");
        requestBody.append("\"active\": true,");
        requestBody.append("\"referenceSet\": \"").append(mapSet.getRefSetCode()).append("\"");
        if (filteredConceptList != null && !filteredConceptList.isEmpty()) {
            requestBody.append(",").append("\"referencedComponentIds\": [").append(String.join(",", filteredConceptList)).append("]");
        }

        // Explicit conceptCodes only (e.g. batch edit return). Text filter matches must not create empty-map placeholders.
        final Set<String> requestedConcepts = explicitConceptCodes ? new HashSet<>(conceptCodes) : new HashSet<>();

        // if (searchParameters.getLimit() != null) {
        // requestBody.append(",").append("\"limit\":
        // ").append(searchParameters.getLimit());
        // }
        // if (searchParameters.getOffset() != null) {
        // requestBody.append(",").append("\"offset\":
        // ").append(searchParameters.getOffset());
        // }
        // if (StringUtils.isNotBlank(searchParameters.getSearchAfter())) {
        // requestBody.append(",").append("\"searchAfter\":
        // ").append(searchParameters.getSearchAfter());
        // }
        requestBody.append("}");

        final String fromTerminology = mapSet.getFromTerminology();
        final String toTerminology = mapSet.getToTerminology();
        final Map<String, Mapping> conceptIdToMappingMap = new LinkedHashMap<>();

        final Map<String, Set<String>> conceptsToLookup = new HashMap<>();
        conceptsToLookup.put(mapSet.getToTerminology(), new HashSet<>());
        conceptsToLookup.put(mapSet.getFromTerminology(), new HashSet<>());

        boolean done = false;
        int i = 0;

        int total = 0;
        int limit = 0;
        int offset = 0;
        String searchAfter = null;

        // Text filter: resolve all matching source concepts, then page map entries via member search.
        final SearchParameters memberSearchPaging = new SearchParameters(paging);

        final long memberSearchStartMs = System.currentTimeMillis();
        while (!done) {

            final String targetUri = SnowstormConnection.getBaseUrl() + branch + "/members/search?"
                + SnowstormApiPaging.getMemberSearchPagingQueryString(memberSearchPaging);
            LOG.info("getMappings phase=memberSearch request referencedComponentIds={}", filteredConceptList.size());

            try (final Response response = SnowstormConnection.postResponse(targetUri, requestBody.toString())) {

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new Exception(
                        "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
                }

                final JsonNode data = ThreadLocalMapper.get().readTree(SnowstormConnection.readEntityAsString(response));

                if (data.has("total")) {
                    total = data.get("total").asInt();
                }
                if (data.has("limit")) {
                    limit = data.get("limit").asInt();
                }
                if (data.has("offset")) {
                    offset = data.get("offset").asInt();
                }
                if (data.has("searchAfter")) {
                    searchAfter = data.get("searchAfter").asText();
                }

                final JsonNode mappingsBatch = data.get("items");
                if (mappingsBatch == null || !mappingsBatch.isArray() || mappingsBatch.isEmpty()) {
                    done = true;
                    continue;
                }

                final Iterator<JsonNode> itemIterator = mappingsBatch.iterator();

                // parse items to retrieve matching concept
                while (itemIterator.hasNext()) {

                    final JsonNode mappingNode = itemIterator.next();
                    // If this is the first time a fromConcept is encountered, set up the
                    // mapping and add it to the tracker
                    final String mapCode = mappingNode.get("referencedComponentId").asText();

                    if (!conceptIdToMappingMap.containsKey(mapCode)) {

                        final Mapping mapping = new Mapping();
                        mapping.setCode(mapCode);
                        conceptsToLookup.get(fromTerminology).add(mapping.getCode());

                        mapping.setMapSetId(mapSet.getId());
                        mapping.setMapEntries(new ArrayList<>());

                        conceptIdToMappingMap.put(mapping.getCode(), mapping);
                    }

                    // Add an entry to the mapping
                    final Mapping mapping = conceptIdToMappingMap.get(mapCode);
                    final MapEntry mapEntry = convertSnowstormMemberToMapEntry(mappingNode, mapSet, branch);

                    conceptsToLookup.get(fromTerminology).add(mapEntry.getRelationCode());
                    conceptsToLookup.get(toTerminology).add(mapEntry.getToCode());

                    mapping.getMapEntries().add(mapEntry);

                }

            }

            if (scopedTextFilter) {
                done = true;
            } else {
                i++;
                memberSearchPaging.setOffset(i * memberSearchPaging.getLimit());
                if (memberSearchPaging.getOffset() >= memberSearchPaging.getLimit()) {
                    done = true;
                }
            }
        }
        LOG.info("getMappings phase=memberSearch {}ms mappingConcepts={}", System.currentTimeMillis() - memberSearchStartMs, conceptIdToMappingMap.size());

        final List<String> invalidConceptIds = new ArrayList<>();
        Set<String> noMapConceptIds = new HashSet<>();

        if (explicitConceptCodes) {
            // Identify concepts that were requested where no mappings were found
            // Check all concepts.  If they are invalid, add them to the invalid list.  If they are valid, create an empty Group 1, Priority 1 map object for them.
            noMapConceptIds = new HashSet<>(requestedConcepts);
            noMapConceptIds.removeAll(conceptIdToMappingMap.keySet());

            for (final String conceptId : noMapConceptIds) {
                conceptsToLookup.get(fromTerminology).add(conceptId);
            }
        }

        // get a list of codes to get concepts - use versions from mapSet (DB)
        final Map<String, String> terminologyToVersion = new HashMap<>();
        terminologyToVersion.put(mapSet.getFromTerminology(), mapSet.getFromVersion());
        terminologyToVersion.put(mapSet.getToTerminology(), mapSet.getToVersion());
        final long getConceptsStartMs = System.currentTimeMillis();
        final Map<String, Map<String, Concept>> terminologyConceptMap = getConcepts(branch, conceptsToLookup, terminologyToVersion);
        LOG.info("getMappings phase=getConcepts {}ms fromCodes={} toCodes={}", System.currentTimeMillis() - getConceptsStartMs,
            conceptsToLookup.get(fromTerminology).size(), conceptsToLookup.get(toTerminology).size());

        if (explicitConceptCodes) {
            for (final String conceptId : noMapConceptIds) {
                final Concept concept = terminologyConceptMap.get(fromTerminology).get(conceptId);
                if (concept == null) {
                    invalidConceptIds.add(conceptId);
                } else {
                    final Mapping mapping = new Mapping();
                    mapping.setCode(conceptId);
                    mapping.setMapSetId(mapSet.getId());
                    mapping.setMapEntries(new ArrayList<>());

                    MapEntry mapEntry = new MapEntry();
                    mapEntry.setRule("");
                    mapEntry.setPriority(1);
                    mapEntry.setGroup(1);
                    mapEntry.setAdvices(new HashSet<>());
                    mapEntry.setRelation("");
                    mapEntry.setRelationCode("");
                    mapEntry.setToCode("");
                    mapEntry.setToName("");
                    mapping.getMapEntries().add(mapEntry);
                    conceptIdToMappingMap.put(conceptId, mapping);
                }
            }
        }

        populateMappingsNamesFromConceptMap(conceptIdToMappingMap.values(), fromTerminology, toTerminology, terminologyConceptMap);
        final List<String> conceptIds = new ArrayList<>();
        for (final Mapping mapping : conceptIdToMappingMap.values()) {
            conceptIds.add(mapping.getCode());
        }

        // Handle edition-precedence in the map entries
        if (!showOverriddenEntries) {
            for (final Mapping mapping : conceptIdToMappingMap.values()) {
                handleEditionPrecedence(mapping);
            }
        }

        // TODO - do this elsewhere
        final Edition edition = new Edition();
        edition.setActive(true);
        edition.setAbbreviation("NO");
        edition.setDefaultLanguageCode("no");
        edition.getDefaultLanguageRefsets().add("61000202103");
        edition.getDefaultLanguageRefsets().add("900000000000509007");
        edition.setShortName("SNOMEDCT-NO");
        edition.setBranch(branch);

        final long descriptionsStartMs = System.currentTimeMillis();
        final Map<String, List<Description>> descriptions = SnowstormDescription.getDescriptions(edition, conceptIds);
        LOG.info("getMappings phase=descriptions {}ms conceptCount={}", System.currentTimeMillis() - descriptionsStartMs, conceptIds.size());

        // Sort all of the map entries in Group/Priority order
        for (final Mapping mapping : conceptIdToMappingMap.values()) {
            MapEntryUtility.sortMapEntries(mapping);
            mapping.setDescriptions(descriptions.get(mapping.getCode()));
        }

        // Once the file is completed parsed, return mappings as list (member encounter order)
        final ResultListMapping mappings = new ResultListMapping();
        mappings.getItems().addAll(conceptIdToMappingMap.values());
        mappings.setTotal(total);
        mappings.setTotalKnown(total > 0);
        if (StringUtils.isNotBlank(searchAfter)) {
            mappings.setSearchAfter(searchAfter);
        }
        mappings.setLimit(paging.getLimit() != null ? paging.getLimit() : limit);
        mappings.setOffset(paging.getOffset() != null ? paging.getOffset() : offset);

        // Add the invalid concept ids to the result list
        if(!invalidConceptIds.isEmpty()) {
            mappings.setInvalidConceptIds(invalidConceptIds);
        }

        int mapEntriesOnPage = 0;
        for (final Mapping mapping : mappings.getItems()) {
            if (mapping.getMapEntries() != null) {
                mapEntriesOnPage += mapping.getMapEntries().size();
            }
        }
        LOG.info("getMappings complete {}ms items={} mapEntriesOnPage={} total={}", System.currentTimeMillis() - requestStartMs, mappings.getItems().size(),
            mapEntriesOnPage, mappings.getTotal());
        return mappings;

    }

    /**
     * Convert Snowstorm Refeet member to mapping.
     *
     * @param mappingNode the mapping node
     * @param mapSet the map set
     * @param branch the branch
     * @return the mapping
     * @throws Exception the exception
     */
    private static MapEntry convertSnowstormMemberToMapEntry(final JsonNode mappingNode, final MapSet mapSet, final String branch) throws Exception {

        final MapEntry mapEntry = new MapEntry();

        if (mappingNode.has("effectiveTime")) {
            mapEntry.setModified(new SimpleDateFormat("yyyyMMdd").parse(mappingNode.get("effectiveTime").asText()));
        }
        mapEntry.setId(mappingNode.get("memberId").asText());
        mapEntry.setReleased(mappingNode.get("released").asBoolean());
        mapEntry.setModuleId(mappingNode.get("moduleId").asText());

        final JsonNode additionalFields = mappingNode.get("additionalFields");

        if (additionalFields.isNull() || additionalFields.isEmpty()) {
            mapEntry.setRule("");
            mapEntry.setPriority(1);
            mapEntry.setGroup(1);
            mapEntry.setAdvices(new HashSet<>());
            mapEntry.setRelation("");
            mapEntry.setRelationCode("");
            mapEntry.setToCode("");
            mapEntry.setToName("");
        } else {
            mapEntry.setRule(additionalFields.get("mapRule").asText());
            mapEntry.setPriority(additionalFields.get("mapPriority").asInt());
            mapEntry.setGroup(additionalFields.get("mapGroup").asInt());

            final Set<String> advices = new HashSet<>();
            final String mapAdviceString = additionalFields.get("mapAdvice").asText();
            // Store each pipe-delimited section of the map advice string as a
            // separate map advice
            for (final String mapAdvice : mapAdviceString.split("\\|")) {
                advices.add(mapAdvice.trim());
            }
            mapEntry.setAdvices(advices);

            if (additionalFields.hasNonNull("mapCategoryId")) {
                mapEntry.setRelationCode(additionalFields.get("mapCategoryId").asText());
            } else {
                mapEntry.setRelationCode("");
            }
            mapEntry.setRelation("");

            if (additionalFields.hasNonNull("mapTarget")) {
                mapEntry.setToCode(additionalFields.get("mapTarget").asText());
            } else {
                mapEntry.setToCode("");
            }
            mapEntry.setToName("");
        }

        return mapEntry;
    }

    /**
     * One page of concept ids from a term search within a map set (ECL).
     */
    private static final class ConceptSearchPage {

        private final List<String> conceptIds;
        private final int total;
        private final String searchAfter;

        private ConceptSearchPage(final List<String> conceptIds, final int total, final String searchAfter) {

            this.conceptIds = conceptIds;
            this.total = total;
            this.searchAfter = searchAfter;
        }
    }

    /**
     * True when the filter is likely a map target code (e.g. ICD-10) rather than a SNOMED term.
     *
     * @param filter the filter
     * @return true if map target member search should run
     */
    private static boolean looksLikeMapTargetFilter(final String filter) {

        return filter.matches(".*[0-9].*") && (filter.contains(".") || filter.length() <= 10);
    }

    /**
     * True when the filter is a map-target glob (ICD-style code plus {@code *}), e.g. {@code R07*} or {@code K14.*}.
     * Digit-only prefixes such as {@code 42586*} are treated as terms/ids, not map targets.
     *
     * @param filter the filter
     * @return true if ECL {@code wild:} mapTarget search should run
     */
    private static boolean looksLikeMapTargetWildcard(final String filter) {

        if (StringUtils.isBlank(filter) || !filter.contains("*")) {
            return false;
        }

        final String withoutWildcards = filter.replace("*", "").trim();
        if (StringUtils.isBlank(withoutWildcards) || !withoutWildcards.matches(".*[0-9].*")) {
            return false;
        }

        return withoutWildcards.matches(".*[A-Za-z].*") || withoutWildcards.contains(".");
    }

    /**
     * Removes user-typed asterisks from a non-map-target filter so Snowstorm prefix matching can run.
     *
     * @param filter the filter
     * @return filter without {@code *}, trimmed
     */
    private static String stripAsterisks(final String filter) {

        return filter.replace("*", "").trim();
    }

    /**
     * Term search for concepts in a map set, returning a single page and total hit count.
     *
     * @param branch the branch
     * @param mapSetCode the map set code
     * @param searchString the search string
     * @param searchParameters paging (limit, offset, searchAfter)
     * @return one page of matching concept ids
     * @throws Exception the exception
     */
    private static ConceptSearchPage searchConceptsPage(final String branch, final String mapSetCode, final String searchString,
        final SearchParameters searchParameters) throws Exception {

        final int pageLimit = searchParameters.getLimit() != null && searchParameters.getLimit() > 0 ? searchParameters.getLimit() : 100;
        final int pageOffset = searchParameters.getOffset() != null && searchParameters.getOffset() > 0 ? searchParameters.getOffset() : 0;

        LOG.info("searchConceptsPage filter='{}' limit={} offset={} searchAfter={}", searchString, pageLimit, pageOffset,
            searchParameters.getSearchAfter());

        if (StringUtils.isNotBlank(searchParameters.getSearchAfter())) {
            return fetchConceptSearchPageBatch(branch, mapSetCode, searchString, pageLimit, searchParameters.getSearchAfter());
        }

        if (pageOffset == 0) {
            return fetchConceptSearchPageBatch(branch, mapSetCode, searchString, pageLimit, "");
        }

        LOG.info("searchConceptsPage skipping {} rows via searchAfter batches", pageOffset);
        int remainingSkip = pageOffset;
        String searchAfter = "";
        int total = -1;
        final List<String> pageIds = new ArrayList<>();

        while (pageIds.size() < pageLimit) {
            final int fetchLimit = remainingSkip > 0 ? Math.min(Math.max(remainingSkip, pageLimit), 5000) : pageLimit - pageIds.size();
            final ConceptSearchPage batch = fetchConceptSearchPageBatch(branch, mapSetCode, searchString, fetchLimit, searchAfter);
            if (total < 0) {
                total = batch.total;
            }

            List<String> batchIds = batch.conceptIds;
            if (batchIds.isEmpty()) {
                return new ConceptSearchPage(pageIds, total >= 0 ? total : 0, null);
            }

            if (remainingSkip > 0) {
                if (batchIds.size() <= remainingSkip) {
                    remainingSkip -= batchIds.size();
                    searchAfter = StringUtils.defaultString(batch.searchAfter);
                    if (remainingSkip > 0) {
                        if (StringUtils.isBlank(searchAfter)) {
                            return new ConceptSearchPage(pageIds, total, null);
                        }
                        continue;
                    }
                    if (StringUtils.isBlank(searchAfter)) {
                        return new ConceptSearchPage(pageIds, total, null);
                    }
                    continue;
                }
                batchIds = batchIds.subList(remainingSkip, batchIds.size());
                remainingSkip = 0;
            }

            final int take = Math.min(pageLimit - pageIds.size(), batchIds.size());
            pageIds.addAll(batchIds.subList(0, take));
            searchAfter = batch.searchAfter;

            if (pageIds.size() < pageLimit && StringUtils.isNotBlank(searchAfter)) {
                continue;
            }
            break;
        }

        return new ConceptSearchPage(pageIds, total, searchAfter);
    }

    /**
     * One batch of concept ids matching term search within a map set (ECL ^refset).
     * Uses POST /concepts/search with returnIdOnly (faster than GET /concepts on this Snowstorm for ECL+term).
     */
    private static ConceptSearchPage fetchConceptSearchPageBatch(final String branch, final String mapSetCode, final String searchString, final int pageLimit,
        final String searchAfter) throws Exception {

        final long batchStartMs = System.currentTimeMillis();
        final String targetUri = SnowstormConnection.getBaseUrl() + branch + "/concepts/search";

        final ObjectNode requestBody = ThreadLocalMapper.get().createObjectNode();
        requestBody.put("termFilter", searchString);
        requestBody.put("eclFilter", "^" + mapSetCode);
        requestBody.put("limit", pageLimit);
        requestBody.put("termActive", true);
        requestBody.put("returnIdOnly", true);
        if (StringUtils.isNotBlank(searchAfter)) {
            requestBody.put("searchAfter", searchAfter);
        }

        LOG.info("fetchConceptSearchPageBatch POST {} limit={} searchAfter={}", targetUri, pageLimit, searchAfter);

        try (final Response response = SnowstormConnection.postResponse(targetUri, requestBody.toString())) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                throw new Exception(
                    "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
            }

            final JsonNode data = ThreadLocalMapper.get().readTree(SnowstormConnection.readEntityAsString(response));
            final List<String> conceptIds = new ArrayList<>();
            final JsonNode conceptNodeBatch = data.get("items");
            if (conceptNodeBatch != null && conceptNodeBatch.isArray()) {
                for (final JsonNode conceptNode : conceptNodeBatch) {
                    if (conceptNode.isTextual()) {
                        conceptIds.add(conceptNode.asText());
                    } else if (conceptNode.hasNonNull("id")) {
                        conceptIds.add(conceptNode.get("id").asText());
                    } else if (conceptNode.hasNonNull("conceptId")) {
                        conceptIds.add(conceptNode.get("conceptId").asText());
                    }
                }
            }

            int total = conceptIds.size();
            if (data.has("total") && !data.get("total").isNull()) {
                total = data.get("total").asInt();
            }

            String nextSearchAfter = null;
            if (data.has("searchAfter") && !data.get("searchAfter").isNull()) {
                nextSearchAfter = data.get("searchAfter").asText();
            }

            LOG.info("fetchConceptSearchPageBatch {}ms ids={} total={}", System.currentTimeMillis() - batchStartMs, conceptIds.size(), total);
            return new ConceptSearchPage(conceptIds, total, nextSearchAfter);
        }
    }

    /**
     * One page of source concept ids whose map target field matches the filter.
     *
     * @param branch the branch
     * @param mapSetCode the map set code
     * @param mapTargetFilter the map target filter
     * @param limit the limit
     * @param offset the offset
     * @return referenced component ids for this page and Snowstorm hit count
     * @throws Exception the exception
     */
    private static ConceptSearchPage searchReferencedComponentsByMapTargetPage(final String branch, final String mapSetCode, final String mapTargetFilter,
        final int limit, final int offset) throws Exception {

        if (StringUtils.isBlank(mapTargetFilter)) {
            return new ConceptSearchPage(new ArrayList<>(), 0, null);
        }

        final ObjectNode requestBody = ThreadLocalMapper.get().createObjectNode();
        requestBody.put("active", true);
        requestBody.put("referenceSet", mapSetCode);
        final ObjectNode additionalFields = ThreadLocalMapper.get().createObjectNode();
        additionalFields.put("mapTarget", mapTargetFilter);
        requestBody.set("additionalFields", additionalFields);

        final LinkedHashSet<String> referencedComponentIds = new LinkedHashSet<>();
        int total = 0;
        final SearchParameters mapTargetPaging = new SearchParameters(null, limit, offset);
        final String targetUri = SnowstormConnection.getBaseUrl() + branch + "/members/search?"
            + SnowstormApiPaging.getMemberSearchPagingQueryString(mapTargetPaging);

        try (final Response response = SnowstormConnection.postResponse(targetUri, requestBody.toString())) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                LOG.warn("mapTarget member search was not successful for map set {} and filter '{}'. Status: {}", mapSetCode, mapTargetFilter,
                    response.getStatus());
                return new ConceptSearchPage(new ArrayList<>(), 0, null);
            }

            final JsonNode data = ThreadLocalMapper.get().readTree(SnowstormConnection.readEntityAsString(response));
            if (data.has("total") && !data.get("total").isNull()) {
                total = data.get("total").asInt();
            }
            final JsonNode items = data.get("items");
            if (items != null && items.isArray()) {
                for (final JsonNode item : items) {
                    if (item != null && item.hasNonNull("referencedComponentId")) {
                        referencedComponentIds.add(item.get("referencedComponentId").asText());
                    }
                }
            }
        }

        return new ConceptSearchPage(new ArrayList<>(referencedComponentIds), total, null);
    }

    /**
     * Resolves all source concept ids matching a map set text filter (term search, optional map target, optional concept id).
     * Asterisks are map-target wildcards when the filter looks like a target code; otherwise they are stripped.
     *
     * @param branch the branch
     * @param mapSetCode the map set code
     * @param trimmedFilter the trimmed filter
     * @return distinct concept ids in stable encounter order
     * @throws Exception the exception
     */
    private static LinkedHashSet<String> resolveFilteredConceptIds(final String branch, final String mapSetCode, final String trimmedFilter) throws Exception {

        final LinkedHashSet<String> conceptIds = new LinkedHashSet<>();

        if (looksLikeMapTargetWildcard(trimmedFilter)) {
            conceptIds.addAll(searchReferencedComponentsByMapTargetEcl(branch, mapSetCode, trimmedFilter));
            return conceptIds;
        }

        final String searchFilter = trimmedFilter.contains("*") ? stripAsterisks(trimmedFilter) : trimmedFilter;
        if (StringUtils.isBlank(searchFilter)) {
            return conceptIds;
        }

        conceptIds.addAll(searchConcepts(branch, mapSetCode, searchFilter));
        if (looksLikeMapTargetFilter(searchFilter)) {
            conceptIds.addAll(searchReferencedComponentsByMapTarget(branch, mapSetCode, searchFilter));
        }
        if (searchFilter.matches("[0-9]{6,18}")) {
            conceptIds.add(searchFilter);
        }
        return conceptIds;
    }

    /**
     * Search concepts.
     *
     * @param branch the branch
     * @param mapSetCode the map set code
     * @param searchString the search string
     * @return the list
     * @throws Exception the exception
     */
    private static List<String> searchConcepts(final String branch, final String mapSetCode, final String searchString) throws Exception {

        // Connect to snowstorm
        String searchAfter = "";

        final String targetUri = SnowstormConnection.getBaseUrl() + branch + "/concepts/search";

        final StringBuilder requestBodyTemplate = new StringBuilder();
        requestBodyTemplate.append("{");
        requestBodyTemplate.append("\"termFilter\": \"").append(searchString).append("\", ");
        requestBodyTemplate.append("\"eclFilter\": \"^").append(mapSetCode).append("\", ");
        requestBodyTemplate.append("\"limit\": ").append(5000).append(",");
        requestBodyTemplate.append("\"termActive\": true, ");
        requestBodyTemplate.append("\"returnIdOnly\": true, ");
        // Is replaced with actual searchAfter value
        requestBodyTemplate.append("\"searchAfter\": \"SEARCH_AFTER\"");
        requestBodyTemplate.append("}");

        final List<String> conceptCodes = new ArrayList<>();

        boolean done = false;
        while (!done) {

            final String requestBodyString = requestBodyTemplate.toString().replace("SEARCH_AFTER", searchAfter);

            try (final Response response = SnowstormConnection.postResponse(targetUri, requestBodyString)) {

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new Exception(
                        "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
                }

                final JsonNode data = ThreadLocalMapper.get().readTree(SnowstormConnection.readEntityAsString(response));

                final JsonNode conceptNodeBatch = data.get("items");
                if (conceptNodeBatch.isArray() && conceptNodeBatch.isEmpty()) {
                    done = true;
                    continue;
                }

                // add to list of concept codes
                final Iterator<JsonNode> itemIterator = conceptNodeBatch.iterator();
                while (itemIterator.hasNext()) {
                    final JsonNode conceptNode = itemIterator.next();
                    conceptCodes.add(conceptNode.asText());
                }
                if (data.has("searchAfter")) {
                    searchAfter = data.get("searchAfter").asText();
                } else {
                    done = true;
                }
            }
        }

        // return list of concept codes
        return conceptCodes;
    }

    /**
     * Finds source concept ids whose mapTarget matches an ECL wildcard, e.g. {@code R07*} via
     * {@code ^mapSet {{ M mapTarget = wild:"R07*" }}}.
     *
     * @param branch the branch path
     * @param mapSetCode the reference set identifier
     * @param mapTargetWildcard the user-typed map target pattern, including {@code *}
     * @return distinct referenced component ids
     * @throws Exception the exception
     */
    private static List<String> searchReferencedComponentsByMapTargetEcl(final String branch, final String mapSetCode, final String mapTargetWildcard)
        throws Exception {

        if (StringUtils.isBlank(mapTargetWildcard)) {
            return new ArrayList<>();
        }

        final String eclFilter = "^" + mapSetCode + " {{ M mapTarget = wild:\"" + mapTargetWildcard + "\" }}";
        LOG.info("mapTarget ECL search mapSet={} eclFilter={}", mapSetCode, eclFilter);

        String searchAfter = "";
        final String targetUri = SnowstormConnection.getBaseUrl() + branch + "/concepts/search";
        final List<String> conceptCodes = new ArrayList<>();
        boolean done = false;

        while (!done) {

            final ObjectNode requestBody = ThreadLocalMapper.get().createObjectNode();
            requestBody.put("eclFilter", eclFilter);
            requestBody.put("limit", 5000);
            requestBody.put("returnIdOnly", true);
            if (StringUtils.isNotBlank(searchAfter)) {
                requestBody.put("searchAfter", searchAfter);
            }

            try (final Response response = SnowstormConnection.postResponse(targetUri, requestBody.toString())) {

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    LOG.warn("mapTarget ECL search was not successful for map set {} and filter '{}'. Status: {}", mapSetCode, mapTargetWildcard,
                        response.getStatus());
                    break;
                }

                final JsonNode data = ThreadLocalMapper.get().readTree(SnowstormConnection.readEntityAsString(response));
                final JsonNode conceptNodeBatch = data.get("items");
                if (conceptNodeBatch == null || !conceptNodeBatch.isArray() || conceptNodeBatch.isEmpty()) {
                    done = true;
                    continue;
                }

                final Iterator<JsonNode> itemIterator = conceptNodeBatch.iterator();
                while (itemIterator.hasNext()) {
                    final JsonNode conceptNode = itemIterator.next();
                    if (conceptNode.isTextual()) {
                        conceptCodes.add(conceptNode.asText());
                    } else if (conceptNode.hasNonNull("id")) {
                        conceptCodes.add(conceptNode.get("id").asText());
                    } else if (conceptNode.hasNonNull("conceptId")) {
                        conceptCodes.add(conceptNode.get("conceptId").asText());
                    }
                }

                String nextSearchAfter = null;
                if (data.has("searchAfter") && !data.get("searchAfter").isNull()) {
                    nextSearchAfter = data.get("searchAfter").asText();
                }
                if (conceptNodeBatch.size() < 5000 || StringUtils.isBlank(nextSearchAfter) || nextSearchAfter.equals(searchAfter)) {
                    done = true;
                } else {
                    searchAfter = nextSearchAfter;
                }
            }
        }

        return conceptCodes;
    }

    /**
     * Finds referenced component (source) concept ids for members of {@code mapSetCode} whose {@code mapTarget}
     * additional field matches {@code mapTargetFilter} (e.g. ICD-10 code {@code R07.4}).
     *
     * @param branch the branch path
     * @param mapSetCode the reference set identifier
     * @param mapTargetFilter the map target code or text to match
     * @return distinct referenced component ids, in encounter order across pages
     * @throws Exception the exception
     */
    private static List<String> searchReferencedComponentsByMapTarget(final String branch, final String mapSetCode, final String mapTargetFilter)
        throws Exception {

        if (StringUtils.isBlank(mapTargetFilter)) {
            return new ArrayList<>();
        }

        final ObjectNode requestBody = ThreadLocalMapper.get().createObjectNode();
        requestBody.put("active", true);
        requestBody.put("referenceSet", mapSetCode);
        final ObjectNode additionalFields = ThreadLocalMapper.get().createObjectNode();
        additionalFields.put("mapTarget", mapTargetFilter);
        requestBody.set("additionalFields", additionalFields);

        final LinkedHashSet<String> referencedComponentIds = new LinkedHashSet<>();
        final int pageLimit = 5000;
        int offset = 0;
        boolean done = false;

        while (!done) {

            final SearchParameters mapTargetPagePaging = new SearchParameters(null, pageLimit, offset);
            final String targetUri = SnowstormConnection.getBaseUrl() + branch + "/members/search?"
                + SnowstormApiPaging.getMemberSearchPagingQueryString(mapTargetPagePaging);

            try (final Response response = SnowstormConnection.postResponse(targetUri, requestBody.toString())) {

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    LOG.warn("mapTarget member search was not successful for map set {} and filter '{}'. Status: {}", mapSetCode, mapTargetFilter,
                        response.getStatus());
                    break;
                }

                final JsonNode data = ThreadLocalMapper.get().readTree(SnowstormConnection.readEntityAsString(response));
                final JsonNode items = data.get("items");
                if (items == null || !items.isArray() || items.isEmpty()) {
                    done = true;
                    continue;
                }

                for (final JsonNode item : items) {
                    if (item != null && item.hasNonNull("referencedComponentId")) {
                        referencedComponentIds.add(item.get("referencedComponentId").asText());
                    }
                }

                if (items.size() < pageLimit) {
                    done = true;
                } else {
                    offset += pageLimit;
                }
            }
        }

        return new ArrayList<>(referencedComponentIds);
    }

    /**
     * Gets the mapping.
     *
     * @param branch the branch
     * @param mapSetCode the map set code
     * @param conceptCode the concept code
     * @param moduleId the module id
     * @param activeOnly the active only
     * @param showOverriddenEntries the show overridden entries
     * @param includeDescriptions the include descriptions
     * @return the mapping
     * @throws Exception the exception
     */
    public static Mapping getMapping(final String branch, final String mapSetCode, final String conceptCode, final String moduleId, final boolean activeOnly,
        final boolean showOverriddenEntries, final boolean includeDescriptions, final MapSet dbMapSet) throws Exception {

        // Connect to snowstorm
        final Client client = getClients().get();
        String searchAfter = null;
        int limit = 50;

        final String targetUri = SnowstormConnection.getBaseUrl() + branch + "/members?referenceSet=" + mapSetCode + "&referencedComponentId=" + conceptCode
            + (moduleId != null ? "&module=" + moduleId : "") + (activeOnly == false ? "" : "&active=true") + "&limit=" + limit
            + (searchAfter != null ? "&searchAfter=" + searchAfter : "") + "&" + SnowstormApiPaging.getMemberSortQueryString();
        LOG.info("getSnowstormMapping url: " + targetUri);

        final WebTarget target = client.target(targetUri);

        final Response response = target.request(DEFAULT_ACCEPT)
            // .header("Cookie", ConfigUtility.getGenericUserCookie())
            .get();
        final String resultString = SnowstormConnection.readEntityAsString(response);
        if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
            throw new Exception(
                "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
        }

        final JsonNode data = ThreadLocalMapper.get().readTree(resultString);
        final JsonNode mappingsBatch = data.get("items");

        // Grab the specified mapSet (from Snowstorm for refset metadata; from DB for terminology/version)
        final MapSet mapSet = getMapSet(branch, mapSetCode);
        final MapSet mapSetForConcept = (dbMapSet != null && StringUtils.isNotBlank(dbMapSet.getFromTerminology()) && StringUtils.isNotBlank(dbMapSet.getFromVersion()))
            ? dbMapSet : mapSet;
        final Mapping mapping = new Mapping();

        final Iterator<JsonNode> itemIterator = mappingsBatch.iterator();

        // parse items to retrieve matching concept
        while (itemIterator.hasNext()) {

            final JsonNode mappingNode = itemIterator.next();

            // If this is the first time the fromConcept is encountered, set up the
            // mapping
            if (mapping.getCode() == null || mapping.getCode().isEmpty()) {
                mapping.setCode(mappingNode.get("referencedComponentId").asText());
                if (StringUtils.isBlank(mapSetForConcept.getFromTerminology()) || StringUtils.isBlank(mapSetForConcept.getFromVersion())) {
                    throw new LocalException("MapSet from database with fromTerminology and fromVersion is required for getMapping. mapSetCode: " + mapSetCode);
                }
                mapping.setMapSetId(mapSet.getId());
                mapping.setMapEntries(new ArrayList<>());
            }

            // Add an entry to the mapping
            final MapEntry mapEntry = convertSnowstormMemberToMapEntry(mappingNode, mapSetForConcept, branch);
            final List<MapEntry> mapEntries = mapping.getMapEntries();
            mapEntries.add(mapEntry);
            mapping.setMapEntries(mapEntries);

        }

        // Handle edition-precedence in the map entries
        if (!showOverriddenEntries) {
            handleEditionPrecedence(mapping);
        }

        populateMappingNamesFromConcepts(branch, mapSetForConcept, mapping);

        // Sort all of the map entries in Group/Priority order

        LOG.info("Before sort Mapping: {}", mapping);
        MapEntryUtility.sortMapEntries(mapping);
        LOG.info("After sort Mapping: {}", mapping);

        // Get descriptions for mapping
        final Edition edition = new Edition();
        edition.setActive(true);
        edition.setAbbreviation("NO");
        edition.setDefaultLanguageCode("no");
        edition.getDefaultLanguageRefsets().add("61000202103");
        edition.getDefaultLanguageRefsets().add("900000000000509007");
        edition.setShortName("SNOMEDCT-NO");
        edition.setBranch(branch);

        if (includeDescriptions) {
            final Map<String, List<Description>> descriptions = SnowstormDescription.getDescriptions(edition, List.of(mapping.getCode()));
            mapping.setDescriptions(descriptions.get(mapping.getCode()));
        }

        return mapping;
    }

    /**
     * Creates the mapping.
     *
     * @param mapProject the map project
     * @param branch the branch
     * @param mapSetCode the map set code
     * @param mappings the mappings
     * @return the list
     * @throws Exception the exception
     */
    public static List<Mapping> createMappings(final MapProject mapProject, final String branch, final String mapSetCode, final List<Mapping> mappings)
        throws Exception {

        final List<Mapping> newMappings = new ArrayList<>();
        final List<String> conceptIds = new ArrayList<>();

        for (final Mapping mapping : mappings) {

            final Mapping newMapping = createMapping(mapProject, branch, mapSetCode, mapping);
            newMappings.add(newMapping);
            conceptIds.add(newMapping.getCode());

        }

        final Map<String, List<Description>> descriptions = SnowstormDescription.getDescriptions(mapProject.getEdition(), conceptIds);
        for (final Mapping mapping : newMappings) {
            mapping.setDescriptions(descriptions.get(mapping.getCode()));
        }

        return newMappings;

    }

    /**
     * Creates the mapping.
     *
     * @param mapProject the map project
     * @param branch the branch
     * @param mapSetCode the map set code
     * @param mapping the mapping
     * @return the mapping
     * @throws Exception the exception
     */
    public static Mapping createMapping(final MapProject mapProject, final String branch, final String mapSetCode, final Mapping mapping) throws Exception {

        final MapSet mapSet = getMapSet(branch, mapSetCode);
        final Mapping newMapping = new Mapping();
        BeanUtils.copyProperties(mapping, newMapping);
        newMapping.getMapEntries().clear();

        // Pre-create cleanup
        for (final MapEntry mapEntry : mapping.getMapEntries()) {
            mapEntry.setAdvices(MapEntryUtility.fixMapEntryAdvices(mapEntry));
            mapEntry.setRelationCode(MapEntryUtility.calculateMapEntryRelationCode(mapProject, mapEntry));
        }

        final List<String> mapEntriesJson = new ArrayList<>();

        for (final MapEntry mapEntry : mapping.getMapEntries()) {
            mapEntriesJson.add(mapEntryToSnowstormMap(mapProject, mapSetCode, mapping.getName(), mapping.getCode(), mapEntry, mapSet));
        }

        final String targetUri = SnowstormConnection.getBaseUrl() + branch + "/members";

        // add each map entry to snowstorm
        for (final String mapEntryJson : mapEntriesJson) {

            try (final Response response = SnowstormConnection.postResponse(targetUri, mapEntryJson)) {

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new Exception(
                        "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
                }

                final JsonNode data = ThreadLocalMapper.get().readTree(SnowstormConnection.readEntityAsString(response));
                newMapping.getMapEntries().add(convertSnowstormMemberToMapEntry(data, mapSet, branch));

            }
        }

        // Handle edition-precedence in the map entries
        handleEditionPrecedence(newMapping);

        populateMappingNamesFromConcepts(branch, mapSet, newMapping);

        // Sort all of the map entries in Group/Priority order
        MapEntryUtility.sortMapEntries(newMapping);

        return newMapping;

    }

    /**
     * Update mappings.
     *
     * @param mapProject the map project
     * @param branch the branch
     * @param mapSetCode the map set code
     * @param mappings the mappings
     * @param mapSet the map set
     * @param user the acting user
     * @return the list
     * @throws Exception the exception
     */
    public static List<Mapping> updateMappings(final MapProject mapProject, final String branch, final String mapSetCode, final List<Mapping> mappings,
        final MapSet mapSet, final User user) throws Exception {

        final List<Mapping> updatedMappings = new ArrayList<>();
        final List<String> conceptIds = new ArrayList<>();

        for (final Mapping mapping : mappings) {
            final Mapping updatedMapping = updateMapping(mapProject, branch, mapSetCode, mapping, mapSet, user);
            updatedMappings.add(updatedMapping);
            conceptIds.add(updatedMapping.getCode());
        }

        // add descriptions to mappings to be returned
        final Map<String, List<Description>> descriptions = SnowstormDescription.getDescriptions(mapProject.getEdition(), conceptIds);

        // Sort all of the map entries in Group/Priority order
        for (final Mapping mapping : updatedMappings) {
            mapping.setDescriptions(descriptions.get(mapping.getCode()));
        }

        return updatedMappings;
    }

    /**
     * Update mapping.
     *
     * @param mapProject the map project
     * @param branch the branch
     * @param mapSetCode the map set code
     * @param submittedMapping the submitted mapping
     * @param mapSet the map set
     * @param user the acting user
     * @return the mapping
     * @throws Exception the exception
     */
    public static Mapping updateMapping(final MapProject mapProject, final String branch, final String mapSetCode, final Mapping submittedMapping,
        final MapSet mapSet, final User user) throws Exception {

        if (mapSet == null || StringUtils.isAnyBlank(mapSet.getFromTerminology(), mapSet.getFromVersion(), mapSet.getToTerminology(), mapSet.getToVersion())) {
            throw new LocalException("MapSet from database with fromTerminology, fromVersion, toTerminology and toVersion is required for updateMapping. mapSetCode: " + mapSetCode);
        }

        final String targetUri = SnowstormConnection.getBaseUrl() + branch + "/members/";

        // Pre-update cleanup
        for (final MapEntry mapEntry : submittedMapping.getMapEntries()) {
            mapEntry.setAdvices(MapEntryUtility.fixMapEntryAdvices(mapEntry));
            mapEntry.setRelationCode(MapEntryUtility.calculateMapEntryRelationCode(mapProject, mapEntry));
            if (StringUtils.isNotBlank(mapSet.getModuleId())) {
                mapEntry.setModuleId(mapSet.getModuleId()); // Only create entries in the Edition module, never in the International
            }
        }

        final Set<MapEntry> mapEntryAddList = new HashSet<>();
        final Set<MapEntry> mapEntryRemoveList = new HashSet<>();
        // Map of modified entries:
        // Key = existing Map Entry
        // Value = submitted Map Entry
        final Map<MapEntry, MapEntry> mapEntryModifyMap = new HashMap<>();

        // get all the map entries for the existing active mapping already in snowstorm
        // This is the current mapping that has precedence, so may be International or
        // Norwegian
        final Mapping existingActiveMapping = getMapping(branch, mapSetCode, submittedMapping.getCode(), null, true, false, false, mapSet);

        // also get the map entries for the active International mapping in snowstorm
        // (this may the same or different than the above).
        final Mapping existingActiveInternationalMapping =
            getMapping(branch, mapSetCode, submittedMapping.getCode(), SnomedConstants.SNOMEDCT_TO_ICD10_MAPPING_MODULE, true, false, false, mapSet);

        boolean revertedToInternational = false;

        // If map content is identical to the existing active map, do nothing.
        if (MapEntryUtility.areMapsEquivalent(submittedMapping, existingActiveMapping)) {
            LOG.info("No update required for mapping for {} - content unchanged", submittedMapping.getCode());
            return submittedMapping;
        }

        // If we get here, then there is a difference between the existing active map in
        // snowstorm and the
        // mapping being saved.

        // If there is no existing active mapping, then all entries of the submitted map
        // will be added (brand new map)
        if (existingActiveMapping == null || existingActiveMapping.getMapEntries() == null || existingActiveMapping.getMapEntries().size() == 0) {
            // If the mapping only has one empty entry, don't save it to snowstorm.
            // This is a special case for mappings brought in to batch edit via list of concept ids, and
            // if no map information was added we don't want to create a new, empty map.
            if (submittedMapping.getMapEntries().size() == 1 && submittedMapping.getMapEntries().get(0).getToCode().isEmpty() && submittedMapping.getMapEntries().get(0).getToName().isEmpty() && (submittedMapping.getMapEntries().get(0).getRelation() == null || submittedMapping.getMapEntries().get(0).getRelation().equals("---"))) {
                LOG.info("No update required for mapping for {} - empty mapping with no pre-existing map entries", submittedMapping.getCode());
                return submittedMapping;
            }
            for (final MapEntry submittedMapEntry : submittedMapping.getMapEntries()) {
                mapEntryAddList.add(submittedMapEntry);
            }
        }

        // If the existing active mapping is International, then all entries
        // of the submitted map will be added (this is a new Norwegian map overriding
        // the International)
        else if (!existingActiveMapping.getMapEntries().isEmpty()
            && SnomedConstants.SNOMEDCT_TO_ICD10_MAPPING_MODULE.equals(existingActiveMapping.getMapEntries().get(0).getModuleId())) {
            for (final MapEntry submittedMapEntry : submittedMapping.getMapEntries()) {
                mapEntryAddList.add(submittedMapEntry);
            }
        }
        // Next check if the submitted map is identical to the active International map.
        // This identifies where the Norwegian map had diverged from the international,
        // but now matches again.
        // In this case, remove all existing Norwegian map entries, and revert back to
        // the International.
        else if (MapEntryUtility.areMapsEquivalent(submittedMapping, existingActiveInternationalMapping)) {
            revertedToInternational = true;
            for (final MapEntry existingMapEntry : existingActiveMapping.getMapEntries()) {
                mapEntryRemoveList.add(existingMapEntry);
            }
        }
        // Now that all mapping-wide cases have been handled,
        // check entry-by-entry to determine which need to be added, removed, or
        // modified
        else {
            // First loop through all existing map entries, and comparing against
            // the submitted map entries where Group and Priority match.
            // If the entries are equivalent, then no action is required.
            // If the entries are not equivalent, check if they are close enough to share a
            // UUID in snowstorm.
            // If they do share a UUID, modify the existing map entry with the submitted
            // map's information.
            // If the don't share a UUID, remove the existing map and add the submitted map.
            // Finally, if an existing map entry has no corresponding group/priority
            // submitted map entry,
            // then that existing map entry needs to be removed.
            for (final MapEntry existingMapEntry : existingActiveMapping.getMapEntries()) {
                boolean matchFound = false;
                for (final MapEntry submittedMapEntry : submittedMapping.getMapEntries()) {
                    if (existingMapEntry.getGroup() == submittedMapEntry.getGroup() && existingMapEntry.getPriority() == submittedMapEntry.getPriority()) {
                        matchFound = true;
                        if (MapEntryUtility.areMapEntriesEquivalent(existingMapEntry, submittedMapEntry)) {
                            // Equivalent map - no action required.
                        } else {
                            if (MapEntryUtility.doMapEntriesShareUUID(existingMapEntry, submittedMapEntry)) {
                                mapEntryModifyMap.put(existingMapEntry, submittedMapEntry);
                            } else {
                                mapEntryRemoveList.add(existingMapEntry);
                                mapEntryAddList.add(submittedMapEntry);
                            }
                        }
                    }
                }
                if (!matchFound) {
                    mapEntryRemoveList.add(existingMapEntry);
                }
            }

            // Now loop through all submitted map entries, to find any cases with no
            // corresponding group/priority existing entry.
            // These entries need to be added.
            for (final MapEntry submittedMapEntry : submittedMapping.getMapEntries()) {
                boolean matchFound = false;
                for (final MapEntry existingMapEntry : existingActiveMapping.getMapEntries()) {
                    if (existingMapEntry.getGroup() == submittedMapEntry.getGroup() && existingMapEntry.getPriority() == submittedMapEntry.getPriority()) {
                        matchFound = true;
                        // No further comparison needed - all modified entries were identified above.
                        break;
                    }
                }
                if (!matchFound) {
                    mapEntryAddList.add(submittedMapEntry);
                }
            }
        }

        // Adding, removing, and modifying is handled differently depending on the
        // existing
        // map entries in snowstorm.
        final Map<MapEntry, MapEntry> mapEntryCreateList = new HashMap<>();
        final Set<MapEntry> mapEntryInactivateList = new HashSet<>();
        final Set<MapEntry> mapEntryReactivateList = new HashSet<>();
        final Set<MapEntry> mapEntryDeleteList = new HashSet<>();
        final Map<MapEntry, MapEntry> mapEntryUpdateList = new HashMap<>();

        // For all map entries to be added, check if there are any UUI-matching,
        // inactive, Norwegian entries in snowstorm.
        // If so, re-activate those existing entries, updating to match the submitted
        // entry if needed.
        // If not, create a new entry.
        final Mapping existingInactiveNorwegianMapping =
            getMapping(branch, mapSetCode, submittedMapping.getCode(), mapSet.getModuleId(), false, false, false, mapSet);

        for (final MapEntry submittedMapEntry : mapEntryAddList) {
            boolean matchFound = false;
            for (MapEntry existingInactiveMapEntry : existingInactiveNorwegianMapping.getMapEntries()) {
                if (MapEntryUtility.doMapEntriesShareUUID(existingInactiveMapEntry, submittedMapEntry)) {
                    matchFound = true;
                    if (!MapEntryUtility.areMapEntriesEquivalent(existingInactiveMapEntry, submittedMapEntry)) {
                        existingInactiveMapEntry = MapEntryUtility.updateExistingMapEntry(existingInactiveMapEntry, submittedMapEntry);
                    }
                    mapEntryReactivateList.add(existingInactiveMapEntry);
                    break;
                }
            }
            if (!matchFound) {
                if (existingActiveInternationalMapping != null && existingActiveInternationalMapping.getMapEntries() != null) {
                    // Find matching International entry to track what we're replacing
                    final MapEntry originalMapEntry = existingActiveInternationalMapping.getMapEntries().stream()
                        .filter(e -> e.getGroup() == submittedMapEntry.getGroup() && e.getPriority() == submittedMapEntry.getPriority()).findFirst()
                        .orElse(null);
                    mapEntryCreateList.put(submittedMapEntry, originalMapEntry);
                } else {
                    // No International entry exists, just create new entry
                    mapEntryCreateList.put(submittedMapEntry, null);
                }
            }
        }

        // For all map entries to be removed, check if they have been previously
        // released of not.
        // If so, then inactivate the entry
        // If not, then the entry can be fully deleted.
        for (final MapEntry mapEntry : mapEntryRemoveList) {
            if (mapEntry.isReleased()) {
                mapEntryInactivateList.add(mapEntry);
            } else {
                mapEntryDeleteList.add(mapEntry);
            }
        }

        // For all modified map entries, check if the corresponding existing map entry
        // has been previously released or not.
        // If not, then delete the existing map entry, and create a new entry using the
        // submitted map entry
        // If so, then update the existing map entry with the submitted map entry's
        // content
        for (MapEntry existingMapEntry : mapEntryModifyMap.keySet()) {
            final MapEntry submittedMapEntry = mapEntryModifyMap.get(existingMapEntry);
            if (!existingMapEntry.isReleased()) {
                mapEntryDeleteList.add(existingMapEntry);
                mapEntryCreateList.put(submittedMapEntry, existingMapEntry);
            } else {
                mapEntryUpdateList.put(submittedMapEntry, existingMapEntry);
            }
        }

        // final MapSet snowstormMapSet = getMapSet(branch, mapSetCode);
        final List<MapEntry> updatedMapEntries = new ArrayList<>();
        final List<AuditEntry> auditEntries = new ArrayList<>();
        final String refSetCode = StringUtils.isNotBlank(mapSet.getRefSetCode()) ? mapSet.getRefSetCode() : mapSetCode;

        // Create Map Entry (Refset member)
        // for (final MapEntry mapEntry : mapEntryCreateList) {
        for (final Map.Entry<MapEntry, MapEntry> entry : mapEntryCreateList.entrySet()) {
            final MapEntry mapEntry = entry.getKey();
            final MapEntry originalMapEntry = entry.getValue();
            // Clear out any existing UUID, since it's creating a new entry
            mapEntry.setId("");

            final String mapEntryJson = mapEntryToSnowstormMap(mapProject, mapSetCode, submittedMapping.getCode(), submittedMapping.getName(), mapEntry, mapSet);
            LOG.info("Add mapping: {} with {}", targetUri, mapEntryJson);
            try (final Response response = SnowstormConnection.postResponse(targetUri, mapEntryJson)) {
                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new Exception(
                        "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
                }
                final JsonNode updatedMapEntryJson = ThreadLocalMapper.get().readTree(SnowstormConnection.readEntityAsString(response));
                final MapEntry updatedMapEntry = convertSnowstormMemberToMapEntry(updatedMapEntryJson, mapSet, branch);
                updatedMapEntries.add(updatedMapEntry);

                if (originalMapEntry != null) {
                    addUpdateMappingAudit(auditEntries, refSetCode, existingActiveMapping, mapEntry, originalMapEntry);
                } else {
                    auditEntries.add(AuditEntryHelper.addMappingEntry(refSetCode, existingActiveMapping, mapEntry));
                }

            }
        }

        // Delete Map Entry (Refset member)
        for (final MapEntry mapEntry : mapEntryDeleteList) {
            LOG.info("Delete mapping: {}{}", targetUri, mapEntry.getId());
            try (final Response response = SnowstormConnection.deleteResponse(targetUri + mapEntry.getId(), null)) {
                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new Exception(
                        "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
                }
                auditEntries.add(AuditEntryHelper.deleteMappingEntry(refSetCode, existingActiveMapping, mapEntry));
            }
        }

        // Inactivate Map Entry (Refset member)
        for (final MapEntry mapEntry : mapEntryInactivateList) {
            mapEntry.setActive(false);
            final String mapEntryJson = mapEntryToSnowstormMap(mapProject, mapSetCode, submittedMapping.getCode(), submittedMapping.getName(), mapEntry, mapSet);
            LOG.info("Inactivate mapping: {} with {}", targetUri, mapEntryJson);
            try (final Response response = SnowstormConnection.putResponse(targetUri + mapEntry.getId(), mapEntryJson)) {
                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new Exception(
                        "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
                }
                final JsonNode updatedMapEntryJson = ThreadLocalMapper.get().readTree(SnowstormConnection.readEntityAsString(response));
                final MapEntry updatedMapEntry = convertSnowstormMemberToMapEntry(updatedMapEntryJson, mapSet, branch);
                updatedMapEntries.add(updatedMapEntry);
                auditEntries.add(AuditEntryHelper.statusChangeMappingEntry(refSetCode, existingActiveMapping, mapEntry));

            }
        }

        // Reactivate Map Entry (Refset member)
        for (final MapEntry mapEntry : mapEntryReactivateList) {
            mapEntry.setActive(true);
            final String mapEntryJson = mapEntryToSnowstormMap(mapProject, mapSetCode, submittedMapping.getCode(), submittedMapping.getName(), mapEntry, mapSet);
            LOG.info("Reactivate mapping: {} with {}", targetUri, mapEntryJson);
            try (final Response response = SnowstormConnection.putResponse(targetUri + mapEntry.getId(), mapEntryJson)) {
                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new Exception(
                        "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
                }
                final JsonNode updatedMapEntryJson = ThreadLocalMapper.get().readTree(SnowstormConnection.readEntityAsString(response));
                final MapEntry updatedMapEntry = convertSnowstormMemberToMapEntry(updatedMapEntryJson, mapSet, branch);
                updatedMapEntries.add(updatedMapEntry);
                auditEntries.add(AuditEntryHelper.statusChangeMappingEntry(refSetCode, existingActiveMapping, mapEntry));
            }
        }

        // Update Map Entry (Refset member)
        for (final Map.Entry<MapEntry, MapEntry> entry : mapEntryUpdateList.entrySet()) {
            final MapEntry mapEntry = entry.getKey();
            final MapEntry originalMapEntry = entry.getValue();

            final String mapEntryJson = mapEntryToSnowstormMap(mapProject, mapSetCode, submittedMapping.getCode(), submittedMapping.getName(), mapEntry, mapSet);
            LOG.info("Update mapping: {} with {}", targetUri, mapEntryJson);
            try (final Response response = SnowstormConnection.putResponse(targetUri + mapEntry.getId(), mapEntryJson)) {
                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new Exception(
                        "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
                }
                final JsonNode updatedMapEntryJson = ThreadLocalMapper.get().readTree(SnowstormConnection.readEntityAsString(response));
                final MapEntry updatedMapEntry = convertSnowstormMemberToMapEntry(updatedMapEntryJson, mapSet, branch);
                updatedMapEntries.add(updatedMapEntry);
                addUpdateMappingAudit(auditEntries, refSetCode, existingActiveMapping, mapEntry, originalMapEntry);
            }
        }

        submittedMapping.getMapEntries().clear();
        submittedMapping.getMapEntries().addAll(updatedMapEntries);

        populateMappingNamesFromConcepts(branch, mapSet, submittedMapping);

        if (revertedToInternational) {
            auditEntries.add(AuditEntryHelper.revertToInternationalMappingEntry(refSetCode, existingActiveMapping, existingActiveInternationalMapping));
        }

        addAuditEntries(user, auditEntries);

        // // Handle edition-precedence in the map entries
        // handleEditionPrecedence(submittedMapping);

        // Sort all of the map entries in Group/Priority order
        MapEntryUtility.sortMapEntries(submittedMapping);

        return submittedMapping;
    }

    /**
     * Fetches concepts from Snowstorm and populates mapping source and map entry display names.
     *
     * @param branch the branch
     * @param mapSet the map set
     * @param mapping the mapping
     * @throws Exception the exception
     */
    private static void populateMappingNamesFromConcepts(final String branch, final MapSet mapSet, final Mapping mapping) throws Exception {

        if (mapping == null || mapSet == null) {
            return;
        }

        final Map<String, Set<String>> conceptsToLookup = new HashMap<>();
        conceptsToLookup.put(mapSet.getFromTerminology(), new HashSet<>());
        conceptsToLookup.put(mapSet.getToTerminology(), new HashSet<>());

        if (StringUtils.isNotBlank(mapping.getCode())) {
            conceptsToLookup.get(mapSet.getFromTerminology()).add(mapping.getCode());
        }

        if (mapping.getMapEntries() != null) {
            for (final MapEntry entry : mapping.getMapEntries()) {
                if (StringUtils.isNotBlank(entry.getRelationCode())) {
                    conceptsToLookup.get(mapSet.getFromTerminology()).add(entry.getRelationCode());
                }
                if (StringUtils.isNotBlank(entry.getToCode())) {
                    conceptsToLookup.get(mapSet.getToTerminology()).add(entry.getToCode());
                }
            }
        }

        final Map<String, String> terminologyToVersion = new HashMap<>();
        terminologyToVersion.put(mapSet.getFromTerminology(), mapSet.getFromVersion());
        terminologyToVersion.put(mapSet.getToTerminology(), mapSet.getToVersion());
        final Map<String, Map<String, Concept>> terminologyConceptMap = getConcepts(branch, conceptsToLookup, terminologyToVersion);
        populateMappingNamesFromConceptMap(mapping, mapSet.getFromTerminology(), mapSet.getToTerminology(), terminologyConceptMap);
    }

    /**
     * Populates mapping source and map entry display names from pre-fetched concepts.
     *
     * @param mappings the mappings
     * @param fromTerminology the from terminology
     * @param toTerminology the to terminology
     * @param terminologyConceptMap the terminology concept map
     */
    private static void populateMappingsNamesFromConceptMap(final Collection<Mapping> mappings, final String fromTerminology, final String toTerminology,
        final Map<String, Map<String, Concept>> terminologyConceptMap) {

        for (final Mapping mapping : mappings) {
            populateMappingNamesFromConceptMap(mapping, fromTerminology, toTerminology, terminologyConceptMap);
        }
    }

    /**
     * Populates a single mapping's source and map entry display names from pre-fetched concepts.
     *
     * @param mapping the mapping
     * @param fromTerminology the from terminology
     * @param toTerminology the to terminology
     * @param terminologyConceptMap the terminology concept map
     */
    private static void populateMappingNamesFromConceptMap(final Mapping mapping, final String fromTerminology, final String toTerminology,
        final Map<String, Map<String, Concept>> terminologyConceptMap) {

        if (mapping == null) {
            return;
        }

        final Concept concept = terminologyConceptMap.get(fromTerminology).get(mapping.getCode());
        if (concept != null) {
            mapping.setName(concept.getName());
        } else if (mapping.getCode() == null || mapping.getCode().equals("")) {
            mapping.setName("");
        } else {
            LOG.error("Concept not found: terminology:{}, code:{}", fromTerminology, mapping.getCode());
            mapping.setName(mapping.getCode() + " CONCEPT NOT FOUND");
        }

        if (mapping.getMapEntries() == null) {
            return;
        }

        for (final MapEntry entry : mapping.getMapEntries()) {
            final Concept relationConcept = terminologyConceptMap.get(fromTerminology).get(entry.getRelationCode());
            if (relationConcept != null) {
                entry.setRelation(relationConcept.getName());
            } else if (entry.getRelationCode() == null || entry.getRelationCode().equals("")) {
                entry.setRelation("");
            } else {
                entry.setRelation(entry.getRelationCode() + " CONCEPT NOT FOUND");
            }

            final Concept toConcept = terminologyConceptMap.get(toTerminology).get(entry.getToCode());
            if (toConcept != null) {
                entry.setToName(toConcept.getName());
            } else if (entry.getToCode() == null || entry.getToCode().equals("")) {
                entry.setToName("");
            } else {
                entry.setToName(entry.getToCode() + " CONCEPT NOT FOUND");
            }
        }
    }

    /**
     * Gets the concepts by terminology.
     *
     * @param branch the branch
     * @param conceptCodes the concept codes
     * @param terminologyToVersion map of terminology to version (from mapSet DB; required for non-SNOMEDCT)
     * @return the concept
     * @throws Exception the exception
     */
    private static Map<String, Map<String, Concept>> getConcepts(final String branch, final Map<String, Set<String>> conceptCodes,
        final Map<String, String> terminologyToVersion) throws Exception {

        // Map<terminology, Map<code, concept>>
        final Map<String, Map<String, Concept>> terminologyConceptMap = new HashMap<>();

        // for each terminology, get the concepts
        for (final String terminology : conceptCodes.keySet()) {

            final List<String> nonEmptyList =
                conceptCodes.get(terminology).stream().filter(str -> !Objects.isNull(str) && !str.isEmpty()).collect(Collectors.toList());

            final String version = terminologyToVersion != null ? terminologyToVersion.get(terminology) : null;
            final Map<String, Concept> concepts = getConceptsFromSnowstorm(branch, terminology, new ArrayList<>(nonEmptyList), version);

            terminologyConceptMap.put(terminology, concepts);

        }

        return terminologyConceptMap;

    }

    /**
     * Gets the concepts from snowstorm.
     *
     * @param branch the branch
     * @param terminology the terminology
     * @param codes the codes
     * @param version the version for this terminology (from map_sets DB; required for non-SNOMEDCT)
     * @return the concepts from snowstorm
     * @throws Exception the exception
     */
    private static Map<String, Concept> getConceptsFromSnowstorm(final String branch, final String terminology, final List<String> codes,
        final String version) throws Exception {

        if (codes == null || codes.isEmpty()) {
            return new HashMap<>();
        }

        final Map<String, Concept> conceptMap = new HashMap<>();

        if (!terminology.contains("SNOMEDCT")) {
            if (StringUtils.isBlank(version)) {
                throw new LocalException("Version is required for terminology " + terminology + ". map_sets.fromVersion/toVersion must be set in database.");
            }
            for (final String code : codes) {
                final Concept concept = SnowstormConcept.getConcept(terminology, version, code);
                conceptMap.put(code, concept);
            }
            return conceptMap;
        }

        LOG.debug("Get concepts for codes: {}", codes);

        final Integer fetchLimit = 1000;
        final SearchParameters searchParameters = new SearchParameters();
        searchParameters.setLimit(fetchLimit);
        searchParameters.setSearchAfter("");

        final String targetUri = SnowstormConnection.getBaseUrl() + branch + "/concepts/search";
        final String requestBodyTempate = "{ \"conceptIds\": [\"CONCEPT_CODES\"], \"searchAfter\": \"SEARCH_AFTER\", \"limit\": " + fetchLimit + "}";
        final int maxIterations = Math.floorDiv(codes.size(), fetchLimit) + 1;

        for (int i = 0; i < maxIterations; i++) {

            final Set<String> fetchCodes = new HashSet<>();
            fetchCodes.addAll(codes.subList(i * fetchLimit, Math.min((i + 1) * fetchLimit, codes.size())));

            final String requestBody = requestBodyTempate.toString().replace("CONCEPT_CODES", String.join("\",\"", fetchCodes)).replace("SEARCH_AFTER",
                StringUtils.isNotBlank(searchParameters.getSearchAfter()) ? searchParameters.getSearchAfter() : "");

            try (final Response response = SnowstormConnection.postResponse(targetUri, requestBody)) {

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new Exception(
                        "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
                }

                final JsonNode doc = ThreadLocalMapper.get().readTree(SnowstormConnection.readEntityAsString(response));
                final JsonNode conceptNodeBatch = doc.get("items");
                final Iterator<JsonNode> itemIterator = conceptNodeBatch.iterator();

                // parse items to retrieve matching concept
                while (itemIterator.hasNext()) {

                    final JsonNode conceptNode = itemIterator.next();
                    final Concept concept = SnowstormConcept.buildConcept(conceptNode);
                    conceptMap.put(concept.getCode(), concept);
                }
            }
        }

        return conceptMap;

    }

    // Handle edition-precedence in the map entries
    // If there are any active edition map entries (module!=449080006),
    // the edition takes priority and only its entries should be used.
    // If there are only International map entries (module=449080006),
    // then use them instead.
    /**
     * Handle edition precedence.
     *
     * @param mapping the mapping
     */
    private static void handleEditionPrecedence(final Mapping mapping) {

        // Separate map entries into international and edition
        final List<MapEntry> internationalEntries = new ArrayList<>();
        final List<MapEntry> editionEntries = new ArrayList<>();
        for (final MapEntry mapEntry : mapping.getMapEntries()) {
            if (SnomedConstants.SNOMEDCT_TO_ICD10_MAPPING_MODULE.equals(mapEntry.getModuleId())) {
                internationalEntries.add(mapEntry);
            } else {
                editionEntries.add(mapEntry);
            }
        }

        // If any active edition map entries exist, use those. Otherwise, use
        // international
        if (!editionEntries.isEmpty()) {
            mapping.setMapEntries(editionEntries);
        } else {
            mapping.setMapEntries(internationalEntries);
        }
    }

    /**
     * Map entry to snowstorm map.
     *
     * @param mapProject the map project
     * @param refsetId the refset id
     * @param fromCode the from code
     * @param fromName the from name
     * @param mapEntry the map entry
     * @return the string
     */
    private static String mapEntryToSnowstormMap(final MapProject mapProject, final String refsetId, final String fromCode, final String fromName,
        final MapEntry mapEntry, final MapSet mapSet) throws LocalException {

        // snowstorm map example
        /*
         * { "active": true, "moduleId": "449080006", "released": true, "releasedEffectiveTime": 20150731, "memberId": "baaae0b7-f564-505e-b604-0bbdd60a69f4",
         * "refsetId": "447562003", "referencedComponentId": "70273001", "additionalFields": { "mapCategoryId": "447637006", "mapRule": "TRUE", "mapAdvice":
         * "ALWAYS X40 | MAPPED FOLLOWING WHO GUIDANCE | POSSIBLE REQUIREMENT FOR PLACE OF OCCURRENCE" , "mapPriority": "1", "mapGroup": "2", "correlationId":
         * "447561005", "mapTarget": "X40" }, "referencedComponent": { "conceptId": "70273001", "active": true, "definitionStatus": "FULLY_DEFINED", "moduleId":
         * "900000000000207008", "fsn": { "term": "Poisoning caused by paracetamol (disorder)", "lang": "en" }, "pt": { "term":
         * "Poisoning caused by acetaminophen", "lang": "en" }, "id": "70273001" }, "effectiveTime": "20150731" }
         */

        final StringBuilder mapEntryJson = new StringBuilder();

        mapEntryJson.append("{");
        if (StringUtils.isNotBlank(mapEntry.getId())) {
            mapEntryJson.append("\"memberId\": \"").append(mapEntry.getId()).append("\",");
        } else {
            mapEntryJson.append("\"memberId\": \"").append(UUID.randomUUID().toString()).append("\",");
        }
        mapEntryJson.append("\"active\": ").append(mapEntry.isActive()).append(",");
        // Module id: MapSet (refset module from DB/Snowstorm) -> MapEntry (from client/existing)
        final String moduleId = (mapSet != null && StringUtils.isNotBlank(mapSet.getModuleId())) ? mapSet.getModuleId()
            : StringUtils.isNotBlank(mapEntry.getModuleId()) ? mapEntry.getModuleId() : null;
        if (StringUtils.isBlank(moduleId)) {
            throw new LocalException("moduleId is required for Snowstorm. Set moduleId on MapProject, MapSet, or ensure map entry has moduleId.");
        }
        mapEntryJson.append("\"moduleId\": \"").append(moduleId).append("\",");
        // Any map entry getting created or updated will be released=false
        mapEntryJson.append("\"released\": false,");
        // mapEntryJson.append("\"releasedEffectiveTime\": 20240415,");
        mapEntryJson.append("\"refsetId\": \"").append(refsetId).append("\",");
        mapEntryJson.append("\"referencedComponentId\": \"").append(fromCode).append("\",");

        // additional fields
        mapEntryJson.append("\"additionalFields\": {");

        if (StringUtils.isNotBlank(mapEntry.getRelationCode())) {
            mapEntryJson.append("\"mapCategoryId\": \"").append(mapEntry.getRelationCode()).append("\",");
        } else {
            mapEntryJson.append("\"mapCategoryId\": \"").append("").append("\",");
        }

        mapEntryJson.append("\"mapRule\": \"").append(mapEntry.getRule()).append("\",");
        mapEntryJson.append("\"mapAdvice\": \"").append(String.join(" | ", mapEntry.getAdvices())).append("\",");
        mapEntryJson.append("\"mapPriority\": ").append(mapEntry.getPriority()).append(",");
        mapEntryJson.append("\"mapGroup\": ").append(mapEntry.getGroup()).append(",");

        // TODO - figure out when/if correlationId will ever not be hardcoded as
        // 447561005
        mapEntryJson.append("\"correlationId\": \"").append("447561005").append("\",");

        mapEntryJson.append("\"mapTarget\": \"").append(mapEntry.getToCode()).append("\"");
        mapEntryJson.append("},");

        // TODO - might not be needed
        // mapEntryJson.append("\"referencedComponent\": {");
        // mapEntryJson.append("\"conceptId\": \"").append(fromCode).append("\",");
        // mapEntryJson.append("\"active\": true,");
        // mapEntryJson.append("\"definitionStatus\": \"FULLY_DEFINED\",");
        // mapEntryJson.append("\"moduleId\": \"900000000000207008\",");
        // mapEntryJson.append("\"fsn\": {");
        // mapEntryJson.append("\"term\": \"").append(fromName).append("\",");
        // mapEntryJson.append("\"lang\": \"en\"");
        // mapEntryJson.append("},");
        // mapEntryJson.append("\"pt\": {");
        // mapEntryJson.append("\"term\": \"").append(fromName).append("\",");
        // mapEntryJson.append("\"lang\": \"en\"");
        // mapEntryJson.append("},");
        // mapEntryJson.append("\"id\": \"").append(fromCode).append("\"");
        // mapEntryJson.append("},");

        // Any map entry getting created or updated will have a blank effectiveTime
        mapEntryJson.append("\"effectiveTime\": \"\"");
        mapEntryJson.append("}");

        return mapEntryJson.toString();

    }

    /**
     * Adds an update audit entry only when there is a user-visible map entry change.
     *
     * @param auditEntries the audit entries
     * @param refSetCode the map / refset code
     * @param mapping the mapping
     * @param updatedMapEntry the submitted map entry
     * @param originalMapEntry the previous map entry
     */
    private static void addUpdateMappingAudit(final List<AuditEntry> auditEntries, final String refSetCode, final Mapping mapping,
        final MapEntry updatedMapEntry, final MapEntry originalMapEntry) {

        final AuditEntry auditEntry = AuditEntryHelper.updateMappingEntry(refSetCode, mapping, updatedMapEntry, originalMapEntry);
        if (auditEntry != null && StringUtils.isNotBlank(auditEntry.getDetails())) {
            auditEntries.add(auditEntry);
        }
    }

    /**
     * Adds the audit entry.
     *
     * @param user the acting user
     * @param auditEntries the audit entries
     */
    private static void addAuditEntries(final User user, final List<AuditEntry> auditEntries) {

        if (auditEntries == null || auditEntries.isEmpty()) {
            return;
        }

        if (user == null || StringUtils.isBlank(user.getUserName())) {
            LOG.error("Failed to add audit entries: authenticated user is required");
            return;
        }

        try (final TerminologyService auditService = new TerminologyService()) {
            auditService.setModifiedBy(user.getUserName());
            auditService.setTransactionPerOperation(false);
            auditService.beginTransaction();
            for (final AuditEntry auditEntry : auditEntries) {
                auditService.add(auditEntry);
            }
            auditService.commit();
        } catch (final Exception e) {
            LOG.error("Failed to add audit entry: {}", e.getMessage());
        }
    }

    // NOT USED
    // /**
    // * Update an existing map entry with the non-defining content of the submitted map entry This can only be done on map entries that share a UUID.
    // *
    // * @param existingMapEntry the existing map entry
    // * @param submittedMapEntry the submitted map entry
    // * @return the map entry
    // * @throws Exception the exception
    // */
    // private static MapEntry updateExistingMapEntry(final MapEntry existingMapEntry, final MapEntry submittedMapEntry) throws Exception {
    //
    // if (!MapEntryUtility.doMapEntriesShareUUID(existingMapEntry, submittedMapEntry)) {
    // throw new Exception("You cannot update an existing map entry with a non UUID-sharing new entry");
    // }
    //
    // existingMapEntry.setAdditionalMapEntryInfos(submittedMapEntry.getAdditionalMapEntryInfos());
    // existingMapEntry.setAdvices(submittedMapEntry.getAdvices());
    // existingMapEntry.setRelation(submittedMapEntry.getRelation());
    // existingMapEntry.setRelationCode(submittedMapEntry.getRelationCode());
    //
    // return existingMapEntry;
    // }

    /**
     * Export mappings.
     *
     * @param branch the branch
     * @param mapSetCode the map set code
     * @param mappingExportRequest the mapping export request
     * @return the file
     * @throws Exception the exception
     */
    public static File exportMappings(final String branch, final String mapSetCode, final MappingExportRequest mappingExportRequest, final MapSet mapSet)
        throws Exception {

        if (mapSet == null) {
            throw new LocalException("MapSet from database is required for exportMappings. No fallback.");
        }
        final SearchParameters sp = new SearchParameters();
        sp.setLimit(10000);

        final ResultListMapping mappings = getMappings(branch, mapSet, sp, "", false, mappingExportRequest.getConceptCodes());

        final File zipFile = exportMappingFilesToDownload(mappings, mappingExportRequest.getColumnNames());

        return zipFile;
    }

    /**
     * Export the members based on user selection in a zipped pkg containing tab delimited format.
     *
     * @author vparekh
     * @param mappings the mappings
     * @param includedColumnsList the column list
     * @return the file containing the txt file with the member list export
     * @throws Exception the exception
     */
    private static File exportMappingFilesToDownload(final ResultListMapping mappings, final List<String> includedColumnsList) throws Exception {

        // Validate and create download directory if it doesn't exist
        final String outputDirPath = PropertyUtility.getProperty("mapexport.fileDir");
        final String outputFileName = PropertyUtility.getProperty("mapexport.file");
        final String DILIMITER = "\t";

        final File downloadDir = new File(outputDirPath);
        if (!downloadDir.exists() && !downloadDir.mkdirs()) {
            throw new IOException("Failed to create download directory for mapping export.  Directory: " + downloadDir);
        }

        // Create the output file
        final File outputFile = new File(downloadDir, outputFileName);

        final List<String> allowedColumns =
            Arrays.asList("Source", "Source PT", "Target", "Target PT", "Group", "Priority", "Relationship", "Rule", "Advices", "Last Modified");

        // Based on the includedColumns values create the customized text file creation
        // - map column with values
        try (final PrintWriter writer = new PrintWriter(new FileOutputStream(outputFile))) {

            // Create a mutable copy of the includedColumnsList to avoid
            // UnsupportedOperationException
            final List<String> columnsToInclude = new ArrayList<>(includedColumnsList);
            // Check if "Target" is in the included columns
            if (columnsToInclude.contains("Target")) {
                columnsToInclude.add("Group");
                columnsToInclude.add("Priority");
            }

            // if column from includedColumnsList is not in allowedColumns, remove it
            columnsToInclude.removeIf(column -> !allowedColumns.contains(column));

            // use allowedColumns as the order of the columns
            final StringBuilder header = new StringBuilder();
            for (final String column : allowedColumns) {
                if (columnsToInclude.contains(column)) {
                    header.append(column).append(DILIMITER);
                }
            }

            // Write the header
            writer.println(header.toString().trim());

            // Iterate over each Mapping and write data
            for (final Mapping mapping : mappings.getItems()) {
                final String sourceCode = mapping.getCode();
                final String sourceName = mapping.getName();

                for (final MapEntry entry : mapping.getMapEntries()) {
                    final Map<String, String> dataRow = new LinkedHashMap<>();
                    dataRow.put("Source", sourceCode);
                    dataRow.put("Source PT", sourceName);
                    dataRow.put("Target", entry.getToCode());
                    dataRow.put("Target PT", entry.getToName());
                    dataRow.put("Group", String.valueOf(entry.getGroup()));
                    dataRow.put("Priority", String.valueOf(entry.getPriority()));
                    dataRow.put("Relationship", entry.getRelation());
                    dataRow.put("Rule", entry.getRule());
                    dataRow.put("Advices", entry.getAdvices() != null ? String.join("|", entry.getAdvices()) : "");
                    dataRow.put("Last Modified",
                        entry.getModified() != null ? DateUtility.formatDate(entry.getModified(), DateUtility.DATE_FORMAT_REVERSE, null) : "N/A");

                    // Build the row based on included columns
                    final List<String> rowValues = new ArrayList<>();
                    for (final String column : allowedColumns) {
                        if (columnsToInclude.contains(column)) {
                            rowValues.add(dataRow.getOrDefault(column, ""));
                        }
                    }
                    writer.println(String.join(DILIMITER, rowValues));
                }
            }
            writer.flush();
        } catch (final IOException e) {
            LOG.error("Error during ZIP file creation or download", e);
            throw e;
        }

        // final List<String> sourceFiles = new ArrayList<>();
        // sourceFiles.add(outputFile.getAbsolutePath());
        // final File zipMapFile = FileUtility.zipFiles(sourceFiles, exportFileName);

        return outputFile;
    }

    /**
     * Import RF2 mappings.
     *
     * @param mapProject the mapProject
     * @param branch the branch
     * @param mappingFile the RF2 mappingFile
     * @param mapSet the map set
     * @param user the acting user
     * @return the mappings updates
     * @throws Exception the exception
     */
    public static List<Mapping> importMappings(final MapProject mapProject, final String branch, final MultipartFile mappingFile, final MapSet mapSet,
        final User user) throws Exception {

        final List<Mapping> mappings = getMappingsFromFile(mappingFile, mapProject);
        final List<Mapping> updatedRF2Mappings = new ArrayList<>();
        final List<String> conceptIds = new ArrayList<>();
        LOG.info("importMappings -RF2 Mapping obj  : {}", mappings);
        for (final Mapping mapping : mappings) {

            final Mapping updatedRF2Mapping = updateMapping(mapProject, branch, mapping.getMapSetId(), mapping, mapSet, user);
            updatedRF2Mappings.add(updatedRF2Mapping);
            conceptIds.add(updatedRF2Mapping.getCode());
        }

        // add descriptions to mappings to be returned
        final Map<String, List<Description>> descriptions = SnowstormDescription.getDescriptions(mapProject.getEdition(), conceptIds);

        // Sort all of the map entries in Group/Priority order
        for (final Mapping mapping : updatedRF2Mappings) {
            mapping.setDescriptions(descriptions.get(mapping.getCode()));
        }

        return updatedRF2Mappings;
    }

    /**
     * Gets the mappings from file.
     *
     * @param mappingFile the mapping file
     * @param mapProject the map project
     * @return the mappings from file
     * @throws Exception the exception
     */
    private static List<Mapping> getMappingsFromFile(final MultipartFile mappingFile, final MapProject mapProject) throws Exception {

        final Map<String, Mapping> mappingMap = new HashMap<>(); // Keyed by "Source"
        final List<Mapping> mappings = new ArrayList<>();

        final List<String> rf2Lines = FileUtility.readFileToArray(mappingFile);
        if (rf2Lines.isEmpty()) {
            throw new RuntimeException("The file is empty.");
        }

        // Extract header and determine column indices
        final String headerLine = rf2Lines.remove(0);
        final String[] headers = headerLine.split("\t");
        final Map<String, Integer> columnIndices = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            columnIndices.put(headers[i], i);
        }

        for (final String line : rf2Lines) {
            if (line.trim().isEmpty()) {
                continue; // Skip empty lines
            }

            try {
                final String[] columns = line.split("\t");
                final String active = columns[columnIndices.get("active")];
                final String moduleId = columns[columnIndices.get("moduleId")];
                // mapSetCode
                final String refsetId = columns[columnIndices.get("refsetId")];
                // source code
                final String referencedComponentId = columns[columnIndices.get("referencedComponentId")];
                final int mapGroup = Integer.parseInt(columns[columnIndices.get("mapGroup")]);
                final int mapPriority = Integer.parseInt(columns[columnIndices.get("mapPriority")]);
                final String mapRule = columns[columnIndices.get("mapRule")];
                final String mapAdvice = columns[columnIndices.get("mapAdvice")];
                final String mapTarget = columns[columnIndices.get("mapTarget")];
                final String correlationId = columns[columnIndices.get("correlationId")];
                final String relationCode = columns[columnIndices.get("mapCategoryId")];

                final MapEntry mapEntry = new MapEntry();
                mapEntry.setActive("1".equals(active));
                // Is reset in updateMappings method mapEntry.setModuleId(moduleId);
                mapEntry.setGroup(mapGroup);
                mapEntry.setPriority(mapPriority);
                mapEntry.setRule(mapRule);
                mapEntry.setToCode(mapTarget);
                // TODO: Web uses text such as "MAP SOURCE CONCEPT IS PROPERLY CLASSIFIED"
                mapEntry.setRelation(correlationId); // correlationId
                // fixed in updateMappings method
                mapEntry.addAdvice(mapAdvice);
                // calculated in updateMappings method
                mapEntry.setRelationCode(relationCode); // mapCategoryId
                final Concept toConcept =
                    SnowstormConcept.getConcept(mapProject.getDestinationTerminology(), mapProject.getDestinationTerminologyVersion(), mapTarget);
                mapEntry.setToName((toConcept != null) ? toConcept.getName() : "Mapping for " + referencedComponentId);

                // Check if a Mapping object already exists for this source
                Mapping mapping = mappingMap.get(referencedComponentId);
                if (mapping == null) {
                    mapping = new Mapping();
                    mapping.setMapSetId(refsetId);
                    mapping.setCode(referencedComponentId);
                    final Concept concept =
                        SnowstormConcept.getConcept(mapProject.getSourceTerminology(), mapProject.getSourceTerminologyVersion(), referencedComponentId);
                    mapping.setName((concept != null) ? concept.getName() : "Mapping for " + referencedComponentId);
                    mapping.setMapEntries(new ArrayList<>());
                    mappingMap.put(referencedComponentId, mapping);
                    mappings.add(mapping);
                }

                // Add the MapEntry to the Mapping
                mapping.getMapEntries().add(mapEntry);

            } catch (final Exception e) {

                LOG.error("Error while processing RF2 Mapping file", e);
                throw new RuntimeException("Error while processing RF2 Mapping file." + e.getMessage());

            }
        }
        LOG.info("getMappingsFromFile RF2 Mappings : {}", mappings);

        return mappings;
    }

}
