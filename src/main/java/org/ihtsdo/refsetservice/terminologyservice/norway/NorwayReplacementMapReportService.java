/*
 * Copyright 2026 West Coast Informatics - All Rights Reserved.
 *
 * Ported from OTF-Mapping-Service NorwayReplacementMapReport.
 */
package org.ihtsdo.refsetservice.terminologyservice.norway;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Norway replacement map report (ICD-10-NO / ICPC), ported from OTF-Mapping-Service.
 */
public class NorwayReplacementMapReportService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(NorwayReplacementMapReportService.class);

  public void runReport() throws Exception {

    LOGGER.info("Start Norway Replacement Map Report");

    try (final NorwaySnowstormClient client = new NorwaySnowstormClient()) {

      String searchAfter = null;

      LOGGER.info(
          "Identify full list of concepts Norway is interested in, as stored in the Helseplattformens refset");

      // Sample JSON
      // {
      // "items": [
      // {
      // "active": true,
      // "moduleId": "51000202101",
      // "released": true,
      // "releasedEffectiveTime": 20221222,
      // "memberId": "ffff31c3-7206-4a52-b95b-da19d98bf627",
      // "refsetId": "88161000202101",
      // "referencedComponentId": "735581004",
      // "additionalFields": {
      // "targetComponentId": ""
      // },
      // "referencedComponent": {
      // "conceptId": "735581004",
      // "active": true,
      // "definitionStatus": "FULLY_DEFINED",
      // "moduleId": "900000000000207008",
      // "fsn": {
      // "term": "Ventricular septal defect following procedure (disorder)",
      // "lang": "en"
      // },
      // "pt": {
      // "term": "Ventricular septal defect following procedure",
      // "lang": "en"
      // },
      // "id": "735581004"
      // }
      // }
      // ]
      // }

      final Set<String> helseplattformensConceptIds = new HashSet<>();
      int limit = 10000;

      while (true) {

        int returnedConceptsCount = 0;

        final JsonNode doc = client.getJson("/MAIN%2FSNOMEDCT-NO%2FREFSETS/members?referenceSet=88161000202101&limit="
                + limit
                + (searchAfter != null ? "&searchAfter=" + searchAfter : ""));

        // get total amount
        // Get concepts returned in this call (up to 1000)
        for (final JsonNode conceptNode : doc.get("items")) {
          helseplattformensConceptIds
              .add(conceptNode.get("referencedComponentId").asText());
          returnedConceptsCount++;
        }

        searchAfter = doc.get("searchAfter").asText();
        // if we don't get a full page of results, we've processed the final
        // page
        if (returnedConceptsCount < limit) {
          break;
        }
      }

      LOGGER.info("Identify current and dependent branch paths");
      // Sample JSON
      // {
      // "items": [
      // {
      // "name": "Norwegian Edition",
      // "owner": "The Norwegian Directorate of eHealth",
      // "shortName": "SNOMEDCT-NO",
      // "branchPath": "MAIN/SNOMEDCT-NO",
      // "dependantVersionEffectiveTime": 20230131,
      // "dailyBuildAvailable": true,
      // "latestDailyBuild": "2023-03-10-050823",
      // "countryCode": "no",
      // "latestVersion": {
      // "shortName": "SNOMEDCT-NO",
      // "importDate": "2022-10-14T11:19:37.529Z",
      // "parentBranchPath": "MAIN/SNOMEDCT-NO",
      // ^--Look for concepts inactive in this branch
      // "effectiveDate": 20221015,
      // "version": "2022-10-15",
      // "description": "SNOMEDCT-NO 20221015 import.",
      // "dependantVersionEffectiveTime": 20220831,
      // "branchPath": "MAIN/SNOMEDCT-NO/2022-10-15"
      // ^---And active in this branch
      // } } ] }
      String currentBranch = null;
      String previousVersionBranch = null;

      int returnedConceptsCount = 0;

      JsonNode doc = client.getJson("/codesystems?forBranch=MAIN%2FSNOMEDCT-NO%2FREFSETS");

      for (final JsonNode node : doc.get("items")) {
        currentBranch = node.get("branchPath").asText();

        final JsonNode latestVersionNode = node.get("latestVersion");
        previousVersionBranch = latestVersionNode.get("branchPath").asText();

        // We're only interested in the first node
        break;
      }

      LOGGER.info(
          "Identify all in-scope concepts that are active in the previousVersionBranch, and inactive in the currentBranch");
      // Sample JSON
      /*
       * { "items": [ "99999003", "99998006", "99997001", "99996005",
       * "99995009", "99994008", "99993002", "99992007",
       */

      final Set<String> activeInPreviousBranchScopeConcepts = new HashSet<>();
      searchAfter = null;
      limit = 10000;

      while (true) {

        returnedConceptsCount = 0;

        doc = client.getJson("/" + previousVersionBranch.replaceAll("/", "%2F")
                + "/concepts?activeFilter=true&returnIdOnly=true&limit=" + limit
                + (searchAfter != null ? "&searchAfter=" + searchAfter : ""));

        // get total amount
        // Get concepts returned in this call (up to 10000)
        final JsonNode conceptIds = doc.get("items");

        for (int i = 0; i < conceptIds.size(); i++) {
          final String activeConceptId = conceptIds.get(i).asText();
          activeInPreviousBranchScopeConcepts.add(activeConceptId);

          returnedConceptsCount++;
        }

        searchAfter = doc.get("searchAfter").asText();
        // if we don't get a full page of results, we've processed the final
        // page
        if (returnedConceptsCount < limit) {
          break;
        }
      }

      final Set<String> scopeConceptsInactivatedSincePreviousBranch =
          new HashSet<>();
      searchAfter = null;
      limit = 10000;

      while (true) {

        returnedConceptsCount = 0;

        doc = client.getJson("/" + currentBranch.replaceAll("/", "%2F")
                + "/concepts?activeFilter=false&returnIdOnly=true&limit="
                + limit
                + (searchAfter != null ? "&searchAfter=" + searchAfter : ""));

        // get total amount
        // Get concepts returned in this call (up to 10000)
        final JsonNode inactiveConceptIds = doc.get("items");

        for (int i = 0; i < inactiveConceptIds.size(); i++) {
          returnedConceptsCount++;

          final String inactiveConceptId = inactiveConceptIds.get(i).asText();
          // Don't include "garbage SCTIDs", defined as concepts that contain
          // "10002" as the
          // 7th through 11th characters.
          // E.g. "1217661000202103"
          // From Cato Spook in Norway:
          // They got their nickname because earlier we made them as a plan B to
          // satisfy
          // Helseplattformen who wanted SCTIDs to ICD-10 codes that did not
          // have a SCTID
          // mapping to them. You can see the ICD-10 code in the square brackets
          // in the FSN.
          // When we find a reason to inactivate these SCTIDs we do it, because
          // they are very
          // primitive and don’t have the functionality of a properly made
          // SCTID. They are just
          // inactivated without replacements because they are “garbage”. We
          // don’t need them
          // in our list.
          if (inactiveConceptId.length() > 11
              && inactiveConceptId.substring(6, 11).equals("10002")) {
            continue;
          }

          if (activeInPreviousBranchScopeConcepts.contains(inactiveConceptId)) {
            scopeConceptsInactivatedSincePreviousBranch.add(inactiveConceptId);
          }

        }

        searchAfter = doc.get("searchAfter").asText();
        // if we don't get a full page of results, we've processed the final
        // page
        if (returnedConceptsCount < limit) {
          break;
        }
      }

      LOGGER.info(
          "Grab all ICD-10-NO maps, keeping international and norwegian maps separate");
      // Sample JSON
      // {
      // "items": [
      // {
      // "active": true,
      // "moduleId": "449080006",
      // "released": true,
      // "releasedEffectiveTime": 20150731,
      // "memberId": "ffff7fc0-eda4-5ac2-837f-a0638ae35961",
      // "refsetId": "447562003",
      // "referencedComponentId": "93342009",
      // "additionalFields": {
      // "mapCategoryId": "447637006",
      // "mapRule": "TRUE",
      // "mapAdvice": "ALWAYS Q50.6",
      // "mapPriority": "1",
      // "mapGroup": "1",
      // "correlationId": "447561005",
      // "mapTarget": "Q50.6"
      // },
      // "referencedComponent": {
      // "conceptId": "93342009",
      // "active": true,
      // "definitionStatus": "FULLY_DEFINED",
      // "moduleId": "900000000000207008",
      // "fsn": {
      // "term": "Congenital malposition of fallopian tube (disorder)",
      // "lang": "en"
      // },
      // "pt": {
      // "term": "Congenital malposition of fallopian tube",
      // "lang": "en"
      // },
      // "id": "93342009"
      // },
      // "effectiveTime": "20150731"
      // }
      // ]
      // }

      final List<String> inactivedMappedScopeConcepts = new ArrayList<>();
      final Map<String, Set<String>> norwayMappedConceptsAndTargets =
          new HashMap<>();
      final Map<String, Set<String>> norwayMappedConceptsAndTargetsInactive =
          new HashMap<>();
      final Map<String, Set<String>> norwayMappedConceptsAndTargetsActive =
          new HashMap<>();
      final Map<String, Set<String>> internationalMappedConceptsAndTargets =
          new HashMap<>();
      final Map<String, Set<String>> internationalMappedConceptsAndTargetsInactive =
          new HashMap<>();
      final Map<String, Set<String>> internationalMappedConceptsAndTargetsActive =
          new HashMap<>();
      searchAfter = null;
      limit = 10000;

      while (true) {

        int returnedMapsCount = 0;

        doc = client.getJson("/MAIN%2FSNOMEDCT-NO%2FREFSETS/members?referenceSet=447562003&limit="
                + limit
                + (searchAfter != null ? "&searchAfter=" + searchAfter : ""));

        // get total amount
        // Get concepts returned in this call (up to 10000)
        for (final JsonNode mapNode : doc.get("items")) {
          final String conceptId =
              mapNode.get("referencedComponentId").asText();
          final String module = mapNode.get("moduleId").asText();
          final String active = mapNode.get("active").asText();

          // If the mapped concept is one of the ones recently inactivated,
          // track it
          if (scopeConceptsInactivatedSincePreviousBranch.contains(conceptId)
              && !inactivedMappedScopeConcepts.contains(conceptId)) {
            inactivedMappedScopeConcepts.add(conceptId);
          }

          // Store international and Norwegian maps separately
          if ("449080006".equals(module)) {
            if (!internationalMappedConceptsAndTargets.containsKey(conceptId)) {
              internationalMappedConceptsAndTargets.put(conceptId,
                  new HashSet<>());
              internationalMappedConceptsAndTargetsInactive.put(conceptId,
                  new HashSet<>());
              internationalMappedConceptsAndTargetsActive.put(conceptId,
                  new HashSet<>());
            }
            final JsonNode additionalFieldsNode =
                mapNode.get("additionalFields");
            if (additionalFieldsNode != null
                && additionalFieldsNode.findValue("mapTarget") != null) {
              final String mapTarget =
                  additionalFieldsNode.get("mapTarget").asText();
              if (active.equals("true")) {
                final Set<String> targetSet =
                    internationalMappedConceptsAndTargetsActive.get(conceptId);
                if (mapTarget != null && mapTarget != "") {
                  targetSet.add(additionalFieldsNode.get("mapTarget").asText());
                }
                internationalMappedConceptsAndTargetsActive.put(conceptId,
                    targetSet);
              } else if (active.equals("false")) {
                final Set<String> targetSet =
                    internationalMappedConceptsAndTargetsInactive
                        .get(conceptId);
                if (mapTarget != null && mapTarget != "") {
                  targetSet.add(additionalFieldsNode.get("mapTarget").asText());
                }
                internationalMappedConceptsAndTargetsInactive.put(conceptId,
                    targetSet);
              } else {
                throw new Exception(
                    "active flag neither true nor false on map for concept "
                        + conceptId);
              }
            }
          } else if ("51000202101".equals(module)) {
            if (!norwayMappedConceptsAndTargets.containsKey(conceptId)) {
              norwayMappedConceptsAndTargets.put(conceptId, new HashSet<>());
              norwayMappedConceptsAndTargetsInactive.put(conceptId,
                  new HashSet<>());
              norwayMappedConceptsAndTargetsActive.put(conceptId,
                  new HashSet<>());
            }
            final JsonNode additionalFieldsNode =
                mapNode.get("additionalFields");
            if (additionalFieldsNode != null
                && additionalFieldsNode.findValue("mapTarget") != null) {
              // String mapTarget =
              // additionalFieldsNode.get("mapTarget").asText();
              if (active.equals("true")) {
                final Set<String> targetSet =
                    norwayMappedConceptsAndTargetsActive.get(conceptId);
                targetSet.add(additionalFieldsNode.get("mapTarget").asText());
                norwayMappedConceptsAndTargetsActive.put(conceptId, targetSet);
              } else if (active.equals("false")) {
                final Set<String> targetSet =
                    norwayMappedConceptsAndTargetsInactive.get(conceptId);
                targetSet.add(additionalFieldsNode.get("mapTarget").asText());
                norwayMappedConceptsAndTargetsInactive.put(conceptId,
                    targetSet);
              } else {
                throw new Exception(
                    "active flag neither true nor false on map for concept "
                        + conceptId);
              }
            }
          } else {
            // If map is neither international nor Norwegian, ignore
          }

          returnedMapsCount++;
        }

        searchAfter = doc.get("searchAfter").asText();
        // if we don't get a full page of results, we've processed the final
        // page
        if (returnedMapsCount < limit) {
          break;
        }
      }

      LOGGER.info(
          "Grab all ICPC maps, keeping international and norwegian maps separate");
      // Sample JSON
      // {
      // "items": [
      // {
      // "active": true,
      // "moduleId": "449080006",
      // "released": true,
      // "releasedEffectiveTime": 20150731,
      // "memberId": "ffff7fc0-eda4-5ac2-837f-a0638ae35961",
      // "refsetId": "447562003",
      // "referencedComponentId": "93342009",
      // "additionalFields": {
      // "mapCategoryId": "447637006",
      // "mapRule": "TRUE",
      // "mapAdvice": "ALWAYS Q50.6",
      // "mapPriority": "1",
      // "mapGroup": "1",
      // "correlationId": "447561005",
      // "mapTarget": "Q50.6"
      // },
      // "referencedComponent": {
      // "conceptId": "93342009",
      // "active": true,
      // "definitionStatus": "FULLY_DEFINED",
      // "moduleId": "900000000000207008",
      // "fsn": {
      // "term": "Congenital malposition of fallopian tube (disorder)",
      // "lang": "en"
      // },
      // "pt": {
      // "term": "Congenital malposition of fallopian tube",
      // "lang": "en"
      // },
      // "id": "93342009"
      // },
      // "effectiveTime": "20150731"
      // }
      // ]
      // }

      final Map<String, Set<String>> norwayMappedConceptsAndICPCTargets =
          new HashMap<>();
      final Map<String, Set<String>> norwayMappedConceptsAndICPCTargetsInactive =
          new HashMap<>();
      final Map<String, Set<String>> norwayMappedConceptsAndICPCTargetsActive =
          new HashMap<>();
      final Map<String, Set<String>> internationalMappedConceptsAndICPCTargets =
          new HashMap<>();
      final Map<String, Set<String>> internationalMappedConceptsAndICPCTargetsInactive =
          new HashMap<>();
      final Map<String, Set<String>> internationalMappedConceptsAndICPCTargetsActive =
          new HashMap<>();
      searchAfter = null;
      limit = 10000;

      while (true) {

        int returnedMapsCount = 0;

        doc = client.getJson("/MAIN%2FSNOMEDCT-NO%2FREFSETS/members?referenceSet=68101000202102&limit="
                + limit
                + (searchAfter != null ? "&searchAfter=" + searchAfter : ""));

        // get total amount
        // Get concepts returned in this call (up to 10000)
        for (final JsonNode mapNode : doc.get("items")) {
          final String conceptId =
              mapNode.get("referencedComponentId").asText();
          final String module = mapNode.get("moduleId").asText();
          final String active = mapNode.get("active").asText();

          // Per Norway, they are only interested in ICPC mapped concepts that
          // also contain an ICD-10 map, so we reuse the existing list of
          // inactivedMappedScopeConcepts.
          // if(scopeConceptsInactivatedSincePreviousBranch.contains(conceptId)
          // && !inactivedMappedScopeConcepts.contains(conceptId)) {
          // inactivedMappedScopeConcepts.add(conceptId);
          // }

          // Store international and Norwegian maps separately
          if ("449080006".equals(module)) {
            if (!internationalMappedConceptsAndICPCTargets
                .containsKey(conceptId)) {
              internationalMappedConceptsAndICPCTargets.put(conceptId,
                  new HashSet<>());
              internationalMappedConceptsAndICPCTargetsInactive.put(conceptId,
                  new HashSet<>());
              internationalMappedConceptsAndICPCTargetsActive.put(conceptId,
                  new HashSet<>());
            }
            final JsonNode additionalFieldsNode =
                mapNode.get("additionalFields");
            if (additionalFieldsNode != null
                && additionalFieldsNode.findValue("mapTarget") != null) {
              String mapTarget = additionalFieldsNode.get("mapTarget").asText();
              if (active.equals("true")) {
                final Set<String> targetSet =
                    internationalMappedConceptsAndICPCTargetsActive
                        .get(conceptId);
                if (mapTarget != null && mapTarget != "") {
                  targetSet.add(additionalFieldsNode.get("mapTarget").asText());
                }
                internationalMappedConceptsAndICPCTargetsActive.put(conceptId,
                    targetSet);
              } else if (active.equals("false")) {
                final Set<String> targetSet =
                    internationalMappedConceptsAndICPCTargetsInactive
                        .get(conceptId);
                if (mapTarget != null && mapTarget != "") {
                  targetSet.add(additionalFieldsNode.get("mapTarget").asText());
                }
                internationalMappedConceptsAndICPCTargetsInactive.put(conceptId,
                    targetSet);
              } else {
                throw new Exception(
                    "active flag neither true nor false on map for concept "
                        + conceptId);
              }
            }
          } else if ("51000202101".equals(module)) {
            if (!norwayMappedConceptsAndICPCTargets.containsKey(conceptId)) {
              norwayMappedConceptsAndICPCTargets.put(conceptId,
                  new HashSet<>());
              norwayMappedConceptsAndICPCTargetsInactive.put(conceptId,
                  new HashSet<>());
              norwayMappedConceptsAndICPCTargetsActive.put(conceptId,
                  new HashSet<>());
            }
            final JsonNode additionalFieldsNode =
                mapNode.get("additionalFields");
            if (additionalFieldsNode != null
                && additionalFieldsNode.findValue("mapTarget") != null) {
              // String mapTarget =
              // additionalFieldsNode.get("mapTarget").asText();
              if (active.equals("true")) {
                final Set<String> targetSet =
                    norwayMappedConceptsAndICPCTargetsActive.get(conceptId);
                targetSet.add(additionalFieldsNode.get("mapTarget").asText());
                norwayMappedConceptsAndICPCTargetsActive.put(conceptId,
                    targetSet);
              } else if (active.equals("false")) {
                final Set<String> targetSet =
                    norwayMappedConceptsAndICPCTargetsInactive.get(conceptId);
                targetSet.add(additionalFieldsNode.get("mapTarget").asText());
                norwayMappedConceptsAndICPCTargetsInactive.put(conceptId,
                    targetSet);
              } else {
                throw new Exception(
                    "active flag neither true nor false on map for concept "
                        + conceptId);
              }
            }
          } else {
            // If map is neither international nor Norwegian, ignore
          }

          returnedMapsCount++;
        }

        searchAfter = doc.get("searchAfter").asText();
        // if we don't get a full page of results, we've processed the final
        // page
        if (returnedMapsCount < limit) {
          break;
        }
      }

      LOGGER.info("Grab all replacement concepts");
      // Sample JSON
      // {
      // "items": [
      // {
      // "conceptId": "267214002",
      // "fsn": {
      // "term": "Congenital abnormality of uterus - baby delivered (disorder)",
      // "lang": "en"
      // },
      // "pt": {
      // "term": "Congenital abnormality of uterus - baby delivered",
      // "lang": "en"
      // },
      // "active": false,
      // "effectiveTime": "20230131",
      // "released": true,
      // "releasedEffectiveTime": 20230131,
      // "inactivationIndicator": "CLASSIFICATION_DERIVED_COMPONENT",
      // "associationTargets": {
      // "PARTIALLY_EQUIVALENT_TO": [
      // "267212003",
      // "289256000"
      // ]
      // }
      // }
      // ]
      // }

      final Map<String, Map<String, String>> conceptAssociationTargets =
          new HashMap<>();
      final Map<String, String> conceptInactivationIndicators = new HashMap<>();
      int batchSize = 100;
      int counter = 0;

      final int inactiveConceptsCount = inactivedMappedScopeConcepts.size();
      boolean reachedFinalConcept = false;
      final Set<String> replacementConceptIds = new HashSet<>();

      while (!reachedFinalConcept && inactiveConceptsCount != 0) {

        String targetUri = "/browser/MAIN%2FSNOMEDCT-NO/concepts?";
        for (int i = 0; i < batchSize; i++) {
          targetUri = targetUri + "conceptIds="
              + inactivedMappedScopeConcepts.get(counter) + "&";
          counter++;
          if (counter >= inactiveConceptsCount) {
            reachedFinalConcept = true;
            targetUri = targetUri + "limit=10000";
            break;
          }
        }

        doc = client.getJson(targetUri);

        // get total amount
        // Get concepts returned in this call (up to 1000)
        for (final JsonNode conceptNode : doc.get("items")) {
          final String conceptId = conceptNode.get("conceptId").asText();
          final String inactivationIndicator = conceptNode
              .get("inactivationIndicator").asText().replaceAll("_", " ");
          conceptInactivationIndicators.put(conceptId, inactivationIndicator);

          final JsonNode associationTargetsNode =
              conceptNode.findValue("associationTargets");
          if (associationTargetsNode == null
              || associationTargetsNode.size() == 0
              || associationTargetsNode.fields() == null) {
            conceptAssociationTargets.put(conceptId, null);
            continue;
          }

          final Entry<String, JsonNode> entry =
              associationTargetsNode.fields().next();
          final String associationType = entry.getKey().replaceAll("_", " ")
              + " association reference set";
          String values = entry.getValue().toString();
          if (values.contains("[")) {
            values = values.substring(1, values.length() - 1);
          }
          values = values.replaceAll("\"", "");
          // Keep track of all replacement concept Ids for description lookup
          // later
          for (final String value : values.split(",")) {
            replacementConceptIds.add(value);
          }
          final Map<String, String> associationTargets = new HashMap<>();
          associationTargets.put(associationType, values);
          conceptAssociationTargets.put(conceptId, associationTargets);
        }
      }

      LOGGER.info("Grab all inactive and replacement concept descriptions");
      // Sample JSON
      // {
      // "items": [
      // {
      // "active": true,
      // "moduleId": "51000202101",
      // "released": true,
      // "releasedEffectiveTime": 20220415,
      // "descriptionId": "2344071000202112",
      // "term": "utilsiktet forgiftning med intravenøst bedøvelsesmiddel",
      // "conceptId": "216579009",
      // "typeId": "900000000000013009",
      // "acceptabilityMap": {
      // "61000202103": "ACCEPTABLE"
      // },
      // "type": "SYNONYM",
      // "lang": "no",
      // "caseSignificance": "CASE_INSENSITIVE",
      // "effectiveTime": "20220415"
      // }
      // ]
      // }

      final Map<String, String> conceptIdToFSN = new HashMap<>();
      final Map<String, String> conceptIdToPTEN = new HashMap<>();
      final Map<String, String> conceptIdToPTNO = new HashMap<>();

      batchSize = 10;
      counter = 0;

      final List<String> inactiveAndReplacementTargets = new ArrayList<>();
      inactiveAndReplacementTargets.addAll(inactivedMappedScopeConcepts);
      inactiveAndReplacementTargets.addAll(replacementConceptIds);

      int inactiveAndReplacementConceptsCount =
          inactiveAndReplacementTargets.size();

      reachedFinalConcept = false;

      while (!reachedFinalConcept && inactiveAndReplacementConceptsCount != 0) {

        returnedConceptsCount = 0;

        String targetUri = "/MAIN%2FSNOMEDCT-NO/descriptions?";
        for (int i = 0; i < batchSize; i++) {
          targetUri = targetUri + "conceptIds="
              + inactiveAndReplacementTargets.get(counter) + "&";
          counter++;
          if (counter >= inactiveAndReplacementConceptsCount) {
            reachedFinalConcept = true;
            break;
          }
        }

        targetUri = targetUri + "limit=10000";
        doc = client.getJson(targetUri);

        // get total amount
        // Get concepts returned in this call (up to 1000)
        for (final JsonNode conceptNode : doc.get("items")) {
          final String active = conceptNode.get("active").asText();
          if (active.equals("false")) {
            continue;
          }

          final String conceptId = conceptNode.get("conceptId").asText();
          final String type = conceptNode.get("type").asText();
          final String term = conceptNode.get("term").asText();

          if (type.equals("FSN")) {
            conceptIdToFSN.put(conceptId, term);
            continue;
          }

          final String lang = conceptNode.get("lang").asText();

          final JsonNode acceptabilityMapNode =
              conceptNode.findValue("acceptabilityMap");
          if (acceptabilityMapNode == null || acceptabilityMapNode.size() == 0
              || acceptabilityMapNode.fields() == null) {
            conceptAssociationTargets.put(conceptId, null);
            continue;
          }

          // Grab American English preferred term
          if (lang.equals("en")
              && acceptabilityMapNode.findValue("900000000000509007") != null
              && acceptabilityMapNode.findValue("900000000000509007").asText()
                  .equals("PREFERRED")) {
            conceptIdToPTEN.put(conceptId, term);
            continue;
          }
          // Grab Nynorsk preferred term
          if (lang.equals("no")
              && acceptabilityMapNode.findValue("61000202103") != null
              && acceptabilityMapNode.findValue("61000202103").asText()
                  .equals("PREFERRED")) {
            conceptIdToPTNO.put(conceptId, term);
            continue;
          }
        }
      }

      final List<String> results = new ArrayList<>();
      // Add header row
      results.add(
          "conceptId\tfsn.term\tpt.lang\tpt.term\tHelseplattformens refset member\tICD-10 MAP\tICPC MAP\tinactivationIndicator.pt.term"
              + "\tassociations.association.pt.term\tassociations.targetId\tassociations.target.fsn.term\t"
              + "associations.target.pt.lang\tassociations.target.pt.term\tNy SCTID OK\tTarget ICD-10 Map\t"
              + "Target ICPC MAP\tIkkje bruk map\tOppr map Ja/Nei\tKommentar\tKolonne1\tKolonne2");

      // Add result rows, in conceptId order
      Collections.sort(inactivedMappedScopeConcepts);
      for (final String conceptId : inactivedMappedScopeConcepts) {

        // If active maps are present for this concept, use them. If no active
        // maps exist, use the inactive ones.
        norwayMappedConceptsAndTargets.put(conceptId,
            norwayMappedConceptsAndTargetsActive.get(conceptId) != null
                && !norwayMappedConceptsAndTargetsActive.get(conceptId)
                    .isEmpty()
                        ? norwayMappedConceptsAndTargetsActive.get(conceptId)
                        : norwayMappedConceptsAndTargetsInactive
                            .get(conceptId));
        internationalMappedConceptsAndTargets.put(conceptId,
            internationalMappedConceptsAndTargetsActive.get(conceptId) != null
                && !internationalMappedConceptsAndTargetsActive.get(conceptId)
                    .isEmpty()
                        ? internationalMappedConceptsAndTargetsActive
                            .get(conceptId)
                        : internationalMappedConceptsAndTargetsInactive
                            .get(conceptId));
        norwayMappedConceptsAndICPCTargets.put(conceptId,
            norwayMappedConceptsAndICPCTargetsActive.get(conceptId) != null
                && !norwayMappedConceptsAndICPCTargetsActive.get(conceptId)
                    .isEmpty()
                        ? norwayMappedConceptsAndICPCTargetsActive
                            .get(conceptId)
                        : norwayMappedConceptsAndICPCTargetsInactive
                            .get(conceptId));
        internationalMappedConceptsAndICPCTargets.put(conceptId,
            internationalMappedConceptsAndICPCTargetsActive
                .get(conceptId) != null
                && !internationalMappedConceptsAndICPCTargetsActive
                    .get(conceptId).isEmpty()
                        ? internationalMappedConceptsAndICPCTargetsActive
                            .get(conceptId)
                        : internationalMappedConceptsAndICPCTargetsInactive
                            .get(conceptId));

        // Per Norway, if the ICD10 map is no-target, they are not interested.
        if ((internationalMappedConceptsAndTargets.get(conceptId) == null
            || internationalMappedConceptsAndTargets.get(conceptId).isEmpty())
            && (norwayMappedConceptsAndTargets.get(conceptId) == null
                || norwayMappedConceptsAndTargets.get(conceptId).isEmpty())) {
          continue;
        }

        final String inactivationIndicator =
            conceptInactivationIndicators.get(conceptId);
        final String inactiveConceptInfo = conceptId + NorwayReplacementReportSupport.COLUMN_DELIMITER
            + conceptIdToFSN.get(conceptId) + NorwayReplacementReportSupport.COLUMN_DELIMITER
            + (conceptIdToPTNO.get(conceptId) != null ? "no" : "en")
            + NorwayReplacementReportSupport.COLUMN_DELIMITER
            + (conceptIdToPTNO.get(conceptId) != null
                ? conceptIdToPTNO.get(conceptId)
                : conceptIdToPTEN.get(conceptId))
            + NorwayReplacementReportSupport.COLUMN_DELIMITER
            + (helseplattformensConceptIds.contains(conceptId) ? "TRUE"
                : "FALSE")
            + NorwayReplacementReportSupport.COLUMN_DELIMITER +
            // Display Norway ICD10 maps targets if available, otherwise use
            // International targets
            (norwayMappedConceptsAndTargets.get(conceptId) != null
                ? String.join(", ",
                    norwayMappedConceptsAndTargets.get(conceptId))
                : internationalMappedConceptsAndTargets.get(conceptId) != null
                    ? String.join(", ",
                        internationalMappedConceptsAndTargets.get(conceptId))
                    : "")
            + NorwayReplacementReportSupport.COLUMN_DELIMITER +
            // Do the same for Norway ICPC map targets
            (norwayMappedConceptsAndICPCTargets.get(conceptId) != null
                ? String.join(", ",
                    norwayMappedConceptsAndICPCTargets.get(conceptId))
                : internationalMappedConceptsAndICPCTargets
                    .get(conceptId) != null
                        ? String.join(", ",
                            internationalMappedConceptsAndICPCTargets
                                .get(conceptId))
                        : "")
            + NorwayReplacementReportSupport.COLUMN_DELIMITER + inactivationIndicator;
        if (conceptAssociationTargets.get(conceptId) == null) {
          results.add(inactiveConceptInfo);
        } else {
          for (final String associationTerm : conceptAssociationTargets
              .get(conceptId).keySet()) {
            final String associationTargets =
                conceptAssociationTargets.get(conceptId).get(associationTerm);
            final List<String> targetIds =
                Arrays.asList(associationTargets.split(","));
            for (final String targetId : targetIds) {

              // If active maps are present for this concept, use them. If no
              // active maps exist, use the inactive ones.
              norwayMappedConceptsAndTargets.put(targetId,
                  norwayMappedConceptsAndTargetsActive.get(targetId) != null
                      ? norwayMappedConceptsAndTargetsActive.get(targetId)
                      : norwayMappedConceptsAndTargetsInactive.get(targetId));
              internationalMappedConceptsAndTargets.put(targetId,
                  internationalMappedConceptsAndTargetsActive
                      .get(targetId) != null
                          ? internationalMappedConceptsAndTargetsActive
                              .get(targetId)
                          : internationalMappedConceptsAndTargetsInactive
                              .get(targetId));
              norwayMappedConceptsAndICPCTargets.put(targetId,
                  norwayMappedConceptsAndICPCTargetsActive.get(targetId) != null
                      ? norwayMappedConceptsAndICPCTargetsActive.get(targetId)
                      : norwayMappedConceptsAndICPCTargetsInactive
                          .get(targetId));
              internationalMappedConceptsAndICPCTargets.put(targetId,
                  internationalMappedConceptsAndICPCTargetsActive
                      .get(targetId) != null
                          ? internationalMappedConceptsAndICPCTargetsActive
                              .get(targetId)
                          : internationalMappedConceptsAndICPCTargetsInactive
                              .get(targetId));

              final String targetConceptInfo = associationTerm
                  + NorwayReplacementReportSupport.COLUMN_DELIMITER + targetId + NorwayReplacementReportSupport.COLUMN_DELIMITER
                  + conceptIdToFSN.get(targetId) + NorwayReplacementReportSupport.COLUMN_DELIMITER
                  + (conceptIdToPTNO.get(targetId) != null ? "no" : "en")
                  + NorwayReplacementReportSupport.COLUMN_DELIMITER
                  + (conceptIdToPTNO.get(targetId) != null
                      ? conceptIdToPTNO.get(targetId)
                      : conceptIdToPTEN.get(targetId))
                  + NorwayReplacementReportSupport.COLUMN_DELIMITER + NorwayReplacementReportSupport.COLUMN_DELIMITER +
                  // Display Norway maps targets if available, otherwise use
                  // International targets
                  (norwayMappedConceptsAndTargets.get(targetId) != null
                      ? String.join(", ",
                          norwayMappedConceptsAndTargets.get(targetId))
                      : internationalMappedConceptsAndTargets
                          .get(targetId) != null
                              ? String
                                  .join(", ",
                                      internationalMappedConceptsAndTargets
                                          .get(targetId))
                              : "")
                  + NorwayReplacementReportSupport.COLUMN_DELIMITER +
                  // Do the same for Norway ICPC map targets
                  (norwayMappedConceptsAndICPCTargets.get(targetId) != null
                      ? String.join(", ",
                          norwayMappedConceptsAndICPCTargets.get(targetId))
                      : internationalMappedConceptsAndICPCTargets
                          .get(targetId) != null
                              ? String.join(", ",
                                  internationalMappedConceptsAndICPCTargets
                                      .get(targetId))
                              : "");

              results.add(
                  inactiveConceptInfo + NorwayReplacementReportSupport.COLUMN_DELIMITER + targetConceptInfo);
            }
          }
        }
      }

      final DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
      final String dateStamp = dateFormat.format(new Date());
      final String filename = "/replacmentMapReport_ICD10NO_" + dateStamp;
      final File resultFile = NorwayReplacementReportSupport.writeResultFile(filename, results);
      final File zipFile = NorwayReplacementReportSupport.zipResultFile(filename, resultFile);

      // Send file to recipients
      NorwayReplacementReportSupport.emailReportFile("[MT2] Norway Replacement Map Report",
          "Hello,\n\nThe Norway replacement map report has been generated.",
          NorwayReplacementReportSupport.MAP_RECIPIENTS_PROPERTY, zipFile);

      LOGGER.info("Norway Replacement Map Report completed.");

    } catch (Exception e) {

      LOGGER.error("ERROR", e);

      NorwayReplacementReportSupport.emailReportError("Error generating Norway Replacement Map Report",
          "There was an error generating the Norway Replacement Map Report.  Please contact support for assistance.",
          NorwayReplacementReportSupport.MAP_RECIPIENTS_PROPERTY);

      throw new Exception("Norway Replacement Map Report failed to complete",
          e);
    }

  }

}
