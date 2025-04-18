/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.handler.snowstorm.export;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ihtsdo.refsetservice.handler.ExportHandler;
import org.ihtsdo.refsetservice.handler.snowstorm.SnomedConstants;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapSetExportRequest;
import org.ihtsdo.refsetservice.model.enums.FileFormatType;
import org.ihtsdo.refsetservice.terminologyservice.S3ConnectionWrapper;
import org.ihtsdo.refsetservice.util.FileUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Strategy for exporting RF2 delta files.
 */
public class Rf2DeltaExportStrategy extends AbstractMapSetExportStrategy {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(Rf2DeltaExportStrategy.class);

    /** The export file dir. */
    private static String exportFileDir;

    static {
        exportFileDir = PropertyUtility.getProperty("mapexport.fileDir");
    }

    /**
     * Instantiates a new RF2 delta export strategy.
     *
     * @param exportHandler the export handler
     */
    public Rf2DeltaExportStrategy(final ExportHandler exportHandler) {

        super(exportHandler);
    }

    /**
     * Export.
     *
     * @param mapSet the map set
     * @param mapProject the map project
     * @param request the request
     * @return the string
     * @throws Exception the exception
     */
    @Override
    public String export(final MapSet mapSet, final MapProject mapProject, final MapSetExportRequest request) throws Exception {

        validateMapSet(mapSet, request);

        // TODO: add back when delta is completed. There are a number of things to resolve around versions.
        final List<Map<String, String>> mapSetVersionList = new ArrayList<>();

        mapSetVersionList.add(Map.of("date", "2023-11-15"));
        mapSetVersionList.add(Map.of("date", "2023-12-15"));
        mapSetVersionList.add(Map.of("date", "2023-12-20"));
        mapSetVersionList.add(Map.of("date", "2024-01-01"));
        mapSetVersionList.add(Map.of("date", "2024-01-15"));
        mapSetVersionList.add(Map.of("date", "2024-03-15"));
        mapSetVersionList.add(Map.of("date", "2024-04-15"));

        try {
            if (mapSet == null) {
                throw new MapSetExportException("Mapset Internal Id: " + request.getMapSetCode() + " does not exist in the MT2 database");
            }

            final String projectDir = PropertyUtility.getProperty("aws.project.base.dir");
            final String deltaSnowGeneratedFileName = exportHandler.generateMt2SnowVersionFileName(mapProject, mapSet, FileFormatType.DELTA,
                request.getTransientEffectiveTime(), request.getStartEffectiveTime());
            final String deltaAwsVersionedPath = exportHandler.generateAwsMt2BaseVersionPath(mapSet, request, projectDir, null);
            final String deltaMt2VersionFileName = exportHandler.generateMt2VersionFileName(mapProject, mapSet, request);

            // Check if delta file already exists
            if (!S3ConnectionWrapper.isInS3Cache(deltaAwsVersionedPath, deltaMt2VersionFileName)) {

                // determine all snapshot versions that will contribute to the delta
                final Map<String, String> versionToRefsetInternalId = new HashMap<>();
                final List<String> versionsInScope = new ArrayList<>();

                for (final Map<String, String> entry : mapSetVersionList) {
                    final String candidateVersion = entry.get("date");
                    if (candidateVersion != null && candidateVersion.replace("-", "").compareTo(request.getStartEffectiveTime()) > 0
                        && candidateVersion.replace("-", "").compareTo(request.getTransientEffectiveTime()) <= 0) {

                        versionsInScope.add(candidateVersion);
                        versionToRefsetInternalId.put(candidateVersion, entry.get("refsetInternalId"));
                    }
                }

                LOG.info("versionsInScope: {}", versionsInScope);

                final Set<String> moduleIds = new HashSet<>();
                moduleIds.add(mapProject.getModuleId());
                moduleIds.add(SnomedConstants.SNOMEDCT_TO_ICD10_MAPPING_MODULE);

                // Local place to store snowBaseVersionFileName
                final Path localSnowGeneratedTempDir = Files.createTempDirectory("mt2LocalSnowGenerated-");

                // build fileContentsArray with contents from each snapshot version
                String headerLine = null;
                final List<String> fileContentsArray = new ArrayList<>();
                String transientEffectiveTime = request.getTransientEffectiveTime();

                for (final String versionInScope : versionsInScope) {

                    if (versionInScope == null) {
                        continue;
                    }

                    transientEffectiveTime = versionInScope.replace("-", "");

                    final String awsVersionedPath = exportHandler.generateAwsMt2BaseVersionPath(mapSet, request, projectDir, "DELTA-SNAPSHOT");

                    // Snowstorm generated RF2 file
                    final String snowGeneratedFileName = exportHandler.generateMt2SnowVersionFileName(mapProject, mapSet, FileFormatType.SNAPSHOT,
                        request.getStartEffectiveTime(), request.getTransientEffectiveTime());

                    // Local Snowstorm generated Rf2 file name
                    final String localSnowGeneratedFilePath = localSnowGeneratedTempDir + File.separator + snowGeneratedFileName;

                    // Check if SnowS version file name does already exist in S3 cache
                    if (!S3ConnectionWrapper.isInS3Cache(awsVersionedPath, snowGeneratedFileName)) {

                        // Base-SnowVersion file is not on S3, so generate it, and after downloading it, store it on S3
                        // final ExportRequestBuilder builder = new ExportRequestBuilder().withRefsetId(mapSet.getRefSetCode())
                        // .withBranchPath(mapSet.getBranchPath()).withConceptsAndRelationshipsOnly(false).withFilenameEffectiveDate(transientEffectiveTime)
                        // .withLegacyZipNaming(false).withType(FileFormatType.SNAPSHOT).withUnpromotedChangesOnly(false).withModuleIds(moduleIds)
                        // .withStartEffectiveTime(transientEffectiveTime).withTransientEffectiveTime(transientEffectiveTime);
                        final ExportRequestBuilder builder = new ExportRequestBuilder().withRefsetId(mapSet.getRefSetCode())
                            .withBranchPath(mapSet.getBranchPath()).withConceptsAndRelationshipsOnly(false).withFilenameEffectiveDate(transientEffectiveTime)
                            .withLegacyZipNaming(false).withType(FileFormatType.SNAPSHOT).withUnpromotedChangesOnly(false).withModuleIds(moduleIds)
                            .withStartEffectiveTime(request.getStartEffectiveTime()).withTransientEffectiveTime(transientEffectiveTime);

                        final String exportRequestParameters = builder.build();

                        LOG.info("generating file from snowstorm with: {}", exportRequestParameters);

                        // Generate on SnowS
                        final String snowGeneratedFileUrl = exportHandler.generateSnowVersionFile(exportRequestParameters);
                        LOG.info("Downloading file from snowstorm: {}", localSnowGeneratedFilePath);

                        // Download file from SnowS
                        exportHandler.downloadSnowGeneratedFile(snowGeneratedFileUrl, localSnowGeneratedFilePath);
                        LOG.info("uploading snowstorm generated file to S3");

                        // store file on s3
                        S3ConnectionWrapper.uploadToS3(awsVersionedPath, localSnowGeneratedTempDir.toString(), snowGeneratedFileName);

                    } else {

                        LOG.info("Downloading snowstorm generated file from S3: {}", snowGeneratedFileName);
                        S3ConnectionWrapper.downloadFileFromS3(awsVersionedPath, snowGeneratedFileName, localSnowGeneratedFilePath);
                    }

                    // append the contents of this snapshot file to the fileContentsArray
                    FileUtility.unzip(localSnowGeneratedFilePath, localSnowGeneratedFilePath.replace(".zip", ""));
                    final String fileNamePath = localSnowGeneratedFilePath.replace(".zip", "") + File.separator + "SnomedCT_Export" + File.separator
                        + "Snapshot" + File.separator + "Refset" + File.separator + "Map" + File.separator;
                    final String[] files = new File(fileNamePath).list();

                    boolean withNames = request.isWithNames();
                    if (files != null) {

                        if (withNames) {
                            final String snowGeneratedMt2FilePath = fileNamePath + files[0];
                            final String builderRf2FilePath = fileNamePath + files[0] + ".names";

                            // Append names to the RF2 file
                            final DescriptionAppender descriptionAppender = new DescriptionAppender();
                            descriptionAppender.appendNamesToRf2(mapProject.getEdition(), request.getBranch(), snowGeneratedMt2FilePath, builderRf2FilePath,
                                request.getLanguageId());

                            final File origFile = new File(snowGeneratedMt2FilePath);
                            Files.deleteIfExists(localSnowGeneratedTempDir);

                            final File namesFile = new File(builderRf2FilePath);
                            if (namesFile.exists()) {
                                namesFile.renameTo(origFile);
                            }
                            withNames = false;
                        }

                        fileContentsArray.addAll(FileUtility.readFileToArray(fileNamePath + files[0]));
                    }

                    LOG.info("fileContentsArray after versionInScope {} has size {}", versionInScope, fileContentsArray.size());

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
                try (final FileOutputStream fos = new FileOutputStream(Paths.get(localSnowGeneratedTempDir.toString(), deltaSnowGeneratedFileName).toFile());
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
                    throw new MapSetExportException("Failed to write fileContents to file", e);
                }

                LOG.info("converting snowstorm generated file to MT2 format");
                // Have access to localSnowGeneratedFilePath from which MT2 will generate the export file
                generateMt2ExportFile(mapProject, mapSet, Paths.get(localSnowGeneratedTempDir.toString(), deltaSnowGeneratedFileName).toString(),
                    deltaMt2VersionFileName, request);

                LOG.info("uploading snowstorm generated file to S3");

                // store file on s3
                S3ConnectionWrapper.uploadToS3(deltaAwsVersionedPath, exportFileDir, deltaMt2VersionFileName);
                FileUtility.deleteDirectory(localSnowGeneratedTempDir.toFile());

            } else {

                final Path exportFilePath = Paths.get(exportFileDir, deltaMt2VersionFileName);
                if (!Files.exists(exportFilePath)) {
                    LOG.info("Downloading MT2 delta generated file from S3");
                    S3ConnectionWrapper.downloadFileFromS3(deltaAwsVersionedPath, deltaMt2VersionFileName, exportFilePath.toString());
                }
            }

            return FILE_DOWNLOAD_URL + deltaMt2VersionFileName;

        } catch (final Exception ex) {
            throw new MapSetExportException("Failed to export delta zip file name: " + ex.getMessage(), ex);
        }
    }

    /**
     * Generate MT2 export file.
     *
     * @param mapProject the map project
     * @param mapSet the map set
     * @param localSnowGeneratedFilePath the local snow generated file path
     * @param mt2ZipFileName the MT2 file name for zip file
     * @param mapSetExportRequest the map set export request
     * @return the string
     * @throws Exception the exception
     */
    private String generateMt2ExportFile(final MapProject mapProject, final MapSet mapSet, final String localSnowGeneratedFilePath, final String mt2ZipFileName,
        final MapSetExportRequest mapSetExportRequest) throws Exception {

        // Generate the MT2 version of refset RF2 Zip file
        final Path builderDirectoryTempDir = Files.createTempDirectory("mt2Builder-");

        LOG.info("creating builder temp dir: {}", builderDirectoryTempDir);

        // Unzip the download if snapshot
        final List<String> sourceFiles = new ArrayList<>();

        if (mapSetExportRequest.getFileFormatType() != FileFormatType.DELTA) {
            sourceFiles.addAll(FileUtility.unzipFiles(localSnowGeneratedFilePath, builderDirectoryTempDir.toString()));
        } else {
            sourceFiles.add(localSnowGeneratedFilePath);
        }

        LOG.info("unzipped source files: {}", sourceFiles);

        if (sourceFiles.size() > 1) {
            throw new MapSetExportException("Unexpected number of files generated by Snowstorm Export MF2: " + sourceFiles.size());

        } else if (sourceFiles.isEmpty()) {

            final Path path = Path.of(builderDirectoryTempDir.toString(), "noresults.txt");
            Files.write(path, ("No results for Map Set " + mapSetExportRequest.getMapSetCode()).getBytes(StandardCharsets.UTF_8));
            sourceFiles.add(path.toString());
        }

        // If Rf2WithNames selected, append the names to the mapset file
        // if there were no files from Snowstorm, there is no need to append names
        if (mapSetExportRequest.isWithNames() && !sourceFiles.isEmpty()) {

            final String snowGeneratedMt2FilePath = sourceFiles.iterator().next();
            final String rf2FileName = snowGeneratedMt2FilePath.substring(snowGeneratedMt2FilePath.lastIndexOf(File.separator) + 1);
            final String builderRf2FilePath = Path.of(builderDirectoryTempDir.toString(), rf2FileName).toString();

            final DescriptionAppender descriptionAppender = new DescriptionAppender();
            descriptionAppender.appendNamesToRf2(mapProject.getEdition(), mapSetExportRequest.getBranch(), snowGeneratedMt2FilePath, builderRf2FilePath,
                mapSetExportRequest.getLanguageId());
            sourceFiles.clear();
            sourceFiles.add(builderRf2FilePath);
        }

        // if exportMetadata requested, add it
        if (mapSetExportRequest.isExportMetadata()) {
            final MapSetMetadataExporter metadataExporter = new MapSetMetadataExporter();
            final String exportMapset = metadataExporter.exportMapSetMetadata(mapProject, mapSetExportRequest.getBranch(), mapSet,
                mapSetExportRequest.getFileFormatType(), builderDirectoryTempDir.toString(), mapSetExportRequest.getTransientEffectiveTime());
            sourceFiles.add(exportMapset);
        }

        LOG.info("ready to be zipped source files: {}", sourceFiles);

        // zip the files together
        FileUtility.zipFiles(sourceFiles, Paths.get(exportFileDir, mt2ZipFileName));

        // Delete directory structure and original zip
        FileUtility.deleteDirectory(builderDirectoryTempDir.toFile());

        return exportFileDir;
    }
}