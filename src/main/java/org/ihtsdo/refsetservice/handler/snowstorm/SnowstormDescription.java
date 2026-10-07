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
        final String fullSnowstormUrl = SnowstormConnection.getRestBaseUrl() + RefsetMemberService.getBranchPath(refset) + "/descriptions?limit="
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
        final String fullSnowstormUrl = SnowstormConnection.getRestBaseUrl() + branchPath + "/descriptions?limit=" + ELASTICSEARCH_MAX_RECORD_LENGTH
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
            final String fullSnowstormUrl = SnowstormConnection.getRestBaseUrl() + edition.getBranch() + "/descriptions?limit=" + ELASTICSEARCH_MAX_RECORD_LENGTH
                + "&conceptIds=" + batch.stream().collect(Collectors.joining(","));

            LOG.info("getDescriptions batch conceptIds={}", batch.size());
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

                // Populate concept with description-based data
                for (final String conceptId : batch) {

                    final Set<JsonNode> descriptionNodes = conceptDescriptionNodes.get(conceptId);
                    final List<Description> descriptions =
                        descriptionNodes != null ? populateDescriptions(descriptionNodes) : new ArrayList<>();
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
     * One entry per active description and language refset. A description in both Bokmål and Nynorsk is returned twice.
     *
     * @param descriptionNodes the description nodes
     * @return the list
     */
    static List<Description> populateDescriptions(final Set<JsonNode> descriptionNodes) {

        final List<Description> descriptions = new ArrayList<>();

        for (final JsonNode descriptionNode : descriptionNodes) {

            final JsonNode acceptabilityMap = descriptionNode.get(Description.Field.ACCEPTABILITY_MAP.getValue());
            if (acceptabilityMap != null && acceptabilityMap.isObject() && acceptabilityMap.size() > 0) {
                final Iterator<String> languageRefsetIds = acceptabilityMap.fieldNames();
                while (languageRefsetIds.hasNext()) {
                    final String languageRefsetId = languageRefsetIds.next();
                    descriptions.add(toDescription(descriptionNode, languageRefsetId, acceptabilityMap.get(languageRefsetId).asText()));
                }
            } else {
                descriptions.add(toDescription(descriptionNode, "", ""));
            }

        }

        return descriptions;

    }

    /**
     * Builds one description for a single language refset membership.
     *
     * @param descriptionNode the Snowstorm description
     * @param languageRefsetId the language refset, or blank when the description has no acceptability
     * @param acceptability PREFERRED, ACCEPTABLE, or blank
     * @return the description
     */
    private static Description toDescription(final JsonNode descriptionNode, final String languageRefsetId, final String acceptability) {

        final String typeId = textValue(descriptionNode, Description.Field.TYPE_ID);
        final String typeName;
        if (TYPE_ID_TO_TYPE_NAME.containsKey(typeId)) {
            typeName = TYPE_ID_TO_TYPE_NAME.get(typeId);
        } else if ("PREFERRED".equals(acceptability)) {
            typeName = "PT";
        } else if ("ACCEPTABLE".equals(acceptability)) {
            typeName = "AC";
        } else {
            typeName = textValue(descriptionNode, Description.Field.TYPE);
        }
        final String lang = textValue(descriptionNode, Description.Field.LANG);

        final Description description = new Description();
        description.setActive(booleanValue(descriptionNode, Description.Field.ACTIVE));
        description.setModuleId(textValue(descriptionNode, Description.Field.MODULE_ID));
        description.setReleased(booleanValue(descriptionNode, Description.Field.RELEASED));
        description.setReleasedEffectiveTime(longValue(descriptionNode, Description.Field.RELEASED_EFFECTIVE_TIME));
        description.setDescriptionId(textValue(descriptionNode, Description.Field.DESCRIPTION_ID));
        description.setTerm(textValue(descriptionNode, Description.Field.TERM));
        description.setConceptId(textValue(descriptionNode, Description.Field.CONCEPT_ID));
        description.setType(textValue(descriptionNode, Description.Field.TYPE));
        description.setTypeName(typeName);
        description.setEffectiveTime(textValue(descriptionNode, Description.Field.EFFECTIVE_TIME));
        description.setCaseSignificance(textValue(descriptionNode, Description.Field.CASE_SIGNIFICANCE));
        description.setLanguage(lang.toLowerCase());
        description.setLanguageId(languageRefsetId + typeName);
        description.setLanguageCode(languageRefsetId);
        description.setLanguageName(lang.toUpperCase() + " (" + typeName + ")");
        return description;
    }

    private static JsonNode fieldNode(final JsonNode node, final Description.Field field) {

        return node == null || field == null ? null : node.get(field.getValue());
    }

    private static boolean hasValue(final JsonNode value) {

        return value != null && !value.isNull() && !value.isMissingNode();
    }

    private static long longValue(final JsonNode node, final Description.Field field) {

        final JsonNode value = fieldNode(node, field);
        return hasValue(value) ? value.asLong() : 0L;
    }

    private static boolean booleanValue(final JsonNode node, final Description.Field field) {

        final JsonNode value = fieldNode(node, field);
        return hasValue(value) && value.asBoolean();
    }

    private static String textValue(final JsonNode node, final Description.Field field) {

        final JsonNode value = fieldNode(node, field);
        return hasValue(value) ? value.asText() : "";
    }

}
