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
import org.ihtsdo.refsetservice.model.SearchParameters;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service class to get refset member concept information from a terminology
 * service
 *
 */
public class RefsetMemberService {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(RefsetMemberService.class);

    /** The simple date format. */
    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");

    /** The refset to language map. */
    private static final Set<String> refsetToLanguages = new HashSet<>();

    /** The description term. */
    private static final String DESCRIPTION_TERM = "term";

    /** The description type. */
    private static final String DESCRIPTION_TYPE = "type";

    /** The description language. */
    private static final String DESCRIPTION_LANG = "lang";

    /** The fully specified name description type. */
    private static final String TYPE_FSN = "FSN";

    /** The preferred term description type. */
    private static final String TYPE_DEFAULT_PT = "PT";

    /** The other description type. */
    private static final String TYPE_OTHER_PT = "OTHER";
    
    static {
        // TODO: Remove once Edition updated
        refsetToLanguages.add("900000000000509007");
        refsetToLanguages.add("31000172101");
        refsetToLanguages.add("21000172104");
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
        refsetId = "721000172106"; 
        List<String> nonDefaultPreferredTerms = null;
        String defaultLanguageCode = null;
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

            // TODO: Integrate with Edition below and remove hard coding listed
            // here
            try (final TerminologyService service = new TerminologyService()) {
                // Refset r = service.get(refsetId, Refset.class);
                // Edition e = r.getEdition();
                // Hardcoding defaultLanguageCode & refsetToLanguageMap as
                // edition
                // is blank right now

                defaultLanguageCode = "en";
                nonDefaultPreferredTerms = refsetToLanguages.stream().collect(Collectors.toList());
                nonDefaultPreferredTerms.remove(defaultLanguageCode);
                Collections.sort(nonDefaultPreferredTerms);

            }

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

            JsonNode items = root.get("items");
            Iterator<JsonNode> iterator = items.iterator();

            while (iterator.hasNext()) {
                JsonNode item = iterator.next();
                Concept concept = new Concept();
                concept.setCode(item.get("referencedComponentId").asText());
                concept.setTerminology("SNOMEDCT");
                concept.setMemberStatus(item.get("active").asBoolean());
                concept.setMemberEffectiveTime(
                        SIMPLE_DATE_FORMAT.parse(item.get("releasedEffectiveTime").asText()));

                members.getItems().add(concept);
            }

            members = getConceptDescriptions(refsetId, members,
                    nonDefaultPreferredTerms, defaultLanguageCode, searchParameters);

            return members;

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
        final String defaultLanguageCode, final SearchParameters searchParameters) throws MalformedURLException, Exception {

        StringBuffer conceptIds = new StringBuffer();

        for (int i = 0; i < members.size(); i++) {

            Concept concept = (Concept) members.getItems().toArray()[i];
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

            JsonNode concepts = root.get("items");
            Iterator<JsonNode> conceptIterator = concepts.iterator();
            HashMap<String, Set<Map<String, String>>> conceptDescriptions = new HashMap<>();

            while (conceptIterator.hasNext()) {

                JsonNode concept = conceptIterator.next();
                String conceptId = concept.get("conceptId").asText();

                JsonNode descriptionNodes = concept.get("descriptions");
                Iterator<JsonNode> descriptionIterator = descriptionNodes.iterator();

                Set<Map<String, String>> descriptions = new HashSet<>();

                while (descriptionIterator.hasNext()) {

                    Map<String, String> descriptionMap = new HashMap<>();
                    JsonNode descriptionNode = descriptionIterator.next();

                    if (descriptionNode.get("active").asBoolean()) {

                        boolean isPreferred = false;
                        String typeName = null;

                        if (!"900000000000003001".equals(descriptionNode.get("typeId").asText())) {

                            JsonNode acceptabilityMap = descriptionNode.get("acceptabilityMap");

                            for (String langRefsetId : refsetToLanguages) {

                                if (acceptabilityMap.has(langRefsetId) && "PREFERRED"
                                        .equals(acceptabilityMap.get(langRefsetId).asText())) {

                                    isPreferred = true;
                                    typeName = "PT";
                                    break;
                                }
                            }
                        } else {
                            typeName = "FSN";
                        }

                        if (isPreferred || "900000000000003001"
                                .equals(descriptionNode.get("typeId").asText())) {

                            descriptionMap.put(DESCRIPTION_TERM,
                                    descriptionNode.get("term").asText());
                            descriptionMap.put(DESCRIPTION_TYPE, typeName);
                            descriptionMap.put(DESCRIPTION_LANG,
                                    descriptionNode.get("lang").asText());

                            descriptions.add(descriptionMap);
                        }
                    }
                }

                conceptDescriptions.put(conceptId, descriptions);
            }

            Map<String, List<Map<String, String>>> sortedDescriptions = sortDescriptions(refsetId,
                    conceptDescriptions, nonDefaultPreferredTerms, defaultLanguageCode);

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

        Map<String, List<Map<String, String>>> sortedMemberDescriptions = new HashMap<>();

        // Actual code
        for (String conceptId : conceptDescriptions.keySet()) {

            // do this for each concept
            List<Map<String, String>> sortedDescriptionList = new ArrayList<>();
            Map<String, Map<String, String>> sortingMap = new HashMap<>();
            int otherPtCount = 0;

            for (Map<String, String> descriptionMap : conceptDescriptions.get(conceptId)) {

                if (descriptionMap.get(DESCRIPTION_TYPE).equalsIgnoreCase("fsn")) {

                    // Always 0
                    if (sortingMap.containsKey(TYPE_FSN)) {

                        logger.debug(
                                "An FSN in the default language has already been identified for conceptId: "
                                        + conceptId);
                        logger.debug("Original one identified: "
                                + printDescription(sortingMap.get(TYPE_FSN)));
                        logger.debug("New one encountered: " + printDescription(descriptionMap));
                        continue;
                    }

                    sortingMap.put(TYPE_FSN, descriptionMap);

                } else if (descriptionMap.get(DESCRIPTION_LANG).equals(defaultLanguageCode)) {

                    // Always 1
                    if (sortingMap.containsKey(TYPE_DEFAULT_PT)) {

                        logger.debug(
                                "A PT in the default language has already been identified for conceptId: "
                                        + conceptId);
                        logger.debug("Original one identified. "
                                + printDescription(sortingMap.get(TYPE_DEFAULT_PT)));
                        logger.debug("New one encountered: " + printDescription(descriptionMap));
                        continue;
                    }

                    sortingMap.put(TYPE_DEFAULT_PT, descriptionMap);

                } else {

                    // Always 2 + the index in nonDefaultPreferredTerms
                    int index =
                            nonDefaultPreferredTerms.indexOf(descriptionMap.get(DESCRIPTION_LANG));

                    if (sortingMap.containsKey(TYPE_OTHER_PT + index)) {

                        logger.debug("A PT in the non-default language "
                                + descriptionMap.get(DESCRIPTION_LANG)
                                + " has already been identified for conceptId: " + conceptId);
                        logger.debug("Original one identified. "
                                + printDescription(sortingMap.get(TYPE_OTHER_PT + index)));
                        logger.debug("New one encountered: " + printDescription(descriptionMap));
                        continue;
                    }

                    otherPtCount++;
                    sortingMap.put(TYPE_OTHER_PT + index, descriptionMap);
                }
            }

            sortedDescriptionList.add(sortingMap.get(TYPE_DEFAULT_PT));
            sortedDescriptionList.add(sortingMap.get(TYPE_FSN));

            for (int i = 0; i < otherPtCount; i++) {
                sortedDescriptionList.add(sortingMap.get(TYPE_OTHER_PT + i));
            }

            sortedMemberDescriptions.put(conceptId, sortedDescriptionList);
        }

        return sortedMemberDescriptions;
    }

    /**
     * Prints the description.
     *
     * @param map the map
     * @return the string
     */
    private static String printDescription(final Map<String, String> map) {

        return "Type = " + map.get(DESCRIPTION_TYPE) + " for lang = " + map.get(DESCRIPTION_LANG)
                + " with term = " + map.get(DESCRIPTION_TERM);
    }
}
