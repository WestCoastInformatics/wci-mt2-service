/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.handler.ExportHandler;
import org.ihtsdo.refsetservice.handler.TerminologyServerHandler;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.ihtsdo.refsetservice.util.FileUtility;
import org.ihtsdo.refsetservice.util.HandlerUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.StringUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service class to handle getting and modifying internal mapset information.
 */
public class MapSetService {

	/** The Constant LOG. */
	private static final Logger LOG = LoggerFactory.getLogger(MapSetService.class);
  
  /** The config properties. */
  protected static final Properties PROPERTIES = PropertyUtility.getProperties();  
  final static String EXPORT_DOWNLOAD_URL = PROPERTIES.getProperty("REFSET_EXPORT_DIR");  
  
  /** The max number of record elasticsearch will return without erroring. */
  private static final int ELASTICSEARCH_MAX_RECORD_LENGTH = 9990; //
 
	/** The config properties. */
	protected static final Properties PROPERTIES = PropertyUtility.getProperties();

	/** The Constant EXPORT_DOWNLOAD_URL. */
	final static String EXPORT_DOWNLOAD_URL = PROPERTIES.getProperty("REFSET_EXPORT_DIR");

	/** The max number of record elasticsearch will return without erroring. */
	private static final int ELASTICSEARCH_MAX_RECORD_LENGTH = 9990; //

	/** The terminology handler. */
	private static TerminologyServerHandler terminologyHandler;

	static {

		// Instantiate terminology handler
		try {
			final String key = "terminology.handler";
			final String handlerName = PropertyUtility.getProperty(key);
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
	 * Returns the map set.
	 *
	 * @param branch the branch
	 * @param code   the code
	 * @return the map set
	 * @throws Exception the exception
	 */
	public static MapSet getMapSet(final String branch, final String code) throws Exception {

		return terminologyHandler.getMapSet(branch, code);

	}

	/**
	 * Returns the map sets.
	 *
	 * @param branch the branch
	 * @return the map sets
	 * @throws Exception the exception
	 */
	public static List<MapSet> getMapSets(final String branch) throws Exception {

		return terminologyHandler.getMapSets(branch);

	}

	/**
	 * Get the refset member concepts in RF2 format.
	 *
	 * @param service                the Terminology Service
	 * @param mapProject             the map project
	 * @param branch                 the branch
	 * @param mapsetId               the mapset id
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
	public static String exportMapsetRf2(final TerminologyService service, final MapProject mapProject,
			final String branch, final String mapsetId, final String type, final String languageId,
			final String fileNameDate, final String startEffectiveTime, final String transientEffectiveTime,
			final boolean exportMetadata, final boolean withNames) throws Exception {

		return exportMapsetRf2File(mapProject, branch, mapsetId, type, languageId, fileNameDate, startEffectiveTime,
				transientEffectiveTime, exportMetadata, withNames);
	}

	/**
	 * Get the refset member concepts in RF2 format.
	 *
	 * @param mapProject             the map project
	 * @param branch                 the branch
	 * @param mapsetCode             the internal mapsetCode
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
	private static String exportMapsetRf2File(final MapProject mapProject, final String branch, final String mapsetCode,
			final String type, final String languageId, final String fileNameDate, final String startEffectiveTime,
			final String transientEffectiveTime, final boolean exportMetadata, final boolean withNames)
			throws Exception {

		try {
			final ExportHandler exporter = new ExportHandler();
			final MapSet mapset = terminologyHandler.getMapSet(branch, mapsetCode);

			if (mapset == null) {
				throw new Exception("Mapset Internal Id: " + mapsetCode + " does not exist in the MT2 database");
			}

			final String mt2VersionFileName = exporter.generateMt2VersionFileName(mapProject, mapset,
					type.toUpperCase(), languageId, transientEffectiveTime, startEffectiveTime, exportMetadata,
					withNames);
			final String awsVersionedPath = exporter.generateAwsMt2BaseVersionPath(branch, mapset, type.toUpperCase(),
					transientEffectiveTime, startEffectiveTime);

			// Check if file already exists
			if (!S3ConnectionWrapper.isInS3Cache(awsVersionedPath, mt2VersionFileName)) {
				// Mt2 Version File doesn't reside on s3

				// Snowstorm generated RF2 file
				final String snowGeneratedFileName = exporter.generateMt2SnowVersionFileName(mapProject, mapset,
						type.toUpperCase(), transientEffectiveTime, startEffectiveTime);

				// Local place to store snowBaseVersionFileName
				final Path localSnowGeneratedTempDir = Files.createTempDirectory("mt2LocalSnowGenerated-");

				// Local Snowstorm generated Rf2 file name
				final String localSnowGeneratedFilePath = localSnowGeneratedTempDir + File.separator
						+ snowGeneratedFileName;

				// Check if SnowS version file name does already exist in S3
				// Cache
				if (!S3ConnectionWrapper.isInS3Cache(awsVersionedPath, snowGeneratedFileName)) {
					// Base-SnowVersion file is not on S3, so generate it, and
					// after downloading it, store it on S3

					// Generate file on Snowstorm
					final String entityString = "{\"refsetIds\": [\"" + mapset.getRefSetCode()
							+ "\"],  \"branchPath\": \"" + branch
							+ "\", \"conceptsAndRelationshipsOnly\": false, \"filenameEffectiveDate\": \""
							+ fileNameDate
							+ "\", \"legacyZipNaming\": false, \"type\": \"SNAPSHOT\", \"unpromotedChangesOnly\": false"
							+ (startEffectiveTime == null ? ""
									: ",  \"startEffectiveTime\": \"" + startEffectiveTime + "\"")
							+ (transientEffectiveTime == null ? ""
									: ",  \"transientEffectiveTime\": \"" + transientEffectiveTime + "\"")
							+ "}";

					LOG.info("Gen snowstorm " + entityString);

					// Generate on Snowstorm
					final String snowGeneratedFileUrl = exporter.generateSnowVersionFile(entityString);

					LOG.info("Downloading file from snowstorm :- " + snowGeneratedFileUrl);

					// Download file from SnowS
					exporter.downloadSnowGeneratedFile(snowGeneratedFileUrl, localSnowGeneratedFilePath);

					// store file one s3
					LOG.info("uploading snowstorm genned file to S3");
					S3ConnectionWrapper.uploadToS3(awsVersionedPath, localSnowGeneratedTempDir.toString(),
							snowGeneratedFileName); // Why, No clear advantage , NUNO ?
				} else {

					LOG.info("Downloading snowstorm genned file from S3");
					S3ConnectionWrapper.downloadFileFromS3(awsVersionedPath, snowGeneratedFileName,
							localSnowGeneratedFilePath);
				}

				LOG.info("converting snowstorm genned file to MT2 format");
				// Have access to localSnowGeneratedFilePath from which mt2 will generate the
				// export file
				generateMt2ExportFile(mapProject, branch, mapset, type.toUpperCase(), localSnowGeneratedFilePath,
						mt2VersionFileName, exportMetadata, withNames, languageId, transientEffectiveTime,
						startEffectiveTime);

				S3ConnectionWrapper.uploadToS3(awsVersionedPath, EXPORT_DOWNLOAD_URL, mt2VersionFileName);

				FileUtility.deleteDirectory(localSnowGeneratedTempDir.toFile());

				// if download is from MT2 server
				// ServletUriComponentsBuilder builder =
				// ServletUriComponentsBuilder.fromCurrentContextPath();
			} else {

				if (!Files.exists(Path.of(EXPORT_DOWNLOAD_URL + mt2VersionFileName))) {

					LOG.info("Downloading RT2 genned file from S3");
					S3ConnectionWrapper.downloadFileFromS3(awsVersionedPath, mt2VersionFileName,
							EXPORT_DOWNLOAD_URL + mt2VersionFileName);
				}
			}

			LOG.info("Final Export File Path: " + EXPORT_DOWNLOAD_URL + mt2VersionFileName);
			return mt2VersionFileName;
		} catch (final Exception ex) {
			throw new Exception("Failed to export zip file name " + ex.getMessage(), ex);
		}
	}

	/**
	 * Generate MT2 export file.
	 *
	 * @param mapProject                 the map project
	 * @param branch                     the branch
	 * @param mapset                     the mapset
	 * @param type                       the type
	 * @param localSnowGeneratedFilePath the local snow generated file path
	 * @param rt2VersionFileName         the mt2 version file name
	 * @param exportMetadata             the export metadata
	 * @param appendNames                the append names
	 * @param languageId                 the language id
	 * @param transientEffectiveTime     the transient effective time
	 * @param startEffectiveTime         the start effective time
	 * @return the string
	 * @throws Exception the exception
	 */
	private static void generateMt2ExportFile(final MapProject mapProject, final String branch, final MapSet mapset,
			final String type, final String localSnowGeneratedFilePath, final String rt2VersionFileName,
			final boolean exportMetadata, final boolean appendNames, final String languageId,
			final String transientEffectiveTime, final String startEffectiveTime) throws Exception {

		// Generate the Mt2 version of mapset RF2 Zip file
		final Path builderDirectoryTempDir = Files.createTempDirectory("mt2Builder-");

		LOG.info("creating builder temp dir: " + builderDirectoryTempDir.toString());

		// Unzip the download if snapshot
		List<String> sourceFiles = new ArrayList<>();
		sourceFiles = FileUtility.unzipFiles(localSnowGeneratedFilePath, builderDirectoryTempDir.toString());

		LOG.info("unzipped source files: " + ModelUtility.toJson(sourceFiles));
		if (sourceFiles.size() >= 1) {
			// file clean up - delete terminology and make it root path
			try {
				FileUtility.processFolder(builderDirectoryTempDir.toString());
				LOG.info("MT2 folder clean up processed successfully. ");

				// Refresh sourceFiles with the updated directory contents
				sourceFiles.clear();
				// Use a temporary list to store refreshed files
				List<String> refreshedFiles = new ArrayList<>();
				Files.walk(Paths.get(builderDirectoryTempDir.toString())).filter(Files::isRegularFile)
						.forEach(file -> refreshedFiles.add(file.toString()));

				// Assign the refreshed list to sourceFiles
				sourceFiles = refreshedFiles;
				LOG.info("Refreshed source files: " + ModelUtility.toJson(sourceFiles));

			} catch (IOException e) {
				System.err.println("Error processing zip file: " + e.getMessage());
				e.printStackTrace();
			}

		} else if (sourceFiles.size() == 0) {

			final Path path = Path.of(builderDirectoryTempDir.toString() + File.separator + "noresults.txt");
			Files.write(path,
					("No results for Reference Set " + mapset.getRefSetCode()).getBytes(StandardCharsets.UTF_8));
			sourceFiles.add(path.toString());
		}

		// remove effectiveTime
		if (!"PUBLISHED".equalsIgnoreCase(mapset.getVersionStatus())) {
			FileUtility.removeEffectiveTime(builderDirectoryTempDir.toString()); // snowGeneratedRf2FilePath
		}

		// if exportMetadata requested, add it
		if (exportMetadata) {
			String exportMapset = exportMapsetMetadata(mapProject, branch, mapset, type,
					builderDirectoryTempDir.toString(), transientEffectiveTime);
			sourceFiles.add(exportMapset);
		}

		LOG.info("ready to be zipped if not already zipped source files: " + ModelUtility.toJson(sourceFiles));

		// Check if any source file is not already zipped
		boolean allFilesAlreadyZipped = sourceFiles.stream()
				.allMatch(filePath -> filePath.toLowerCase().endsWith(".zip"));

		if (allFilesAlreadyZipped) {
			LOG.info("All source files are already zipped. No need to zip them again." + rt2VersionFileName);
			// Copy the file , No zipping needed
			Files.copy(Paths.get(sourceFiles.toString().replace("[", "").replace("]", "")),
					Paths.get(EXPORT_DOWNLOAD_URL + rt2VersionFileName));

		} else {
			// zip the files together
			FileUtility.zipFiles(sourceFiles, EXPORT_DOWNLOAD_URL + rt2VersionFileName);
		}

		// Delete directory structure and original zip
		FileUtility.deleteDirectory(builderDirectoryTempDir.toFile());
	}

	/**
	 * Get either the version date or the current date in yyyy-MM-dd format.
	 *
	 * @param mapset the mapset
	 * @return the URL of the file containing the metadata
	 * @throws Exception the exception
	 */
	public static String getRefsetAsOfDate(final MapSet mapset) throws Exception {

		String asOfDate = "";
		final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");

		if (mapset.getModified() != null) {

			asOfDate = simpleDateFormat.format(mapset.getModified());
		} else {

			asOfDate = simpleDateFormat.format(new Date());
		}
		return asOfDate;
	}

	/**
	 * Export the refset metadata in a text format.
	 *
	 * @param mapProject             the map project
	 * @param branch                 the branch
	 * @param mapset                 the mapset
	 * @param type                   the type
	 * @param directory              the directory
	 * @param transientEffectiveTime the transient effective time
	 * @return the URL of the file containing the metadata
	 * @throws Exception the exception
	 */
	public static String exportMapsetMetadata(final MapProject mapProject, final String branch, final MapSet mapset,
			final String type, final String directory, final String transientEffectiveTime) throws Exception {

		final StringBuilder fileLines = new StringBuilder();
		// final String pathDate = getRefsetAsOfDate(mapset);

		final String outputPath = "der2_iisssccRefset" + mapProject.getDestinationTerminology() + "ExtendedMap"
				+ mapset.getRefSetCode() + StringUtility.capitalizeEachWord(mapProject.getEdition().getAbbreviation())
				+ "_" + transientEffectiveTime + "_metadata.txt";

		final String separator = "\t";
		final String LINE_FEED = "\n";

		fileLines.append("Mapset ID" + separator + mapset.getRefSetCode() + LINE_FEED);
		fileLines.append("Mapset Name" + separator + mapset.getRefSetName() + LINE_FEED);
		fileLines.append("Edition Name" + separator + mapProject.getEdition().getShortName() + LINE_FEED);
		fileLines.append("Edition Branch" + separator + branch + LINE_FEED);
		fileLines.append("Organization" + separator + mapProject.getEdition().getOrganization().getName() + LINE_FEED);
		fileLines.append("Project" + separator + mapProject.getName() + LINE_FEED);
		fileLines.append("Module ID" + separator + mapProject.getModuleId() + LINE_FEED);
		fileLines.append("Mapset Version Status" + separator + mapset.getVersionStatus() + LINE_FEED); // PUBLISHED

		if (mapset.getModified() != null) { // Version Date
			fileLines.append("MapSet Version Date" + separator
					+ DateUtility.formatDate(mapset.getModified(), DateUtility.DATE_FORMAT_REVERSE, null) + LINE_FEED);
		} else {
			fileLines.append("MapSet Version Date" + separator + LINE_FEED);
		}

		fileLines.append("MapSet Last Modified Date" + separator
				+ DateUtility.formatDate(mapset.getModified(), DateUtility.DATE_FORMAT_REVERSE, null) + LINE_FEED);
		fileLines.append("MapSet Type" + separator + type + LINE_FEED);

		if (mapset.isActive()) {
			fileLines.append("MapSet Status" + separator + "Active" + LINE_FEED);
		} else {
			fileLines.append("MapSet Status" + separator + "Inactive" + LINE_FEED);
		}

		if (StringUtils.isNotBlank(mapset.getRefSetName())) { // getNarrative
			fileLines.append("MapSet Narrative" + separator + mapset.getRefSetName() + LINE_FEED);
		}

		// print the mapset file
		try (final FileOutputStream fileOutputStream = new FileOutputStream(outputPath);
				final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(fileOutputStream,
						StandardCharsets.UTF_8);

				final PrintWriter printWriter = new PrintWriter(outputStreamWriter);) {
			printWriter.print(fileLines);
			return outputPath;
		} catch (final Exception ex) {
			throw new Exception("Could not create metadata mapset export txt file: " + ex.getMessage(), ex);
		}

	}

	/**
	 * Export mapset sctid list.
	 *
	 * @param service                the service
	 * @param mapProject             the map project
	 * @param branch                 the branch
	 * @param mapsetId               the mapset id
	 * @param exportType             the export type
	 * @param languageId             the language id
	 * @param fileNameDate           the file name date
	 * @param startEffectiveTime     the start effective time
	 * @param transientEffectiveTime the transient effective time
	 * @param exportMetadata         the export metadata
	 * @return the string
	 * @throws Exception the exception
	 */
	public static String exportMapsetSctidList(TerminologyService service, MapProject mapProject, String branch,
			String mapsetId, String exportType, String languageId, String fileNameDate, String startEffectiveTime,
			String transientEffectiveTime, boolean exportMetadata) throws Exception {

		final int limit = ELASTICSEARCH_MAX_RECORD_LENGTH;
		boolean hasMorePages = true;
		final Set<String> uniqueConceptIds = new HashSet<>(); // Use a Set to ensure uniqueness
		String zipOutputPath = EXPORT_DOWNLOAD_URL;
		String mapsetFileName = "";
		String sctidsFilePath = "";
		final List<String> sourceFiles = new ArrayList<>();
		Path tempDirectoryPath = null;

		try {

			final MapSet mapset = terminologyHandler.getMapSet(branch, mapsetId);

			if (mapset == null) {
				throw new Exception("Mapset Internal Id: " + mapsetId + " does not exist in the MT2 database");
			}

			mapsetFileName = "mapset_" + mapset.getRefSetCode() + "_" + getRefsetAsOfDate(mapset) + "_member_ids.txt";
			zipOutputPath += mapsetFileName.replace(".txt", ".zip");
			tempDirectoryPath = Files.createTempDirectory("sctidList-" + mapsetFileName.replace(".txt", ""));
			sctidsFilePath = tempDirectoryPath.toString() + File.separator + mapsetFileName;

			LOG.info("SCTID txt output path = " + sctidsFilePath);
			LOG.info("zip output path = " + zipOutputPath);

			// if exportMetadata requested, add it
			if (exportMetadata) {
				String exportMapset = exportMapsetMetadata(mapProject, branch, mapset, exportType,
						tempDirectoryPath.toString(), transientEffectiveTime);
				sourceFiles.add(exportMapset);
			}

			String searchAfter = "";
			while (hasMorePages) {
				final String resultString = getMemberSctids(mapset.getRefSetCode(), limit, searchAfter, branch);
				// LOG.info("exportRefsetSctidList: resultString" + resultString);
				final ObjectMapper mapper = new ObjectMapper();
				final JsonNode root = mapper.readTree(resultString);
				final JsonNode items = root.get("items");
				final Iterator<JsonNode> iterator = items.iterator();
				LOG.info("exportRefsetSctidList items.size(): " + items.size());

				if (root.get("searchAfter") != null) {
					searchAfter = root.get("searchAfter").asText();
				} else {
					searchAfter = "";
				}

				LOG.info("exportRefsetSctidList searchAfter: " + searchAfter);

				if (items.size() < limit) {
					hasMorePages = false;
				}

				LOG.info("exportRefsetSctidList hasMorePages: " + hasMorePages);

				while (iterator.hasNext()) {
					final JsonNode item = iterator.next();
					final String conceptId = (item.get("referencedComponentId").asText());
					uniqueConceptIds.add(conceptId); // Add to the Set
				}
			}
		} catch (final Exception ex) {
			throw new Exception("Could not get Reference Set member data from snowstorm: " + ex.getMessage(), ex);
		}

		// Write the unique SCTIDs to the output file
		try (final PrintWriter writer = new PrintWriter(
				new OutputStreamWriter(new FileOutputStream(sctidsFilePath), StandardCharsets.UTF_8))) {
			for (String conceptId : uniqueConceptIds) {
				writer.println(conceptId);
			}
		} catch (final Exception ex) {
			throw new Exception("Could not create export txt file: " + ex.getMessage(), ex);
		}

		// zip the files together
		sourceFiles.add(sctidsFilePath);
		FileUtility.zipFiles(sourceFiles, zipOutputPath);

		// Delete temp directory structure and files
		FileUtility.deleteDirectory(tempDirectoryPath.toFile());

		// if download is from RT2 server
		final String zippedFileUrl = EXPORT_DOWNLOAD_URL + mapsetFileName.replace(".txt", ".zip");

		return zippedFileUrl;
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

		final String pagingParams = "&limit=" + limit + "&searchAfter=" + searchAfter;

		final String url = SnowstormConnection.getBaseUrl() + "" + branchPath + "/members?referenceSet=" + refsetId
				+ "&" + pagingParams;

		LOG.info("Snowstorm getMemberSctIds URL: " + url);

		try (final Response response = SnowstormConnection.getResponse(url)) {

			if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

				throw new Exception("Call to URL '" + url + "' wasn't successful. Status: " + response.getStatus()
						+ " Message: " + formatErrorMessage(response));
			}

			final String resultString = response.readEntity(String.class);
			return resultString;

		} catch (final Exception ex) {

			throw new Exception("Could not retrieve Reference Set members from snowstorm: " + ex.getMessage(), ex);
		}

	}

	/**
	 * Format error message.
	 *
	 * @param response the response
	 * @return the string
	 */
	private static String formatErrorMessage(final Response response) {

		String snowstormErrorMessage = response.readEntity(String.class);
		if (StringUtils.isEmpty(snowstormErrorMessage)) {
			return "";
		}
		if (StringUtility.isJson(snowstormErrorMessage)) {
			final ObjectMapper mapper = new ObjectMapper();
			try {
				final JsonNode json = mapper.readTree(snowstormErrorMessage);
				snowstormErrorMessage = json.has("message") ? json.get("message").asText() : "";
			} catch (final Exception e) {
				LOG.error("formatErrorMessage snowstormErrorMessage:{}", snowstormErrorMessage, e);
			}
		}
		return snowstormErrorMessage.replaceAll("[\\r\\n]+", " ");
	}

}
