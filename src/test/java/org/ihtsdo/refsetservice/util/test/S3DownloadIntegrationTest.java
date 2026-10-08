package org.ihtsdo.refsetservice.util.test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Validates the code-system-* files in the "src/resources" folder.
 */
public class S3DownloadIntegrationTest extends BaseTest {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(S3DownloadIntegrationTest.class);

    /**
     * Test file download.
     *
     * @throws Exception the exception
     */
    @Test
    public void testFileDownload() throws Exception {

        final S3Client s3Client = connectToAmazonS3();

        final List<S3Object> fullKeyList = new ArrayList<>();
        String continuationToken = null;
        do {
            final ListObjectsV2Request.Builder requestBuilder = ListObjectsV2Request.builder().bucket("wci1");
            if (continuationToken != null) {
                requestBuilder.continuationToken(continuationToken);
            }
            final ListObjectsV2Response response = s3Client.listObjectsV2(requestBuilder.build());
            fullKeyList.addAll(response.contents());
            continuationToken = Boolean.TRUE.equals(response.isTruncated()) ? response.nextContinuationToken() : null;
        } while (continuationToken != null);

        // Download a single file
        LOG.info("List of files in S3 Bucket:");
        for (final S3Object obj : fullKeyList) {
            LOG.info(obj.key());
        }

        String downloadFilename = null;
        for (final S3Object item : fullKeyList) {
            if (!item.key().endsWith("/")) {
                downloadFilename = item.key();
                break;
            }
        }

        if (downloadFilename == null) {
            throw new Exception("No files found in S3 bucket");
        }

        LOG.info("Going to download the first object only: " + downloadFilename);

        final GetObjectRequest request = GetObjectRequest.builder().bucket("wci1").key(downloadFilename).build();
        s3Client.getObject(request, Path.of(downloadFilename.substring(downloadFilename.indexOf("/") + 1)));
    }

    /**
     * Connect to amazon S 3.
     *
     * @return the amazon S 3
     */
    private S3Client connectToAmazonS3() {

        final String accessKeyId = PropertyUtility.getProperties().getProperty("aws.access.key.id");
        final String secretAccessKey = PropertyUtility.getProperties().getProperty("aws.secret.access.key");

        final AwsCredentialsProvider credentialsProvider;
        if (accessKeyId != null && !accessKeyId.isEmpty() && !"none".equals(accessKeyId) && !"change_me".equals(accessKeyId)) {
            credentialsProvider = StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey));
        } else {
            credentialsProvider = DefaultCredentialsProvider.builder().build();
        }

        return S3Client.builder().region(Region.US_EAST_1).credentialsProvider(credentialsProvider).build();
    }
}
