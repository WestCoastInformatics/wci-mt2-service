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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.ResultListConcept;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.ihtsdo.refsetservice.util.ConceptLookupParameters;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * The Class SnowstormRefsetMember.
 */
public class SnowstormRefsetMember extends SnowstormAbstract {

  /** The Constant LOG. */
  private static final Logger LOG = LoggerFactory.getLogger(SnowstormRefsetMember.class);

  /**
   * Gets all refset members.
   *
   * @param service the service
   * @param refsetInternalId the refset internal id
   * @param searchAfter the search after
   * @param concepts the concepts
   * @return the all refset members
   * @throws Exception the exception
   */
  public static List<Concept> getAllRefsetMembers(final TerminologyService service,
    final String refsetInternalId, final String searchAfter, final List<Concept> concepts)
    throws Exception {

    final Refset refset = service.get(refsetInternalId, Refset.class);

    if (refset == null) {

      throw new Exception(
          "Reference Set Internal Id: " + refsetInternalId + " does not exist in the RT2 database");
    }

    final String url = SnowstormConnection.getBaseUrl() + RefsetMemberService.getBranchPath(refset)
        + "/concepts?ecl=%5E%20" + refset.getRefsetId() + "&offset=0&limit=10000"
        + (searchAfter.contentEquals("") ? "" : "&searchAfter=" + searchAfter);

    final ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
    lookupParameters.setGetMembershipInformation(true);
    lookupParameters.setGetDescriptions(true);

    try (final Response response = SnowstormConnection.getResponse(url)) {

      if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

        throw new Exception("call to url '" + url + "' wasn't successful. Status: "
            + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
      }

      final String resultString = response.readEntity(String.class);

      final ObjectMapper mapper = new ObjectMapper();
      final JsonNode root = mapper.readTree(resultString.toString());

      final ResultListConcept conceptList =
          RefsetMemberService.populateConcepts(root, refset, lookupParameters);
      concepts.addAll(conceptList.getItems());

      final String newSearchAfter =
          (root.get("searchAfter") != null ? root.get("searchAfter").asText() : "");
      if (StringUtils.isNoneBlank(newSearchAfter)) {

        getAllRefsetMembers(service, refsetInternalId, newSearchAfter, concepts);
      }

    }

    LOG.debug("Refset has " + concepts.size() + " members");

    return concepts;
  }

  /**
   * Gets the refset members.
   *
   * @param refset the refset
   * @param nonDefaultPreferredTerms the non default preferred terms
   * @param searchParameters the search parameters
   * @return the refset members
   * @throws Exception the exception
   */
  public static ResultListConcept getMemberList(final Refset refset,
    final List<String> nonDefaultPreferredTerms, final SearchParameters searchParameters)
    throws Exception {

    // 2 Snowstorm calls: 1) Memberlist and 2) Descriptions
    final ResultListConcept members = new ResultListConcept();
    final String branchPath = RefsetMemberService.getBranchPath(refset);
    final String cacheString = refset.getRefsetId() + searchParameters.toString() + "true";
    final Map<String, ResultListConcept> branchCache =
        RefsetMemberService.getCacheForConceptsCall(branchPath);
    final String refsetId = refset.getRefsetId();

    // check if the members call has been cached
    if (branchCache.containsKey(cacheString)) {

      LOG.debug("getMemberList USING CACHE");
      return branchCache.get(cacheString);
    }

    try {

      boolean notSearching = true;
      final ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
      lookupParameters.setGetMembershipInformation(true);

      final List<Concept> conceptsToProcess = new ArrayList<>();

      if (searchParameters.getQuery() == null) {

        searchParameters.setQuery("");
      }

      ResultListConcept currentList = new ResultListConcept();

      // if search term is indicated, find members that match search term
      if (searchParameters.getQuery() != null && !searchParameters.getQuery().isEmpty()) {

        notSearching = false;
        currentList = SnowstormConcept.searchConcepts(refset, searchParameters, "members", -1);
      } else {

        String searchAfter = "";
        final long start = System.currentTimeMillis();
        boolean hasMorePages = true;
        final String acceptLanguage = SnowstormConnection.DEFAULT_ACCECPT_LANGUAGES;

        // when searching for members we only want concepts whose membership is
        // active
        // (though the concept itself can be inactive)
        final String url = SnowstormConnection.getBaseUrl()
            + RefsetMemberService.getBranchPath(refset) + "/members?referenceSet=" + refsetId
            + "&active=true&offset=0&limit=" + RefsetMemberService.ELASTICSEARCH_MAX_RECORD_LENGTH;

        while (hasMorePages) {

          final String fullSnowstormUrl = url + searchAfter;
          LOG.debug("Get Member List URL: " + fullSnowstormUrl);

          try (final Response response =
              SnowstormConnection.getResponse(fullSnowstormUrl, acceptLanguage)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

              hasMorePages = false;
              throw new Exception("call to url '" + fullSnowstormUrl
                  + "' wasn't successful. Status: " + response.getStatus() + " Message: "
                  + response.getStatusInfo().getReasonPhrase());
            }

            final String resultString = response.readEntity(String.class);

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

              throw new Exception(Integer.toString(response.getStatus()));
            }

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());
            final JsonNode conceptNodeBatch = root.get("items");

            // if the search returned results set the total
            if (!currentList.isTotalKnown()) {

              currentList.setTotal(root.get("total").asInt());
              currentList.setTotalKnown(true);
            }

            if (root.get("searchAfter") != null) {

              searchAfter = "&searchAfter=" + root.get("searchAfter").asText();
            }

            if (conceptNodeBatch.size() == 0 || conceptNodeBatch.size()
                + currentList.getItems().size() >= currentList.getTotal()) {

              hasMorePages = false;
            }

            if (System.currentTimeMillis()
                - start > RefsetMemberService.TIMEOUT_MILLISECOND_THRESHOLD) {

              hasMorePages = false;
            }

            final ResultListConcept currentMemberBatch =
                RefsetMemberService.populateConcepts(root, refset, lookupParameters);
            currentList.getItems().addAll(currentMemberBatch.getItems());
          }

        }

      }

      int snowstormCallCount = 0;
      final boolean doNotSearch = notSearching;

      // Change the numbers to '1's to avoid threading
      final ExecutorService executor = new ThreadPoolExecutor(30, 30, 0L, TimeUnit.MILLISECONDS,
          new ArrayBlockingQueue<>(30), new ThreadPoolExecutor.CallerRunsPolicy());

      // add the descriptions to the children concepts in batches
      for (int i = 0; i < currentList.getItems().size(); i++) {

        final Concept concept = currentList.getItems().get(i);
        conceptsToProcess.add(concept);

        if (conceptsToProcess.size() == RefsetMemberService.CONCEPT_DESCRIPTIONS_PER_CALL
            || i == currentList.getItems().size() - 1) {

          snowstormCallCount++;
          final List<Concept> threadConcepts = new ArrayList<Concept>();
          threadConcepts.addAll(conceptsToProcess);

          executor.submit(new Runnable() {

            /* see superclass */
            @Override
            public void run() {

              try {

                // LOG.debug("getMemberList IN THREAD ID: " +
                // Thread.currentThread().getId());
                SnowstormDescription.populateAllLanguageDescriptions(refset, threadConcepts);

                // if not searching and editing then get the concept leaf
                // information
                if (doNotSearch && searchParameters.getEditing()) {

                  SnowstormConcept.populateConceptLeafStatus(refset, threadConcepts);
                }

              } catch (final Exception e) {

                throw new RuntimeException(e);
              }

            }

          });

          conceptsToProcess.clear();
        }

      }

      executor.shutdown();
      executor.awaitTermination(600, TimeUnit.SECONDS);

      LOG.debug("getMemberList snowstormCallCount: " + snowstormCallCount);

      members.getItems().addAll(currentList.getItems());
      members.setTotal(currentList.getTotal());
      members.setTotalKnown(true);

      branchCache.put(cacheString, members);
      RefsetMemberService.CONCEPTS_CALL_CACHE.put(branchPath, branchCache);

    } catch (final Exception ex) {

      throw new Exception("Could not get Reference Set member list for Reference Set " + refsetId
          + " from snowstorm: " + ex.getMessage(), ex);
    }

    return members;
  }

  /**
   * Populate membership information.
   *
   * @param refset the refset
   * @param concepts the concepts
   * @throws Exception the exception
   */
  public static void populateMembershipInformation(final Refset refset,
    final List<Concept> concepts) throws Exception {

    final String baseUrl = SnowstormConnection.getBaseUrl()
        + RefsetMemberService.getBranchPath(refset) + "/members?referenceSet="
        + refset.getRefsetId() + "&active=true&limit="
        + RefsetMemberService.ELASTICSEARCH_MAX_RECORD_LENGTH + "&offset=0&referencedComponentId=";
    final List<Concept> conceptsToProcess = new ArrayList<>();
    String conceptIds = "";
    int snowstormCallCount = 0;

    // Change the '30's to '1's to avoid threading
    final ExecutorService executor = new ThreadPoolExecutor(30, 30, 0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(30), new ThreadPoolExecutor.CallerRunsPolicy());

    // add the descriptions to the children concepts in batches
    for (int i = 0; i < concepts.size(); i++) {

      final Concept concept = concepts.get(i);
      conceptsToProcess.add(concept);
      conceptIds += concept.getCode() + ",";

      if (conceptsToProcess.size() == RefsetMemberService.CONCEPT_DESCRIPTIONS_PER_CALL
          || i == concepts.size() - 1) {

        final String memberUrl = baseUrl + conceptIds;
        conceptIds = "";
        snowstormCallCount++;
        final List<Concept> threadConcepts = new ArrayList<Concept>();
        threadConcepts.addAll(conceptsToProcess);

        executor.submit(new Runnable() {

          /* see superclass */
          @Override
          public void run() {

            try {

              // LOG.debug("populateMembershipInformation IN THREAD ID: " +
              // Thread.currentThread().getId());
              LOG.debug("Get Membership URL for Populate: " + memberUrl);

              final ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
              lookupParameters.setGetMembershipInformation(true);
              final ResultListConcept resultList = SnowstormConcept
                  .getConceptsFromSnowstorm(memberUrl, refset, lookupParameters, null);

              for (final Concept resultConcept : resultList.getItems()) {

                for (final Concept conceptToProcess : threadConcepts) {

                  if (resultConcept.getCode().equals(conceptToProcess.getCode())) {

                    conceptToProcess.setMemberOfRefset(resultConcept.isMemberOfRefset());
                    conceptToProcess.setMemberEffectiveTime(resultConcept.getMemberEffectiveTime());
                    conceptToProcess.setReleased(resultConcept.isReleased());
                    break;
                  }

                }

              }

            } catch (final Exception e) {

              throw new RuntimeException(e);
            }

          }

        });

        conceptsToProcess.clear();
      }

    }

    executor.shutdown();
    executor.awaitTermination(600, TimeUnit.SECONDS);

    LOG.debug("populateMembershipInformation snowstormCallCount: " + snowstormCallCount);
  }

  /**
   * Gets the member history.
   *
   * @param service the service
   * @param referencedComponentId the referenced component id
   * @param versions the versions
   * @return the member history
   * @throws Exception the exception
   */
  public static List<Map<String, String>> getMemberHistory(final TerminologyService service,
    final String referencedComponentId, final List<Map<String, String>> versions) throws Exception {

    // Note, the system expects that versions are ordered from oldest first
    // to newest last. Failure to adhere to this convention will break the
    // algorithm.
    final List<Map<String, String>> memberHistory = new ArrayList<>();
    String previousStatus = null;

    for (final Map<String, String> version : versions) {

      final String refsetInternalId = version.get("refsetInternalId");
      final String branchDate = version.get("date");

      LOG.debug(
          "Processing history on: " + branchDate + " using internalRefsetId: " + refsetInternalId);

      final Refset refset = service.get(refsetInternalId, Refset.class);

      if (refset == null) {

        throw new Exception("Reference Set Internal Id: " + refsetInternalId
            + " does not exist in the RT2 database");
      }

      final String url = SnowstormConnection.getBaseUrl() + RefsetService.getBranchPath(refset)
          + "/members?referenceSet=" + refset.getRefsetId() + "&referencedComponentId="
          + referencedComponentId;

      LOG.debug("Get Membership History URL: " + url);

      try (final Response response = SnowstormConnection.getResponse(url)) {

        if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

          throw new Exception("call to url '" + url + "' wasn't successful. Status: "
              + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
        }

        final String resultString = response.readEntity(String.class);
        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode root = mapper.readTree(resultString.toString());
        final JsonNode node = root.get("items");
        String currentVersionDate = null;
        final Iterator<JsonNode> iterator = node.iterator();
        String currentStatus = null;

        if (iterator.hasNext()) {

          final JsonNode memberNode = iterator.next();

          // Calculate the version date and ensure it has
          // proper format yyyy-mm-dd
          currentVersionDate = memberNode.get("releasedEffectiveTime").asText();
          currentVersionDate = currentVersionDate.substring(0, 4) + "-"
              + currentVersionDate.substring(4, 6) + "-" + currentVersionDate.substring(6);

          if (memberNode.has("active")) {

            if (memberNode.get("active").asBoolean()) {

              currentStatus = "Active";
            } else {

              currentStatus = "Inactive";
            }

          }

        }

        // have reached to the point prior to the concept
        // becoming a member, so can cancel searching further
        // versions
        if (currentStatus == null) {

          continue;
        }

        if (previousStatus == null) {
          // First time encountering a membership status, thus
          // first time added

          final Map<String, String> historyEntry = new HashMap<>();

          historyEntry.put("version", currentVersionDate);

          if ("active".equals(currentStatus.toLowerCase())) {

            historyEntry.put("change", "Added");
          } else {

            historyEntry.put("change", "Added as Inactive");
          }

          memberHistory.add(historyEntry);

        } else if (!currentStatus.equals(previousStatus)) {
          // if the previous status wasn't null and the
          // current
          // status doesn't match it, then set the last status

          final Map<String, String> historyEntry = new HashMap<>();
          historyEntry.put("version", currentVersionDate);

          if (currentStatus.equals("Active")) {

            historyEntry.put("change", "Activated");
          } else {

            historyEntry.put("change", "Inactivated");
          }

          memberHistory.add(historyEntry);

        }

        previousStatus = currentStatus;

      } catch (final Exception ex) {

        LOG.error("Could not grab Reference Set members for Reference Set " + refset.getRefsetId()
            + " from snowstorm: " + ex.getMessage(), ex);
        continue;
      }

    }

    return memberHistory;
  }

  /**
   * Cache member ancestors.
   *
   * @param refset the refset
   * @return true, if successful
   * @throws Exception the exception
   */
  public static boolean cacheMemberAncestors(final Refset refset) throws Exception {

    final long start = System.currentTimeMillis();
    final String branchPath = refset.getBranchPath();
    final String cacheString = refset.getRefsetId();
    final Map<String, Set<String>> branchCache =
        RefsetMemberService.getCacheForMemberAncestors(branchPath);

    // check if the members call has been cached
    if (branchCache.containsKey(cacheString)) {

      LOG.debug("cacheMemberAncestors USING CACHE");
      return true;
    }

    final String memberCountUrl =
        SnowstormConnection.getBaseUrl() + RefsetMemberService.getBranchPath(refset)
            + "/members?referenceSet=" + refset.getRefsetId() + "&active=true&limit=1";

    // See how many members the refset has - if it is more than 100k we can not
    // cache the ancestors
    try (final Response response = SnowstormConnection.getResponse(memberCountUrl)) {

      if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

        throw new Exception("Call to url '" + memberCountUrl + "' wasn't successful. "
            + response.getStatus() + ": " + response.getStatusInfo().getReasonPhrase());
      }

      final String resultString = response.readEntity(String.class);

      final ObjectMapper mapper = new ObjectMapper();
      final JsonNode root = mapper.readTree(resultString.toString());
      final int memberTotal = root.get("total").asInt();

      if (memberTotal > 100000) {

        LOG.warn("Could not cache the ancestors of Reference Set " + refset.getRefsetId()
            + " because it has too many members: " + memberTotal);
        branchCache.put(cacheString, new HashSet<String>());
        RefsetMemberService.ANCESTORS_CACHE.put(branchPath, branchCache);
        return true;
      }

    } catch (final Exception e) {

      LOG.error("Could not cache the ancestors of Reference Set " + refset.getRefsetId()
          + " from snowstorm: " + e.getMessage(), e);
    }

    // Get ancestors of all members via ecl e.g. >(^723264001)
    final String url = SnowstormConnection.getBaseUrl() + RefsetMemberService.getBranchPath(refset)
        + "/concepts?ecl=%3E(%5E" + refset.getRefsetId() + ")&limit=1000&offset=";
    final List<Set<String>> ancestorsSetBatches =
        new ArrayList<>(Collections.nCopies(10, new HashSet<>()));

    // Change the '10's to '1's to avoid threading
    final ExecutorService executor = new ThreadPoolExecutor(10, 10, 0L, TimeUnit.MILLISECONDS,
        new ArrayBlockingQueue<>(10), new ThreadPoolExecutor.CallerRunsPolicy());

    // add the descriptions to the children concepts in batches
    for (int i = 0; i < ancestorsSetBatches.size(); i++) {

      final Set<String> ancestorsSet = ancestorsSetBatches.get(i);
      final int offset = 1000 * i;
      LOG.debug("cacheMemberAncestors URL: " + url + offset);

      executor.submit(new Runnable() {

        /* see superclass */
        @Override
        public void run() {

          final String fullSnowstormUrl = url + offset;
          try (final Response response = SnowstormConnection.getResponse(fullSnowstormUrl)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

              throw new Exception("Call to url '" + fullSnowstormUrl + "' wasn't successful. "
                  + response.getStatus() + ": " + response.getStatusInfo().getReasonPhrase());
            }

            final String resultString = response.readEntity(String.class);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());
            final JsonNode allResultNodes = root.get("items");
            final Iterator<JsonNode> resultsIterator = allResultNodes.iterator();

            while (resultsIterator.hasNext()) {

              final JsonNode resultNode = resultsIterator.next();

              if (!resultNode.has("conceptId")) {

                throw new Exception("Result wasn't as expected with resultNode: " + resultNode);
              }

              ancestorsSet.add(resultNode.get("conceptId").asText());
            }

          } catch (final Exception e) {

            LOG.error("Could not cache the ancestors of Reference Set " + refset.getRefsetId()
                + " from snowstorm: " + e.getMessage(), e);
          }

        }

      });
    }

    executor.shutdown();
    executor.awaitTermination(120, TimeUnit.SECONDS);

    final Set<String> ancestorsSet = new HashSet<>();

    for (final Set<String> ancestorsBatch : ancestorsSetBatches) {

      ancestorsSet.addAll(ancestorsBatch);
    }

    branchCache.put(cacheString, ancestorsSet);
    RefsetMemberService.ANCESTORS_CACHE.put(branchPath, branchCache);

    LOG.debug("cacheMemberAncestors Time Taken: " + (System.currentTimeMillis() - start));
    LOG.debug("cacheMemberAncestors Number of Ancestors: " + ancestorsSet.size());

    return true;
  }

  /**
   * Adds the refset members.
   *
   * @param service the service
   * @param user the user
   * @param refset the refset
   * @param conceptIds the concept ids
   * @return the list
   * @throws Exception the exception
   */
  public static List<String> addRefsetMembers(final TerminologyService service, final User user,
    final Refset refset, final List<String> conceptIds) throws Exception {

    if (refset == null) {
      throw new Exception("Reference Set Internal Id: is NULL does not exist in the RT2 database");
    }

    final List<String> unaddedConcepts = new ArrayList<>();
    final ObjectMapper mapper = new ObjectMapper();

    final String branchPath = RefsetService.getBranchPath(refset);
    final String url = SnowstormConnection.getBaseUrl() + branchPath + "/" + "members";

    if (conceptIds.size() == 0) {

      return unaddedConcepts;
    }

    final String refsetId = refset.getRefsetId();

    // when searching for members we only want concepts whose membership is
    // active
    // (though the concept itself can be inactive)
    final String conceptSearchUrl =
        SnowstormConnection.getBaseUrl() + branchPath + "/concepts/search";
    final String memberSearchUrl = SnowstormConnection.getBaseUrl() + branchPath
        + "/members/search?limit=" + RefsetMemberService.ELASTICSEARCH_MAX_RECORD_LENGTH;
    final String bodyBase =
        "{\"limit\": " + RefsetMemberService.ELASTICSEARCH_MAX_RECORD_LENGTH + ", ";
    final String memberSearchBodyBase =
        "{\"referenceSet\":\"" + refsetId + "\", \"referencedComponentIds\":[";
    final List<String> permanentFullConceptList = new ArrayList<>(conceptIds);
    final Map<String, Map<String, String>> conceptsStatus =
        RefsetMemberService.REFSETS_UPDATED_MEMBERS.get(refset.getId());
    final List<String> validatedConcepts = new ArrayList<>();
    final ArrayNode memberUpdateArray = mapper.createArrayNode();
    boolean searchAgain = true;
    int searchIndex = 0;
    int loopNumber = 1;
    LOG.debug("addRefsetMembers concept search URL: " + conceptSearchUrl);
    LOG.debug("addRefsetMembers concept member verification URL: " + memberSearchUrl);

    while (searchAgain) {

      searchAgain = false;
      String bodyConceptIds = "\"conceptIds\":[";
      Iterator<JsonNode> iterator = null;
      final List<String> conceptBatch = new ArrayList<>();

      for (; searchIndex < permanentFullConceptList.size(); searchIndex++) {

        // LOG.debug("addRefsetMembers searchIndex: " + searchIndex + " ::
        // permanentFullConceptList.size(): " + permanentFullConceptList.size()
        // + "
        // ::
        // permanentFullConceptList.get(searchIndex): " +
        // permanentFullConceptList.get(searchIndex));
        final String conceptId = permanentFullConceptList.get(searchIndex);
        final Map<String, String> status = new HashMap<>();
        status.put("operation", "Added");
        status.put("status", "Failed");
        conceptsStatus.put(conceptId, status);
        conceptBatch.add(conceptId);
        bodyConceptIds += "\"" + conceptId + "\",";

        if (searchIndex / loopNumber >= RefsetMemberService.ELASTICSEARCH_MAX_RECORD_LENGTH) {

          loopNumber++;
          searchAgain = true;
          break;
        }

      }

      LOG.debug("addRefsetMembers searchIndex :: loopNumber :: batch size: " + searchIndex + " :: "
          + loopNumber + " :: " + (searchIndex / loopNumber));
      bodyConceptIds = StringUtils.removeEnd(bodyConceptIds, ",") + "]";

      // verify the concept IDs if a bulk add is going to be used
      if (conceptIds.size() > 0) {

        final String conceptVerificationBody = bodyBase + bodyConceptIds + "}";
        // LOG.debug("addRefsetMembers bulk verification body: " +
        // conceptVerificationBody);

        try (final Response response =
            SnowstormConnection.postResponse(conceptSearchUrl, conceptVerificationBody)) {

          final String resultString = response.readEntity(String.class);

          // Only process payload if Rest call is successful
          if (response.getStatus() != Response.Status.OK.getStatusCode()) {

            throw new Exception("call to url '" + conceptSearchUrl
                + "' for concept verification wasn't successful. Status: " + response.getStatus()
                + " Message: " + response.getStatusInfo().getReasonPhrase());
          }

          final JsonNode root = mapper.readTree(resultString.toString());
          iterator = root.get("items").iterator();

          while (iterator != null && iterator.hasNext()) {

            final JsonNode conceptNode = iterator.next();
            final String conceptId = conceptNode.get("conceptId").asText();
            String name = "";
            validatedConcepts.add(conceptId);

            if (conceptNode.get("pt") != null && conceptNode.get("pt").get("term") != null) {

              name = conceptNode.get("pt").get("term").asText();
            }

            final Map<String, String> status = new HashMap<>();
            status.put("operation", "Added");
            status.put("status", "Success");
            status.put("name", name);
            status.put("active", conceptNode.get("active").asText());
            conceptsStatus.put(conceptId, status);
          }

        }

      }

      // gather any input concept not in the validated list
      final List<String> invalidConcepts = conceptBatch.stream().filter((inputConceptId) -> {

        final boolean found = validatedConcepts.contains(inputConceptId);

        if (found) {

          return false;
        } else {

          LOG.debug("The ID " + inputConceptId + " is not a valid concept.");
          return true;
        }

      }).collect(Collectors.toList());

      // remove invalid concepts from the concept lists
      LOG.debug("invalidConcepts: " + invalidConcepts);
      unaddedConcepts.addAll(invalidConcepts);
      conceptIds.removeAll(invalidConcepts);
      conceptBatch.removeAll(invalidConcepts);

      if (conceptBatch.size() > 0) {

        bodyConceptIds = "";

        // generate the body list for the check for concepts that are already
        // members
        for (final String conceptId : conceptBatch) {

          bodyConceptIds += conceptId + ",";
        }

        bodyConceptIds = StringUtils.removeEnd(bodyConceptIds, ",");
        final String memberSearchBody = memberSearchBodyBase + bodyConceptIds + "]}";
        // LOG.debug("addRefsetMembers member search body: " +
        // memberSearchBody);

        try (final Response response =
            SnowstormConnection.postResponse(memberSearchUrl, memberSearchBody)) {

          final String resultString = response.readEntity(String.class);

          // Only process payload if Rest call is successful
          if (response.getStatus() != Response.Status.OK.getStatusCode()) {

            throw new Exception("call to url '" + conceptSearchUrl
                + "' for member search wasn't successful. Status: " + response.getStatus()
                + " Message: " + response.getStatusInfo().getReasonPhrase());
          }

          final JsonNode root = mapper.readTree(resultString.toString());
          iterator = root.get("items").iterator();

          // loop thru the returned member details
          while (iterator != null && iterator.hasNext()) {

            final JsonNode conceptNode = iterator.next();
            final String conceptId = conceptNode.get("referencedComponentId").asText();
            final boolean active = conceptNode.get("active").asBoolean();

            // if active remove from the list to add, else update the membership
            // if the
            // concept used to be a member
            if (active) {

              LOG.debug("addRefsetMembers removing already member conceptId: " + conceptId);
              conceptIds.remove(conceptId);

              final Map<String, String> status = new HashMap<>();
              status.put("operation", "Added");
              status.put("status", "Already Member");
              conceptsStatus.put(conceptId, status);

            } else {

              conceptIds.remove(conceptId);

              final ObjectNode memberBody = mapper.createObjectNode().put("active", true)
                  .put("memberId", conceptNode.get("memberId").asText())
                  .put("moduleId", conceptNode.get("moduleId").asText())
                  .put("referencedComponentId", conceptNode.get("referencedComponentId").asText())
                  .put("refsetId", conceptNode.get("refsetId").asText())
                  .put("released", conceptNode.get("released").asBoolean())
                  .put("releasedEffectiveTime", conceptNode.get("releasedEffectiveTime").asInt())
                  .set("additionalFields", conceptNode.get("additionalFields"));

              if (conceptNode.get("effectiveTime") != null) {
                memberBody.put("effectiveTime", conceptNode.get("effectiveTime").asText());
              }

              memberUpdateArray.add(memberBody);
            }
          }
        }
      }
    }

    LOG.debug("addRefsetMembers about to add concept size: " + conceptIds.size());

    final String moduleId = refset.getModuleId();

    if (conceptIds.size() == 1) {
      unaddedConcepts.addAll(callAddMemberSingle(refsetId, url, conceptIds.get(0), moduleId));

    } else if (conceptIds.size() > 1) {
      unaddedConcepts.addAll(callAddMembersBulk(refsetId, url, conceptIds, moduleId));
    }

    // re-add any concepts that used to be members
    if (!memberUpdateArray.isEmpty()) {
      callUpdateMembersBulk(refsetId,
          SnowstormConnection.getBaseUrl() + branchPath + "/members/bulk", memberUpdateArray);
    }

    conceptIds.removeAll(unaddedConcepts);

    // clear the caches for this refset
    RefsetMemberService.clearAllMemberCaches(branchPath);

    // update the member count and save the refset
    if (refset.getMemberCount() == -1) {

      refset.setMemberCount(0);
    }

    refset.setMemberCount(refset.getMemberCount() + conceptIds.size() + memberUpdateArray.size());
    service.update(refset);

    for (final String conceptId : unaddedConcepts) {

      final Map<String, String> status = new HashMap<>();
      status.put("operation", "Added");
      status.put("status", "Failed");
      conceptsStatus.put(conceptId, status);
    }

    return unaddedConcepts;
  }

  /**
   * Call add member single.
   *
   * @param refsetId the refset id
   * @param url the url
   * @param conceptId the concept id
   * @param moduleId the module id
   * @return the list
   * @throws Exception the exception
   */
  public static List<String> callAddMemberSingle(final String refsetId, final String url,
    final String conceptId, final String moduleId) throws Exception {

    final List<String> unaddedConcepts = new ArrayList<>();

    final ObjectMapper mapper = new ObjectMapper();
    final ObjectNode body = mapper.createObjectNode().put("refsetId", refsetId)
        .put("moduleId", moduleId).put("referencedComponentId", conceptId);

    LOG.debug("callAddMemberSingle URL: " + url);
    // LOG.debug("callAddMemberSingle URL body: " + body.toString());

    try (final Response response = SnowstormConnection.postResponse(url, body.toString())) {

      // Only process payload if Rest call is successful
      if (response.getStatus() != Response.Status.OK.getStatusCode()) {

        LOG.error("Add Reference Set Member call to url '" + url + "' for Reference Set '"
            + refsetId + "' and concept '" + conceptId + "' wasn't successful. Status: "
            + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
        unaddedConcepts.add(conceptId);
      }

    }

    return unaddedConcepts;
  }

  /**
   * Call add members bulk.
   *
   * @param refsetId the refset id
   * @param url the url
   * @param conceptIds the concept ids
   * @param moduleId the module id
   * @return the list
   * @throws Exception the exception
   */
  public static List<String> callAddMembersBulk(final String refsetId, final String url,
    final List<String> conceptIds, final String moduleId) throws Exception {

    final List<String> unaddedConcepts = new ArrayList<>();
    final String bulkUrl = url + "/bulk";
    final ObjectMapper mapper = new ObjectMapper();
    final ArrayNode body = mapper.createArrayNode();

    for (final String conceptId : conceptIds) {

      final ObjectNode memberBody = mapper.createObjectNode().put("refsetId", refsetId)
          .put("moduleId", moduleId).put("referencedComponentId", conceptId);

      body.add(memberBody);
    }

    LOG.debug("callAddMembersBulk URL: " + bulkUrl);
    // LOG.debug("callAddMembersBulk URL body: " + body.toString());

    String jobStatusUrl = null;
    boolean jobDone = false;
    final String errorMessage = "Add Reference Set Member bulk call to url '" + bulkUrl
        + "' for Reference Set '" + refsetId + " wasn't successful. ";

    try (final Response response = SnowstormConnection.postResponse(bulkUrl, body.toString())) {

      // Only process payload if Rest call is successful
      if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {

        LOG.error(errorMessage + " Status: " + response.getStatus() + " Message: "
            + response.getStatusInfo().getReasonPhrase());
      }

      jobStatusUrl = response.getHeaderString("Location");
    }

    if (jobStatusUrl == null) {

      LOG.error(errorMessage);

    } else {

      try {

        Thread.sleep(800);
      } catch (final InterruptedException ex) {

        Thread.currentThread().interrupt();
      }

      LOG.debug("callAddMembersBulk job status URL: " + jobStatusUrl);

      while (!jobDone) {

        try (final Response response = SnowstormConnection.getResponse(jobStatusUrl)) {

          // Only process payload if Rest call is successful
          if (response.getStatus() != Response.Status.OK.getStatusCode()) {

            jobDone = true;
            LOG.error(errorMessage + " Status: " + response.getStatus() + " Message: "
                + response.getStatusInfo().getReasonPhrase());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                errorMessage + " Status: " + response.getStatus() + " Message: "
                    + response.getStatusInfo().getReasonPhrase());
          }

          final String resultString = response.readEntity(String.class);
          final JsonNode root = mapper.readTree(resultString.toString());

          // LOG.debug("addRefsetMembers job status response: " + root);
          final String status = root.get("status").asText();

          if (status.equalsIgnoreCase("COMPLETED")) {

            jobDone = true;

          } else if (status.equalsIgnoreCase("failed")) {

            jobDone = true;
            LOG.error(errorMessage + " Status: " + response.getStatus() + " Message: "
                + response.getStatusInfo().getReasonPhrase());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                errorMessage + root.get("message").asText());
          } else {

            LOG.debug("Bulk member add hasn't finished yet...");

            try {

              Thread.sleep(800);
            } catch (final InterruptedException ex) {

              Thread.currentThread().interrupt();
            }

          }

        }

      }

    }

    return unaddedConcepts;
  }

  /**
   * Removes the refset members.
   *
   * @param service the service
   * @param user the user
   * @param refset the refset
   * @param conceptIds the concept ids
   * @return the list
   * @throws Exception the exception
   */
  public static List<String> removeRefsetMembers(final TerminologyService service, final User user,
    final Refset refset, final String conceptIds) throws Exception {

    List<String> unremovedConcepts = new ArrayList<>();
    final ObjectMapper mapper = new ObjectMapper();
    final Map<String, Map<String, String>> conceptsStatus =
        RefsetMemberService.REFSETS_UPDATED_MEMBERS.get(refset.getId());

    if (conceptIds.isEmpty()) {

      return unremovedConcepts;
    }

    final String refsetId = refset.getRefsetId();
    final String branchPath = RefsetService.getBranchPath(refset);
    final String url = SnowstormConnection.getBaseUrl() + branchPath + "/" + "members";

    // when searching for members we only want concepts whose membership is
    // active
    // (though the concept itself can be inactive)
    final String memberSearchUrlBase = SnowstormConnection.getBaseUrl() + branchPath
        + "/members?referenceSet=" + refset.getRefsetId() + "&offset=0&active=true" + "&limit="
        + RefsetMemberService.URL_MAX_CHAR_LENGTH + "&referencedComponentId=";
    final ArrayNode memberDeleteArray = mapper.createArrayNode();
    final ArrayNode memberUpdateArray = mapper.createArrayNode();
    final List<String> permanentFullConceptList = Arrays.asList(conceptIds.split(","));
    final List<String> members = new ArrayList<>();
    boolean searchAgain = true;
    int searchIndex = 0;

    while (searchAgain) {

      searchAgain = false;
      String bodyConceptIds = "";

      for (; searchIndex < permanentFullConceptList.size(); searchIndex++) {

        final String conceptId = permanentFullConceptList.get(searchIndex);
        final Map<String, String> status = new HashMap<>();
        status.put("operation", "Removed");
        status.put("status", "Failed");
        conceptsStatus.put(conceptId, status);
        bodyConceptIds += conceptId + ",";

        if (memberSearchUrlBase.length()
            + bodyConceptIds.length() >= RefsetMemberService.URL_MAX_CHAR_LENGTH) {

          searchAgain = true;
          break;
        }

      }

      bodyConceptIds = StringUtils.removeEnd(bodyConceptIds, ",");

      final String memberSearchUrl = memberSearchUrlBase + bodyConceptIds;
      Iterator<JsonNode> iterator = null;

      LOG.debug("removeRefsetMembers search URL: " + memberSearchUrl);

      try (final Response response = SnowstormConnection.getResponse(memberSearchUrl)) {

        final String resultString = response.readEntity(String.class);

        // Only process payload if Rest call is successful
        if (response.getStatus() != Response.Status.OK.getStatusCode()) {

          searchAgain = false;
          throw new Exception("call to url '" + memberSearchUrl + "' wasn't successful. Status: "
              + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
        }

        final JsonNode root = mapper.readTree(resultString.toString());
        iterator = root.get("items").iterator();
      }

      // loop thru the returned member details and add it to the list to delete
      while (iterator != null && iterator.hasNext()) {

        final JsonNode conceptNode = iterator.next();
        final boolean released = conceptNode.get("released").asBoolean();
        final String membershipId = conceptNode.get("memberId").asText();
        String name = "";
        members.add(conceptNode.get("referencedComponentId").asText());

        // if the member has not been released then remove the membership
        if (!released) {

          memberDeleteArray.add(membershipId);
        }

        // if the member has been released add the information to the update
        // array
        else {

          final ObjectNode memberBody = mapper.createObjectNode().put("active", false)
              .put("effectiveTime", conceptNode.get("effectiveTime").asText())
              .put("memberId", membershipId).put("moduleId", conceptNode.get("moduleId").asText())
              .put("referencedComponentId", conceptNode.get("referencedComponentId").asText())
              .put("refsetId", conceptNode.get("refsetId").asText()).put("released", released)
              .put("releasedEffectiveTime", conceptNode.get("releasedEffectiveTime").asInt())
              .set("additionalFields", conceptNode.get("additionalFields"));

          memberUpdateArray.add(memberBody);
        }

        final JsonNode referencedComponent = conceptNode.get("referencedComponent");

        if (referencedComponent.get("pt") != null
            && referencedComponent.get("pt").get("term") != null) {

          name = referencedComponent.get("pt").get("term").asText();
        }

        final Map<String, String> status = new HashMap<>();
        status.put("operation", "Removed");
        status.put("status", "Success");
        status.put("name", name);
        status.put("active", referencedComponent.get("active").asText());
        conceptsStatus.put(conceptNode.get("referencedComponentId").asText(), status);
      }

    }

    // gather any input concept that was not a member and adjust its status
    // permanentFullConceptList.stream().filter((inputConceptId) -> {
    //
    // boolean isMember = members.contains(inputConceptId);
    //
    // if (isMember) {
    // return false;
    // } else {
    //
    // final Map<String, String> status = new HashMap<>();
    // status.put("operation", "Removed");
    // status.put("status", "Was Not a Member");
    // conceptsStatus.put(inputConceptId, status);
    // return true;
    // }
    // });

    try {

      // delete any members that haven't been released
      if (memberDeleteArray.size() > 0) {

        final String deleteBody =
            mapper.createObjectNode().set("memberIds", memberDeleteArray).toString();
        final String deleteUrl = url + "?force";
        final String errorMessage = "Remove Reference Set Member bulk call to url '" + deleteUrl
            + "' for Reference Set '" + refsetId + " wasn't successful. ";

        LOG.debug("removeRefsetMembers URL: " + deleteUrl);
        // LOG.debug("removeRefsetMembers URL Body: " + deleteBody);

        try (final Response response = SnowstormConnection.deleteResponse(deleteUrl, deleteBody)) {

          // Only process payload if Rest call is successful
          if (response.getStatus() != Response.Status.NO_CONTENT.getStatusCode()) {

            LOG.error(errorMessage + " Status: " + response.getStatus() + " Message: "
                + response.getStatusInfo().getReasonPhrase());
          }

        }

      }

      // If there is one member to inactivate call the single update method,
      // otherwise
      // call the batch update
      if (memberUpdateArray.size() == 1) {

        final JsonNode memberBody = memberUpdateArray.get(0);
        unremovedConcepts = callUpdateMemberSingle(refsetId,
            url + "/" + memberBody.get("memberId").asText(), memberBody);

      } else if (memberUpdateArray.size() > 1) {

        unremovedConcepts = callUpdateMembersBulk(refsetId, url + "/bulk", memberUpdateArray);
      }

    } finally {

      // clear the caches for this refset
      RefsetMemberService.clearAllMemberCaches(branchPath);
    }

    // update the member count and save the refset
    refset.setMemberCount(getMemberCount(refset));
    service.update(refset);

    for (final String conceptId : unremovedConcepts) {

      final Map<String, String> status = new HashMap<>();
      status.put("operation", "Removed");
      status.put("status", "Failed");
      conceptsStatus.put(conceptId, status);
    }

    return unremovedConcepts;
  }

  /**
   * Call update member single.
   *
   * @param refsetId the refset id
   * @param url the url
   * @param memberBody the member body
   * @return the list
   * @throws Exception the exception
   */
  public static List<String> callUpdateMemberSingle(final String refsetId, final String url,
    final JsonNode memberBody) throws Exception {

    final List<String> unchangedConcepts = new ArrayList<>();

    LOG.debug("callUpdateMemberSingle URL: " + url);
    // LOG.debug("callUpdateMemberSingle URL body: " + memberBody.toString());

    try (final Response response = SnowstormConnection.putResponse(url, memberBody.toString())) {

      // Only process payload if Rest call is successful
      if (response.getStatus() != Response.Status.OK.getStatusCode()) {

        final String memberId = memberBody.get("memberId").asText();
        final String conceptId = memberBody.get("referencedComponentId").asText();

        LOG.error("Inactivate Reference Set Member call to url '" + url + "' for Reference Set '"
            + refsetId + "' and concept '" + conceptId + "' and member '" + memberId
            + "' wasn't successful. " + " Status: " + response.getStatus() + " Message: "
            + response.getStatusInfo().getReasonPhrase());
        unchangedConcepts.add(memberId);
      }

    }

    return unchangedConcepts;
  }

  /**
   * Call update members bulk.
   *
   * @param refsetId the refset id
   * @param url the url
   * @param memberBodies the member bodies
   * @return the list
   * @throws Exception the exception
   */
  public static List<String> callUpdateMembersBulk(final String refsetId, final String url,
    final ArrayNode memberBodies) throws Exception {

    final List<String> unchangedConcepts = new ArrayList<>();
    final ObjectMapper mapper = new ObjectMapper();

    LOG.debug("callUpdateMembersBulk URL: " + url);
    LOG.debug("callUpdateMembersBulk URL body: " + memberBodies.toString());

    String jobStatusUrl = null;
    boolean jobDone = false;
    final String errorMessage = "Reference Set Member bulk update call to url '" + url
        + "' for Reference Set '" + refsetId + " wasn't successful. ";

    try (final Response response = SnowstormConnection.postResponse(url, memberBodies.toString())) {

      // Only process payload if Rest call is successful
      if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {

        LOG.error(errorMessage + " Status: " + response.getStatus() + " Message: "
            + response.getStatusInfo().getReasonPhrase());
      }

      jobStatusUrl = response.getHeaderString("Location");
    }

    if (jobStatusUrl == null) {

      LOG.error(errorMessage);

    } else {

      try {

        Thread.sleep(800);
      } catch (final InterruptedException ex) {

        Thread.currentThread().interrupt();
      }

      LOG.debug("callUpdateMembersBulk job status URL: " + jobStatusUrl);

      while (!jobDone) {

        try (final Response response = SnowstormConnection.getResponse(jobStatusUrl)) {

          // Only process payload if Rest call is successful
          if (response.getStatus() != Response.Status.OK.getStatusCode()) {

            jobDone = true;
            LOG.error(errorMessage + " Status: " + response.getStatus() + " Message: "
                + response.getStatusInfo().getReasonPhrase());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                errorMessage + " Status: " + response.getStatus() + " Message: "
                    + response.getStatusInfo().getReasonPhrase());
          }

          final String resultString = response.readEntity(String.class);
          final JsonNode root = mapper.readTree(resultString.toString());

          // LOG.debug("addRefsetMembers job status response: " + root);
          final String status = root.get("status").asText();

          if (status.equalsIgnoreCase("COMPLETED")) {

            jobDone = true;

          } else if (status.equalsIgnoreCase("failed")) {

            jobDone = true;
            LOG.error(errorMessage + root.get("message").asText());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                errorMessage + root.get("message").asText());

          } else {

            LOG.debug("Bulk member update hasn't finished yet...");

            try {

              Thread.sleep(800);
            } catch (final InterruptedException ex) {

              Thread.currentThread().interrupt();
            }

          }

        }

      }

    }

    return unchangedConcepts;
  }

  /**
   * Gets the member count.
   *
   * @param refset the refset
   * @return the member count
   * @throws Exception the exception
   */
  public static int getMemberCount(final Refset refset) throws Exception {

    int count = 0;

    // when searching for members we only want concepts whose membership is
    // active
    // (though the concept itself can be inactive)
    final String url = SnowstormConnection.getBaseUrl() + RefsetMemberService.getBranchPath(refset)
        + "/members?referenceSet=" + refset.getRefsetId() + "&active=true&offset=0&limit=1";

    LOG.debug("Get Refset Member Count URL: " + url);

    try (final Response response = SnowstormConnection.getResponse(url)) {

      if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

        throw new Exception("call to url '" + url + "' wasn't successful. Status: "
            + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
      }

      final String resultString = response.readEntity(String.class);

      // Only process payload if Rest call is successful
      if (response.getStatus() != Response.Status.OK.getStatusCode()) {

        throw new Exception(Integer.toString(response.getStatus()));
      }

      final ObjectMapper mapper = new ObjectMapper();
      final JsonNode root = mapper.readTree(resultString.toString());
      count = root.get("total").asInt();
    }

    return count;
  }

  /**
   * Gets the member sctids.
   *
   * @param refsetId the refset id
   * @param limit the limit
   * @param searchAfter the search after
   * @param branchPath the branch path
   * @return the member sctids
   * @throws Exception the exception
   */
  public static String getMemberSctids(final String refsetId, final int limit,
    final String searchAfter, final String branchPath) throws Exception {

    final String pagingParams = "&limit=" + limit + "&searchAfter=" + searchAfter;

    final String url = SnowstormConnection.getBaseUrl() + "" + branchPath + "/members?referenceSet="
        + refsetId + "&" + pagingParams;

    LOG.debug("Snowstorm URL: " + url);

    try (Response response = SnowstormConnection.getResponse(url)) {

      if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

        throw new Exception("call to url '" + url + "' wasn't successful. Status: "
            + response.getStatus() + " Message: " + response.getStatusInfo().getReasonPhrase());
      }

      final String resultString = response.readEntity(String.class);
      return resultString;

    } catch (final Exception ex) {

      throw new Exception(
          "Could not retrieve Reference Set members from snowstorm: " + ex.getMessage(), ex);
    }
  }

}
