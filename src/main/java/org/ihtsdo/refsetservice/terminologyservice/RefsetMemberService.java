 /**
 * 
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.handler.ExportHandler;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.UpgradeInactiveConcecpt;
import org.ihtsdo.refsetservice.model.UpgradeReplacementConcecpt;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ConceptLookupParameters;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.ihtsdo.refsetservice.util.FileUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.ihtsdo.refsetservice.util.StringUtility;
import org.ihtsdo.refsetservice.util.TaxonomyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

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
    
    /** The upgrade changed status for REPLACEMENT_REMOVED. */
    public static final String REPLACEMENT_REMOVED = "REPLACEMENT_REMOVED";
    
    /** The upgrade changed status for REPLACEMENT_ADDED. */
    public static final String REPLACEMENT_ADDED = "REPLACEMENT_ADDED";
    
    /** The upgrade changed status for REMOVED_MANUAL_REPLACEMENT. */
    public static final String REMOVED_MANUAL_REPLACEMENT = "REMOVED_MANUAL_REPLACEMENT";
    
    /** The upgrade changed status for NEW_MANUAL_REPLACEMENT. */
    public static final String NEW_MANUAL_REPLACEMENT = "NEW_MANUAL_REPLACEMENT";
    
    /** The upgrade changed status for INACTIVE_ADDED. */
    public static final String INACTIVE_ADDED = "INACTIVE_ADDED";
    
    /** The upgrade changed status for INACTIVE_REMOVED. */
    public static final String INACTIVE_REMOVED = "INACTIVE_REMOVED";

    /** The local directory to store exported refset files. */
    private static String EXPORT_FILE_DIR;

    /** The local server url to download exported refset files. */
    private static String EXPORT_DOWNLOAD_URL = "export/download/";

    /** A list of refset actively being updated. */
    public final static Set<String> refsetsBeingUpdated = new HashSet<>();
    
    /** A list of refset actively being updated. */
    public final static Map<String, Map<String, Map<String, String>>> refsetsUpdatedMembers = new HashMap<>();
    
    /** A cache of the members returned for a specific URL. */
    private final static Map<String, Map<String, ConceptResultList>> conceptsCallCache = new HashMap<>();

    /** A cache of the details for any concept. */
    private final static Map<String, Map<String, Concept>> conceptDetailsCache = new HashMap<>();

    /** A cache of the taxonomy ancestor path for concepts. */
    private final static Map<String, Map<String, List<Concept>>> taxonomySearchAncestorsCache = new HashMap<>();

    /** A cache of the children for each tree node. */
    private final static Map<String, Map<String, ConceptResultList>> treeCache = new HashMap<>();

    /** A cache of the children for each tree node. */
    public final static Map<String, Map<String, Set<String>>> ancestorsCache = new HashMap<>();

    /** The Constant CONCEPT_DESCRIPTIONS_PER_CALL. */
    private static final int CONCEPT_DESCRIPTIONS_PER_CALL = 386;
    
    /** The Constant URL_MAX_CHAR_LENGTH - URLs will error if larger. */
    private static final int URL_MAX_CHAR_LENGTH = 7500;
    
    /** The max number of record elasticsearch will return without erroring. */
    private static final int ELASTICSEARCH_MAX_RECORD_LENGTH = 9990;

    /** The column index of the concept ID in an RF2 file. */
    public static final int REFEST_RF2_CONCEPTID_COLUMN = 5;
    
    /** The number of milliseconds to stop processing records to avoid a gateway timeout. */
    public static final int TIMEOUT_MILLISECOND_THRESHOLD = 60000;

    private static boolean returnEmptyCache = true;

    static {

        EXPORT_FILE_DIR = PropertyUtility.getProperty("export.fileDir") + File.separator;

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
     * @param user the user
     * @param refsetInternalId the internal refset ID
     * @param searchParameters the search parameters
     * @param displayType Should results be a list or hierarchical taxonomy
     * @param taxonomyParameters the taxonomy parameters
     * @return the refset member concepts
     * @throws Exception the exception
     */
    public static ConceptResultList getRefsetMembers(final User user, final String refsetInternalId,
        final SearchParameters searchParameters, final String displayType, final TaxonomyParameters taxonomyParameters) throws Exception {

        ConceptResultList concepts = new ConceptResultList();

        try (final TerminologyService service = new TerminologyService()) {
            
            Refset refset = getRefset(user, service, refsetInternalId);
            final List<String> nonDefaultPreferredTerms = identifyNonDefaultPreferredTerms(refset.getEdition());

            // the next call depend if a list or taxonomy is being returned
            if (displayType.equals("list")) {

                concepts = getMemberList(refset, nonDefaultPreferredTerms, searchParameters);
                logger.debug("Refset has " + concepts.size() + " members");

            } else {
                concepts = getMemberTaxonomy(refset, nonDefaultPreferredTerms, taxonomyParameters);
            }

            //logger.debug("******** getRefsetMembers results: " + ModelUtility.toJson(concepts));
        }

        return concepts;
    }
    
    /**
     * Get the refset.
     *
     * @param user the user
     * @param service the service
     * @param refsetInternalId the internal refset ID
     * @return the refset
     * @throws Exception the exception
     */
    public static Refset getRefset(final User user, final TerminologyService service, final String refsetInternalId) throws Exception {

        Refset refset = service.get(refsetInternalId, Refset.class);

        if (refset == null) {
            throw new Exception("Refset Internal Id: " + refsetInternalId
                    + " does not exist in the RT2 database");
        }
        
        refset = RefsetService.setRefsetPermissions(user, refset);

        return refset;
    }

    // called recursively to accumulate all refset members in order to compose a
    // freeset
    // uses the searchAfter mechanism rather than paging
    public static List<Concept> getAllRefsetMembers(final String refsetInternalId,
        String searchAfter, List<Concept> concepts) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {
            Refset refset = service.get(refsetInternalId, Refset.class);

            if (refset == null) {
                throw new Exception("Refset Internal Id: " + refsetInternalId
                        + " does not exist in the RT2 database");
            }

            final String url = SnowstormConnection.BASE_URL + getBranchPath(refset)
                    + "/concepts?ecl=%5E%20" + refset.getRefsetId() + "&offset=0&limit=10000"
                    + (searchAfter.contentEquals("") ? "" : "&searchAfter=" + searchAfter);

            ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
            lookupParameters.setGetMembershipInformation(true);
            lookupParameters.setGetDescriptions(true);

            try (final Response response = SnowstormConnection.getResponse(url)) {
                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    throw new Exception(
                            "call to url '" + url + "' wasn't successful. " + response.toString());
                }

                final String resultString = response.readEntity(String.class);

                final ObjectMapper mapper = new ObjectMapper();
                final JsonNode root = mapper.readTree(resultString.toString());

                ConceptResultList conceptList = populateConcepts(root, refset, lookupParameters);
                concepts.addAll(conceptList.getItems());

                searchAfter =
                        (root.get("searchAfter") != null ? root.get("searchAfter").asText() : "");
                if (!searchAfter.isEmpty()) {
                    getAllRefsetMembers(refsetInternalId, searchAfter, concepts);
                }
            }
        }

        logger.debug("Refset has " + concepts.size() + " members");

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
        final Map<String, Set<Map<String, String>>> sortingMap = new HashMap<>();

        // Actual code
        for (Map<String, String> descriptionMap : descriptions) {

            final String languageId = descriptionMap.get(LANGUAGE_ID);

            if (!sortingMap.containsKey(languageId)) {
                sortingMap.put(languageId, new HashSet<Map<String, String>>());
            }
            // Handle the default language
            if (descriptionMap.get(DESCRIPTION_LANGUAGE)
                    .equals(refset.getEdition().getDefaultLanguageCode())) {

                if (descriptionMap.get(DESCRIPTION_TYPE).equalsIgnoreCase("fsn")) {

                    if (sortingMap.containsKey(languageId)
                            && !sortingMap.get(languageId).isEmpty()) {

                        displayDuplicateWarning("A FSN in the default language", conceptId,
                                descriptionMap.get(DESCRIPTION_LANGUAGE),
                                sortingMap.get(languageId), descriptionMap);
                        continue;
                    }

                    sortingMap.get(languageId).add(descriptionMap);

                } else {

                    if ("pt".equalsIgnoreCase(descriptionMap.get(DESCRIPTION_TYPE))
                            && sortingMap.containsKey(languageId)
                            && !sortingMap.get(languageId).isEmpty()) {

                        displayDuplicateWarning("A PT in the default language", conceptId,
                                descriptionMap.get(DESCRIPTION_LANGUAGE),
                                sortingMap.get(languageId), descriptionMap);
                        continue;
                    }

                    sortingMap.get(languageId).add(descriptionMap);
                }
            }

            // Handle the non-default languages
            else {

                if (descriptionMap.get(DESCRIPTION_TYPE).equalsIgnoreCase("fsn")) {

                    if (sortingMap.containsKey(languageId)
                            && !sortingMap.get(languageId).isEmpty()) {

                        displayDuplicateWarning("A FSN in a non-default language", conceptId,
                                descriptionMap.get(DESCRIPTION_LANGUAGE),
                                sortingMap.get(languageId), descriptionMap);
                        continue;
                    }

                    sortingMap.get(languageId).add(descriptionMap);

                } else {

                    if ("pt".equalsIgnoreCase(descriptionMap.get(DESCRIPTION_TYPE))
                            && sortingMap.containsKey(languageId)
                            && !sortingMap.get(languageId).isEmpty()) {

                        displayDuplicateWarning("A PT in a non-default language", conceptId,
                                descriptionMap.get(DESCRIPTION_LANGUAGE),
                                sortingMap.get(languageId), descriptionMap);
                        continue;
                    }

                    sortingMap.get(languageId).add(descriptionMap);
                }
            }
        }

        final List<Map<String, String>> languageRefsets =
                refset.getEdition().getFullyQualifiedLanguageRefsets();

        Set<String> languageIdsProcessed = new HashSet<>();

        for (final Map<String, String> languageRefset : languageRefsets) {
            final String languageId = languageRefset.get("qualifiedLanguageRefset");

            if (sortingMap.get(languageId) != null) {
                sortedDescriptionList.addAll(sortingMap.get(languageId));
                languageIdsProcessed.add(languageId);
            } else {
                sortedDescriptionList.add(null);
            }
        }

        // Add non-FSN & Default Language PTs... but defer the Text Definitions
        // to end
        Set<String> textDescriptionLanguageIds = new HashSet<>();

        for (final String languageId : sortingMap.keySet()) {
            if (!languageIdsProcessed.contains(languageId)) {
                if (languageId.toLowerCase().endsWith("def")) {
                    textDescriptionLanguageIds.add(languageId);
                } else {
                    sortedDescriptionList.addAll(sortingMap.get(languageId));
                }
            }
        }

        // Finally, add Text Definitions
        for (final String languageId : textDescriptionLanguageIds) {
            sortedDescriptionList.addAll(sortingMap.get(languageId));
        }

        return sortedDescriptionList;
    }

    /**
     * Display duplicate warning.
     *
     * @param errorMessage the error msg
     * @param conceptId the con id
     * @param language the language
     * @param set the orig map
     * @param descriptionMap the desc map
     */
    private static void displayDuplicateWarning(final String errorMessage, final String conceptId,
        final String language, final Set<Map<String, String>> existingDescriptions,
        final Map<String, String> descriptionMap) {

        logger.warn(errorMessage + "(" + language + ") has already been identified for conceptId: "
                + conceptId);

        logger.warn("Original ones identified:");

        for (Map<String, String> set : existingDescriptions) {
            printDescription(set);
        }

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
     * Get a list of refsets containing members matching the search.
     *
     * @param searchParameters the search parameters
     * @return a list of refsets containing members matching the search
     * @throws Exception the exception
     */
    public static Set<String> searchDirectoryMembers(final SearchParameters searchParameters) throws Exception {

        final String query = searchParameters.getQuery();
        final List<String> directoryColumns =
            Arrays.asList("id", "refsetId", "name", "editionName", "organizationName", "versionStatus", "versionDate", "modified", "privateRefset");
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
        return getDirectoryMembers(snowstormQuery);
    }
    
    /**
     * Multisearch descriptions of reference sets
     * @param snowstormQuery the query string
     * @return JSON string search results
     * @throws Exception the exception
     */
    private static Set<String> getDirectoryMembers(String snowstormQuery) throws Exception {

        // refsetId: "12345" AND privateRefset:false AND term:"blood" AND name:"work"
        final Set<String> refsetIds = new HashSet<>();

        // if there are no query terms just exit the method
        if (snowstormQuery.equals("")) {
            return refsetIds;
        }

        String url = SnowstormConnection.BASE_URL + "multisearch/descriptions/referencesets?active=true&offset=0&limit=1&term="
            + StringUtility.encodeValue(snowstormQuery);

        logger.debug("Snowstorm URL: " + url);

        try (Response response = SnowstormConnection.getResponse(url)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                throw new Exception("call to url '" + url + "' wasn't successful. " + response.toString());
            }

            final String resultString = response.readEntity(String.class);
            logger.debug("searchDirectoryMembers resultString: " + resultString);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

            if (root.get("buckets") != null) {

                Iterator<String> membershipIterator = root.get("buckets").get("membership").fieldNames();

                while (membershipIterator.hasNext()) {
                    refsetIds.add(membershipIterator.next());
                }
            }

        } catch (Exception ex) {
            throw new Exception("Could not retrieve refset members from snowstorm: " + ex.getMessage(), ex);
        }

        logger.debug("searchDirectoryMembers refsetQuery: {}", refsetIds);
        return refsetIds;
    }
    
    /**
     * Multisearch of descriptions
     * 
     * @param term Term to search
     * @param ecl ECL to narrow search
     * @return Collection of conceptIds as strings.
     * @throws Exception the exception
     */
    public static Set<String> searchMultisearchDescriptions(final SearchParameters searchParameters, final String ecl) throws Exception {

        final String query = searchParameters.getQuery();
        final List<String> directoryColumns =
            Arrays.asList("id", "refsetId", "name", "editionName", "organizationName", "versionStatus", "versionDate", "modified", "privateRefset");
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

        String url = SnowstormConnection.BASE_URL + "multisearch/descriptions?active=true&offset=0&limit=10000" + "&ecl=" + StringUtility.encodeValue(ecl) + "&term="
            + StringUtility.encodeValue(snowstormQuery);

        try (Response response = SnowstormConnection.getResponse(url)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                throw new Exception("call to url '" + url + "' wasn't successful. " + response.toString());
            }

            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

            final Set<String> conceptIds = new HashSet<>();
            if (root.get("items") != null) {
                final JsonNode itemsNode = root.get("items");
                if (itemsNode.isArray()) {
                    for (JsonNode itemNode : itemsNode) {
                        conceptIds.add(itemNode.get("concept").get("conceptId").asText());
                    }
                }
            }
            return conceptIds;
        }
    }

    /**
     * Get the refset member concepts in RF2 format.
     *
     * @param refsetInternalId the internal refset ID
     * @param type the type
     * @param languageId the language to display names in
     * @param fileNameDate the file name date
     * @param startEffectiveTime the start effective time
     * @param transientEffectiveTime the transient effective time
     * @param exportMetadata should refset metadata be included in the export
     * @return the refset member concepts
     * @throws Exception the exception
     */
    @SuppressWarnings({
            "null", "unused"
    })
    public static String exportRefsetRf2(final String refsetInternalId, final String type,
        final String languageId, final String fileNameDate, final String startEffectiveTime,
        final String transientEffectiveTime, final boolean exportMetadata, final boolean withNames)
        throws Exception {
        // TODO: Turn this into a method variable
        final Set<String> dates = new HashSet<>();

        dates.add(transientEffectiveTime);
        if (startEffectiveTime != null) {
            dates.add(startEffectiveTime);
        }
        ExportHandler exporter = new ExportHandler();

        try (final TerminologyService service = new TerminologyService()) {
            final Refset refset = service.get(refsetInternalId, Refset.class);

            if (refset == null) {
                throw new Exception("Refset Internal Id: " + refsetInternalId
                        + " does not exist in the RT2 database");
            }

            try {
                S3ConnectionWrapper.connectToAmazonS3();
            } catch (Exception e) {
                // do nothing
            }
            final String awsVersionedPath =
                    exporter.generateAwsBaseVersionPath(refset, type, dates);

            final String rt2VersionFileName = exporter.generateRt2VersionFileName(refset, type,
                    languageId, dates, exportMetadata, withNames);

            // Check if file already exists
            if (!S3ConnectionWrapper.isInS3Cache(awsVersionedPath, rt2VersionFileName)) {
                // Rt2 Version File doesn't reside on s3

                // Snowstorm generated RF2 file
                final String snowGeneratedFileName =
                        exporter.generateSnowVersionFileName(refset, type, dates);

                // Local place to store snowBaseVersionFileName
                final Path localSnowGeneratedTempDir =
                        Files.createTempDirectory("rt2LocalSnowGenerated-");

                // Local Snowstorm generated Rf2 file name
                final String localSnowGeneratedFilePath =
                        localSnowGeneratedTempDir + File.separator + snowGeneratedFileName;

                // Check if SnowS version file name does already exist in S3
                // Cache
                if (!S3ConnectionWrapper.isInS3Cache(awsVersionedPath, snowGeneratedFileName)) {
                    // Base-SnowVersion file is not on S3, so generate it, and
                    // after downloading it, store it on S3

                    // Generate file on SnowS
                    final String entityString = "{\"refsetIds\": [\"" + refset.getRefsetId()
                            + "\"],  \"branchPath\": \"" + getBranchPath(refset)
                            + "\", \"conceptsAndRelationshipsOnly\": false, \"filenameEffectiveDate\": \""
                            + fileNameDate
                            + "\", \"legacyZipNaming\": false, \"type\": \"SNAPSHOT\", \"unpromotedChangesOnly\": false"
                            + (startEffectiveTime == null ? ""
                                    : ",  \"startEffectiveTime\": \"" + startEffectiveTime + "\"")
                            + (transientEffectiveTime == null ? ""
                                    : ",  \"transientEffectiveTime\": \"" + transientEffectiveTime
                                            + "\"")
                            + "}";

                    logger.debug("generating file from snowstorm");
                    // Generate on SnowS
                    final String snowGeneratedFileUrl =
                            exporter.generateSnowVersionFile(entityString);

                    logger.debug("Downloading file from snowstorm");
                    // Download file from SnowS
                    exporter.downloadSnowGeneratedFile(snowGeneratedFileUrl,
                            localSnowGeneratedFilePath);

                    logger.debug("uploading snowstorm genned file to S3");
                    // store file one s3
                    S3ConnectionWrapper.uploadToS3(awsVersionedPath,
                            localSnowGeneratedTempDir.toString(), snowGeneratedFileName);
                } else {

                    logger.debug("Downloading snowstorm genned file from S3");
                    S3ConnectionWrapper.downloadSnowFromS3(awsVersionedPath, snowGeneratedFileName,
                            localSnowGeneratedFilePath);
                }

                logger.debug("converting snowstorm genned file to RT2 format");
                // Have access to localSnowGeneratedFilePath from which rt2 will
                // generate the
                // export file
                generateRt2ExportFile(refset, localSnowGeneratedFilePath, rt2VersionFileName,
                        exportMetadata, withNames, languageId);

                S3ConnectionWrapper.uploadToS3(awsVersionedPath, EXPORT_FILE_DIR,
                        rt2VersionFileName);

                FileUtility.deleteDirectory(localSnowGeneratedTempDir.toFile());

            } else {

                if (!Files.exists(Path.of(EXPORT_FILE_DIR + rt2VersionFileName))) {
                    logger.debug("Downloading RT2 genned file from S3");
                    S3ConnectionWrapper.downloadSnowFromS3(awsVersionedPath, rt2VersionFileName,
                            EXPORT_FILE_DIR + rt2VersionFileName);
                }
            }

            logger.debug("Final Export File Path: " + EXPORT_FILE_DIR + rt2VersionFileName);

            // if download is from RT2 server
            ServletUriComponentsBuilder builder =
                    ServletUriComponentsBuilder.fromCurrentContextPath();
            return EXPORT_DOWNLOAD_URL + rt2VersionFileName; // builder.build().toString()
                                                             // +

        } catch (

        Exception ex) {
            throw new Exception("Failed to export zip file name" + ex.getMessage(), ex);
        }
    }

    @SuppressWarnings({
            "null", "unused"
    })
    public static String exportRefsetRf2Delta(final User user, final String refsetInternalId, final String type,
        final String languageId, final String fileNameDate, final String startEffectiveTime,
        final String transientEffectiveTime, final boolean exportMetadata, boolean withNames)
        throws Exception {
        
        // TODO: Turn this into a method variable
        final Set<String> dates = new HashSet<>();
        dates.add(transientEffectiveTime);
        
        if (startEffectiveTime != null) {
            dates.add(startEffectiveTime);
        }
        
        ExportHandler exporter = new ExportHandler();

        try (final TerminologyService service = new TerminologyService()) {
            
            final Refset refset = service.get(refsetInternalId, Refset.class);
            RefsetService.setRefsetPermissions(user, refset);

            if (refset == null) {
                throw new Exception("Refset Internal Id: " + refsetInternalId
                        + " does not exist in the RT2 database");
            }

            S3ConnectionWrapper.connectToAmazonS3();

            String deltaAwsVersionedPath = exporter.generateAwsBaseVersionPath(refset, type, dates);

            String deltaRt2VersionFileName = exporter.generateRt2VersionFileName(refset, type,
                    languageId, dates, exportMetadata, withNames);

            String deltaSnowGeneratedFileName =
                    exporter.generateSnowVersionFileName(refset, "DELTA", dates);

            // Check if delta file already exists
            if (!S3ConnectionWrapper.isInS3Cache(deltaAwsVersionedPath, deltaRt2VersionFileName)) {

                // determine all snapshot versions that will contribute to the delta
                List<Map<String, String>> versionMap = RefsetService.getSortedRefsetVersionList(refset, service, true);
                Map<String, String> versionToRefsetInternalId = new HashMap<>();
                List<String> versionsInScope = new ArrayList<>();
                
                for (Map<String, String> entry : versionMap) {
                    
                    String candidateVersion = entry.get("date");
                    
                    if (candidateVersion != null && candidateVersion.replaceAll("-", "").compareTo(startEffectiveTime) > 0
                            && candidateVersion.replaceAll("-", "").compareTo(transientEffectiveTime) <= 0) {
                        
                        versionsInScope.add(candidateVersion);
                        versionToRefsetInternalId.put(candidateVersion, entry.get("refsetInternalId"));
                    }
                }
                
                logger.debug("versionsInScope " + versionsInScope);

                // Local place to store snowBaseVersionFileName
                final Path localSnowGeneratedTempDir = Files.createTempDirectory("rt2LocalSnowGenerated-");

                // build fileContentsArray with contents from each snapshot version
                String headerLine = null;
                List<String> fileContentsArray = new ArrayList<>();
                
                for (String versionInScope : versionsInScope) {
                    
                    dates.clear();
                    dates.add(versionInScope.replaceAll("-", ""));

                    String awsVersionedPath = exporter.generateAwsBaseVersionPath(refset, "DELTA-SNAPSHOT", dates);

                    String rt2VersionFileName = exporter.generateRt2VersionFileName(refset, "SNAPSHOT", languageId, dates, exportMetadata, withNames);

                    // Snowstorm generated RF2 file
                    final String snowGeneratedFileName = exporter.generateSnowVersionFileName(refset, "SNAPSHOT", dates);

                    // Local Snowstorm generated Rf2 file name
                    final String localSnowGeneratedFilePath = localSnowGeneratedTempDir + File.separator + snowGeneratedFileName;

                    // Check if SnowS version file name does already exist in S3 cache
                    if (!S3ConnectionWrapper.isInS3Cache(awsVersionedPath, snowGeneratedFileName)) {
                        
                        // Base-SnowVersion file is not on S3, so generate it, and after downloading it, store it on S3
                        final String entityString = "{\"refsetIds\": [\"" + refset.getRefsetId() + "\"],  \"branchPath\": \"" + refset.getEdition().getBranch() + "/"
                                + versionInScope + "\", \"conceptsAndRelationshipsOnly\": false, \"filenameEffectiveDate\": \""
                                + versionInScope.replaceAll("-", "") + "\", \"legacyZipNaming\": false, \"type\": \"SNAPSHOT\", \"unpromotedChangesOnly\": false"
                                + (versionInScope == null ? "" : ",  \"startEffectiveTime\": \"" + versionInScope.replaceAll("-", "") + "\"")
                                + (versionInScope == null ? "" : ",  \"transientEffectiveTime\": \"" + versionInScope.replaceAll("-", "") + "\"") + "}";

                        logger.info("generating file from snowstorm" + entityString);
                        
                        // Generate on SnowS
                        final String snowGeneratedFileUrl = exporter.generateSnowVersionFile(entityString);
                        logger.debug("Downloading file from snowstorm, " + localSnowGeneratedFilePath);
                        
                        // Download file from SnowS
                        exporter.downloadSnowGeneratedFile(snowGeneratedFileUrl, localSnowGeneratedFilePath);
                        logger.debug("uploading snowstorm genned file to S3");

                        // store file one s3
                        S3ConnectionWrapper.uploadToS3(awsVersionedPath, localSnowGeneratedTempDir.toString(), snowGeneratedFileName);
                        
                    } else {

                        logger.info("Downloading snowstorm genned file from S3, " + snowGeneratedFileName);
                        S3ConnectionWrapper.downloadSnowFromS3(awsVersionedPath, snowGeneratedFileName, localSnowGeneratedFilePath);
                    }

                    // append the contents of this snapshot file to the fileContentsArray
                    FileUtility.unzip(localSnowGeneratedFilePath, localSnowGeneratedFilePath.replace(".zip", ""));
                    String fileNamePath = localSnowGeneratedFilePath.replace(".zip", "")
                            + File.separator + "SnomedCT_Export" + File.separator + "Snapshot" + File.separator + "Refset" + File.separator + "Content" + File.separator;
                    String[] files = new File(fileNamePath).list();

                    if (files != null) {
                        
                        if (withNames) {
                            
                            final String snowGeneratedRf2FilePath = fileNamePath + files[0];
                            final String rf2FileName = snowGeneratedRf2FilePath.substring(
                                    snowGeneratedRf2FilePath.lastIndexOf(File.separator) + 1);
                            final String builderRf2FilePath = fileNamePath + files[0] + ".names";

                            Refset specificRefset = service.findSingle("id:" + versionToRefsetInternalId.get(versionInScope), Refset.class, null);
                            appendNamesToRf2(specificRefset, snowGeneratedRf2FilePath, builderRf2FilePath, languageId);

                            File origFile = new File(snowGeneratedRf2FilePath);
                            
                            if (origFile.exists()) {
                                origFile.delete();
                            }
                            
                            File namesFile = new File(builderRf2FilePath);
                            
                            if (namesFile.exists()) {
                                namesFile.renameTo(origFile);
                            }
                            
                            withNames = false;
                        }
                        
                        fileContentsArray.addAll(FileUtility.readFileToArray(fileNamePath + files[0]));
                    }
                    
                    logger.debug("fileContentsArray after versionInScope " + fileContentsArray.size() + " " + versionInScope);
                    logger.debug("****@@@@@@@@@@@@@@@@@@@@@ fileContentsArray: " + fileContentsArray);

                    // If first file, store header so can print it later
                    if (headerLine == null) {
                        
                        for (String line : fileContentsArray) {
                            
                            if (line.toLowerCase().startsWith("id")) {
                                
                                headerLine = line;
                                break;
                            }
                        }
                    }
                }

                // Processed all intermediate files - put in a set to remove duplicates from fileContents
                Set<String> fileContentsSet = new HashSet<>(fileContentsArray);
                
                // sort fileContents
                List<String> fileContentsArrayList = new ArrayList<>(fileContentsSet);
                Collections.sort(fileContentsArrayList);
                
                // if the files were empty then print out an empty file with just the header line
                if (headerLine == null) {
                    
                    final String separator = "\t";
                    headerLine = "id" + separator + "effectiveTime" + separator + "active" + separator + "moduleId" + separator + "refsetId" + separator + "referencedComponentId";
                }

                // write fileContents to file
                try {
                    
                    FileOutputStream fos = new FileOutputStream(localSnowGeneratedTempDir.toString() + File.separator + deltaSnowGeneratedFileName);
                    BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));

                    // Write header onto delta file
                    bw.write(headerLine);
                    bw.newLine();

                    for (String line : fileContentsArrayList) {

                        // Only print the header line once... Was done above
                        if (line.toLowerCase().startsWith("id")) {
                            continue;
                        }

                        bw.write(line);
                        bw.newLine();
                    }

                    bw.close();
                    fos.close();

                } catch (IOException e) {
                    e.printStackTrace();
                }

                logger.debug("converting snowstorm genned file to RT2 format");
                // Have access to localSnowGeneratedFilePath from which rt2 will generate the export file
                generateRt2ExportFile(refset, localSnowGeneratedTempDir.toString() + File.separator + deltaSnowGeneratedFileName, deltaRt2VersionFileName, exportMetadata, withNames, languageId);

                logger.debug("uploading snowstorm genned file to S3");
                
                // store file on s3
                S3ConnectionWrapper.uploadToS3(deltaAwsVersionedPath, EXPORT_FILE_DIR, deltaRt2VersionFileName);
                FileUtility.deleteDirectory(localSnowGeneratedTempDir.toFile());

            } else {

                if (!Files.exists(Path.of(EXPORT_FILE_DIR + deltaRt2VersionFileName))) {
                    
                    logger.debug("Downloading RT2 snapshot genned file from S3");
                    S3ConnectionWrapper.downloadSnowFromS3(deltaAwsVersionedPath, deltaRt2VersionFileName, EXPORT_FILE_DIR + deltaRt2VersionFileName);
                }
            }
            
            // if download is from RT2 server
            ServletUriComponentsBuilder builder = ServletUriComponentsBuilder.fromCurrentContextPath();
            return EXPORT_DOWNLOAD_URL + deltaRt2VersionFileName;

        } catch (Exception ex) {
            throw new Exception("Failed to export delta zip file name: " + ex.getMessage(), ex);
        }
    }

    private static String generateRt2ExportFile(final Refset refset, final String localSnowGeneratedFilePath, final String rt2VersionFileName,
        final boolean exportMetadata, final boolean appendNames, final String languageId) throws Exception {

        // Generate the Rt2 version of refset RF2 Zip file
        final Path builderDirectoryTempDir = Files.createTempDirectory("rt2Builder-");

        logger.debug("creating builder temp dir: " + builderDirectoryTempDir.toString());

        // Unzip the download if snapshot
        List<String> sourceFiles = new ArrayList<>();
        if (!localSnowGeneratedFilePath.contains("DELTA")) {
            sourceFiles = unzipFiles(localSnowGeneratedFilePath, builderDirectoryTempDir.toString());
        } else {
            sourceFiles.add(localSnowGeneratedFilePath);
        }

        logger.debug("unzipped source files: " + ModelUtility.toJson(sourceFiles));

        if (sourceFiles.size() != 1) {
            throw new Exception("Unexpected number of files generated by Snowstorm Export RF2: " + sourceFiles.size());
        }

        // If Rf2WithNames selected, append the names to the refset file
        if (appendNames) {

            final String snowGeneratedRf2FilePath = sourceFiles.iterator().next();
            final String rf2FileName = snowGeneratedRf2FilePath.substring(snowGeneratedRf2FilePath.lastIndexOf(File.separator) + 1);
            final String builderRf2FilePath = builderDirectoryTempDir.toString() + File.separator + rf2FileName;

            appendNamesToRf2(refset, snowGeneratedRf2FilePath, builderRf2FilePath, languageId);

            sourceFiles.clear();
            sourceFiles.add(builderRf2FilePath);
        }

        // if exportMetadata requested, add it
        if (exportMetadata) {
            sourceFiles.add(exportRefsetMetadata(refset, builderDirectoryTempDir));
        }

        logger.debug("ready to be zipped source files: " + ModelUtility.toJson(sourceFiles));

        // zip the files together
        zipFiles(sourceFiles, EXPORT_FILE_DIR + rt2VersionFileName);

        // Delete directory structure and original zip
        FileUtility.deleteDirectory(builderDirectoryTempDir.toFile());

        return EXPORT_FILE_DIR;
    }

    private static void appendNamesToRf2(final Refset refset, final String origFilePath, String newFileWithNamesPath, final String languageId) throws Exception {

        // Move rf2 file to a tmp (as we create new one below). Update sourceFiles accordingly
        logger.debug("Appending descriptions to RF2 file");

        // Get member cache
        List<Concept> conceptsNotInCache = new ArrayList<>();
        Map<String, Concept> members = new HashMap<>();

        // Read through file and identify those concepts not in cache or don't have all requisite languages populated
        try (BufferedReader br = new BufferedReader(new FileReader(new File(origFilePath)))) {

            String extractedLine = br.readLine();
            extractedLine = br.readLine();

            while (extractedLine != null && !extractedLine.trim().isEmpty()) {

                String conceptId = extractedLine.split("\t")[REFEST_RF2_CONCEPTID_COLUMN];

                // TODO: Also check doesn't have all needed languages
                if (!members.containsKey(conceptId) || members.get(conceptId).getDescriptions().isEmpty()) {
                    
                    Concept concept = new Concept();
                    concept.setCode(conceptId);
                    conceptsNotInCache.add(concept);
                }

                extractedLine = br.readLine();

                if (conceptsNotInCache.size() == CONCEPT_DESCRIPTIONS_PER_CALL || extractedLine == null) {

                    populateAllLanguageDescriptions(refset, conceptsNotInCache);

                    // Populate Members cache with data
                    for (Concept concept : conceptsNotInCache) {

                        if (!members.containsKey(concept.getCode())) {
                            members.put(concept.getCode(), concept);
                        } else {

                            members.get(concept.getCode()).setDescriptions(concept.getDescriptions());
                        }
                    }

                    conceptsNotInCache.clear();
                }
            }

            br.close();
        }

        FileWriter fw = new FileWriter(new File(newFileWithNamesPath));

        // Get descriptions for those not cached or not cached with all languages. Read through file 2nd time and write each line to new file while appending selected name
        try (BufferedReader br = new BufferedReader(new FileReader(new File(origFilePath)))) {

            // get the header line so we can add the new description header
            String extractedLine = br.readLine();

            for (Map<String, String> defaultLanguages : refset.getEdition().getFullyQualifiedLanguageRefsets()) {

                if (languageId.equals(defaultLanguages.get("qualifiedLanguageRefset"))) {
                    fw.write(extractedLine + "\t" + defaultLanguages.get("qualifiedLanguageCode") + "\n");
                }
            }
            
            // get the first line of concepts
            extractedLine = br.readLine();

            while (extractedLine != null) {

                String conceptId = extractedLine.split("\t")[REFEST_RF2_CONCEPTID_COLUMN];

                // TODO: How to determine which language
                if (!members.containsKey(conceptId)) {
                    throw new Exception("Didn't have concept populated with descriptions yet");
                }

                boolean written = false;
                int i = 0;
                String fallbackDescription = null;

                while (i < members.get(conceptId).getDescriptions().size()) {

                    final Map<String, String> description = members.get(conceptId).getDescriptions().get(i);

                    // if this isn't the description we want
                    if (description == null || !languageId.equals(description.get(LANGUAGE_ID))) {

                        // If this is the English PT add it as a fallback to use if the language we want isn't on this concept
                        if (description != null && description.get(LANGUAGE_ID).equals("900000000000509007PT")) {
                            fallbackDescription = extractedLine + "\t" + description.get(DESCRIPTION_TERM);
                        }

                        i++;
                        continue;
                    }

                    fw.write(extractedLine + "\t" + description.get(DESCRIPTION_TERM));
                    written = true;
                    break;
                }

                // If the language we want isn't on this concept try to use the English fallback
                if (!written && fallbackDescription != null) {

                    fw.write(fallbackDescription);
                    written = true;

                } else if (!written) {
                    throw new Exception("Not seeing the expected descriptions for member: " + conceptId + " as have these descriptions: " + members.get(conceptId).getDescriptions());
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
     * @param limit the number of results per page
     * @param searchAfter the member to search after
     * @param branchPath the branch and version of the refset
     * @return the raw resultString
     * @throws Exception the exception
     */
    private static String getMemberSctids(final String refsetId, final int limit, final String searchAfter, final String branchPath) throws Exception {
        
        final String pagingParams = "&limit=" + limit + "&searchAfter=" + searchAfter;

        String url = SnowstormConnection.BASE_URL + "" + branchPath + "/members?referenceSet=" + refsetId + "&" + pagingParams;

        logger.debug("Snowstorm URL: " + url);

        try (Response response = SnowstormConnection.getResponse(url)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                throw new Exception(
                        "call to url '" + url + "' wasn't successful. " + response.toString());
            }

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
    public static String exportRefsetSctidList(final String refsetInternalId, final boolean exportMetadata) throws Exception {

        int limit = ELASTICSEARCH_MAX_RECORD_LENGTH;
        boolean hasMorePages = true;
        StringBuilder fileLines = new StringBuilder();
        String zipOutputPath = EXPORT_FILE_DIR;
        String refsetFileName = "";
        String sctidsFilePath = "";
        List<String> sourceFiles = new ArrayList<>();
        Path tempDirectoryPath = null;

        // get the refset and member information
        try (final TerminologyService service = new TerminologyService()) {

            final Refset refset = service.get(refsetInternalId, Refset.class);

            if (refset == null) {
                throw new Exception("Refset Internal Id: " + refsetInternalId + " does not exist in the RT2 database");
            }

            refsetFileName = "refset_" + refset.getRefsetId() + "_" + getRefsetAsOfDate(refset) + "_member_ids.txt";
            zipOutputPath += refsetFileName.replace(".txt", ".zip");
            tempDirectoryPath = Files.createTempDirectory("sctidList-" + refsetFileName.replace(".txt", ""));
            sctidsFilePath = tempDirectoryPath.toString() + File.separator + refsetFileName;

            logger.debug("SCTID txt output path = " + sctidsFilePath);
            logger.debug("zip output path = " + zipOutputPath);

            if (exportMetadata) {
                sourceFiles.add(exportRefsetMetadata(refset, tempDirectoryPath));
            }

            String searchAfter = "";

            while (hasMorePages) {

                final String resultString = getMemberSctids(refset.getRefsetId(), limit, searchAfter, getBranchPath(refset));
                //logger.debug("exportRefsetSctidList: resultString" + resultString);
                final ObjectMapper mapper = new ObjectMapper();
                final JsonNode root = mapper.readTree(resultString);
                final JsonNode items = root.get("items");
                final Iterator<JsonNode> iterator = items.iterator();
                logger.debug("exportRefsetSctidList items.size(): " + items.size());
                
                
                if (root.get("searchAfter") != null) {
                    searchAfter = root.get("searchAfter").asText();
                } else {
                    searchAfter = "";
                }
                
                logger.debug("exportRefsetSctidList searchAfter: " + searchAfter);

                if (items.size() < limit) {
                    hasMorePages = false;
                }
                
                logger.debug("exportRefsetSctidList hasMorePages: " + hasMorePages);

                while (iterator.hasNext()) {

                    final JsonNode item = iterator.next();
                    final String conceptId = (item.get("referencedComponentId").asText());
                    fileLines.append(conceptId + "\n");
                }

            }

        } catch (Exception ex) {
            throw new Exception("Could not get refset member data from snowstorm: " + ex.getMessage(), ex);
        }

        // print the sctids file
        try (final FileOutputStream sctidsFileOutputStream = new FileOutputStream(sctidsFilePath);
            
            final OutputStreamWriter sctidsOutputStreamWriter = new OutputStreamWriter(sctidsFileOutputStream, "UTF-8");
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
        String zippedFileUrl = EXPORT_DOWNLOAD_URL + refsetFileName.replace(".txt", ".zip");

        return zippedFileUrl;
    }

    public static String exportFreeset(final String refsetInternalId) throws Exception {

        StringBuilder fileLines = new StringBuilder();
        String sctidsOutputPath = "";
        String zipOutputPath = EXPORT_FILE_DIR;
        String refsetFileName = "";
        List<String> sourceFiles = new ArrayList<>();
        Path tempDirectoryPath = null;

        try (final TerminologyService service = new TerminologyService()) {

            final Refset refset = service.get(refsetInternalId, Refset.class);

            if (refset == null) {
                throw new Exception("Refset Internal Id: " + refsetInternalId
                        + " does not exist in the RT2 database");
            }

            service.close();

            refsetFileName =
                    "freeset_" + refset.getRefsetId() + "_" + getRefsetAsOfDate(refset) + ".txt";
            tempDirectoryPath =
                    Files.createTempDirectory("freeset-" + refsetFileName.replace(".txt", ""));
            zipOutputPath += refsetFileName.replace(".txt", ".zip");
            sctidsOutputPath = tempDirectoryPath.toString() + File.separator + refsetFileName;

            logger.debug("SCTID freeset txt output path = " + sctidsOutputPath);
            logger.debug("zip freeset output path = " + zipOutputPath);

            logger.debug("*********** exportFreeset: refsetInternalId: " + refsetInternalId);

            final long start = System.currentTimeMillis();
            ConceptResultList results = new ConceptResultList();

            List<Concept> concepts =
                    getAllRefsetMembers(refsetInternalId, "", new ArrayList<Concept>());
            Collections.sort(concepts,
                    Comparator.comparing((Concept concept) -> Long.parseLong(concept.getCode())));

            results.setTimeTaken(System.currentTimeMillis() - start);
            results.setItems(concepts);

            fileLines.append("ConceptID").append("\t");
            fileLines.append("Active").append("\t");
            fileLines.append("FSN").append("\t");
            fileLines.append("USPreferredTerm").append("\t");
            fileLines.append("\r\n");

            for (Concept cpt : results.getItems()) {

                String fsn = "";
                for (Map<String, String> entry : cpt.getDescriptions()) {
                    fsn = entry.get("fsn");
                }

                fileLines.append(cpt.getCode()).append("\t");
                fileLines.append(cpt.isActive() ? "1" : "0").append("\t");
                fileLines.append(fsn).append("\t");
                fileLines.append(cpt.getName());
                fileLines.append("\r\n");
            }

            // print the sctids file
            try (final FileOutputStream sctidsFileOutputStream =
                    new FileOutputStream(sctidsOutputPath);
                    final OutputStreamWriter sctidsOutputStreamWriter =
                            new OutputStreamWriter(sctidsFileOutputStream, "UTF-8");
                    final PrintWriter freesetWriter = new PrintWriter(sctidsOutputStreamWriter);) {
                freesetWriter.print(fileLines);
            }

        } catch (Exception ex) {
            throw new Exception("Could not create freeset txt file: " + ex.getMessage(), ex);
        }

        // zip the files together
        sourceFiles.add(sctidsOutputPath);
        zipFiles(sourceFiles, zipOutputPath);

        // Delete temp directory structure and files
        FileUtility.deleteDirectory(tempDirectoryPath.toFile());

        // if download is from RT2 server
        ServletUriComponentsBuilder builder = ServletUriComponentsBuilder.fromCurrentContextPath();
        String zippedFileUrl = EXPORT_DOWNLOAD_URL + refsetFileName.replace(".txt", ".zip");

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

        if (refset.getVersionDate() != null) {
            fileLines.append("Refset Version Date" + separator + DateUtility.formatDate(
                    refset.getVersionDate(), DateUtility.DATE_FORMAT_REVERSE, null) + "\n");
        } else {
            fileLines.append("Refset Version Date" + separator + "\n");
        }

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
     * Get the branch and version path for a refset.
     *
     * @param refset the refset
     * @return the branch and version path
     * @throws Exception the exception
     */
    public static String getBranchPath(final Refset refset) throws Exception {
        return RefsetService.getBranchPath(refset);
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
    public static void populateAllLanguageDescriptions(final Refset refset, final List<Concept> conceptsToProcess) throws MalformedURLException, Exception {

        final StringBuffer conceptIds = new StringBuffer();

        // Create Snowstorm URL
        final String url = SnowstormConnection.BASE_URL + getBranchPath(refset) + "/descriptions?limit=" + ELASTICSEARCH_MAX_RECORD_LENGTH;

        boolean firstTime = true;
        
        for (Concept concept : conceptsToProcess) {
            
            if (firstTime) {
                firstTime = false;
            } else {
                conceptIds.append(",");
            }
            
            conceptIds.append(concept.getCode());
        }

        //logger.debug("Get Member Descriptions URL: " + url + "&conceptIds=" + conceptIds);

        // Call Snowstorm
        try (final Response response = SnowstormConnection.getResponse(url + "&conceptIds=" + conceptIds)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                throw new Exception("call to url '" + url + "' wasn't successful. " + response.toString());
            }

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

            final List<String> nonDefaultPreferredTerms = identifyNonDefaultPreferredTerms(refset.getEdition());

            for (String conceptId : conceptDescriptionNodes.keySet()) {
                
                Set<JsonNode> descriptionNodes = conceptDescriptionNodes.get(conceptId);
                Set<Map<String, String>> descriptions = new HashSet<>();

                descriptions = processDescriptionNodes(descriptionNodes, refset.getEdition().getDefaultLanguageRefsets(), nonDefaultPreferredTerms);

                final List<Map<String, String>> sortedDescriptions = sortConceptDescriptions(conceptId, descriptions, refset, nonDefaultPreferredTerms);
                conceptDescriptionMap.put(conceptId, sortedDescriptions);
            }

            // Populate concept with description-based data
            for (Concept concept : conceptsToProcess) {
                
                List<Map<String, String>> descriptions = conceptDescriptionMap.get(concept.getCode());

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
     * Populates concepts with information on if they have children.
     *
     * @param refset the refset who's members are being retrieved
     * @param conceptsToProcess the concepts to add hasChild info to
     * @throws MalformedURLException the malformed URL exception
     * @throws Exception the exception
     */
    public static void populateConceptLeafStatus(final Refset refset,
        final List<Concept> conceptsToProcess) throws MalformedURLException, Exception {

        final StringBuffer conceptIds = new StringBuffer();

        // Create Snowstorm URL
        final String url = SnowstormConnection.BASE_URL + getBranchPath(refset)
                + "/concepts?limit=3000&includeLeafFlag=true&form=inferred";

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
        //logger.debug("Get Concept Leaf Status URL: " + url + "&conceptIds=" + conceptIds);

        try (final Response response = SnowstormConnection.getResponse(url + "&conceptIds=" + conceptIds)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                throw new Exception("call to url '" + url + "' wasn't successful. " + response.toString());
            }

            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

            final JsonNode allConceptNodes = root.get("items");
            final Iterator<JsonNode> conceptIterator = allConceptNodes.iterator();
            final HashMap<String, JsonNode> conceptNodes = new HashMap<>();

            // Map concept info
            while (conceptIterator.hasNext()) {

                final JsonNode conceptNode = conceptIterator.next();

                String conceptId = conceptNode.get("conceptId").asText();
                conceptNodes.put(conceptId, conceptNode);
            }

            // Populate concept with child info
            for (Concept concept : conceptsToProcess) {

                JsonNode conceptNode = conceptNodes.get(concept.getCode());

                if (conceptNode == null || conceptNode.size() == 0) {

                    logger.debug("Concept info not retrieved for concept " + concept.getCode());
                    continue;
                }

                if (conceptNode.has("isLeafInferred")) {
                    concept.setHasChildren(!conceptNode.get("isLeafInferred").asBoolean());
                }
            }

        } catch (Exception ex) {
            logger.error("Could not retrieve concept leaf info " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * Get ready to search concepts.
     *
     * @param user the user
     * @param refsetInternalId the internal refset ID
     * @param searchParameters the search parameters
     * @param searchRefsetMembers Should the search be for members of the refset
     *            or for all concepts
     * @return the concept result list
     * @throws MalformedURLException the malformed URL exception
     * @throws Exception the exception
     */
    public static ConceptResultList prepareConceptSearch(final User user, final String refsetInternalId,
        final SearchParameters searchParameters, final boolean searchRefsetMembers)
        throws MalformedURLException, Exception {

        ConceptResultList concepts = new ConceptResultList();

        try (final TerminologyService service = new TerminologyService()) {

            final Refset refset = getRefset(user, service, refsetInternalId);
            final String branchPath = getBranchPath(refset);
            final String cacheString = refset.getRefsetId() + searchParameters.toString() + searchRefsetMembers;
            final Map<String, ConceptResultList> branchCache = getCacheForConceptsCall(branchPath);
            
            // check if the concept call has been cached
            if (branchCache.containsKey(cacheString)) {
                
                logger.debug("####### prepareConceptSearch USING CACHE");
                return branchCache.get(cacheString);
            }

            concepts = searchConcepts(refset, searchParameters, searchRefsetMembers);

            if (searchRefsetMembers) {
                
                final List<Concept> allConceptList = concepts.getItems();
                final List<Concept> conceptsToProcess = new ArrayList<>();
                int i = 0;
                
                for (final Concept concept : allConceptList) {
                    
                    conceptsToProcess.add(concept);
                    i++;
                    
                    if (conceptsToProcess.size() == CONCEPT_DESCRIPTIONS_PER_CALL || i == allConceptList.size()) {
                        populateAllLanguageDescriptions(refset, concepts.getItems());
                    }
                }
            }

            branchCache.put(cacheString, concepts);
            conceptsCallCache.put(branchPath, branchCache);
            //logger.debug("******** prepareConceptSearch: results: " + ModelUtility.toJson(concepts));
        }

        return concepts;
    }

    /**
     * Get the ancestor path for a list of conceptIDs.
     *
     * @param refset the refset
     * @param concepts the list of concepts ancestor paths are being generated for
     * @return the concept result list
     * @throws MalformedURLException the malformed URL exception
     * @throws Exception the exception
     */
    public static List<Concept> getConceptAncestors(final Refset refset,
        final List<Concept> concepts) throws MalformedURLException, Exception {

        String conceptIds = "";
        final ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
        lookupParameters.setNonDefaultPreferredTerms(identifyNonDefaultPreferredTerms(refset.getEdition()));
        lookupParameters.setGetDescriptions(true);
        final String branchPath = getBranchPath(refset);
        final List<Concept> inactiveConcepts = new ArrayList<>();
        final String cacheString = refset.getRefsetId() + concepts.toString();
        final Map<String, List<Concept>> branchCache = getCacheForTaxonomySearchAncestors(branchPath);
        
        // check if the members call has been cached
        if (branchCache.containsKey(cacheString)) {
            
            logger.debug("####### getConceptAncestors USING CACHE");
            return branchCache.get(cacheString);
        }

        for (Concept concept : concepts) {

            // snowstorm does not allow searching for inactive concepts so
            // remove them from the results.
            if (!concept.isActive()) {

                logger.debug("Inactive concept in taxonomy search: " + concept.getCode());
                inactiveConcepts.add(concept);
                continue;
            } else {

                conceptIds += concept.getCode() + ",";
            }
        }

        for (Concept inactiveConcept : inactiveConcepts) {
            concepts.remove(inactiveConcept);
        }

        conceptIds = StringUtils.removeEnd(conceptIds, ",");

        // Create Snowstorm URL
        final String url = SnowstormConnection.BASE_URL + "browser/" + branchPath
                + "/concepts/ancestor-paths?conceptIds=" + conceptIds;

        // Call Snowstorm
        logger.debug("Get Concept Ancestors URL: " + url);

        try (final Response response = SnowstormConnection.getResponse(url)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                throw new Exception(
                        "call to url '" + url + "' wasn't successful. " + response.toString());
            }

            // read the results of the call for ancestors for many concepts
            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());
            Iterator<JsonNode> iterator = root.iterator();

            // loop thru each concept to get the ancestor path for it
            while (iterator.hasNext()) {

                final JsonNode conceptNode = iterator.next();
                final String nodeConceptId = conceptNode.get("conceptId").asText();
                final JsonNode ancestorPathNode = conceptNode.get("ancestorPath");

                // get the ancestor list and reverse the order so the taxonomy
                // root is first
                final ConceptResultList ancestorList = populateConcepts(ancestorPathNode, refset, lookupParameters);
                final List<Concept> parents = ancestorList.getItems();
                Collections.reverse(parents);

                // pick out the concept that we are going to load the ancestors into
                final Concept concept = concepts.stream()
                        .filter(filterConcept -> nodeConceptId.equals(filterConcept.getCode()))
                        .findFirst().orElse(null);

                // load the ancestors into the concept
                if (concept != null) {
                    concept.setParents(parents);
                } else {
                    logger.info("Couldn't find concept " + nodeConceptId + " to load ancestors into.");
                }
            }
        }

        branchCache.put(cacheString, concepts);
        taxonomySearchAncestorsCache.put(branchPath, branchCache);

        return concepts;
    }

    /**
     * Search concepts.
     *
     * @param refset the refset
     * @param searchParameters the search parameters
     * @param searchRefsetMembers Should the search be for members of the refset or for all concepts
     * @return the concept result list
     * @throws MalformedURLException the malformed URL exception
     * @throws Exception the exception
     */
    public static ConceptResultList searchConcepts(final Refset refset, final SearchParameters searchParameters, final boolean searchRefsetMembers) throws MalformedURLException, Exception {

        ConceptResultList members = new ConceptResultList();
        final ObjectMapper mapper = new ObjectMapper();
        final String encodedCaret = "%5E";
        final String encodedSpace = "%20";

        // Create Snowstorm URL
        String url = SnowstormConnection.BASE_URL + getBranchPath(refset) + "/concepts?&offset=0&limit=" + (ELASTICSEARCH_MAX_RECORD_LENGTH - 1);

        // if this search is for editing then get the concept leaf information
        if (searchParameters.isEditing()) {
            url += "&includeLeafFlag=true&form=inferred";
        }

        boolean searchEcl = false;

        // if the query is not an ID then see if it passes ECL syntax
        if (searchParameters.getQuery() != null && !searchParameters.getQuery().matches("\\d*")) {

            final String eclUrl = SnowstormConnection.BASE_URL + "util/ecl-string-to-model";
            final String body = StringUtility.encodeValue(searchParameters.getQuery());

            logger.debug("searchConcepts ECL Parse URL: " + eclUrl + "; body: " + body);

            try (final Response response = SnowstormConnection.postResponse(eclUrl, body)) {
                // if the query parses as ECL then search by ecl
                if (response.getStatusInfo().getFamily() == Family.SUCCESSFUL) {
                    searchEcl = true;
                }
            }
        }

        // set the appropriate way to search
        if (!searchEcl) {

            url += "&term=" + StringUtility
                    .encodeValue(searchParameters.getQuery());

            if (searchRefsetMembers) {
                url += "&ecl=" + encodedCaret + refset.getRefsetId();
            }

        } else {

            url += "&ecl=" + StringUtility.encodeValue("(" + searchParameters.getQuery() + ")");

            if (searchRefsetMembers) {
                url += encodedSpace + "AND" + encodedSpace + encodedCaret + refset.getRefsetId();
            }
        }

        String searchAfter = "";
        boolean hasMorePages = true;
        final long start = System.currentTimeMillis();

        while (hasMorePages) {
   
            // Call Snowstorm
            logger.debug("searchConcepts URL: " + url + searchAfter);
    
            try (final Response response = SnowstormConnection.getResponse(url + searchAfter)) {
    
                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    
                    hasMorePages = false;
                    throw new Exception("call to url '" + url + "' wasn't successful. " + response.toString());
                }
    
                final String resultString = response.readEntity(String.class);
                final JsonNode root = mapper.readTree(resultString.toString());
                JsonNode conceptNodeBatch = root.get("items");
                
                if (root.get("searchAfter") != null) {
                    searchAfter = "&searchAfter=" + root.get("searchAfter").asText();
                }
                
                // if the search returned results set the total
                if (!members.isTotalKnown()) {
                    
                    members.setTotal(root.get("total").asInt());
                    members.setTotalKnown(true);
                }
    
                if (conceptNodeBatch.size() == 0 || conceptNodeBatch.size() + members.getItems().size() >= members.getTotal()) {
                    hasMorePages = false;
                }
                
                if (System.currentTimeMillis() - start > TIMEOUT_MILLISECOND_THRESHOLD) {
                    hasMorePages = false;
                }
    
                if (conceptNodeBatch.size() != 0 && !conceptNodeBatch.get(0).has("error")) {
    
                    final Iterator<JsonNode> itemIterator = conceptNodeBatch.iterator();
                    final ArrayList<Concept> conceptBatch = new ArrayList<>();
    
                    // parse items to retrieve matching concepts
                    while (itemIterator.hasNext()) {
    
                        final JsonNode conceptNode = itemIterator.next();
    
                        Concept concept = new Concept();
                        concept.setActive(conceptNode.get("active").asBoolean());
                        concept.setId(conceptNode.get("id").asText());
                        concept.setCode(conceptNode.get("id").asText());
    
                        if (!conceptNode.get("definitionStatus").asText().equals("PRIMITIVE")) {
                            concept.setDefined(true);
                        } else {
                            concept.setDefined(false);
                        }
    
                        if (conceptNode.get("pt") != null) {
                            concept.setName(conceptNode.get("pt").get("term").asText());
                        }
    
                        if (conceptNode.has("isLeafInferred")) {
                            concept.setHasChildren(!conceptNode.get("isLeafInferred").asBoolean());
                        }
    
                        setConceptPermissions(concept);
                        concept.setMemberOfRefset(searchRefsetMembers);
                        processIntensionalDefinitionException(refset, concept);
                        conceptBatch.add(concept);
                    }
    
                    if (searchParameters.isEditing()) {
                        populateMembershipInformation(refset, conceptBatch);
                    }
                    
                    members.getItems().addAll(conceptBatch);
                }
                
            } catch (Exception ex) {

                logger.error("searchConcepts Could not retrieve concepts matching term: " + ex.getMessage());
                ex.printStackTrace();
            }
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
                } else if ("900000000000550004".equals(descriptionNode.get("typeId").asText())) {
                    typeName = "DEF";
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
     * Get a taxonomy tree cache collection for a branch path.
     *
     * @param branchPath the branch path of cache collection to return
     * @return the cache collection
     * @throws Exception the exception
     */
    public static Map<String, ConceptResultList> getCacheForTree(final String branchPath) throws Exception {
        
        if (treeCache.containsKey(branchPath)) {
            return treeCache.get(branchPath);
        } else {
            return new HashMap<>();
        }
    }
    
    /**
     * Get a member ancestors cache collection for a branch path.
     *
     * @param branchPath the branch path of cache collection to return
     * @return the cache collection
     * @throws Exception the exception
     */
    public static Map<String, Set<String>> getCacheForMemberAncestors(final String branchPath) throws Exception {
        
        if (ancestorsCache.containsKey(branchPath)) {
            return ancestorsCache.get(branchPath);
        } else {
            return new HashMap<>();
        }
    }
    
    /**
     * Get a taxonomy ancestors cache collection for a branch path.
     *
     * @param branchPath the branch path of cache collection to return
     * @return the cache collection
     * @throws Exception the exception
     */
    public static Map<String, List<Concept>> getCacheForTaxonomySearchAncestors(final String branchPath) throws Exception {
        
        if (taxonomySearchAncestorsCache.containsKey(branchPath)) {
            return taxonomySearchAncestorsCache.get(branchPath);
        } else {
            return new HashMap<>();
        }
    }
    
    /**
     * Get a concept detail cache collection for a branch path.
     *
     * @param branchPath the branch path of cache collection to return
     * @return the cache collection
     * @throws Exception the exception
     */
    public static Map<String, Concept> getCacheForConceptDetails(final String branchPath) throws Exception {
        
        if (conceptDetailsCache.containsKey(branchPath)) {
            return conceptDetailsCache.get(branchPath);
        } else {
            return new HashMap<>();
        }
    }
    
    /**
     * Get a member call cache collection for a branch path.
     *
     * @param branchPath the branch path of cache collection to return
     * @return the cache collection
     * @throws Exception the exception
     */
    public static Map<String, ConceptResultList> getCacheForConceptsCall(final String branchPath) throws Exception {
        
        if (conceptsCallCache.containsKey(branchPath)) {
            return conceptsCallCache.get(branchPath);
        } else {
            return new HashMap<>();
        }
    }
    
    /**
     * Clear all caches related to refset members.
     *
     * @param branchPath the branch to clear the cache collections for
     * @throws Exception the exception
     */
    public static void clearAllMemberCaches(final String branchPath) throws Exception {

        if (branchPath != null) {
            
            logger.debug(" Clearing caches for branch path: " + branchPath);
            
            conceptsCallCache.remove(branchPath);
            conceptDetailsCache.remove(branchPath);
            taxonomySearchAncestorsCache.remove(branchPath);
            treeCache.remove(branchPath);
            ancestorsCache.remove(branchPath);
            

        } else {
            
            logger.debug(" Clearing caches for all branches");
            
            conceptsCallCache.clear();
            conceptDetailsCache.clear();
            taxonomySearchAncestorsCache.clear();
            treeCache.clear();
            ancestorsCache.clear();
        }
        
    }
    
    /**
     * Copy all branch cache collections to another branch.
     *
     * @param fromBranchPath the branch to copy the cache collections from
     * @param toBranchPath the branch to copy the cache collections to
     * @param changeEditPropertyFrom should cache keys containing the edit property be change. Null for no change, or "true" or "false" for the current state of the property 
     * @throws Exception the exception
     */
    public static void copyAllMemberCachesToBranch(final String fromBranchPath, final String toBranchPath, final String changeEditPropertyFrom) throws Exception {

        if (fromBranchPath == null || fromBranchPath.equals("") || toBranchPath == null || toBranchPath.equals("")) {
            return;
        }
        
        boolean changeEditProperty = false;
        String changeEditPropertyTo = "true";
        
        if (changeEditPropertyFrom != null && !changeEditPropertyFrom.equals("")) {
            
            changeEditProperty = true;
            
            if (changeEditPropertyFrom.equals("true")) {
                changeEditPropertyTo = "false";
            }
        }
        
        logger.debug("BRANCH CACHE copying from: " + fromBranchPath);
        logger.debug("BRANCH CACHE copying to: " + toBranchPath);
        
        if (conceptsCallCache.containsKey(fromBranchPath)) {
            
            Map<String, ConceptResultList> tempCache = conceptsCallCache.get(fromBranchPath);
            
            if (changeEditProperty) { 
                
                String cacheString = ModelUtility.toJson(tempCache);
                cacheString = cacheString.replace("\\\"editing\\\":" + changeEditPropertyFrom, "\\\"editing\\\":" + changeEditPropertyTo);
                tempCache = ModelUtility.fromJson(cacheString, new TypeReference<Map<String, ConceptResultList>>() {/**/});
            }
            
            conceptsCallCache.put(toBranchPath, tempCache);
        }
        
        if (conceptDetailsCache.containsKey(fromBranchPath)) {
            conceptDetailsCache.put(toBranchPath, conceptDetailsCache.get(fromBranchPath));
        }
        
        if (taxonomySearchAncestorsCache.containsKey(fromBranchPath)) {
            taxonomySearchAncestorsCache.put(toBranchPath, taxonomySearchAncestorsCache.get(fromBranchPath));
        }
        
        if (treeCache.containsKey(fromBranchPath)) {
            treeCache.put(toBranchPath, treeCache.get(fromBranchPath));
        }
        
        if (ancestorsCache.containsKey(fromBranchPath)) {
            ancestorsCache.put(toBranchPath, ancestorsCache.get(fromBranchPath));
        }
    }
    

    /**
     * Get the refset member concepts as a list.
     *
     * @param refset the refset who's members are being retrieved
     * @param nonDefaultPreferredTerms the non-default preferred terms
     * @return the refset member concepts
     * @throws Exception the exception
     */
    public static ConceptResultList getMemberList(final Refset refset, final List<String> nonDefaultPreferredTerms, final SearchParameters searchParameters) throws Exception {

        // 2 Snowstorm calls: 1) Memberlist and 2) Descriptions
        ConceptResultList members = new ConceptResultList();
        final String branchPath = getBranchPath(refset);
        final String cacheString = refset.getRefsetId() + searchParameters.toString() + "true";
        final Map<String, ConceptResultList> branchCache = getCacheForConceptsCall(branchPath);
        final String refsetId = refset.getRefsetId();
        
        // check if the members call has been cached
        if (branchCache.containsKey(cacheString)) {
            
            logger.debug("####### getMemberList USING CACHE");
            return branchCache.get(cacheString);
        }

        try {

            boolean notSearching = true;
            ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
            lookupParameters.setGetMembershipInformation(true);

            final List<Concept> conceptsToProcess = new ArrayList<>();

            if (searchParameters.getQuery() == null) {
                searchParameters.setQuery("");
            }
            
            ConceptResultList currentList = new ConceptResultList();
            // if search term is indicated, find members that match search term
            if (searchParameters.getQuery() != null && !searchParameters.getQuery().isEmpty()) {

                notSearching = false;
                currentList = searchConcepts(refset, searchParameters, true);
            } else {

                String searchAfter = "";
                final long start = System.currentTimeMillis();
                boolean hasMorePages = true; 
                final String acceptLanguage = SnowstormConnection.DEFAULT_ACCECPT_LANGUAGES;
                
                // when searching for members we only want concepts whose membership is active (though the concept itself can be inactive)
                final String url = SnowstormConnection.BASE_URL + getBranchPath(refset) + "/members?referenceSet=" + refsetId + "&active=true&offset=0&limit=" + ELASTICSEARCH_MAX_RECORD_LENGTH;
                
                while (hasMorePages) {
                    
                    logger.debug("Get Member List URL: " + url + searchAfter);
    
                    try (final Response response = SnowstormConnection.getResponse(url + searchAfter, acceptLanguage)) {
    
                        if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                            
                            hasMorePages = false;
                            throw new Exception("call to url '" + url + "' wasn't successful. " + response.toString());
                        }
    
                        final String resultString = response.readEntity(String.class);
    
                        // Only process payload if Rest call is successful
                        if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                            throw new Exception(Integer.toString(response.getStatus()));
                        }
    
                        final ObjectMapper mapper = new ObjectMapper();
                        final JsonNode root = mapper.readTree(resultString.toString());
                        JsonNode conceptNodeBatch = root.get("items");
                        
                        // if the search returned results set the total
                        if (!currentList.isTotalKnown()) {
                            
                            currentList.setTotal(root.get("total").asInt());
                            currentList.setTotalKnown(true);
                        }
                        
                        if (root.get("searchAfter") != null) {
                            searchAfter = "&searchAfter=" + root.get("searchAfter").asText();
                        }
                        
                        if (conceptNodeBatch.size() == 0 || conceptNodeBatch.size() + currentList.getItems().size() >= currentList.getTotal()) {
                            hasMorePages = false;
                        }

                        if (System.currentTimeMillis() - start > TIMEOUT_MILLISECOND_THRESHOLD) {
                            hasMorePages = false;
                        }
    
                        final ConceptResultList currentMemberBatch = populateConcepts(root, refset, lookupParameters);
                        currentList.getItems().addAll(currentMemberBatch.getItems());
                    }
                }
            }

            int snowstormCallCount = 0;
            final boolean doNotSearch = notSearching;
            
            // Change the numbers to '1's to avoid threading 
            final ExecutorService executor = new ThreadPoolExecutor(30, 30, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(30), new ThreadPoolExecutor.CallerRunsPolicy());
            
            // add the descriptions to the children concepts in batches
            for (int i = 0; i < currentList.getItems().size(); i++) {

                Concept concept = currentList.getItems().get(i);
                conceptsToProcess.add(concept);

                if (conceptsToProcess.size() == CONCEPT_DESCRIPTIONS_PER_CALL || i == currentList.getItems().size() - 1) {
                    
                    snowstormCallCount++;
                    final List<Concept> threadConcepts = new ArrayList<Concept>();
                    threadConcepts.addAll(conceptsToProcess);
                    
                    executor.submit(new Runnable() {
                        
                        /* see superclass */
                        @Override
                        public void run() {
                       
                            try {
                    
                                //logger.debug("%%%%%%%%% getMemberList IN THREAD ID: " + Thread.currentThread().getId());
                                populateAllLanguageDescriptions(refset, threadConcepts);
    
                                // if not searching and editing then get the concept leaf information
                                if (doNotSearch && searchParameters.isEditing()) {
                                    populateConceptLeafStatus(refset, threadConcepts);
                                }
                                
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        }
                        
                    });

                    conceptsToProcess.clear();
                }
            }
            
            executor.shutdown();
            executor.awaitTermination(600, TimeUnit.SECONDS);
            
            logger.debug("####### getMemberList snowstormCallCount: " + snowstormCallCount);

            members.getItems().addAll(currentList.getItems());
            members.setTotal(currentList.getTotal());
            members.setTotalKnown(true);
            
            branchCache.put(cacheString, members);
            conceptsCallCache.put(branchPath, branchCache);

        } catch (Exception ex) {
            throw new Exception("Could not get refset member list for refset " + refsetId + " from snowstorm: " + ex.getMessage(), ex);
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
    public static ConceptResultList getMemberTaxonomy(final Refset refset, final List<String> nonDefaultPreferredTerms, TaxonomyParameters taxonomyParameters) throws Exception {

        final String startingConceptId = taxonomyParameters.getStartingConceptId();
        String language = taxonomyParameters.getLanguage();
        List<Concept> relationsList = new ArrayList<>();
        final List<Concept> processedTreeNodes = new ArrayList<>();
        ConceptResultList conceptResultList = new ConceptResultList();
        final String branchPath = getBranchPath(refset);
        final String cacheString = refset.getRefsetId() + taxonomyParameters.toString();
        final Map<String, ConceptResultList> branchCache = getCacheForTree(branchPath);
        
        // check if the members call has been cached
        if (branchCache.containsKey(cacheString)) {
            
            logger.debug("####### getMemberTaxonomy USING CACHE");
            return branchCache.get(cacheString);
        }

        // If parent concept is inactive, getChildren() will return a 400 error.
        try {

            if (taxonomyParameters.getReturnChildren()) {
                conceptResultList = getChildren(startingConceptId, refset, language);
            } else {
                conceptResultList = getParents(startingConceptId, refset, language);
            }

        } catch (Exception e) {
            
            // Only throw exception if the rest status code is something other than 400
            if (!"400".equals(e.getMessage())) {
                throw e;
            }
        }
        
        relationsList = conceptResultList.getItems();
        final List<Concept> conceptsToProcessMembership = new ArrayList<>();

        for (Concept concept : relationsList) {
            conceptsToProcessMembership.add(concept);
        }

        if (!conceptsToProcessMembership.isEmpty()) {
            populateMembershipInformation(refset, conceptsToProcessMembership);
        }

        for (final Concept concept : relationsList) {
            processedTreeNodes.add(concept);
        }

        if (taxonomyParameters.getReturnChildren()) {
            logger.debug("Concept has " + processedTreeNodes.size() + " children");
        } else {
            logger.debug("Concept has " + processedTreeNodes.size() + " parents");
        }

        // if returning the starting concept get the details and set the concept properties appropriately for taxonomy
        if (taxonomyParameters.getReturnStartingConcept()) {

            Concept startingConcept = getConceptDetails(startingConceptId, refset);
            
            // make sure this is a fresh object since concept details can be cached and it would otherwise modify objects in that cache and this cache.
            startingConcept = ModelUtility.fromJson(ModelUtility.toJson(startingConcept), Concept.class);

            // set the name and FSN properties appropriately
            for (final Map<String, String> description : startingConcept.getDescriptions()) {

                if (description == null) {
                    continue;
                }

                // check if this is the english FSN, if so set the FSN property
                if (description.get(DESCRIPTION_LANGUAGE).equalsIgnoreCase("en") && description.get(DESCRIPTION_TYPE).equalsIgnoreCase("fsn")) {
                    startingConcept.setFsn(description.get(DESCRIPTION_TERM));
                }

                // check if this is the requested language, if so set the name property
                if (language
                        .equalsIgnoreCase(description.get(DESCRIPTION_LANGUAGE) + "-X-" + description.get(LANGUAGE_CODE)) && description.get(DESCRIPTION_TYPE).equalsIgnoreCase("pt")) {
                    startingConcept.setName(description.get(DESCRIPTION_TERM));
                }
            }

            startingConcept.setChildren(processedTreeNodes);
            conceptResultList.setItems(Arrays.asList(startingConcept));

        } else {
            conceptResultList.setItems(processedTreeNodes);
        }

        branchCache.put(cacheString, conceptResultList);
        treeCache.put(branchPath, branchCache);
        
        return conceptResultList;
    }

    public static Concept getConceptDetails(String conceptId, Refset refset) throws Exception {

        final String refsetInternalId = refset.getId();
        final String branchPath = getBranchPath(refset);
        final String cacheString = refset.getRefsetId() + conceptId;
        final Map<String, Concept> branchCache = getCacheForConceptDetails(branchPath);
        
        // check if the members call has been cached
        if (branchCache.containsKey(cacheString)) {
            
            logger.debug("####### getConceptDetails USING CACHE");
            return branchCache.get(cacheString);
        }

        // 3 Snowstorm calls: 1) on concept, 2) parents, and 3) children
        try {

            final String url = SnowstormConnection.BASE_URL + "browser/" + getBranchPath(refset) + "/" + "concepts/" + conceptId;

            logger.debug("Get Concept Details URL: " + url);

            ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
            lookupParameters.setGetDescriptions(true);
            lookupParameters.setGetRoleGroups(true);
            lookupParameters.setSingleConceptRequest(true);

            ConceptResultList conceptResultList =
                    getConceptsFromSnowstorm(url, refset, lookupParameters, null);

            if (conceptResultList.size() != 1) {
                throw new Exception("Unexpected number of concepts found (" + conceptResultList.size() + ") in getConceptDetails");
            }

            final Concept concept = conceptResultList.getItems().iterator().next();
            branchCache.put(cacheString, concept);
            conceptDetailsCache.put(branchPath, branchCache);
            
            return concept;

        } catch (Exception ex) {
            throw new Exception("Could not get refset children for concept " + conceptId + " from snowstorm: " + ex.getMessage(), ex);
        }
    }

    protected static ConceptResultList getParents(String conceptId, Refset refset, final String language) throws Exception {
        
        try {
            
            final String url = SnowstormConnection.BASE_URL + "browser/" + getBranchPath(refset) + "/" + "concepts/" + conceptId + "/parents?form=inferred";

            logger.debug("Get Parents URL: " + url);
            ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
            lookupParameters.setGetFsn(true);

            return getConceptsFromSnowstorm(url, refset, lookupParameters, language);

        } catch (Exception ex) {

            if ("400".equals(ex.getMessage())) {
                throw ex;
            }

            throw new Exception("Could not get refset parents for concept " + conceptId + " from snowstorm: " + ex.getMessage(), ex);
        }
    }

    protected static ConceptResultList getChildren(final String conceptId, final Refset refset, final String language) throws Exception {

        try {

            final String branchPath = getBranchPath(refset);
            final String url = SnowstormConnection.BASE_URL + "browser/" + branchPath + "/"
                    + "concepts/" + conceptId + "/children?form=inferred";

            logger.debug("Get Children URL: " + url);
            ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
            lookupParameters.setGetFsn(true);

            return getConceptsFromSnowstorm(url, refset, lookupParameters, language);

        } catch (Exception ex) {

            if ("400".equals(ex.getMessage())) {
                throw ex;
            }

            throw new Exception("Could not get refset children for concept " + conceptId
                    + " from snowstorm: " + ex.getMessage(), ex);
        }
    }

    /**
     * Call the provided Snowstorm URL to get concepts and return a processed
     * result list.
     * 
     * @param url The API URL to call
     * @param refset the refset
     * @param lookupParameters the parts of the concept to retrieve
     * @param language the language to the return the concept descriptions in
     * @return the concepts
     * @throws Exception the exception
     */
    protected static ConceptResultList getConceptsFromSnowstorm(final String url,
        final Refset refset, final ConceptLookupParameters lookupParameters, final String language)
        throws Exception {

        String acceptLanguage = language;

        if (acceptLanguage == null) {
            acceptLanguage = SnowstormConnection.DEFAULT_ACCECPT_LANGUAGES;
        }

        try (final Response response = SnowstormConnection.getResponse(url, acceptLanguage)) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                throw new Exception(
                        "call to url '" + url + "' wasn't successful. " + response.toString());
            }

            final String resultString = response.readEntity(String.class);

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                throw new Exception(Integer.toString(response.getStatus()));
            }

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
     * @return the concept result list
     * @throws Exception the exception
     */
    private static ConceptResultList populateConcepts(JsonNode root, Refset refset, ConceptLookupParameters lookupParameters) throws Exception {

        final ConceptResultList conceptList = new ConceptResultList();
        final String branchPath = getBranchPath(refset);
        final String cacheString = refset.getRefsetId();
        final Map<String, Set<String>> branchCache = getCacheForMemberAncestors(branchPath);

        JsonNode conceptNode = root;
        Iterator<JsonNode> iterator = null;
        int total = 0;

        if (root.get("total") != null) {
            total = root.get("total").asInt();
        }
        
        String searchAfter = "";
        if (root.get("searchAfter") != null) {
            searchAfter = root.get("searchAfter").asText();
        }

        if (!lookupParameters.isGetMembershipInformation()) {
            iterator = root.iterator();
        } else {
            iterator = root.get("items").iterator();
        }

        while (lookupParameters.isSingleConceptRequest() || iterator.hasNext()) {

            if (!lookupParameters.isSingleConceptRequest()) {
                conceptNode = iterator.next();
            }

            String conceptId = null;

            if (conceptNode.has("referencedComponent")) {
                conceptId = conceptNode.get("referencedComponent").get("conceptId").asText();

            } else if (conceptNode.has("conceptId")) {
                conceptId = conceptNode.get("conceptId").asText();
            } else {
                throw new Exception("Unable to process the conceptNode: " + conceptNode);
            }
            
            final Concept concept = new Concept();
            ConceptLookupParameters missingLookupParameters = identifyContentPopulated(concept, refset, lookupParameters);

            String name = "";
            boolean memberStatus = false;
            boolean defined = false;

            // if this has a referenced component it is an active refset member, otherwise it at this point it is not known if it is a member
            if (conceptNode.has("referencedComponentId")) {
                
                final JsonNode referencedComponent = conceptNode.get("referencedComponent");

                // Read member-representation of basic concept content
                if (referencedComponent.get("pt") != null && referencedComponent.get("pt").get("term") != null) {
                    name = referencedComponent.get("pt").get("term").asText();
                } else {
                    
                    if (referencedComponent.get("term") != null) {
                        name = referencedComponent.get("term").asText();
                    }
                }

                // Add FSN if required
                if (missingLookupParameters.isGetFsn() && referencedComponent.get("fsn") != null) {

                    if (referencedComponent.get("fsn") != null && referencedComponent.get("fsn").get("term") != null) {
                        concept.setFsn(referencedComponent.get("fsn").get("term").asText());
                    } else {
                        concept.setFsn(name);
                    }
                }

                // concept status - not membership status
                concept.setActive(referencedComponent.get("active").asBoolean());

                // grab all membership info
                memberStatus = conceptNode.get("active").asBoolean();
                concept.setReleased(conceptNode.get("released").asBoolean());

                // if the member has been released get the effective time
                if (conceptNode.has("releasedEffectiveTime")) {
                    concept.setMemberEffectiveTime(SIMPLE_DATE_FORMAT.parse(conceptNode.get("releasedEffectiveTime").asText()));
                }

            } else if (conceptNode.has("conceptId")) {

                // Concept is General (and is a child of the node opened) Read general-representation of basic concept content
                if (conceptNode.get("pt") != null && conceptNode.get("pt").get("term") != null) {
                    name = conceptNode.get("pt").get("term").asText();
                    
                } else if (conceptNode.get("term") != null) {
                    name = conceptNode.get("term").asText();
                }

                // Add FSN if required
                if (missingLookupParameters.isGetFsn() && conceptNode.get("fsn") != null) {

                    if (conceptNode.get("fsn") != null && conceptNode.get("fsn").get("term") != null) {
                        concept.setFsn(conceptNode.get("fsn").get("term").asText());
                    } else {
                        concept.setFsn(name);
                    }
                }

                // grab other concept information
                if (!conceptNode.get("definitionStatus").asText().equals("PRIMITIVE")) {
                    defined = true;
                }
                
                if (branchCache.containsKey(cacheString) && branchCache.get(cacheString).contains(conceptNode.get("conceptId").asText())) {
                    concept.setHasDescendantRefsetMembers(true);
                }

                if (conceptNode.has("descendantCount")) {
                    concept.setHasChildren(conceptNode.get("descendantCount").asInt() > 0);
                } else if (conceptNode.has("isLeafInferred")) {
                    concept.setHasChildren(!conceptNode.get("isLeafInferred").asBoolean());
                }

                // This is retrieving concept details so get concept status
                concept.setActive(conceptNode.get("active").asBoolean());
            }

            concept.setCode(conceptId);
            concept.setName(name);
            concept.setTerminology("SNOMEDCT");
            concept.setMemberOfRefset(memberStatus);
            concept.setDefined(defined);
            setConceptPermissions(concept);
            processIntensionalDefinitionException(refset, concept);

            // Populate descriptions
            if (missingLookupParameters.isGetDescriptions()) {

                // because this may come from a children call the node may not have descriptions
                if (conceptNode.get("descriptions") != null) {
                    concept.setDescriptions(populateDescriptions(concept.getCode(), conceptNode.get("descriptions"), refset, missingLookupParameters.getNonDefaultPreferredTerms()));
                    
                } else if (conceptNode.get("fsn") != null) {
                    
                    String fsn = conceptNode.get("fsn").get("term").asText();
                    Map<String, String> descMap = new HashMap<>();
                    descMap.put("fsn", fsn);
                    List<Map<String, String>> list = new ArrayList<>();
                    list.add(descMap);
                    concept.setDescriptions(list);
                } else {
                    populateAllLanguageDescriptions(refset, new ArrayList<>(Arrays.asList(concept)));
                }
            }

            // Snowstorm throws a 400-Exception when children/parents of an inactive concepts are requested
            if (missingLookupParameters.isGetParents() && concept.isActive()) {
                concept.setParents(getParents(conceptId, refset, null).getItems());
            }

            // Snowstorm throws a 400-Exception when children/parents of an inactive concepts are requested
            if (missingLookupParameters.isGetChildren() && concept.isActive()) {
                concept.setChildren(getChildren(conceptId, refset, null).getItems());
            }

            if (missingLookupParameters.isGetRoleGroups()) {
                concept.setRoleGroups(populateRoleGroups(concept.getCode(), conceptNode.get("relationships")));
            }

            if (missingLookupParameters.isGetMembershipInformation()) {
                
                concept.setMemberOfRefset(true);
                
                if (conceptNode.get("releasedEffectiveTime") != null) {
                    concept.setMemberEffectiveTime(SIMPLE_DATE_FORMAT.parse(conceptNode.get("releasedEffectiveTime").asText()));
                }
            }

            conceptList.getItems().add(concept);

            if (lookupParameters.isSingleConceptRequest()) {
                break;
            }
        }

        conceptList.setTotal(total);
        conceptList.setSearchAfter(searchAfter);
        return conceptList;
    }

    private static ConceptLookupParameters identifyContentPopulated(Concept concept, Refset refset, ConceptLookupParameters lookupParameters) {

        // If concept not found in cache, concept is null. Just return original lookup parameters
        if (concept == null) {
            return lookupParameters;
        }

        // Concept found in cache, so review contents to see what requires further lookup
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

        if (lookupParameters.isGetParents() && concept.getParents().isEmpty()) {
            missingConceptLookupParameters.setGetParents(true);
            missingContentFound = true;
        }

        if (lookupParameters.isGetChildren() && concept.getChildren().isEmpty()) {
            missingConceptLookupParameters.setGetChildren(true);
            missingContentFound = true;
        }

        if (lookupParameters.isGetFsn() && concept.getFsn() == null) {
            missingConceptLookupParameters.setGetFsn(true);
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

    private static Map<Integer, List<String>> populateRoleGroups(String conceptId,
        JsonNode relationshipsNode) {

        Map<Integer, List<String>> roleGroups = new HashMap<>();
        final Iterator<JsonNode> iterator = relationshipsNode.iterator();

        while (iterator.hasNext()) {

            JsonNode relationship = iterator.next();

            if (relationship.get("active").asBoolean() && "INFERRED_RELATIONSHIP"
                    .equals(relationship.get("characteristicType").asText())) {

                int groupId = relationship.get("groupId").asInt();

                if (!roleGroups.containsKey(groupId)) {
                    roleGroups.put(groupId, new ArrayList<String>());
                }

                String type = relationship.get("type").get("pt").get("term").asText();

                if (!"Is a".equals(type)) {

                    String target = relationship.get("target").get("pt").get("term").asText();
                    roleGroups.get(groupId).add(type + " -> " + target);
                }
            }
        }

        if (roleGroups.size() > 0 && roleGroups.get(0).size() == 0) {
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

    private static void populateMembershipInformation(final Refset refset, final List<Concept> concepts) throws Exception {
        
        final String baseUrl = SnowstormConnection.BASE_URL + getBranchPath(refset) + "/members?referenceSet=" + refset.getRefsetId() + "&active=true&limit=" + ELASTICSEARCH_MAX_RECORD_LENGTH + "&offset=0&referencedComponentId=";
        final List<Concept> conceptsToProcess = new ArrayList<>();
        String conceptIds = "";
        int snowstormCallCount = 0;
        
        // Change the '30's to '1's to avoid threading 
        final ExecutorService executor = new ThreadPoolExecutor(30, 30, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(30), new ThreadPoolExecutor.CallerRunsPolicy());
        
        // add the descriptions to the children concepts in batches
        for (int i = 0; i < concepts.size(); i++) {
            
            final Concept concept = concepts.get(i);
            conceptsToProcess.add(concept);
            conceptIds += concept.getCode() + ",";
            
            if (conceptsToProcess.size() == CONCEPT_DESCRIPTIONS_PER_CALL || i == concepts.size() - 1) {
                
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
                
                            //logger.debug("%%%%%%%%% populateMembershipInformation IN THREAD ID: " + Thread.currentThread().getId());
                            logger.debug("Get Membership URL for Populate: " + memberUrl);

                            final ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
                            lookupParameters.setGetMembershipInformation(true);
                            final ConceptResultList resultList = getConceptsFromSnowstorm(memberUrl, refset, lookupParameters, null);

                            for (Concept resultConcept : resultList.getItems()) {

                                for (Concept conceptToProcess : threadConcepts) {

                                    if (resultConcept.getCode().equals(conceptToProcess.getCode())) {
                                        
                                        conceptToProcess.setMemberOfRefset(resultConcept.isMemberOfRefset());
                                        conceptToProcess.setMemberEffectiveTime(resultConcept.getMemberEffectiveTime());
                                        conceptToProcess.setReleased(resultConcept.isReleased());
                                        break;
                                    }
                                }
                            }
                            
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                    
                });
                
                conceptsToProcess.clear();
            }
        }
        
        executor.shutdown();
        executor.awaitTermination(600, TimeUnit.SECONDS);
        
        logger.debug("####### populateMembershipInformation snowstormCallCount: " + snowstormCallCount);
    }
    
    private static String processIntensionalDefinitionException(final Refset refset, final Concept concept) throws Exception {
        
        String conceptExceptionType = "";
        
        if (!refset.getType().equals(Refset.INTENSIONAL)) {
            return conceptExceptionType;
        }
        
        final List<DefinitionClause> definitionClauses = refset.getDefinitionClauses();
        final Pattern pattern = Pattern.compile("\\b" + concept.getCode() + "\\b");
        
        for (int i = 1; i < definitionClauses.size(); i++) {
            
            final DefinitionClause clause = definitionClauses.get(i);
            final Matcher matcher = pattern.matcher(clause.getValue());
            
            if (matcher.find()) {
                
                if (clause.getNegated()) {
                    conceptExceptionType = Refset.EXCLUSION;
                } else {
                    conceptExceptionType = Refset.INCLUSION;
                }
                
                concept.setDefinitionExceptionType(conceptExceptionType);
                concept.setDefinitionExceptionId(clause.getId());
                return conceptExceptionType;
            }
        }
        
        return conceptExceptionType;
    }

    public static List<Map<String, String>> getMemberHistory(String referencedComponentId, List<Map<String, String>> versions) throws Exception {

        // Note, the system expects that versions are ordered from oldest first
        // to newest last. Failure to adhere to this convention will break the
        // algorithm.
        List<Map<String, String>> memberHistory = new ArrayList<>();
        String previousStatus = null;

        try (final TerminologyService service = new TerminologyService()) {

            for (Map<String, String> version : versions) {

                String refsetInternalId = version.get("refsetInternalId");
                String branchDate = version.get("date");

                logger.debug("Processing history on: " + branchDate
                        + " using internalRefsetId: " + refsetInternalId);

                final Refset refset = service.get(refsetInternalId, Refset.class);

                if (refset == null) {
                    throw new Exception("Refset Internal Id: " + refsetInternalId
                            + " does not exist in the RT2 database");
                }

                final String url = SnowstormConnection.BASE_URL + RefsetService.getBranchPath(refset) + "/members?referenceSet=" + refset.getRefsetId() + "&referencedComponentId=" + referencedComponentId;

                logger.debug("Get Membership History URL: " + url);

                try (final Response response = SnowstormConnection.getResponse(url)) {

                    if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                        throw new Exception("call to url '" + url + "' wasn't successful. "
                                + response.toString());
                    }

                    final String resultString = response.readEntity(String.class);
                    final ObjectMapper mapper = new ObjectMapper();
                    final JsonNode root = mapper.readTree(resultString.toString());
                    final JsonNode node = root.get("items");
                    String currentVersionDate = null;
                    Iterator<JsonNode> iterator = node.iterator();
                    String currentStatus = null;

                    if (iterator.hasNext()) {

                        JsonNode memberNode = iterator.next();

                        // Calculate the version date and ensure it has
                        // proper format yyyy-mm-dd
                        currentVersionDate = memberNode.get("releasedEffectiveTime").asText();
                        currentVersionDate = currentVersionDate.substring(0, 4) + "-"
                                + currentVersionDate.substring(4, 6) + "-"
                                + currentVersionDate.substring(6);
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

                        Map<String, String> historyEntry = new HashMap<>();

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

                        Map<String, String> historyEntry = new HashMap<>();
                        historyEntry.put("version", currentVersionDate);

                        if (currentStatus.equals("Active")) {
                            historyEntry.put("change", "Activated");
                        } else {
                            historyEntry.put("change", "Inactivated");
                        }

                        memberHistory.add(historyEntry);

                    }
                    previousStatus = currentStatus;

                } catch (Exception ex) {
                    
                    logger.error("Could not grab refset members for refset " + refset.getRefsetId() + " from snowstorm: " + ex.getMessage(), ex);
                    continue;
                }
            }
        }

        return memberHistory;
    }

    /**
     * Populate the user permissions properties on a concept.
     *
     * @param concept The concept to set properties on
     * @param user The user object to determine permissions from
     */
    private static void setConceptPermissions(Concept concept) {

        concept.setHistoryVisible(true);
        concept.setFeedbackVisible(true);
    }

    /**
     * Populate the user permissions properties on a concept.
     *
     */
    public static void cacheAllMemberAncestors() {

        try (final TerminologyService service = new TerminologyService()) {

            final PfsParameter pfs = new PfsParameter();
            pfs.setAscending(false);
            pfs.setSort("latestVersion");

            final ResultList<Refset> refsets = service.find("", null, Refset.class, null);
            logger.info("Starting to cache member ancestors for all " + refsets.getItems().size() + " refsets");

            for (final Refset refset : refsets.getItems()) {
                
                refset.setBranchPath(RefsetService.getBranchPath(refset));
                cacheMemberAncestors(refset);
            }

        } catch (Exception e) {
            logger.error("Could not cache all member ancestors", e);
        }
    }
    
    public static boolean cacheMemberAncestors(final User user, String refsetId, final String versionDate) throws Exception {
        return cacheMemberAncestors(RefsetService.getRefset(user, refsetId, versionDate));
    }

    public static boolean cacheMemberAncestors(final Refset refset) throws Exception {

        final long start = System.currentTimeMillis();
        final String branchPath = refset.getBranchPath();
        final String cacheString = refset.getRefsetId();
        final Map<String, Set<String>> branchCache = getCacheForMemberAncestors(branchPath);
        
        // check if the members call has been cached
        if (branchCache.containsKey(cacheString)) {
            
            logger.debug("####### cacheMemberAncestors USING CACHE");
            return true;
        }
        
        final String memberCountUrl = SnowstormConnection.BASE_URL + getBranchPath(refset) + "/members?referenceSet=" + refset.getRefsetId() + "&active=true&limit=1";
        
        // See how many members the refset has - if it is more than 100k we can not cache the ancestors
        try (final Response response = SnowstormConnection.getResponse(memberCountUrl)) {
            
            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                throw new Exception("Call to url '" + memberCountUrl + "' wasn't successful. " + response.getStatus() + ": " + response.toString());
            }

            final String resultString = response.readEntity(String.class);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());
            int memberTotal = root.get("total").asInt();
            
            if (memberTotal > 100000) {
                
                logger.warn("Could not cache the ancestors of refset " + refset.getRefsetId() + " because it has too many members: " + memberTotal);
                branchCache.put(cacheString, new HashSet<String>());
                ancestorsCache.put(branchPath, branchCache);
                return true;
            }
            
        } catch (Exception e) {
            logger.error("Could not cache the ancestors of refset " + refset.getRefsetId() + " from snowstorm: " + e.getMessage(), e);
        }

        // Get ancestors of all members via ecl e.g. >(^723264001)
        final String url = SnowstormConnection.BASE_URL + getBranchPath(refset) + "/concepts?ecl=%3E(%5E" + refset.getRefsetId() + ")&limit=1000&offset=";
        final List<Set<String>> ancestorsSetBatches = new ArrayList<>(Collections.nCopies(10, new HashSet<>()));
        
        // Change the '10's to '1's to avoid threading 
        final ExecutorService executor = new ThreadPoolExecutor(10, 10, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(10), new ThreadPoolExecutor.CallerRunsPolicy());
        
        // add the descriptions to the children concepts in batches
        for (int i = 0; i < ancestorsSetBatches.size(); i++) {

            final Set<String> ancestorsSet = ancestorsSetBatches.get(i);
            final int offset = 1000 * i;
            logger.debug("cacheMemberAncestors URL: " + url + offset);
            
            executor.submit(new Runnable() {
                
                /* see superclass */
                @Override
                public void run() {
               
                    try (final Response response = SnowstormConnection.getResponse(url + offset)) {
                        
                        if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                            throw new Exception("Call to url '" + url + offset + "' wasn't successful. " + response.getStatus() + ": " + response.toString());
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
                    } catch (Exception e) {
                        logger.error("Could not cache the ancestors of refset " + refset.getRefsetId() + " from snowstorm: " + e.getMessage(), e);
                    }
                }
                
            });
        }
        
        executor.shutdown();
        executor.awaitTermination(120, TimeUnit.SECONDS);
        
        final Set<String> ancestorsSet = new HashSet<>();
        
        for (Set<String> ancestorsBatch : ancestorsSetBatches) {
            ancestorsSet.addAll(ancestorsBatch);
        }
        
        branchCache.put(cacheString, ancestorsSet);
        ancestorsCache.put(branchPath, branchCache);
        
        logger.debug("cacheMemberAncestors Time Taken: " + (System.currentTimeMillis() - start));
        logger.debug("cacheMemberAncestors Number of Ancestors: " + ancestorsSet.size());

        return true;
    }
    
    /**
     * Convert a list of concepts into an ECL statement.
     *
     * @param conceptIds a list of concept IDs
     * @return An ECL statement composed of the list of concept IDs
     * @throws Exception the exception
     */
    public static String conceptListToEclStatement(List<String> conceptIds) throws Exception {
        
        String ecl = "";
        
        for (final String conceptId : conceptIds) {
            ecl += conceptId + " OR ";
        }
        
        return StringUtils.removeEnd(ecl, " OR ");
    }

    /**
     * Add a list of concepts as members to a refset.
     *
     * @param user the user
     * @param refsetInternalId the internal refset ID
     * @param conceptIds a list of concept IDs to make members
     * @return A list of concepts that were unable to be added
     * @throws Exception the exception
     */
    public static List<String> addRefsetMembers(final User user, final String refsetInternalId, List<String> conceptIds) throws Exception {

        List<String> unaddedConcepts = new ArrayList<>();
        final ObjectMapper mapper = new ObjectMapper();
        
        // get the edition and project for the new refset
        try (final TerminologyService service = new TerminologyService()) {

            final Refset refset = getRefset(user, service, refsetInternalId);
            WorkflowService.canUserEditRefset(user, refset);
            
            final String branchPath = RefsetService.getBranchPath(refset);
            final String url = SnowstormConnection.BASE_URL + branchPath + "/" + "members";

            if (refset == null) {
                throw new Exception("Refset Internal Id: " + refsetInternalId + " does not exist in the RT2 database");
            }
            
            if (conceptIds.size() == 0) {
                return unaddedConcepts;
            }

            final String refsetId = refset.getRefsetId();

            // clear the caches for this refset
            clearAllMemberCaches(branchPath);

            // when searching for members we only want concepts whose membership is active (though the concept itself can be inactive)
            final String conceptSearchUrl = SnowstormConnection.BASE_URL + branchPath + "/concepts/search";
            final String bodyBase = "{\"limit\": " + ELASTICSEARCH_MAX_RECORD_LENGTH + ", ";
            final List<String> conceptsToSearch = new ArrayList<>(conceptIds);
            final Map<String, Map<String, String>> conceptsStatus = refsetsUpdatedMembers.get(refsetInternalId);
            final List<String> validatedConcepts = new ArrayList<>();
            boolean searchAgain = true;
            int searchIndex = 0;
            int loopNumber = 1;
            logger.debug("addRefsetMembers concept search/verification URL: " + conceptSearchUrl);
            
            while (searchAgain) { 
                
                searchAgain = false;
                String bodyConceptIds = "\"conceptIds\":[";
                
                for (; searchIndex < conceptsToSearch.size(); searchIndex++) {
                    
                    //logger.debug("addRefsetMembers searchIndex: " + searchIndex + " :: conceptsToSearch.size(): " + conceptsToSearch.size() + " :: conceptsToSearch.get(searchIndex): " + conceptsToSearch.get(searchIndex));
                    final String conceptId = conceptsToSearch.get(searchIndex);
                    final Map<String, String> status = new HashMap<>();
                    status.put("operation", "Added");
                    status.put("status", "Failed");
                    conceptsStatus.put(conceptId, status);
                    bodyConceptIds += "\"" + conceptId + "\",";
                    
                    if (searchIndex / loopNumber >= ELASTICSEARCH_MAX_RECORD_LENGTH) {
                        
                        loopNumber++;
                        searchAgain = true;
                        break;
                    }
                }
                
                logger.debug("addRefsetMembers searchIndex :: loopNumber :: batch size: " + searchIndex + " :: " + loopNumber + " :: " + (searchIndex / loopNumber));
                bodyConceptIds = StringUtils.removeEnd(bodyConceptIds, ",") + "]";
                
                final String memberSearchBody = bodyBase + bodyConceptIds + ", \"eclFilter\": \"^" + refset.getRefsetId() + "\"}";
                Iterator<JsonNode> iterator = null;
                
                //logger.debug("addRefsetMembers member search body: " + memberSearchBody);
                
                try (final Response response = SnowstormConnection.postResponse(conceptSearchUrl, memberSearchBody)) {

                    final String resultString = response.readEntity(String.class);

                    // Only process payload if Rest call is successful
                    if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                        throw new Exception("call to url '" + conceptSearchUrl + "' for member search wasn't successful. " + response.toString());
                    }

                    final JsonNode root = mapper.readTree(resultString.toString());
                    iterator = root.get("items").iterator();
                    
                    // loop thru the returned member details remove any from the list to add
                    while (iterator != null && iterator.hasNext()) {

                        final JsonNode conceptNode = iterator.next();
                        final String conceptId = conceptNode.get("conceptId").asText();
                        conceptIds.remove(conceptId);
                        
                        final Map<String, String> status = new HashMap<>();
                        status.put("operation", "Added");
                        status.put("status", "Already Member");
                        conceptsStatus.put(conceptId, status);
                    }
                }
                
                // verify the concept IDs if a bulk add is going to be used -- TODO - This can be removed if invalid concepts are handled on SnowStorm
                if (conceptIds.size() > 0) {
                    
                    final String conceptVerificationBody = bodyBase + bodyConceptIds + "}";
                    //logger.debug("addRefsetMembers bulk verification body: " + conceptVerificationBody);
                    
                    try (final Response response = SnowstormConnection.postResponse(conceptSearchUrl, conceptVerificationBody)) {
                        
                        final String resultString = response.readEntity(String.class);
                        
                        // Only process payload if Rest call is successful
                        if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                            throw new Exception("call to url '" + conceptSearchUrl + "' for concept verification wasn't successful. " + response.toString());
                        }
                        
                        final JsonNode root = mapper.readTree(resultString.toString());
                        iterator = root.get("items").iterator();
                        
                        while (iterator != null && iterator.hasNext()) {

                            final JsonNode conceptNode = iterator.next();
                            final String conceptId = conceptNode.get("conceptId").asText();
                            validatedConcepts.add(conceptId);
                            
                            final Map<String, String> status = new HashMap<>();
                            status.put("operation", "Added");
                            status.put("status", "Success");
                            conceptsStatus.put(conceptId, status);
                        }
                    }
                }
            }
            
            // gather any input concept not in the validated list 
            final List<String> invalidConcepts = conceptIds.stream()
                .filter((inputConceptId) -> { 
                    
                    boolean found = validatedConcepts.contains(inputConceptId);
                    
                    if (found) {
                        return false;
                    } else {
                        
                        logger.debug("The ID " + inputConceptId + " is not a valid concept.");
                        return true;
                    }
                })
                .collect(Collectors.toList());
            
            logger.debug("********* invalidConcepts: " + invalidConcepts);
            unaddedConcepts.addAll(invalidConcepts);
            conceptIds.removeAll(invalidConcepts);
            
            logger.debug("addRefsetMembers about to add concept size: " + conceptIds.size());
            
            if (conceptIds.size() == 1) {
                unaddedConcepts.addAll(callAddMemberSingle(refsetId, url, conceptIds.get(0)));
            } else {
                unaddedConcepts.addAll(callAddMembersBulk(refsetId, url, conceptIds));
            }
            
            for (final String conceptId : unaddedConcepts) {
                
                final Map<String, String> status = new HashMap<>();
                status.put("operation", "Added");
                status.put("status", "Failed");
                conceptsStatus.put(conceptId, status);
            }
        }
        
        return unaddedConcepts;
    }
    
    /**
     * Call the API to add a single member to a refset.
     *
     * @param refsetId the refset ID
     * @param url the URL to call
     * @param conceptId the concept ID to add as a member
     * @return A list of concepts that were unable to be added
     * @throws Exception the exception
     */
    private static List<String> callAddMemberSingle(final String refsetId, final String url, final String conceptId) throws Exception {
        
        final List<String> unaddedConcepts = new ArrayList<>();
        
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode body = mapper.createObjectNode().put("refsetId", refsetId)
            .put("referencedComponentId", conceptId);

        logger.debug("callAddMemberSingle URL: " + url);
        logger.debug("callAddMemberSingle URL body: " + body.toString());
    
        try (final Response response =
                SnowstormConnection.postResponse(url, body.toString())) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
    
                logger.error("Add Refset Member call to url '" + url + "' for refset '"
                        + refsetId + "' and concept '" + conceptId + "' wasn't successful. "
                        + response.toString());
                unaddedConcepts.add(conceptId);
            }
        }
        
        return unaddedConcepts;
    }
    
    /**
     * Call the API to add members in bulk to a refset.
     *
     * @param refsetId the refset ID
     * @param url the base URL to call
     * @param conceptIds a list of concept IDs to make members
     * @return A list of concepts that were unable to be added
     * @throws Exception the exception
     */
    private static List<String> callAddMembersBulk(final String refsetId, final String url, List<String> conceptIds) throws Exception {
        
        final List<String> unaddedConcepts = new ArrayList<>();
        final String bulkUrl = url + "/bulk";
        final ObjectMapper mapper = new ObjectMapper();
        final ArrayNode body = mapper.createArrayNode();

        for (final String conceptId : conceptIds) {
            
            final ObjectNode memberBody = mapper.createObjectNode()
                .put("refsetId", refsetId)
                .put("referencedComponentId", conceptId);
            
            body.add(memberBody);
        }
        
        logger.debug("callAddMembersBulk URL: " + bulkUrl);
        logger.debug("callAddMembersBulk URL body: " + body.toString());
        
        String jobStatusUrl = null;
        boolean jobDone = false;
        String errorMessage = "Add Refset Member bulk call to url '" + bulkUrl + "' for refset '" + refsetId + " wasn't successful. ";
        
        try (final Response response = SnowstormConnection.postResponse(bulkUrl, body.toString())) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
                logger.error(errorMessage + response.toString());
            }
            
            jobStatusUrl = response.getHeaderString("Location");
        }
        
        if (jobStatusUrl == null) {
            logger.error(errorMessage);
            
        } else {
            
            try {
                Thread.sleep(800);
            } catch(InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            
            logger.debug("callAddMembersBulk job status URL: " + jobStatusUrl);
            
            while (!jobDone) {
                
                try (final Response response = SnowstormConnection.getResponse(jobStatusUrl)) {

                    // Only process payload if Rest call is successful
                    if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                        logger.error(errorMessage + response.toString());
                    }
                    
                    final String resultString = response.readEntity(String.class);
                    final JsonNode root = mapper.readTree(resultString.toString());
                    
                    //logger.debug("addRefsetMembers job status response: " + root);
                    final String status = root.get("status").asText();
                    
                    if (status.equalsIgnoreCase("COMPLETED")) {
                        jobDone = true;
                    
                    } else if (status.equalsIgnoreCase("failed")) {
                        logger.error(errorMessage + root.get("message").asText());
                    } else {
                        
                        logger.debug("Bulk member add hasn't finished yet...");
                        try {
                            Thread.sleep(800);
                        } catch(InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        }
        
        return unaddedConcepts;
    }

    /**
     * Remove or inactivate refset membership for a list of concepts.
     *
     * @param user the user
     * @param refsetInternalId the internal refset ID
     * @param conceptIds a list of concept IDs to make members
     * @return A list of concepts that were unable to have membership removed
     * @throws Exception the exception
     */
    public static List<String> removeRefsetMembers(final User user, final String refsetInternalId, String conceptIds)
        throws Exception {

        List<String> unremovedConcepts = new ArrayList<>();
        final ObjectMapper mapper = new ObjectMapper();
        final Map<String, Map<String, String>> conceptsStatus = refsetsUpdatedMembers.get(refsetInternalId);
        
        if (conceptIds.isEmpty()) {
            return unremovedConcepts;
        }

        // get the edition and project for the new refset
        try (final TerminologyService service = new TerminologyService()) {

            final Refset refset = getRefset(user, service, refsetInternalId);
            WorkflowService.canUserEditRefset(user, refset);

            final String refsetId = refset.getRefsetId();
            final String branchPath = RefsetService.getBranchPath(refset);
            final String url = SnowstormConnection.BASE_URL + branchPath + "/" + "members";

            // clear the caches for this refset
            clearAllMemberCaches(branchPath);

            // when searching for members we only want concepts whose membership is active (though the concept itself can be inactive)
            final String memberSearchUrlBase = SnowstormConnection.BASE_URL + "browser/" + branchPath + "/members?referenceSet=" + refset.getRefsetId() + "&offset=0&active=true" 
            + "&limit=" + URL_MAX_CHAR_LENGTH + "&referencedComponentId=";
            final ArrayNode memberDeleteArray = mapper.createArrayNode();
            final ArrayNode memberUpdateArray = mapper.createArrayNode();
            final List<String> conceptsToSearch = Arrays.asList(conceptIds.split(","));
            boolean searchAgain = true;
            int searchIndex = 0;
            
            while (searchAgain) {
                
                searchAgain = false;
                String bodyConceptIds = "";
                
                for (; searchIndex < conceptsToSearch.size(); searchIndex++) {
                    
                    final String conceptId = conceptsToSearch.get(searchIndex);
                    final Map<String, String> status = new HashMap<>();
                    status.put("operation", "Removed");
                    status.put("status", "Failed");
                    conceptsStatus.put(conceptId, status);
                    bodyConceptIds += conceptId + ",";
                    
                    if (memberSearchUrlBase.length() + bodyConceptIds.length() >= URL_MAX_CHAR_LENGTH) {
                        
                        searchAgain = true;
                        break;
                    }
                }
                
                bodyConceptIds = StringUtils.removeEnd(bodyConceptIds, ",");
                
                final String memberSearchUrl = memberSearchUrlBase + bodyConceptIds;
                Iterator<JsonNode> iterator = null;
                
                logger.debug("removeRefsetMembers search URL: " + memberSearchUrl);
                
                try (final Response response = SnowstormConnection.getResponse(memberSearchUrl)) {

                    final String resultString = response.readEntity(String.class);

                    // Only process payload if Rest call is successful
                    if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                        
                        searchAgain = false;
                        throw new Exception("call to url '" + memberSearchUrl + "' wasn't successful. " + response.toString());
                    }

                    final JsonNode root = mapper.readTree(resultString.toString());
                    iterator = root.get("items").iterator();
                }
                
                // loop thru the returned member details and add it to the list to delete
                while (iterator != null && iterator.hasNext()) {

                    final JsonNode conceptNode = iterator.next();
                    final boolean released = conceptNode.get("released").asBoolean();
                    final String membershipId = conceptNode.get("memberId").asText();
                    
                    // if the member has not been released then remove the membership
                    if (!released) {
                        memberDeleteArray.add(membershipId);
                    }
                    
                    // if the member has been released add the information to the update array
                    else {
                        
                        final ObjectNode memberBody = mapper.createObjectNode().put("active", false)
                            .put("effectiveTime", conceptNode.get("effectiveTime").asText())
                            .put("memberId", membershipId)
                            .put("moduleId", conceptNode.get("moduleId").asText())
                            .put("referencedComponentId",
                                    conceptNode.get("referencedComponentId").asText())
                            .put("refsetId", conceptNode.get("refsetId").asText())
                            .put("released", released)
                            .put("releasedEffectiveTime",
                                    conceptNode.get("releasedEffectiveTime").asInt())
                            .set("additionalFields", conceptNode.get("additionalFields"));
                        
                        memberUpdateArray.add(memberBody);
                    }
                    
                    logger.debug("removeRefsetMembers conceptNode: " + ModelUtility.toJson(conceptNode)); 
                    
                    final Map<String, String> status = new HashMap<>();
                    status.put("operation", "Removed");
                    status.put("status", "Success");
                    conceptsStatus.put(conceptNode.get("referencedComponentId").asText(), status);
                }
            }
            
            // delete any members that haven't been released
            if (memberDeleteArray.size() > 0) {
                
                final String deleteBody = mapper.createObjectNode().set("memberIds", memberDeleteArray).toString();
                final String deleteUrl = url + "?force";
                String errorMessage = "Remove Refset Member bulk call to url '" + deleteUrl + "' for refset '" + refsetId + " wasn't successful. ";
                
                logger.debug("removeRefsetMembers URL: " + deleteUrl);
                logger.debug("removeRefsetMembers URL Body: " + deleteBody);
                
                try (final Response response = SnowstormConnection.deleteResponse(deleteUrl, deleteBody)) {

                    // Only process payload if Rest call is successful
                    if (response.getStatus() != Response.Status.NO_CONTENT.getStatusCode()) {
                        logger.error(errorMessage + response.toString());
                    }
                }
            }
            
            // If there is one member to inactivate call the single update method, otherwise call the batch update
            if (memberUpdateArray.size() == 1) {
                
                final JsonNode memberBody = memberUpdateArray.get(0);
                unremovedConcepts = callUpdateMemberSingle(refsetId, url + "/" + memberBody.get("memberId").asText() , memberBody);
                
            } else if (memberUpdateArray.size() > 1) {
                
                unremovedConcepts = callUpdateMembersBulk(refsetId, url + "/bulk", memberUpdateArray);
            }
            
            for (final String conceptId : unremovedConcepts) {
                
                final Map<String, String> status = new HashMap<>();
                status.put("operation", "Removed");
                status.put("status", "Failed");
                conceptsStatus.put(conceptId, status);
            }
        }

        return unremovedConcepts;
    }
    
    /**
     * Call the API to add a single member to a refset.
     *
     * @param refsetId the refset ID
     * @param url the URL to call
     * @param memberBody the details to update the member to
     * @return A list of concepts that were unable to be added
     * @throws Exception the exception
     */
    private static List<String> callUpdateMemberSingle(final String refsetId, final String url, final JsonNode memberBody) throws Exception {
        
        final List<String> unchangedConcepts = new ArrayList<>();
        
        final ObjectMapper mapper = new ObjectMapper();

        logger.debug("callUpdateMemberSingle URL: " + url);
        logger.debug("callUpdateMemberSingle URL body: " + memberBody.toString());
    
        try (final Response response =
                SnowstormConnection.putResponse(url, memberBody.toString())) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
    
                final String memberId = memberBody.get("memberId").asText();
                final String conceptId = memberBody.get("referencedComponentId").asText();
                
                logger.error("Inactivate Refset Member call to url '" + url + "' for refset '"
                        + refsetId + "' and concept '" + conceptId + "' and member '" + memberId +"' wasn't successful. "
                        + response.toString());
                unchangedConcepts.add(memberId);
            }
        }
        
        return unchangedConcepts;
    }
    
    /**
     * Call the API to add members in bulk to a refset.
     *
     * @param refsetId the refset ID
     * @param url the base URL to call
     * @param memberBodies an array of details to update the members to
     * @return A list of concepts that were unable to be added
     * @throws Exception the exception
     */
    private static List<String> callUpdateMembersBulk(final String refsetId, final String url, final ArrayNode memberBodies) throws Exception {
        
        final List<String> unchangedConcepts = new ArrayList<>();
        final ObjectMapper mapper = new ObjectMapper();
        
        logger.debug("callUpdateMembersBulk URL: " + url);
        logger.debug("callUpdateMembersBulk URL body: " + memberBodies.toString());
        
        String jobStatusUrl = null;
        boolean jobDone = false;
        String errorMessage = "Inactive Refset Member bulk call to url '" + url + "' for refset '" + refsetId + " wasn't successful. ";
        
        try (final Response response = SnowstormConnection.postResponse(url, memberBodies.toString())) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.CREATED.getStatusCode()) {
                logger.error(errorMessage + response.toString());
            }
            
            jobStatusUrl = response.getHeaderString("Location");
        }
        
        if (jobStatusUrl == null) {
            logger.error(errorMessage);
            
        } else {
            
            try {
                Thread.sleep(800);
            } catch(InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
            
            logger.debug("callUpdateMembersBulk job status URL: " + jobStatusUrl);
            
            while (!jobDone) {
                
                try (final Response response = SnowstormConnection.getResponse(jobStatusUrl)) {

                    // Only process payload if Rest call is successful
                    if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                        logger.error(errorMessage + response.toString());
                    }
                    
                    final String resultString = response.readEntity(String.class);
                    final JsonNode root = mapper.readTree(resultString.toString());
                    
                    //logger.debug("addRefsetMembers job status response: " + root);
                    final String status = root.get("status").asText();
                    
                    if (status.equalsIgnoreCase("COMPLETED")) {
                        jobDone = true;
                    
                    } else if (status.equalsIgnoreCase("failed")) {
                        logger.error(errorMessage + root.get("message").asText());
                    } else {
                        
                        logger.debug("Bulk member inactivate hasn't finished yet...");
                        try {
                            Thread.sleep(800);
                        } catch(InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
            }
        }
        
        return unchangedConcepts;
    }

    /**
     * Get a list of concepts ID from an ECL query.
     *
     * @param refsetInternalId the internal refset ID
     * @param conceptIds a list of concept IDs to make members
     * @return A list of concepts that were unable to have membership removed
     * @throws Exception the exception
     */
    public static List<String> getConceptIdsFromEcl(final String branch, final String ecl)
        throws Exception {

        final List<String> concepts = new ArrayList<>();
        final ObjectMapper mapper = new ObjectMapper();
        String url = SnowstormConnection.BASE_URL + branch + "/" + "concepts?ecl="
                + StringUtility.encodeValue(ecl) + "&limit=" + ELASTICSEARCH_MAX_RECORD_LENGTH;
        boolean keepSearching = true;
        int total = 0;
        int totalReturned = 0;
        String searchAfter = "";

        while (keepSearching) {

            logger.debug("getConceptIdsFromEcl URL: " + url + searchAfter);

            // update the concept with the new data
            try (final Response response = SnowstormConnection.getResponse(url + searchAfter)) {

                // Only process payload if Rest call is successful
                if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                    throw new Exception("Unable to get refset concepts. Status: "
                            + Integer.toString(response.getStatus()) + ". Error: "
                            + response.toString());
                }

                final String resultString = response.readEntity(String.class);
                final JsonNode root = mapper.readTree(resultString.toString());
                final JsonNode items = root.get("items");
                final Iterator<JsonNode> iterator = items.iterator();
                totalReturned += items.size();
                
                if (total == 0) {
                    total = root.get("total").asInt();
                }
                
                logger.debug("getConceptIdsFromEcl total: " + total);
                logger.debug("getConceptIdsFromEcl totalReturned: " + totalReturned);
                logger.debug("getConceptIdsFromEcl concepts.size(): " + concepts.size());

                if (totalReturned == 0 || totalReturned >= total) {
                    keepSearching = false;
                } else {
                    searchAfter = "&searchAfter=" + root.get("searchAfter").asText();
                }
                
                logger.debug("getConceptIdsFromEcl totalReturned so far: " + totalReturned);

                // loop thru the returned concepts add them to the list
                while (iterator != null && iterator.hasNext()) {

                    final JsonNode conceptNode = iterator.next();
                    final String conceptId = conceptNode.get("conceptId").asText();

                    if (concepts.contains(conceptId)) {
                        continue;
                    }

                    concepts.add(conceptId);
                }
            }
        }

        return concepts;
    }
    
    /**
     * Compile and store the data to upgrade a refset.
     *
     * @param service the Terminology Service
     * @param refsetInternalId the internal refset ID
     * @param upgradeBranch the branch to upgrade to
     * @return The operation status
     * @throws Exception the exception
     */
    public static String compileUpgradeData(final TerminologyService service, final User user, final String refsetInternalId, String upgradeBranch) throws Exception {
        
        String status = "Upgrade data compiled";
        final Refset refset = RefsetService.getRefset(user, refsetInternalId);
        ConceptResultList replacements = new ConceptResultList();
        List<Concept> inactiveMemberList = new ArrayList<>();
        List<String> activeMemberList = new ArrayList<>();
        LinkedHashMap<String, UpgradeInactiveConcecpt> inactiveData = new LinkedHashMap<>();
        final List<Concept> replacementConcepts = new ArrayList<>();
        final String branchPath = getBranchPath(refset);
        final String refsetId = refset.getRefsetId();
        ConceptLookupParameters lookupParameters = new ConceptLookupParameters();
        lookupParameters.setGetMembershipInformation(true);
        String searchAfter = "";
        final long start = System.currentTimeMillis();
        boolean hasMorePages = true; 
        final String acceptLanguage = SnowstormConnection.DEFAULT_ACCECPT_LANGUAGES;
        int memberTotal = 0;
        boolean memberTotalKnown = false;
        String inactiveConceptIds = "";
        final ObjectMapper mapper = new ObjectMapper();
        final List<String> nonDefaultPreferredTerms = identifyNonDefaultPreferredTerms(refset.getEdition());
        int replacementCount = 0;
        
        // when searching for members we only want concepts whose membership is active (though the concept itself can be inactive)
        final String url = SnowstormConnection.BASE_URL + branchPath + "/members?referenceSet=" + refsetId + "&active=true&offset=0&limit=" + ELASTICSEARCH_MAX_RECORD_LENGTH;
        
        // use members call to get members
        while (hasMorePages) {
            
            logger.debug("compileUpgradeData Member list URL: " + url + searchAfter);

            try (final Response response = SnowstormConnection.getResponse(url + searchAfter, acceptLanguage)) {

                if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                    
                    hasMorePages = false;
                    throw new Exception("call to url '" + url + "' wasn't successful. " + response.toString());
                }

                final String resultString = response.readEntity(String.class);

                // Only process payload if Rest call is successful
                if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                    throw new Exception(Integer.toString(response.getStatus()));
                }

                final JsonNode root = mapper.readTree(resultString.toString());
                JsonNode conceptNodeBatch = root.get("items");
                
                // if the search returned results set the total
                if (!memberTotalKnown) {
                    
                    memberTotal = root.get("total").asInt();
                    memberTotalKnown = true;
                }
                
                if (root.get("searchAfter") != null) {
                    searchAfter = "&searchAfter=" + root.get("searchAfter").asText();
                }
                
                if (conceptNodeBatch.size() == 0 || conceptNodeBatch.size() + inactiveMemberList.size() >= memberTotal) {
                    hasMorePages = false;
                }

                final ConceptResultList currentMemberBatch = populateConcepts(root, refset, lookupParameters);
                
                // filter for inactive concepts
                for (final Concept concept : currentMemberBatch.getItems()) {
                
                    if (concept.isActive()) {
                        activeMemberList.add(concept.getCode());
                    } else {

                        inactiveMemberList.add(concept);
                        inactiveConceptIds += "\"" + concept.getCode() + "\", ";
                    }
                }
            }
        }
        
        inactiveConceptIds = StringUtils.removeEnd(inactiveConceptIds, ", ");
        final String conceptDetailsBaseUrl = SnowstormConnection.BASE_URL + "browser/" + branchPath + "/concepts?conceptIds=";
        boolean searchAgain = true;
        int searchIndex = 0;
        
        if (inactiveMemberList.size() == 0) {
            return status;
        }
        
        // Change the numbers to '1's to avoid threading 
        final ExecutorService executor = new ThreadPoolExecutor(30, 30, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(30), new ThreadPoolExecutor.CallerRunsPolicy());
        
        // use /browser/MAIN/concepts/bulk-load POST call to get the inactive concept details including descriptions and reasons (root.associationTargets)
        while (searchAgain) {
            
            searchAgain = false;
            String bodyConceptIds = "";
            
            for (; searchIndex < inactiveMemberList.size(); searchIndex++) {
                
                Concept member = inactiveMemberList.get(searchIndex);
                final String conceptId = member.getCode();
                final UpgradeInactiveConcecpt inactiveConcept = new UpgradeInactiveConcecpt();
                inactiveConcept.setRefsetId(refsetId);
                inactiveConcept.setCode(member.getCode());
                inactiveConcept.setStillMember(true);
                inactiveData.put(conceptId, inactiveConcept);
                bodyConceptIds += conceptId + ",";
                
                if (conceptDetailsBaseUrl.length() + bodyConceptIds.length() >= URL_MAX_CHAR_LENGTH) {
                    
                    searchAgain = true;
                    break;
                }
            }
            
            bodyConceptIds = StringUtils.removeEnd(bodyConceptIds, ",");
            
            final String memberDetailsUrl = conceptDetailsBaseUrl + bodyConceptIds;
            Iterator<JsonNode> iterator = null;
            
            logger.debug("compileUpgradeData member details URL: " + memberDetailsUrl);
            
            try (final Response response = SnowstormConnection.getResponse(memberDetailsUrl)) {

                final String resultString = response.readEntity(String.class);

                // Only process payload if Rest call is successful
                if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                    
                    searchAgain = false;
                    throw new Exception("call to url '" + memberDetailsUrl + "' wasn't successful. " + response.toString());
                }

                final JsonNode root = mapper.readTree(resultString.toString());
                iterator = root.get("items").iterator();
                
                // loop thru the returned member details and process the descriptions and replacement concepts
                while (iterator != null && iterator.hasNext()) {

                    final JsonNode conceptNode = iterator.next();
                    final UpgradeInactiveConcecpt inactiveConcept = inactiveData.get(conceptNode.get("conceptId").asText());
                    
                    if (conceptNode.get("descriptions") != null) {
                        
                        final List<Map<String, String>> descriptions = populateDescriptions(inactiveConcept.getCode(), conceptNode.get("descriptions"), refset, nonDefaultPreferredTerms);
                        inactiveConcept.setDescriptions(ModelUtility.toJson(descriptions));
                    }
                    
                    final JsonNode associationTargets = conceptNode.get("associationTargets");

                    if (associationTargets != null && associationTargets.size() != 0 && associationTargets.fields() != null) {

                        final Map<String, String> reasonMap = new HashMap<>();
                        Entry<String, JsonNode> entry = associationTargets.fields().next();
                        final List<Concept> replacementConceptsToLookup = new ArrayList<>();
                        String reason = entry.getKey();
                        String replacementConceptIds = entry.getValue().toString();
                        
                        if (replacementConceptIds.contains("[")) {
                          replacementConceptIds = replacementConceptIds.substring(1, replacementConceptIds.length() - 1);
                        }
                        
                        replacementConceptIds = replacementConceptIds.replaceAll("\"", "");
                        
                        for (String replacementConceptId : replacementConceptIds.split(",")) {
                            
                            replacementCount++;
                            reasonMap.put(replacementConceptId, reason);
                            replacementConceptsToLookup.add(new Concept(replacementConceptId));
                        }
                        
                        // process descriptions of any replacement concepts 
                        if (replacementConceptsToLookup.size() > 0) {
                            
                            executor.submit(new Runnable() {
                                
                                /* see superclass */
                                @Override
                                public void run() {
                               
                                    try (final TerminologyService threadService = new TerminologyService()) {

                                        threadService.setModifiedBy(user.getUserName());
                                        threadService.setModifiedFlag(true);
                            
                                        //logger.debug("%%%%%%%%% getMemberList IN THREAD ID: " + Thread.currentThread().getId());
                                        populateAllLanguageDescriptions(refset, replacementConceptsToLookup);
                                        
                                        for (final Concept replacementConcept: replacementConceptsToLookup) {
                                            
                                            final UpgradeReplacementConcecpt upgradeReplacementConcecpt = new UpgradeReplacementConcecpt();
                                            upgradeReplacementConcecpt.setCode(replacementConcept.getCode());
                                            upgradeReplacementConcecpt.setReason(reasonMap.get(replacementConcept.getCode()));
                                            
                                            if (conceptNode.get("descriptions") != null) {
                                                upgradeReplacementConcecpt.setDescriptions(ModelUtility.toJson(conceptNode.get("descriptions")));
                                            }
                                            
                                            threadService.add(upgradeReplacementConcecpt);
                                            inactiveConcept.getReplacementConcecpts().add(upgradeReplacementConcecpt);
                                        }
                                        
                                        threadService.add(inactiveConcept);
                                        
                                    } catch (Exception e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                            });
                            
                        } else {
                            service.add(inactiveConcept);
                        }
                    }
                }
                
                // set the refset into IN_UPGRADE status
                WorkflowService.setWorkflowStatusByAction(user, WorkflowService.UPGRADE, refset, "");
            }
        }
        
        executor.shutdown();
        executor.awaitTermination(600, TimeUnit.SECONDS);
        
        return status;
    }
    
    /**
     * get the stored the data to upgrade a refset.
     *
     * @param service the Terminology Service
     * @param refsetInternalId the internal refset ID
     * @return The upgrade data
     * @throws Exception the exception
     */
    public static ResultList<UpgradeInactiveConcecpt> getUpgradeData(final TerminologyService service, final User user, final String refsetInternalId) throws Exception {
        
        final Refset refset = RefsetService.getRefset(user, refsetInternalId);
        final String refsetId = refset.getRefsetId();
        final PfsParameter pfs = new PfsParameter();
        pfs.setSort("code");
        
        ResultList<UpgradeInactiveConcecpt> results = service.find("refsetId: " + refsetId, pfs, UpgradeInactiveConcecpt.class, null);
        results.setTotal(results.getItems().size());
        results.setTotalKnown(true);
        
        return results;
    }
    
    /**
     * get a single inactive upgrade concept.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param refsetInternalId the internal refset ID
     * @param inactiveConceptId the code of the inactive upgrade concept to get
     * @return The upgrade data
     * @throws Exception the exception
     */
    public static UpgradeInactiveConcecpt getUpgradeConcept(final TerminologyService service, final User user, final String refsetInternalId, final String inactiveConceptId) throws Exception {
        
        final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);
        final String refsetId = refset.getRefsetId();
        
        UpgradeInactiveConcecpt upgradeInactiveConcecpt = service.findSingle("refsetId: " + refsetId + " AND code:" + inactiveConceptId, UpgradeInactiveConcecpt.class, null);
        return upgradeInactiveConcecpt;
    }
    
    /**
     * Make a change to an upgrade concept.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param refsetInternalId the internal refset ID
     * @param inactiveConceptId the concept ID of the inactive concept to be upgraded
     * @param replacementConceptId the concept ID of the replacement concept to be updated
     * @param manualReplacementConcecpt the manual upgrade replacement concept that to be added
     * @param changed a string identifying what has been changed
     * @return the status of the operation
     * @throws Exception the exception
     */
    public static String modifyUpgradeConcept(final TerminologyService service, final User user, final String refsetInternalId, final String inactiveConceptId, 
        final String replacementConceptId, final UpgradeReplacementConcecpt manualReplacementConcecpt, final String changed) throws Exception {
        
        try {
            
            RefsetMemberService.refsetsBeingUpdated.add(refsetInternalId);
            
            List<String> unchangedConcepts;
            final List<String> replacementChangeStatuses = Arrays.asList(REPLACEMENT_REMOVED, REPLACEMENT_ADDED, REMOVED_MANUAL_REPLACEMENT);
            boolean add = true;
            String changeText = "added";
            boolean memberChange = true;
            final UpgradeInactiveConcecpt upgradeInactiveConcecpt = getUpgradeConcept(service, user, refsetInternalId, inactiveConceptId);
            UpgradeReplacementConcecpt upgradeReplacementConcecpt = null;
            String conceptIdToChange = inactiveConceptId;
            
            if (changed.contains("REMOVED")) {
                
                add = false;
                changeText = "removed";
            }
            
            // if the operation needs it get the stored replacement concept
            if (replacementChangeStatuses.contains(changed)) {
                 
                for (UpgradeReplacementConcecpt replacementConcecpt : upgradeInactiveConcecpt.getReplacementConcecpts()) {
                    
                    if (replacementConcecpt.getCode().equals(replacementConceptId)) {
                        
                        upgradeReplacementConcecpt = replacementConcecpt;
                        conceptIdToChange = replacementConceptId;
                        
                        if (changed.equals(REMOVED_MANUAL_REPLACEMENT) && !upgradeReplacementConcecpt.isAdded()) {
                            memberChange = false;
                        }
                    }
                }
            } else if (changed.equals(NEW_MANUAL_REPLACEMENT)) {
                
                upgradeReplacementConcecpt = manualReplacementConcecpt;
                memberChange = false;
                conceptIdToChange = upgradeReplacementConcecpt.getCode();
                
                // save the replacement concept and add it to the inactive concept
                service.add(upgradeReplacementConcecpt);
                upgradeInactiveConcecpt.getReplacementConcecpts().add(upgradeReplacementConcecpt);
            }
            
            logger.debug("modifyUpgradeConcept: member change: " + conceptIdToChange);
            
            // add or remove the concept as a member to the refset
            if (memberChange) {
                
                RefsetMemberService.refsetsUpdatedMembers.put(refsetInternalId, new HashMap<>());
                
                if (add) {
                    unchangedConcepts = RefsetMemberService.addRefsetMembers(user, refsetInternalId, Arrays.asList(conceptIdToChange));
                } else {
                    unchangedConcepts = RefsetMemberService.removeRefsetMembers(user, refsetInternalId, conceptIdToChange);
                }
                
                // see if there the concept was unable to be changed and craft the error message
                if (unchangedConcepts.size() > 0) {
                    return "The concept " + unchangedConcepts.get(0) + " was unable to be " + changeText;
                }
            }
            
            if (changed.equals(INACTIVE_REMOVED)) {
                upgradeInactiveConcecpt.setStillMember(false);
                
            } else if (replacementChangeStatuses.contains(changed)) {
                
                if (upgradeReplacementConcecpt == null) {
                    throw new Exception("Unable to find replacement concept code");
                }
                
                if (memberChange) {
                    upgradeInactiveConcecpt.setReplaced(add);
                    logger.debug("modifyUpgradeConcept: inactive concept marked as replaced = " + add);
                }
                
                // save or remove the replacement concept
                if (changed.equals(REMOVED_MANUAL_REPLACEMENT)) {
                    
                    upgradeInactiveConcecpt.getReplacementConcecpts().remove(upgradeReplacementConcecpt);
                    service.remove(upgradeReplacementConcecpt);
                    logger.debug("modifyUpgradeConcept: removed the replacement concept: " + conceptIdToChange);
                } else {
                    
                    upgradeReplacementConcecpt.setAdded(add);
                    service.update(upgradeReplacementConcecpt);
                    logger.debug("modifyUpgradeConcept: updated the replacement concept: " + conceptIdToChange);
                }
                
            } else if (changed.equals(INACTIVE_ADDED)) {
                upgradeInactiveConcecpt.setStillMember(true);
            }
              
            // save the inactive concept
            service.update(upgradeInactiveConcecpt);
            
            return "All changes made successfully";
            
        } catch (final Exception e) {
            throw new Exception(e);
        }
        
        finally {
            RefsetMemberService.refsetsBeingUpdated.remove(refsetInternalId);
        }
    }

}
