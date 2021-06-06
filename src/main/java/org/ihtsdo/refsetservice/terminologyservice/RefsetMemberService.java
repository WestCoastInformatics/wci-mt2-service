/**
 * 
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ConceptLookupParameters;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.ihtsdo.refsetservice.util.FileUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.ihtsdo.refsetservice.util.StringUtility;
import org.ihtsdo.refsetservice.util.TaxonomyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.amazonaws.services.s3.AmazonS3;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service class to get refset member concept information from a terminology
 * service.
 */
/**
 * @author jesseefron
 *
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

    /** The description language code. */
    private static final String LANGUAGE_CODE = "languageCode";

    /** The description language code and type combined. */
    private static final String LANGUAGE_ID = "languageId";

    /** The description language. */
    private static final String LANGUAGE_NAME = "languageName";

    /** The local directory to store exported refset files. */
    private static String EXPORT_FILE_DIR;

    /** The local server url to download exported refset files. */
    private static String EXPORT_DOWNLOAD_URL = "/export/download/";

    /** A cache of the all the member concepts for each refset. */
    private final static Map<String, Map<String, Concept>> membersCache = new HashMap<>();

    /** A cache of the members returned for a specific URL. */
    private final static Map<String, ConceptResultList> memberListCallCache = new HashMap<>();

    /** A cache of the children for each tree node. */
    private final static Map<String, List<Concept>> treeCache = new HashMap<>();

    /**
     * A cache that lists each tree node concept whose children have been
     * checked for refset members.
     */
    private final static Map<String, Set<String>> refsetTreeNodeCache = new HashMap<>();

    /** The Constant CONCEPT_DESCRIPTIONS_PER_CALL. */
    private static final int CONCEPT_DESCRIPTIONS_PER_CALL = 500;

    /** The Constant INFERRED_RELATIONSHIP. */
    private static final String INFERRED_RELATIONSHIP = "INFERRED_RELATIONSHIP";

    /** The Constant IS_A_TYPE_ID. */
    private static final String IS_A_TYPE_ID = "116680003";

    private static final String TOP_LEVEL_AWS_FOLDER = "rt2/";

    private static final int REFEST_RF2_CONCEPTID_COLUMN = 5;

    static {

        EXPORT_FILE_DIR = PropertyUtility.getProperty("export.fileDir") + "/";

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
     * @param refsetInternalId the internal refset ID
     * @param searchParameters the search parameters
     * @param displayType Should results be a list or hierarchical taxonomy
     * @param taxonomyParameters the taxonomy parameters
     * @return the refset member concepts
     * @throws Exception the exception
     */
    public static ConceptResultList getRefsetMembers(final String refsetInternalId,
        final SearchParameters searchParameters, final String displayType,
        final TaxonomyParameters taxonomyParameters) throws Exception {

        ConceptResultList concepts = new ConceptResultList();

        try (final TerminologyService service = new TerminologyService()) {
            Refset refset = service.get(refsetInternalId, Refset.class);

            final List<String> nonDefaultPreferredTerms =
                    identifyNonDefaultPreferredTerms(refset.getEdition());

            // if (searchParameters.getSortAscending() != null) {
            //
            // }
            //
            // if (searchParameters.getSort() != null) {
            //
            // }

            // the next call depend if a list or taxonomy is being returned
            if (displayType.equals("list")) {

                final String pagingParams =
                        "offset=" + (searchParameters.getOffset() * searchParameters.getLimit())
                                + "&limit=" + searchParameters.getLimit();

                final String url = SnowstormConnection.BASE_URL + getBranchPath(refset)
                        + "/members?referenceSet=" + refset.getRefsetId() + "&" + pagingParams;

                concepts = getMemberList(refset, nonDefaultPreferredTerms, url, searchParameters);
                logger.info("Refset has " + concepts.size() + " members");

            } else {

                concepts = getMemberTaxonomy(refset, nonDefaultPreferredTerms, taxonomyParameters);

                logger.info("Concept has " + concepts.size() + " children");
            }
        }

        return concepts;
    }

    /**
     * Identify non default preferred terms.
     *
     * @param edition the edition
     * @return the list
     */
    private static List<String> identifyNonDefaultPreferredTerms(Edition edition) {
        final List<String> nonDefaultPreferredTerms = new ArrayList<>();
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
        nonDefaultPreferredTerms.removeIf(
                languageRefset -> !edition.getDefaultLanguageRefsets().contains(languageRefset));

        // make sure every refset includes English as a fall back language
        if (edition.getDefaultLanguageCode() != null
                && !edition.getDefaultLanguageCode().equals("en")
                && !nonDefaultPreferredTerms.contains("en")) {
            nonDefaultPreferredTerms.add("en");
        }

        Collections.sort(nonDefaultPreferredTerms);

        return nonDefaultPreferredTerms;
    }

    /**
     * Gets the children.
     *
     * @param conceptId the starting concept id
     * @param branchPath the branch
     * @return the children
     * @throws Exception the exception
     */
    protected static ConceptResultList getChildren(String conceptId, String branchPath)
        throws Exception {
        final String url = SnowstormConnection.BASE_URL + "browser/" + branchPath + "/"
                + "concepts/" + conceptId + "/children?includeDescendantCount=true";

        logger.debug("Get Children URL: " + url);
        try (final Response response = SnowstormConnection.getResponse(url)) {
            final String resultString = response.readEntity(String.class);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());
            Iterator<JsonNode> iterator = root.iterator();

            return populateSnowstormConcepts(iterator);
        } catch (Exception ex) {
            throw new Exception("Could not get refset children for concept " + conceptId
                    + " from snowstorm: " + ex.getMessage(), ex);
        }
    }

    /**
     * Populate concepts from Snowstorm.
     *
     * @param iterator the iterator
     * @param branch the branch
     * @return the concept result list
     * @throws Exception the exception
     */
    private static ConceptResultList populateSnowstormConcepts(Iterator<JsonNode> iterator)
        throws Exception {

        final ConceptResultList retList = new ConceptResultList();
        final Map<String, Concept> conceptIdMap = new HashMap<>();

        while (iterator.hasNext()) {
            JsonNode conceptNode = iterator.next();
            final Concept concept = populateConcept(conceptNode);

            retList.getItems().add(concept);
            retList.setTotal(retList.getTotal() + 1);

            conceptIdMap.put(concept.getCode(), concept);
        }

        // populateVersionInfo(conceptIdMap, branch);

        return retList;
    }

    /**
     * Populate concept.
     *
     * @param conceptNode the item
     * @return the concept
     * @throws Exception the exception
     */
    private static Concept populateConcept(JsonNode conceptNode) throws Exception {
        final Concept concept = new Concept();
        String conceptId = null;
        String name = null;
        boolean memberStatus = false;
        boolean defined = false;

        if (conceptNode.has("referencedComponentId")) {

            conceptId = conceptNode.get("referencedComponent").get("conceptId").asText();
            memberStatus = conceptNode.get("active").asBoolean();
            concept.setMemberEffectiveTime(
                    SIMPLE_DATE_FORMAT.parse(conceptNode.get("releasedEffectiveTime").asText()));

            if (conceptNode.get("referencedComponent").get("pt") != null) {
                name = conceptNode.get("referencedComponent").get("pt").get("term").asText();
            } else {
                name = conceptNode.get("referencedComponent").get("term").asText();
            }
        } else if (conceptNode.has("conceptId")) {

            conceptId = conceptNode.get("conceptId").asText();
            concept.setHasChildren(conceptNode.get("descendantCount").asInt() > 0);

            if (!conceptNode.get("definitionStatus").asText().equals("PRIMITIVE")) {
                defined = true;
            }

            if (conceptNode.get("pt") != null) {
                name = conceptNode.get("pt").get("term").asText();
            } else {
                name = conceptNode.get("term").asText();
            }
        } else {
            throw new Exception("Unable to process the conceptNode: " + conceptNode);
        }

        concept.setCode(conceptId);
        concept.setName(name);
        concept.setTerminology("SNOMEDCT");
        concept.setHistoryVisible(true);
        concept.setFeedbackVisible(true);
        concept.setMemberStatus(memberStatus);
        concept.setDefined(defined);

        return concept;
    }

    /**
     * Sort descriptions.
     *
     * @param conceptId the concept id
     * @param descriptions the descriptions
     * @param refset the refset
     * @param nonDefaultPreferredTerms the non-default preferred terms
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
    private static List<Map<String, String>> sortConceptDescriptions(String conceptId,
        final Set<Map<String, String>> descriptions, Refset refset,
        List<String> nonDefaultPreferredTerms) throws Exception {

        // do this for each concept
        final List<Map<String, String>> sortedDescriptionList = new ArrayList<>();
        final Map<String, Map<String, String>> sortingMap = new HashMap<>();

        // Actual code
        for (Map<String, String> descriptionMap : descriptions) {

            final String languageId = descriptionMap.get(LANGUAGE_ID);

            // Handle the default language
            if (descriptionMap.get(DESCRIPTION_LANGUAGE)
                    .equals(refset.getEdition().getDefaultLanguageCode())) {

                if (descriptionMap.get(DESCRIPTION_TYPE).equalsIgnoreCase("fsn")) {

                    if (sortingMap.containsKey(languageId)) {

                        displayDuplicateWarning("A FSN in the default language", conceptId,
                                descriptionMap.get(DESCRIPTION_LANGUAGE),
                                sortingMap.get(languageId), descriptionMap);
                        continue;
                    }

                    sortingMap.put(languageId, descriptionMap);

                } else {

                    if (sortingMap.containsKey(languageId)) {

                        displayDuplicateWarning("A PT in the default language", conceptId,
                                descriptionMap.get(DESCRIPTION_LANGUAGE),
                                sortingMap.get(languageId), descriptionMap);
                        continue;
                    }

                    sortingMap.put(languageId, descriptionMap);
                }
            }

            // Handle the non-default languages
            else {

                if (descriptionMap.get(DESCRIPTION_TYPE).equalsIgnoreCase("fsn")) {

                    if (sortingMap.containsKey(languageId)) {

                        displayDuplicateWarning("A FSN in a non-default language", conceptId,
                                descriptionMap.get(DESCRIPTION_LANGUAGE),
                                sortingMap.get(languageId), descriptionMap);
                        continue;
                    }

                    sortingMap.put(languageId, descriptionMap);

                } else {

                    if (sortingMap.containsKey(languageId)) {

                        displayDuplicateWarning("A PT in a non-default language", conceptId,
                                descriptionMap.get(DESCRIPTION_LANGUAGE),
                                sortingMap.get(languageId), descriptionMap);
                        continue;
                    }

                    sortingMap.put(languageId, descriptionMap);
                }
            }
        }

        final List<Map<String, String>> languageRefsets =
                refset.getEdition().getFullyQualifiedLanguageRefsets();

        for (final Map<String, String> languageRefset : languageRefsets) {

            final String languageId = languageRefset.get("qualifiedLanguageRefset");

            if (sortingMap.get(languageId) != null) {
                sortedDescriptionList.add(sortingMap.get(languageId));
            } else {
                sortedDescriptionList.add(null);
            }
        }

        return sortedDescriptionList;
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
     * Get the details of a member concept.
     *
     * @param conceptId the concept ID
     * @param refsetInternalId the refset internal id
     * @return the member concept details
     * @throws Exception the exception
     */
    public static Concept getMemberDetails(final String conceptId, final String refsetInternalId)
        throws Exception {
        Concept concept = null;

        try (final TerminologyService service = new TerminologyService()) {

            final Refset refset = service.get(refsetInternalId, Refset.class);
            logger.debug("Get Concept Details refset: " + refset);

            String url = SnowstormConnection.BASE_URL + "browser/" + getBranchPath(refset)
                    + "/concepts/" + conceptId + "?descendantCountForm=inferred";

            logger.debug("Get Member Details URL: " + url);

            try (final Response response = SnowstormConnection.getResponse(url)) {

                final String resultString = response.readEntity(String.class);
                final ObjectMapper mapper = new ObjectMapper();
                final JsonNode root = mapper.readTree(resultString.toString());

                concept = populateConcept(root);
                concept.setVersion(root.get("effectiveTime").asText());

                // Populate descriptions
                concept.setDescriptions(populateAllDescriptions(concept.getCode(),
                        root.get("descriptions"), refset));

                // Populate parents
                concept.setParents(populateParents(root.get("relationships")));

                // Populate children
                if (root.get("descendantCount").asInt() > 0) {
                    concept.setHasChildren(true);
                    ConceptResultList children =
                            getChildren(concept.getCode(), getBranchPath(refset));

                    for (Concept child : children.getItems()) {
                        concept.getChildren().add(child);
                    }
                }
            } catch (Exception ex) {
                throw new Exception("Could not get concept " + conceptId + " from snowstorm: "
                        + ex.getMessage(), ex);
            }
        } catch (Exception ex) {
            throw new Exception(
                    "Could not find refset in database for internalId " + refsetInternalId, ex);
        }
        return concept;
    }

    /**
     * Populate all descriptions.
     *
     * @param conceptId the concept id
     * @param descriptions the descriptions
     * @param branchPath the refset
     * @return the list
     * @throws Exception the exception
     */
    private static List<Map<String, String>> populateAllDescriptions(String conceptId,
        JsonNode descriptions, Refset refset) throws Exception {
        final Iterator<JsonNode> iterator = descriptions.iterator();

        Set<JsonNode> descriptionNodes = new HashSet<>();

        while (iterator.hasNext()) {
            JsonNode description = iterator.next();
            descriptionNodes.add(description);
        }

        List<String> emptyArrayList = new ArrayList<String>();
        Set<Map<String, String>> populatedDescriptions = processDescriptionNodes(descriptionNodes,
                refset.getEdition().getDefaultLanguageRefsets(), emptyArrayList);

        return sortConceptDescriptions(conceptId, populatedDescriptions, refset, emptyArrayList);
    }

    /**
     * Populate parents.
     *
     * @param relationships the relationships
     * @return the list
     */
    private static List<Concept> populateParents(JsonNode relationships) {
        final List<Concept> parents = new ArrayList<>();
        final Iterator<JsonNode> iterator = relationships.iterator();

        while (iterator.hasNext()) {

            JsonNode relationship = iterator.next();

            // Parent relationship
            if (relationship.get("active").asBoolean()
                    && INFERRED_RELATIONSHIP.equals(relationship.get("characteristicType").asText())
                    && IS_A_TYPE_ID.equals(relationship.get("typeId").asText())) {

                Concept parent = new Concept();
                boolean defined = false;
                parent.setCode(relationship.get("destinationId").asText());
                parent.setName(relationship.get("target").get("pt").get("term").asText());

                if (!relationship.get("target").get("definitionStatus").asText()
                        .equals("PRIMITIVE")) {
                    defined = true;
                }

                parent.setDefined(defined);
                parents.add(parent);
            }
        }

        return parents;
    }

    /**
     * Get a list of refsets containing members matching the search.
     *
     * @param searchParameters the search parameters
     * @return a list of refsets containing members matching the search
     * @throws Exception the exception
     */
    public static String searchDirectoryMembers(final SearchParameters searchParameters)
        throws Exception {

        String refsetQuery = "";
        final String query = searchParameters.getQuery(); // refsetId: "12345"
                                                          // AND privateRefset:
                                                          // false AND term:
                                                          // "blood" AND name:
                                                          // "work"
        final List<String> directoryColumns = Arrays.asList("id", "refsetId", "name", "editionName",
                "organizationName", "versionStatus", "versionDate", "modified", "privateRefset");
        String snowstormQuery = "";
        String[] queryParts = query.split(" AND ");

        for (final String queryPart : queryParts) {

            String[] keyValue = queryPart.split(":");

            if (keyValue.length > 1 && directoryColumns.contains(keyValue[0])) {
                continue;
            } else {

                snowstormQuery += queryPart + " AND ";
            }
        }

        snowstormQuery = StringUtils.removeEnd(snowstormQuery, " AND ");

        String url = SnowstormConnection.BASE_URL
                + "browser/MAIN/descriptions?active=true&conceptActive=true&groupByConcept=true&searchMode=STANDARD&offset=0&limit=1&term="
                + StringUtility.encodeValue(QueryParserBase.escape(snowstormQuery));

        logger.debug("Snowstorm URL: " + url);

        try (Response response = SnowstormConnection.getResponse(url)) {

            final String resultString = response.readEntity(String.class);
            logger.debug("searchDirectoryMembers resultString: " + resultString);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

            if (root.get("buckets") != null) {

                Iterator<String> membershipIterator =
                        root.get("buckets").get("membership").fieldNames();

                while (membershipIterator.hasNext()) {

                    String refsetId = membershipIterator.next();
                    refsetQuery += refsetId + " OR ";
                }

                if (!refsetQuery.equals("")) {
                    refsetQuery = "refsetId:(" + StringUtils.removeEnd(refsetQuery, " OR ") + ")";
                }

            }

        } catch (Exception ex) {
            throw new Exception(
                    "Could not retrieve refset members from snowstorm: " + ex.getMessage(), ex);
        }

        logger.debug("searchDirectoryMembers refsetQuery: " + refsetQuery);

        return refsetQuery;
    }

    /**
     * Get the refset member concepts in RF2 format.
     *
     * @param refsetInternalId the internal refset ID
     * @param type the type
     * @param fileNameDate the file name date
     * @param startEffectiveTime the start effective time
     * @param transientEffectiveTime the transient effective time
     * @param exportMetadata should refset metadata be included in the export
     * @return the refset member concepts
     * @throws Exception the exception
     */
    public static String exportRefsetRf2(final String refsetInternalId, final String type,
        final String fileNameDate, final String startEffectiveTime,
        final String transientEffectiveTime, final boolean exportMetadata, final boolean withNames)
        throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final Refset refset = service.get(refsetInternalId, Refset.class);

            String zippedFileUrl = "";
            String zipFileName = "";
            String awsPath = "";
            String tmp = null;
            if (("snapshot".equals(type.toLowerCase()) && tmp != null)
                    // if (("snapshot".equals(type.toLowerCase()) &&
                    // transientEffectiveTime != null)
                    || ("delta".equals(type.toLowerCase()) && transientEffectiveTime == null)) {
                throw new Exception("Have a type/transientEffectiveTime mismatch with type: " + type
                        + " and transientEffectiveTime: " + transientEffectiveTime);
            }

            if ("snapshot".equals(type.toLowerCase())) {
                zipFileName = "refset_" + refset.getRefsetId() + "_" + getRefsetAsOfDate(refset)
                        + "_" + type + ".zip";
                awsPath = TOP_LEVEL_AWS_FOLDER + refset.getRefsetId() + "/"
                        + getRefsetAsOfDate(refset) + "/" + type;

            } else {
                zipFileName = "refset_" + refset.getRefsetId() + "_" + getRefsetAsOfDate(refset)
                        + "_" + type + "_" + transientEffectiveTime + ".zip";
                awsPath = TOP_LEVEL_AWS_FOLDER + refset.getRefsetId() + "/"
                        + getRefsetAsOfDate(refset) + "/" + type + "/" + transientEffectiveTime;
            }

            if (withNames) {
                awsPath = awsPath + "-withNames"; // TODO: Add Language here too
            }

            // Check S3 cache if file exists. If exists, return path to S3
            // If doesn't, generate, upload to S3, then return path to S3
            // AmazonS3 s3Client = S3Connection.connectToAmazonS3();

            // String s3ZippedFileUrl = null; //S3Connection.getS3Path(s3Client,
            // awsPath, zipFileName);
            AmazonS3 s3Client = S3Connection.connectToAmazonS3();
            String s3ZippedFileUrl = S3Connection.getS3Path(s3Client, awsPath, zipFileName);

            if (s3ZippedFileUrl == null) {
                // File doesn't exist

                /*-
                 * Example of entity
                 {
                    "branchPath": "MAIN/SNOMEDCT-BE/2020-03-15",
                    "conceptsAndRelationshipsOnly": false,
                    "filenameEffectiveDate": "20210315",
                    "legacyZipNaming": false,
                    "refsetIds": [
                        "741000172102"
                    ],
                    "startEffectiveTime": "20210315",
                    "transientEffectiveTime": "20210315",
                    "type": "SNAPSHOT",
                    "unpromotedChangesOnly": false
                }
                 */
                String entity = "{\"refsetIds\": [\"" + refset.getRefsetId()
                        + "\"],  \"branchPath\": \"" + getBranchPath(refset)
                        + "\", \"conceptsAndRelationshipsOnly\": false, \"filenameEffectiveDate\": \""
                        + fileNameDate + "\", \"legacyZipNaming\": false, \"type\": \"" + type
                        + "\", \"unpromotedChangesOnly\": false"
                        + (startEffectiveTime == null ? ""
                                : ",  \"startEffectiveTime\": \"" + startEffectiveTime + "\"")
                        + (transientEffectiveTime == null ? "" : ",  \"transientEffectiveTime\": \""
                                + transientEffectiveTime + "\"")
                        + "}";

                entity = "{\"refsetIds\": [\"551000172106\"],  \"branchPath\": \"MAIN/SNOMEDCT-BE/2020-03-15\", \"conceptsAndRelationshipsOnly\": false, \"filenameEffectiveDate\": \"20200315\", \"legacyZipNaming\": false, \"type\": \"SNAPSHOT\", \"unpromotedChangesOnly\": false,  \"transientEffectiveTime\": \"20200315\"}";
                logger.debug(entity);

                // generate zip files including support for metadata and
                final String zipFilePath = generateRefsetZipFile(refset, zipFileName,
                        exportMetadata, withNames, entity);

                // upload to S3
                // S3Connection.uploadToS3(s3Client, awsPath, zipFilePath,
                // zipFileName);

                // getS3 Path
                // zippedFileUrl = S3Connection.getS3Path(s3Client, awsPath,
                // zipFileName);

                // if download is from RT2 server
                ServletUriComponentsBuilder builder =
                        ServletUriComponentsBuilder.fromCurrentContextPath();
                zippedFileUrl = builder.build().toString() + EXPORT_DOWNLOAD_URL + zipFileName;
            }

            return zippedFileUrl;

        } catch (Exception ex) {
            throw new Exception("Failed to export zip file name" + ex.getMessage(), ex);
        }
    }

    private static String generateRefsetZipFile(final Refset refset, final String zipFileName,
        final boolean exportMetadata, final boolean appendNames, final String entityString)
        throws Exception {
        String zipFilePath = "";

        // Call Snowstorm to create RF2 file
        String snowstormExportApiUrl = SnowstormConnection.POST_URL + "exports";

        logger.debug("Snowstorm Export API URL: " + snowstormExportApiUrl + entityString);

        String snowstormFileUrl = ""; // "https://www.learningcontainer.com/download/sample-zip-files/?wpdmdl=1637";

        try (Response response =
                SnowstormConnection.postResponse(snowstormExportApiUrl, entityString)) {

            snowstormFileUrl = response.getLocation().toString() + "/archive";
            logger.info("Response location " + snowstormFileUrl);

        } catch (Exception ex) {
            throw new Exception("Could not export refset from snowstorm: " + ex.getMessage(), ex);

        }

        logger.debug("Snowstorm File URL: " + snowstormFileUrl);

        // Download generated file from Snowstorm
        try {

            zipFilePath = EXPORT_FILE_DIR + zipFileName;
            logger.debug("Zip Path is: " + zipFilePath);

            // Download the Snowstorm file
            try (InputStream inputStream = SnowstormConnection.getFileDownload(snowstormFileUrl);
                    ReadableByteChannel readableByteChannel = Channels.newChannel(inputStream);
                    FileOutputStream fileOutputStream = new FileOutputStream(zipFilePath);
                    FileChannel fileChannel = fileOutputStream.getChannel()) {

                fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
                fileOutputStream.close();
            }

        } catch (Exception ex) {
            throw new Exception(
                    "Failed to download the Snowstorm generated RF2 file: " + ex.getMessage(), ex);
        }

        // Generate the Rt2 version of refset RF2 Zip file
        final Path downloadDirectoryPath =
                Files.createTempDirectory("rt2Download-" + zipFileName.replace(".zip", ""));

        // Unzip the download
        final List<String> sourceFiles = unzipFiles(zipFilePath, downloadDirectoryPath.toString());

        if (sourceFiles.size() != 1) {
            throw new Exception("Unexpected number of files generated by Snowstorm Export RF2: "
                    + sourceFiles.size());
        }

        /*-
         * Jesse
        // move refset RF2 file to top level
        final String unzippedFilePath = sourceFiles.iterator().next();
        final String unzippedFileName =
                unzippedFilePath.substring(unzippedFilePath.lastIndexOf("/") + 1)
                        .substring(unzippedFilePath.lastIndexOf("\\") + 1);
        FileUtility.move(unzippedFilePath, toZipDirectoryPath.toString() + "/" + unzippedFileName);
        sourceFiles.clear();
        sourceFiles.add(toZipDirectoryPath.toString() + "/" + unzippedFileName);
        
        // Delete directory structure and original zip
        FileUtility.deleteDirectory(downloadDirectoryPath.toFile());
        
         */
        // If Rf2WithNames selected, append the names to the refset file
        if (appendNames) {
            appendNamesToRf2(refset, sourceFiles, zipFileName);
        }

        // if exportMetadata requested, add it
        if (exportMetadata) {
            sourceFiles.add(exportRefsetMetadata(refset, downloadDirectoryPath));
        }

        // zip the files together
        zipFiles(sourceFiles, EXPORT_FILE_DIR + zipFileName);

        // Delete directory structure and original zip
        FileUtility.deleteDirectory(downloadDirectoryPath.toFile());

        return EXPORT_FILE_DIR;
    }

    private static void appendNamesToRf2(Refset refset, List<String> sourceFiles,
        String zipFileName) throws Exception {
        // Move rf2 file to a tmp (as we create new one below). Update
        // sourceFiles accordingly
        String originalFilePath = sourceFiles.iterator().next();
        String newFilePath = originalFilePath.substring(0, originalFilePath.indexOf(".")) + "-orig"
                + originalFilePath.substring(originalFilePath.indexOf("."));
        FileUtility.move(originalFilePath, newFilePath);
        sourceFiles.clear();
        sourceFiles.add(newFilePath);

        // Get member cache
        Set<Concept> conceptsNotInCache = new HashSet<>();
        Map<String, Concept> members = getCachedRefsetMembers(refset.getId());

        // Read through file and identify those concepts not in cache or don't
        // have all requisite languages populated
        try (BufferedReader br = new BufferedReader(new FileReader(new File(newFilePath)))) {
            String extractedLine = br.readLine();
            extractedLine = br.readLine();

            while (extractedLine != null && !extractedLine.trim().isEmpty()) {
                String conceptId = extractedLine.split("\t")[REFEST_RF2_CONCEPTID_COLUMN];

                // TODO: Also check doesn't have all needed languages
                if (!members.containsKey(conceptId)
                        || members.get(conceptId).getDescriptions().isEmpty()) {
                    Concept concept = new Concept();
                    concept.setCode(conceptId);
                    conceptsNotInCache.add(concept);
                }

                extractedLine = br.readLine();

                if (conceptsNotInCache.size() == CONCEPT_DESCRIPTIONS_PER_CALL
                        || extractedLine == null) {

                    populateAllLanguageDescriptions(refset, conceptsNotInCache);

                    // Populate Members cache with data
                    for (Concept concept : conceptsNotInCache) {
                        if (!members.containsKey(concept.getCode())) {
                            members.put(concept.getCode(), concept);
                        } else {
                            members.get(concept.getCode())
                                    .setDescriptions(concept.getDescriptions());
                        }
                    }
                    conceptsNotInCache.clear();
                }
            }

            br.close();
        }
        // Get descriptions for those not cached or not cached with all
        // languages

        // Read through file 2nd time and write each line to new file while
        // appending selected name
        FileWriter fw = new FileWriter(new File(originalFilePath));

        try (BufferedReader br = new BufferedReader(new FileReader(new File(newFilePath)))) {
            String extractedLine = br.readLine();
            extractedLine = br.readLine();
            while (extractedLine != null) {
                String conceptId = extractedLine.split("\t")[REFEST_RF2_CONCEPTID_COLUMN];

                // TODO: How to determine which language
                if (!members.containsKey(conceptId)) {
                    throw new Exception("Didn't have concept populated with descriptions yet");
                }
                if (members.get(conceptId).getDescriptions().get(1) != null) {
                    fw.write(extractedLine + "\t" + members.get(conceptId).getDescriptions().get(1)
                            .get(DESCRIPTION_TERM));
                } else if (members.get(conceptId).getDescriptions().get(0) != null) {
                    fw.write(extractedLine + "\t" + members.get(conceptId).getDescriptions().get(0)
                            .get(DESCRIPTION_TERM));
                } else if (members.get(conceptId).getDescriptions().get(2) != null) {
                    fw.write(extractedLine + "\t" + members.get(conceptId).getDescriptions().get(2)
                            .get(DESCRIPTION_TERM));
                } else {
                    throw new Exception("Not seeing the expected descriptions for member: "
                            + conceptId + " as have these descriptions: "
                            + members.get(conceptId).getDescriptions());
                }

                fw.write("\n");
                extractedLine = br.readLine();
            }
        }

        fw.close();
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

        logger.debug("Snowstorm URL: " + url);

        try (Response response = SnowstormConnection.getResponse(url)) {

            final String resultString = response.readEntity(String.class);
            return resultString;

        } catch (Exception ex) {
            throw new Exception(
                    "Could not retrieve refset members from snowstorm: " + ex.getMessage(), ex);
        }
    }

    /**
     * Export the refset member concept IDs in a zipped CSV format.
     *
     * @param refsetInternalId the internal refset ID
     * @param exportMetadata should refset metadata be included in the export
     * @return the URL of the file containing the member list
     * @throws Exception the exception
     */
    public static String exportRefsetSctidList(final String refsetInternalId,
        final boolean exportMetadata) throws Exception {

        int offset = 0;
        int limit = 10000;
        boolean morePages = true;
        StringBuilder fileLines = new StringBuilder();
        String zipOutputPath = EXPORT_FILE_DIR;
        String refsetFileName = "";
        String sctidsFilePath = "";
        List<String> sourceFiles = new ArrayList<>();
        Path tempDirectoryPath = null;

        // get the refset and member information
        try (final TerminologyService service = new TerminologyService()) {

            final Refset refset = service.get(refsetInternalId, Refset.class);

            refsetFileName = "refset_" + refset.getRefsetId() + "_" + getRefsetAsOfDate(refset)
                    + "_member_ids.txt";
            zipOutputPath += refsetFileName.replace(".txt", ".zip");
            tempDirectoryPath =
                    Files.createTempDirectory("sctidList-" + refsetFileName.replace(".txt", ""));
            sctidsFilePath = tempDirectoryPath.toString() + "/" + refsetFileName;

            logger.debug("SCTID txt output path = " + sctidsFilePath);
            logger.debug("zip output path = " + zipOutputPath);

            if (exportMetadata) {
                sourceFiles.add(exportRefsetMetadata(refset, tempDirectoryPath));
            }

            while (morePages) {

                final String resultString =
                        getMemberSctids(refset.getRefsetId(), offset, limit, getBranchPath(refset));

                final ObjectMapper mapper = new ObjectMapper();
                final JsonNode root = mapper.readTree(resultString);
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

        } catch (Exception ex) {
            throw new Exception(
                    "Could not get refset member data from snowstorm: " + ex.getMessage(), ex);
        }

        // print the sctids file
        try (final FileOutputStream sctidsFileOutputStream = new FileOutputStream(sctidsFilePath);
                final OutputStreamWriter sctidsOutputStreamWriter =
                        new OutputStreamWriter(sctidsFileOutputStream, "UTF-8");
                final PrintWriter sctidsWriter = new PrintWriter(sctidsOutputStreamWriter);) {

            sctidsWriter.print(fileLines);

        } catch (Exception ex) {
            throw new Exception("Could not create export txt file: " + ex.getMessage(), ex);
        }

        // zip the files together
        sourceFiles.add(sctidsFilePath);
        zipFiles(sourceFiles, zipOutputPath);

        // Delete temp directory structure and files
        FileUtility.deleteDirectory(tempDirectoryPath.toFile());

        // if download is from RT2 server
        ServletUriComponentsBuilder builder = ServletUriComponentsBuilder.fromCurrentContextPath();
        String zippedFileUrl = builder.build().toString() + EXPORT_DOWNLOAD_URL
                + refsetFileName.replace(".txt", ".zip");

        return zippedFileUrl;
    }

    /**
     * Zip files together.
     *
     * @param sourceFiles the list of files to zip together
     * @param zipOutputFilePath the path and filename of the zip file to create
     * @throws Exception the exception
     */
    public static void zipFiles(final List<String> sourceFiles, final String zipOutputFilePath)
        throws Exception {

        try (final FileOutputStream zipFileOutputStream = new FileOutputStream(zipOutputFilePath);
                final ZipOutputStream zipOutputStream = new ZipOutputStream(zipFileOutputStream);) {

            for (String sourceFile : sourceFiles) {

                File fileToZip = new File(sourceFile);

                try (final FileInputStream zipFileInputStream = new FileInputStream(fileToZip)) {

                    ZipEntry zipEntry = new ZipEntry(fileToZip.getName());
                    zipOutputStream.putNextEntry(zipEntry);

                    byte[] bytes = new byte[1024];
                    int length;

                    while ((length = zipFileInputStream.read(bytes)) >= 0) {
                        zipOutputStream.write(bytes, 0, length);
                    }
                }
            }

        } catch (Exception ex) {
            throw new Exception("Could not zip the files: " + ex.getMessage(), ex);
        }
    }

    /**
     * Extract files from a zip archive.
     *
     * @param zipFilePath the path and filename of the zip file to unzip
     * @param extractionPath the path of the directory to extract files to
     * @return a list of file paths of the extracted files
     * @throws Exception the exception
     */
    public static List<String> unzipFiles(final String zipFilePath, final String extractionPath)
        throws Exception {

        final List<String> sourceFiles = new ArrayList<>();

        try (final ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath));) {

            final File extractionDirectory = new File(extractionPath);
            byte[] buffer = new byte[1024];
            ZipEntry zipEntry;

            while ((zipEntry = zis.getNextEntry()) != null) {

                File newFile = new File(extractionDirectory, zipEntry.getName());
                String extractionCanonicalPath = extractionDirectory.getCanonicalPath();
                String fileCanonicalPath = newFile.getCanonicalPath();

                if (!fileCanonicalPath.startsWith(extractionCanonicalPath + File.separator)) {
                    throw new IOException(
                            "Entry is outside of the target directory: " + zipEntry.getName());
                }

                if (zipEntry.isDirectory()) {

                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }

                } else {

                    // fix for Windows-created archives
                    File parent = newFile.getParentFile();

                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }

                    // write file content
                    try (final FileOutputStream fileOutputStream = new FileOutputStream(newFile)) {

                        int length;

                        while ((length = zis.read(buffer)) > 0) {
                            fileOutputStream.write(buffer, 0, length);
                        }
                    }

                    sourceFiles.add(fileCanonicalPath);
                }
            }

            return sourceFiles;

        } catch (Exception ex) {
            throw new Exception("Could not unzip the file: " + ex.getMessage(), ex);
        }
    }

    /**
     * Export the refset metadata in a text format.
     *
     * @param refset the refset
     * @return the URL of the file containing the metadata
     * @throws Exception the exception
     */
    public static String exportRefsetMetadata(final Refset refset, Path directory)
        throws Exception {

        StringBuilder fileLines = new StringBuilder();
        String pathDate = getRefsetAsOfDate(refset);
        String outputPath =
                directory + "/refset_" + refset.getRefsetId() + "_" + pathDate + "_metadata.txt";
        String separator = "\t";

        fileLines.append("Refset ID" + separator + refset.getRefsetId() + "\n");
        fileLines.append("Refset Name" + separator + refset.getName() + "\n");
        fileLines.append("Edition Name" + separator + refset.getEditionName() + "\n");
        fileLines.append("Edition Branch" + separator + refset.getEdition().getBranch() + "\n");
        fileLines.append("Organization" + separator + refset.getOrganizationName() + "\n");
        fileLines.append("Project" + separator + refset.getProject().getName() + "\n");
        fileLines.append("Module ID" + separator + refset.getModuleId() + "\n");
        fileLines.append("Refset Version Status" + separator + refset.getVersionStatus() + "\n");
        fileLines.append("Refset Version Date" + separator + DateUtility
                .formatDate(refset.getVersionDate(), DateUtility.DATE_FORMAT_REVERSE, null) + "\n");
        fileLines.append("Refset Last Modified Date" + separator + DateUtility
                .formatDate(refset.getModified(), DateUtility.DATE_FORMAT_REVERSE, null) + "\n");
        fileLines.append("Refset Type" + separator + refset.getType() + "\n");

        if (refset.isActive()) {
            fileLines.append("Refset Status" + separator + "Active" + "\n");
        } else {
            fileLines.append("Refset Status" + separator + "Inactive" + "\n");
        }

        if (refset.getDefinitionClauses().size() > 0) {

            final List<String> definitionList = new ArrayList<>();

            for (DefinitionClause clause : refset.getDefinitionClauses()) {

                String entry = clause.getValue();

                if (clause.getNegated()) {
                    entry = "(-) " + entry;
                }

                definitionList.add(entry);
            }

            fileLines.append(
                    "Refset Definition" + separator + String.join(", ", definitionList) + "\n");
        }

        fileLines.append("Tags" + separator + String.join(", ", refset.getTags()) + "\n");

        if (refset.getNarrative() != null && !refset.getNarrative().equals("")) {
            fileLines.append("Refset Narrative" + separator + refset.getNarrative() + "\n");
        }

        if (refset.getVersionNotes() != null && !refset.getVersionNotes().equals("")) {
            fileLines.append("Refset Version Notes" + separator + refset.getVersionNotes() + "\n");
        }

        if (refset.getExternalUrl() != null && !refset.getExternalUrl().equals("")) {
            fileLines.append("External URL" + separator + refset.getExternalUrl() + "\n");
        }

        // print the sctids file
        try (final FileOutputStream fileOutputStream = new FileOutputStream(outputPath);
                final OutputStreamWriter outputStreamWriter =
                        new OutputStreamWriter(fileOutputStream, "UTF-8");
                final PrintWriter printWriter = new PrintWriter(outputStreamWriter);) {

            printWriter.print(fileLines);
            return outputPath;

        } catch (Exception ex) {
            throw new Exception("Could not create metadata export txt file: " + ex.getMessage(),
                    ex);
        }
    }

    /**
     * Get either the version date or the current date in yyyy-MM-dd format.
     *
     * @param refset the refset
     * @return the URL of the file containing the metadata
     * @throws Exception the exception
     */
    public static String getRefsetAsOfDate(final Refset refset) throws Exception {

        String asOfDate = "";
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");

        if (refset.getVersionDate() != null) {

            asOfDate = simpleDateFormat.format(refset.getVersionDate());
        } else {
            asOfDate = simpleDateFormat.format(new Date());
        }

        return asOfDate;
    }

    /**
     * Get either the version date or the current date in yyyy-MM-dd format.
     *
     * @param refset the refset
     * @return the URL of the file containing the metadata
     * @throws Exception the exception
     */
    public static String getBranchPath(final Refset refset) throws Exception {

        String branchPath = "";
        String pathDate = "";

        if (refset.getVersionDate() != null) {
            Date tmpDate = refset.getVersionDate();
            pathDate = "/" + DateUtility.formatDate(tmpDate, DateUtility.DATE_FORMAT_REVERSE, null);
        }

        branchPath = refset.getEdition().getBranch() + pathDate;

        return branchPath;
    }

    /**
     * Get the cached set of tree nodes checked for members of the specified
     * refset.
     *
     * @param refsetInternalId the internal ID of the refset
     * @return the set of tree nodes checked for members of the specified refset
     * @throws Exception the exception
     */
    public static Set<String> getCachedRefsetCheckedTreeNodes(final String refsetInternalId)
        throws Exception {

        if (refsetTreeNodeCache.containsKey(refsetInternalId)) {
            return refsetTreeNodeCache.get(refsetInternalId);
        } else {
            return new HashSet<>();
        }
    }

    /**
     * Get the map of cached refset members.
     *
     * @param refsetInternalId the internal ID of the refset
     * @return the map of refset members
     * @throws Exception the exception
     */
    public static Map<String, Concept> getCachedRefsetMembers(final String refsetInternalId)
        throws Exception {

        if (membersCache.containsKey(refsetInternalId)) {
            return membersCache.get(refsetInternalId);
        } else {
            return new HashMap<>();
        }
    }

    /**
     * Gets the concept descriptions.
     *
     * @param refset the refset who's members are being retrieved
     * @param conceptsToProcess the concepts to add descriptions to
     * @return the concept descriptions
     * @throws MalformedURLException the malformed URL exception
     * @throws Exception the exception
     */
    public static void populateAllLanguageDescriptions(final Refset refset,
        final Set<Concept> conceptsToProcess) throws MalformedURLException, Exception {

        final StringBuffer conceptIds = new StringBuffer();

        // Create Snowstorm URL
        final String url =
                SnowstormConnection.BASE_URL + getBranchPath(refset) + "/descriptions?limit=1000";

        boolean firstTime = true;
        for (Concept concept : conceptsToProcess) {
            if (firstTime) {
                firstTime = false;
            } else {
                conceptIds.append(",");
            }
            conceptIds.append(concept.getCode());
        }

        // Call Snowstorm
        logger.debug("Get Member Descriptions URL: " + url + "&conceptIds=" + conceptIds);

        try (final Response response =
                SnowstormConnection.getResponse(url + "&conceptIds=" + conceptIds)) {

            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

            final JsonNode allDescriptionNodes = root.get("items");
            final Iterator<JsonNode> descriptionIterator = allDescriptionNodes.iterator();
            final HashMap<String, Set<JsonNode>> conceptDescriptionNodes = new HashMap<>();

            // Assign descriptions to proper concept
            while (descriptionIterator.hasNext()) {
                final JsonNode descriptionNode = descriptionIterator.next();

                if (descriptionNode.get("active").asBoolean()) {
                    String conceptId = descriptionNode.get("conceptId").asText();

                    if (!conceptDescriptionNodes.containsKey(conceptId)) {
                        conceptDescriptionNodes.put(conceptId, new HashSet<JsonNode>());
                    }

                    conceptDescriptionNodes.get(conceptId).add(descriptionNode);
                }
            }

            // Process and sort each concept's descriptions
            final Map<String, List<Map<String, String>>> conceptDescriptionMap = new HashMap<>();

            final List<String> nonDefaultPreferredTerms =
                    identifyNonDefaultPreferredTerms(refset.getEdition());

            for (String conceptId : conceptDescriptionNodes.keySet()) {
                Set<JsonNode> descriptionNodes = conceptDescriptionNodes.get(conceptId);
                Set<Map<String, String>> descriptions = new HashSet<>();

                descriptions = processDescriptionNodes(descriptionNodes,
                        refset.getEdition().getDefaultLanguageRefsets(), nonDefaultPreferredTerms);

                final List<Map<String, String>> sortedDescriptions = sortConceptDescriptions(
                        conceptId, descriptions, refset, nonDefaultPreferredTerms);

                conceptDescriptionMap.put(conceptId, sortedDescriptions);
            }

            // Populate concept with description-based data
            for (Concept concept : conceptsToProcess) {
                List<Map<String, String>> descriptions =
                        conceptDescriptionMap.get(concept.getCode());

                if (descriptions == null || descriptions.size() == 0) {

                    logger.debug("Description not retrieved for concept " + concept.getCode());
                    continue;
                }

                concept.setDescriptions(descriptions);

                if (descriptions.get(0) != null) {
                    concept.setName(descriptions.get(0).get(DESCRIPTION_TERM));
                } else {

                    for (final Map<String, String> description : descriptions) {

                        if (description == null) {
                            continue;
                        }

                        if (description.get(LANGUAGE_ID).equals("900000000000509007PT")) {

                            concept.setName(description.get(DESCRIPTION_TERM));
                            break;
                        }
                    }
                }

            }
        } catch (Exception ex) {
            logger.error("Could not retrieve descriptions" + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Search refset members.
     *
     * @param refset the refset
     * @param searchParameters the search parameters
     * @return the concept result list
     * @throws MalformedURLException the malformed URL exception
     * @throws Exception the exception
     */
    public static ConceptResultList searchRefsetMembers(final Refset refset,
        final SearchParameters searchParameters) throws MalformedURLException, Exception {

        ConceptResultList members = new ConceptResultList();

        // Create Snowstorm URL
        final String url = SnowstormConnection.BASE_URL + "browser/" + getBranchPath(refset)
                + "/descriptions?term=" + searchParameters.getQuery() + "&conceptRefset="
                + refset.getRefsetId()
                + "&groupByConcept=false&searchMode=STANDARD&offset=0&limit=1000";

        // Call Snowstorm
        logger.debug("Get Member Descriptions URL: " + url);

        try (final Response response = SnowstormConnection.getResponse(url)) {

            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

            final JsonNode allDescriptionNodes = root.get("items");
            final Iterator<JsonNode> itemIterator = allDescriptionNodes.iterator();
            final HashMap<String, Concept> conceptIdToConcept = new HashMap<>();

            // parse items to retrieve matching concepts
            while (itemIterator.hasNext()) {
                final JsonNode itemNode = itemIterator.next();

                if (itemNode.get("active").asBoolean()) {
                    JsonNode conceptNode = itemNode.get("concept");
                    String conceptId = conceptNode.get("conceptId").asText();

                    if (!conceptIdToConcept.containsKey(conceptId)) {
                        Concept cpt = new Concept();
                        cpt.setActive(conceptNode.get("active").asBoolean());
                        cpt.setId(conceptNode.get("id").asText());
                        cpt.setCode(conceptNode.get("id").asText());
                        if (!conceptNode.get("definitionStatus").asText().equals("PRIMITIVE")) {
                            cpt.setDefined(true);
                        } else {
                            cpt.setDefined(false);
                        }
                        if (conceptNode.get("pt") != null) {
                            cpt.setName(conceptNode.get("pt").get("term").asText());
                        }
                        // cpt.setMemberOfRefset(true);
                        // cpt.setMemberStatus(true);
                        conceptIdToConcept.put(conceptId, cpt);
                    }

                }
            }
            populateMembershipInformation(refset,
                    new HashSet<Concept>(conceptIdToConcept.values()));
            members.setItems(new ArrayList<Concept>(conceptIdToConcept.values()));
            members.setTotal(conceptIdToConcept.size());

            return members;
        } catch (Exception ex) {
            logger.error("Could not retrieve descriptions matching term" + ex.getMessage());
            ex.printStackTrace();
        }
        return members;
    }

    /**
     * Process description node.
     *
     * @param descriptionNodes the description nodes
     * @param defaultLanguageRefsets the default language refsets
     * @param nonDefaultPreferredTerms the non default preferred terms
     * @return the sets the
     */
    private static Set<Map<String, String>> processDescriptionNodes(Set<JsonNode> descriptionNodes,
        Set<String> defaultLanguageRefsets, List<String> nonDefaultPreferredTerms) {

        final Set<Map<String, String>> descriptions = new HashSet<>();

        for (JsonNode descriptionNode : descriptionNodes) {

            final Map<String, String> descriptionAttributesMap = new HashMap<>();
            final JsonNode acceptabilityMap = descriptionNode.get("acceptabilityMap");
            String acceptability = null;
            String languageId = null;
            String typeName = null;

            for (String langRefsetId : defaultLanguageRefsets) {

                if (acceptabilityMap.has(langRefsetId)) {

                    acceptability = acceptabilityMap.get(langRefsetId).asText();
                    languageId = langRefsetId;
                    break;
                }
            }

            if (acceptability != null
                    && (nonDefaultPreferredTerms.isEmpty() || "PREFERRED".equals(acceptability))) {

                if ("900000000000003001".equals(descriptionNode.get("typeId").asText())) {
                    typeName = "FSN";
                } else {

                    if ("PREFERRED".equals(acceptability)) {
                        typeName = "PT";
                    } else {
                        typeName = "AC";
                    }
                }

                descriptionAttributesMap.put(DESCRIPTION_TERM,
                        descriptionNode.get("term").asText());
                descriptionAttributesMap.put(DESCRIPTION_TYPE, typeName);
                descriptionAttributesMap.put(DESCRIPTION_ID,
                        descriptionNode.get("descriptionId").asText());
                descriptionAttributesMap.put(LANGUAGE_CODE, languageId);
                descriptionAttributesMap.put(LANGUAGE_ID, languageId + typeName);
                descriptionAttributesMap.put(LANGUAGE_NAME,
                        descriptionNode.get("lang").asText().toUpperCase() + " (" + typeName + ")");
                descriptionAttributesMap.put(DESCRIPTION_LANGUAGE,
                        descriptionNode.get("lang").asText());

                descriptions.add(descriptionAttributesMap);
            }
        }

        return descriptions;
    }

    /**
     * Get the refset member concepts as a list.
     *
     * @param refset the refset who's members are being retrieved
     * @param nonDefaultPreferredTerms the non-default preferred terms
     * @param url the terminology server URL
     * @return the refset member concepts
     * @throws Exception the exception
     */
    public static ConceptResultList getMemberList(final Refset refset,
        final List<String> nonDefaultPreferredTerms, final String url,
        final SearchParameters searchParameters) throws Exception {

        // 2 Snowstorm calls: 1) Memberlist and 2) Descriptions
        ConceptResultList members = new ConceptResultList();

        // TODO: Make the memberListCallCache store a list of concept Ids, not a
        // list of concepts.
        // Then parse through returned list and for any conIds not in
        // memberIdMap, populate just those concepts
        // TODO: Also add to memberListCallCache if the url is not already a key
        if (!memberListCallCache.containsKey(url)) {

            logger.debug("Get Member List URL: " + url);
            try {

                ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
                lookupParameters.setGetMembershipInformation(true);

                final Set<Concept> conceptsToProcess = new HashSet<>();
                final Map<String, Concept> memberIdMap = getCachedRefsetMembers(refset.getId());

                ConceptResultList currentList;
                // if search term is indicated, find members that match search
                // term
                if (searchParameters != null && searchParameters.getQuery() != null) {
                    currentList = searchRefsetMembers(refset, searchParameters);
                } else {
                    // Populate results for member list
                    currentList = getConceptsFromSnowstorm(url, refset, lookupParameters);
                }

                // add the descriptions to the children concepts in batches
                for (int i = 0; i < currentList.getItems().size(); i++) {
                    Concept concept = currentList.getItems().get(i);

                    // Only search concepts that haven't already populated
                    if (!memberIdMap.containsKey(concept.getCode())
                            || concept.getDescriptions().isEmpty()) {
                        conceptsToProcess.add(concept);

                        if (conceptsToProcess.size() == CONCEPT_DESCRIPTIONS_PER_CALL
                                || i == currentList.getItems().size() - 1) {

                            populateAllLanguageDescriptions(refset, conceptsToProcess);
                            conceptsToProcess.clear();
                        }
                    }
                }

                // if the memberCache doesn't have this concept already add it
                for (Concept concept : currentList.getItems()) {

                    if (!memberIdMap.containsKey(concept.getCode())) {
                        memberIdMap.put(concept.getCode(), concept);
                    }
                }

                members.getItems().addAll(currentList.getItems());
                members.setTotal(currentList.getTotal());
                members.setTotalKnown(true);

            } catch (Exception ex) {
                throw new Exception("Could not get refset member list for refset "
                        + refset.getRefsetId() + " from snowstorm: " + ex.getMessage(), ex);
            }
        } else {
            members = memberListCallCache.get(url);
        }

        return members;
    }

    /**
     * Get the refset member concepts as a list.
     *
     * @param refset the refset who's members are being retrieved
     * @param nonDefaultPreferredTerms the non-default preferred terms
     * @param url the terminology server URL
     * @return the refset member concepts
     * @throws Exception the exception
     */
    public static ConceptResultList getMemberTaxonomy(final Refset refset,
        final List<String> nonDefaultPreferredTerms, TaxonomyParameters taxonomyParameters)
        throws Exception {

        // 3 Snowstorm calls: 1) Children, 2) Membership info, and 3) Member
        // Descriptions
        final String parentId = taxonomyParameters.getStartingConceptId();
        List<Concept> childList = new ArrayList<>();
        final boolean childrenChecked =
                getCachedRefsetCheckedTreeNodes(refset.getId()).contains(parentId);
        final Map<String, Concept> memberIdMap = getCachedRefsetMembers(refset.getId());
        final List<Concept> processedTreeNodes = new ArrayList<>();
        ConceptResultList conceptResultList = new ConceptResultList();
        final String branchPath = getBranchPath(refset);

        if (treeCache.containsKey(parentId + branchPath)) {
            childList = treeCache.get(parentId + branchPath);
        } else {

            conceptResultList = getChildrenUpdated(parentId, refset);
            childList = conceptResultList.getItems();
            final Set<Concept> conceptsToProcessDescriptions = new HashSet<>();
            final Set<Concept> conceptsToProcessMembership = new HashSet<>();

            for (Concept concept : childList) {
                // Only search concepts that haven't already populated
                if (memberIdMap.containsKey(concept.getCode())
                        || concept.getMemberEffectiveTime() == null) {
                    conceptsToProcessMembership.add(concept);
                }

                if (!memberIdMap.containsKey(concept.getCode())
                        || concept.getDescriptions().isEmpty()) {
                    // add the descriptions to the children concepts in batches
                    conceptsToProcessDescriptions.add(concept);

                    if (conceptsToProcessDescriptions.size() == CONCEPT_DESCRIPTIONS_PER_CALL) {
                        populateAllLanguageDescriptions(refset, conceptsToProcessDescriptions);
                        conceptsToProcessDescriptions.clear();
                    }
                }
            }

            if (!conceptsToProcessMembership.isEmpty()) {
                populateMembershipInformation(refset, conceptsToProcessMembership);
            }

            if (!conceptsToProcessDescriptions.isEmpty()) {
                populateAllLanguageDescriptions(refset, conceptsToProcessDescriptions);
            }
        }

        for (final Concept concept : childList) {

            final String code = concept.getCode();

            // Populate Member data on the tree nodes
            if (memberIdMap.containsKey(code)) {
                memberIdMap.get(code).populateFrom(concept);
            } else {
                memberIdMap.put(code, concept);
            }

            processedTreeNodes.add(concept);
        }

        if (!treeCache.containsKey(parentId + branchPath)) {
            treeCache.put(parentId + branchPath, childList);
        }

        if (!childrenChecked) {

            Set<String> checkedConcepts = getCachedRefsetCheckedTreeNodes(refset.getId());
            checkedConcepts.add(parentId);
            refsetTreeNodeCache.put(refset.getId(), checkedConcepts);
        }

        conceptResultList.setItems(processedTreeNodes);

        return conceptResultList;
    }

    public static Concept getConceptDetails(String conceptId, Refset refset) throws Exception {
        // 3 Snowstorm calls: 1) on concept, 2) parents, and 3) children
        try {
            final String url = SnowstormConnection.BASE_URL + "browser/" + getBranchPath(refset)
                    + "/" + "concepts/" + conceptId + "?descendantCountForm=inferred";

            logger.debug("Get Concept Details URL: " + url);

            ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
            lookupParameters.setGetDescriptions(true);
            lookupParameters.setGetParentsAndChildren(true);
            lookupParameters.setGetRoleGroups(true);
            lookupParameters.setSingleConceptRequest(true);

            ConceptResultList retList = getConceptsFromSnowstorm(url, refset, lookupParameters);

            if (retList.size() != 1) {
                throw new Exception("Unexpected number of concepts found (" + retList.size()
                        + ") in getConceptDetails");
            }

            return retList.getItems().iterator().next();
        } catch (Exception ex) {
            throw new Exception("Could not get refset children for concept " + conceptId
                    + " from snowstorm: " + ex.getMessage(), ex);
        }
    }

    protected static ConceptResultList getParentsUpdated(String conceptId, Refset refset)
        throws Exception {
        try {
            final String url = SnowstormConnection.BASE_URL + "browser/" + getBranchPath(refset)
                    + "/" + "concepts/" + conceptId + "/parents?form=inferred";

            logger.debug("Get Parents URL: " + url);

            ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
            return getConceptsFromSnowstorm(url, refset, lookupParameters);
        } catch (Exception ex) {
            throw new Exception("Could not get refset parents for concept " + conceptId
                    + " from snowstorm: " + ex.getMessage(), ex);
        }
    }

    protected static ConceptResultList getChildrenUpdated(String conceptId, Refset refset)
        throws Exception {
        try {
            final String url = SnowstormConnection.BASE_URL + "browser/" + getBranchPath(refset)
                    + "/" + "concepts/" + conceptId + "/children?form=inferred";

            logger.debug("Get Children URL: " + url);

            ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
            return getConceptsFromSnowstorm(url, refset, lookupParameters);
        } catch (Exception ex) {
            throw new Exception("Could not get refset children for concept " + conceptId
                    + " from snowstorm: " + ex.getMessage(), ex);
        }
    }

    /**
     * Gets the children.
     * 
     * @param branchPath
     *
     * @param conceptId the starting concept id
     * @param branchPath the branch
     * @return the children
     * @throws Exception the exception
     */
    protected static ConceptResultList getConceptsFromSnowstorm(String url, Refset refset,
        ConceptLookupParameters lookupParameters) throws Exception {
        try (final Response response = SnowstormConnection.getResponse(url)) {
            final String resultString = response.readEntity(String.class);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

            return populateConcepts(root, refset, lookupParameters);
        }
    }

    /**
     * Populate concepts from Snowstorm.
     *
     * @param root the iterator
     * @param branchPath
     * @param conceptList
     * @param branch the branch
     * @return
     * @return the concept result list
     * @throws Exception the exception
     */
    private static ConceptResultList populateConcepts(JsonNode root, Refset refset,
        ConceptLookupParameters lookupParameters) throws Exception {

        final ConceptResultList conceptList = new ConceptResultList();

        JsonNode conceptNode = root;
        Iterator<JsonNode> iterator = null;
        int total = 0;

        if (root.get("total") != null) {
            total = root.get("total").asInt();
        }

        if (!lookupParameters.isGetMembershipInformation()) {
            iterator = root.iterator();
        } else {
            iterator = root.get("items").iterator();
        }

        final Map<String, Concept> memberIdMap = getCachedRefsetMembers(refset.getId());

        while (lookupParameters.isSingleConceptRequest() || iterator.hasNext()) {
            if (!lookupParameters.isSingleConceptRequest()) {
                conceptNode = iterator.next();
            }

            String conceptId = null;
            if (conceptNode.has("referencedComponentId")) {
                conceptId = conceptNode.get("referencedComponent").get("conceptId").asText();
            } else if (conceptNode.has("conceptId")) {
                conceptId = conceptNode.get("conceptId").asText();
            } else {
                throw new Exception("Unable to process the conceptNode: " + conceptNode);
            }

            final Concept concept = new Concept();
            final Concept cachedConcept = memberIdMap.get(conceptId);

            ConceptLookupParameters missingLookupParameters =
                    identifyContentPopulated(cachedConcept, refset, lookupParameters);

            if (missingLookupParameters == null) {
                // Null means cached concept is fully populated as needed
                concept.populateFrom(cachedConcept);
            } else {

                if (cachedConcept != null) {
                    concept.populateFrom(cachedConcept);
                }

                String name = null;
                boolean memberStatus = false;
                boolean defined = false;

                if (conceptNode.has("referencedComponentId")) {
                    // Concept is a member
                    // Read member-representation of basic concept content
                    if (conceptNode.get("referencedComponent").get("pt") != null) {
                        name = conceptNode.get("referencedComponent").get("pt").get("term")
                                .asText();
                    } else {
                        name = conceptNode.get("referencedComponent").get("term").asText();
                    }

                    // grab all membership info
                    memberStatus = conceptNode.get("active").asBoolean();
                    concept.setMemberEffectiveTime(SIMPLE_DATE_FORMAT
                            .parse(conceptNode.get("releasedEffectiveTime").asText()));
                } else if (conceptNode.has("conceptId")) {
                    // Concept is General
                    // Read general-representation of basic concept content
                    if (conceptNode.get("pt") != null) {
                        name = conceptNode.get("pt").get("term").asText();
                    } else {
                        name = conceptNode.get("term").asText();
                    }

                    // grab other concept information
                    if (!conceptNode.get("definitionStatus").asText().equals("PRIMITIVE")) {
                        defined = true;
                    }

                    if (conceptNode.has("descendantCount")) {
                        concept.setHasChildren(conceptNode.get("descendantCount").asInt() > 0);
                    } else if (conceptNode.has("isLeafInferred")) {
                        concept.setHasChildren(!conceptNode.get("isLeafInferred").asBoolean());
                    }
                }

                concept.setCode(conceptId);
                concept.setName(name);
                concept.setTerminology("SNOMEDCT");
                concept.setHistoryVisible(true);
                concept.setFeedbackVisible(true);
                concept.setMemberStatus(memberStatus);
                concept.setDefined(defined);

                // Populate descriptions
                if (missingLookupParameters.isGetDescriptions()) {
                    concept.setDescriptions(
                            populateDescriptions(concept.getCode(), conceptNode.get("descriptions"),
                                    refset, missingLookupParameters.getNonDefaultPreferredTerms()));
                }

                if (missingLookupParameters.isGetParentsAndChildren()) {
                    concept.setParents(getParentsUpdated(conceptId, refset).getItems());
                    concept.setChildren(getChildrenUpdated(conceptId, refset).getItems());

                }

                if (missingLookupParameters.isGetRoleGroups()) {
                    concept.setRoleGroups(populateRoleGroups(concept.getCode(),
                            conceptNode.get("relationships")));
                }

                if (missingLookupParameters.isGetMembershipInformation()) {
                    concept.setMemberOfRefset(true);
                    concept.setMemberStatus(conceptNode.get("active").asBoolean());
                    concept.setMemberEffectiveTime(SIMPLE_DATE_FORMAT
                            .parse(conceptNode.get("releasedEffectiveTime").asText()));
                }
            }

            conceptList.getItems().add(concept);

            if (lookupParameters.isSingleConceptRequest()) {
                break;
            }
        }

        conceptList.setTotal(total);
        return conceptList;
        // populateVersionInfo(conceptIdMap, branch);
    }

    private static ConceptLookupParameters identifyContentPopulated(Concept concept, Refset refset,
        ConceptLookupParameters lookupParameters) {

        // If concept not found in cache, concept is null. Just return origianl
        // lookup
        // parameters
        if (concept == null) {
            return lookupParameters;
        }

        // Concept found in cache, so review contents to see what requires
        // further
        // lookup
        ConceptLookupParameters missingConceptLookupParameters = new ConceptLookupParameters();
        boolean missingContentFound = false;

        if (lookupParameters.isGetDescriptions() && concept.getDescriptions().isEmpty()) {
            missingConceptLookupParameters.setGetDescriptions(true);
            missingContentFound = true;
        }

        if (lookupParameters.isGetMembershipInformation()
                && concept.getMemberEffectiveTime() == null) {
            missingConceptLookupParameters.setGetMembershipInformation(true);
            missingContentFound = true;
        }

        if (lookupParameters.isGetParentsAndChildren() && concept.getParents().isEmpty()
                && concept.getChildren().isEmpty()) {
            missingConceptLookupParameters.setGetParentsAndChildren(true);
            missingContentFound = true;
        }

        if (lookupParameters.isGetRoleGroups() && concept.getRoleGroups().isEmpty()) {
            missingConceptLookupParameters.setGetRoleGroups(true);
            missingContentFound = true;
        }

        if (lookupParameters.isSingleConceptRequest()) {
            missingConceptLookupParameters.setSingleConceptRequest(true);
            missingContentFound = true;
        }

        // Concept already contains all needed data, so no further lookup
        // needed. Return
        // Null
        if (!missingContentFound) {
            return null;
        }

        // Return required updated content
        return missingConceptLookupParameters;
    }

    private static Map<Integer, Map<String, String>> populateRoleGroups(String conceptId,
        JsonNode relationshipsNode) {
        Map<Integer, Map<String, String>> roleGroups = new HashMap<>();

        final Iterator<JsonNode> iterator = relationshipsNode.iterator();

        while (iterator.hasNext()) {
            JsonNode relationship = iterator.next();

            if (relationship.get("active").asBoolean() && "INFERRED_RELATIONSHIP"
                    .equals(relationship.get("characteristicType").asText()))

            {
                int groupId = relationship.get("groupId").asInt();
                if (!roleGroups.containsKey(groupId)) {
                    roleGroups.put(groupId, new HashMap<String, String>());
                }

                String type = relationship.get("type").get("pt").get("term").asText();

                if (!"Is a".equals(type)) {
                    String target = relationship.get("target").get("pt").get("term").asText();

                    roleGroups.get(groupId).put(type, target);
                }
            }
        }

        if (roleGroups.get(0).size() == 0) {
            roleGroups.remove(0);
        }

        return roleGroups;
    }

    /**
     * Populate all descriptions.
     *
     * @param conceptId
     * @param descriptions
     * @param refset
     * @param nonDefaultPreferredTerms
     * @return
     * @throws Exception
     */
    private static List<Map<String, String>> populateDescriptions(String conceptId,
        JsonNode descriptions, Refset refset, List<String> nonDefaultPreferredTerms)
        throws Exception {
        final Iterator<JsonNode> iterator = descriptions.iterator();

        Set<JsonNode> descriptionNodes = new HashSet<>();

        while (iterator.hasNext()) {
            JsonNode description = iterator.next();
            descriptionNodes.add(description);
        }

        Set<Map<String, String>> populatedDescriptions = processDescriptionNodes(descriptionNodes,
                refset.getEdition().getDefaultLanguageRefsets(), nonDefaultPreferredTerms);

        return sortConceptDescriptions(conceptId, populatedDescriptions, refset,
                nonDefaultPreferredTerms);
    }

    private static void populateMembershipInformation(Refset refset, Set<Concept> conceptsToProcess)
        throws Exception {
        final String url = SnowstormConnection.BASE_URL + getBranchPath(refset)
                + "/members?referenceSet=" + refset.getRefsetId() + "&limit=1000" + "&offset=0";

        logger.debug("Get Membership URL: " + url);

        ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
        lookupParameters.setGetMembershipInformation(true);
        ConceptResultList resultList = getConceptsFromSnowstorm(url, refset, lookupParameters);

        int conceptsToBeProcessed = conceptsToProcess.size();
        for (Concept lookupConcept : resultList.getItems()) {
            for (Concept conceptToProcess : conceptsToProcess) {
                if (lookupConcept.getCode().equals(conceptToProcess.getCode())) {
                    conceptToProcess.setMemberOfRefset(lookupConcept.isMemberOfRefset());
                    conceptToProcess.setMemberStatus(lookupConcept.isMemberStatus());
                    conceptToProcess.setMemberEffectiveTime(lookupConcept.getMemberEffectiveTime());
                    conceptsToBeProcessed--;
                    break;
                }
            }

            if (conceptsToBeProcessed == 0) {
                break;
            }
        }
    }

    public static List<Map<String, String>> getMemberHistory(String referencedComponentId,
        List<Map<String, String>> versions) throws Exception {

        List<Map<String, String>> memberHistory = new ArrayList<>();
        String previousStatus = null;
        String previousVersion = null;
        String lastAddedVersion = null;

        try (final TerminologyService service = new TerminologyService()) {

            for (Map<String, String> version : versions) {

                if ("beta, published".contains(version.get("status").toLowerCase())) {

                    String refsetInternalId = version.get("refsetInternalId");
                    String currentVersion = version.get("date");

                    logger.debug("Processing history on: " + currentVersion
                            + " using internalRefsetId: " + refsetInternalId);

                    final Refset refset = service.get(refsetInternalId, Refset.class);

                    final String url = SnowstormConnection.BASE_URL + getBranchPath(refset)
                            + "/members?referenceSet=" + refset.getRefsetId()
                            + "&referencedComponentId=" + referencedComponentId;

                    logger.debug("Get Membership History URL: " + url);

                    try (final Response response = SnowstormConnection.getResponse(url)) {

                        final String resultString = response.readEntity(String.class);
                        final ObjectMapper mapper = new ObjectMapper();
                        final JsonNode root = mapper.readTree(resultString.toString());
                        final JsonNode node = root.get("items");
                        Iterator<JsonNode> iterator = node.iterator();
                        String currentStatus = null;

                        if (iterator.hasNext()) {

                            JsonNode memberNode = iterator.next();

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
                            break;
                        }

                        // if the previous status wasn't null and the current
                        // status doesn't match it then set the last status
                        if (previousStatus != null && !currentStatus.equals(previousStatus)) {

                            Map<String, String> historyEntry = new HashMap<>();
                            historyEntry.put("version", previousVersion);

                            if (previousStatus.equals("Active")) {
                                historyEntry.put("change", "Activated");
                            } else {
                                historyEntry.put("change", "Inactivated");
                            }

                            memberHistory.add(historyEntry);
                            lastAddedVersion = previousVersion;
                        }

                        previousStatus = currentStatus;
                        previousVersion = currentVersion;

                    } catch (Exception ex) {
                        throw new Exception("Could not grab refset members for refset "
                                + refset.getRefsetId() + " from snowstorm: " + ex.getMessage(), ex);
                    }
                }
            }

            // if the final version added to the list is the previous version
            if (lastAddedVersion != null && lastAddedVersion.equals(previousVersion)) {

                // change the verb to indicate this was when the concept was
                // added to the refset
                if (previousStatus != null && previousStatus.equals("Active")) {
                    memberHistory.get(memberHistory.size() - 1).put("change", "Added");
                } else if (previousStatus != null && previousStatus.equals("Inactive")) {
                    memberHistory.get(memberHistory.size() - 1).put("change", "Added as inactive");
                }
            }

            // since the last version added was not the previous version add
            // that version to the list
            else if (previousStatus != null) {

                Map<String, String> historyEntry = new HashMap<>();
                historyEntry.put("version", previousVersion);

                if (previousStatus.equals("Active")) {
                    historyEntry.put("change", "Added");
                } else {
                    historyEntry.put("change", "Added as inactive");
                }

                memberHistory.add(historyEntry);
            }

        }

        return memberHistory;
    }
}
