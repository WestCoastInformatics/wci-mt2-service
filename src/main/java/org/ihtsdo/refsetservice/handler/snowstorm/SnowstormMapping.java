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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.ihtsdo.refsetservice.util.LocalException;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// TODO: Auto-generated Javadoc
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

    final String targetUri = SnowstormConnection.getBaseUrl() + branch
        + "/concepts?activeFilter=true&ecl=%3C609331003&includeLeafFlag=false&form=inferred&offset=0&limit="
        + limit + (searchAfter != null ? "&searchAfter=" + searchAfter : "");
    LOG.info("getSnowstormMapsets url: " + targetUri);

    final WebTarget target = client.target(targetUri);
    final Response response = target.request(DEFAULT_ACCEPT)
        // .header("Cookie", ConfigUtility.getGenericUserCookie())
        .get();
    final String resultString = response.readEntity(String.class);
    if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
      throw new Exception("Call to URL '" + targetUri + "' wasn't successful. Status: "
          + response.getStatus() + " Message: " + formatErrorMessage(response));
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
      mapSet.setToTerminology("TBD");

      // TEMPORARY//
      if (mapSet.getRefSetCode().equals("447562003")) {
        mapSet.setToTerminology("ICD10NO");
      } else if (mapSet.getRefSetCode().equals("68101000202102")) {
        mapSet.setToTerminology("ICPC2NO");
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

    final String targetUri = SnowstormConnection.getBaseUrl() + branch
        + "/concepts?activeFilter=true&includeLeafFlag=false&form=inferred&conceptIds=" + code
        + SnowstormApiPaging.getPagingQueryString(null);

    LOG.info("getSnowstormMapset url: " + targetUri);

    final WebTarget target = client.target(targetUri);

    final Response response = target.request(DEFAULT_ACCEPT)
        // .header("Cookie", ConfigUtility.getGenericUserCookie())
        .get();
    final String resultString = response.readEntity(String.class);
    if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
      throw new Exception("Call to URL '" + targetUri + "' wasn't successful. Status: "
          + response.getStatus() + " Message: " + formatErrorMessage(response));
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
      mapSet.setToTerminology("TBD");

      // TEMPORARY//
      if (mapSet.getRefSetCode().equals("447562003")) {
        mapSet.setToTerminology("ICD10NO");
      } else if (mapSet.getRefSetCode().equals("68101000202102")) {
        mapSet.setToTerminology("ICPC2NO");
      }
      // TEMPORARY//

      return mapSet;
    }

    return null;
  }
  
  /**
   * Update map set.
   *
   * @param branch the branch
   * @param mapSet the map set
   * @return the map set
   * @throws Exception the exception
   */
  public static MapSet updateMapSet(final String branch, final MapSet mapSet) throws Exception {

      // TODO - implement
      return null;
  }

  /**
   * Gets the mappings.
   *
   * @param branch the branch
   * @param mapSetCode the map set code
   * @param searchParameters the search parameters
   * @param filter the filter
   * @param conceptCodes the concept codes
   * @return the mappings
   * @throws Exception the exception
   */
  public static ResultList<Mapping> getMappings(final String branch, final String mapSetCode,
    final SearchParameters searchParameters, final String filter, final List<String> conceptCodes)
    throws Exception {

    if (StringUtils.isBlank(mapSetCode)) {
      throw new LocalException("Map set code is required.");
    }

    final List<String> filteredConceptList = new ArrayList<>();
    if (StringUtils.isNotBlank(filter)) {
      filteredConceptList.addAll(searchConcepts(branch, mapSetCode, filter));
    }

    final StringBuilder requestBody = new StringBuilder();
    requestBody.append("{");
    requestBody.append("\"active\": true,");
    requestBody.append("\"referenceSet\": \"").append(mapSetCode).append("\"");
    if (filteredConceptList != null && !filteredConceptList.isEmpty()) {
      requestBody.append(",").append("\"referencedComponentIds\": [")
          .append(String.join(",", filteredConceptList)).append("]");
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
    final Map<String, Mapping> conceptIdToMappingMap = new HashMap<>();

    final Map<String, Set<String>> conceptsToLookup = new HashMap<>();
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

      final String targetUri = SnowstormConnection.getBaseUrl() + branch + "/members/search?"
          + SnowstormApiPaging.getPagingQueryString(searchParameters);
      LOG.debug("getSnowstormMappings url: {}", targetUri);
      LOG.debug("request body: {}", requestBody.toString());

      try (final Response response =
          SnowstormConnection.postResponse(targetUri, requestBody.toString())) {

        if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
          throw new Exception("Call to URL '" + targetUri + "' wasn't successful. Status: "
              + response.getStatus() + " Message: " + formatErrorMessage(response));
        }

        final JsonNode doc = mapper.readTree(response.readEntity(String.class));
        final JsonNode mappingsBatch = doc.get("items");
        if (mappingsBatch.isArray() && mappingsBatch.isEmpty()) {
          done = true;
          continue;
        }

        if (searchParameters != null) {
          if (doc.has("total")) {
            total = doc.get("total").asInt();
          }
          if (doc.has("limit")) {
            limit = doc.get("limit").asInt();
          }
          if (doc.has("offset")) {
            offset = doc.get("offset").asInt();
          }
          if (doc.has("searchAfter")) {
            searchAfter = doc.get("searchAfter").asText();
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
          final MapEntry mapEntry = new MapEntry();

          mapEntry.setModified(
              new SimpleDateFormat("yyyyMMdd").parse(mappingNode.get("effectiveTime").asText()));

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

          mapEntry.setRelationCode(additionalFields.get("mapCategoryId").asText());
          conceptsToLookup.get(fromTerminology).add(mapEntry.getRelationCode());

          mapEntry.setToCode(additionalFields.get("mapTarget").asText());
          conceptsToLookup.get(toTerminology).add(mapEntry.getToCode());

          final List<MapEntry> mapEntries = mapping.getMapEntries();
          mapEntries.add(mapEntry);
          mapping.setMapEntries(mapEntries);

        }

      }

      i++;
      searchParameters.setOffset(i * searchParameters.getLimit());
      if (searchParameters.getOffset() >= searchParameters.getLimit()) {
        done = true;
      }
    }

    // get a list of codes to get concepts
    final Map<String, Map<String, Concept>> terminologyConceptMap =
        getConcepts(branch, conceptsToLookup);
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

        final Concept relationConcept =
            terminologyConceptMap.get(fromTerminology).get(entry.getRelationCode());
        entry.setRelation(relationConcept != null ? relationConcept.getName()
            : entry.getRelationCode() + " CONCEPT NOT FOUND");

        final Concept toConcept = terminologyConceptMap.get(toTerminology).get(entry.getToCode());
        entry.setToName(
            toConcept != null ? toConcept.getName() : entry.getToCode() + " CONCEPT NOT FOUND");

      }
    }

    // Handle edition-precedence in the map entries
    for (final Mapping mapping : conceptIdToMappingMap.values()) {
      handleEditionPrecedence(mapping);
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

    final Map<String, List<Description>> descriptions =
        SnowstormDescription.getDescriptions(edition, conceptIds);

    // Sort all of the map entries in Group/Priority order
    for (final Mapping mapping : conceptIdToMappingMap.values()) {
      sortMapEntries(mapping);
      mapping.setDescriptions(descriptions.get(mapping.getCode()));
    }

    // Once the file is completed parsed, return mappings as list
    final ResultList<Mapping> mappings = new ResultList<>();
    mappings.getItems().addAll(conceptIdToMappingMap.values());
    mappings.setTotal(total);
    mappings.setTotalKnown(total > 0);
    mappings.setLimit(limit);
    mappings.setOffset(offset);
    mappings.setSearchAfter(searchAfter);

    return mappings;

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
  private static List<String> searchConcepts(final String branch, final String mapSetCode,
    final String searchString) throws Exception {

    // Connect to snowstorm
    String searchAfter = "";

    final String targetUri = SnowstormConnection.getBaseUrl() + branch + "/concepts/search";

    final StringBuilder requestBodyTemplate = new StringBuilder();
    requestBodyTemplate.append("{");
    requestBodyTemplate.append("\"termFilter\": \"").append(searchString).append("\",");
    requestBodyTemplate.append("\"eclFilter\": \"^").append(mapSetCode).append("\",");
    requestBodyTemplate.append("\"limit\": ").append(5000).append(",");
    requestBodyTemplate.append("\"returnIdOnly\": true,");
    // Is replaced with actual searchAfter value
    requestBodyTemplate.append("\"searchAfter\": \"SEARCH_AFTER\"");
    requestBodyTemplate.append("}");

    final List<String> conceptCodes = new ArrayList<>();

    boolean done = false;
    while (!done) {

      final String requestBodyString =
          requestBodyTemplate.toString().replace("SEARCH_AFTER", searchAfter);

      try (final Response response =
          SnowstormConnection.postResponse(targetUri, requestBodyString)) {

        if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
          throw new Exception("Call to URL '" + targetUri + "' wasn't successful. Status: "
              + response.getStatus() + " Message: " + formatErrorMessage(response));
        }

        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode doc = mapper.readTree(response.readEntity(String.class));

        final JsonNode conceptNodeBatch = doc.get("items");
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
        if (doc.has("searchAfter")) {
          searchAfter = doc.get("searchAfter").asText();
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
   * @return the mapping
   * @throws Exception the exception
   */
  public static Mapping getMapping(final String branch, final String mapSetCode,
    final String conceptCode) throws Exception {

    // Grab the specified mapSet
    final MapSet mapSet = getMapSet(branch, mapSetCode);

    final Mapping mapping = new Mapping();

    // Connect to snowstorm
    final Client client = getClients().get();
    String searchAfter = null;
    int limit = 50;

    final String targetUri = SnowstormConnection.getBaseUrl() + branch + "/members?referenceSet="
        + mapSetCode + "&referencedComponentId=" + conceptCode + "&active=true&limit=" + limit
        + (searchAfter != null ? "&searchAfter=" + searchAfter : "");
    LOG.info("getSnowstormMapping url: " + targetUri);

    final WebTarget target = client.target(targetUri);

    final Response response = target.request(DEFAULT_ACCEPT)
        // .header("Cookie", ConfigUtility.getGenericUserCookie())
        .get();
    final String resultString = response.readEntity(String.class);
    if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
      throw new LocalException("Unexpected terminology server failure. Message = " + resultString);
    }

    final ObjectMapper mapper = new ObjectMapper();
    final JsonNode doc = mapper.readTree(resultString);

    final JsonNode mappingsBatch = doc.get("items");

    final Iterator<JsonNode> itemIterator = mappingsBatch.iterator();

    // parse items to retrieve matching concept
    while (itemIterator.hasNext()) {

      final JsonNode mappingNode = itemIterator.next();

      // If this is the first time the fromConcept is encountered, set up the
      // mapping
      if (mapping.getCode() == null || mapping.getCode().isEmpty()) {
        mapping.setCode(mappingNode.get("referencedComponentId").asText());
        mapping.setName(SnowstormConcept
            .getConcept(branch, mapSet.getFromTerminology(), "", mapping.getCode()).getName());
        mapping.setMapSetId(mapSet.getId());
        mapping.setMapEntries(new ArrayList<>());
      }

      // Add an entry to the mapping
      final MapEntry mapEntry = new MapEntry();

      if (mappingNode.has("effectiveTime")) {
          mapEntry.setModified(new SimpleDateFormat("yyyyMMdd").parse(mappingNode.get("effectiveTime").asText()));
      }      
      mapEntry.setId(mappingNode.get("memberId").asText());

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

      final Concept relationConcept = SnowstormConcept.getConcept(branch,
          mapSet.getFromTerminology(), "", additionalFields.get("mapCategoryId").asText());
      if (relationConcept != null) {
        mapEntry.setRelation(relationConcept.getName());
      } else {
        mapEntry.setRelation(mapEntry.getToCode() + " CONCEPT NOT FOUND");
      }

      mapEntry.setToCode(additionalFields.get("mapTarget").asText());

      final Concept toConcept = SnowstormConcept.getConcept(branch, mapSet.getToTerminology(), "", 
          additionalFields.get("mapTarget").asText());

      if (toConcept != null) {
        mapEntry.setToName(toConcept.getName());
      } else {
        mapEntry.setToName(mapEntry.getToCode() + " CONCEPT NOT FOUND");
      }

      final List<MapEntry> mapEntries = mapping.getMapEntries();
      mapEntries.add(mapEntry);
      mapping.setMapEntries(mapEntries);

    }

    // Handle edition-precedence in the map entries
    handleEditionPrecedence(mapping);

    // Sort all of the map entries in Group/Priority order
    sortMapEntries(mapping);

    // Get descriptions for mapping
    final Edition edition = new Edition();
    edition.setActive(true);
    edition.setAbbreviation("NO");
    edition.setDefaultLanguageCode("no");
    edition.getDefaultLanguageRefsets().add("61000202103");
    edition.getDefaultLanguageRefsets().add("900000000000509007");
    edition.setShortName("SNOMEDCT-NO");
    edition.setBranch(branch);

    final List<String> conceptIds = new ArrayList<>();
    conceptIds.add(mapping.getCode());
    final Map<String, List<Description>> descriptions =
        SnowstormDescription.getDescriptions(edition, conceptIds);
    mapping.setDescriptions(descriptions.get(mapping.getCode()));

    return mapping;
  }

  /**
   * Creates the mapping.
   *
   * @param branch the branch
   * @param mapSetCode the map set code
   * @param mapping the mapping
   * @return the mapping
   * @throws Exception the exception
   */
  public static void createMapping(final String branch, final String mapSetCode,
    final Mapping mapping) throws Exception {

    final List<String> mapEntriesJson = new ArrayList<>();

    for (final MapEntry mapEntry : mapping.getMapEntries()) {
      mapEntriesJson
          .add(mapEntryToSnowstormMap(mapSetCode, mapping.getName(), mapping.getCode(), mapEntry));
    }

    final String targetUri = SnowstormConnection.getBaseUrl() + branch + "/members";

    // add each map entry to snowstorm
    for (final String mapEntryJson : mapEntriesJson) {

      try (final Response response = SnowstormConnection.postResponse(targetUri, mapEntryJson)) {

        if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
          throw new LocalException("Unexpected terminology server failure. Message = "
              + response.readEntity(String.class));
        }

      }
    }

  }

  /**
   * Update mapping.
   *
   * @param branch the branch
   * @param mapSetCode the map set code
   * @param mapping the mapping
   * @return the mapping
   * @throws Exception the exception
   */
  public static void updateMapping(final String branch, final String mapSetCode,
    final Mapping mapping) throws Exception {

    final String targetUri = SnowstormConnection.getBaseUrl() + branch + "/members/";

    for (final MapEntry mapEntry : mapping.getMapEntries()) {

      final String mapEntryJson =
          mapEntryToSnowstormMap(mapSetCode,mapping.getCode(), mapping.getName(), mapEntry);

      LOG.info("Snowstorm: Update mapping: {} with {}", targetUri + mapEntry.getId(), mapEntryJson);
      
      try (final Response response =
          SnowstormConnection.putResponse(targetUri + mapEntry.getId(), mapEntryJson)) {

        if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
          throw new LocalException("Unexpected terminology server failure. Message = "
              + response.readEntity(String.class));
        }

      }

    }

  }

  /**
   * Gets the concepts by terminology.
   *
   * @param branch the branch
   * @param conceptCodes the concept codes
   * @return the concept
   * @throws Exception the exception
   */
  private static Map<String, Map<String, Concept>> getConcepts(final String branch,
    final Map<String, Set<String>> conceptCodes) throws Exception {

    // Map<terminology, List<code, concept>>
    final Map<String, Map<String, Concept>> terminologyConceptMap = new HashMap<>();

    // for each terminology, get the concepts
    for (final String terminology : conceptCodes.keySet()) {

      final List<String> nonEmptyList = conceptCodes.get(terminology).stream()
          .filter(str -> !str.isEmpty()).collect(Collectors.toList());

      final Map<String, Concept> concepts =
          getConceptsFromSnowstorm(branch, terminology, new ArrayList<>(nonEmptyList));

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
  private static Map<String, Concept> getConceptsFromSnowstorm(final String branch,
    final String terminology, final List<String> codes) throws Exception {

    if (codes == null || codes.isEmpty()) {
      return new HashMap<>();
    }

    final Map<String, Concept> conceptMap = new HashMap<>();
    
    //TODO: fix this hacky hardcoding
    if(!terminology.contains("SNOMED")) {
    	for(String code : codes) {
    		final Concept concept = SnowstormConcept.getConcept(branch, terminology, "", code);
    		conceptMap.put(code, concept);
    	}
    	return conceptMap;
    }
    
    LOG.debug("Codes to look up: {}", codes);

    final Integer fetchLimit = 1000;
    final SearchParameters searchParameters = new SearchParameters();
    searchParameters.setLimit(fetchLimit);
    searchParameters.setSearchAfter("");

    final String targetUri = SnowstormConnection.getBaseUrl() + branch + "/concepts/search";
    final String requestBodyTempate =
        "{ \"conceptIds\": [\"CONCEPT_CODES\"], \"searchAfter\": \"SEARCH_AFTER\", \"limit\": "
            + fetchLimit + "}";
    final ObjectMapper mapper = new ObjectMapper();

    int maxIterations = Math.floorDiv(codes.size(), fetchLimit) + 1;

    for (int i = 0; i < maxIterations; i++) {

      final Set<String> fetchCodes = new HashSet<>();
      fetchCodes
          .addAll(codes.subList(i * fetchLimit, Math.min((i + 1) * fetchLimit, codes.size())));

      final String requestBody =
          requestBodyTempate.replace("CONCEPT_CODES", String.join("\",\"", fetchCodes))
              .replace("SEARCH_AFTER", StringUtils.isNotBlank(searchParameters.getSearchAfter())
                  ? searchParameters.getSearchAfter() : "");

      try (final Response response = SnowstormConnection.postResponse(targetUri, requestBody)) {

        if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
          throw new Exception("Call to URL '" + targetUri + "' wasn't successful. Status: "
              + response.getStatus() + " Message: " + formatErrorMessage(response));
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
    entries
        .sort(Comparator.comparingInt(MapEntry::getGroup).thenComparingInt(MapEntry::getPriority));

    mapping.setMapEntries(entries);
  }

  // Handle edition-precedence in the map entries
  // For example: if there is an International map entry (module=449080006) for
  // group 1, priority 1,
  // And also a Norwegian map entry (module=51000202101) for group 1, priority
  // 1,
  // then the Edition/Norwegian map entry should be kept, and the international
  /**
   * Handle edition precedence.
   *
   * @param mapping the mapping
   */
  // one dropped.
  private static void handleEditionPrecedence(final Mapping mapping) {
    final Map<String, MapEntry> groupPriorityToEntryMap = new HashMap<>();
    for (MapEntry mapEntry : mapping.getMapEntries()) {
      final String key = mapEntry.getGroup() + "-" + mapEntry.getPriority();
      if (groupPriorityToEntryMap.containsKey(key)) {
        final MapEntry existingMapEntry = groupPriorityToEntryMap.get(key);
        if (!existingMapEntry.getModuleId().equals("449080006")
            && mapEntry.getModuleId().equals("449080006")) {
          // Keep existing map entry if it does not have moduleId 449080006
          continue;
        }
      }
      groupPriorityToEntryMap.put(key, mapEntry);
    }

    // Set the remaining map entries to the mapping
    final List<MapEntry> remainingMapEntries = new ArrayList<>(groupPriorityToEntryMap.values());
    mapping.setMapEntries(remainingMapEntries);
  }

  /**
   * Map entry to snowstorm map.
   *
   * @param refsetId the refset id
   * @param fromCode the from code
   * @param fromName the from name
   * @param mapEntry the map entry
   * @return the string
   */
  private static String mapEntryToSnowstormMap(final String refsetId, final String fromCode,
    final String fromName, final MapEntry mapEntry) {

    // snowstorm map example
    /*
     * { "active": true, "moduleId": "449080006", "released": true,
     * "releasedEffectiveTime": 20150731, "memberId":
     * "baaae0b7-f564-505e-b604-0bbdd60a69f4", "refsetId": "447562003",
     * "referencedComponentId": "70273001", "additionalFields": {
     * "mapCategoryId": "447637006", "mapRule": "TRUE", "mapAdvice":
     * "ALWAYS X40 | MAPPED FOLLOWING WHO GUIDANCE | POSSIBLE REQUIREMENT FOR PLACE OF OCCURRENCE"
     * , "mapPriority": "1", "mapGroup": "2", "correlationId": "447561005",
     * "mapTarget": "X40" }, "referencedComponent": { "conceptId": "70273001",
     * "active": true, "definitionStatus": "FULLY_DEFINED", "moduleId":
     * "900000000000207008", "fsn": { "term":
     * "Poisoning caused by paracetamol (disorder)", "lang": "en" }, "pt": {
     * "term": "Poisoning caused by acetaminophen", "lang": "en" }, "id":
     * "70273001" }, "effectiveTime": "20150731" }
     */

    final StringBuilder mapEntryJson = new StringBuilder();

    mapEntryJson.append("{");
    if (StringUtils.isNotBlank(mapEntry.getId())) {
        mapEntryJson.append("\"memberId\": \"").append(mapEntry.getId()).append("\",");
    }
    else {
        mapEntryJson.append("\"memberId\": \"").append(UUID.randomUUID().toString()).append("\",");
    }
    mapEntryJson.append("\"active\": ").append(mapEntry.isActive()).append(",");
    mapEntryJson.append("\"moduleId\": \"").append(mapEntry.getModuleId()).append("\",");
    // mapEntryJson.append("\"released\": false,");
    // mapEntryJson.append("\"releasedEffectiveTime\": 20240415,");
    mapEntryJson.append("\"refsetId\": \"").append(refsetId).append("\",");
    mapEntryJson.append("\"referencedComponentId\": \"").append(fromCode).append("\",");

    // additional fields
    mapEntryJson.append("\"additionalFields\": {");
    mapEntryJson.append("\"mapCategoryId\": \"").append(mapEntry.getRelationCode()).append("\",");
    mapEntryJson.append("\"mapRule\": \"").append(mapEntry.getRule()).append("\",");
    mapEntryJson.append("\"mapAdvice\": \"").append(String.join(" | ", mapEntry.getAdvices()))
        .append("\",");
    mapEntryJson.append("\"mapPriority\": ").append(mapEntry.getPriority()).append(",");
    mapEntryJson.append("\"mapGroup\": ").append(mapEntry.getGroup()).append(",");

    // TODO - what is correlationId?
    if (mapEntry.getAdditionalMapEntryInfos() != null && !mapEntry.getAdditionalMapEntryInfos().isEmpty()) {
        final String correlationId =
            mapEntry.getAdditionalMapEntryInfos().stream().filter(info -> info.getName().equals("correlationId")).findFirst().get().getValue();

        if (StringUtils.isNotBlank(correlationId)) {
            mapEntryJson.append("\"correlationId\": \"").append(correlationId).append("\",");
        }
    }
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

    // TODO: replace hard coded effective
    mapEntryJson.append("\"effectiveTime\": \"20240415\"");
    mapEntryJson.append("}");

    return mapEntryJson.toString();

  }

}
