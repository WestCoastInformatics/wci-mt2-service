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
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.Concept;
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

/**
 * The Class SnowstormMapping.
 */
public class SnowstormMapping extends SnowstormAbstract {

  /** The Constant LOG. */
  private static final Logger LOG = LoggerFactory.getLogger(SnowstormMapping.class);

  /** The Constant DEFAULT_ACCEPT. */
  private static final String DEFAULT_ACCEPT = MediaType.APPLICATION_JSON;

  private static final Map<String, String> icd10noCodeToName = new HashMap<>();
  private static final Map<String, String> icpc2noCodeToName = new HashMap<>();

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
   * @return the map sets
   * @throws Exception the exception
   */
  public static List<MapSet> getMapSets() throws Exception {

    final ArrayList<MapSet> mapSets = new ArrayList<>();

    // Connect to snowstorm
    final Client client = getClients().get();

    String searchAfter = null;

    final int limit = 50;
    final String branch = "MAIN%2FSNOMEDCT-NO%2F2024-04-15";

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

      //TEMPORARY - only keep ICD10NO (447562003) and ICPC2NO (68101000202102) maps//
      final String refsetId = mapSetNode.get("conceptId").asText();
      if (!(refsetId.equals("447562003") || refsetId.equals("68101000202102"))) {
          continue;
      }
      //TEMPORARY//
      
      
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

      //TEMPORARY//
      if(mapSet.getRefSetCode().equals("447562003")) {
          mapSet.setToTerminology("ICD10NO");
      }
      else if (mapSet.getRefSetCode().equals("68101000202102")) {
          mapSet.setToTerminology("ICPC2NO");          
      }
      //TEMPORARY//
      
      mapSets.add(mapSet);
    }

    return mapSets;
  }

  /**
   * Gets the map set.
   *
   * @param code the code
   * @return the map set
   * @throws Exception the exception
   */
  public static MapSet getMapSet(final String code) throws Exception {

    final Client client = getClients().get();

    final SearchParameters searchParameters = new SearchParameters();
    searchParameters.setLimit(50);
    searchParameters.setSearchAfter(null);

    final String branch = "MAIN%2FSNOMEDCT-NO%2F2024-04-15";
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
      
      //TEMPORARY//
      if(mapSet.getRefSetCode().equals("447562003")) {
          mapSet.setToTerminology("ICD10NO");
      }
      else if (mapSet.getRefSetCode().equals("68101000202102")) {
          mapSet.setToTerminology("ICPC2NO");          
      }
      //TEMPORARY//
      

      return mapSet;
    }

    return null;
  }

  /**
   * Gets the mappings.
   *
   * @param mapSetCode the map set code
   * @param searchParameters the search parameters
   * @param filter the filter
   * @param conceptCodes the concept codes
   * @return the mappings
   * @throws Exception the exception
   */
  public static ResultList<Mapping> getMappings(final String mapSetCode,
    final SearchParameters searchParameters, final String filter, final List<String> conceptCodes)
    throws Exception {

    if (StringUtils.isBlank(mapSetCode)) {
      throw new LocalException("Map set code is required.");
    }

    final List<String> filteredConceptList = new ArrayList<>();
    if (StringUtils.isNotBlank(filter)) {
      filteredConceptList.addAll(searchConcepts(mapSetCode, filter));
    }

    final String branch = "MAIN%2FSNOMEDCT-NO%2F2024-04-15"; 

    final StringBuilder requestBody = new StringBuilder();
    requestBody.append("{");
    requestBody.append("\"active\": true,");
    requestBody.append("\"referenceSet\": \"").append(mapSetCode).append("\"");
    if (filteredConceptList != null && !filteredConceptList.isEmpty()) {
      requestBody.append(",").append("\"referencedComponentIds\": [")
          .append(String.join(",", filteredConceptList)).append("]");
    }
//    if (searchParameters.getLimit() != null) {
//      requestBody.append(",").append("\"limit\": ").append(searchParameters.getLimit());
//    }
//    if (searchParameters.getOffset() != null) {
//      requestBody.append(",").append("\"offset\": ").append(searchParameters.getOffset());
//    }
//    if (StringUtils.isNotBlank(searchParameters.getSearchAfter())) {
//      requestBody.append(",").append("\"searchAfter\": ").append(searchParameters.getSearchAfter());
//    }
    requestBody.append("}");

    // Grab the specified mapSet
    final MapSet mapSet = getMapSet(mapSetCode);
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
    final Map<String, Map<String, Concept>> terminologyConceptMap = getConcepts(conceptsToLookup);

    // add names to mappings and to map entries
    for (final Mapping mapping : conceptIdToMappingMap.values()) {

      final Concept concept = terminologyConceptMap.get(fromTerminology).get(mapping.getCode());

      if (concept != null) {
        mapping.setName(concept.getName());
      } else {
        LOG.error("Concept not found: terminology:{}, code:{}", fromTerminology, mapping.getCode());
        mapping.setName(mapping.getCode() + " CONCEPT NOT FOUND");
      }

      for (final MapEntry entry : mapping.getMapEntries()) {

        final Concept relationConcept =
            terminologyConceptMap.get(fromTerminology).get(entry.getRelationCode());
        entry.setRelation(relationConcept != null ? relationConcept.getName()
            : entry.getRelationCode() + " CONCEPT NOT FOUND");

        final Concept toConcept = terminologyConceptMap.get(toTerminology).get(entry.getToCode());
        entry.setToName(
            toConcept != null ? toConcept.getName() : entry.getToCode() + " CONCEPT NOT FOUND");

        //TEMPORARY//
        if(toTerminology.equals("ICD10NO")) {
            entry.setToName(getICD10NOName(entry.getToCode()));
        }
        else if(toTerminology.equals("ICPC2NO")) {
            entry.setToName(getICPC2NOName(entry.getToCode()));
        }
        //END TEMPORARY//

      }
    }

    // Handle edition-precedence in the map entries
    for (final Mapping mapping : conceptIdToMappingMap.values()) {
        //handleEditionPrecedence(mapping);
    }
    
    // Sort all of the map entries in Group/Priority order
    for (final Mapping mapping : conceptIdToMappingMap.values()) {
        //sortMapEntries(mapping);
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
   * @param mapSetCode the map set code
   * @param searchString the search string
   * @return the list
   */
  private static List<String> searchConcepts(final String mapSetCode, final String searchString)
    throws Exception {

    // Connect to snowstorm
    final String branch = "MAIN%2FSNOMEDCT-NO%2F2024-04-15";
    String searchAfter = "";

    final String targetUri = SnowstormConnection.getBaseUrl() + branch + "/concepts/search";

    final StringBuilder requestBodyTempate = new StringBuilder();
    requestBodyTempate.append("{");
    requestBodyTempate.append("\"termFilter\": \"").append(searchString).append("\",");
    requestBodyTempate.append("\"eclFilter\": \"^").append(mapSetCode).append("\",");
    requestBodyTempate.append("\"limit\": ").append(5000).append(",");
    requestBodyTempate.append("\"returnIdOnly\": true,");
    // Is replaced with actual searchAfter value
    requestBodyTempate.append("\"searchAfter\": \"SEARCH_AFTER\"");
    requestBodyTempate.append("}");

    final List<String> conceptCodes = new ArrayList<>();

    boolean done = false;
    while (!done) {

      final String requestBodyString =
          requestBodyTempate.toString().replace("SEARCH_AFTER", searchAfter);

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
   * @param mapSetCode the map set code
   * @param conceptCode the concept code
   * @return the mapping
   * @throws Exception the exception
   */
  public static Mapping getMapping(final String mapSetCode, final String conceptCode)
    throws Exception {

    // Grab the specified mapSet
    final MapSet mapSet = getMapSet(mapSetCode);

    final Mapping mapping = new Mapping();

    // Connect to snowstorm
    final Client client = getClients().get();
    String searchAfter = null;
    int limit = 50;

    final String targetUri =
        SnowstormConnection.getBaseUrl() + "MAIN%2FSNOMEDCT-NO%2F2024-04-15/members?referenceSet="
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
        mapping.setName(
            SnowstormConcept.getConcept(mapSet.getFromTerminology(), mapping.getCode()).getName());
        mapping.setMapSetId(mapSet.getId());
        mapping.setMapEntries(new ArrayList<>());
      }

      // Add an entry to the mapping
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

      final Concept relationConcept = SnowstormConcept.getConcept(mapSet.getFromTerminology(),
          additionalFields.get("mapCategoryId").asText());
      if (relationConcept != null) {
        mapEntry.setRelation(relationConcept.getName());
      } else {
        mapEntry.setRelation(mapEntry.getToCode() + " DOES NOT EXIST");
      }

      mapEntry.setToCode(additionalFields.get("mapTarget").asText());

      final Concept toConcept = SnowstormConcept.getConcept(mapSet.getToTerminology(),
          additionalFields.get("mapTarget").asText());

      if (toConcept != null) {
        mapEntry.setToName(toConcept.getName());
      } else {
        mapEntry.setToName(mapEntry.getToCode() + " DOES NOT EXIST");
      }
      // TEMPORARY//
      mapEntry.setToName(getICD10NOName(mapEntry.getToCode()));
      // TEMPORARY//

      final List<MapEntry> mapEntries = mapping.getMapEntries();

      // Handle edition-precedence in the map entries
      //handleEditionPrecedence(mapping);
      
      // Sort all of the map entries in Group/Priority order
      //sortMapEntries(mapping);
      
    }

    return mapping;
  }

  /**
   * Gets the concepts by terminology.
   *
   * @param conceptCodes the concept codes
   * @return the concept
   * @throws Exception the exception
   */
  private static Map<String, Map<String, Concept>> getConcepts(
    final Map<String, Set<String>> conceptCodes) throws Exception {

    // Map<terminology, List<code, concept>>
    final Map<String, Map<String, Concept>> terminologyConceptMap = new HashMap<>();

    // for each terminology, get the concepts
    for (final String terminology : conceptCodes.keySet()) {

      final List<String> nonEmptyList = conceptCodes.get(terminology).stream()
          .filter(str -> !str.isEmpty()).collect(Collectors.toList());

      final Map<String, Concept> concepts =
          getConceptsFromSnowstorm(terminology, new ArrayList<>(nonEmptyList));

      terminologyConceptMap.put(terminology, concepts);

    }

    return terminologyConceptMap;

  }

  /**
   * Gets the concepts from snowstorm.
   *
   * @param terminology the terminology
   * @param codes the codes
   * @return the concepts from snowstorm
   * @throws Exception the exception
   */
  private static Map<String, Concept> getConceptsFromSnowstorm(final String terminology,
    final List<String> codes) throws Exception {

    if (codes == null || codes.isEmpty()) {
      return new HashMap<>();
    }

    LOG.debug("Codes to look up: {}", codes);

    final Integer fetchLimit = 1000;
    final SearchParameters searchParameters = new SearchParameters();
    searchParameters.setLimit(fetchLimit);
    searchParameters.setSearchAfter("");

    final Map<String, Concept> conceptMap = new HashMap<>();

    final String branch = "MAIN%2FSNOMEDCT-NO%2F2024-04-15";
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

  private static void sortMapEntries (Mapping mapping) {
      List<MapEntry> entries = mapping.getMapEntries();
      entries.sort(
          Comparator.comparingInt(MapEntry::getGroup).thenComparingInt(MapEntry::getPriority));

      mapping.setMapEntries(entries);
  }
  
  // Handle edition-precedence in the map entries
  // For example: if there is an International map entry (module=449080006) for group 1, priority 1,
  // And also a Norwegian map entry (module=51000202101) for group 1, priority 1,
  // then the Edition/Norwegian map entry should be kept, and the international one dropped. 
  private static void handleEditionPrecedence (Mapping mapping) {
      Map<String, MapEntry> groupPriorityToEntryMap = new HashMap<>();
      for (MapEntry mapEntry : mapping.getMapEntries()) {
          String key = mapEntry.getGroup() + "-" + mapEntry.getPriority();
          if (groupPriorityToEntryMap.containsKey(key)) {
              MapEntry existingMapEntry = groupPriorityToEntryMap.get(key);
              if (!existingMapEntry.getModuleId().equals("449080006") && mapEntry.getModuleId().equals("449080006")) {
                  // Keep existing map entry if it does not have moduleId 449080006
                  continue;
              }
          }
          groupPriorityToEntryMap.put(key, mapEntry);
      }
      
      //Set the remaining map entries to the mapping
      List<MapEntry> remainingMapEntries =  new ArrayList<>(groupPriorityToEntryMap.values());
      mapping.setMapEntries(remainingMapEntries);
  }
  
  // TEMPORARY//
  private static String getICD10NOName(String code) throws Exception {
    if (icd10noCodeToName.isEmpty()) {
          cacheICD10NONames();
      }
      String ICD10NOName = icd10noCodeToName.get(code);
    if (ICD10NOName == null || ICD10NOName.isBlank()) {
          ICD10NOName = "CONCEPT NOT FOUND FOR " + code;
      }
      return ICD10NOName;
  }

  // TEMPORARY//
  private static void cacheICD10NONames() throws Exception {

      String dataDir = PropertyUtility.getProperty("terminology.handler.SNOMED_SNOWSTORM.dir");

      final File f = new File(dataDir + "/ICD10NO_concepts.txt");
      if (!f.exists()) {
          LOG.error("ICD10NO file doesn't exist: " + f.getPath());
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
                  icd10noCodeToName.put(key, value);
              } else {
                  System.out.println("Ignoring malformed line: " + line);
              }
          }
      } catch (IOException e) {
          e.printStackTrace();
      }
  }

  // TEMPORARY//
  private static String getICPC2NOName(String code) throws Exception {
    if (icpc2noCodeToName.isEmpty()) {
      cacheICPC2NONames();
    }
    String ICPC2NOName = icpc2noCodeToName.get(code);
    if (ICPC2NOName == null || ICPC2NOName.isBlank()) {
        ICPC2NOName = "CONCEPT NOT FOUND FOR " + code;
    }
    return ICPC2NOName;
  }

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
  
}
