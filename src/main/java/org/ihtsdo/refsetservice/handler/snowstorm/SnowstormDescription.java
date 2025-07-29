/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.handler.snowstorm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.Description;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.ihtsdo.refsetservice.util.ThreadLocalMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * The Class SnowstormDescription.
 */
public class SnowstormDescription extends SnowstormAbstract {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SnowstormDescription.class);

    /** The Constant typeIdToTypeName. */
    private static final Map<String, String> TYPE_ID_TO_TYPE_NAME = new HashMap<>();
    static {
        TYPE_ID_TO_TYPE_NAME.put("900000000000003001", "FSN");
        TYPE_ID_TO_TYPE_NAME.put("900000000000550004", "DEF");
    };

    /**
     * Populate all language descriptions.
     *
     * @param refset the refset
     * @param conceptsToProcess the concepts to process
     * @throws Exception the exception
     */
    public static void populateAllLanguageDescriptions(final Refset refset, final List<Concept> conceptsToProcess) throws Exception {

        if (conceptsToProcess == null || conceptsToProcess.isEmpty()) {
            return;
        }

        // Create Snowstorm URL
        final String fullSnowstormUrl = SnowstormConnection.getBaseUrl() + RefsetMemberService.getBranchPath(refset) + "/descriptions?limit="
            + ELASTICSEARCH_MAX_RECORD_LENGTH + "&conceptIds=" + conceptsToProcess.stream().map(Concept::getCode).collect(Collectors.joining(","));

        // Call Snowstorm
        try (final Response response = SnowstormConnection.getResponse(fullSnowstormUrl)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                LOG.error(formatErrorMessage(response));
                throw new Exception("call to url '" + fullSnowstormUrl + "' wasn't successful. Status: " + response.getStatus() + " Message: "
                    + response.getStatusInfo().getReasonPhrase());
            }

            final String resultString = SnowstormConnection.readEntityAsString(response);
            final JsonNode root = ThreadLocalMapper.get().readTree(resultString);

            final JsonNode allDescriptionNodes = root.get("items");
            final Iterator<JsonNode> descriptionIterator = allDescriptionNodes.iterator();
            final HashMap<String, Set<JsonNode>> conceptDescriptionNodes = new HashMap<>();

            // Assign descriptions to proper concept
            while (descriptionIterator.hasNext()) {

                final JsonNode descriptionNode = descriptionIterator.next();

                if (descriptionNode.get("active").asBoolean()) {

                    final String conceptId = descriptionNode.get("conceptId").asText();

                    if (!conceptDescriptionNodes.containsKey(conceptId)) {

                        conceptDescriptionNodes.put(conceptId, new HashSet<JsonNode>());
                    }

                    conceptDescriptionNodes.get(conceptId).add(descriptionNode);
                }

            }

            // Process and sort each concept's descriptions
            final Map<String, List<Map<String, String>>> conceptDescriptionMap = new HashMap<>();

            final List<String> nonDefaultPreferredTerms = RefsetMemberService.identifyNonDefaultPreferredTerms(refset.getEdition());

            for (final String conceptId : conceptDescriptionNodes.keySet()) {

                final Set<JsonNode> descriptionNodes = conceptDescriptionNodes.get(conceptId);
                Set<Map<String, String>> descriptions = new HashSet<>();

                descriptions =
                    RefsetMemberService.processDescriptionNodes(descriptionNodes, refset.getEdition().getDefaultLanguageRefsets(), nonDefaultPreferredTerms);

                final List<Map<String, String>> sortedDescriptions =
                    RefsetMemberService.sortConceptDescriptions(conceptId, descriptions, refset.getEdition(), nonDefaultPreferredTerms);
                conceptDescriptionMap.put(conceptId, sortedDescriptions);
            }

            // Populate concept with description-based data
            for (final Concept concept : conceptsToProcess) {

                final List<Map<String, String>> descriptions = conceptDescriptionMap.get(concept.getCode());

                if (descriptions == null || descriptions.isEmpty()) {

                    LOG.debug("Description not retrieved for concept {}", concept.getCode());
                    continue;
                }

                concept.setDescriptions(descriptions);

                if (descriptions.get(0) != null) {

                    concept.setName(descriptions.get(0).get(RefsetMemberService.DESCRIPTION_TERM));
                } else {

                    for (final Map<String, String> description : descriptions) {

                        if (description == null) {
                            continue;
                        }

                        if (description.get(RefsetMemberService.LANGUAGE_ID).equals(RefsetMemberService.PREFERRED_TERM_EN)) {

                            concept.setName(description.get(RefsetMemberService.DESCRIPTION_TERM));
                            break;
                        }

                    }

                }

            }

        } catch (final Exception ex) {

            LOG.error("Could not retrieve descriptions " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Populate all language descriptions.
     *
     * @param edition the edition
     * @param branchPath the branch path
     * @param conceptsToProcess the concepts to process
     * @throws Exception the exception
     */
    public static void populateAllLanguageDescriptions(final Edition edition, final String branchPath, final List<Concept> conceptsToProcess) throws Exception {

        if (conceptsToProcess == null || conceptsToProcess.isEmpty()) {
            return;
        }

        // Create Snowstorm URL
        final String fullSnowstormUrl = SnowstormConnection.getBaseUrl() + branchPath + "/descriptions?limit=" + ELASTICSEARCH_MAX_RECORD_LENGTH
            + "&conceptIds=" + conceptsToProcess.stream().map(Concept::getCode).collect(Collectors.joining(","));

        // Call Snowstorm
        LOG.info("populateAllLanguageDescriptions with URL: {}", fullSnowstormUrl);
        try (final Response response = SnowstormConnection.getResponse(fullSnowstormUrl)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                LOG.error(formatErrorMessage(response));
                throw new Exception("call to url '" + fullSnowstormUrl + "' wasn't successful. Status: " + response.getStatus() + " Message: "
                    + response.getStatusInfo().getReasonPhrase());
            }

            final String resultString = SnowstormConnection.readEntityAsString(response);
            final JsonNode root = ThreadLocalMapper.get().readTree(resultString);

            final JsonNode allDescriptionNodes = root.get("items");
            final Iterator<JsonNode> descriptionIterator = allDescriptionNodes.iterator();
            final HashMap<String, Set<JsonNode>> conceptDescriptionNodes = new HashMap<>();

            // Assign descriptions to proper concept
            while (descriptionIterator.hasNext()) {

                final JsonNode descriptionNode = descriptionIterator.next();
                if (descriptionNode.get("active").asBoolean()) {
                    final String conceptId = descriptionNode.get("conceptId").asText();
                    if (!conceptDescriptionNodes.containsKey(conceptId)) {
                        conceptDescriptionNodes.put(conceptId, new HashSet<>());
                    }
                    conceptDescriptionNodes.get(conceptId).add(descriptionNode);
                }
            }

            // Process and sort each concept's descriptions
            final Map<String, List<Map<String, String>>> conceptDescriptionMap = new HashMap<>();
            final List<String> nonDefaultPreferredTerms = RefsetMemberService.identifyNonDefaultPreferredTerms(edition);
            final Set<String> defaultLanguageRefsets = edition.getDefaultLanguageRefsets();

            for (final String conceptId : conceptDescriptionNodes.keySet()) {

                final Set<JsonNode> descriptionNodes = conceptDescriptionNodes.get(conceptId);
                final Set<Map<String, String>> descriptions =
                    RefsetMemberService.processDescriptionNodes(descriptionNodes, defaultLanguageRefsets, nonDefaultPreferredTerms);
                final List<Map<String, String>> sortedDescriptions =
                    RefsetMemberService.sortConceptDescriptions(conceptId, descriptions, edition, nonDefaultPreferredTerms);
                conceptDescriptionMap.put(conceptId, sortedDescriptions);
            }

            // Populate concept with description-based data
            for (final Concept concept : conceptsToProcess) {

                final List<Map<String, String>> descriptions = conceptDescriptionMap.get(concept.getCode());
                if (descriptions == null || descriptions.isEmpty()) {
                    LOG.debug("Description not retrieved for concept {}", concept.getCode());
                    continue;
                }

                concept.setDescriptions(descriptions);

                if (descriptions.get(0) != null) {
                    concept.setName(descriptions.get(0).get(RefsetMemberService.DESCRIPTION_TERM));
                } else {
                    for (final Map<String, String> description : descriptions) {
                        if (description == null) {
                            continue;
                        }
                        if (description.get(RefsetMemberService.LANGUAGE_ID).equals(RefsetMemberService.PREFERRED_TERM_EN)) {
                            concept.setName(description.get(RefsetMemberService.DESCRIPTION_TERM));
                            break;
                        }
                    }
                }
            }

        } catch (final Exception ex) {

            LOG.error("Could not retrieve descriptions " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Gets the descriptions for a list of concept ids.
     *
     * @param edition the edition
     * @param conceptIds the concept ids
     * @return the descriptions
     */
    public static Map<String, List<Description>> getDescriptions(final Edition edition, final List<String> conceptIds) {

        final Map<String, List<Description>> conceptDescriptions = new HashMap<>();

        if (conceptIds == null || conceptIds.isEmpty()) {
            return conceptDescriptions;
        }

        // there could be thousands of descriptions, so we need to limit the number of descriptions requested
        // batch the concept ids into groups of 230
        final List<List<String>> conceptIdBatches = new ArrayList<>();
        final int batchSize = 230;
        for (int i = 0; i < conceptIds.size(); i += batchSize) {
            conceptIdBatches.add(conceptIds.subList(i, Math.min(i + batchSize, conceptIds.size())));
        }

        for (final List<String> batch : conceptIdBatches) {

            // Create Snowstorm URL
            final String fullSnowstormUrl = SnowstormConnection.getBaseUrl() + edition.getBranch() + "/descriptions?limit=" + ELASTICSEARCH_MAX_RECORD_LENGTH
                + "&conceptIds=" + batch.stream().collect(Collectors.joining(","));

            // Call Snowstorm
            try (final Response response = SnowstormConnection.getResponse(fullSnowstormUrl)) {

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                    LOG.error(formatErrorMessage(response));

                    throw new Exception("call to url '" + fullSnowstormUrl + "' wasn't successful. Status: " + response.getStatus() + " Message: "
                        + response.getStatusInfo().getReasonPhrase());
                }

                final String resultString = SnowstormConnection.readEntityAsString(response);
                final JsonNode root = ThreadLocalMapper.get().readTree(resultString);

                final JsonNode allDescriptionNodes = root.get("items");
                final Iterator<JsonNode> descriptionIterator = allDescriptionNodes.iterator();
                final HashMap<String, Set<JsonNode>> conceptDescriptionNodes = new HashMap<>();

                // Assign descriptions to proper concept
                while (descriptionIterator.hasNext()) {

                    final JsonNode descriptionNode = descriptionIterator.next();
                    if (descriptionNode.get("active").asBoolean()) {

                        final String conceptId = descriptionNode.get("conceptId").asText();

                        if (!conceptDescriptionNodes.containsKey(conceptId)) {
                            conceptDescriptionNodes.put(conceptId, new HashSet<JsonNode>());
                        }
                        conceptDescriptionNodes.get(conceptId).add(descriptionNode);
                    }
                }

                final List<String> nonDefaultPreferredTerms = RefsetMemberService.identifyNonDefaultPreferredTerms(edition);

                // Populate concept with description-based data
                for (final String conceptId : batch) {

                    final List<Description> descriptions =
                        populateDescriptions(conceptDescriptionNodes.get(conceptId), edition.getDefaultLanguageRefsets(), nonDefaultPreferredTerms);
                    conceptDescriptions.put(conceptId, descriptions);

                }

            } catch (final Exception ex) {

                LOG.error("Could not retrieve descriptions " + ex.getMessage());
                ex.printStackTrace();
            }

        }

        return conceptDescriptions;

    }

    /**
     * Populate description.
     *
     * @param descriptionNodes the description nodes
     * @param defaultLanguageRefsets the default language refsets
     * @param nonDefaultPreferredTerms the non default preferred terms
     * @return the list
     * @throws Exception the exception
     */
    private static List<Description> populateDescriptions(final Set<JsonNode> descriptionNodes, final Set<String> defaultLanguageRefsets,
        final List<String> nonDefaultPreferredTerms) throws Exception {

        final List<Description> descriptions = new ArrayList<>();

        for (final JsonNode descriptionNode : descriptionNodes) {

            final JsonNode acceptabilityMap = descriptionNode.get(Description.Field.ACCEPTABILITY_MAP.getValue());
            String acceptability = null;
            String languageId = null;
            String typeName = null;

            for (final String langRefsetId : defaultLanguageRefsets) {

                if (acceptabilityMap.has(langRefsetId)) {

                    acceptability = acceptabilityMap.get(langRefsetId).asText();
                    languageId = langRefsetId;
                    break;
                }

            }

            if (acceptability != null && (nonDefaultPreferredTerms.isEmpty() || "PREFERRED".equals(acceptability))) {

                final String typeId = descriptionNode.get(Description.Field.TYPE_ID.getValue()).asText();
                typeName = TYPE_ID_TO_TYPE_NAME.getOrDefault(typeId, ("PREFERRED".equals(acceptability)) ? "PT" : "AC");

                final Description description = new Description();
                description.setActive(descriptionNode.get(Description.Field.ACTIVE.getValue()).asBoolean());
                description.setModuleId(descriptionNode.get(Description.Field.MODULE_ID.getValue()).asText());
                description.setReleased(descriptionNode.get(Description.Field.RELEASED.getValue()).asBoolean());
                description.setReleasedEffectiveTime(descriptionNode.get(Description.Field.RELEASED_EFFECTIVE_TIME.getValue()).asLong());
                description.setDescriptionId(descriptionNode.get(Description.Field.DESCRIPTION_ID.getValue()).asText());
                description.setTerm(descriptionNode.get(Description.Field.TERM.getValue()).asText());
                description.setConceptId(descriptionNode.get(Description.Field.CONCEPT_ID.getValue()).asText());
                description.setType(descriptionNode.get(Description.Field.TYPE.getValue()).asText());
                description.setTypeName(typeName);
                description.setEffectiveTime(descriptionNode.get(Description.Field.EFFECTIVE_TIME.getValue()).asText());
                description.setCaseSignificance(descriptionNode.get(Description.Field.CASE_SIGNIFICANCE.getValue()).asText());

                description.setLanguage(descriptionNode.get(Description.Field.LANG.getValue()).asText().toLowerCase());
                description.setLanguageId(languageId + typeName);
                description.setLanguageCode(languageId);
                description.setLanguageName(descriptionNode.get(Description.Field.LANG.getValue()).asText().toUpperCase() + " (" + typeName + ")");
                // description.setLanguage();
                descriptions.add(description);

            }

        }

        return descriptions;

    }

}
