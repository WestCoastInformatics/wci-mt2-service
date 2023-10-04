/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.persistence.Query;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.handler.ExportHandler;
import org.ihtsdo.refsetservice.handler.TerminologyServerHandler;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.RefsetMemberComparison;
import org.ihtsdo.refsetservice.model.UpgradeInactiveConcept;
import org.ihtsdo.refsetservice.model.UpgradeReplacementConcept;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ConceptLookupParameters;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.ihtsdo.refsetservice.util.FileUtility;
import org.ihtsdo.refsetservice.util.HandlerUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.ihtsdo.refsetservice.util.TaxonomyParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.type.TypeReference;
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
public final class RefsetMemberService {

	/** The Constant LOG. */
	private static final Logger LOG = LoggerFactory.getLogger(RefsetMemberService.class);

	/** The terminology handler. */
	private static TerminologyServerHandler terminologyHandler;

	/** The simple date format. */
	public static final String DATE_FORMAT = "yyyyMMdd";

	/** The refset to language map. */
	private static final Map<String, String> REFSET_TO_LANGUAGES_MAP = new HashMap<>();

	/** The description term. */
	public static final String DESCRIPTION_TERM = "term";

	/** The description type. */
	private static final String DESCRIPTION_TYPE = "type";

	/** The description language. */
	private static final String DESCRIPTION_LANGUAGE = "language";

	/** The description language. */
	private static final String DESCRIPTION_ID = "descriptionId";

	/** The description language code. */
	private static final String LANGUAGE_CODE = "languageCode";

	/** The description language code and type combined. */
	public static final String LANGUAGE_ID = "languageId";

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
	private static String exportFileDir;

	/** The local server url to download exported refset files. */
	private static final String EXPORT_DOWNLOAD_URL = "export/download/";

	/** A list of refset actively being updated. */
	public static final Set<String> REFSETS_BEING_UPDATED = new HashSet<>();

	/** A list of refset actively being updated. */
	public static final Map<String, Map<String, Map<String, String>>> REFSETS_UPDATED_MEMBERS = new HashMap<>();

	/** A cache of the members returned for a specific URL. */
	public static final Map<String, Map<String, ConceptResultList>> CONCEPTS_CALL_CACHE = new HashMap<>();

	/** A cache of the details for any concept. */
	public static final Map<String, Map<String, Concept>> CONCEPT_DETAILS_CACHE = new HashMap<>();

	/** A cache of the taxonomy ancestor path for concepts. */
	public static final Map<String, Map<String, Concept>> TAXONOMY_SEARCH_ANCESTORS_CACHE = new HashMap<>();

	/** A cache of the children for each tree node. */
	private static final Map<String, Map<String, ConceptResultList>> TREE_CACHE = new HashMap<>();

	/** A cache of the children for each tree node. */
	public static final Map<String, Map<String, Set<String>>> ANCESTORS_CACHE = new HashMap<>();

	/** The Constant CONCEPT_DESCRIPTIONS_PER_CALL. */
	public static final int CONCEPT_DESCRIPTIONS_PER_CALL = 250;

	/** The Constant URL_MAX_CHAR_LENGTH - URLs will error if larger. */
	public static final int URL_MAX_CHAR_LENGTH = 6000;

	/** The max number of record elasticsearch will return without erroring. */
	public static final int ELASTICSEARCH_MAX_RECORD_LENGTH = 9990;

	/** The column index of the concept ID in an RF2 file. */
	public static final int REFEST_RF2_CONCEPTID_COLUMN = 5;

	/**
	 * The number of milliseconds to stop processing records to avoid a gateway
	 * timeout.
	 */
	public static final int TIMEOUT_MILLISECOND_THRESHOLD = 60000;

	/** Snomed code for prefrered term in English *. */
	public static final String PREFERRED_TERM_EN = "900000000000509007PT";

	/** The Constant REFSET_TO_PUBLISHED_VERSION_MAP. */
	public static final Map<String, List<Long>> REFSET_TO_PUBLISHED_VERSION_MAP = new HashMap<>();

	static {

		exportFileDir = PropertyUtility.getProperty("export.fileDir") + File.separator;

		// TODO: Remove once Edition updated
		REFSET_TO_LANGUAGES_MAP.put("450828004", "es");
		REFSET_TO_LANGUAGES_MAP.put("32570271000036106", "en");
		REFSET_TO_LANGUAGES_MAP.put("900000000000509007", "en");
		REFSET_TO_LANGUAGES_MAP.put("21000172104", "fr");
		REFSET_TO_LANGUAGES_MAP.put("31000172101", "nl");
		REFSET_TO_LANGUAGES_MAP.put("554461000005103", "da");
		REFSET_TO_LANGUAGES_MAP.put("71000181105", "et");
		REFSET_TO_LANGUAGES_MAP.put("5641000179103", "es");
		REFSET_TO_LANGUAGES_MAP.put("21000220103", "en");
		REFSET_TO_LANGUAGES_MAP.put("61000202103", "no");
		REFSET_TO_LANGUAGES_MAP.put("46011000052107", "sv");

		// Instantiate terminology handler
		try {
			String key = "terminology.handler";
			String handlerName = PropertyUtility.getProperty(key);
			if (handlerName.isEmpty()) {
				throw new Exception("terminology.handler expected and does not exist.");
			}

			terminologyHandler = HandlerUtility.newStandardHandlerInstanceWithConfiguration(key, handlerName,
					TerminologyServerHandler.class);

		} catch (Exception e) {
			LOG.error("Failed to initialize terminology.handler - serious error", e);
			terminologyHandler = null;
		}
	}

	/**
	 * Instantiates an empty {@link RefsetMemberService}.
	 */
	private RefsetMemberService() {

		// n/a
	}

	/**
	 * Get the refset member concepts.
	 *
	 * @param service            the Terminology Service
	 * @param user               the user
	 * @param refsetInternalId   the internal refset ID
	 * @param searchParameters   the search parameters
	 * @param displayType        Should results be a list or hierarchical taxonomy
	 * @param taxonomyParameters the taxonomy parameters
	 * @return the refset member concepts
	 * @throws Exception the exception
	 */
	public static ConceptResultList getRefsetMembers(final TerminologyService service, final User user,
			final String refsetInternalId, final SearchParameters searchParameters, final String displayType,
			final TaxonomyParameters taxonomyParameters) throws Exception {

		ConceptResultList concepts = new ConceptResultList();

		final Refset refset = getRefset(user, service, refsetInternalId);
		final List<String> nonDefaultPreferredTerms = identifyNonDefaultPreferredTerms(refset.getEdition());

		// the next call depend if a list or taxonomy is being returned
		if (displayType.equals("list")) {

			concepts = getMemberList(refset, nonDefaultPreferredTerms, searchParameters);
			LOG.debug("Refset has " + concepts.size() + " members");

		} else {

			concepts = getMemberTaxonomy(refset, nonDefaultPreferredTerms, taxonomyParameters);
		}

		// LOG.debug("getRefsetMembers results: " + ModelUtility.toJson(concepts));

		return concepts;
	}

	/**
	 * Get the refset.
	 *
	 * @param user             the user
	 * @param service          the Terminology Service
	 * @param refsetInternalId the internal refset ID
	 * @return the refset
	 * @throws Exception the exception
	 */
	public static Refset getRefset(final User user, final TerminologyService service, final String refsetInternalId)
			throws Exception {

		Refset refset = service.get(refsetInternalId, Refset.class);

		if (refset == null) {

			throw new Exception(
					"Reference Set Internal Id: " + refsetInternalId + " does not exist in the RT2 database");
		}

		refset = RefsetService.setRefsetPermissions(user, refset);

		return refset;
	}

	/**
	 * Called recursively to accumulate all refset members in order to compose a
	 * freeset.
	 *
	 * @param service          the Terminology Service
	 * @param refsetInternalId the internal refset ID
	 * @param searchAfter      the searchAfter identifier
	 * @param concepts         the list of concepts
	 * @return the list of member concepts
	 * @throws Exception the exception
	 */
	public static List<Concept> getAllRefsetMembers(final TerminologyService service, final String refsetInternalId,
			final String searchAfter, final List<Concept> concepts) throws Exception {

		return terminologyHandler.getAllRefsetMembers(service, refsetInternalId, searchAfter, concepts);
	}

	/**
	 * Identify non default preferred terms.
	 *
	 * @param edition the edition
	 * @return the list
	 */
	public static List<String> identifyNonDefaultPreferredTerms(final Edition edition) {

		final List<String> nonDefaultPreferredTerms = new ArrayList<>();
		// get the list of languages the refset supports
		nonDefaultPreferredTerms.addAll(REFSET_TO_LANGUAGES_MAP.keySet().stream().collect(Collectors.toList()));

		final String languageToRemove = (edition.getDefaultLanguageCode()) == null ? "en"
				: edition.getDefaultLanguageCode();

		if (edition.getDefaultLanguageCode() != null) {

			REFSET_TO_LANGUAGES_MAP.entrySet().stream().filter(entry -> languageToRemove.equals(entry.getValue()))
					.map(Map.Entry::getKey).forEach((val) -> {

						nonDefaultPreferredTerms.remove(val);
					});
		}

		// remove the default language and any languages that are not in the
		// edition's default list
		nonDefaultPreferredTerms
				.removeIf(languageRefset -> !edition.getDefaultLanguageRefsets().contains(languageRefset));

		// make sure every refset includes English as a fall back language
		if (edition.getDefaultLanguageCode() != null && !edition.getDefaultLanguageCode().equals("en")
				&& !nonDefaultPreferredTerms.contains("en")) {

			nonDefaultPreferredTerms.add("en");
		}

		Collections.sort(nonDefaultPreferredTerms);

		return nonDefaultPreferredTerms;
	}

	/**
	 * Sort descriptions.
	 *
	 * @param conceptId                the concept id
	 * @param descriptions             the descriptions
	 * @param refset                   the refset
	 * @param nonDefaultPreferredTerms the non-default preferred terms
	 * @return the map
	 * @throws Exception the exception
	 */
	public static List<Map<String, String>> sortConceptDescriptions(final String conceptId,
			final Set<Map<String, String>> descriptions, final Refset refset,
			final List<String> nonDefaultPreferredTerms) throws Exception {

		// Sort descriptions in the order defined below.
		//
		// 1) PT � Default Lang Code
		// 2) FSN
		// 3) All other PTs
		// -- a. Order by language code
		// -- b. If no translation, will be null

		// do this for each concept
		final List<Map<String, String>> sortedDescriptionList = new ArrayList<>();
		final Map<String, Set<Map<String, String>>> sortingMap = new HashMap<>();

		// Actual code
		for (final Map<String, String> descriptionMap : descriptions) {

			final String languageId = descriptionMap.get(LANGUAGE_ID);

			if (!sortingMap.containsKey(languageId)) {

				sortingMap.put(languageId, new HashSet<Map<String, String>>());
			}

			// Handle the default language
			if (descriptionMap.get(DESCRIPTION_LANGUAGE).equals(refset.getEdition().getDefaultLanguageCode())) {

				if (descriptionMap.get(DESCRIPTION_TYPE).equalsIgnoreCase("fsn")) {

					if (sortingMap.containsKey(languageId) && !sortingMap.get(languageId).isEmpty()) {

						displayDuplicateWarning("A FSN in the default language", conceptId,
								descriptionMap.get(DESCRIPTION_LANGUAGE), sortingMap.get(languageId), descriptionMap);
						continue;
					}

					sortingMap.get(languageId).add(descriptionMap);

				} else {

					if ("pt".equalsIgnoreCase(descriptionMap.get(DESCRIPTION_TYPE))
							&& sortingMap.containsKey(languageId) && !sortingMap.get(languageId).isEmpty()) {

						displayDuplicateWarning("A PT in the default language", conceptId,
								descriptionMap.get(DESCRIPTION_LANGUAGE), sortingMap.get(languageId), descriptionMap);
						continue;
					}

					sortingMap.get(languageId).add(descriptionMap);
				}

			}

			// Handle the non-default languages
			else {

				if (descriptionMap.get(DESCRIPTION_TYPE).equalsIgnoreCase("fsn")) {

					if (sortingMap.containsKey(languageId) && !sortingMap.get(languageId).isEmpty()) {

						displayDuplicateWarning("A FSN in a non-default language", conceptId,
								descriptionMap.get(DESCRIPTION_LANGUAGE), sortingMap.get(languageId), descriptionMap);
						continue;
					}

					sortingMap.get(languageId).add(descriptionMap);

				} else {

					if ("pt".equalsIgnoreCase(descriptionMap.get(DESCRIPTION_TYPE))
							&& sortingMap.containsKey(languageId) && !sortingMap.get(languageId).isEmpty()) {

						displayDuplicateWarning("A PT in a non-default language", conceptId,
								descriptionMap.get(DESCRIPTION_LANGUAGE), sortingMap.get(languageId), descriptionMap);
						continue;
					}

					sortingMap.get(languageId).add(descriptionMap);
				}

			}

		}

		final List<Map<String, String>> languageRefsets = refset.getEdition().getFullyQualifiedLanguageRefsets();

		final Set<String> languageIdsProcessed = new HashSet<>();

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
		final Set<String> textDescriptionLanguageIds = new HashSet<>();

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
	 * @param errorMessage         the error msg
	 * @param conceptId            the con id
	 * @param language             the language
	 * @param existingDescriptions the existing descriptions
	 * @param descriptionMap       the desc map
	 */
	private static void displayDuplicateWarning(final String errorMessage, final String conceptId,
			final String language, final Set<Map<String, String>> existingDescriptions,
			final Map<String, String> descriptionMap) {

		LOG.warn(errorMessage + "(" + language + ") has already been identified for conceptId: " + conceptId);

		LOG.warn("Original ones identified:");

		for (final Map<String, String> set : existingDescriptions) {

			printDescription(set);
		}

		LOG.warn("New one encountered: " + printDescription(descriptionMap));
	}

	/**
	 * Prints the description.
	 *
	 * @param description the map
	 * @return the string
	 */
	private static String printDescription(final Map<String, String> description) {

		return "Type = " + description.get(DESCRIPTION_TYPE) + " for lang = " + description.get(DESCRIPTION_LANGUAGE)
				+ " with term = " + description.get(DESCRIPTION_TERM);
	}

	/**
	 * Gets the refset to languages map.
	 *
	 * @return the REFSET_TO_LANGUAGES_MAP
	 */
	public static Map<String, String> getRefsetToLanguagesMap() {

		return REFSET_TO_LANGUAGES_MAP;
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
		final List<String> directoryColumns = Arrays.asList("id", "refsetId", "name", "editionName", "organizationName",
				"versionStatus", "versionDate", "modified", "privateRefset");
		String snowstormQuery = "";
		final String[] queryParts = query.split(" AND ");

		for (final String queryPart : queryParts) {

			final String[] keyValue = queryPart.split(":");

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
	 * Multisearch descriptions of reference sets.
	 *
	 * @param snowstormQuery the query string
	 * @return JSON string search results
	 * @throws Exception the exception
	 */
	private static Set<String> getDirectoryMembers(final String snowstormQuery) throws Exception {

		return terminologyHandler.getDirectoryMembers(snowstormQuery);
	}

	/**
	 * Multisearch of descriptions.
	 *
	 * @param searchParameters        the search parameters
	 * @param ecl                     ECL to narrow search
	 * @param nonPublishedBranchPaths a set of non-published branch paths to search
	 *                                in addition to all published branches
	 * @return Collection of conceptIds as strings.
	 * @throws Exception the exception
	 */
	public static Set<String> searchMultisearchDescriptions(final SearchParameters searchParameters, final String ecl,
			final Set<String> nonPublishedBranchPaths) throws Exception {

		return terminologyHandler.searchMultisearchDescriptions(searchParameters, ecl, nonPublishedBranchPaths);

	}

	/**
	 * Export latest version of published all refsets for a project.
	 *
	 * @param service        the service
	 * @param user           the user
	 * @param projectId      the project id
	 * @param type           the type
	 * @param languageId     the language id
	 * @param fileNameDate   the file name date
	 * @param exportMetadata the export metadata
	 * @param withNames      the with names
	 * @return the string
	 * @throws Exception the exception
	 */
	@SuppressWarnings("unchecked")
	public static String exportAllRefsetsRf2ForProject(final TerminologyService service, final User user,
			final String projectId, final String type, final String languageId, final String fileNameDate,
			final boolean exportMetadata, final boolean withNames) throws Exception {

		final List<String> refsetFiles = new ArrayList<>();
		final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");

		final Query query = service.getEntityManager().createNativeQuery(
				"SELECT refsetId, MAX(versionDate) FROM refsets WHERE project_Id = :projectId AND versionStatus = 'PUBLISHED' GROUP BY project_id, refsetId")
				.setParameter("projectId", projectId);
		final List<Object[]> queryResults = query.getResultList();

		if (queryResults == null || queryResults.isEmpty()) {

			throw new Exception("Found no published Reference Sets for project id " + projectId + " to export.");
		}

		try {

			for (final Object[] o : queryResults) {

				final SearchParameters searchParameters = new SearchParameters();
				final String versionDate = simpleDateFormat.format(o[1]);
				searchParameters.setQuery("refsetId:" + o[0].toString() + " AND versionDate:" + versionDate
						+ " AND versionStatus:PUBLISHED");
				final ResultList<Refset> refsetList = RefsetService.searchRefsets(user, service, searchParameters,
						false, false, false, false, false);

				if (refsetList != null && refsetList.getItems() != null && !refsetList.getItems().isEmpty()) {

					for (final Refset refset : refsetList.getItems()) {

						RefsetService.setRefsetPermissions(user, refset);

						final String fileName = exportRefsetRf2File(service, refset.getId(), type, languageId,
								fileNameDate, null, versionDate.replace("-", ""), exportMetadata, withNames);
						refsetFiles.add(exportFileDir + fileName);
					}

				}

			}

			// add all files into a zip file
			final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyymmdd-hhmmss");
			final String zipFileName = String.format("RT2-Downloaded-refsets-%s.zip", dateFormat.format(new Date()));
			zipFiles(refsetFiles, exportFileDir + zipFileName);
			return EXPORT_DOWNLOAD_URL + zipFileName;

		} catch (final Exception ex) {

			throw new Exception("Failed to export zip file name " + ex.getMessage(), ex);
		}

	}

	/**
	 * Get the refset member concepts in RF2 format.
	 *
	 * @param service                the Terminology Service
	 * @param refsetInternalId       the internal refset ID
	 * @param type                   the type
	 * @param languageId             the language to display names in
	 * @param fileNameDate           the file name date
	 * @param startEffectiveTime     the start effective time
	 * @param transientEffectiveTime the transient effective time
	 * @param exportMetadata         should refset metadata be included in the
	 *                               export
	 * @param withNames              the with names
	 * @return the refset member concepts
	 * @throws Exception the exception
	 */
	public static String exportRefsetRf2(final TerminologyService service, final String refsetInternalId,
			final String type, final String languageId, final String fileNameDate, final String startEffectiveTime,
			final String transientEffectiveTime, final boolean exportMetadata, final boolean withNames)
			throws Exception {

		return EXPORT_DOWNLOAD_URL + exportRefsetRf2File(service, refsetInternalId, type, languageId, fileNameDate,
				startEffectiveTime, transientEffectiveTime, exportMetadata, withNames);
	}

	/**
	 * Get the refset member concepts in RF2 format.
	 *
	 * @param service                the Terminology Service
	 * @param refsetInternalId       the internal refset ID
	 * @param type                   the type
	 * @param languageId             the language to display names in
	 * @param fileNameDate           the file name date
	 * @param startEffectiveTime     the start effective time
	 * @param transientEffectiveTime the transient effective time
	 * @param exportMetadata         should refset metadata be included in the
	 *                               export
	 * @param withNames              the with names
	 * @return the refset member concepts
	 * @throws Exception the exception
	 */
	private static String exportRefsetRf2File(final TerminologyService service, final String refsetInternalId,
			final String type, final String languageId, final String fileNameDate, final String startEffectiveTime,
			final String transientEffectiveTime, final boolean exportMetadata, final boolean withNames)
			throws Exception {

		final Set<String> dates = new HashSet<>();
		dates.add(transientEffectiveTime);

		if (startEffectiveTime != null) {

			dates.add(startEffectiveTime);
		}

		final ExportHandler exporter = new ExportHandler();

		try {

			final Refset refset = RefsetService.getRefset(service, SecurityService.getUserFromSession(),
					refsetInternalId);

			if (refset == null) {

				throw new Exception(
						"Reference Set Internal Id: " + refsetInternalId + " does not exist in the RT2 database");
			}

			final String awsVersionedPath = exporter.generateAwsBaseVersionPath(refset, type, dates);

			final String rt2VersionFileName = exporter.generateRt2VersionFileName(refset, type, languageId, dates,
					exportMetadata, withNames);

			// Check if file already exists
			if (!S3ConnectionWrapper.isInS3Cache(awsVersionedPath, rt2VersionFileName)) {
				// Rt2 Version File doesn't reside on s3

				// Snowstorm generated RF2 file
				final String snowGeneratedFileName = exporter.generateSnowVersionFileName(refset, type, dates);

				// Local place to store snowBaseVersionFileName
				final Path localSnowGeneratedTempDir = Files.createTempDirectory("rt2LocalSnowGenerated-");

				// Local Snowstorm generated Rf2 file name
				final String localSnowGeneratedFilePath = localSnowGeneratedTempDir + File.separator
						+ snowGeneratedFileName;

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
									: ",  \"transientEffectiveTime\": \"" + transientEffectiveTime + "\"")
							+ "}";

					LOG.debug("generating file from snowstorm");
					// Generate on SnowS
					final String snowGeneratedFileUrl = exporter.generateSnowVersionFile(entityString);

					LOG.debug("Downloading file from snowstorm");
					// Download file from SnowS
					exporter.downloadSnowGeneratedFile(snowGeneratedFileUrl, localSnowGeneratedFilePath);

					LOG.debug("uploading snowstorm genned file to S3");
					// store file one s3
					S3ConnectionWrapper.uploadToS3(awsVersionedPath, localSnowGeneratedTempDir.toString(),
							snowGeneratedFileName);
				} else {

					LOG.debug("Downloading snowstorm genned file from S3");
					S3ConnectionWrapper.downloadFileFromS3(awsVersionedPath, snowGeneratedFileName,
							localSnowGeneratedFilePath);
				}

				LOG.debug("converting snowstorm genned file to RT2 format");
				// Have access to localSnowGeneratedFilePath from which rt2 will
				// generate the
				// export file
				generateRt2ExportFile(refset, localSnowGeneratedFilePath, rt2VersionFileName, exportMetadata, withNames,
						languageId);

				S3ConnectionWrapper.uploadToS3(awsVersionedPath, exportFileDir, rt2VersionFileName);

				FileUtility.deleteDirectory(localSnowGeneratedTempDir.toFile());

			} else {

				if (!Files.exists(Path.of(exportFileDir + rt2VersionFileName))) {

					LOG.debug("Downloading RT2 genned file from S3");
					S3ConnectionWrapper.downloadFileFromS3(awsVersionedPath, rt2VersionFileName,
							exportFileDir + rt2VersionFileName);
				}

			}

			LOG.debug("Final Export File Path: " + exportFileDir + rt2VersionFileName);

			// if download is from RT2 server
			// ServletUriComponentsBuilder builder =
			// ServletUriComponentsBuilder.fromCurrentContextPath();
			return rt2VersionFileName;

		} catch (final Exception ex) {

			throw new Exception("Failed to export zip file name " + ex.getMessage(), ex);
		}

	}

	/**
	 * Get the refset member concepts in RF2 DELTA format.
	 *
	 * @param service                the Terminology Service
	 * @param user                   the user
	 * @param refsetInternalId       the internal refset ID
	 * @param type                   the type
	 * @param languageId             the language to display names in
	 * @param fileNameDate           the file name date
	 * @param startEffectiveTime     the start effective time
	 * @param transientEffectiveTime the transient effective time
	 * @param exportMetadata         should refset metadata be included in the
	 *                               export
	 * @param withNames              the with names
	 * @return the refset member concepts
	 * @throws Exception the exception
	 */
	public static String exportRefsetRf2Delta(final TerminologyService service, final User user,
			final String refsetInternalId, final String type, final String languageId, final String fileNameDate,
			final String startEffectiveTime, final String transientEffectiveTime, final boolean exportMetadata,
			boolean withNames) throws Exception {

		boolean lWithNames = withNames;

		// TODO: Turn this into a method variable
		final Set<String> dates = new HashSet<>();
		dates.add(transientEffectiveTime);

		if (startEffectiveTime != null) {

			dates.add(startEffectiveTime);
		}

		final ExportHandler exporter = new ExportHandler();

		try {

			final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);

			if (refset == null) {

				throw new Exception(
						"Reference Set Internal Id: " + refsetInternalId + " does not exist in the RT2 database");
			}

			final String deltaAwsVersionedPath = exporter.generateAwsBaseVersionPath(refset, type, dates);

			final String deltaRt2VersionFileName = exporter.generateRt2VersionFileName(refset, type, languageId, dates,
					exportMetadata, lWithNames);

			final String deltaSnowGeneratedFileName = exporter.generateSnowVersionFileName(refset, "DELTA", dates);

			// Check if delta file already exists
			if (!S3ConnectionWrapper.isInS3Cache(deltaAwsVersionedPath, deltaRt2VersionFileName)) {

				// determine all snapshot versions that will contribute to the delta
				final List<Map<String, String>> versionMap = RefsetService.getSortedRefsetVersionList(refset, service,
						true);
				final Map<String, String> versionToRefsetInternalId = new HashMap<>();
				final List<String> versionsInScope = new ArrayList<>();

				for (final Map<String, String> entry : versionMap) {

					final String candidateVersion = entry.get("date");

					if (candidateVersion != null
							&& candidateVersion.replaceAll("-", "").compareTo(startEffectiveTime) > 0
							&& candidateVersion.replaceAll("-", "").compareTo(transientEffectiveTime) <= 0) {

						versionsInScope.add(candidateVersion);
						versionToRefsetInternalId.put(candidateVersion, entry.get("refsetInternalId"));
					}

				}

				LOG.debug("versionsInScope " + versionsInScope);

				// Local place to store snowBaseVersionFileName
				final Path localSnowGeneratedTempDir = Files.createTempDirectory("rt2LocalSnowGenerated-");

				// build fileContentsArray with contents from each snapshot version
				String headerLine = null;
				final List<String> fileContentsArray = new ArrayList<>();

				for (final String versionInScope : versionsInScope) {

					if (versionInScope == null) {
						continue;
					}

					dates.clear();
					dates.add(versionInScope.replaceAll("-", ""));

					Refset refsetVersion = refset;

					try {

						refsetVersion = RefsetService.getRefset(service, user, refset.getRefsetId(), versionInScope);

					} catch (final Exception ex) {

						// N/A
					}

					final String awsVersionedPath = exporter.generateAwsBaseVersionPath(refset, "DELTA-SNAPSHOT",
							dates);

					// NOT USED final String rt2VersionFileName =
					// exporter.generateRt2VersionFileName(refset, "SNAPSHOT", languageId, dates,
					// exportMetadata,
					// lWithNames);

					// Snowstorm generated RF2 file
					final String snowGeneratedFileName = exporter.generateSnowVersionFileName(refset, "SNAPSHOT",
							dates);

					// Local Snowstorm generated Rf2 file name
					final String localSnowGeneratedFilePath = localSnowGeneratedTempDir + File.separator
							+ snowGeneratedFileName;

					// Check if SnowS version file name does already exist in S3 cache
					if (!S3ConnectionWrapper.isInS3Cache(awsVersionedPath, snowGeneratedFileName)) {

						// Base-SnowVersion file is not on S3, so generate it, and after downloading it,
						// store it on S3
						final String entityString = "{\"refsetIds\": [\"" + refset.getRefsetId()
								+ "\"],  \"branchPath\": \"" + refsetVersion.getBranchPath()
								+ "\", \"conceptsAndRelationshipsOnly\": false, \"filenameEffectiveDate\": \""
								+ versionInScope.replaceAll("-", "")
								+ "\", \"legacyZipNaming\": false, \"type\": \"SNAPSHOT\", \"unpromotedChangesOnly\": false"
								+ (",  \"startEffectiveTime\": \"" + versionInScope.replaceAll("-", "") + "\"")
								+ (",  \"transientEffectiveTime\": \"" + versionInScope.replaceAll("-", "") + "\"")
								+ "}";

						LOG.info("generating file from snowstorm" + entityString);

						// Generate on SnowS
						final String snowGeneratedFileUrl = exporter.generateSnowVersionFile(entityString);
						LOG.debug("Downloading file from snowstorm, " + localSnowGeneratedFilePath);

						// Download file from SnowS
						exporter.downloadSnowGeneratedFile(snowGeneratedFileUrl, localSnowGeneratedFilePath);
						LOG.debug("uploading snowstorm genned file to S3");

						// store file one s3
						S3ConnectionWrapper.uploadToS3(awsVersionedPath, localSnowGeneratedTempDir.toString(),
								snowGeneratedFileName);

					} else {

						LOG.info("Downloading snowstorm genned file from S3, " + snowGeneratedFileName);
						S3ConnectionWrapper.downloadFileFromS3(awsVersionedPath, snowGeneratedFileName,
								localSnowGeneratedFilePath);
					}

					// append the contents of this snapshot file to the fileContentsArray
					FileUtility.unzip(localSnowGeneratedFilePath, localSnowGeneratedFilePath.replace(".zip", ""));
					final String fileNamePath = localSnowGeneratedFilePath.replace(".zip", "") + File.separator
							+ "SnomedCT_Export" + File.separator + "Snapshot" + File.separator + "Refset"
							+ File.separator + "Content" + File.separator;
					final String[] files = new File(fileNamePath).list();

					if (files != null) {

						if (lWithNames) {

							final String snowGeneratedRf2FilePath = fileNamePath + files[0];
							// NOT USED final String rf2FileName =
							// snowGeneratedRf2FilePath.substring(snowGeneratedRf2FilePath.lastIndexOf(File.separator)
							// + 1);
							final String builderRf2FilePath = fileNamePath + files[0] + ".names";

							final Refset specificRefset = service.findSingle(
									"id:" + versionToRefsetInternalId.get(versionInScope), Refset.class, null);
							appendNamesToRf2(specificRefset, snowGeneratedRf2FilePath, builderRf2FilePath, languageId);

							final File origFile = new File(snowGeneratedRf2FilePath);

							if (origFile.exists()) {

								origFile.delete();
							}

							final File namesFile = new File(builderRf2FilePath);

							if (namesFile.exists()) {

								namesFile.renameTo(origFile);
							}

							lWithNames = false;
						}

						fileContentsArray.addAll(FileUtility.readFileToArray(fileNamePath + files[0]));
					}

					LOG.debug("fileContentsArray after versionInScope " + fileContentsArray.size() + " "
							+ versionInScope);
					LOG.debug("fileContentsArray: " + fileContentsArray);

					// If first file, store header so can print it later
					if (headerLine == null) {

						for (final String line : fileContentsArray) {

							if (line.toLowerCase().startsWith("id")) {

								headerLine = line;
								break;
							}

						}

					}

				}

				// Processed all intermediate files - put in a set to remove duplicates from
				// fileContents
				final Set<String> fileContentsSet = new HashSet<>(fileContentsArray);

				// sort fileContents
				final List<String> fileContentsArrayList = new ArrayList<>(fileContentsSet);
				Collections.sort(fileContentsArrayList);

				// if the files were empty then print out an empty file with just the header
				// line
				if (headerLine == null) {

					final String separator = "\t";
					headerLine = "id" + separator + "effectiveTime" + separator + "active" + separator + "moduleId"
							+ separator + "refsetId" + separator + "referencedComponentId";
				}

				// write fileContents to file
				try (final FileOutputStream fos = new FileOutputStream(
						localSnowGeneratedTempDir.toString() + File.separator + deltaSnowGeneratedFileName);
						final BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));) {

					// Write header onto delta file
					bw.write(headerLine);
					bw.newLine();

					for (final String line : fileContentsArrayList) {

						// Only print the header line once... Was done above
						if (line.toLowerCase().startsWith("id")) {

							continue;
						}

						bw.write(line);
						bw.newLine();
					}

					bw.close();
					fos.close();

				} catch (final IOException e) {

					e.printStackTrace();
				}

				LOG.debug("converting snowstorm genned file to RT2 format");
				// Have access to localSnowGeneratedFilePath from which rt2 will generate the
				// export file
				generateRt2ExportFile(refset,
						localSnowGeneratedTempDir.toString() + File.separator + deltaSnowGeneratedFileName,
						deltaRt2VersionFileName, exportMetadata, lWithNames, languageId);

				LOG.debug("uploading snowstorm genned file to S3");

				// store file on s3
				S3ConnectionWrapper.uploadToS3(deltaAwsVersionedPath, exportFileDir, deltaRt2VersionFileName);
				FileUtility.deleteDirectory(localSnowGeneratedTempDir.toFile());

			} else {

				if (!Files.exists(Path.of(exportFileDir + deltaRt2VersionFileName))) {

					LOG.debug("Downloading RT2 snapshot genned file from S3");
					S3ConnectionWrapper.downloadFileFromS3(deltaAwsVersionedPath, deltaRt2VersionFileName,
							exportFileDir + deltaRt2VersionFileName);
				}

			}

			// if download is from RT2 server
			// final ServletUriComponentsBuilder builder =
			// ServletUriComponentsBuilder.fromCurrentContextPath();
			return EXPORT_DOWNLOAD_URL + deltaRt2VersionFileName;

		} catch (final Exception ex) {

			throw new Exception("Failed to export delta zip file name: " + ex.getMessage(), ex);
		}

	}

	/**
	 * Generate RT2 export file.
	 *
	 * @param refset                     the refset
	 * @param localSnowGeneratedFilePath the local snow generated file path
	 * @param rt2VersionFileName         the rt 2 version file name
	 * @param exportMetadata             the export metadata
	 * @param appendNames                the append names
	 * @param languageId                 the language id
	 * @return the string
	 * @throws Exception the exception
	 */
	private static String generateRt2ExportFile(final Refset refset, final String localSnowGeneratedFilePath,
			final String rt2VersionFileName, final boolean exportMetadata, final boolean appendNames,
			final String languageId) throws Exception {

		// Generate the Rt2 version of refset RF2 Zip file
		final Path builderDirectoryTempDir = Files.createTempDirectory("rt2Builder-");
		boolean noFiles = false;

		LOG.debug("creating builder temp dir: " + builderDirectoryTempDir.toString());

		// Unzip the download if snapshot
		List<String> sourceFiles = new ArrayList<>();

		if (!localSnowGeneratedFilePath.contains("DELTA")) {

			sourceFiles = unzipFiles(localSnowGeneratedFilePath, builderDirectoryTempDir.toString());
		} else {

			sourceFiles.add(localSnowGeneratedFilePath);
		}

		LOG.debug("unzipped source files: " + ModelUtility.toJson(sourceFiles));

		if (sourceFiles.size() > 1) {

			throw new Exception("Unexpected number of files generated by Snowstorm Export RF2: " + sourceFiles.size());
		} else if (sourceFiles.size() == 0) {

			final Path path = Path.of(builderDirectoryTempDir.toString() + File.separator + "noresults.txt");
			Files.write(path,
					("No results for Reference Set " + refset.getRefsetId()).getBytes(StandardCharsets.UTF_8));
			sourceFiles.add(path.toString());
			noFiles = true;
		}

		// If Rf2WithNames selected, append the names to the refset file
		// if there were no files from Snowstorm, there is no need to append names
		if (appendNames && !noFiles) {

			final String snowGeneratedRf2FilePath = sourceFiles.iterator().next();
			final String rf2FileName = snowGeneratedRf2FilePath
					.substring(snowGeneratedRf2FilePath.lastIndexOf(File.separator) + 1);
			final String builderRf2FilePath = builderDirectoryTempDir.toString() + File.separator + rf2FileName;

			appendNamesToRf2(refset, snowGeneratedRf2FilePath, builderRf2FilePath, languageId);

			sourceFiles.clear();
			sourceFiles.add(builderRf2FilePath);
		}

		// remove effectiveTime
		if (!"PUBLISHED".equalsIgnoreCase(refset.getVersionStatus())) {
			final String snowGeneratedRf2FilePath = sourceFiles.iterator().next();
			removeEffectiveTime(snowGeneratedRf2FilePath);
		}

		// if exportMetadata requested, add it
		if (exportMetadata) {

			sourceFiles.add(exportRefsetMetadata(refset, builderDirectoryTempDir));
		}

		LOG.debug("ready to be zipped source files: " + ModelUtility.toJson(sourceFiles));

		// zip the files together
		zipFiles(sourceFiles, exportFileDir + rt2VersionFileName);

		// Delete directory structure and original zip
		FileUtility.deleteDirectory(builderDirectoryTempDir.toFile());

		return exportFileDir;
	}

	/**
	 * Removes the effective time.
	 *
	 * @param origFilePath the orig file path
	 * @throws Exception the exception
	 */
	private static void removeEffectiveTime(final String origFilePath) throws Exception {

		LOG.debug("Removing effectiveTime for non-PUBLISHED refsets.");
		try {
			final Path tempFilePath = Files.createTempFile("temp", ".txt");

			try (final BufferedReader br = new BufferedReader(new FileReader(new File(origFilePath)));
					final BufferedWriter writer = new BufferedWriter(new FileWriter(tempFilePath.toFile()))) {

				String line;
				while ((line = br.readLine()) != null) {
					if (StringUtils.isEmpty(line)) {
						continue;
					}
					if (line.startsWith("id")) {
						writer.write(line);
						writer.newLine();
						continue;
					}

					final String[] tokens = line.split("\t");
					tokens[1] = "";
					final String updatedLine = String.join("\t", tokens);
					writer.write(updatedLine);
					writer.newLine();

				}
			}

			// Replace the original file with the modified temporary file
			Files.move(tempFilePath, Path.of(origFilePath), StandardCopyOption.REPLACE_EXISTING);

		} catch (IOException e) {
			LOG.error("ERROR removing effectiveTime from file {}", origFilePath);
			throw e;
		}
	}

	/**
	 * Append names to rf 2.
	 *
	 * @param refset               the refset
	 * @param origFilePath         the orig file path
	 * @param newFileWithNamesPath the new file with names path
	 * @param languageId           the language id
	 * @throws Exception the exception
	 */
	private static void appendNamesToRf2(final Refset refset, final String origFilePath,
			final String newFileWithNamesPath, final String languageId) throws Exception {

		// Move rf2 file to a tmp (as we create new one below). Update sourceFiles
		// accordingly
		LOG.debug("Appending descriptions to RF2 file");

		// Get member cache
		final List<Concept> conceptsNotInCache = new ArrayList<>();
		final Map<String, Concept> members = new HashMap<>();

		// Read through file and identify those concepts not in cache or don't have all
		// requisite languages populated
		try (BufferedReader br = new BufferedReader(new FileReader(new File(origFilePath)))) {

			String extractedLine = br.readLine();
			extractedLine = br.readLine();

			while (extractedLine != null && !extractedLine.trim().isEmpty()) {

				final String conceptId = extractedLine.split("\t")[REFEST_RF2_CONCEPTID_COLUMN];

				// TODO: Also check doesn't have all needed languages
				if (!members.containsKey(conceptId) || members.get(conceptId).getDescriptions().isEmpty()) {

					final Concept concept = new Concept();
					concept.setCode(conceptId);
					conceptsNotInCache.add(concept);
				}

				extractedLine = br.readLine();

				if (conceptsNotInCache.size() == CONCEPT_DESCRIPTIONS_PER_CALL || extractedLine == null) {

					populateAllLanguageDescriptions(refset, conceptsNotInCache);

					// Populate Members cache with data
					for (final Concept concept : conceptsNotInCache) {

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

		// Get descriptions for those not cached or not cached with all languages. Read
		// through file 2nd time and write each line to new file while appending
		// selected name
		try (final FileWriter fw = new FileWriter(new File(newFileWithNamesPath));
				final BufferedReader br = new BufferedReader(new FileReader(new File(origFilePath)))) {

			// get the header line so we can add the new description header
			String extractedLine = br.readLine();

			for (final Map<String, String> defaultLanguages : refset.getEdition().getFullyQualifiedLanguageRefsets()) {

				if (languageId.equals(defaultLanguages.get("qualifiedLanguageRefset"))) {

					fw.write(extractedLine + "\t" + defaultLanguages.get("qualifiedLanguageCode") + "\n");
				}

			}

			// get the first line of concepts
			extractedLine = br.readLine();

			while (extractedLine != null) {

				final String conceptId = extractedLine.split("\t")[REFEST_RF2_CONCEPTID_COLUMN];

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

						// If this is the English PT add it as a fallback to use if the language we want
						// isn't on this concept
						if (description != null && description.get(LANGUAGE_ID).equals(PREFERRED_TERM_EN)) {

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

					throw new Exception("Not seeing the expected descriptions for member: " + conceptId
							+ " as have these descriptions: " + members.get(conceptId).getDescriptions());
				}

				fw.write("\n");
				extractedLine = br.readLine();
			}

		}

	}

	/**
	 * Get the refset member basic information.
	 *
	 * @param refsetId    the refset ID
	 * @param limit       the number of results per page
	 * @param searchAfter the member to search after
	 * @param branchPath  the branch and version of the refset
	 * @return the raw resultString
	 * @throws Exception the exception
	 */
	private static String getMemberSctids(final String refsetId, final int limit, final String searchAfter,
			final String branchPath) throws Exception {

		return terminologyHandler.getMemberSctids(refsetId, limit, searchAfter, branchPath);

	}

	/**
	 * Export the refset member concept IDs in a zipped CSV format.
	 *
	 * @param service          the Terminology Service
	 * @param refsetInternalId the internal refset ID
	 * @param exportMetadata   should refset metadata be included in the export
	 * @return the URL of the file containing the member list
	 * @throws Exception the exception
	 */
	public static String exportRefsetSctidList(final TerminologyService service, final String refsetInternalId,
			final boolean exportMetadata) throws Exception {

		final int limit = ELASTICSEARCH_MAX_RECORD_LENGTH;
		boolean hasMorePages = true;
		final StringBuilder fileLines = new StringBuilder();
		String zipOutputPath = exportFileDir;
		String refsetFileName = "";
		String sctidsFilePath = "";
		final List<String> sourceFiles = new ArrayList<>();
		Path tempDirectoryPath = null;

		// get the refset and member information
		try {

			final Refset refset = RefsetService.getRefset(service, SecurityService.getUserFromSession(),
					refsetInternalId);

			if (refset == null) {

				throw new Exception(
						"Reference Set Internal Id: " + refsetInternalId + " does not exist in the RT2 database");
			}

			refsetFileName = "refset_" + refset.getRefsetId() + "_" + getRefsetAsOfDate(refset) + "_member_ids.txt";
			zipOutputPath += refsetFileName.replace(".txt", ".zip");
			tempDirectoryPath = Files.createTempDirectory("sctidList-" + refsetFileName.replace(".txt", ""));
			sctidsFilePath = tempDirectoryPath.toString() + File.separator + refsetFileName;

			LOG.debug("SCTID txt output path = " + sctidsFilePath);
			LOG.debug("zip output path = " + zipOutputPath);

			if (exportMetadata) {

				sourceFiles.add(exportRefsetMetadata(refset, tempDirectoryPath));
			}

			String searchAfter = "";

			while (hasMorePages) {

				final String resultString = getMemberSctids(refset.getRefsetId(), limit, searchAfter,
						getBranchPath(refset));
				// LOG.debug("exportRefsetSctidList: resultString" + resultString);
				final ObjectMapper mapper = new ObjectMapper();
				final JsonNode root = mapper.readTree(resultString);
				final JsonNode items = root.get("items");
				final Iterator<JsonNode> iterator = items.iterator();
				LOG.debug("exportRefsetSctidList items.size(): " + items.size());

				if (root.get("searchAfter") != null) {

					searchAfter = root.get("searchAfter").asText();
				} else {

					searchAfter = "";
				}

				LOG.debug("exportRefsetSctidList searchAfter: " + searchAfter);

				if (items.size() < limit) {

					hasMorePages = false;
				}

				LOG.debug("exportRefsetSctidList hasMorePages: " + hasMorePages);

				while (iterator.hasNext()) {

					final JsonNode item = iterator.next();
					final String conceptId = (item.get("referencedComponentId").asText());
					fileLines.append(conceptId + "\n");
				}

			}

		} catch (final Exception ex) {

			throw new Exception("Could not get Reference Set member data from snowstorm: " + ex.getMessage(), ex);
		}

		// print the sctids file
		try (final FileOutputStream sctidsFileOutputStream = new FileOutputStream(sctidsFilePath);

				final OutputStreamWriter sctidsOutputStreamWriter = new OutputStreamWriter(sctidsFileOutputStream,
						"UTF-8");
				final PrintWriter sctidsWriter = new PrintWriter(sctidsOutputStreamWriter);) {

			sctidsWriter.print(fileLines);

		} catch (final Exception ex) {

			throw new Exception("Could not create export txt file: " + ex.getMessage(), ex);
		}

		// zip the files together
		sourceFiles.add(sctidsFilePath);
		zipFiles(sourceFiles, zipOutputPath);

		// Delete temp directory structure and files
		FileUtility.deleteDirectory(tempDirectoryPath.toFile());

		// if download is from RT2 server
		// final ServletUriComponentsBuilder builder =
		// ServletUriComponentsBuilder.fromCurrentContextPath();
		final String zippedFileUrl = EXPORT_DOWNLOAD_URL + refsetFileName.replace(".txt", ".zip");

		return zippedFileUrl;
	}

	/**
	 * Export a freeset.
	 *
	 * @param service          the Terminology Service
	 * @param refsetInternalId the internal refset ID
	 * @param languageId       the language id
	 * @return the freeset in export format
	 * @throws Exception the exception
	 */
	public static String exportFreeset(final TerminologyService service, final String refsetInternalId,
			final String languageId) throws Exception {

		final StringBuilder fileLines = new StringBuilder();
		String sctidsOutputPath = "";
		String zipOutputPath = exportFileDir;
		String refsetFileName = "";
		final List<String> sourceFiles = new ArrayList<>();
		Path tempDirectoryPath = null;

		try {

			final Refset refset = RefsetService.getRefset(service, SecurityService.getUserFromSession(),
					refsetInternalId);

			if (refset == null) {

				throw new Exception(
						"Reference Set Internal Id: " + refsetInternalId + " does not exist in the RT2 database");
			}

			refsetFileName = "freeset_" + refset.getRefsetId() + "_" + getRefsetAsOfDate(refset) + ".txt";
			tempDirectoryPath = Files.createTempDirectory("freeset-" + refsetFileName.replace(".txt", ""));
			zipOutputPath += refsetFileName.replace(".txt", ".zip");
			sctidsOutputPath = tempDirectoryPath.toString() + File.separator + refsetFileName;

			LOG.debug("SCTID freeset txt output path = " + sctidsOutputPath);
			LOG.debug("zip freeset output path = " + zipOutputPath);

			LOG.debug("exportFreeset: refsetInternalId: " + refsetInternalId);

			final long start = System.currentTimeMillis();
			final ConceptResultList results = new ConceptResultList();

			final List<Concept> concepts = getAllRefsetMembers(service, refsetInternalId, "", new ArrayList<Concept>());
			Collections.sort(concepts,
					Comparator.comparing((final Concept concept) -> Long.parseLong(concept.getCode())));

			final List<Concept> conceptsToProcess = new ArrayList<>();
			int i = 0;

			for (final Concept concept : concepts) {

				conceptsToProcess.add(concept);
				i++;

				if (conceptsToProcess.size() == CONCEPT_DESCRIPTIONS_PER_CALL || i == concepts.size()) {

					populateAllLanguageDescriptions(refset, conceptsToProcess);
					conceptsToProcess.clear();
				}

			}

			results.setTimeTaken(System.currentTimeMillis() - start);
			results.setItems(concepts);

			fileLines.append("ConceptID").append("\t");
			fileLines.append("Active").append("\t");
			fileLines.append("FSN").append("\t");
			fileLines.append("PreferredTerm").append("\t");
			fileLines.append("\r\n");

			for (final Concept cpt : results.getItems()) {

				String fsn = "";
				String pt = "";

				for (final Map<String, String> entry : cpt.getDescriptions()) {

					if (entry != null && "fsn".equalsIgnoreCase(entry.get("type")) && StringUtils.isBlank(fsn)) {

						fsn = entry.get("term");
					}

					if (StringUtils.isNotBlank(languageId) && StringUtils.isBlank(pt)) {

						if (entry != null && entry.get("languageId").equals(languageId)) {

							pt = entry.get("term");
						}

					}

				}

				fileLines.append(cpt.getCode()).append("\t");
				fileLines.append(cpt.isActive() ? "1" : "0").append("\t");
				fileLines.append(StringUtils.isNotEmpty(fsn) ? fsn : "").append("\t");
				fileLines.append(StringUtils.isNotEmpty(pt) ? pt : cpt.getName());
				fileLines.append("\r\n");
			}

			// print the sctids file
			try (final FileOutputStream sctidsFileOutputStream = new FileOutputStream(sctidsOutputPath);
					final OutputStreamWriter sctidsOutputStreamWriter = new OutputStreamWriter(sctidsFileOutputStream,
							"UTF-8");
					final PrintWriter freesetWriter = new PrintWriter(sctidsOutputStreamWriter);) {

				freesetWriter.print(fileLines);
			}

		} catch (final Exception ex) {

			throw new Exception("Could not create free set txt file: " + ex.getMessage(), ex);
		}

		// zip the files together
		sourceFiles.add(sctidsOutputPath);
		zipFiles(sourceFiles, zipOutputPath);

		// Delete temp directory structure and files
		FileUtility.deleteDirectory(tempDirectoryPath.toFile());

		// if download is from RT2 server
		// final ServletUriComponentsBuilder builder =
		// ServletUriComponentsBuilder.fromCurrentContextPath();
		final String zippedFileUrl = EXPORT_DOWNLOAD_URL + refsetFileName.replace(".txt", ".zip");

		return zippedFileUrl;
	}

	/**
	 * Zip files together.
	 *
	 * @param sourceFiles       the list of files to zip together
	 * @param zipOutputFilePath the path and filename of the zip file to create
	 * @throws Exception the exception
	 */
	public static void zipFiles(final List<String> sourceFiles, final String zipOutputFilePath) throws Exception {

		try (final FileOutputStream zipFileOutputStream = new FileOutputStream(zipOutputFilePath);
				final ZipOutputStream zipOutputStream = new ZipOutputStream(zipFileOutputStream);) {

			for (final String sourceFile : sourceFiles) {

				final File fileToZip = new File(sourceFile);

				try (final FileInputStream zipFileInputStream = new FileInputStream(fileToZip)) {

					final ZipEntry zipEntry = new ZipEntry(fileToZip.getName());
					zipOutputStream.putNextEntry(zipEntry);

					final byte[] bytes = new byte[1024];
					int length;

					while ((length = zipFileInputStream.read(bytes)) >= 0) {

						zipOutputStream.write(bytes, 0, length);
					}

				}

			}

		} catch (final Exception ex) {

			throw new Exception("Could not zip the files: " + ex.getMessage(), ex);
		}

	}

	/**
	 * Extract files from a zip archive.
	 *
	 * @param zipFilePath    the path and filename of the zip file to unzip
	 * @param extractionPath the path of the directory to extract files to
	 * @return a list of file paths of the extracted files
	 * @throws Exception the exception
	 */
	public static List<String> unzipFiles(final String zipFilePath, final String extractionPath) throws Exception {

		final List<String> sourceFiles = new ArrayList<>();

		try (final ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath));) {

			final File extractionDirectory = new File(extractionPath);
			final byte[] buffer = new byte[1024];
			ZipEntry zipEntry;

			while ((zipEntry = zis.getNextEntry()) != null) {

				final File newFile = new File(extractionDirectory, zipEntry.getName());
				final String extractionCanonicalPath = extractionDirectory.getCanonicalPath();
				final String fileCanonicalPath = newFile.getCanonicalPath();

				if (!fileCanonicalPath.startsWith(extractionCanonicalPath + File.separator)) {

					throw new IOException("Entry is outside of the target directory: " + zipEntry.getName());
				}

				if (zipEntry.isDirectory()) {

					if (!newFile.isDirectory() && !newFile.mkdirs()) {

						throw new IOException("Failed to create directory " + newFile);
					}

				} else {

					// fix for Windows-created archives
					final File parent = newFile.getParentFile();

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

		} catch (final Exception ex) {

			throw new Exception("Could not unzip the file: " + ex.getMessage(), ex);
		}

	}

	/**
	 * Export the refset metadata in a text format.
	 *
	 * @param refset    the refset
	 * @param directory the directory
	 * @return the URL of the file containing the metadata
	 * @throws Exception the exception
	 */
	public static String exportRefsetMetadata(final Refset refset, final Path directory) throws Exception {

		final StringBuilder fileLines = new StringBuilder();
		final String pathDate = getRefsetAsOfDate(refset);
		final String outputPath = directory + "/refset_" + refset.getRefsetId() + "_" + pathDate + "_metadata.txt";
		final String separator = "\t";

		fileLines.append("Refset ID" + separator + refset.getRefsetId() + "\n");
		fileLines.append("Refset Name" + separator + refset.getName() + "\n");
		fileLines.append("Edition Name" + separator + refset.getEditionName() + "\n");
		fileLines.append("Edition Branch" + separator + refset.getEdition().getBranch() + "\n");
		fileLines.append("Organization" + separator + refset.getEdition().getOrganization().getName() + "\n");
		fileLines.append("Project" + separator + refset.getProject().getName() + "\n");
		fileLines.append("Module ID" + separator + refset.getModuleId() + "\n");
		fileLines.append("Refset Version Status" + separator + refset.getVersionStatus() + "\n");

		if (refset.getVersionDate() != null) {

			fileLines.append("Reference Set Version Date" + separator
					+ DateUtility.formatDate(refset.getVersionDate(), DateUtility.DATE_FORMAT_REVERSE, null) + "\n");
		} else {

			fileLines.append("Reference Set Version Date" + separator + "\n");
		}

		if (refset.isLocalSet()) {

			fileLines.append("Local Reference Set" + separator + "True" + "\n");
		}

		fileLines.append("Reference Set Last Modified Date" + separator
				+ DateUtility.formatDate(refset.getModified(), DateUtility.DATE_FORMAT_REVERSE, null) + "\n");
		fileLines.append("Reference Set Type" + separator + refset.getType() + "\n");

		if (refset.isActive()) {

			fileLines.append("Reference Set Status" + separator + "Active" + "\n");
		} else {

			fileLines.append("Reference Set Status" + separator + "Inactive" + "\n");
		}

		if (refset.isPrivateRefset()) {

			fileLines.append("Private Reference Set" + separator + "True" + "\n");
		} else {

			fileLines.append("Private Reference Set" + separator + "False" + "\n");
		}

		fileLines.append("Tags" + separator + String.join(", ", refset.getTags()) + "\n");

		if (refset.getNarrative() != null && !refset.getNarrative().equals("")) {

			fileLines.append("Reference Set Narrative" + separator + refset.getNarrative() + "\n");
		}

		if (refset.getVersionNotes() != null && !refset.getVersionNotes().equals("")) {

			fileLines.append("Reference Set Version Notes" + separator + refset.getVersionNotes() + "\n");
		}

		if (refset.getExternalUrl() != null && !refset.getExternalUrl().equals("")) {

			fileLines.append("External URL" + separator + refset.getExternalUrl() + "\n");
		}

		if (refset.getType().equals(Refset.INTENSIONAL)) {

			final String definition = RefsetService.getEclFromDefinition(refset.getDefinitionClauses());
			fileLines.append("Reference Set Definition" + separator + definition + "\n");
		}

		// print the sctids file
		try (final FileOutputStream fileOutputStream = new FileOutputStream(outputPath);
				final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream, "UTF-8");
				final PrintWriter printWriter = new PrintWriter(outputStreamWriter);) {

			printWriter.print(fileLines);
			return outputPath;

		} catch (final Exception ex) {

			throw new Exception("Could not create metadata export txt file: " + ex.getMessage(), ex);
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
		final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");

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
	 * @param refset            the refset who's members are being retrieved
	 * @param conceptsToProcess the concepts to add descriptions to
	 * @throws Exception the exception
	 */
	public static void populateAllLanguageDescriptions(final Refset refset, final List<Concept> conceptsToProcess)
			throws Exception {

		terminologyHandler.populateAllLanguageDescriptions(refset, conceptsToProcess);

	}

	/**
	 * Populates concepts with information on if they have children.
	 *
	 * @param refset            the refset who's members are being retrieved
	 * @param conceptsToProcess the concepts to add hasChild info to
	 * @throws Exception the exception
	 */
	public static void populateConceptLeafStatus(final Refset refset, final List<Concept> conceptsToProcess)
			throws Exception {

		terminologyHandler.populateConceptLeafStatus(refset, conceptsToProcess);

	}

	/**
	 * Get ready to search concepts.
	 *
	 * @param service             the Terminology Service
	 * @param user                the user
	 * @param refsetInternalId    the internal refset ID
	 * @param searchParameters    the search parameters
	 * @param searchRefsetMembers Should the search be for members of the refset or
	 *                            for all concepts
	 * @return the concept result list
	 * @throws Exception the exception
	 */
	public static ConceptResultList prepareConceptSearch(final TerminologyService service, final User user,
			final String refsetInternalId, final SearchParameters searchParameters, final boolean searchRefsetMembers)
			throws Exception {

		ConceptResultList concepts = new ConceptResultList();

		final Refset refset = getRefset(user, service, refsetInternalId);
		final String branchPath = getBranchPath(refset);
		final String cacheString = refset.getRefsetId() + searchParameters.toString() + searchRefsetMembers;
		final Map<String, ConceptResultList> branchCache = getCacheForConceptsCall(branchPath);
		String searchMembersMode = "all";

		// check if the concept call has been cached
		if (branchCache.containsKey(cacheString)) {

			LOG.debug("prepareConceptSearch USING CACHE");
			return branchCache.get(cacheString);
		}

		if (searchRefsetMembers) {

			searchMembersMode = "members";
		}

		concepts = searchConcepts(refset, searchParameters, searchMembersMode, -1);

		if (searchRefsetMembers) {

			final List<Concept> allConceptList = concepts.getItems();
			final List<Concept> conceptsToProcess = new ArrayList<>();
			int i = 0;

			for (final Concept concept : allConceptList) {

				conceptsToProcess.add(concept);
				i++;

				if (conceptsToProcess.size() == CONCEPT_DESCRIPTIONS_PER_CALL || i == allConceptList.size()) {

					populateAllLanguageDescriptions(refset, conceptsToProcess);
					conceptsToProcess.clear();
				}

			}

		}

		branchCache.put(cacheString, concepts);
		CONCEPTS_CALL_CACHE.put(branchPath, branchCache);
		// LOG.debug("prepareConceptSearch: results: " + ModelUtility.toJson(concepts));

		return concepts;
	}

	/**
	 * Get the ancestor path for a list of conceptIDs.
	 *
	 * @param refset    the refset
	 * @param conceptId the concept ID paths are being generated for
	 * @return the concept result list
	 * @throws Exception the exception
	 */
	public static Concept getConceptAncestors(final Refset refset, final String conceptId) throws Exception {

		return terminologyHandler.getConceptAncestors(refset, conceptId);
	}

	/**
	 * Search concepts.
	 *
	 * @param refset            the refset
	 * @param searchParameters  the search parameters
	 * @param searchMembersMode Should the search be for only for members, non
	 *                          members, or all concepts. Values: 'all', 'members',
	 *                          'non members'
	 * @param limitReturnNumber -1 if all results should be returned, or the number
	 *                          of final results that should be returned (search may
	 *                          request more than what is returned)
	 * @return the concept result list
	 * @throws Exception the exception
	 */
	public static ConceptResultList searchConcepts(final Refset refset, final SearchParameters searchParameters,
			final String searchMembersMode, final int limitReturnNumber) throws Exception {

		return terminologyHandler.searchConcepts(refset, searchParameters, searchMembersMode, limitReturnNumber);

	}

	/**
	 * Process description node.
	 *
	 * @param descriptionNodes         the description nodes
	 * @param defaultLanguageRefsets   the default language refsets
	 * @param nonDefaultPreferredTerms the non default preferred terms
	 * @return the sets the
	 */
	public static Set<Map<String, String>> processDescriptionNodes(final Set<JsonNode> descriptionNodes,
			final Set<String> defaultLanguageRefsets, final List<String> nonDefaultPreferredTerms) {

		final Set<Map<String, String>> descriptions = new HashSet<>();

		for (final JsonNode descriptionNode : descriptionNodes) {

			final Map<String, String> descriptionAttributesMap = new HashMap<>();
			final JsonNode acceptabilityMap = descriptionNode.get("acceptabilityMap");
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

				descriptionAttributesMap.put(DESCRIPTION_TERM, descriptionNode.get("term").asText());
				descriptionAttributesMap.put(DESCRIPTION_TYPE, typeName);
				descriptionAttributesMap.put(DESCRIPTION_ID, descriptionNode.get("descriptionId").asText());
				descriptionAttributesMap.put(LANGUAGE_CODE, languageId);
				descriptionAttributesMap.put(LANGUAGE_ID, languageId + typeName);
				descriptionAttributesMap.put(LANGUAGE_NAME,
						descriptionNode.get("lang").asText().toUpperCase() + " (" + typeName + ")");
				descriptionAttributesMap.put(DESCRIPTION_LANGUAGE, descriptionNode.get("lang").asText());

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

		if (TREE_CACHE.containsKey(branchPath)) {

			return TREE_CACHE.get(branchPath);
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

		if (ANCESTORS_CACHE.containsKey(branchPath)) {

			return ANCESTORS_CACHE.get(branchPath);
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
	public static Map<String, Concept> getCacheForTaxonomySearchAncestors(final String branchPath) throws Exception {

		if (TAXONOMY_SEARCH_ANCESTORS_CACHE.containsKey(branchPath)) {

			return TAXONOMY_SEARCH_ANCESTORS_CACHE.get(branchPath);
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

		if (CONCEPT_DETAILS_CACHE.containsKey(branchPath)) {

			return CONCEPT_DETAILS_CACHE.get(branchPath);
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

		if (CONCEPTS_CALL_CACHE.containsKey(branchPath)) {

			return CONCEPTS_CALL_CACHE.get(branchPath);
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

			LOG.debug("clearAllMemberCaches: Clearing caches for branch path: " + branchPath);

			CONCEPTS_CALL_CACHE.remove(branchPath);
			CONCEPT_DETAILS_CACHE.remove(branchPath);
			TAXONOMY_SEARCH_ANCESTORS_CACHE.remove(branchPath);
			TREE_CACHE.remove(branchPath);
			ANCESTORS_CACHE.remove(branchPath);

			// we also need to clear the refset export cache on S3
			final ExportHandler exportHandler = new ExportHandler();
			exportHandler.deleteFilesFromBranchPath(branchPath);

		} else {

			LOG.debug("clearAllMemberCaches: Clearing caches for all branches");

			CONCEPTS_CALL_CACHE.clear();
			CONCEPT_DETAILS_CACHE.clear();
			TAXONOMY_SEARCH_ANCESTORS_CACHE.clear();
			TREE_CACHE.clear();
			ANCESTORS_CACHE.clear();
		}

	}

	/**
	 * Copy all branch cache collections to another branch.
	 *
	 * @param fromBranchPath         the branch to copy the cache collections from
	 * @param toBranchPath           the branch to copy the cache collections to
	 * @param changeEditPropertyFrom should cache keys containing the edit property
	 *                               be change. Null for no change, or "true" or
	 *                               "false" for the current state of the property
	 * @throws Exception the exception
	 */
	public static void copyAllMemberCachesToBranch(final String fromBranchPath, final String toBranchPath,
			final String changeEditPropertyFrom) throws Exception {

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

		LOG.debug("BRANCH CACHE copying from: " + fromBranchPath);
		LOG.debug("BRANCH CACHE copying to: " + toBranchPath);

		if (CONCEPTS_CALL_CACHE.containsKey(fromBranchPath)) {

			Map<String, ConceptResultList> tempCache = CONCEPTS_CALL_CACHE.get(fromBranchPath);

			if (changeEditProperty) {

				String cacheString = ModelUtility.toJson(tempCache);
				cacheString = cacheString.replace("\\\"editing\\\":" + changeEditPropertyFrom,
						"\\\"editing\\\":" + changeEditPropertyTo);
				tempCache = ModelUtility.fromJson(cacheString, new TypeReference<Map<String, ConceptResultList>>() {
					/**/
				});
			}

			CONCEPTS_CALL_CACHE.put(toBranchPath, tempCache);

		} else {

			CONCEPTS_CALL_CACHE.remove(toBranchPath);
		}

		if (CONCEPT_DETAILS_CACHE.containsKey(fromBranchPath)) {

			CONCEPT_DETAILS_CACHE.put(toBranchPath, CONCEPT_DETAILS_CACHE.get(fromBranchPath));
		} else {

			CONCEPT_DETAILS_CACHE.remove(toBranchPath);
		}

		if (TAXONOMY_SEARCH_ANCESTORS_CACHE.containsKey(fromBranchPath)) {

			TAXONOMY_SEARCH_ANCESTORS_CACHE.put(toBranchPath, TAXONOMY_SEARCH_ANCESTORS_CACHE.get(fromBranchPath));
		} else {

			TAXONOMY_SEARCH_ANCESTORS_CACHE.remove(toBranchPath);
		}

		if (TREE_CACHE.containsKey(fromBranchPath)) {

			TREE_CACHE.put(toBranchPath, TREE_CACHE.get(fromBranchPath));
		} else {

			TREE_CACHE.remove(toBranchPath);
		}

		if (ANCESTORS_CACHE.containsKey(fromBranchPath)) {

			ANCESTORS_CACHE.put(toBranchPath, ANCESTORS_CACHE.get(fromBranchPath));
		} else {

			ANCESTORS_CACHE.remove(toBranchPath);
		}

	}

	/**
	 * Get the refset member concepts as a list.
	 *
	 * @param refset the refset
	 * @return the count of refset members
	 * @throws Exception the exception
	 */
	public static int getMemberCount(final Refset refset) throws Exception {

		return terminologyHandler.getMemberCount(refset);
	}

	/**
	 * Get the refset member concepts as a list.
	 *
	 * @param refset                   the refset who's members are being retrieved
	 * @param nonDefaultPreferredTerms the non-default preferred terms
	 * @param searchParameters         the search parameters
	 * @return the refset member concepts
	 * @throws Exception the exception
	 */
	public static ConceptResultList getMemberList(final Refset refset, final List<String> nonDefaultPreferredTerms,
			final SearchParameters searchParameters) throws Exception {

		return terminologyHandler.getMemberList(refset, nonDefaultPreferredTerms, searchParameters);
	}

	/**
	 * Get the refset member concepts as a list.
	 *
	 * @param refset                   the refset who's members are being retrieved
	 * @param nonDefaultPreferredTerms the non-default preferred terms
	 * @param taxonomyParameters       the taxonomy parameters
	 * @return the refset member concepts
	 * @throws Exception the exception
	 */
	public static ConceptResultList getMemberTaxonomy(final Refset refset, final List<String> nonDefaultPreferredTerms,
			final TaxonomyParameters taxonomyParameters) throws Exception {

		final String startingConceptId = taxonomyParameters.getStartingConceptId();
		final String language = taxonomyParameters.getLanguage();
		List<Concept> relationsList = new ArrayList<>();
		final List<Concept> processedTreeNodes = new ArrayList<>();
		ConceptResultList conceptResultList = new ConceptResultList();
		final String branchPath = getBranchPath(refset);
		final String cacheString = refset.getRefsetId() + taxonomyParameters.toString();
		final Map<String, ConceptResultList> branchCache = getCacheForTree(branchPath);

		// check if the members call has been cached
		if (branchCache.containsKey(cacheString)) {

			LOG.debug("getMemberTaxonomy USING CACHE");
			return branchCache.get(cacheString);
		}

		// If parent concept is inactive, getChildren() will return a 400 error.
		try {

			if (taxonomyParameters.getReturnChildren()) {

				conceptResultList = getChildren(startingConceptId, refset, language);
			} else {

				conceptResultList = getParents(startingConceptId, refset, language);
			}

		} catch (final Exception e) {

			// Only throw exception if the rest status code is something other than 400
			if (!"400".equals(e.getMessage())) {

				throw e;
			}

		}

		relationsList = conceptResultList.getItems();
		final List<Concept> conceptsToProcessMembership = new ArrayList<>();

		for (final Concept concept : relationsList) {

			conceptsToProcessMembership.add(concept);
		}

		if (!conceptsToProcessMembership.isEmpty()) {

			populateMembershipInformation(refset, conceptsToProcessMembership);
		}

		for (final Concept concept : relationsList) {

			processedTreeNodes.add(concept);
		}

		if (taxonomyParameters.getReturnChildren()) {

			LOG.debug("Concept has " + processedTreeNodes.size() + " children");
		} else {

			LOG.debug("Concept has " + processedTreeNodes.size() + " parents");
		}

		// if returning the starting concept get the details and set the concept
		// properties appropriately for taxonomy
		if (taxonomyParameters.getReturnStartingConcept()) {

			Concept startingConcept = getConceptDetails(startingConceptId, refset);

			// make sure this is a fresh object since concept details can be cached and it
			// would otherwise modify objects in that cache and this cache.
			startingConcept = ModelUtility.fromJson(ModelUtility.toJson(startingConcept), Concept.class);

			// set the name and FSN properties appropriately
			for (final Map<String, String> description : startingConcept.getDescriptions()) {

				if (description == null) {

					continue;
				}

				// check if this is the english FSN, if so set the FSN property
				if (description.get(DESCRIPTION_LANGUAGE).equalsIgnoreCase("en")
						&& description.get(DESCRIPTION_TYPE).equalsIgnoreCase("fsn")) {

					startingConcept.setFsn(description.get(DESCRIPTION_TERM));
				}

				// check if this is the requested language, if so set the name property
				if (language.equalsIgnoreCase(
						description.get(DESCRIPTION_LANGUAGE) + "-X-" + description.get(LANGUAGE_CODE))
						&& description.get(DESCRIPTION_TYPE).equalsIgnoreCase("pt")) {

					startingConcept.setName(description.get(DESCRIPTION_TERM));
				}

			}

			startingConcept.setChildren(processedTreeNodes);
			conceptResultList.setItems(Arrays.asList(startingConcept));

		} else {

			conceptResultList.setItems(processedTreeNodes);
		}

		branchCache.put(cacheString, conceptResultList);
		TREE_CACHE.put(branchPath, branchCache);

		return conceptResultList;
	}

	/**
	 * Returns the concept details.
	 *
	 * @param conceptId the concept id
	 * @param refset    the refset
	 * @return the concept details
	 * @throws Exception the exception
	 */
	public static Concept getConceptDetails(final String conceptId, final Refset refset) throws Exception {

		return terminologyHandler.getConceptDetails(conceptId, refset);

	}

	/**
	 * Returns the parents.
	 *
	 * @param conceptId the concept id
	 * @param refset    the refset
	 * @param language  the language
	 * @return the parents
	 * @throws Exception the exception
	 */
	protected static ConceptResultList getParents(final String conceptId, final Refset refset, final String language)
			throws Exception {

		return terminologyHandler.getParents(conceptId, refset, language);

	}

	/**
	 * Returns the children.
	 *
	 * @param conceptId the concept id
	 * @param refset    the refset
	 * @param language  the language
	 * @return the children
	 * @throws Exception the exception
	 */
	protected static ConceptResultList getChildren(final String conceptId, final Refset refset, final String language)
			throws Exception {

		return terminologyHandler.getChildren(conceptId, refset, language);

	}

	/**
	 * Call the provided Snowstorm URL to get concepts and return a processed result
	 * list.
	 * 
	 * @param url              The API URL to call
	 * @param refset           the refset
	 * @param lookupParameters the parts of the concept to retrieve
	 * @param language         the language to the return the concept descriptions
	 *                         in
	 * @return the concepts
	 * @throws Exception the exception
	 */
	protected static ConceptResultList getConceptsFromSnowstorm(final String url, final Refset refset,
			final ConceptLookupParameters lookupParameters, final String language) throws Exception {

		return terminologyHandler.getConceptsFromSnowstorm(url, refset, lookupParameters, language);

	}

	/**
	 * Populate concepts from Snowstorm.
	 *
	 * @param root             the iterator
	 * @param refset           the refset
	 * @param lookupParameters the lookup parameters
	 * @return the concept result list
	 * @throws Exception the exception
	 */
	public static ConceptResultList populateConcepts(final JsonNode root, final Refset refset,
			final ConceptLookupParameters lookupParameters) throws Exception {

		final ConceptResultList conceptList = new ConceptResultList();
		final String branchPath = getBranchPath(refset);
		final String cacheString = refset.getRefsetId();
		final Map<String, Set<String>> branchCache = getCacheForMemberAncestors(branchPath);
		final SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DATE_FORMAT);

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
			final ConceptLookupParameters missingLookupParameters = identifyContentPopulated(concept, refset,
					lookupParameters);

			String name = "";
			boolean memberStatus = false;
			boolean defined = false;

			// if this has a referenced component it is an active refset member, otherwise
			// it at this point it is not known if it is a member
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

				if (conceptNode.has("memberId")) {

					concept.setMemberId(conceptNode.get("memberId").asText());
				}

				// if the member has been released get the effective time
				if (conceptNode.has("releasedEffectiveTime")) {

					concept.setMemberEffectiveTime(
							simpleDateFormat.parse(conceptNode.get("releasedEffectiveTime").asText()));
				}

			} else if (conceptNode.has("conceptId")) {

				// Concept is General (and is a child of the node opened) Read
				// general-representation of basic concept content
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

				if (branchCache.containsKey(cacheString)
						&& branchCache.get(cacheString).contains(conceptNode.get("conceptId").asText())) {

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

					concept.setDescriptions(populateDescriptions(concept.getCode(), conceptNode.get("descriptions"),
							refset, missingLookupParameters.getNonDefaultPreferredTerms()));

				} else if (conceptNode.get("fsn") != null) {

					final String fsn = conceptNode.get("fsn").get("term").asText();
					final Map<String, String> descMap = new HashMap<>();
					descMap.put("fsn", fsn);
					final List<Map<String, String>> list = new ArrayList<>();
					list.add(descMap);
					concept.setDescriptions(list);
				} else {

					populateAllLanguageDescriptions(refset, new ArrayList<>(Arrays.asList(concept)));
				}

			}

			// Snowstorm throws a 400-Exception when children/parents of an inactive
			// concepts are requested
			if (missingLookupParameters.isGetParents() && concept.isActive()) {

				concept.setParents(getParents(conceptId, refset, null).getItems());
			}

			// Snowstorm throws a 400-Exception when children/parents of an inactive
			// concepts are requested
			if (missingLookupParameters.isGetChildren() && concept.isActive()) {

				concept.setChildren(getChildren(conceptId, refset, null).getItems());
			}

			if (missingLookupParameters.isGetRoleGroups()) {

				concept.setRoleGroups(populateRoleGroups(concept.getCode(), conceptNode.get("relationships")));
			}

			if (missingLookupParameters.isGetMembershipInformation()) {

				concept.setMemberOfRefset(true);

				if (conceptNode.get("releasedEffectiveTime") != null) {

					concept.setMemberEffectiveTime(
							simpleDateFormat.parse(conceptNode.get("releasedEffectiveTime").asText()));
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

	/**
	 * Identify content populated.
	 *
	 * @param concept          the concept
	 * @param refset           the refset
	 * @param lookupParameters the lookup parameters
	 * @return the concept lookup parameters
	 */
	private static ConceptLookupParameters identifyContentPopulated(final Concept concept, final Refset refset,
			final ConceptLookupParameters lookupParameters) {

		// If concept not found in cache, concept is null. Just return original lookup
		// parameters
		if (concept == null) {

			return lookupParameters;
		}

		// Concept found in cache, so review contents to see what requires further
		// lookup
		final ConceptLookupParameters missingConceptLookupParameters = new ConceptLookupParameters();
		boolean missingContentFound = false;

		if (lookupParameters.isGetDescriptions() && concept.getDescriptions().isEmpty()) {

			missingConceptLookupParameters.setGetDescriptions(true);
			missingContentFound = true;
		}

		if (lookupParameters.isGetMembershipInformation() && concept.getMemberEffectiveTime() == null) {

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

	/**
	 * Populate role groups.
	 *
	 * @param conceptId         the concept id
	 * @param relationshipsNode the relationships node
	 * @return the map
	 */
	private static Map<Integer, List<String>> populateRoleGroups(final String conceptId,
			final JsonNode relationshipsNode) {

		final Map<Integer, List<String>> roleGroups = new HashMap<>();
		final Iterator<JsonNode> iterator = relationshipsNode.iterator();

		while (iterator.hasNext()) {

			final JsonNode relationship = iterator.next();

			if (relationship.get("active").asBoolean()
					&& "INFERRED_RELATIONSHIP".equals(relationship.get("characteristicType").asText())) {

				final int groupId = relationship.get("groupId").asInt();

				if (!roleGroups.containsKey(groupId)) {

					roleGroups.put(groupId, new ArrayList<String>());
				}

				final String type = relationship.get("type").get("pt").get("term").asText();

				if (!"Is a".equals(type)) {

					final String target = relationship.get("target").get("pt").get("term").asText();
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
	 * @param conceptId                the concept id
	 * @param descriptions             the descriptions
	 * @param refset                   the refset
	 * @param nonDefaultPreferredTerms the non default preferred terms
	 * @return the list
	 * @throws Exception the exception
	 */
	public static List<Map<String, String>> populateDescriptions(final String conceptId, final JsonNode descriptions,
			final Refset refset, final List<String> nonDefaultPreferredTerms) throws Exception {

		final Iterator<JsonNode> iterator = descriptions.iterator();

		final Set<JsonNode> descriptionNodes = new HashSet<>();

		while (iterator.hasNext()) {

			final JsonNode description = iterator.next();
			descriptionNodes.add(description);
		}

		final Set<Map<String, String>> populatedDescriptions = processDescriptionNodes(descriptionNodes,
				refset.getEdition().getDefaultLanguageRefsets(), nonDefaultPreferredTerms);

		return sortConceptDescriptions(conceptId, populatedDescriptions, refset, nonDefaultPreferredTerms);
	}

	/**
	 * Populate membership information.
	 *
	 * @param refset   the refset
	 * @param concepts the concepts
	 * @throws Exception the exception
	 */
	private static void populateMembershipInformation(final Refset refset, final List<Concept> concepts)
			throws Exception {

		terminologyHandler.populateMembershipInformation(refset, concepts);
	}

	/**
	 * Process intensional definition exception.
	 *
	 * @param refset  the refset
	 * @param concept the concept
	 * @return the string
	 * @throws Exception the exception
	 */
	public static String processIntensionalDefinitionException(final Refset refset, final Concept concept)
			throws Exception {

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

	/**
	 * Returns the latest changed version date.
	 *
	 * @param branch   the branch
	 * @param refsetId the refset id
	 * @return the latest changed version date
	 * @throws Exception the exception
	 */
	public static Long getLatestChangedVersionDate(final String branch, final String refsetId) throws Exception {

		return terminologyHandler.getLatestChangedVersionDate(branch, refsetId);

	}

	/**
	 * get the refset member history for a single refset.
	 *
	 * @param service               the Terminology Service
	 * @param referencedComponentId the member concept ID
	 * @param versions              a list of the refset versions to get history
	 *                              from
	 * @return the member history
	 * @throws Exception the exception
	 */
	public static List<Map<String, String>> getMemberHistory(final TerminologyService service,
			final String referencedComponentId, final List<Map<String, String>> versions) throws Exception {

		return terminologyHandler.getMemberHistory(service, referencedComponentId, versions);
	}

	/**
	 * Populate the user permissions properties on a concept.
	 *
	 * @param concept The concept to set properties on
	 */
	public static void setConceptPermissions(final Concept concept) {

		concept.setHistoryVisible(true);
		concept.setFeedbackVisible(true);
	}

	/**
	 * Populate the user permissions properties on a concept.
	 * 
	 * @param service the Terminology Service
	 */
	public static void cacheAllMemberAncestors(final TerminologyService service) {

		try {

			final PfsParameter pfs = new PfsParameter();
			pfs.setAscending(false);
			pfs.setSort("latestVersion");

			final ResultList<Refset> refsets = service.find("", null, Refset.class, null);
			LOG.info("Starting to cache member ancestors for all " + refsets.getItems().size() + " Reference Sets");

			for (final Refset refset : refsets.getItems()) {

				refset.setBranchPath(RefsetService.getBranchPath(refset));
				cacheMemberAncestors(refset);
			}

		} catch (final Exception e) {

			LOG.error("Could not cache all member ancestors", e);
		}

	}

	/**
	 * Cache member ancestors.
	 *
	 * @param service     the service
	 * @param user        the user
	 * @param refsetId    the refset id
	 * @param versionDate the version date
	 * @return true, if successful
	 * @throws Exception the exception
	 */
	public static boolean cacheMemberAncestors(final TerminologyService service, final User user, final String refsetId,
			final String versionDate) throws Exception {

		return cacheMemberAncestors(RefsetService.getRefset(service, user, refsetId, versionDate));
	}

	/**
	 * Cache member ancestors.
	 *
	 * @param refset the refset
	 * @return true, if successful
	 * @throws Exception the exception
	 */
	public static boolean cacheMemberAncestors(final Refset refset) throws Exception {

		return terminologyHandler.cacheMemberAncestors(refset);
	}

	/**
	 * Convert a list of concepts into an ECL statement.
	 *
	 * @param conceptIds a list of concept IDs
	 * @return An ECL statement composed of the list of concept IDs
	 * @throws Exception the exception
	 */
	public static String conceptListToEclStatement(final List<String> conceptIds) throws Exception {

		String ecl = "";

		for (final String conceptId : conceptIds) {

			ecl += conceptId + " OR ";
		}

		return StringUtils.removeEnd(ecl, " OR ");
	}

	/**
	 * Add a list of concepts as members to a refset.
	 *
	 * @param service    the Terminology Service
	 * @param user       the user
	 * @param refset     the refset
	 * @param conceptIds a list of concept IDs to make members
	 * @return A list of concepts that were unable to be added
	 * @throws Exception the exception
	 */
	public static List<String> addRefsetMembers(final TerminologyService service, final User user, final Refset refset,
			final List<String> conceptIds) throws Exception {

		return terminologyHandler.addRefsetMembers(service, user, refset, conceptIds);
	}

	/**
	 * Remove or inactivate refset membership for a list of concepts.
	 *
	 * @param service    the Terminology Service
	 * @param user       the user
	 * @param refset     the refset
	 * @param conceptIds a list of concept IDs to make members
	 * @return A list of concepts that were unable to have membership removed
	 * @throws Exception the exception
	 */
	public static List<String> removeRefsetMembers(final TerminologyService service, final User user,
			final Refset refset, final String conceptIds) throws Exception {

		return terminologyHandler.removeRefsetMembers(service, user, refset, conceptIds);

	}

	/**
	 * Get a list of concepts ID from an ECL query.
	 *
	 * @param branch the branch
	 * @param ecl    the ecl
	 * @return A list of concepts that were unable to have membership removed
	 * @throws Exception the exception
	 */
	public static List<String> getConceptIdsFromEcl(final String branch, final String ecl) throws Exception {

		return terminologyHandler.getConceptIdsFromEcl(branch, ecl);

	}

	/**
	 * Compile and store the data to upgrade a refset.
	 *
	 * @param service          the Terminology Service
	 * @param user             the user
	 * @param refsetInternalId the internal refset ID
	 * @return The operation status
	 * @throws Exception the exception
	 */
	public static String compileUpgradeData(final TerminologyService service, final User user,
			final String refsetInternalId) throws Exception {

		return terminologyHandler.compileUpgradeData(service, user, refsetInternalId);

	}

	/**
	 * get the stored the data to upgrade a refset.
	 *
	 * @param service the Terminology Service
	 * @param user    the user
	 * @param refset  the refset
	 * @return The upgrade data
	 * @throws Exception the exception
	 */
	public static ResultList<UpgradeInactiveConcept> getUpgradeData(final TerminologyService service, final User user,
			final Refset refset) throws Exception {

		final PfsParameter pfs = new PfsParameter();
		pfs.setSort("code");

		final ResultList<UpgradeInactiveConcept> results = service.find("refsetId: " + refset.getRefsetId(), pfs,
				UpgradeInactiveConcept.class, null);
		results.setTotal(results.getItems().size());
		results.setTotalKnown(true);
		results.setMiscCountA(refset.getMemberCount() - results.getItems().size());

		return results;
	}

	/**
	 * get a single inactive upgrade concept.
	 *
	 * @param service           the Terminology Service
	 * @param user              the user
	 * @param refset            the refset
	 * @param inactiveConceptId the code of the inactive upgrade concept to get
	 * @return The upgrade data
	 * @throws Exception the exception
	 */
	public static UpgradeInactiveConcept getUpgradeConcept(final TerminologyService service, final User user,
			final Refset refset, final String inactiveConceptId) throws Exception {

		final UpgradeInactiveConcept upgradeInactiveConcept = service.findSingle(
				"refsetId: " + refset.getRefsetId() + " AND code:" + inactiveConceptId, UpgradeInactiveConcept.class,
				null);
		return upgradeInactiveConcept;
	}

	/**
	 * remove the upgrade data for a refset.
	 *
	 * @param service          the Terminology Service
	 * @param user             the user
	 * @param refsetInternalId the internal refset ID
	 * @throws Exception the exception
	 */
	public static void removeUpgradeData(final TerminologyService service, final User user,
			final String refsetInternalId) throws Exception {

		final Refset refset = RefsetService.getRefset(service, user, refsetInternalId);
		final String refsetId = refset.getRefsetId();

		final ResultList<UpgradeInactiveConcept> results = service.find("refsetId: " + refsetId, null,
				UpgradeInactiveConcept.class, null);

		for (final UpgradeInactiveConcept upgradeInactiveConcept : results.getItems()) {

			service.removeObject(upgradeInactiveConcept);
		}

	}

	/**
	 * Make a change to an upgrade concept.
	 *
	 * @param service                  the Terminology Service
	 * @param user                     the user
	 * @param refset                   the refset
	 * @param inactiveConceptId        the concept ID of the inactive concept to be
	 *                                 upgraded
	 * @param replacementConceptId     the concept ID of the replacement concept to
	 *                                 be updated
	 * @param manualReplacementConcept the manual upgrade replacement concept that
	 *                                 to be added
	 * @param changed                  a string identifying what has been changed
	 * @return the status of the operation
	 * @throws Exception the exception
	 */
	public static String modifyUpgradeConcept(final TerminologyService service, final User user, final Refset refset,
			final String inactiveConceptId, final String replacementConceptId,
			final UpgradeReplacementConcept manualReplacementConcept, final String changed) throws Exception {

		return terminologyHandler.modifyUpgradeConcept(service, user, refset, inactiveConceptId, replacementConceptId,
				manualReplacementConcept, changed);

	}

	/**
	 * Remove all inactive upgrade concepts.
	 *
	 * @param service the Terminology Service
	 * @param user    the user
	 * @param refset  the refset
	 * @return the status of the operation
	 * @throws Exception the exception
	 */
	public static String removeAllUpgradeInactiveConcepts(final TerminologyService service, final User user,
			final Refset refset) throws Exception {

		try {

			List<String> unchangedConcepts;
			String conceptIdsToChange = "";
			WorkflowService.canUserEditRefset(user, refset);
			RefsetMemberService.REFSETS_UPDATED_MEMBERS.put(refset.getId(), new HashMap<>());

			final ResultList<UpgradeInactiveConcept> inactiveConceptList = service.find(
					"refsetId: " + refset.getRefsetId() + " AND stillMember: true", null, UpgradeInactiveConcept.class,
					null);

			for (final UpgradeInactiveConcept inactiveConcept : inactiveConceptList.getItems()) {

				conceptIdsToChange += inactiveConcept.getCode() + ",";
			}

			conceptIdsToChange = StringUtils.removeEnd(conceptIdsToChange, ",");

			// remove the concepts as members from the refset
			unchangedConcepts = RefsetMemberService.removeRefsetMembers(service, user, refset, conceptIdsToChange);

			for (final UpgradeInactiveConcept inactiveConcept : inactiveConceptList.getItems()) {

				// don't process concepts that couldn't be removed
				if (unchangedConcepts.contains(inactiveConcept.getCode())) {

					continue;
				}

				inactiveConcept.setStillMember(false);

				// save the inactive concept
				service.update(inactiveConcept);
				LOG.debug("removeAllUpgradeInactiveConcepts: member removed: " + inactiveConcept.getCode());
			}

			// see if there the concept was unable to be changed and craft the error message
			if (unchangedConcepts.size() > 0) {

				return "The concepts " + unchangedConcepts + " were unable to be removed.";
			}

			return "All changes made successfully";

		} catch (final Exception e) {

			throw new Exception(e);
		}

		finally {

			RefsetMemberService.REFSETS_BEING_UPDATED.remove(refset.getId());
		}

	}

	/**
	 * Remove all inactive upgrade concepts.
	 *
	 * @param service the Terminology Service
	 * @param user    the user
	 * @param refset  the refset
	 * @return the status of the operation
	 * @throws Exception the exception
	 */
	public static String addAllUpgradeReplacementConcepts(final TerminologyService service, final User user,
			final Refset refset) throws Exception {

		try {

			RefsetMemberService.REFSETS_BEING_UPDATED.add(refset.getId());

			String message = "";
			List<String> unaddedConcepts;
			List<String> unremovedConcepts;
			final List<String> conceptIdsToAdd = new ArrayList<>();
			String conceptIdsToRemove = "";
			RefsetMemberService.REFSETS_UPDATED_MEMBERS.put(refset.getId(), new HashMap<>());

			final ResultList<UpgradeInactiveConcept> inactiveConceptList = service
					.find("refsetId: " + refset.getRefsetId(), null, UpgradeInactiveConcept.class, null);

			for (final UpgradeInactiveConcept inactiveConcept : inactiveConceptList.getItems()) {

				if (!conceptIdsToRemove.contains("," + inactiveConcept.getCode() + ",")
						&& inactiveConcept.isStillMember()) {
					conceptIdsToRemove += inactiveConcept.getCode() + ",";
				}

				for (final UpgradeReplacementConcept replacementConcept : inactiveConcept.getReplacementConcepts()) {

					if (!replacementConcept.isAdded() && !replacementConcept.isExistingMember()) {

						if (!conceptIdsToAdd.contains(replacementConcept.getCode())) {
							conceptIdsToAdd.add(replacementConcept.getCode());
						}

					}

				}

			}

			// add the concepts as members to the refset
			unaddedConcepts = RefsetMemberService.addRefsetMembers(service, user, refset, conceptIdsToAdd);

			// remove the inactive concepts from the refset
			conceptIdsToRemove = StringUtils.removeEnd(conceptIdsToRemove, ",");
			unremovedConcepts = RefsetMemberService.removeRefsetMembers(service, user, refset, conceptIdsToRemove);

			// to make searching easier
			final Set<String> removedConcepts = new HashSet<String>(Arrays.asList(conceptIdsToRemove.split(",")));
			removedConcepts.removeAll(unremovedConcepts);

			for (final UpgradeInactiveConcept inactiveConcept : inactiveConceptList.getItems()) {

				boolean changeInactive = false;

				for (final UpgradeReplacementConcept replacementConcept : inactiveConcept.getReplacementConcepts()) {

					// don't process concepts that couldn't be added or that weren't attempted to be
					// added
					if (unaddedConcepts.contains(replacementConcept.getCode())
							|| !conceptIdsToAdd.contains(replacementConcept.getCode())) {

						continue;
					}

					changeInactive = true;
					replacementConcept.setAdded(true);
					service.update(replacementConcept);
					LOG.debug("addAllUpgradeReplacementConcepts: replacement added as member: "
							+ replacementConcept.getCode());
				}

				if (changeInactive) {
					inactiveConcept.setReplaced(true);
				}

				if (removedConcepts.contains(inactiveConcept.getCode())) {

					inactiveConcept.setStillMember(false);
					changeInactive = true;
				}

				if (changeInactive) {

					service.update(inactiveConcept);
					LOG.debug(
							"addAllUpgradeReplacementConcepts: inactive concept updated: " + inactiveConcept.getCode());
				}
			}

			// see if there the concept was unable to be changed and craft the error message
			if (unaddedConcepts.size() > 0 || unremovedConcepts.size() > 0) {

				if (unaddedConcepts.size() > 0) {
					message = "The concepts " + unaddedConcepts + " were unable to be added. ";
				}

				if (unremovedConcepts.size() > 0) {
					message += "The inactive concepts " + unremovedConcepts + " were unable to be removed.";
				}

			} else {
				message = "All changes made successfully";
			}

			return message;

		} catch (final Exception e) {

			throw new Exception(e);
		}

		finally {

			RefsetMemberService.REFSETS_BEING_UPDATED.remove(refset.getId());
		}

	}

	/**
	 * Search for replacement concepts for Upgrade.
	 *
	 * @param user             the user
	 * @param service          the terminology service
	 * @param refset           the refset
	 * @param searchParameters the search parameters
	 * @return the upgrade replacement concept result list
	 * @throws Exception the exception
	 */
	public static ResultList<UpgradeReplacementConcept> replacementConceptSearch(final User user,
			final TerminologyService service, final Refset refset, final SearchParameters searchParameters)
			throws Exception {

		final ResultList<UpgradeReplacementConcept> replacementConcepts = new ResultList<>();
		searchParameters.setActiveOnly(true);

		final ConceptResultList concepts = conceptDropdownSearch(user, service, refset, searchParameters, "non members",
				true);

		for (final Concept concept : concepts.getItems()) {

			final UpgradeReplacementConcept replacementConcept = new UpgradeReplacementConcept();
			replacementConcept.setCode(concept.getCode());
			replacementConcept.setReason("MANUAL_REPLACEMENT");

			if (concept.getDescriptions().size() > 0) {

				replacementConcept.setDescriptions(ModelUtility.toJson(concept.getDescriptions()));
			}

			replacementConcepts.getItems().add(replacementConcept);
		}

		replacementConcepts.setTotal(concepts.getTotal());
		replacementConcepts.setTotalKnown(concepts.isTotalKnown());

		LOG.debug("replacementConceptSearch: results: " + ModelUtility.toJson(replacementConcepts));

		return replacementConcepts;
	}

	/**
	 * Search for concepts for display in dropdown options.
	 *
	 * @param user              the user
	 * @param service           the terminology service
	 * @param refset            the refset
	 * @param searchParameters  the search parameters
	 * @param searchMembersMode Should the search be for only for members, non
	 *                          members, or all concepts. Values: 'all', 'members',
	 *                          'non members'
	 * @param getDescriptions   should all descriptions be populated
	 * @return the upgrade replacement concept result list
	 * @throws Exception the exception
	 */
	public static ConceptResultList conceptDropdownSearch(final User user, final TerminologyService service,
			final Refset refset, final SearchParameters searchParameters, final String searchMembersMode,
			final boolean getDescriptions) throws Exception {

		searchParameters.setEditing(false);

		if (searchParameters.getLimit() <= 0) {

			searchParameters.setLimit(10);
		}

		final ConceptResultList concepts = searchConcepts(refset, searchParameters, searchMembersMode,
				searchParameters.getLimit() * 6);

		if (concepts.getItems().size() == 0) {

			return concepts;
		}

		if (getDescriptions) {

			populateAllLanguageDescriptions(refset, concepts.getItems());
		}

		LOG.debug("conceptDropdownSearch: results: " + ModelUtility.toJson(concepts));

		return concepts;
	}

	/**
	 * Compile and store the data to upgrade a refset.
	 *
	 * @param service                    the Terminology Service
	 * @param user                       the user
	 * @param activeRefsetInternalId     the internal refset ID of the active refset
	 * @param comparisonRefsetInternalId the internal refset ID of the comparison
	 *                                   refset
	 * @return The operation status
	 * @throws Exception the exception
	 */
	public static String compileComparisonData(final TerminologyService service, final User user,
			final String activeRefsetInternalId, final String comparisonRefsetInternalId) throws Exception {

		final String status = "Comparison data compiled";
		final Refset activeRefset = service.get(activeRefsetInternalId, Refset.class);
		final Refset comparisonRefset = service.get(comparisonRefsetInternalId, Refset.class);
		final RefsetMemberComparison refsetMemberComparison = new RefsetMemberComparison();
		refsetMemberComparison.setActiveRefsetInternalId(activeRefset.getId());
		refsetMemberComparison.setComparisonRefsetInternalId(comparisonRefset.getId());
		refsetMemberComparison.setActiveRefsetId(activeRefset.getRefsetId());
		refsetMemberComparison.setComparisonRefsetId(comparisonRefset.getRefsetId());
		refsetMemberComparison.setActiveRefsetName(activeRefset.getName());
		refsetMemberComparison.setComparisonRefsetName(comparisonRefset.getName());
		final boolean editing = activeRefset.getWorkflowStatus().equals(WorkflowService.IN_EDIT);
		final TreeMap<String, Concept> comparisonRefsetMembers = new TreeMap<>();
		final TreeMap<String, Concept> activeRefsetMembers = new TreeMap<>();
		final SearchParameters searchParameters = new SearchParameters();
		searchParameters.setLimit(10);
		searchParameters.setEditing(editing);

		// remove any existing comparison data for this refset
		SecurityService.removeFromSession("refsetMemberComparison_" + activeRefsetInternalId);

		// Change the numbers to '1's to avoid threading
		final ExecutorService executor = new ThreadPoolExecutor(30, 30, 0L, TimeUnit.MILLISECONDS,
				new ArrayBlockingQueue<>(30), new ThreadPoolExecutor.CallerRunsPolicy());

		for (final String refsetType : Arrays.asList("active", "comparison")) {

			executor.submit(new Runnable() {

				/* see superclass */
				@Override
				public void run() {

					try {

						final Refset refset;

						if (refsetType.equals("active")) {

							refset = activeRefset;
						} else {

							refset = comparisonRefset;
						}

						final List<String> nonDefaultPreferredTerms = identifyNonDefaultPreferredTerms(
								refset.getEdition());
						final ResultList<Concept> concepts = getMemberList(refset, nonDefaultPreferredTerms,
								searchParameters);
						final TreeMap<String, Concept> members;

						if (refsetType.equals("active")) {

							members = activeRefsetMembers;
						} else {

							members = comparisonRefsetMembers;
						}

						for (final Concept concept : concepts.getItems()) {

							members.put(concept.getCode(), concept);
						}

					} catch (final Exception e) {

						throw new RuntimeException(e);
					}

				}
			});
		}

		executor.shutdown();
		executor.awaitTermination(10, TimeUnit.MINUTES);

		refsetMemberComparison.setActiveRefsetMemberTotal(activeRefsetMembers.size());
		refsetMemberComparison.setComparisonRefsetMemberTotal(comparisonRefsetMembers.size());

		for (final Map.Entry<String, Concept> activeMemberEntry : activeRefsetMembers.entrySet()) {

			final String activeConceptId = activeMemberEntry.getKey();
			final Concept activeConcept = activeMemberEntry.getValue();
			final Map<String, String> returnMap = new HashMap<>();
			returnMap.put("code", activeConceptId);
			returnMap.put("active", activeConcept.isActive() + "");
			returnMap.put("memberOfRefset", "true");
			returnMap.put("definitionExceptionType", activeConcept.getDefinitionExceptionType());
			returnMap.put("hasChildren", "false"); // activeConcept.getHasChildren() + "");
			Map<String, String> preferedTermEnglish = null; // activeConcept.getDescriptions().stream().filter(f ->
															// f.get(LANGUAGE_ID).equals(PREFERRED_TERM_EN)).findFirst().get();

			boolean foundDescription = false;

			for (final Map<String, String> description : activeConcept.getDescriptions()) {

				if (description == null) {
					continue;
				}

				if (description.get(LANGUAGE_ID).equals(PREFERRED_TERM_EN)) {

					preferedTermEnglish = description;
					foundDescription = true;
					break;
				}
			}

			if (!foundDescription) {
				LOG.error("Could not get english description for concept ID: " + activeConceptId);
			}

			returnMap.put("name", (preferedTermEnglish != null) ? preferedTermEnglish.get(DESCRIPTION_TERM).strip()
					: activeConcept.getName().strip());

			// check to see if this member is also a member of the comparison refset
			if (comparisonRefsetMembers.containsKey(activeConceptId)) {

				returnMap.put("membership", "Both");
			} else {

				returnMap.put("membership", "Active Reference Set");
				refsetMemberComparison.getActiveRefsetDistinctMembers().add(activeConceptId);
			}

			refsetMemberComparison.getItems().add(returnMap);
		}

		// since the members of the active or both refsets are handled, remove all but
		// the unique comparison refset members
		comparisonRefsetMembers.keySet().removeAll(activeRefsetMembers.keySet());

		for (final Map.Entry<String, Concept> comparisonMemberEntry : comparisonRefsetMembers.entrySet()) {

			final String comparisonConceptId = comparisonMemberEntry.getKey();
			final Concept comparisonConcept = comparisonMemberEntry.getValue();
			final Map<String, String> returnMap = new HashMap<>();
			returnMap.put("code", comparisonConceptId);
			returnMap.put("active", comparisonConcept.isActive() + "");
			returnMap.put("memberOfRefset", "false");
			returnMap.put("definitionExceptionType", comparisonConcept.getDefinitionExceptionType());
			returnMap.put("hasChildren", "false"); // comparisonConcept.getHasChildren() + "");
			returnMap.put("membership", "Comparison Reference Set");
			refsetMemberComparison.getComparisonRefsetDistinctMembers().add(comparisonConceptId);

			final Map<String, String> preferedTermEnglish = comparisonConcept.getDescriptions().stream()
					.filter(f -> f != null && PREFERRED_TERM_EN.equals(f.get(LANGUAGE_ID))).findFirst().orElse(null);

			returnMap.put("name", (preferedTermEnglish != null) ? preferedTermEnglish.get(DESCRIPTION_TERM).strip()
					: comparisonConcept.getName().strip());

			refsetMemberComparison.getItems().add(returnMap);
		}

		refsetMemberComparison
				.setActiveRefsetDistinctMembersCount(refsetMemberComparison.getActiveRefsetDistinctMembers().size());
		refsetMemberComparison.setComparisonRefsetDistinctMembersCount(
				refsetMemberComparison.getComparisonRefsetDistinctMembers().size());

		final String uuid = UUID.randomUUID().toString();
		SecurityService.setInMemoryStorage(uuid, refsetMemberComparison);
		final boolean wasSet = SecurityService.setInSession("refsetMemberComparison_" + activeRefsetInternalId, uuid);
		LOG.debug("compileComparisonData setInSession: " + wasSet);
		LOG.debug("compileComparisonData refsetMemberComparison: " + refsetMemberComparison);

		return status;
	}

	/**
	 * Clear unique refset versions.
	 *
	 * @param refsetId the refset id
	 */
	public static void clearRefsetVersionsWithChanges(final String refsetId) {

		if (REFSET_TO_PUBLISHED_VERSION_MAP.containsKey(refsetId)) {

			REFSET_TO_PUBLISHED_VERSION_MAP.get(refsetId).clear();
		}

	}

	/**
	 * Clear versions with changes.
	 */
	public static void clearVersionsWithChanges() {

		REFSET_TO_PUBLISHED_VERSION_MAP.clear();
	}
}
