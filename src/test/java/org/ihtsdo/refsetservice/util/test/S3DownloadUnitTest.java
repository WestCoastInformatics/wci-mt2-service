package org.ihtsdo.refsetservice.util.test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;

/**
 * Validates the code-system-* files in the "src/resources" folder.
 */
public class S3DownloadUnitTest extends BaseTest {

    /** The logger. */
    private final Logger logger = LoggerFactory.getLogger(S3DownloadUnitTest.class);

    /**
     * 
     * Gets an AmazonS3 object based first on
     * InstanceProfileCredentialsProvider. If not available, will then use
     * AWSStaticCredentialsProvider.
     * 
     * @throws Exception the exception
     */
    @Test
    public void testConnectability() throws Exception {
        AmazonS3 s3Client = connectToAmazonS3();
        assertNotNull(s3Client);
    }

    /**
     * Test file download.
     *
     * @throws Exception the exception
     */
    @Test
    public void testFileDownload() throws Exception {
        AmazonS3 s3Client = connectToAmazonS3();

        ObjectListing objects = s3Client.listObjects("wci1");
        List<S3ObjectSummary> fullKeyList = objects.getObjectSummaries();
        objects = s3Client.listNextBatchOfObjects(objects);

        while (objects.isTruncated()) {
            fullKeyList.addAll(objects.getObjectSummaries());
            objects = s3Client.listNextBatchOfObjects(objects);
        }

        fullKeyList.addAll(objects.getObjectSummaries());

        // Download a single file
        logger.info("List of files in S3 Bucket:");
        for (S3ObjectSummary obj : fullKeyList) {
            logger.info(obj.getKey());
        }

        String downloadFilename = null;
        for (S3ObjectSummary item : fullKeyList) {
            if (!item.getKey().endsWith("/")) {
                downloadFilename = item.getKey();
                break;
            }
        }

        if (downloadFilename == null) {
            throw new Exception("No files found in S3 bucket");
        }

        logger.info("Going to download the first object only: " + downloadFilename);

        S3Object o = s3Client.getObject("wci1", downloadFilename);
        S3ObjectInputStream s3is = o.getObjectContent();
        FileOutputStream fos = new FileOutputStream(
                new File(downloadFilename.substring(downloadFilename.indexOf("/") + 1)));
        byte[] readBuf = new byte[1024];
        int readLen = 0;
        while ((readLen = s3is.read(readBuf)) > 0) {
            fos.write(readBuf, 0, readLen);
        }
        s3is.close();
        fos.close();
    }

    /**
     * Connect to amazon S 3.
     *
     * @return the amazon S 3
     */
    private AmazonS3 connectToAmazonS3() {
        // Connect to server using instance profile credentials
        AmazonS3 s3Client = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_1)
                .withCredentials(new InstanceProfileCredentialsProvider(false)).build();

        // Check if connection was successful. If not, try to connect with
        // static
        // keys instead
        try {
            s3Client.listBuckets();
        } catch (SdkClientException e) {
            // Connect to server with static keys
            BasicAWSCredentials awsCreds = new BasicAWSCredentials(
                    PropertyUtility.getProperties().getProperty("aws.access.key.id"),
                    PropertyUtility.getProperties().getProperty("aws.secret.access.key"));
            s3Client = AmazonS3ClientBuilder.standard().withRegion(Regions.US_EAST_1)
                    .withCredentials(new AWSStaticCredentialsProvider(awsCreds)).build();

            // Check connection again. If this fails as well, it will throw the
            // exception to the calling method
        }

        return s3Client;
    }
}
