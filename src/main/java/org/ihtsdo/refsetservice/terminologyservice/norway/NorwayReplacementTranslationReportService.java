/*
 * Copyright 2026 West Coast Informatics - All Rights Reserved.
 *
 * Ported from OTF-Mapping-Service NorwayReplacementTranslationReport.
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
 * Norway replacement translation report, ported from OTF-Mapping-Service.
 */
public class NorwayReplacementTranslationReportService {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(NorwayReplacementTranslationReportService.class);

  public void runReport() throws Exception {

    LOGGER.info("Start Norway Replacement Translation Report");
    
    try (final NorwaySnowstormClient client = new NorwaySnowstormClient()) {

      String searchAfter = null;

    LOGGER.info("Identify full list of concepts Norway is interested in, as stored in the Bokmål and Nynorsk langauge refsets (both standard and 'for General Practitioners' versions)");
    //Sample JSON
    /*
{
  "items": [
    {
      "active": true,
      "moduleId": "51000202101",
      "released": true,
      "releasedEffectiveTime": 20221015,
      "memberId": "fffff893-1c68-4dc7-8cee-d6ccc3b823e0",
      "refsetId": "61000202103",
      "referencedComponentId": "5215891000202119",
      "additionalFields": {
        "acceptabilityId": "900000000000548007"
      },
      "referencedComponent": {
        "active": true,
        "moduleId": "51000202101",
        "released": true,
        "releasedEffectiveTime": 20221015,
        "descriptionId": "5215891000202119",
        "term": "inneklemt diafragmahernie",
        "conceptId": "196904003",
        "typeId": "900000000000013009",
        "acceptabilityMap": {
          "61000202103": "PREFERRED"
        },
        "type": "SYNONYM",
        "lang": "no",
        "caseSignificance": "CASE_INSENSITIVE",
        "effectiveTime": "20221015"
      },
      "effectiveTime": "20221015"
    },
   */
    
    Set<String> bokmalRefsetsConceptIds = new HashSet<>();
    Set<String> nynorskRefsetsConceptIds = new HashSet<>();
    Set<String> bokmalGPRefsetsConceptIds = new HashSet<>();
    Set<String> nynorskGPRefsetsConceptIds = new HashSet<>();
    Set<String> allLangRefsetsConceptIds = new HashSet<>();

    bokmalRefsetsConceptIds.addAll(langRefsetLookup(client, "61000202103"));
    allLangRefsetsConceptIds.addAll(bokmalRefsetsConceptIds);
    nynorskRefsetsConceptIds.addAll(langRefsetLookup(client, "91000202106"));
    allLangRefsetsConceptIds.addAll(nynorskRefsetsConceptIds);
    bokmalGPRefsetsConceptIds.addAll(langRefsetLookup(client, "47351000202107"));
    allLangRefsetsConceptIds.addAll(bokmalGPRefsetsConceptIds);
    nynorskGPRefsetsConceptIds.addAll(langRefsetLookup(client, "188001000202106"));
    allLangRefsetsConceptIds.addAll(nynorskGPRefsetsConceptIds);
      
    LOGGER.info("Identify current and dependent branch paths");
      //Sample JSON
      /*
{
  "items": [
    {
      "name": "Norwegian Edition",
      "owner": "The Norwegian Directorate of eHealth",
      "shortName": "SNOMEDCT-NO",
      "branchPath": "MAIN/SNOMEDCT-NO",
      "dependantVersionEffectiveTime": 20230131,
      "dailyBuildAvailable": true,
      "latestDailyBuild": "2023-03-10-050823",
      "countryCode": "no",
      "latestVersion": {
        "shortName": "SNOMEDCT-NO",
        "importDate": "2022-10-14T11:19:37.529Z",
        "parentBranchPath": "MAIN/SNOMEDCT-NO", <--Look for concepts inactive in this branch
        "effectiveDate": 20221015,
        "version": "2022-10-15",
        "description": "SNOMEDCT-NO 20221015 import.",
        "dependantVersionEffectiveTime": 20220831,
        "branchPath": "MAIN/SNOMEDCT-NO/2022-10-15" <---And active in this branch
      },
     */
      String currentBranch = null;
      String previousVersionBranch = null;

      int returnedConceptsCount = 0;
      JsonNode doc;
      
      doc = client.getJson("/codesystems?forBranch=MAIN%2FSNOMEDCT-NO%2FREFSETS");
   
      for (final JsonNode node : doc.get("items")) {
        currentBranch = node.get("branchPath").asText();
        
        final JsonNode latestVersionNode = node.get("latestVersion");
        previousVersionBranch = latestVersionNode.get("branchPath").asText();
        
        //We're only interested in the first node
        break;
      }

      if (currentBranch == null || previousVersionBranch == null) {
        throw new Exception("Unable to determine current or previous version branch for Norway replacement translation report.");
      }
     
      
      LOGGER.info("Identify all in-scope concepts that are active in the previousVersionBranch, and inactive in the currentBranch");
      //Sample JSON
      /*
{
  "items": [
    "99999003",
    "99998006",
    "99997001",
    "99996005",
    "99995009",
    "99994008",
    "99993002",
    "99992007",
     */
      
      Set<String> activeInPreviousBranchScopeConcepts = new HashSet<>();
      searchAfter = null;
      int limit = 10000;
      
      while (true) {
        
        returnedConceptsCount = 0;
        
        doc = client.getJson("/"+previousVersionBranch.replaceAll("/", "%2F")+"/concepts?activeFilter=true&returnIdOnly=true&limit="+limit+ (searchAfter != null ? "&searchAfter=" + searchAfter : ""));
        
        // get total amount
        // Get concepts returned in this call (up to 10000)
        final JsonNode conceptIds = doc.get("items");

        for(int i = 0; i<conceptIds.size(); i++) {
          String activeConceptId = conceptIds.get(i).asText();
        if(allLangRefsetsConceptIds.contains(activeConceptId)) {
          activeInPreviousBranchScopeConcepts.add(activeConceptId);
        }

          returnedConceptsCount++;
        }
         
        searchAfter = doc.get("searchAfter").asText();
        // if we don't get a full page of results, we've processed the final page
        if(returnedConceptsCount < limit) {
            break;
        }
      }  
      
      Set<String> scopeConceptsInactivatedSincePreviousBranch = new HashSet<>();
      searchAfter = null;
      limit = 10000;
      
      while (true) {
        
        returnedConceptsCount = 0;
        
        doc = client.getJson("/"+currentBranch.replaceAll("/", "%2F")+"/concepts?activeFilter=false&returnIdOnly=true&limit="+limit+ (searchAfter != null ? "&searchAfter=" + searchAfter : ""));
        
        // get total amount
        // Get concepts returned in this call (up to 10000)
        final JsonNode inactiveConceptIds = doc.get("items");

        for(int i = 0; i<inactiveConceptIds.size(); i++) {
          returnedConceptsCount++;
          
          String inactiveConceptId = inactiveConceptIds.get(i).asText();
          //Don't include "garbage SCTIDs", defined as concepts that contain "10002" as the
          //7th through 11th characters.
          //E.g. "1217661000202103"
          //From Cato Spook in Norway:
          //   They got their nickname because earlier we made them as a plan B to satisfy 
          //   Helseplattformen who wanted SCTIDs to ICD-10 codes that did not have a SCTID 
          //   mapping to them. You can see the ICD-10 code in the square brackets in the FSN. 
          //   When we find a reason to inactivate these SCTIDs we do it, because they are very 
          //   primitive and don’t have the functionality of a properly made SCTID. They are just 
          //   inactivated without replacements because they are “garbage”. We don’t need them 
          //   in our list.
          if(inactiveConceptId.length()>11 && inactiveConceptId.substring(6, 11).equals("10002")) {
            continue;
          }
          
          if(activeInPreviousBranchScopeConcepts.contains(inactiveConceptId)) {
            scopeConceptsInactivatedSincePreviousBranch.add(inactiveConceptId);
          }

        }
         
        searchAfter = doc.get("searchAfter").asText();
        // if we don't get a full page of results, we've processed the final page
        if(returnedConceptsCount < limit) {
            break;
        }
      }                
      
      LOGGER.info("Grab all replacement concepts");
      //Sample JSON
      /*
{
  "items": [
    {
      "conceptId": "267214002",
      "fsn": {
        "term": "Congenital abnormality of uterus - baby delivered (disorder)",
        "lang": "en"
      },
      "pt": {
        "term": "Congenital abnormality of uterus - baby delivered",
        "lang": "en"
      },
      "active": false,
      "effectiveTime": "20230131",
      "released": true,
      "releasedEffectiveTime": 20230131,
      "inactivationIndicator": "CLASSIFICATION_DERIVED_COMPONENT",
      "associationTargets": {
        "PARTIALLY_EQUIVALENT_TO": [
          "267212003",
          "289256000"
        ]
      },
     */
      
      Map<String,Map<String,String>> conceptAssociationTargets = new HashMap<>();
      Map<String,String> conceptInactivationIndicators = new HashMap<>();
      List<String> inactivedScopeConcepts = new ArrayList<>(scopeConceptsInactivatedSincePreviousBranch);
      int batchSize = 100;
      int counter = 0;

      int inactiveConceptsCount = inactivedScopeConcepts.size();
      boolean reachedFinalConcept= false;
      Set<String> replacementConceptIds = new HashSet<>();
      
      while (!reachedFinalConcept && inactiveConceptsCount != 0) {
        
        String targetUri = "/browser/MAIN%2FSNOMEDCT-NO/concepts?";
        for (int i = 0; i < batchSize; i++) {
          targetUri = targetUri + "conceptIds=" + inactivedScopeConcepts.get(counter) + "&";
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
          String conceptId = conceptNode.get("conceptId").asText();
          String inactivationIndicator = conceptNode.get("inactivationIndicator").asText().replaceAll("_", " ");
          conceptInactivationIndicators.put(conceptId, inactivationIndicator);
          
          JsonNode associationTargetsNode = conceptNode.findValue("associationTargets");
          if (associationTargetsNode == null || associationTargetsNode.size() == 0
              || associationTargetsNode.fields() == null) {
            conceptAssociationTargets.put(conceptId, null);
            continue;
          }

          Entry<String, JsonNode> entry = associationTargetsNode.fields().next();
          String associationType = entry.getKey().replaceAll("_"," ") + " association reference set";
          String values = entry.getValue().toString();
          if (values.contains("[")) {
            values = values.substring(1, values.length() - 1);
          }
          values = values.replaceAll("\"", "");
          // Keep track of all replacement concept Ids for description lookup later
          for (String value : values.split(",")) {
            replacementConceptIds.add(value);
          }
          Map<String,String> associationTargets = new HashMap<>();
          associationTargets.put(associationType,values);
          conceptAssociationTargets.put(conceptId, associationTargets);
        }
      }            
      
      LOGGER.info("Grab all inactive and replacement concept descriptions");
      //Sample JSON
      /*
{
{
  "items": [
    {
      "active": true,
      "moduleId": "51000202101",
      "released": true,
      "releasedEffectiveTime": 20220415,
      "descriptionId": "2344071000202112",
      "term": "utilsiktet forgiftning med intravenøst bedøvelsesmiddel",
      "conceptId": "216579009",
      "typeId": "900000000000013009",
      "acceptabilityMap": {
        "61000202103": "ACCEPTABLE"
      },
      "type": "SYNONYM",
      "lang": "no",
      "caseSignificance": "CASE_INSENSITIVE",
      "effectiveTime": "20220415"
    },
     */
      
      Map<String,String> conceptIdToFSN = new HashMap<>();
      Map<String,String> conceptIdToPTEN = new HashMap<>();
      Map<String,String> conceptIdToPTNO = new HashMap<>();
      
      batchSize = 10;
      counter = 0;
      
      List<String> inactiveAndReplacementTargets = new ArrayList<>();
      inactiveAndReplacementTargets.addAll(inactivedScopeConcepts);
      inactiveAndReplacementTargets.addAll(replacementConceptIds);
      
      int inactiveAndReplacementConceptsCount = inactiveAndReplacementTargets.size();
      
      reachedFinalConcept= false;
      
      while (!reachedFinalConcept && inactiveAndReplacementConceptsCount != 0) {
        
        returnedConceptsCount = 0;
        
        String targetUri = "/MAIN%2FSNOMEDCT-NO/descriptions?";
        for (int i = 0; i < batchSize; i++) {
          targetUri = targetUri + "conceptIds=" + inactiveAndReplacementTargets.get(counter) + "&";
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
          String active = conceptNode.get("active").asText();
          if(active.equals("false")) {
            continue;
          }
          
          String conceptId = conceptNode.get("conceptId").asText();
          String type = conceptNode.get("type").asText();
          String term = conceptNode.get("term").asText();
          
          if(type.equals("FSN")) {
            conceptIdToFSN.put(conceptId, term);
            continue;
          }
          
          String lang = conceptNode.get("lang").asText();
          
          JsonNode acceptabilityMapNode = conceptNode.findValue("acceptabilityMap");
          if (acceptabilityMapNode == null || acceptabilityMapNode.size() == 0
              || acceptabilityMapNode.fields() == null) {
            conceptAssociationTargets.put(conceptId, null);
            continue;
          }

          // Grab American English preferred term
          if(lang.equals("en") && acceptabilityMapNode.findValue("900000000000509007") != null && acceptabilityMapNode.findValue("900000000000509007").asText().equals("PREFERRED")) {
            conceptIdToPTEN.put(conceptId, term);
            continue;
          }
          // Grab Nynorsk preferred term
          if(lang.equals("no") && acceptabilityMapNode.findValue("61000202103") != null && acceptabilityMapNode.findValue("61000202103").asText().equals("PREFERRED")) {
            conceptIdToPTNO.put(conceptId, term);
            continue;
          }
        }
      }
      

      List<String> results = new ArrayList<>();
      // Add header row
      results.add(
          "Id\tFSN\tSemTag\tTranslation(s)\tBokmål\tNynorsk\tBokmål GP\tNynorsk GP\tReason\tAssoc Type\tAssoc Id\tAssoc FSN\tAssoc Translation(s)");

      // Add result rows, in conceptId order
      Collections.sort(inactivedScopeConcepts);
      for (String conceptId : inactivedScopeConcepts) {
        
        
        final String inactivationIndicator = conceptInactivationIndicators.get(conceptId);
        final String inactiveConceptInfo = 
            conceptId + NorwayReplacementReportSupport.COLUMN_DELIMITER +
            conceptIdToFSN.get(conceptId) + NorwayReplacementReportSupport.COLUMN_DELIMITER +
            conceptIdToFSN.get(conceptId).substring(conceptIdToFSN.get(conceptId).indexOf("("), conceptIdToFSN.get(conceptId).indexOf(")") + 1) + NorwayReplacementReportSupport.COLUMN_DELIMITER +
            (conceptIdToPTNO.get(conceptId)!=null ? conceptIdToPTNO.get(conceptId) : "") + NorwayReplacementReportSupport.COLUMN_DELIMITER+
            (bokmalRefsetsConceptIds.contains(conceptId) ? "TRUE" : "FALSE") + NorwayReplacementReportSupport.COLUMN_DELIMITER +
            (nynorskRefsetsConceptIds.contains(conceptId) ? "TRUE" : "FALSE") + NorwayReplacementReportSupport.COLUMN_DELIMITER +
            (bokmalGPRefsetsConceptIds.contains(conceptId) ? "TRUE" : "FALSE") + NorwayReplacementReportSupport.COLUMN_DELIMITER +
            (nynorskGPRefsetsConceptIds.contains(conceptId) ? "TRUE" : "FALSE") + NorwayReplacementReportSupport.COLUMN_DELIMITER +            
            inactivationIndicator;
        if(conceptAssociationTargets.get(conceptId) == null) {
          results.add(inactiveConceptInfo);
        }
        else {
          for(String associationTerm : conceptAssociationTargets.get(conceptId).keySet()) {
            String associationTargets = conceptAssociationTargets.get(conceptId).get(associationTerm);
            List<String> targetIds = Arrays.asList(associationTargets.split(","));
            for(String targetId : targetIds) {
                            
              final String targetConceptInfo = 
                  associationTerm + NorwayReplacementReportSupport.COLUMN_DELIMITER + 
              targetId + NorwayReplacementReportSupport.COLUMN_DELIMITER+
              conceptIdToFSN.get(targetId) + NorwayReplacementReportSupport.COLUMN_DELIMITER +
              (conceptIdToPTNO.get(targetId)!=null ? conceptIdToPTNO.get(targetId) : "");

              results.add(inactiveConceptInfo + NorwayReplacementReportSupport.COLUMN_DELIMITER + targetConceptInfo);
            }
          }
        }
      }

      final DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
      final String dateStamp = dateFormat.format(new Date());
      final String filename = "/replacmentTranslationReport_" + dateStamp;
      final File resultFile = NorwayReplacementReportSupport.writeResultFile(filename, results);
      final File zipFile = NorwayReplacementReportSupport.zipResultFile(filename, resultFile);

      // Send file to recipients
      NorwayReplacementReportSupport.emailReportFile("[MT2] Norway Replacement Translation Report",
          "Hello,\n\nThe Norway replacement translation report has been generated.",
          NorwayReplacementReportSupport.TRANSLATION_RECIPIENTS_PROPERTY, zipFile);

      LOGGER.info("Norway Replacement Translation Report completed.");

    } catch (Exception e) {
      LOGGER.error("ERROR", e);
      
      NorwayReplacementReportSupport.emailReportError("Error generating Norway Replacement Translation Report",
          "There was an error generating the Norway Replacement Translation Report.  Please contact support for assistance.",
          NorwayReplacementReportSupport.TRANSLATION_RECIPIENTS_PROPERTY);

      
      throw new Exception(
          "Norway Replacement Translation Report mojo failed to complete", e);
    }
  }


  private Set<String> langRefsetLookup(final NorwaySnowstormClient client, final String refsetId) throws Exception {

    String searchAfter = null;
    int limit = 10000;
    Set<String> langRefsetsConceptIds = new HashSet<>();
    
    while (true) {
      
      int returnedConceptsCount = 0;
      
      
      final JsonNode doc = client.getJson("/MAIN%2FSNOMEDCT-NO%2FREFSETS/members?referenceSet="+refsetId+"&limit="+limit+ (searchAfter != null ? "&searchAfter=" + searchAfter : ""));
   
      // get total amount
      // Get concepts returned in this call (up to 1000)
      for (final JsonNode conceptNode : doc.get("items")) {
        final JsonNode referencedComponentNode = conceptNode.get("referencedComponent");
        if(referencedComponentNode != null) {
          langRefsetsConceptIds.add(referencedComponentNode.get("conceptId").asText());
          returnedConceptsCount++;
        }
      }
       
      searchAfter = doc.get("searchAfter").asText();
      // if we don't get a full page of results, we've processed the final page
      if(returnedConceptsCount < limit) {
          break;
      }
    }   
    
    return langRefsetsConceptIds;
  }
  
  

}
