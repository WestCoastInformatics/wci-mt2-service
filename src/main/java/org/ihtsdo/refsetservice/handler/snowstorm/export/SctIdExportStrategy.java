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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
 * Strategy for exporting SCTID list files. Note: This strategy is dependent on the RF2 snapshot file. If the snapshot file does not exist in S3, it must be
 * generated first before SCTIDs can be extracted.
 */
public class SctIdExportStrategy extends AbstractMapSetExportStrategy {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SctIdExportStrategy.class);

    /**
     * Instantiates a new SCTID export strategy.
     *
     * @param exportHandler the export handler
     */
    public SctIdExportStrategy(final ExportHandler exportHandler) {

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

        this.validateMapSet(mapSet, request);

        final String sctIdsFileName = "mapset_" + mapSet.getRefSetCode() + "_" + getRefsetAsOfDate(mapSet) + "_member_ids.txt";
        final String sctIdsZipFileName = "mapset_" + mapSet.getRefSetCode() + "_" + getRefsetAsOfDate(mapSet) + "_member_ids.zip";
        final String projectDir = PropertyUtility.getProperty("aws.project.base.dir");
        final String awsVersionedPath = this.generateAwsVersionedPath(mapSet, request, projectDir);
        // Path for temp directory will be created only if needed
        Path builderDirectoryTempDir = null;

        try {
            // 1. Check S3 for _member_ids.zip (sctIdsZipFileName) using isInS3Cache
            LOG.info("Checking S3 for existing SCTID zip file: {} in {}", sctIdsZipFileName, awsVersionedPath);
            if (S3ConnectionWrapper.isInS3Cache(awsVersionedPath, sctIdsZipFileName)) {
                // 2. If found, download to export dir so download endpoint can serve it, then return
                final String exportFileDir = PropertyUtility.getProperty("mapexport.fileDir");
                final String localPath = Paths.get(exportFileDir, sctIdsZipFileName).toString();
                S3ConnectionWrapper.downloadFileFromS3(awsVersionedPath, sctIdsZipFileName, localPath);
                LOG.info("SCTID zip file found in S3, downloaded to: {}", localPath);
                return FILE_DOWNLOAD_URL + sctIdsZipFileName;
            }

            // If not found, create the temp directory
            builderDirectoryTempDir = FileUtility.createTempDirectory("mt2SctIdExport-");
            final String localSctIdsZipPath = Paths.get(builderDirectoryTempDir.toString()).toString();

            // 3. If not found, check S3 for RF2 snapshot file
            LOG.info("SCTID zip file not found. Checking for RF2 snapshot file.");
            final String snapshotFileName = this.generateSnowstormFileName(mapProject, mapSet, FileFormatType.SNAPSHOT, request.getTransientEffectiveTime(),
                request.getStartEffectiveTime());
            final String localSnapshotFilePath = Paths.get(builderDirectoryTempDir.toString(), snapshotFileName).toString();

            // Check if snapshot file exists in S3 and download if it does using inherited method
            if (S3ConnectionWrapper.checkAndDownloadFromS3(awsVersionedPath, snapshotFileName, localSnapshotFilePath)) {

                LOG.info("RF2 snapshot file found in S3 and downloaded to: {}", localSnapshotFilePath);

            } else {
                // 4. If RF2 snapshot file not found, generate it
                LOG.info("RF2 snapshot file not found in S3, generating it first...");

                final Set<String> moduleIds = new HashSet<>();
                if (mapProject.getModuleId() != null) {
                    moduleIds.add(mapProject.getModuleId());
                }
                moduleIds.add(SnomedConstants.SNOMEDCT_TO_ICD10_MAPPING_MODULE);

                final ExportRequestBuilder builder =
                    new ExportRequestBuilder().withRefsetId(mapSet.getRefSetCode()).withBranchPath(request.getBranch()).withConceptsAndRelationshipsOnly(false)
                        .withFilenameEffectiveDate(request.getFileNameDate()).withLegacyZipNaming(false).withType(FileFormatType.SNAPSHOT)
                        .withUnpromotedChangesOnly(false).withModuleIds(moduleIds).withTransientEffectiveTime(request.getTransientEffectiveTime())
                        .withStartEffectiveTime(request.getStartEffectiveTime()).withStartExport(false);
                final String exportRequestParameters = builder.build();

                LOG.info("Generating RF2 snapshot file: {}", exportRequestParameters);

                // Generate on Snowstorm
                final String snowGeneratedFileUrl = this.exportHandler.generateSnowVersionFile(exportRequestParameters);
                LOG.info("Downloading snapshot file from Snowstorm: {}", snowGeneratedFileUrl);

                // Download file from Snowstorm to the temporary directory
                this.exportHandler.downloadSnowGeneratedFile(snowGeneratedFileUrl, localSnapshotFilePath);

                // Store file on S3 using inherited method
                LOG.info("Uploading snapshot file to S3: {} to {}", snapshotFileName, awsVersionedPath);
                S3ConnectionWrapper.uploadToS3(awsVersionedPath, builderDirectoryTempDir.toString(), snapshotFileName);

            }

            // 5. Unzip the RF2 snapshot file
            LOG.info("Unzipping snapshot file: {}", localSnapshotFilePath);
            final List<String> unzippedFiles = FileUtility.unzipFiles(localSnapshotFilePath, builderDirectoryTempDir.toString());
            if (unzippedFiles.isEmpty()) {
                throw new MapSetExportException("No files found in the snapshot zip file: " + localSnapshotFilePath);
            }
            // Find the actual ExtendedMap data file within the unzipped archive
            final String unzippedSnapshotDataFile =
                unzippedFiles.stream().filter(f -> f.matches(".*ExtendedMap.*\\.txt")).findFirst().orElseThrow(() -> new MapSetExportException(
                    "Could not find ExtendedMap Refset data file (.*ExtendedMap.*\\.txt) in snapshot archive: " + localSnapshotFilePath));
            LOG.info("Found snapshot data file: {}", unzippedSnapshotDataFile);

            // 6. Extract the SCTIDs from the unzipped file
            final String localSctIdsFilePath = Paths.get(builderDirectoryTempDir.toString(), sctIdsFileName).toString();
            LOG.info("Extracting SCTIDs from snapshot data file: {} to {}", unzippedSnapshotDataFile, localSctIdsFilePath);
            getMemberSctIds(Paths.get(unzippedSnapshotDataFile), Paths.get(localSctIdsFilePath));

            // 7. Zip the SCTID file
            LOG.info("Zipping SCTID file: {} into {}", sctIdsFileName, localSctIdsZipPath);
            final List<String> filesToZip = new ArrayList<>();
            filesToZip.add(localSctIdsFilePath);
            FileUtility.zipFiles(filesToZip, Paths.get(localSctIdsZipPath, sctIdsZipFileName));

            // 8. Upload the zipped SCTID file to S3
            LOG.info("Uploading zipped SCTID file to S3: {} to {}", sctIdsZipFileName, awsVersionedPath);
            S3ConnectionWrapper.uploadToS3(awsVersionedPath, localSctIdsZipPath, sctIdsZipFileName);

            // 9. Copy zip to export file dir so download endpoint can serve it
            final String exportFileDir = PropertyUtility.getProperty("mapexport.fileDir");
            final Path sourceZip = Paths.get(localSctIdsZipPath, sctIdsZipFileName);
            final Path targetZip = Paths.get(exportFileDir, sctIdsZipFileName);
            Files.copy(sourceZip, targetZip, StandardCopyOption.REPLACE_EXISTING);
            LOG.info("Copied SCTID zip to export dir for download: {}", targetZip);

            // 10. Return the zipped SCTID file name
            LOG.info("SCTID export successful. Returning file name: {}", sctIdsZipFileName);
            return FILE_DOWNLOAD_URL + sctIdsZipFileName;

        } finally {
            // Clean up temporary files regardless of success or failure, only if temp dir was created
            if (builderDirectoryTempDir != null) {
                LOG.debug("Cleaning up temporary directory: {}", builderDirectoryTempDir);
                try {
                    FileUtility.cleanupTempFiles(builderDirectoryTempDir);
                } catch (final Exception e) {
                    LOG.error("Error cleaning up temporary files: {}", e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Get member SCT IDs.
     *
     * @param origFilePath the original file path
     * @param newFilePath the new file path
     * @return the member sct ids
     * @throws Exception the exception
     */
    private void getMemberSctIds(final Path origFilePath, final Path newFilePath) throws Exception {

        final Set<String> uniqueConceptIds = new HashSet<>();

        try (final BufferedReader br = new BufferedReader(new FileReader(origFilePath.toFile()))) {
            // Skip header
            String line = br.readLine();
            while ((line = br.readLine()) != null && !line.trim().isEmpty()) {
                final String conceptId = line.split("\t")[SnomedConstants.REFEST_RF2_CONCEPTID_COLUMN];
                uniqueConceptIds.add(conceptId);
            }
        }

        final StringBuilder contentBuilder = new StringBuilder();
        contentBuilder.append("referenceComponentId\n"); // Append header
        for (final String conceptId : uniqueConceptIds) {
            contentBuilder.append(conceptId).append("\n"); // Append each concept ID with a line feed
        }

        try (final FileWriter fw = new FileWriter(newFilePath.toFile())) {
            fw.write(contentBuilder.toString());
        }

    }

}
