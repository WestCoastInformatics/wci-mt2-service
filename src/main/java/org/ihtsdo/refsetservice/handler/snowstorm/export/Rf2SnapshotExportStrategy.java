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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
 * Strategy for exporting RF2 snapshot files.
 */
public class Rf2SnapshotExportStrategy extends AbstractMapSetExportStrategy {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(Rf2SnapshotExportStrategy.class);

    /** The export file dir. */
    private static String exportFileDir;

    /** The Constant FILE_SEPARATOR. */
    private static final String FILE_SEPARATOR = System.getProperty("file.separator");

    static {
        exportFileDir = PropertyUtility.getProperty("mapexport.fileDir");
    }

    /**
     * Instantiates a new RF2 snapshot export strategy.
     *
     * @param exportHandler the export handler
     */
    public Rf2SnapshotExportStrategy(final ExportHandler exportHandler) {

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

        final String mt2VersionFileName = generateMt2VersionFileName(mapProject, mapSet, request);
        final String projectDir = PropertyUtility.getProperty("aws.project.base.dir");
        final String awsVersionedPath = generateAwsVersionedPath(mapSet, request, projectDir);

        // Check if file already exists
        if (!S3ConnectionWrapper.isInS3Cache(awsVersionedPath, mt2VersionFileName)) {
            // Mt2 Version File doesn't reside on s3

            // Snowstorm generated RF2 file
            final String snowGeneratedFileName = generateSnowstormFileName(mapProject, mapSet, request.getFileFormatType(), request.getTransientEffectiveTime(),
                request.getStartEffectiveTime());

            // Local place to store snowBaseVersionFileName
            final Path localSnowGeneratedTempDir = Files.createTempDirectory("mt2LocalSnowGenerated-");

            // Local Snowstorm generated Rf2 file name
            final String localSnowGeneratedFilePath = localSnowGeneratedTempDir + FILE_SEPARATOR + snowGeneratedFileName;

            // Check if SnowStorm version file name does already exist in S3 Cache
            if (!S3ConnectionWrapper.isInS3Cache(awsVersionedPath, snowGeneratedFileName)) {

                final Set<String> moduleIds = new HashSet<>();
                if (mapProject.getModuleId() != null) {
                    moduleIds.add(mapProject.getModuleId());
                }
                moduleIds.add(SnomedConstants.SNOMEDCT_TO_ICD10_MAPPING_MODULE);

                // Base-SnowVersion file is not on S3, so generate it, and after downloading it, store it on S3
                final ExportRequestBuilder builder =
                    new ExportRequestBuilder().withRefsetId(mapSet.getRefSetCode()).withBranchPath(request.getBranch()).withConceptsAndRelationshipsOnly(false)
                        .withFilenameEffectiveDate(request.getFileNameDate()).withLegacyZipNaming(false).withType(request.getFileFormatType())
                        .withUnpromotedChangesOnly(false).withModuleIds(moduleIds).withTransientEffectiveTime(request.getTransientEffectiveTime())
                        .withStartEffectiveTime(request.getStartEffectiveTime()).withStartExport(false);
                final String exportRequestParameters = builder.build();

                LOG.info("Snowstorm export request: {}", exportRequestParameters);

                // Generate on Snowstorm
                final String snowGeneratedFileUrl = exportHandler.generateSnowVersionFile(exportRequestParameters);

                LOG.info("Downloading file from snowstorm : {}", snowGeneratedFileUrl);

                // Download file from Snowstorm
                exportHandler.downloadSnowGeneratedFile(snowGeneratedFileUrl, localSnowGeneratedFilePath);

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
            generateMt2ExportFile(mapProject, mapSet, localSnowGeneratedFilePath, mt2VersionFileName, request);

            S3ConnectionWrapper.uploadToS3(awsVersionedPath, exportFileDir, mt2VersionFileName);

            FileUtility.deleteDirectory(localSnowGeneratedTempDir.toFile());

        } else {

            final Path exportFilePath = Paths.get(exportFileDir, mt2VersionFileName);

            if (!Files.exists(exportFilePath)) {

                LOG.info("Downloading RT2 genned file from S3");
                S3ConnectionWrapper.downloadFileFromS3(awsVersionedPath, mt2VersionFileName, exportFilePath.toString());
            }
        }

        LOG.info("Final Export File Path: {}", Paths.get(exportFileDir, mt2VersionFileName));
        return FILE_DOWNLOAD_URL + mt2VersionFileName;
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

        // Generate the Rt2 version of refset RF2 Zip file
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

        }
        if (sourceFiles.isEmpty()) {

            final Path path = Path.of(builderDirectoryTempDir.toString(), "noresults.txt");
            Files.write(path, ("No results for Map Set " + mapSetExportRequest.getMapSetCode()).getBytes());
            sourceFiles.add(path.toString());
        }

        // If Rf2WithNames selected, append the names to the mapset file
        // if there were no files from Snowstorm, there is no need to append names
        if (mapSetExportRequest.isWithNames() && !sourceFiles.isEmpty()) {

            final String snowGeneratedMt2FilePath = sourceFiles.iterator().next();
            final String rf2FileName = snowGeneratedMt2FilePath.substring(snowGeneratedMt2FilePath.lastIndexOf(File.separator) + 1);
            final String builderRf2FilePath = Path.of(builderDirectoryTempDir.toString(), rf2FileName).toString();

            final DescriptionAppender descriptionAppender = new DescriptionAppender();
            // Edition edition, String branchPath, String origFilePath, String newFileWithNamesPath, String languageId
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
