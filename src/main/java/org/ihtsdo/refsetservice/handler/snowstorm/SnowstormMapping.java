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
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.Description;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.MapEntry;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapRelation;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.model.MappingExportRequest;
import org.ihtsdo.refsetservice.model.RestException;
import org.ihtsdo.refsetservice.model.ResultListMapping;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.ihtsdo.refsetservice.util.FileUtility;
import org.ihtsdo.refsetservice.util.LocalException;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The Class SnowstormMapping.
 */
public class SnowstormMapping extends SnowstormAbstract {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SnowstormMapping.class);

    /** The Constant DEFAULT_ACCEPT. */
    private static final String DEFAULT_ACCEPT = MediaType.APPLICATION_JSON;

    /** The client. */
    private static ThreadLocal<Client> clients = new ThreadLocal<Client>() {

        @Override
        public Client initialValue() {

            return ClientBuilder.newClient();
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
        final String resultString = response.readEntity(String.class);
        if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
            throw new Exception(
                "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
        }

        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode doc = mapper.readTree(resultString);
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

            // TODO - unhack this. Some will need to be pulled from database rather
            // than snowstorm

            final JsonNode additionalFields = mapSetNode.get("additionalFields");

            mapSet.setVersionStatus("Published");
            mapSet.setVersion("2024-04-15");
            mapSet.setModified(new SimpleDateFormat("yyyy-MM-dd").parse("2024-04-15"));
            mapSet.setFromTerminology("SNOMEDCT-NO");
            mapSet.setFromVersion("2024-04-15");
            mapSet.setToTerminology("TBD");

            // TEMPORARY//
            if (mapSet.getRefSetCode().equals("447562003")) {
                mapSet.setToTerminology("ICD-10-NO");
                mapSet.setToVersion("20240723");
            } else if (mapSet.getRefSetCode().equals("68101000202102")) {
                mapSet.setToTerminology("ICPC2NO");
                mapSet.setToVersion("");
            }
            // TEMPORARY//

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

        LOG.info("getSnowstormMapset url: " + targetUri);

        final WebTarget target = client.target(targetUri);

        final Response response = target.request(DEFAULT_ACCEPT)
            // .header("Cookie", ConfigUtility.getGenericUserCookie())
            .get();
        final String resultString = response.readEntity(String.class);
        if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
            throw new Exception(
                "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
        }

        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode doc = mapper.readTree(resultString);
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

            // TODO - unhack this. Some will need to be pulled from database rather
            // than snowstorm

            final JsonNode additionalFields = mapSetNode.get("additionalFields");

            mapSet.setVersionStatus("Published");
            mapSet.setVersion("2024-04-15");
            mapSet.setModified(new SimpleDateFormat("yyyy-MM-dd").parse("2024-04-15"));
            mapSet.setFromTerminology("SNOMEDCT-NO");
            mapSet.setFromVersion("2024-04-15");
            mapSet.setToTerminology("TBD");

            // TEMPORARY//
            if (mapSet.getRefSetCode().equals("447562003")) {
                mapSet.setToTerminology("ICD-10-NO");
                mapSet.setFromVersion("");
            } else if (mapSet.getRefSetCode().equals("68101000202102")) {
                mapSet.setToTerminology("ICPC2NO");
                mapSet.setFromVersion("");
            }
            // TEMPORARY//

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
    public static ResultListMapping getMappings(final String branch, final String mapSetCode, final SearchParameters searchParameters, final String filter,
        final boolean showOverriddenEntries, final List<String> conceptCodes) throws Exception {

        if (StringUtils.isBlank(mapSetCode)) {
            throw new LocalException("Map set code is required.");
        }

        final List<String> filteredConceptList = new ArrayList<>();
        if (StringUtils.isNotBlank(filter)) {
            filteredConceptList.addAll(searchConcepts(branch, mapSetCode, filter));
        }

        if (conceptCodes != null && !conceptCodes.isEmpty()) {
            filteredConceptList.addAll(conceptCodes);
        }

        final StringBuilder requestBody = new StringBuilder();
        requestBody.append("{");
        requestBody.append("\"active\": true,");
        requestBody.append("\"referenceSet\": \"").append(mapSetCode).append("\"");
        if (filteredConceptList != null && !filteredConceptList.isEmpty()) {
            requestBody.append(",").append("\"referencedComponentIds\": [").append(String.join(",", filteredConceptList)).append("]");
        }
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

        // Grab the specified mapSet
        final MapSet mapSet = getMapSet(branch, mapSetCode);
        final String fromTerminology = mapSet.getFromTerminology();
        final String toTerminology = mapSet.getToTerminology();
        final Map<String, Mapping> conceptIdToMappingMap = new TreeMap<>();

        final Map<String, Set<String>> conceptsToLookup = new TreeMap<>();
        conceptsToLookup.put(mapSet.getToTerminology(), new HashSet<>());
        conceptsToLookup.put(mapSet.getFromTerminology(), new HashSet<>());
        final ObjectMapper mapper = new ObjectMapper();

        boolean done = false;
        int i = 0;

        int total = 0;
        int limit = 0;
        int offset = 0;
        String searchAfter = null;

        while (!done) {

            final String targetUri = SnowstormConnection.getBaseUrl() + branch + "/members/search?" + SnowstormApiPaging.getPagingQueryString(searchParameters);
            LOG.debug("getSnowstormMappings url: {}", targetUri);
            LOG.debug("request body: {}", requestBody.toString());

            try (final Response response = SnowstormConnection.postResponse(targetUri, requestBody.toString())) {

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new Exception(
                        "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
                }

                final JsonNode data = mapper.readTree(response.readEntity(String.class));
                final JsonNode mappingsBatch = data.get("items");
                if (mappingsBatch.isArray() && mappingsBatch.isEmpty()) {
                    done = true;
                    continue;
                }

                if (searchParameters != null) {
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

            i++;
            searchParameters.setOffset(i * searchParameters.getLimit());
            if (searchParameters.getOffset() >= searchParameters.getLimit()) {
                done = true;
            }
        }

        // get a list of codes to get concepts
        final Map<String, Map<String, Concept>> terminologyConceptMap = getConcepts(branch, conceptsToLookup);
        final List<String> conceptIds = new ArrayList<>();

        // add names to mappings and to map entries
        for (final Mapping mapping : conceptIdToMappingMap.values()) {

            final Concept concept = terminologyConceptMap.get(fromTerminology).get(mapping.getCode());

            if (concept != null) {
                mapping.setName(concept.getName());
            } else {
                LOG.error("Concept not found: terminology:{}, code:{}", fromTerminology, mapping.getCode());
                mapping.setName(mapping.getCode() + " CONCEPT NOT FOUND");
            }
            conceptIds.add(mapping.getCode());

            for (final MapEntry entry : mapping.getMapEntries()) {

                final Concept relationConcept = terminologyConceptMap.get(fromTerminology).get(entry.getRelationCode());
                entry.setRelation(relationConcept != null ? relationConcept.getName() : entry.getRelationCode() + " CONCEPT NOT FOUND");

                final Concept toConcept = terminologyConceptMap.get(toTerminology).get(entry.getToCode());
                entry.setToName(toConcept != null ? toConcept.getName() : entry.getToCode() + " CONCEPT NOT FOUND");

            }
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

        final Map<String, List<Description>> descriptions = SnowstormDescription.getDescriptions(edition, conceptIds);

        // Sort all of the map entries in Group/Priority order
        for (final Mapping mapping : conceptIdToMappingMap.values()) {
            sortMapEntries(mapping);
            mapping.setDescriptions(descriptions.get(mapping.getCode()));
        }

        // Once the file is completed parsed, return mappings as list
        final ResultListMapping mappings = new ResultListMapping();
        mappings.getItems().addAll(conceptIdToMappingMap.values());
        mappings.setTotal(total);
        mappings.setTotalKnown(total > 0);
        mappings.setLimit(limit);
        mappings.setOffset(offset);
        mappings.setSearchAfter(searchAfter);

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

        final JsonNode additionalFields = mappingNode.get("additionalFields");

        mapEntry.setRule(additionalFields.get("mapRule").asText());
        mapEntry.setPriority(additionalFields.get("mapPriority").asInt());
        mapEntry.setGroup(additionalFields.get("mapGroup").asInt());
        mapEntry.setModuleId(mappingNode.get("moduleId").asText());

        final Set<String> advices = new HashSet<>();
        final String mapAdviceString = additionalFields.get("mapAdvice").asText();
        // Store each pipe-delimited section of the map advice string as a
        // separate map advice
        for (final String mapAdvice : mapAdviceString.split("\\|")) {
            advices.add(mapAdvice.trim());
        }
        mapEntry.setAdvices(advices);

        final Concept relationConcept =
            SnowstormConcept.getConcept(mapSet.getFromTerminology(), mapSet.getFromVersion(), additionalFields.get("mapCategoryId").asText());
        if (relationConcept != null) {
            mapEntry.setRelation(relationConcept.getName());
            mapEntry.setRelationCode(relationConcept.getCode());
        } else {
            mapEntry.setRelation(mapEntry.getToCode() + " CONCEPT NOT FOUND");
        }

        mapEntry.setToCode(additionalFields.get("mapTarget").asText());

        final Concept toConcept = SnowstormConcept.getConcept(mapSet.getToTerminology(), mapSet.getToVersion(), additionalFields.get("mapTarget").asText());

        if (toConcept != null) {
            mapEntry.setToName(toConcept.getName());
        } else {
            mapEntry.setToName(mapEntry.getToCode() + " CONCEPT NOT FOUND");
        }

        return mapEntry;
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

                final ObjectMapper mapper = new ObjectMapper();
                final JsonNode data = mapper.readTree(response.readEntity(String.class));

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
     * Gets the mapping.
     *
     * @param branch the branch
     * @param mapSetCode the map set code
     * @param conceptCode the concept code
     * @param showOverriddenEntries the show overridden entries
     * @param includeDescriptions the include descriptions
     * @return the mapping
     * @throws Exception the exception
     */
    public static Mapping getMapping(final String branch, final String mapSetCode, final String conceptCode, final String moduleId, final boolean activeOnly,
        final boolean showOverriddenEntries, final boolean includeDescriptions) throws Exception {

        // Connect to snowstorm
        final Client client = getClients().get();
        String searchAfter = null;
        int limit = 50;

        final String targetUri = SnowstormConnection.getBaseUrl() + branch + "/members?referenceSet=" + mapSetCode + "&referencedComponentId=" + conceptCode
            + (moduleId != null ? "&module=" + moduleId : "") + (activeOnly == false ? "" : "&active=true") + "&limit=" + limit
            + (searchAfter != null ? "&searchAfter=" + searchAfter : "");
        LOG.info("getSnowstormMapping url: " + targetUri);

        final WebTarget target = client.target(targetUri);

        final Response response = target.request(DEFAULT_ACCEPT)
            // .header("Cookie", ConfigUtility.getGenericUserCookie())
            .get();
        final String resultString = response.readEntity(String.class);
        if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
            throw new Exception(
                "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
        }

        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode data = mapper.readTree(resultString);
        final JsonNode mappingsBatch = data.get("items");

        // Grab the specified mapSet
        final MapSet mapSet = getMapSet(branch, mapSetCode);
        final Mapping mapping = new Mapping();

        final Iterator<JsonNode> itemIterator = mappingsBatch.iterator();

        // parse items to retrieve matching concept
        while (itemIterator.hasNext()) {

            final JsonNode mappingNode = itemIterator.next();

            // If this is the first time the fromConcept is encountered, set up the
            // mapping
            if (mapping.getCode() == null || mapping.getCode().isEmpty()) {
                mapping.setCode(mappingNode.get("referencedComponentId").asText());
                mapping.setName(SnowstormConcept.getConcept(mapSet.getFromTerminology(), mapSet.getFromVersion(), mapping.getCode()).getName());
                mapping.setMapSetId(mapSet.getId());
                mapping.setMapEntries(new ArrayList<>());
            }

            // Add an entry to the mapping
            final MapEntry mapEntry = convertSnowstormMemberToMapEntry(mappingNode, mapSet, branch);
            final List<MapEntry> mapEntries = mapping.getMapEntries();
            mapEntries.add(mapEntry);
            mapping.setMapEntries(mapEntries);

        }

        // Handle edition-precedence in the map entries
        if (!showOverriddenEntries) {
            handleEditionPrecedence(mapping);
        }

        // Sort all of the map entries in Group/Priority order

        LOG.info("Before sort Mapping: {}", mapping);
        sortMapEntries(mapping);
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
            mapEntry.setAdvices(fixMapEntryAdvices(mapEntry));
            mapEntry.setRelationCode(calculateMapEntryRelationCode(mapProject, mapEntry));
        }

        final List<String> mapEntriesJson = new ArrayList<>();

        for (final MapEntry mapEntry : mapping.getMapEntries()) {
            mapEntriesJson.add(mapEntryToSnowstormMap(mapProject, mapSetCode, mapping.getName(), mapping.getCode(), mapEntry));
        }

        final String targetUri = SnowstormConnection.getBaseUrl() + branch + "/members";

        // add each map entry to snowstorm
        for (final String mapEntryJson : mapEntriesJson) {

            try (final Response response = SnowstormConnection.postResponse(targetUri, mapEntryJson)) {

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new Exception(
                        "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
                }

                final ObjectMapper mapper = new ObjectMapper();
                final JsonNode data = mapper.readTree(response.readEntity(String.class));
                newMapping.getMapEntries().add(convertSnowstormMemberToMapEntry(data, mapSet, branch));

            }
        }

        // Handle edition-precedence in the map entries
        handleEditionPrecedence(newMapping);

        // Sort all of the map entries in Group/Priority order
        sortMapEntries(newMapping);

        return newMapping;

    }

    /**
     * Update mappings.
     *
     * @param mapProject the map project
     * @param branch the branch
     * @param mapSetCode the map set code
     * @param mappings the mappings
     * @return the list
     * @throws Exception the exception
     */
    public static List<Mapping> updateMappings(final MapProject mapProject, final String branch, final String mapSetCode, final List<Mapping> mappings)
        throws Exception {

        final List<Mapping> updatedMappings = new ArrayList<>();
        final List<String> conceptIds = new ArrayList<>();

        for (final Mapping mapping : mappings) {
            final Mapping updatedMapping = updateMapping(mapProject, branch, mapSetCode, mapping);
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
     * @return the mapping
     * @throws Exception the exception
     */
    public static Mapping updateMapping(final MapProject mapProject, final String branch, final String mapSetCode, final Mapping submittedMapping)
        throws Exception {

        final String targetUri = SnowstormConnection.getBaseUrl() + branch + "/members/";

        // Pre-update cleanup
        for (final MapEntry mapEntry : submittedMapping.getMapEntries()) {
            mapEntry.setAdvices(fixMapEntryAdvices(mapEntry));
            mapEntry.setRelationCode(calculateMapEntryRelationCode(mapProject, mapEntry));
            mapEntry.setModuleId(mapProject.getModuleId()); // Only create entries in the Edition module, never in the International
        }

        final Set<MapEntry> mapEntryAddList = new HashSet<>();
        final Set<MapEntry> mapEntryRemoveList = new HashSet<>();
        // Map of modified entries:
        // Key = existing Map Entry
        // Value = submitted Map Entry
        final Map<MapEntry, MapEntry> mapEntryModifyMap = new HashMap<>();

        // get all the map entries for the existing active mapping already in snowstorm
        // This is the current mapping that has precedence, so may be International or Norwegian
        final Mapping existingActiveMapping = getMapping(branch, mapSetCode, submittedMapping.getCode(), null, true, false, false);

        // also get the map entries for the active International mapping in snowstorm
        // (this may the same or different than the above).
        final Mapping existingActiveInternationalMapping = getMapping(branch, mapSetCode, submittedMapping.getCode(), "449080006", true, false, false);

        // If map content is identical to the existing active map, do nothing.
        if (areMapsEquivalent(submittedMapping, existingActiveMapping)) {
            LOG.info("No update required for mapping for {} - content unchanged", submittedMapping.getCode());
            return submittedMapping;
        }

        // If we get here, then there is a difference between the existing active map in snowstorm and the
        // mapping being saved.

        // If there is no existing active mapping, then all entries of the submitted map
        // will be added (brand new map)
        if (existingActiveMapping == null || existingActiveMapping.getMapEntries() == null || existingActiveMapping.getMapEntries().size() == 0) {
            for (MapEntry submittedMapEntry : submittedMapping.getMapEntries()) {
                mapEntryAddList.add(submittedMapEntry);
            }
        }

        // If the existing active mapping is International, then all entries
        // of the submitted map will be added (this is a new Norwegian map overriding the International)
        else if (existingActiveMapping.getMapEntries().size() > 0 && existingActiveMapping.getMapEntries().get(0).getModuleId().equals("449080006")) {
            for (MapEntry submittedMapEntry : submittedMapping.getMapEntries()) {
                mapEntryAddList.add(submittedMapEntry);
            }
        }
        // Next check if the submitted map is identical to the active International map.
        // This identifies where the Norwegian map had diverged from the international, but now matches again.
        // In this case, remove all existing Norwegian map entries, and revert back to the International.
        else if (areMapsEquivalent(submittedMapping, existingActiveInternationalMapping)) {
            for (MapEntry existingMapEntry : existingActiveMapping.getMapEntries()) {
                mapEntryRemoveList.add(existingMapEntry);
            }
        }
        // Now that all mapping-wide cases have been handled,
        // check entry-by-entry to determine which need to be added, removed, or modified
        else {
            // First loop through all existing map entries, and comparing against
            // the submitted map entries where Group and Priority match.
            // If the entries are equivalent, then no action is required.
            // If the entries are not equivalent, check if they are close enough to share a UUID in snowstorm.
            // If they do share a UUID, modify the existing map entry with the submitted map's information.
            // If the don't share a UUID, remove the existing map and add the submitted map.
            // Finally, if an existing map entry has no corresponding group/priority submitted map entry,
            // then that existing map entry needs to be removed.
            for (MapEntry existingMapEntry : existingActiveMapping.getMapEntries()) {
                boolean matchFound = false;
                for (MapEntry submittedMapEntry : submittedMapping.getMapEntries()) {
                    if (existingMapEntry.getGroup() == submittedMapEntry.getGroup() && existingMapEntry.getPriority() == submittedMapEntry.getPriority()) {
                        matchFound = true;
                        if (areMapEntriesEquivalent(existingMapEntry, submittedMapEntry)) {
                            // Equivalent map - no action required.
                        } else {
                            if (doMapEntriesShareUUID(existingMapEntry, submittedMapEntry)) {
                                mapEntryModifyMap.put(existingMapEntry, submittedMapEntry);
                            } else {
                                mapEntryRemoveList.add(existingMapEntry);
                                mapEntryAddList.add(submittedMapEntry);
                            }
                        }
                    }
                }
                if (matchFound == false) {
                    mapEntryRemoveList.add(existingMapEntry);
                }
            }

            // Now loop through all submitted map entries, to find any cases with no
            // corresponding group/priority existing entry.
            // These entries need to be added.
            for (MapEntry submittedMapEntry : submittedMapping.getMapEntries()) {
                boolean matchFound = false;
                for (MapEntry existingMapEntry : existingActiveMapping.getMapEntries()) {
                    if (existingMapEntry.getGroup() == submittedMapEntry.getGroup() && existingMapEntry.getPriority() == submittedMapEntry.getPriority()) {
                        matchFound = true;
                        // No further comparison needed - all modified entries were identified above.
                        break;
                    }
                }
                if (matchFound == false) {
                    mapEntryAddList.add(submittedMapEntry);
                }
            }
        }

        // Adding, removing, and modifying is handled differently depending on the existing
        // map entries in snowstorm.

        final Set<MapEntry> mapEntryCreateList = new HashSet<>();
        final Set<MapEntry> mapEntryInactivateList = new HashSet<>();
        final Set<MapEntry> mapEntryReactivateList = new HashSet<>();
        final Set<MapEntry> mapEntryDeleteList = new HashSet<>();
        final Set<MapEntry> mapEntryUpdateList = new HashSet<>();

        // For all map entries to be added, check if there are any UUI-matching, inactive, Norwegian entries in snowstorm.
        // If so, re-activate those existing entries, updating to match the submitted entry if needed.
        // If not, create a new entry.
        final Mapping existingInactiveNorwegianMapping =
            getMapping(branch, mapSetCode, submittedMapping.getCode(), mapProject.getModuleId(), false, false, false);

        for (MapEntry submittedMapEntry : mapEntryAddList) {
            boolean matchFound = false;
            for (MapEntry existingInactiveMapEntry : existingInactiveNorwegianMapping.getMapEntries()) {
                if (doMapEntriesShareUUID(existingInactiveMapEntry, submittedMapEntry)) {
                    matchFound = true;
                    if (!areMapEntriesEquivalent(existingInactiveMapEntry, submittedMapEntry)) {
                        existingInactiveMapEntry = updateExistingMapEntry(existingInactiveMapEntry, submittedMapEntry);
                    }
                    mapEntryReactivateList.add(existingInactiveMapEntry);
                    break;
                }
            }
            if (matchFound == false) {
                mapEntryCreateList.add(submittedMapEntry);
            }
        }

        // For all map entries to be removed, check if they have been previously released of not.
        // If so, then inactivate the entry
        // If not, then the entry can be fully deleted.
        for (MapEntry mapEntry : mapEntryRemoveList) {
            if (mapEntry.isReleased()) {
                mapEntryInactivateList.add(mapEntry);
            } else {
                mapEntryDeleteList.add(mapEntry);
            }
        }

        // For all modified map entries, check if the corresponding existing map entry has been previously released or not.
        // If not, then delete the existing map entry, and create a new entry using the submitted map entry
        // If so, then update the existing map entry with the submitted map entry's content
        for (MapEntry existingMapEntry : mapEntryModifyMap.keySet()) {
            MapEntry submittedMapEntry = mapEntryModifyMap.get(existingMapEntry);
            if (!existingMapEntry.isReleased()) {
                mapEntryDeleteList.add(existingMapEntry);
                mapEntryCreateList.add(submittedMapEntry);
            } else {
                existingMapEntry = updateExistingMapEntry(existingMapEntry, submittedMapEntry);
                mapEntryUpdateList.add(existingMapEntry);
            }
        }

        final MapSet mapSet = getMapSet(branch, mapSetCode);

        final ObjectMapper mapper = new ObjectMapper();
        final List<MapEntry> updatedMapEntries = new ArrayList<>();

        // Create Map Entry (Refset member)
        for (final MapEntry mapEntry : mapEntryCreateList) {
            // Clear out any existing UUID, since it's creating a new entry
            mapEntry.setId("");

            final String mapEntryJson = mapEntryToSnowstormMap(mapProject, mapSetCode, submittedMapping.getCode(), submittedMapping.getName(), mapEntry);
            LOG.info("Add mapping: {} with {}", targetUri, mapEntryJson);
            try (final Response response = SnowstormConnection.postResponse(targetUri, mapEntryJson)) {
                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new Exception(
                        "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
                }
                final JsonNode updatedMapEntryJson = mapper.readTree(response.readEntity(String.class));
                final MapEntry updatedMapEntry = convertSnowstormMemberToMapEntry(updatedMapEntryJson, mapSet, branch);
                updatedMapEntries.add(updatedMapEntry);
            }
        }

        // Delete Map Entry (Refset member)
        for (final MapEntry mapEntry : mapEntryDeleteList) {
            LOG.info("Delete mapping: {}", targetUri + mapEntry.getId());
            try (final Response response = SnowstormConnection.deleteResponse(targetUri + mapEntry.getId(), null)) {
                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new Exception(
                        "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
                }
                // only returns array of member ids
            }
        }

        // Inactivate Map Entry (Refset member)
        for (final MapEntry mapEntry : mapEntryInactivateList) {
            mapEntry.setActive(false);
            final String mapEntryJson = mapEntryToSnowstormMap(mapProject, mapSetCode, submittedMapping.getCode(), submittedMapping.getName(), mapEntry);
            LOG.info("Inactivate mapping: {} with {}", targetUri, mapEntryJson);
            try (final Response response = SnowstormConnection.putResponse(targetUri + mapEntry.getId(), mapEntryJson)) {
                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new Exception(
                        "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
                }
                final JsonNode updatedMapEntryJson = mapper.readTree(response.readEntity(String.class));
                final MapEntry updatedMapEntry = convertSnowstormMemberToMapEntry(updatedMapEntryJson, mapSet, branch);
                updatedMapEntries.add(updatedMapEntry);
            }
        }

        // Reactivate Map Entry (Refset member)
        for (final MapEntry mapEntry : mapEntryReactivateList) {
            mapEntry.setActive(true);
            final String mapEntryJson = mapEntryToSnowstormMap(mapProject, mapSetCode, submittedMapping.getCode(), submittedMapping.getName(), mapEntry);
            LOG.info("Reactivate mapping: {} with {}", targetUri, mapEntryJson);
            try (final Response response = SnowstormConnection.putResponse(targetUri + mapEntry.getId(), mapEntryJson)) {
                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new Exception(
                        "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
                }
                final JsonNode updatedMapEntryJson = mapper.readTree(response.readEntity(String.class));
                final MapEntry updatedMapEntry = convertSnowstormMemberToMapEntry(updatedMapEntryJson, mapSet, branch);
                updatedMapEntries.add(updatedMapEntry);
            }
        }

        // Update Map Entry (Refset member)
        for (final MapEntry mapEntry : mapEntryUpdateList) {
            final String mapEntryJson = mapEntryToSnowstormMap(mapProject, mapSetCode, submittedMapping.getCode(), submittedMapping.getName(), mapEntry);
            LOG.info("Update mapping: {} with {}", targetUri, mapEntryJson);
            try (final Response response = SnowstormConnection.putResponse(targetUri + mapEntry.getId(), mapEntryJson)) {
                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new Exception(
                        "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
                }
                final JsonNode updatedMapEntryJson = mapper.readTree(response.readEntity(String.class));
                final MapEntry updatedMapEntry = convertSnowstormMemberToMapEntry(updatedMapEntryJson, mapSet, branch);
                updatedMapEntries.add(updatedMapEntry);
            }
        }

        submittedMapping.getMapEntries().clear();
        submittedMapping.getMapEntries().addAll(updatedMapEntries);

        // // Handle edition-precedence in the map entries
        // handleEditionPrecedence(submittedMapping);

        // Sort all of the map entries in Group/Priority order
        sortMapEntries(submittedMapping);

        return submittedMapping;
    }

    /**
     * Gets the concepts by terminology.
     *
     * @param branch the branch
     * @param conceptCodes the concept codes
     * @return the concept
     * @throws Exception the exception
     */
    private static Map<String, Map<String, Concept>> getConcepts(final String branch, final Map<String, Set<String>> conceptCodes) throws Exception {

        // Map<terminology, Map<code, concept>>
        final Map<String, Map<String, Concept>> terminologyConceptMap = new HashMap<>();

        // for each terminology, get the concepts
        for (final String terminology : conceptCodes.keySet()) {

            final List<String> nonEmptyList =
                conceptCodes.get(terminology).stream().filter(str -> !Objects.isNull(str) && !str.isEmpty()).collect(Collectors.toList());

            final Map<String, Concept> concepts = getConceptsFromSnowstorm(branch, terminology, new ArrayList<>(nonEmptyList));

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
     * @return the concepts from snowstorm
     * @throws Exception the exception
     */
    private static Map<String, Concept> getConceptsFromSnowstorm(final String branch, final String terminology, final List<String> codes) throws Exception {

        if (codes == null || codes.isEmpty()) {
            return new HashMap<>();
        }

        final Map<String, Concept> conceptMap = new HashMap<>();

        // TODO: fix this hacky hardcoding
        if (!terminology.contains("SNOMEDCT")) {
            for (final String code : codes) {
                // TODO: get version for non-SNOMEDCT terminologies
                final Concept concept = SnowstormConcept.getConcept(terminology, "20240723", code);
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
        final ObjectMapper mapper = new ObjectMapper();

        final int maxIterations = Math.floorDiv(codes.size(), fetchLimit) + 1;

        for (int i = 0; i < maxIterations; i++) {

            final Set<String> fetchCodes = new HashSet<>();
            fetchCodes.addAll(codes.subList(i * fetchLimit, Math.min((i + 1) * fetchLimit, codes.size())));

            final String requestBody = requestBodyTempate.replace("CONCEPT_CODES", String.join("\",\"", fetchCodes)).replace("SEARCH_AFTER",
                StringUtils.isNotBlank(searchParameters.getSearchAfter()) ? searchParameters.getSearchAfter() : "");

            try (final Response response = SnowstormConnection.postResponse(targetUri, requestBody)) {

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new Exception(
                        "Call to URL '" + targetUri + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
                }

                final JsonNode doc = mapper.readTree(response.readEntity(String.class));
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

    /**
     * Sort map entries.
     *
     * @param mapping the mapping
     */
    private static void sortMapEntries(final Mapping mapping) {

        final List<MapEntry> entries = mapping.getMapEntries();
        entries.sort(Comparator.comparingInt(MapEntry::getGroup).thenComparingInt(MapEntry::getPriority));

        mapping.setMapEntries(entries);
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
            if (mapEntry.getModuleId().equals("449080006")) {
                internationalEntries.add(mapEntry);
            } else {
                editionEntries.add(mapEntry);
            }
        }

        // If any active edition map entries exist, use those. Otherwise, use international
        if (editionEntries.size() > 0) {
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
        final MapEntry mapEntry) {

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
        // Module id for created or updated map entries will always match the map project
        mapEntryJson.append("\"moduleId\": \"").append(mapProject.getModuleId()).append("\",");
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

        // TODO - figure out when/if correlationId will ever not be hardcoded as 447561005
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
     * Fix map entry advices.
     *
     * @param mapEntry the map entry
     * @return the sets the
     */
    private static Set<String> fixMapEntryAdvices(final MapEntry mapEntry) {

        // Handle "ALWAYS {target}" advices
        // TODO - this will be different for RULE-based projects
        final Set<String> mapEntryAdvices = new HashSet<>();
        mapEntryAdvices.addAll(mapEntry.getAdvices());
        boolean alwaysAdviceFound = false;
        for (final String advice : mapEntry.getAdvices()) {
            if (advice.startsWith("ALWAYS ")) {
                alwaysAdviceFound = true;
                // If no current target code, remove ALWAYS advice
                if (StringUtils.isBlank(mapEntry.getToCode())) {
                    mapEntryAdvices.remove(advice);
                }
                // If ALWAYS advice doesn't end with the current target code, remove and replace it.
                else if (!advice.endsWith(mapEntry.getToCode())) {
                    mapEntryAdvices.remove(advice);
                    mapEntryAdvices.add("ALWAYS " + mapEntry.getToCode());
                }
            }
        }
        // If there is a specified target and no ALWAYS advice found, add it
        if (!alwaysAdviceFound && !StringUtils.isBlank(mapEntry.getToCode())) {
            mapEntryAdvices.add("ALWAYS " + mapEntry.getToCode());
        }

        return mapEntryAdvices;
    }

    /**
     * Calculate map entry relation code.
     *
     * @param mapProject the map project
     * @param mapEntry the map entry
     * @return the string
     */
    private static String calculateMapEntryRelationCode(final MapProject mapProject, final MapEntry mapEntry) {

        // Calculate a map's relation code
        String relationCode = "";
        if (mapEntry.getRelation() != null) {
            final String relationString = mapEntry.getRelation();

            for (final MapRelation mapRelation : mapProject.getMapRelations()) {
                if (mapRelation.getName().toUpperCase().equals(relationString.toUpperCase())) {
                    relationCode = mapRelation.getTerminologyId();
                    break;
                }
            }
        }
        return relationCode;
    }

    /**
     * Return true if maps have equivalent content. This only considers the map information: target, advice, relations, etc. This does not compare other
     * information: release date, last modified, etc. Note: this intentionally ignores moduleId, so we can check edition maps against international maps
     *
     * @param mapping1 the mapping 1
     * @param mapping2 the mapping 2
     * @return the boolean
     */
    private static Boolean areMapsEquivalent(Mapping mapping1, Mapping mapping2) {

        // Check for null mappings
        if ((mapping1 == null && mapping2 == null)) {
            return true;
        }
        if ((mapping1 == null || mapping2 == null)) {
            return false;
        }

        // Check top-level mapping information
        if (!(mapping1.getCode().equals(mapping2.getCode()))) {
            return false;
        }

        // Check for null map entries
        if (mapping1.getMapEntries() == null && mapping2.getMapEntries() == null) {
            return true;
        }
        if (mapping1.getMapEntries() == null || mapping2.getMapEntries() == null) {
            return false;
        }

        // Check for map-entry count
        if (!(mapping1.getMapEntries().size() == mapping2.getMapEntries().size())) {
            return false;
        }

        // Sort the map entries for both mappings in Group/Priority order
        sortMapEntries(mapping1);
        sortMapEntries(mapping2);

        // Check individual map entry information
        // Since the map entries were sorted, we can compare entries by index location.
        for (int i = 0; i < mapping1.getMapEntries().size(); i++) {
            if (!areMapEntriesEquivalent(mapping1.getMapEntries().get(i), mapping2.getMapEntries().get(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Return true if map entries have equivalent content. This only considers the map information: target, advice, relations, etc. This does not compare other
     * information: release date, last modified, etc. Note: this intentionally ignores moduleId, so we can check edition maps against international maps
     *
     * @param mapEntry1 the map entry 1
     * @param mapEntry2 the map entry 2
     * @return the boolean
     */
    private static Boolean areMapEntriesEquivalent(MapEntry mapEntry1, MapEntry mapEntry2) {

        Boolean mapEntriesEquivalent = true;

        // Check for null map entries
        if (mapEntry1 == null && mapEntry2 == null) {
            return true;
        }
        if (mapEntry1 == null || mapEntry2 == null) {
            return false;
        }

        // Check individual map entry information
        mapEntriesEquivalent = mapEntry1.getGroup() == mapEntry2.getGroup() && mapEntry1.getPriority() == mapEntry2.getPriority()
            && Objects.equals(mapEntry1.getAdditionalMapEntryInfos(), mapEntry2.getAdditionalMapEntryInfos())
            && Objects.equals(mapEntry1.getAdvices(), mapEntry2.getAdvices()) && mapEntry1.getBlock() == mapEntry2.getBlock()
            && Objects.equals(mapEntry1.getRelationCode(), mapEntry2.getRelationCode()) && Objects.equals(mapEntry1.getRule(), mapEntry2.getRule())
            && Objects.equals(mapEntry1.getToCode(), mapEntry2.getToCode());

        return mapEntriesEquivalent;
    }

    /**
     * If two map entries have the same group, priority, target, and rule, then they represent the same object (i.e. will have the same UUID in snowstorm). This
     * determines whether changes should update an existing map entry, or create a new one.
     * 
     *
     * @param mapEntry1 the map entry 1
     * @param mapEntry2 the map entry 2
     * @return the boolean
     */
    private static Boolean doMapEntriesShareUUID(MapEntry mapEntry1, MapEntry mapEntry2) {

        Boolean mapEntriesShareUUID = true;

        // Check for null map entries
        if (mapEntry1 == null && mapEntry2 == null) {
            return true;
        }
        if (mapEntry1 == null || mapEntry2 == null) {
            return false;
        }

        // Check individual map entry information
        mapEntriesShareUUID =
            mapEntry1.getGroup() == mapEntry2.getGroup() && mapEntry1.getPriority() == mapEntry2.getPriority() && mapEntry1.getBlock() == mapEntry2.getBlock()
                && Objects.equals(mapEntry1.getRule(), mapEntry2.getRule()) && Objects.equals(mapEntry1.getToCode(), mapEntry2.getToCode());

        return mapEntriesShareUUID;
    }

    /**
     * Update an existing map entry with the non-defining content of the submitted map entry This can only be done on map entries that share a UUID
     * 
     *
     * @param existingMapEntry the existing map entry
     * @param submittedMapEntry the submitted map entry
     * @return the map entry
     */
    private static MapEntry updateExistingMapEntry(MapEntry existingMapEntry, MapEntry submittedMapEntry) throws Exception {

        if (!doMapEntriesShareUUID(existingMapEntry, submittedMapEntry)) {
            throw new Exception("You cannot update an existing map entry with a non UUID-sharing new entry");
        }

        existingMapEntry.setAdditionalMapEntryInfos(submittedMapEntry.getAdditionalMapEntryInfos());
        existingMapEntry.setAdvices(submittedMapEntry.getAdvices());
        existingMapEntry.setRelation(submittedMapEntry.getRelation());
        existingMapEntry.setRelationCode(submittedMapEntry.getRelationCode());

        return existingMapEntry;
    }

    /**
     * Export mappings.
     *
     * @param branch the branch
     * @param mapSetCode the map set code
     * @param mappingExportRequest the mapping export request
     * @return the file
     * @throws Exception the exception
     */
    public static File exportMappings(final String branch, final String mapSetCode, final MappingExportRequest mappingExportRequest) throws Exception {

        final SearchParameters sp = new SearchParameters();
        sp.setLimit(10000); // Snowstorm limitation

        final ResultListMapping mappings = getMappings(branch, mapSetCode, sp, "", false, mappingExportRequest.getConceptCodes());
        final File zipFile = exportMappingFilesToDownload(mappings, mappingExportRequest.getColumnNames());

        return zipFile;
    }

    /**
     * Export the members based on user selection in a zipped pkg containing tab delimited format.
     * @author vparekh
     * @param mappings the mappings
     * @param includedColumnsList the column list
     * @return the zip pkg containing the txt file with the member list export
     * @throws Exception the exception
     */
    private static File exportMappingFilesToDownload(final ResultListMapping mappings, final List<String> includedColumnsList) throws Exception {

        // Validate and create download directory if it doesn't exist
        final String outputDirPath = PropertyUtility.getProperty("mapexport.fileDir");
        final String outputFileName = PropertyUtility.getProperty("mapexport.file");
        final String DILIMITER = "\t";

        final File downloadDir = new File(outputDirPath);
        if (!downloadDir.exists()) {
            if (!downloadDir.mkdirs()) {
                throw new IOException("Failed to create download directory for mapping export.  Directory: " + downloadDir);
            }
        }

        // Create the output file
        final File outputFile = new File(downloadDir, outputFileName);
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-hhmmss");

        // TODO: What is the proper file name for the zip file?
        final String zipFileName = String.format("MT2-Downloaded-mapsets-%s.zip", dateFormat.format(new Date()));
        final List<String> allowedColumns =
            Arrays.asList("Source", "Source PT", "Target", "Target PT", "Group", "Priority", "Relationship", "Rule", "Advices", "Last Modified");

        // Based on the includedColumns values create the customized text file creation - map column with values
        try (final PrintWriter writer = new PrintWriter(new FileOutputStream(outputFile))) {

            // Create a mutable copy of the includedColumnsList to avoid UnsupportedOperationException
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
                    dataRow.put("Last Modified", entry.getModified() != null ? DateUtility.formatDate(entry.getModified(), DateUtility.DATE_FORMAT_REVERSE, null) : "N/A");

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
        } catch (Exception e) {
            e.printStackTrace();
            LOG.error("Error during ZIP file creation or download", e);
            throw e;
        }

        final List<String> sourceFiles = new ArrayList<>();
        sourceFiles.add(outputFile.getAbsolutePath());
        final File zipMapFile = FileUtility.zipFiles(sourceFiles, zipFileName);

        return zipMapFile;
    }

	
    
    /**
     * Import RF2 mappings.
     * @param mapProject the mapProject
     * @param branch the branch
     * @param mappingFile the RF2 mappingFile
     * @return the mappings updates
     * @throws Exception the exception
     */
    public static List<Mapping> importMappings(final MapProject mapProject, final String branch, final MultipartFile mappingFile) throws Exception {

        final List<Mapping> mappings = getMappingsFromFile(mappingFile); // return mapsetCode 
        final List<Mapping> updatedRF2Mappings = new ArrayList<>();
        final List<String> conceptIds = new ArrayList<>();
        LOG.info("importMappings -RF2 Mapping obj  : {}", mappings);
        for (final Mapping mapping : mappings) {
        	
            final Mapping updatedRF2Mapping = updateMapping(mapProject, branch, mapping.getMapSetId(), mapping);
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
     * @return the mappings from file
     * @throws Exception the exception
     */
    private static List<Mapping> getMappingsFromFile(MultipartFile mappingFile) throws Exception {
		 	Map<String, Mapping> mappingMap = new HashMap<>(); // Keyed by "Source"
	        List<Mapping> mappings = new ArrayList<>();

	            final List<String> rf2Lines = FileUtility.readFileToArray(mappingFile);
	            if (rf2Lines.isEmpty()) {
	                throw new Exception("The file is empty.");
	            }
	            
	            // Extract header and determine column indices
	            String headerLine = rf2Lines.remove(0);
	            String[] headers = headerLine.split("\t");
	            Map<String, Integer> columnIndices = new HashMap<>();
	            for (int i = 0; i < headers.length; i++) {
	                columnIndices.put(headers[i], i);
	            }	           

	            for (final String line : rf2Lines) {
	            	if (line.trim().isEmpty()) {
	                    continue; // Skip empty lines
	                }
	            	
	                try {
	                	String[] columns = line.split("\t");
	                	String active = columns[columnIndices.get("active")]; 
	                	String moduleId = columns[columnIndices.get("moduleId")]; 
	                	String referencedComponentId = columns[columnIndices.get("referencedComponentId")]; //source
	                	String refsetId = columns[columnIndices.get("refsetId")]; // mapSetCode	                   
	                    int mapGroup = Integer.parseInt(columns[columnIndices.get("mapGroup")]); 
	                    int mapPriority = Integer.parseInt(columns[columnIndices.get("mapPriority")]); 
	                    //String correlationId = columns[columnIndices.get("correlationId")]; //Hardcoded value used - 447562003
	                    String mapRule = columns[columnIndices.get("mapRule")]; 
	                    String mapAdvice = columns[columnIndices.get("mapAdvice")]; 
	                    String mapTarget = columns[columnIndices.get("mapTarget")]; 	   
	                    String relationCode = columns[columnIndices.get("mapCategoryId")]; 	                     
	                    // Create a new MapEntry object
	                    MapEntry mapEntry = new MapEntry();	                   
	                    if ("1".equals(active)) {
	                        mapEntry.setActive(true);
	                    } else if ("0".equals(active)) {
	                        mapEntry.setActive(false);
	                    }
	                    mapEntry.setModuleId(moduleId); //moduleId
	                    mapEntry.setGroup(mapGroup); //mapGroup
	                    mapEntry.setPriority(mapPriority); //mapPriority	                 
	                    mapEntry.setRule(mapRule); //mapRule
	                    mapEntry.setToCode(mapTarget); //mapTarget
	                    //mapEntry.setRelation(correlationId); //correlationId
	                    mapEntry.addAdvice(mapAdvice); //mapAdvice
	                    mapEntry.setRelationCode(relationCode); //mapCategoryId
	               
	                    // Check if a Mapping object already exists for this source
	                    Mapping mapping = mappingMap.get(referencedComponentId);	              
	                    if (mapping == null) {
	                        mapping = new Mapping();
	                        mapping.setMapSetId(refsetId);
	                        mapping.setCode(referencedComponentId);
	                        mapping.setName("Mapping for " + referencedComponentId);
	                        mapping.setMapEntries(new ArrayList<>());
	                        mappingMap.put(referencedComponentId, mapping);
	                        mappings.add(mapping);
	                    }

	                    // Add the MapEntry to the Mapping
	                    mapping.getMapEntries().add(mapEntry);	                
	                } catch (final Exception e) {
	                    continue;
	                }
	            }	
	            LOG.info("getMappingsFromFile RF2 Mappings : {}", mappings);

	        return mappings;
		}
	

}
