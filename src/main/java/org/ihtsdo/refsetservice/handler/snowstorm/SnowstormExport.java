/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.handler.snowstorm;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;

import javax.ws.rs.core.Response;

import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SnowstormExport extends SnowstormAbstract {

  /** The Constant LOG. */
  private static final Logger LOG = LoggerFactory.getLogger(SnowstormExport.class);

  public static String generateVersionFile(final String entityString) throws Exception {

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

    LOG.debug(entityString);

    // Call Snowstorm to create RF2 file
    final String snowstormExportApiUrl = SnowstormConnection.getBaseUrl() + "exports";
    LOG.debug("Snowstorm Export API URL: " + snowstormExportApiUrl + entityString);

    String snowVersionFileUrl = "";

    try (final Response response =
        SnowstormConnection.postResponse(snowstormExportApiUrl, entityString);) {

      snowVersionFileUrl = response.getLocation().toString() + "/archive";
      LOG.debug("Snowstorm File URL: " + snowVersionFileUrl);

    } catch (final Exception ex) {
      throw new Exception("Could not generate the Rf2 file by snowstorm with : " + entityString,
          ex);

    }

    return snowVersionFileUrl;
  }

  /**
   * Download generated file.
   *
   * @param snowVersionFileUrl the snow version file url
   * @param localSnowVersionPath the local snow version path
   * @throws Exception the exception
   */
  public static void downloadGeneratedFile(final String snowVersionFileUrl,
    final String localSnowVersionPath) throws Exception {

    // Download generated file from Snowstorm
    LOG.debug("Local snow version file path is: " + localSnowVersionPath);

    // Download the Snowstorm file
    try (final InputStream inputStream = SnowstormConnection.getFileDownload(snowVersionFileUrl);
        final ReadableByteChannel readableByteChannel = Channels.newChannel(inputStream);
        final FileOutputStream fileOutputStream = new FileOutputStream(localSnowVersionPath);
        final FileChannel fileChannel = fileOutputStream.getChannel()) {

      fileChannel.transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
    } catch (final Exception ex) {
      throw new Exception("Failed to download the Snowstorm generated RF2 file: " + ex.getMessage(),
          ex);
    }
  }

}
