/**
 * 
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.SearchParameters;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service class to get refset member concept information from a terminology
 * service.
 */
public class RefsetMemberService {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(RefsetMemberService.class);

    /** The simple date format. */
    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");

    /** The refset to language map. */
    private static final Map<String, String> refsetToLanguagesMap = new HashMap<>();

    /** The description term. */
    private static final String DESCRIPTION_TERM = "term";

    /** The description type. */
    private static final String DESCRIPTION_TYPE = "type";

    /** The description language. */
    private static final String DESCRIPTION_LANGUAGE = "language";

    /** The description language. */
    private static final String DESCRIPTION_ID = "descriptionId";

    /** The description language. */
    private static final String LANGUAGE_ID = "languageId";

    /** The description language. */
    private static final String LANGUAGE_NAME = "languageName";

    /** The Constant DESC_LANG. */
    private static final String COLUMN_IDENTIFIER = "columnId";

    /** The fully specified name description type. */
    private static final String TYPE_FSN = "FSN";

    /** The preferred term description type. */
    private static final String TYPE_DEFAULT_PT = "PT";

    /** The other description type. */
    private static final String TYPE_OTHER_PT = "OTHER";

    static {
        // TODO: Remove once Edition updated
        refsetToLanguagesMap.put("450828004", "es");
        refsetToLanguagesMap.put("32570271000036106", "en");
        refsetToLanguagesMap.put("900000000000509007", "en");
        refsetToLanguagesMap.put("21000172104", "fr");
        refsetToLanguagesMap.put("31000172101", "nl");
        refsetToLanguagesMap.put("554461000005103", "da");
        refsetToLanguagesMap.put("71000181105", "et");
        refsetToLanguagesMap.put("5641000179103", "es");
        refsetToLanguagesMap.put("21000220103", "en");
        refsetToLanguagesMap.put("61000202103", "no");
        refsetToLanguagesMap.put("46011000052107", "sv");
    }

    /**
     * Get the refset member concepts.
     *
     * @param refsetId the refset ID
     * @param searchParameters the search parameters
     * @return the refset member concepts
     * @throws Exception the exception
     */
    public static ConceptResultList getRefsetMembers(String refsetId,
        final SearchParameters searchParameters) throws Exception {

        // TODO remove this hardcoding once good data is in
        // refsetId = "721000172106";
        List<String> nonDefaultPreferredTerms = null;
        ConceptResultList members = new ConceptResultList();
        String url = SnowstormConnection.BASE_URL
                + "browser/MAIN%2FSNOMEDCT-BE%2F2021-03-15/members?referenceSet=" + refsetId;

        if (searchParameters.getOffset() != null) {
            url += "&offset=" + searchParameters.getOffset();
        }

        if (searchParameters.getLimit() != null) {
            url += "&limit=" + searchParameters.getLimit();
        }

        // if (searchParameters.getSortAscending() != null) {
        //
        // }
        //
        // if (searchParameters.getSort() != null) {
        //
        // }

        try (final Response response = SnowstormConnection.getResponse(url)) {

            final String resultString = response.readEntity(String.class);

            try (final TerminologyService service = new TerminologyService()) {
                Refset r = service.get(refsetId, Refset.class);
                Edition e = r.getEdition();

                // TODO: Remove Hardcoding of refsetToLanguageMap
                nonDefaultPreferredTerms =
                        refsetToLanguagesMap.keySet().stream().collect(Collectors.toList());
                final String langToRemove = refsetToLanguagesMap.entrySet().stream()
                        .filter(entry -> e.getDefaultLanguageCode().equals(entry.getValue()))
                        .map(Map.Entry::getKey).findFirst().get();
                nonDefaultPreferredTerms.remove(langToRemove);

                Collections.sort(nonDefaultPreferredTerms);

                final ObjectMapper mapper = new ObjectMapper();
                final JsonNode root = mapper.readTree(resultString.toString());

                final JsonNode items = root.get("items");
                final Iterator<JsonNode> iterator = items.iterator();

                while (iterator.hasNext()) {
                    final JsonNode item = iterator.next();
                    final Concept concept = new Concept();
                    concept.setCode(item.get("referencedComponentId").asText());
                    concept.setTerminology("SNOMEDCT");
                    concept.setMemberStatus(item.get("active").asBoolean());
                    concept.setHistoryVisible(true);
                    concept.setFeedbackVisible(true);
                    concept.setMemberEffectiveTime(
                            SIMPLE_DATE_FORMAT.parse(item.get("releasedEffectiveTime").asText()));

                    members.getItems().add(concept);
                }

                members = getConceptDescriptions(refsetId, members, nonDefaultPreferredTerms,
                        e.getDefaultLanguageCode(), searchParameters);

                members.setTotal(root.get("totalElements").asInt());

                return members;
            }

        }
    }

    /**
     * Gets the concept descriptions.
     *
     * @param refsetId the refset id
     * @param members the members
     * @param nonDefaultPreferredTerms the non-default preferred terms
     * @param defaultLanguageCode the default language code
     * @param searchParameters the search parameters
     * @return the concept descriptions
     * @throws MalformedURLException the malformed URL exception
     * @throws Exception the exception
     */
    public static ConceptResultList getConceptDescriptions(final String refsetId,
        final ConceptResultList members, final List<String> nonDefaultPreferredTerms,
        final String defaultLanguageCode, final SearchParameters searchParameters)
        throws MalformedURLException, Exception {

        final StringBuffer conceptIds = new StringBuffer();

        for (int i = 0; i < members.size(); i++) {

            final Concept concept = (Concept) members.getItems().toArray()[i];
            conceptIds.append(concept.getCode());

            if (i + 1 < members.size()) {
                conceptIds.append(",");
            }
        }

        // SHould have 3 results
        String url = SnowstormConnection.BASE_URL
                + "browser/MAIN%2FSNOMEDCT-BE%2F2021-03-15/concepts?conceptIds=" + conceptIds;

        if (searchParameters.getOffset() != null) {
            url += "&offset=" + searchParameters.getOffset();
        }

        if (searchParameters.getLimit() != null) {
            url += "&limit=" + searchParameters.getLimit();
        }

        try (final Response response = SnowstormConnection.getResponse(url)) {

            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

            final JsonNode concepts = root.get("items");
            final Iterator<JsonNode> conceptIterator = concepts.iterator();
            final HashMap<String, Set<Map<String, String>>> conceptDescriptions = new HashMap<>();

            while (conceptIterator.hasNext()) {

                final JsonNode concept = conceptIterator.next();
                final String conceptId = concept.get("conceptId").asText();

                final JsonNode descriptionNodes = concept.get("descriptions");
                final Iterator<JsonNode> descriptionIterator = descriptionNodes.iterator();

                final Set<Map<String, String>> descriptions = new HashSet<>();

                while (descriptionIterator.hasNext()) {

                    final Map<String, String> descriptionMap = new HashMap<>();
                    final JsonNode descriptionNode = descriptionIterator.next();

                    if (descriptionNode.get("active").asBoolean()) {
                        final JsonNode acceptabilityMap = descriptionNode.get("acceptabilityMap");
                        String acceptability = null;
                        String languageId = null;
                        String typeName = null;

                        for (String langRefsetId : refsetToLanguagesMap.keySet()) {
                            if (acceptabilityMap.has(langRefsetId)) {
                                acceptability = acceptabilityMap.get(langRefsetId).asText();
                                languageId = langRefsetId;
                                break;
                            }
                        }

                        if (acceptability != null && "PREFERRED".equals(acceptability)) {
                            if ("900000000000003001"
                                    .equals(descriptionNode.get("typeId").asText())) {
                                typeName = "FSN";
                            } else {
                                typeName = "PT";
                            }
                        }

                        if (typeName != null) {
                            descriptionMap.put(DESCRIPTION_TERM,
                                    descriptionNode.get("term").asText());
                            descriptionMap.put(DESCRIPTION_TYPE, typeName);
                            descriptionMap.put(DESCRIPTION_ID,
                                    descriptionNode.get("descriptionId").asText());
                            descriptionMap.put(LANGUAGE_ID, languageId);
                            descriptionMap.put(LANGUAGE_NAME,
                                    descriptionNode.get("lang").asText().toUpperCase() + " ("
                                            + typeName + ")");
                            descriptionMap.put(DESCRIPTION_LANGUAGE,
                                    descriptionNode.get("lang").asText());
                            descriptionMap.put(COLUMN_IDENTIFIER, languageId + "-" + typeName);

                            descriptions.add(descriptionMap);
                        }
                    }
                }

                conceptDescriptions.put(conceptId, descriptions);
            }

            final Map<String, List<Map<String, String>>> sortedDescriptions = sortDescriptions(
                    refsetId, conceptDescriptions, nonDefaultPreferredTerms, defaultLanguageCode);

            for (Concept concept : members.getItems()) {
                concept.setDescriptions(sortedDescriptions.get(concept.getCode()));
            }
        }

        return members;
    }

    /**
     * Sort descriptions.
     *
     * @param refsetId the refset id
     * @param conceptDescriptions the con desc map
     * @param nonDefaultPreferredTerms the non-default preferred terms
     * @param defaultLanguageCode the default language code
     * @return the map
     * @throws Exception the exception
     */
    /*-
     * Sort descriptions in the order defined below.
     * 
     * 1)   PT � Default Lang Code
     * 2)  FSN
     * 3)  All other PTs 
     *      a.  Order by language code
     *      b.  If no translation, will be null
     */
    private static Map<String, List<Map<String, String>>> sortDescriptions(final String refsetId,
        final HashMap<String, Set<Map<String, String>>> conceptDescriptions,
        final List<String> nonDefaultPreferredTerms, final String defaultLanguageCode)
        throws Exception {

        final Map<String, List<Map<String, String>>> sortedMemberDescriptions = new HashMap<>();

        // Actual code
        for (String conceptId : conceptDescriptions.keySet()) {

            // do this for each concept
            final List<Map<String, String>> sortedDescriptionList = new ArrayList<>();
            final Map<String, Map<String, String>> sortingMap = new HashMap<>();

            for (Map<String, String> descriptionMap : conceptDescriptions.get(conceptId)) {

                if (descriptionMap.get(DESCRIPTION_TYPE).equalsIgnoreCase("fsn")) {
                    // Always 0
                    if (sortingMap.containsKey(TYPE_FSN)) {
                        displayDuplicateWarning("A FSN in the default language", conceptId,
                                descriptionMap.get(DESCRIPTION_LANGUAGE), sortingMap.get(TYPE_FSN),
                                descriptionMap);
                        continue;
                    }

                    sortingMap.put(TYPE_FSN, descriptionMap);
                } else if (descriptionMap.get(DESCRIPTION_LANGUAGE).equals(defaultLanguageCode)) {
                    // Always 1
                    if (sortingMap.containsKey(TYPE_DEFAULT_PT)) {
                        displayDuplicateWarning("A PT in the default language", conceptId,
                                descriptionMap.get(DESCRIPTION_LANGUAGE),
                                sortingMap.get(TYPE_DEFAULT_PT), descriptionMap);
                        continue;
                    }

                    sortingMap.put(TYPE_DEFAULT_PT, descriptionMap);
                } else {
                    // Always 2 + the index in nonDefaultPreferredTerms
                    final int index =
                            nonDefaultPreferredTerms.indexOf(descriptionMap.get(LANGUAGE_ID));

                    if (sortingMap.containsKey(TYPE_OTHER_PT + index)) {
                        displayDuplicateWarning("A PT in the non-default language", conceptId,
                                descriptionMap.get(DESCRIPTION_LANGUAGE),
                                sortingMap.get(TYPE_OTHER_PT + index), descriptionMap);

                        continue;
                    }

                    sortingMap.put(TYPE_OTHER_PT + index, descriptionMap);
                }
            }

            sortedDescriptionList.add(sortingMap.get(TYPE_DEFAULT_PT));
            sortedDescriptionList.add(sortingMap.get(TYPE_FSN));

            for (int i = 0; i < nonDefaultPreferredTerms.size(); i++) {
                sortedDescriptionList.add(sortingMap.get(TYPE_OTHER_PT + i));
            }

            sortedMemberDescriptions.put(conceptId, sortedDescriptionList);
        }

        return sortedMemberDescriptions;
    }

    /**
     * Display duplicate warning.
     *
     * @param errorMsg the error msg
     * @param conId the con id
     * @param language the language
     * @param origMap the orig map
     * @param descMap the desc map
     */
    private static void displayDuplicateWarning(final String errorMsg, final String conId,
        final String language, final Map<String, String> origMap,
        final Map<String, String> descMap) {
        logger.warn(errorMsg + "(" + language + ") has already been identified for conceptId: "
                + conId);
        logger.warn("Original one identified. " + printDescription(origMap));
        logger.warn("New one encountered: " + printDescription(descMap));
    }

    /**
     * Prints the description.
     *
     * @param map the map
     * @return the string
     */
    private static String printDescription(final Map<String, String> map) {
        return "Type = " + map.get(DESCRIPTION_TYPE) + " for lang = "
                + map.get(DESCRIPTION_LANGUAGE) + " with term = " + map.get(DESCRIPTION_TERM);
    }
}
