/*
 * Copyright 2022 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.handler;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Set;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.Edition;
//import org.flywaydb.core.internal.license.Edition;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetService;
import org.ihtsdo.refsetservice.terminologyservice.S3ConnectionWrapper;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.ihtsdo.refsetservice.util.StringUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.services.s3.AmazonS3;

/**
 * Generically represents an algorithm for object searching.
 */
/**
 * @author jesseefron
 *
 */
public class ExportHandler {

    private static final String TOP_LEVEL_AWS_FOLDER = S3ConnectionWrapper.PROJECT_DIR + "/";

    /** The logger. */
    @SuppressWarnings("unused")
    private static Logger logger = LoggerFactory.getLogger(ExportHandler.class);

    public boolean isRequestedFileOnS3(final Refset refset, AmazonS3 s3Client, String type,
        Set<String> dates) throws Exception {
        // Check if date lives on s3
        return false;
    }

    /**
     * Generate RT2 version file name.
     *
     * @param refset the refset
     * @param type the type
     * @param languageId the language id
     * @param dates the dates
     * @param exportMetadata the export metadata
     * @param withNames the with names
     * @return the string
     * @throws Exception the exception
     */
    public String generateRt2VersionFileName(Refset refset, String type, String languageId, Set<String> dates, boolean exportMetadata, boolean withNames) throws Exception {

        // RT2-1037
        // der2_Refset_<refset_name_in_camelcase><Snapshot|Delta><CountryCode><Namespace>_<PublicationDate>.txt

        if ((type.toLowerCase().contains("snapshot") && dates.size() != 1)
            // if (("snapshot".equals(type.toLowerCase()) &&
            // transientEffectiveTime != null)
            || ("delta".equals(type.toLowerCase()) && dates.size() != 2)) {
            throw new Exception("Have a " + type + " rf2 request with " + dates.size() + " number of dates provided");
        }

        // if (type.toLowerCase().contains("snapshot")) {
        // name = "refset_" + refset.getRefsetId() + "_" + dates.toArray()[0] + "_" + type;
        // } else {
        // name = "refset_" + refset.getRefsetId() + "_" + dates.toArray()[0] + "_" + type + "_"
        // + dates.toArray()[1];
        // }

        String namespace = "";
        try (final TerminologyService service = new TerminologyService()) {

            final String editionId = refset.getEditionId();
            final Edition edition = service.get(editionId, Edition.class);
            namespace = edition.getNamespace() != null ? edition.getNamespace() : "";
        }

        String countryCode = refset.getEditionShortName().replace("SNOMEDCT", "").replace("-", "");
        if (StringUtils.isBlank(countryCode)) {
            countryCode = "INT";
        }

        String name;

        if ("snapshot".equals(type.toLowerCase())) {

            name = "der2_Refset_" + StringUtility.camelCase(refset.getName().replaceAll("[\\\\/:*?\"<>|]", "-")) + "Snapshot" + "_" + countryCode + namespace + refset.getRefsetId() + "_" + dates.toArray()[0];

        } else {

            name = "der2_Refset_" + StringUtility.camelCase(refset.getName().replaceAll("[\\\\/:*?\"<>|]", "-")) + "Delta" + "_" + countryCode + namespace + refset.getRefsetId() + "_" + dates.toArray()[0] + "_"
                + (dates.toArray().length > 1 ? dates.toArray()[1] : dates.toArray()[0]);
        }

        if (withNames) {
            name = name + "_" + languageId; // TODO: Add Language here too
        }

        if (exportMetadata) {
            name = name + "_With-Metadata"; // TODO: Add Language here too
        }

        name = name + ".zip";

        return name;
    }

    public String generateAwsBaseVersionPath(Refset refset, String type, Set<String> dates) throws Exception {
        
        String path =  getAwsBranchPath(refset) + "/" + refset.getRefsetId() + "/" + dates.toArray()[0] + "/" + type;
            
        if (!"snapshot".equals(type.toLowerCase())) {
            path += "/" + (dates.toArray().length > 1 ? dates.toArray()[1] : dates.toArray()[0]);
        }
        
        return path;
    }
    
    public String getAwsBranchPath(Refset refset) throws Exception {
        return TOP_LEVEL_AWS_FOLDER + RefsetService.getBranchPath(refset);
    }

    public String generateSnowVersionFileName(Refset refset, String type, Set<String> dates) {
        // der2_Refset_Simple551000172106Snapshot_BE_20200315
        if ("snapshot".equals(type.toLowerCase())) {
            return "der2_Refset_Simple" + refset.getRefsetId() + type + "_"
                    + refset.getEditionShortName() + "_" + dates.toArray()[0] + ".zip";
        } else {
            return "der2_Refset_Simple" + refset.getRefsetId() + type + "_"
                    + refset.getEditionShortName() + "_" + dates.toArray()[0] + "_"
                    + (dates.toArray().length > 1 ? dates.toArray()[1] : dates.toArray()[0]) + ".txt";
        }
    }

    public String getTopLevelAwsPath() {
        return TOP_LEVEL_AWS_FOLDER;
    }
    
    public boolean deleteFilesFromBranchPath(final String branchPath) throws Exception{
        
        S3ConnectionWrapper.connectToAmazonS3();
        return S3ConnectionWrapper.deleteObjectFromAws(getTopLevelAwsPath() + branchPath);
    }

    public String generateSnowVersionFile(String entityString) throws Exception {
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
        } */

        logger.debug(entityString);

        // Call Snowstorm to create RF2 file
        String snowstormExportApiUrl = SnowstormConnection.BASE_URL + "exports";

        logger.debug("Snowstorm Export API URL: " + snowstormExportApiUrl + entityString);

        String snowVersionFileUrl = "";

        try (Response response =
                SnowstormConnection.postResponse(snowstormExportApiUrl, entityString)) {

            snowVersionFileUrl = response.getLocation().toString() + "/archive";
            logger.debug("Snowstorm File URL: " + snowVersionFileUrl);

        } catch (Exception ex) {
            throw new Exception(
                    "Could not generate the Rf2 file by snowstorm with : " + entityString, ex);

        }

        return snowVersionFileUrl;
    }

    public void downloadSnowGeneratedFile(String snowVersionFileUrl, String localSnowVersionPath)
        throws Exception {

        // Download generated file from Snowstorm
        try {

            logger.debug("Local snow version file path is: " + localSnowVersionPath);

            // Download the Snowstorm file
            try (InputStream inputStream = SnowstormConnection.getFileDownload(snowVersionFileUrl);
                    ReadableByteChannel readableByteChannel = Channels.newChannel(inputStream);
                    FileOutputStream fileOutputStream = new FileOutputStream(localSnowVersionPath);
                    FileChannel fileChannel = fileOutputStream.getChannel()) {

                fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
                fileOutputStream.close();
            }

        } catch (Exception ex) {
            throw new Exception(
                    "Failed to download the Snowstorm generated RF2 file: " + ex.getMessage(), ex);
        }
    }
}
