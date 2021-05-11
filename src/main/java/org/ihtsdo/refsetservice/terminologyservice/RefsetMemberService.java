/**
 * 
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.ihtsdo.refsetservice.util.TaxonomyParameters;
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

    /** The fully specified name description type. */
    private static final String TYPE_FSN = "FSN";

    /** The preferred term description type. */
    private static final String TYPE_DEFAULT_PT = "PT";

    /** The other description type. */
    private static final String TYPE_OTHER_PT = "OTHER";

    /** The local directory to store exported refset files. */
    private static String EXPORT_FILE_DIR;

    private final static Map<String, Map<String, Concept>> membersCache = new HashMap<>();

    private static final int TAXONOMY_PAGING_LIMIT = 1000;

    static {

        EXPORT_FILE_DIR = PropertyUtility.getProperty("export.fileDir");

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
     * @param displayType Should results be a list or hierarchical taxonomy
     * @param taxonomyParameters the taxonomy parameters
     * @return the refset member concepts
     * @throws Exception the exception
     */
    public static ConceptResultList getRefsetMembers(String refsetId,
        final SearchParameters searchParameters, final String displayType,
        final TaxonomyParameters taxonomyParameters) throws Exception {

        final List<String> nonDefaultPreferredTerms = new ArrayList<>();
        ConceptResultList members = new ConceptResultList();

        try (final TerminologyService service = new TerminologyService()) {

            Refset refset = service.get(refsetId, Refset.class);
            final Edition edition = refset.getEdition();

            // get the list of languages the refset supports
            nonDefaultPreferredTerms
                    .addAll(refsetToLanguagesMap.keySet().stream().collect(Collectors.toList()));

            final String languageToRemove = (edition.getDefaultLanguageCode()) == null ? "en"
                    : edition.getDefaultLanguageCode();

            if (edition.getDefaultLanguageCode() != null) {

                refsetToLanguagesMap.entrySet().stream()
                        .filter(entry -> languageToRemove.equals(entry.getValue()))
                        .map(Map.Entry::getKey).forEach((val) -> {
                            nonDefaultPreferredTerms.remove(val);
                        });
            }

            // remove the default language and any languages that are not in the
            // edition's default list
            nonDefaultPreferredTerms.removeIf(languageRefset -> !edition.getDefaultLanguageRefsets()
                    .contains(languageRefset));

            Collections.sort(nonDefaultPreferredTerms);

            // build the common terminolgy server url params
            final String pagingParams =
                    "offset=" + (searchParameters.getOffset() * searchParameters.getLimit())
                            + "&limit=" + searchParameters.getLimit();
            String versionDate = "";

            // if (searchParameters.getSortAscending() != null) {
            //
            // }
            //
            // if (searchParameters.getSort() != null) {
            //
            // }

            // the next call depend if a list or taxonomy is being returned
            if (displayType.equals("list")) {
                final String url = createBrowserUrl(refset) + "members?referenceSet="
                        + refset.getRefsetId() + "&" + pagingParams;

                members = getRefsetMemberList(refset, members, nonDefaultPreferredTerms, url);

                // add the descriptions to the member concepts
                // For each concept populated, identify all descriptions
                members = getConceptDescriptions(refset, members, nonDefaultPreferredTerms,
                        createBrowserUrl(refset) + "concepts?" + pagingParams + "&");
            } else {
                // Should have 19 concepts if starting is SNOMED_ROOT
                // A) Identify descendants & ancestors
                // B) Populate all concepts within Depth wiht refset membership
                // info

                ConceptResultList children = getRefsetMemberTaxonomy(refset,
                        nonDefaultPreferredTerms, taxonomyParameters);

                // add the descriptions to the children concepts
                // For each concept populated, identify all descriptions
                children = getConceptDescriptions(refset, children, nonDefaultPreferredTerms,
                        createBrowserUrl(refset) + "concepts?" + pagingParams + "&");

                logger.info("Concept has " + children.size() + " children");
                for (Concept child : children.getItems()) {
                    ConceptResultList grandchildren =
                            getChildren(child.getCode(), refset.getEdition().getBranch());

                    child.setChildren(grandchildren.getItems());
                    
                    // TODO: Need to only populate hasChildren on the child... so update the getChildren() method to break after one found and return boolean
                    
                    /* - Not needed for 

                    ConceptResultList grandparents =
                            getParents(child.getCode(), refset.getEdition().getBranch());

                    child.setParents(grandparents.getItems());
                */

                }
            }
        }

        return members;
    }

    /**
     * Get the refset member concepts as a hierarchical taxonomy tree.
     *
     * @param refset the refset who's members are being retrieved
     * @param nonDefaultPreferredTerms the non-default preferred terms
     * @param taxonomyParameters the taxonomy parameters
     * @return the refset member concepts
     * @throws Exception the exception
     */
    public static ConceptResultList getRefsetMemberTaxonomy(final Refset refset,
        final List<String> nonDefaultPreferredTerms, final TaxonomyParameters taxonomyParameters)
        throws Exception {
        Map<String, Concept> allMembersMap = null;

        if (membersCache.containsKey(refset.getRefsetId())) {
            allMembersMap = membersCache.get(refset.getRefsetId());
        } else {
            logger.info("Starting grabbing memberList");
            // Get & store all members of refset (which will give all membership
            // details)
            allMembersMap = generateMemberIdMap(refset);

            membersCache.put(refset.getRefsetId(), allMembersMap);

            logger.info("Finishing grabbing memberList");
        }

        /*-
        TopDownRefset.populateRefsetMembersTopDown(taxonomyParameters.getStartingConceptId(),
                allMembersMap, populatedConcepts, taxonomyParameters.getDepth(), refset, false,
                true);
        
        Map<String, Concept> processedConcepts =
                BottomUpRefset.populateAncestorDescendantMembership(allMembersMap, refset);
        
        
        
        Concept rootConcept =
                BottomUpRefset.getConcept(taxonomyParameters.getStartingConceptId(), refset);
        
        BottomUpRefset.buildTaxonomy(rootConcept, processedConcepts, taxonomyParameters.getDepth(),
                refset);
        */
        try (final TerminologyService service = new TerminologyService()) {

            ConceptResultList populatedConcepts = getChildren(
                    taxonomyParameters.getStartingConceptId(), refset.getEdition().getBranch());

            for (Concept child : populatedConcepts.getItems()) {
                if (allMembersMap.containsKey(child.getId())) {
                    Concept member = allMembersMap.get(child.getId());
                    child.setMemberOfRefset(member.isMemberOfRefset());
                    child.setMemberStatus(member.isMemberStatus());
                    child.setMemberEffectiveTime(member.getMemberEffectiveTime());
                }
            }

            return populatedConcepts;
        }
    }

    /**
     * Get the refset member concepts as a list.
     *
     * @param refset the refset who's members are being retrieved
     * @param members the members
     * @param nonDefaultPreferredTerms the non-default preferred terms
     * @param url the terminology server URL
     * @return the refset member concepts
     * @throws Exception the exception
     */
    public static ConceptResultList getRefsetMemberList(final Refset refset,
        final ConceptResultList members, final List<String> nonDefaultPreferredTerms,
        final String url) throws Exception {

        logger.debug("Get Member List URL: " + url);
        String branchVersion = refset.getEdition().getBranch();
        return populateConceptsFromSnowstorm(url);
    }

    /**
     * Gets the children.
     *
     * @param startingConceptId the starting concept id
     * @param string the refset
     * @return the children
     * @throws Exception the exception
     */
    protected static ConceptResultList getChildren(String startingConceptId, String branch)
        throws Exception {
        final String baseUrl = SnowstormConnection.BASE_URL + "browser/" + branch + "/";

        final String url = baseUrl + "concepts/" + startingConceptId + "/children";
        logger.debug("Get Children URL: " + url);
        return populateConceptsFromSnowstorm(url);
    }

    /**
     * Gets the children.
     *
     * @param startingConceptId the starting concept id
     * @param string the refset
     * @return the children
     * @throws Exception the exception
     */
    protected static ConceptResultList getParents(String startingConceptId, String branch)
        throws Exception {
        final String baseUrl = SnowstormConnection.BASE_URL + "browser/" + branch + "/";

        final String url = baseUrl + "concepts/" + startingConceptId + "/parents";
        logger.debug("Get Parents URL: " + url);
        return populateConceptsFromSnowstorm(url);
    }

    /**
     * Generate member id map.
     *
     * @param refset the refset
     * @param url the url
     * @return the map
     * @throws Exception the exception
     */
    private static Map<String, Concept> generateMemberIdMap(Refset refset) throws Exception {
        Map<String, Concept> memberIdMap = new HashMap<>();

        while (memberIdMap.size() == 0 || (memberIdMap.size() % TAXONOMY_PAGING_LIMIT == 0)) {
            // https://snowstorm.ihtsdotools.org/snowstorm/snomed-ct/MAIN%2FSNOMEDCT-BE/members?referenceSet=551000172106&offset=200&limit=100
            final String url =
                    createBrowserUrl(refset) + "members?referenceSet=" + refset.getRefsetId()
                            + "&limit=" + TAXONOMY_PAGING_LIMIT + "&offset=" + memberIdMap.size();
            logger.debug("Get Member List URL: " + url);

            try (final Response response = SnowstormConnection.getResponse(url)) {
                final String resultString = response.readEntity(String.class);

                final ObjectMapper mapper = new ObjectMapper();
                final JsonNode root = mapper.readTree(resultString.toString());
                final JsonNode node = root.get("items");
                Iterator<JsonNode> iterator = node.iterator();

                while (iterator.hasNext()) {
                    JsonNode item = iterator.next();

                    // Only process Active Members
                    if (item.get("active").asBoolean()) {
                        final Concept member = populateConcept(item);

                        // Populate Member data
                        member.setMemberOfRefset(true);
                        member.setMemberStatus(true);
                        if (item.has("releasedEffectiveTime")) {
                            member.setMemberEffectiveTime(SIMPLE_DATE_FORMAT
                                    .parse(item.get("releasedEffectiveTime").asText()));
                        }

                        memberIdMap.put(member.getCode(), member);
                    }
                }
            }
        }

        return memberIdMap;
    }

    /**
     * Populate concepts from Snowstorm.
     *
     * @param url the url
     * @param innerTags the inner tags
     * @return the concept result list
     * @throws Exception the exception
     */
    private static ConceptResultList populateConceptsFromSnowstorm(String url) throws Exception {
        final ConceptResultList retList = new ConceptResultList();

        try (final Response response = SnowstormConnection.getResponse(url)) {
            final String resultString = response.readEntity(String.class);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());
            Iterator<JsonNode> iterator = root.iterator();

            while (iterator.hasNext()) {
                JsonNode item = iterator.next();
                final Concept concept = populateConcept(item);
                retList.getItems().add(concept);
                retList.setTotal(retList.getTotal() + 1);

            }
        }

        return retList;
    }

    /**
     * Populate concept.
     *
     * @param item the item
     * @return the concept
     * @throws ParseException the parse exception
     */
    private static Concept populateConcept(JsonNode item) throws ParseException {
        final Concept concept = new Concept();

        String conId = (item.has("referencedComponentId"))
                ? item.get("referencedComponentId").asText() : item.get("conceptId").asText();
        concept.setCode(conId);
        concept.setTerminology("SNOMEDCT");
        concept.setHistoryVisible(true);
        concept.setFeedbackVisible(true);

        // TODO: Add Version info

        return concept;
    }

    /**
     * Gets the concept descriptions.
     *
     * @param refset the refset who's members are being retrieved
     * @param conceptsToProcess the concepts to add descriptions to
     * @param nonDefaultPreferredTerms the non-default preferred terms
     * @param url the terminology server URL
     * @return the concept descriptions
     * @throws MalformedURLException the malformed URL exception
     * @throws Exception the exception
     */
    public static ConceptResultList getConceptDescriptions(final Refset refset,
        final ConceptResultList conceptsToProcess, final List<String> nonDefaultPreferredTerms,
        final String url) throws MalformedURLException, Exception {

        final Edition edition = refset.getEdition();
        final StringBuffer conceptIds = new StringBuffer();

        for (int i = 0; i < conceptsToProcess.size(); i++) {

            final Concept concept = (Concept) conceptsToProcess.getItems().toArray()[i];
            conceptIds.append(concept.getCode());

            if (i + 1 < conceptsToProcess.size()) {
                conceptIds.append(",");
            }
        }

        logger.debug("Get Member Descriptions URL: " + url + "conceptIds=" + conceptIds);

        try (final Response response =
                SnowstormConnection.getResponse(url + "conceptIds=" + conceptIds)) {

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

                        for (String langRefsetId : edition.getDefaultLanguageRefsets()) {
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

                            descriptionMap.put(DESCRIPTION_TERM,
                                    descriptionNode.get("term").asText());
                            descriptionMap.put(DESCRIPTION_TYPE, typeName);
                            descriptionMap.put(DESCRIPTION_ID,
                                    descriptionNode.get("descriptionId").asText());
                            descriptionMap.put(LANGUAGE_ID, languageId + typeName);
                            descriptionMap.put(LANGUAGE_NAME,
                                    descriptionNode.get("lang").asText().toUpperCase() + " ("
                                            + typeName + ")");
                            descriptionMap.put(DESCRIPTION_LANGUAGE,
                                    descriptionNode.get("lang").asText());

                            descriptions.add(descriptionMap);
                        }
                    }
                }
                conceptDescriptions.put(conceptId, descriptions);
            }

            final Map<String, List<Map<String, String>>> sortedDescriptions =
                    sortDescriptions(conceptDescriptions, nonDefaultPreferredTerms,
                            edition.getDefaultLanguageCode());

            for (Concept concept : conceptsToProcess.getItems()) {
                concept.setDescriptions(sortedDescriptions.get(concept.getCode()));

                List<Map<String, String>> descriptions = sortedDescriptions.get(concept.getCode());
                concept.setName(descriptions.get(0).get(DESCRIPTION_TERM));
            }

        } catch (Exception ex) {

            logger.error("Could not retrieve descriptions" + ex.getMessage());
            ex.printStackTrace();
        }

        return conceptsToProcess;
    }

    /**
     * Creates the base url.
     *
     * @param refset the refset
     * @return the string
     */
    private static String createBrowserUrl(Refset refset) {
        return createUrl(refset, true);
    }

    /**
     * Creates the base url.
     *
     * @param refset the refset
     * @param addBrowser the add browser
     * @return the string
     */
    private static String createUrl(Refset refset, boolean addBrowser) {
        String versionDate = "";

        if (refset.getVersionDate() != null) {

            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
            // versionDate = "/" +
            // simpleDateFormat.format(refset.getVersionDate());
        }

        if (addBrowser) {
            return SnowstormConnection.BASE_URL + "browser/" + refset.getEdition().getBranch()
                    + versionDate + "/";
        } else {
            return SnowstormConnection.BASE_URL + refset.getEdition().getBranch() + versionDate
                    + "/";
        }
    }

    /**
     * Sort descriptions.
     *
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
    private static Map<String, List<Map<String, String>>> sortDescriptions(
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

            if (sortingMap.get(TYPE_DEFAULT_PT) != null) {
                sortedDescriptionList.add(sortingMap.get(TYPE_DEFAULT_PT));
            }

            if (sortingMap.get(TYPE_FSN) != null) {
                sortedDescriptionList.add(sortingMap.get(TYPE_FSN));
            }

            for (int i = 0; i < nonDefaultPreferredTerms.size(); i++) {

                if (sortingMap.get(TYPE_OTHER_PT + i) != null) {
                    sortedDescriptionList.add(sortingMap.get(TYPE_OTHER_PT + i));
                }

            }

            sortedMemberDescriptions.put(conceptId, sortedDescriptionList);
        }

        return sortedMemberDescriptions;
    }

    /**
     * Display duplicate warning.
     *
     * @param errorMessage the error msg
     * @param conceptId the con id
     * @param language the language
     * @param firstFoundMap the orig map
     * @param descriptionMap the desc map
     */
    private static void displayDuplicateWarning(final String errorMessage, final String conceptId,
        final String language, final Map<String, String> firstFoundMap,
        final Map<String, String> descriptionMap) {

        logger.warn(errorMessage + "(" + language + ") has already been identified for conceptId: "
                + conceptId);
        logger.warn("Original one identified. " + printDescription(firstFoundMap));
        logger.warn("New one encountered: " + printDescription(descriptionMap));
    }

    /**
     * Prints the description.
     *
     * @param description the map
     * @return the string
     */
    private static String printDescription(final Map<String, String> description) {
        return "Type = " + description.get(DESCRIPTION_TYPE) + " for lang = "
                + description.get(DESCRIPTION_LANGUAGE) + " with term = "
                + description.get(DESCRIPTION_TERM);
    }

    /**
     * Gets the refset to languages map.
     *
     * @return the refsetToLanguagesMap
     */
    public static Map<String, String> getRefsetToLanguagesMap() {
        return refsetToLanguagesMap;
    }

    /**
     * Get the refset member concepts in RF2 format.
     *
     * @param refsetId the refset ID
     * @param type the type
     * @param fileNameDate the file name date
     * @param startEffectiveTime the start effective time
     * @param transientEffectiveTime the transient effective time
     * @param branchPath the branch path
     * @return the refset member concepts
     * @throws Exception the exception
     */
    public static String exportRefsetRf2(final String refsetId, final String type,
        final String fileNameDate, final String startEffectiveTime,
        final String transientEffectiveTime, final String branchPath) throws Exception {

        String url = SnowstormConnection.BASE_URL + "exports";

        String entity = "{\"refsetIds\": [\"" + refsetId + "\"],  \"branchPath\": \"" + branchPath
                + "\", \"conceptsAndRelationshipsOnly\": \"false\", \"filenameEffectiveDate\": \""
                + fileNameDate + "\", \"legacyZipNaming\": \"false\", \"type\": \"" + type
                + "\", \"unpromotedChangesOnly\": \"false\""
                + (startEffectiveTime == null ? ""
                        : ",  \"startEffectiveTime\": \"" + startEffectiveTime + "\"")
                + (transientEffectiveTime == null ? ""
                        : ",  \"transientEffectiveTime\": \"" + transientEffectiveTime + "\"")
                + "}";

        logger.info("Snowstorm URL: " + url + entity);

        try (Response response = SnowstormConnection.postResponse(url, entity)) {

            logger.info("Response location " + response.getLocation());
            return response.getLocation().toString();

        } catch (Exception ex) {

            logger.error("Could not export refset" + ex.getMessage());
            ex.printStackTrace();
            return null;
        }

    }

    /**
     * Get the refset member basic information.
     *
     * @param refsetId the refset ID
     * @param offset the 0 based page number to get
     * @param limit the number of results per page
     * @param branchPath the branch and version of the refset
     * @return the raw resultString
     * @throws Exception the exception
     */
    private static String getMemberSctids(final String refsetId, final int offset, final int limit,
        final String branchPath) throws Exception {

        final String pagingParams = "offset=" + (offset * limit) + "&limit=" + limit;

        String url = SnowstormConnection.BASE_URL + "browser/" + branchPath
                + "/members?referenceSet=" + refsetId + "&" + pagingParams;

        logger.info("Snowstorm URL: " + url);

        try (Response response = SnowstormConnection.getResponse(url)) {

            final String resultString = response.readEntity(String.class);
            return resultString;

        } catch (Exception ex) {

            logger.error("Could not retrieve refset members" + ex.getMessage());
            ex.printStackTrace();
            return null;
        }

    }

    /**
     * Export the refset member concept IDs in a zipped CSV format.
     *
     * @param refsetId the refset ID
     * @param branchPath the branch path
     * @return the URL of the file containing the member list
     * @throws Exception the exception
     */
    public static String exportRefsetSctidList(final String refsetId) throws Exception {

        int offset = 0;
        int limit = 10000;
        String branchPath = "";
        boolean morePages = true;
        StringBuilder fileLines = new StringBuilder();
        String outputPath = EXPORT_FILE_DIR + "/";
        String versionDate = "";
        String pathDate = "";
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");

        logger.info("    output path = " + outputPath);

        try (final TerminologyService service = new TerminologyService();
                final FileOutputStream fileOutputStream = new FileOutputStream(outputPath);
                final OutputStreamWriter outputStreamWriter =
                        new OutputStreamWriter(fileOutputStream, "UTF-8");
                final PrintWriter printWriter = new PrintWriter(outputStreamWriter);) {

            final Refset refset = service.get(refsetId, Refset.class);

            if (refset.getVersionDate() != null) {

                versionDate = simpleDateFormat.format(refset.getVersionDate());
                // pathDate = "/" + versionDate;
            } else {
                versionDate = simpleDateFormat.format(new Date());
            }

            outputPath += "refset_" + refset.getRefsetId() + "_" + versionDate + "_member_ids.txt";
            branchPath = refset.getEdition().getBranch() + pathDate;

            while (morePages) {

                final String resultString =
                        getMemberSctids(refset.getRefsetId(), offset, limit, branchPath);

                final ObjectMapper mapper = new ObjectMapper();
                final JsonNode root = mapper.readTree(resultString.toString());
                int totalPages = (root.get("totalPages")).asInt();
                offset = (root.get("number")).asInt();
                final JsonNode items = root.get("items");
                final Iterator<JsonNode> iterator = items.iterator();

                if (offset == totalPages - 1) {
                    morePages = false;
                }

                while (iterator.hasNext()) {

                    final JsonNode item = iterator.next();
                    final String conceptId = (item.get("referencedComponentId").asText());
                    fileLines.append(conceptId + "\n");
                }
            }

            printWriter.print(fileLines);

            return outputPath;

        } catch (Exception ex) {

            logger.error("Could not export refset" + ex.getMessage());
            ex.printStackTrace();
            return null;
        }

    }
}
