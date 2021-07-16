
package org.ihtsdo.refsetservice.handler;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.Set;

import javax.ws.rs.core.Response;

import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.terminologyservice.S3ConnectionWrapper;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
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

    private static final String TOP_LEVEL_AWS_FOLDER = S3ConnectionWrapper.FOLDER_DIRECTORY + "/";

    /** The logger. */
    @SuppressWarnings("unused")
    private static Logger logger = LoggerFactory.getLogger(ExportHandler.class);

    public boolean isRequestedFileOnS3(final Refset refset, AmazonS3 s3Client, String type,
        Set<String> dates) throws Exception {
        // Check if date lives on s3
        return false;
    }

    public String generateRt2VersionFileName(Refset refset, String type, String languageId,
        Set<String> dates, boolean exportMetadata, boolean withNames) throws Exception {
        if (("snapshot".equals(type.toLowerCase()) && dates.size() != 1)
                // if (("snapshot".equals(type.toLowerCase()) &&
                // transientEffectiveTime != null)
                || ("delta".equals(type.toLowerCase()) && dates.size() != 2)) {
            throw new Exception("Have a " + type + " rf2 request with " + dates.size()
                    + " number of dates provided");
        }

        String name;

        if ("snapshot".equals(type.toLowerCase())) {
            name = "refset_" + refset.getRefsetId() + "_" + dates.toArray()[0] + "_" + type;
        } else {
            name = "refset_" + refset.getRefsetId() + "_" + dates.toArray()[0] + "_" + type + "_"
                    + dates.toArray()[1];
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

    public String generateAwsBaseVersionPath(Refset refset, String type, Set<String> dates) {
        if ("snapshot".equals(type.toLowerCase())) {
            return TOP_LEVEL_AWS_FOLDER + refset.getRefsetId() + "/" + dates.toArray()[0] + "/"
                    + type;

        } else {
            return TOP_LEVEL_AWS_FOLDER + refset.getRefsetId() + "/" + dates.toArray()[0] + "/"
                    + type + "/" + dates.toArray()[1];
        }
    }

    public String generateSnowVersionFileName(Refset refset, String type, Set<String> dates) {
        // der2_Refset_Simple551000172106Snapshot_BE_20200315
        if ("snapshot".equals(type.toLowerCase())) {
            return "der2_Refset_Simple" + refset.getRefsetId() + type + "_"
                    + refset.getEditionShortName() + "_" + dates.toArray()[0] + ".zip";
        } else {
            return "der2_Refset_Simple" + refset.getRefsetId() + type + "_"
                    + refset.getEditionShortName() + "_" + dates.toArray()[0] + "_"
                    + dates.toArray()[1] + ".zip";
        }
    }

    public String getTopLevelAwsPath() {
        return TOP_LEVEL_AWS_FOLDER;
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
