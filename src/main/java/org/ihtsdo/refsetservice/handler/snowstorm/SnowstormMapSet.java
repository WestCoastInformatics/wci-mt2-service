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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.handler.ExportHandler;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapSetExportRequest;
import org.ihtsdo.refsetservice.model.enums.FileExportType;
import org.ihtsdo.refsetservice.model.enums.FileFormatType;
import org.ihtsdo.refsetservice.terminologyservice.S3ConnectionWrapper;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.ihtsdo.refsetservice.util.FileUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.StringUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The Class SnowstormMapset.
 */
public class SnowstormMapSet extends SnowstormAbstract {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SnowstormMapSet.class);

    /** The Constant EXPORT_DOWNLOAD_URL. */
    private static String exportFileDir;

    /** The column index of the concept ID in an RF2 file. */
    public static final int REFEST_RF2_CONCEPTID_COLUMN = 5;

    /** The Constant CONCEPT_DESCRIPTIONS_PER_CALL. */
    private static final int CONCEPT_DESCRIPTIONS_PER_CALL = 230;

    /** The description language code and type combined. */
    private static final String LANGUAGE_ID = "languageId";

    /** The description term. */
    private static final String DESCRIPTION_TERM = "term";

    /** The Constant DEFAULT_LANGUAGE_REFSET_US. */
    public static final String DEFAULT_LANGUAGE_REFSET_US = "900000000000509007";

    static {
        exportFileDir = PropertyUtility.getProperty("mapexport.fileDir");
    }

    /**
     * Get the refset member concepts in RF2 format.
     *
     * @param mapProject the map project
     * @param mapSetExportRequest the map set export request
     * @return the refset member concepts
     * @throws Exception the exception
     */
    public static String exportMapSet(final MapProject mapProject, final MapSetExportRequest mapSetExportRequest) throws Exception {

        final MapSet mapSet = SnowstormMapping.getMapSet(mapSetExportRequest.getBranch(), mapSetExportRequest.getMapSetCode());
        mapSet.setBranchPath("MAIN/SNOMEDCT-NO/2024-04-15");

        if (mapSetExportRequest.getFileExportType() == FileExportType.RF2 || mapSetExportRequest.getFileExportType() == FileExportType.RF2_WITH_NAMES) {

            // TODO: add back when delta is completed. There are a number of things to resolve around versions.
            final List<Map<String, String>> mapSetVersionList = new ArrayList<>();

            mapSetVersionList.add(Map.of("date", "2023-11-15"));
            mapSetVersionList.add(Map.of("date", "2023-12-15"));
            mapSetVersionList.add(Map.of("date", "2023-12-20"));
            mapSetVersionList.add(Map.of("date", "2024-01-01"));
            mapSetVersionList.add(Map.of("date", "2024-01-15"));
            mapSetVersionList.add(Map.of("date", "2024-03-15"));
            mapSetVersionList.add(Map.of("date", "2024-04-15"));

            if (mapSetExportRequest.getFileFormatType() == FileFormatType.DELTA) {
                final String downloadUri = exportMapSetRf2DeltaFile(mapSet, mapProject, mapSetExportRequest, mapSetVersionList);
                LOG.info("{} Export completed successfully. Download URI: {}", mapSetExportRequest.getFileFormatType(), downloadUri);
                return downloadUri;
            }

            final String downloadUri = exportMapSetRf2SnapshotFile(mapSet, mapProject, mapSetExportRequest);
            LOG.info("{} Export completed successfully. Download URI: {}", mapSetExportRequest.getFileFormatType(), downloadUri);
            return downloadUri;

        } else if (mapSetExportRequest.getFileExportType() == FileExportType.SCTIDS) {

            final String downloadUri = exportMapSetSctIdList(mapProject, mapSetExportRequest);
            LOG.info("SCTIDs Export completed successfully. Download URI: {}", downloadUri);
            return downloadUri;

        } else {
            throw new IllegalArgumentException("Invalid format specified: " + mapSetExportRequest.getFileExportType());
        }
    }

    /**
     * Export map set rf 2 snapshot file.
     *
     * @param mapset the mapset
     * @param mapProject the map project
     * @param mapSetExportRequest the map set export request
     * @return the string
     * @throws Exception the exception
     */
    private static String exportMapSetRf2SnapshotFile(final MapSet mapset, final MapProject mapProject, final MapSetExportRequest mapSetExportRequest)
        throws Exception {

        try {
            final ExportHandler exporter = new ExportHandler();

            if (mapset == null) {
                throw new Exception("Mapset Internal Id: " + mapSetExportRequest.getMapSetCode() + " does not exist in the MT2 database");
            }

            final String mt2VersionFileName = exporter.generateMt2VersionFileName(mapProject, mapset, mapSetExportRequest);
            final String projectDir = PropertyUtility.getProperty("aws.project.base.dir");
            final String awsVersionedPath = exporter.generateAwsMt2BaseVersionPath(mapset, mapSetExportRequest, projectDir);

            // Check if file already exists
            if (!S3ConnectionWrapper.isInS3Cache(awsVersionedPath, mt2VersionFileName)) {
                // Mt2 Version File doesn't reside on s3

                // Snowstorm generated RF2 file
                final String snowGeneratedFileName = exporter.generateMt2SnowVersionFileName(mapProject, mapset, mapSetExportRequest.getFileFormatType(),
                    mapSetExportRequest.getTransientEffectiveTime(), mapSetExportRequest.getStartEffectiveTime());

                // Local place to store snowBaseVersionFileName
                final Path localSnowGeneratedTempDir = Files.createTempDirectory("mt2LocalSnowGenerated-");

                // Local Snowstorm generated Rf2 file name
                final String localSnowGeneratedFilePath = localSnowGeneratedTempDir + File.separator + snowGeneratedFileName;

                // Check if SnowS version file name does already exist in S3 Cache
                if (!S3ConnectionWrapper.isInS3Cache(awsVersionedPath, snowGeneratedFileName)) {

                    // Base-SnowVersion file is not on S3, so generate it, and after downloading it, store it on S3

                    final StringBuilder exportRequestParameters = new StringBuilder();
                    exportRequestParameters.append("{ ");
                    exportRequestParameters.append("\"refsetIds\": [\"").append(mapset.getRefSetCode()).append("\"] ");
                    exportRequestParameters.append(", \"branchPath\": \"").append(mapSetExportRequest.getBranch()).append("\" ");
                    exportRequestParameters.append(", \"conceptsAndRelationshipsOnly\": false ");
                    exportRequestParameters.append(", \"filenameEffectiveDate\": \"").append(mapSetExportRequest.getFileNameDate()).append("\" ");
                    exportRequestParameters.append(", \"legacyZipNaming\": false ");
                    exportRequestParameters.append(", \"type\": \"").append(mapSetExportRequest.getFileFormatType()).append("\" ");
                    exportRequestParameters.append(", \"unpromotedChangesOnly\": false");

                    exportRequestParameters.append(", \"moduleIds\": [\"").append(mapProject.getModuleId()).append("\", \"")
                        .append(SNOMEDCT_TO_ICD10_MAPPING_MODULE).append("\"] ");

                    if (StringUtils.isNotBlank(mapSetExportRequest.getTransientEffectiveTime())) {
                        exportRequestParameters.append(", \"transientEffectiveTime\": \"").append(mapSetExportRequest.getTransientEffectiveTime())
                            .append("\" ");
                    }

                    if (StringUtils.isNotBlank(mapSetExportRequest.getStartEffectiveTime())) {
                        exportRequestParameters.append(", \"startEffectiveTime\": \"").append(mapSetExportRequest.getStartEffectiveTime()).append("\" ");
                    }

                    exportRequestParameters.append("}");

                    LOG.info("Snowstorm export request: {}", exportRequestParameters);

                    // Generate on Snowstorm
                    final String snowGeneratedFileUrl = exporter.generateSnowVersionFile(exportRequestParameters.toString());

                    LOG.info("Downloading file from snowstorm : {}", snowGeneratedFileUrl);

                    // Download file from Snowstorm
                    exporter.downloadSnowGeneratedFile(snowGeneratedFileUrl, localSnowGeneratedFilePath);

                    // store file one s3
                    LOG.info("uploading snowstorm generated file to S3");
                    S3ConnectionWrapper.uploadToS3(awsVersionedPath, localSnowGeneratedTempDir.toString(), snowGeneratedFileName);

                } else {

                    LOG.info("Downloading snowstorm generated file from S3");
                    S3ConnectionWrapper.downloadFileFromS3(awsVersionedPath, snowGeneratedFileName, localSnowGeneratedFilePath);
                }

                LOG.info("converting snowstorm generated file to MT2 format");
                // Have access to localSnowGeneratedFilePath from which mt2 will generate the
                // export file
                generateMt2ExportFile(mapProject, mapset, localSnowGeneratedFilePath, mt2VersionFileName, mapSetExportRequest);

                S3ConnectionWrapper.uploadToS3(awsVersionedPath, exportFileDir, mt2VersionFileName);

                FileUtility.deleteDirectory(localSnowGeneratedTempDir.toFile());

            } else {

                final Path exportFilePath = Paths.get(exportFileDir + mt2VersionFileName);

                if (!Files.exists(exportFilePath)) {

                    LOG.info("Downloading RT2 genned file from S3");
                    S3ConnectionWrapper.downloadFileFromS3(awsVersionedPath, mt2VersionFileName, exportFilePath.toString());
                }
            }

            LOG.info("Final Export File Path: {}", Paths.get(exportFileDir, mt2VersionFileName).toString());
            return mt2VersionFileName;
        } catch (final Exception ex) {
            throw new Exception("Failed to export zip file name " + ex.getMessage(), ex);
        }
    }

    /**
     * Export map set rf 2 delta file.
     *
     * @param mapSet the map set
     * @param mapProject the map project
     * @param mapSetExportRequest the map set export request
     * @param mapSetVerionList the map set verion list
     * @return the string
     * @throws Exception the exception
     */
    private static String exportMapSetRf2DeltaFile(final MapSet mapSet, final MapProject mapProject, final MapSetExportRequest mapSetExportRequest,
        final List<Map<String, String>> mapSetVerionList) throws Exception {

        final ExportHandler exporter = new ExportHandler();

        try {

            if (mapSet == null) {
                throw new Exception("Mapset Internal Id: " + mapSetExportRequest.getMapSetCode() + " does not exist in the MT2 database");
            }

            final String projectDir = PropertyUtility.getProperty("aws.project.base.dir");
            final String deltaSnowGeneratedFileName = exporter.generateMt2SnowVersionFileName(mapProject, mapSet, FileFormatType.DELTA,
                mapSetExportRequest.getTransientEffectiveTime(), mapSetExportRequest.getStartEffectiveTime());
            final String deltaAwsVersionedPath = exporter.generateAwsMt2BaseVersionPath(mapSet, mapSetExportRequest, projectDir, "");
            final String deltaMt2VersionFileName = exporter.generateMt2VersionFileName(mapProject, mapSet, mapSetExportRequest);

            // Check if delta file already exists
            if (!S3ConnectionWrapper.isInS3Cache(deltaAwsVersionedPath, deltaMt2VersionFileName)) {

                // determine all snapshot versions that will contribute to the delta
                final Map<String, String> versionToRefsetInternalId = new HashMap<>();
                final List<String> versionsInScope = new ArrayList<>();

                for (final Map<String, String> entry : mapSetVerionList) {
                    final String candidateVersion = entry.get("date");
                    if (candidateVersion != null && candidateVersion.replaceAll("-", "").compareTo(mapSetExportRequest.getStartEffectiveTime()) > 0
                        && candidateVersion.replaceAll("-", "").compareTo(mapSetExportRequest.getTransientEffectiveTime()) <= 0) {

                        versionsInScope.add(candidateVersion);
                        versionToRefsetInternalId.put(candidateVersion, entry.get("refsetInternalId"));
                    }
                }

                LOG.info("versionsInScope: {}", versionsInScope);

                // Local place to store snowBaseVersionFileName
                final Path localSnowGeneratedTempDir = Files.createTempDirectory("mt2LocalSnowGenerated-");

                // build fileContentsArray with contents from each snapshot version
                String headerLine = null;
                final List<String> fileContentsArray = new ArrayList<>();
                String transientEffectiveTime = mapSetExportRequest.getTransientEffectiveTime();

                for (final String versionInScope : versionsInScope) {

                    if (versionInScope == null) {
                        continue;
                    }

                    transientEffectiveTime = versionInScope.replaceAll("-", "");

                    // exporter.generateAwsBaseVersionPath(refset, "DELTA-SNAPSHOT", dates);
                    final String awsVersionedPath = exporter.generateAwsMt2BaseVersionPath(mapSet, mapSetExportRequest, projectDir, "DELTA-SNAPSHOT");

                    // NOT USED final String rt2VersionFileName = exporter.generateRt2VersionFileName(refset, "SNAPSHOT", languageId, dates, exportMetadata,
                    // withNames);

                    // Snowstorm generated RF2 file
                    // final String snowGeneratedFileName = exporter.generateSnowVersionFileName(refset, "SNAPSHOT", dates);
                    final String snowGeneratedFileName = exporter.generateMt2SnowVersionFileName(mapProject, mapSet, FileFormatType.SNAPSHOT,
                        mapSetExportRequest.getTransientEffectiveTime(), mapSetExportRequest.getStartEffectiveTime());

                    // Local Snowstorm generated Rf2 file name
                    final String localSnowGeneratedFilePath = localSnowGeneratedTempDir + File.separator + snowGeneratedFileName;

                    // Check if SnowS version file name does already exist in S3 cache
                    if (!S3ConnectionWrapper.isInS3Cache(awsVersionedPath, snowGeneratedFileName)) {

                        // Base-SnowVersion file is not on S3, so generate it, and after downloading it, store it on S3
                        final String entityString = "{\"refsetIds\": [\"" + mapSet.getRefSetCode() + "\"], \"branchPath\": \"" + mapSet.getBranchPath()
                            + "\", \"conceptsAndRelationshipsOnly\": false, \"filenameEffectiveDate\": \"" + transientEffectiveTime
                            + "\", \"legacyZipNaming\": false, \"type\": \"SNAPSHOT\", \"unpromotedChangesOnly\": false" + ", \"startEffectiveTime\": \""
                            + transientEffectiveTime + "\"" + ", \"transientEffectiveTime\": \"" + transientEffectiveTime + "\"" + "}";

                        LOG.info("generating file from snowstorm" + entityString);

                        // Generate on SnowS
                        final String snowGeneratedFileUrl = exporter.generateSnowVersionFile(entityString);
                        LOG.info("Downloading file from snowstorm, " + localSnowGeneratedFilePath);

                        // Download file from SnowS
                        exporter.downloadSnowGeneratedFile(snowGeneratedFileUrl, localSnowGeneratedFilePath);
                        LOG.info("uploading snowstorm genned file to S3");

                        // store file one s3
                        S3ConnectionWrapper.uploadToS3(awsVersionedPath, localSnowGeneratedTempDir.toString(), snowGeneratedFileName);

                    } else {

                        LOG.info("Downloading snowstorm genned file from S3, " + snowGeneratedFileName);
                        S3ConnectionWrapper.downloadFileFromS3(awsVersionedPath, snowGeneratedFileName, localSnowGeneratedFilePath);
                    }

                    // append the contents of this snapshot file to the fileContentsArray
                    FileUtility.unzip(localSnowGeneratedFilePath, localSnowGeneratedFilePath.replace(".zip", ""));
                    final String fileNamePath = localSnowGeneratedFilePath.replace(".zip", "") + File.separator + "SnomedCT_Export" + File.separator
                        + "Snapshot" + File.separator + "Refset" + File.separator + "Content" + File.separator;
                    final String[] files = new File(fileNamePath).list();

                    boolean withNames = mapSetExportRequest.isWithNames();
                    if (files != null) {

                        if (withNames) {

                            final String snowGeneratedMt2FilePath = fileNamePath + files[0];
                            final String builderRf2FilePath = fileNamePath + files[0] + ".names";

                            // final Refset specificRefset = service.findSingle("id:" + versionToRefsetInternalId.get(versionInScope), Refset.class, null);
                            // appendNamesToRf2(specificRefset, snowGeneratedMt2FilePath, builderRf2FilePath, mapSetExportRequest.getLanguageId());
                            appendNamesToRf2(mapProject.getEdition(), mapSetExportRequest.getBranch(), snowGeneratedMt2FilePath, builderRf2FilePath,
                                mapSetExportRequest.getLanguageId());

                            final File origFile = new File(snowGeneratedMt2FilePath);

                            if (origFile.exists()) {
                                origFile.delete();
                            }

                            final File namesFile = new File(builderRf2FilePath);
                            if (namesFile.exists()) {
                                namesFile.renameTo(origFile);
                            }
                            withNames = false;
                        }

                        fileContentsArray.addAll(FileUtility.readFileToArray(fileNamePath + files[0]));
                    }

                    LOG.info("fileContentsArray after versionInScope " + fileContentsArray.size() + " " + versionInScope);
                    LOG.info("fileContentsArray: " + fileContentsArray);

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

                // Processed all intermediate files - put in a set to remove duplicates from fileContents
                final Set<String> fileContentsSet = new HashSet<>(fileContentsArray);

                // sort fileContents
                final List<String> fileContentsArrayList = new ArrayList<>(fileContentsSet);
                Collections.sort(fileContentsArrayList);

                // if the files were empty then print out an empty file with just the header line
                if (headerLine == null) {
                    final String separator = "\t";
                    headerLine = "id" + separator + "effectiveTime" + separator + "active" + separator + "moduleId" + separator + "refsetId" + separator
                        + "referencedComponentId";
                }

                // write fileContents to file
                try (final FileOutputStream fos = new FileOutputStream(localSnowGeneratedTempDir.toString() + File.separator + deltaSnowGeneratedFileName);
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

                } catch (final IOException e) {
                    e.printStackTrace();
                }

                LOG.info("converting snowstorm generated file to RT2 format");
                // Have access to localSnowGeneratedFilePath from which rt2 will generate the export file
                // generateRt2ExportFile(refset, localSnowGeneratedTempDir.toString() + File.separator + deltaSnowGeneratedFileName, deltaRt2VersionFileName,
                // exportMetadata, withNames, languageId);
                generateMt2ExportFile(mapProject, mapSet, localSnowGeneratedTempDir.toString(), deltaMt2VersionFileName, mapSetExportRequest);

                LOG.info("uploading snowstorm generated file to S3");

                // store file on s3
                S3ConnectionWrapper.uploadToS3(deltaAwsVersionedPath, exportFileDir, deltaMt2VersionFileName);
                FileUtility.deleteDirectory(localSnowGeneratedTempDir.toFile());

            } else {

                final Path exportFilePath = Paths.get(exportFileDir, deltaMt2VersionFileName);
                if (!Files.exists(exportFilePath)) {
                    LOG.info("Downloading RT2 snapshot generated file from S3");
                    S3ConnectionWrapper.downloadFileFromS3(deltaAwsVersionedPath, deltaMt2VersionFileName, exportFilePath.toString());
                }
            }

            return deltaMt2VersionFileName;

        } catch (final Exception ex) {

            throw new Exception("Failed to export delta zip file name: " + ex.getMessage(), ex);
        }

    }

    /**
     * Generate MT2 export file.
     *
     * @param mapProject the map project
     * @param mapSet the map set
     * @param localSnowGeneratedFilePath the local snow generated file path
     * @param mt2VersionFileName the mt 2 version file name
     * @param mapSetExportRequest the map set export request
     * @return the string
     * @throws Exception the exception
     */
    private static String generateMt2ExportFile(final MapProject mapProject, final MapSet mapSet, final String localSnowGeneratedFilePath,
        final String mt2VersionFileName, final MapSetExportRequest mapSetExportRequest) throws Exception {

        // Generate the Rt2 version of refset RF2 Zip file
        final Path builderDirectoryTempDir = Files.createTempDirectory("mt2Builder-");

        LOG.info("creating builder temp dir: " + builderDirectoryTempDir.toString());

        // Unzip the download if snapshot
        final List<String> sourceFiles = new ArrayList<>();

        if (mapSetExportRequest.getFileFormatType() != FileFormatType.DELTA) {
            sourceFiles.addAll(FileUtility.unzipFiles(localSnowGeneratedFilePath, builderDirectoryTempDir.toString()));
        } else {
            sourceFiles.add(localSnowGeneratedFilePath);
        }

        LOG.info("unzipped source files: {}", ModelUtility.toJson(sourceFiles));

        if (sourceFiles.size() > 1) {
            throw new Exception("Unexpected number of files generated by Snowstorm Export MF2: " + sourceFiles.size());

        } else if (sourceFiles.isEmpty()) {

            final Path path = Path.of(builderDirectoryTempDir.toString() + File.separator + "noresults.txt");
            Files.write(path, ("No results for Map Set " + mapSetExportRequest.getMapSetCode()).getBytes(StandardCharsets.UTF_8));
            sourceFiles.add(path.toString());
        }

        // If Rf2WithNames selected, append the names to the mapset file
        // if there were no files from Snowstorm, there is no need to append names
        if (mapSetExportRequest.isWithNames() && !sourceFiles.isEmpty()) {

            final String snowGeneratedMt2FilePath = sourceFiles.iterator().next();
            final String rf2FileName = snowGeneratedMt2FilePath.substring(snowGeneratedMt2FilePath.lastIndexOf(File.separator) + 1);
            final String builderRf2FilePath = builderDirectoryTempDir.toString() + File.separator + rf2FileName;

            // appendNamesToRf2(refset, snowGeneratedRf2FilePath, builderRf2FilePath, mapSetExportRequest);
            appendNamesToRf2(mapProject.getEdition(), mapSetExportRequest.getBranch(), snowGeneratedMt2FilePath, builderRf2FilePath,
                mapSetExportRequest.getLanguageId());

            sourceFiles.clear();
            sourceFiles.add(builderRf2FilePath);
        }

        // remove effectiveTime
        // if ("PUBLISHED".equalsIgnoreCase(mapSet.getVersionStatus())) {
        //
        // final String snowGeneratedRf2FilePath = sourceFiles.iterator().next();
        // removeEffectiveTime(snowGeneratedRf2FilePath);
        // }

        // if exportMetadata requested, add it
        if (mapSetExportRequest.isExportMetadata()) {
            final String exportMapset = exportMapSetMetadata(mapProject, mapSetExportRequest.getBranch(), mapSet, mapSetExportRequest.getFileFormatType(),
                builderDirectoryTempDir.toString(), mapSetExportRequest.getTransientEffectiveTime());
            sourceFiles.add(exportMapset);
        }

        LOG.info("ready to be zipped source files: " + ModelUtility.toJson(sourceFiles));

        // zip the files together
        FileUtility.zipFiles(sourceFiles, Paths.get(exportFileDir, mt2VersionFileName));

        // Delete directory structure and original zip
        FileUtility.deleteDirectory(builderDirectoryTempDir.toFile());

        return exportFileDir;
    }

    /**
     * Export the refset metadata in a text format.
     *
     * @param mapProject the map project
     * @param branch the branch
     * @param mapset the mapset
     * @param type the type
     * @param directory the directory
     * @param transientEffectiveTime the transient effective time
     * @return the URL of the file containing the metadata
     * @throws Exception the exception
     */
    public static String exportMapSetMetadata(final MapProject mapProject, final String branch, final MapSet mapset, final FileFormatType type,
        final String directory, final String transientEffectiveTime) throws Exception {

        final StringBuilder fileLines = new StringBuilder();
        // final String pathDate = getRefsetAsOfDate(mapset);

        final String outputPath = "der2_iisssccRefset" + mapProject.getDestinationTerminology() + "ExtendedMap" + mapset.getRefSetCode()
            + StringUtility.capitalizeEachWord(mapProject.getEdition().getAbbreviation()) + "_" + transientEffectiveTime + "_metadata.txt";

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
            fileLines
                .append("MapSet Version Date" + separator + DateUtility.formatDate(mapset.getModified(), DateUtility.DATE_FORMAT_REVERSE, null) + LINE_FEED);
        } else {
            fileLines.append("MapSet Version Date" + separator + LINE_FEED);
        }

        fileLines
            .append("MapSet Last Modified Date" + separator + DateUtility.formatDate(mapset.getModified(), DateUtility.DATE_FORMAT_REVERSE, null) + LINE_FEED);
        fileLines.append("MapSet Type" + separator + type + LINE_FEED);

        if (mapset.isActive()) {
            fileLines.append("MapSet Status" + separator + "Active" + LINE_FEED);
        } else {
            fileLines.append("MapSet Status" + separator + "Inactive" + LINE_FEED);
        }

        if (StringUtils.isNotBlank(mapset.getRefSetName())) { // getNarrative
            fileLines.append("MapSet Narrative" + separator + mapset.getRefSetName() + LINE_FEED);
        }

        try {
            final Path path = Paths.get(outputPath);
            Files.write(path, fileLines.toString().getBytes(StandardCharsets.UTF_8));
            return outputPath;
        } catch (IOException ex) {
            throw new Exception("Could not create metadata mapset export txt file: " + ex.getMessage(), ex);
        }

    }

    /**
     * Export mapset sctid list.
     *
     * @param mapProject the map project
     * @param mappingExportRequest the mapping export request
     * @return the string
     * @throws Exception the exception
     */
    public static String exportMapSetSctIdList(final MapProject mapProject, final MapSetExportRequest mappingExportRequest) throws Exception {

        final MapSet mapset = SnowstormMapping.getMapSet(mappingExportRequest.getBranch(), mappingExportRequest.getMapSetCode());

        boolean hasMorePages = true;
        final Set<String> uniqueConceptIds = new HashSet<>();
        String zipOutputPath = exportFileDir;
        String mapSetFileName = "";
        String sctIdsFilePath = "";
        final List<String> sourceFiles = new ArrayList<>();
        Path tempDirectoryPath = null;

        try {

            if (mapset == null) {
                throw new Exception("Map Set Code: " + mappingExportRequest.getMapSetCode() + " does not exist in the MT2 database");
            }

            mapSetFileName = "mapset_" + mapset.getRefSetCode() + "_" + getRefsetAsOfDate(mapset) + "_member_ids.txt";
            zipOutputPath += (File.separator + mapSetFileName.replace(".txt", ".zip"));
            tempDirectoryPath = Files.createTempDirectory("sctidList-" + mapSetFileName.replace(".txt", ""));
            sctIdsFilePath = tempDirectoryPath.toString() + File.separator + mapSetFileName;

            LOG.info("sctId txt output path = {}; zip output path = {}", sctIdsFilePath, zipOutputPath);

            // if exportMetadata requested, add it
            if (mappingExportRequest.isExportMetadata()) {
                final String exportMapset = exportMapSetMetadata(mapProject, mappingExportRequest.getBranch(), mapset, mappingExportRequest.getFileFormatType(),
                    tempDirectoryPath.toString(), mappingExportRequest.getTransientEffectiveTime());
                sourceFiles.add(exportMapset);
            }

            String searchAfter = "";
            int totalRetured = 0;
            int total = 0;
            int itemCount = 0;

            while (hasMorePages) {

                final String resultString =
                    getMemberSctIds(mapset.getRefSetCode(), ELASTICSEARCH_MAX_RECORD_LENGTH, searchAfter, mappingExportRequest.getBranch());

                final ObjectMapper mapper = new ObjectMapper();
                final JsonNode root = mapper.readTree(resultString);
                final JsonNode items = root.get("items");

                if (items == null || items.isEmpty()) {
                    hasMorePages = false;
                    searchAfter = "";
                    continue;
                }

                final Iterator<JsonNode> iterator = items.iterator();

                itemCount = items.size();
                LOG.info("exportRefsetSctidList items.size(): {}", itemCount);
                totalRetured += itemCount;
                total = (root.get("total") != null) ? root.get("total").asInt() : 0;
                searchAfter = (root.get("searchAfter") != null) ? root.get("searchAfter").asText() : "";

                LOG.info("exportRefsetSctidList searchAfter: {}, total:{}, totalRetured:{}, itemCount:{}", searchAfter, total, totalRetured, itemCount);

                if (totalRetured >= total || StringUtils.isBlank(searchAfter)) {
                    hasMorePages = false;
                    searchAfter = "";
                }

                LOG.info("exportRefsetSctidList hasMorePages: " + hasMorePages);

                while (iterator.hasNext()) {
                    final JsonNode item = iterator.next();
                    final String conceptId = (item.get("referencedComponentId").asText());
                    uniqueConceptIds.add(conceptId);
                }
            }
        } catch (final Exception ex) {
            throw new Exception("Could not get Reference Set member data from snowstorm: " + ex.getMessage(), ex);
        }

        // Write the unique SCTIDs to the output file
        try {
            final Path path = Paths.get(sctIdsFilePath);
            Files.write(path, uniqueConceptIds, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new Exception("Could not create export txt file: " + ex.getMessage(), ex);
        }

        // zip the files together
        sourceFiles.add(sctIdsFilePath);
        FileUtility.zipFiles(sourceFiles, zipOutputPath);

        // Delete temp directory structure and files
        FileUtility.deleteDirectory(tempDirectoryPath.toFile());

        // if download is from RT2 server
        final String zippedFileUrl = mapSetFileName.replace(".txt", ".zip");

        return zippedFileUrl;
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
    private static String getMemberSctIds(final String refsetId, final int limit, final String searchAfter, final String branchPath) throws Exception {

        final String url = SnowstormConnection.getBaseUrl() + branchPath + "/members?referenceSet=" + refsetId
            + "&sortField=memberId&active=true&offset=0&limit=" + limit + "&searchAfter=" + searchAfter;

        LOG.info("Snowstorm getMemberSctIds URL: " + url);

        try (final Response response = SnowstormConnection.getResponse(url)) {
            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                throw new Exception(
                    "Call to URL '" + url + "' wasn't successful. Status: " + response.getStatus() + " Message: " + formatErrorMessage(response));
            }

            final String resultString = response.readEntity(String.class);
            return resultString;

        } catch (final Exception ex) {

            throw new Exception("Could not retrieve Reference Set members from snowstorm: " + ex.getMessage(), ex);
        }

    }

    /**
     * Append names to rf 2.
     *
     * @param edition the edition
     * @param branchPath the branch path
     * @param origFilePath the orig file path
     * @param newFileWithNamesPath the new file with names path
     * @param languageId the language id
     * @throws Exception the exception
     */
    private static void appendNamesToRf2(final Edition edition, final String branchPath, final String origFilePath, final String newFileWithNamesPath,
        final String languageId) throws Exception {

        LOG.info("Appending descriptions to RF2 file");

        // First pass: collect all unique concept IDs
        final Set<String> uniqueConceptIds = new HashSet<>();
        try (final BufferedReader br = new BufferedReader(new FileReader(new File(origFilePath)))) {
            // Skip header
            br.readLine();

            String line;
            while ((line = br.readLine()) != null && !line.trim().isEmpty()) {
                final String conceptId = line.split("\t")[REFEST_RF2_CONCEPTID_COLUMN];
                uniqueConceptIds.add(conceptId);
            }
        }

        // Split concept IDs into batches of 230
        final List<Set<String>> conceptBatches = new ArrayList<>();
        final Set<String> currentBatch = new HashSet<>();
        for (String conceptId : uniqueConceptIds) {
            currentBatch.add(conceptId);
            if (currentBatch.size() >= CONCEPT_DESCRIPTIONS_PER_CALL) {
                conceptBatches.add(new HashSet<>(currentBatch));
                currentBatch.clear();
            }
        }
        if (!currentBatch.isEmpty()) {
            conceptBatches.add(currentBatch);
        }

        // Create a thread-safe map to store results
        final Map<String, Concept> members = Collections.synchronizedMap(new HashMap<>());

        // Use a bounded thread pool with a reasonable size
        final int executorSize = 6;
        LOG.info("Using executor size: {}", executorSize);
        final ExecutorService executor = Executors.newFixedThreadPool(executorSize);
        final List<Future<?>> futures = new ArrayList<>();

        final long startTime = System.currentTimeMillis();
        int batchCount = 0;

        // Submit tasks for each batch
        for (final Set<String> batch : conceptBatches) {
            if (batch.isEmpty()) {
                continue;
            }
            batchCount++;
            final int currentBatchNumber = batchCount;
            futures.add(executor.submit(() -> {
                try {
                    LOG.debug("Processing batch {} of {} with {} concepts", currentBatchNumber, conceptBatches.size(), batch.size());
                    final List<Concept> concepts = new ArrayList<>();
                    for (final String conceptId : batch) {
                        final Concept concept = new Concept();
                        concept.setCode(conceptId);
                        concepts.add(concept);
                    }

                    SnowstormDescription.populateAllLanguageDescriptions(edition, branchPath, concepts);

                    // Store results in thread-safe map
                    for (final Concept concept : concepts) {
                        members.put(concept.getCode(), concept);
                    }
                    LOG.debug("Completed batch {} of {}", currentBatchNumber, conceptBatches.size());
                } catch (Exception e) {
                    LOG.error("Error processing batch {} of {}: {}", currentBatchNumber, conceptBatches.size(), e.getMessage(), e);
                    throw new RuntimeException("Failed to process batch " + currentBatchNumber, e);
                }
            }));
        }

        // Wait for all tasks to complete
        for (final Future<?> future : futures) {
            try {
                future.get(5, TimeUnit.MINUTES); // Add timeout to prevent hanging
            } catch (Exception e) {
                LOG.error("Error waiting for task completion: {}", e.getMessage(), e);
                executor.shutdownNow(); // Force shutdown on error
                throw new RuntimeException("Failed to complete all batches", e);
            }
        }

        executor.shutdown();
        if (!executor.awaitTermination(1, TimeUnit.MINUTES)) {
            LOG.warn("Executor did not terminate within timeout");
            executor.shutdownNow();
        }

        final long endTime = System.currentTimeMillis();
        LOG.info("Processing completed in {} ms for {} batches", (endTime - startTime), batchCount);

        // Write the output file with the collected results
        try (final FileWriter fw = new FileWriter(new File(newFileWithNamesPath));
            final BufferedReader br = new BufferedReader(new FileReader(new File(origFilePath)))) {

            // Write header
            String headerLine = br.readLine();
            for (final Map<String, String> defaultLanguages : edition.getFullyQualifiedLanguageRefsets()) {
                if (languageId.equals(defaultLanguages.get("qualifiedLanguageRefset"))) {
                    fw.write(headerLine + "\t" + defaultLanguages.get("qualifiedLanguageCode") + "\n");
                }
            }

            // Write data rows
            String line;
            while ((line = br.readLine()) != null) {
                final String conceptId = line.split("\t")[REFEST_RF2_CONCEPTID_COLUMN];
                Concept concept = members.get(conceptId);

                if (concept == null) {
                    throw new Exception("Didn't have concept populated with descriptions yet");
                }

                boolean matchingDescriptionFound = false;
                String fallbackDescription = null;

                for (Map<String, String> description : concept.getDescriptions()) {
                    if (description == null || !languageId.equals(description.get(LANGUAGE_ID))) {
                        if (description != null && description.get(LANGUAGE_ID).equals(DEFAULT_LANGUAGE_REFSET_US)) {
                            fallbackDescription = line + "\t" + description.get(DESCRIPTION_TERM);
                        }
                        continue;
                    }

                    fw.write(line + "\t" + description.get(DESCRIPTION_TERM));
                    matchingDescriptionFound = true;
                    break;
                }

                if (!matchingDescriptionFound && fallbackDescription != null) {
                    fw.write(fallbackDescription);
                } else if (!matchingDescriptionFound) {
                    fw.write(line);
                }

                fw.write("\n");
            }
        }
    }

    /**
     * Get either the version date or the current date in yyyy-MM-dd format.
     *
     * @param mapset the mapset
     * @return the URL of the file containing the metadata
     * @throws Exception the exception
     */
    private static String getRefsetAsOfDate(final MapSet mapset) throws Exception {

        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd");
        return mapset.getModified() != null ? simpleDateFormat.format(mapset.getModified()) : simpleDateFormat.format(new Date());
    }

}
