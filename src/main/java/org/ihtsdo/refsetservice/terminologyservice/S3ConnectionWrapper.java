/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.DeleteObjectsRequest;
import com.amazonaws.services.s3.model.DeleteObjectsRequest.KeyVersion;
import com.amazonaws.services.s3.model.DeleteObjectsResult;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.S3ObjectSummary;

/**
 * Class to handle making calls to Amazon S3.
 */
public final class S3ConnectionWrapper {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(S3ConnectionWrapper.class);

    /** The snowstorm url. */
    private static String awsAccessKeyId;

    /** The snowstorm url for performing write or update actions. */
    private static String awsSecretAccessKey;

    /** The bucket. */
    private static String awsBucket;

    /** The region. */
    private static Regions awsRegion;

    /** The project directory. */
    private static String projectDir;

    /** The icon directory. */
    private static String iconDir;

    /** The artifact directory. */
    private static String artifactDir;

    /** The S3 client. */
    private static AmazonS3 s3Client;

    /** The S3 SEPARATOR character. */
    private static final String SEPARATOR = "/";

    /** Static initialization. */
    static {

        awsBucket = PropertyUtility.getProperty("aws.bucket");
        awsRegion = Regions.fromName(PropertyUtility.getProperty("aws.region"));
        projectDir = PropertyUtility.getProperty("aws.project.base.dir");
        iconDir = PropertyUtility.getProperty("aws.icon.dir");
        artifactDir = PropertyUtility.getProperty("aws.artifact.dir");
        awsAccessKeyId = PropertyUtility.getProperty("aws.access.key.id");
        awsSecretAccessKey = PropertyUtility.getProperty("aws.secret.access.key");
    }

    /**
     * Instantiates an empty {@link S3ConnectionWrapper}.
     */
    private S3ConnectionWrapper() {

        // n/a
    }

    /**
     * Connect to amazon S 3.
     */
    @SuppressWarnings("resource")
	private static void connectToAmazonS3() {

        if (s3Client != null) {
            return;
        }

        try {

            // Connect to server using instance profile credentials
            s3Client = AmazonS3ClientBuilder.standard().withRegion(awsRegion).withCredentials(new InstanceProfileCredentialsProvider(false)).build();

            // Check if connection was successful. If not, try to connect with static keys instead
            try {
                s3Client.listBuckets();

            } catch (final SdkClientException e) {

                // Connect to server with static keys
                AmazonS3ClientBuilder clientBuilder = AmazonS3ClientBuilder.standard().withRegion(awsRegion);

                if (awsAccessKeyId != null && !awsAccessKeyId.equals("") && !awsAccessKeyId.equals("none") && !awsAccessKeyId.equals("change_me")) {

                    final BasicAWSCredentials awsCreds = new BasicAWSCredentials(awsAccessKeyId, awsSecretAccessKey);
                    clientBuilder = clientBuilder.withCredentials(new AWSStaticCredentialsProvider(awsCreds));
                }

                s3Client = clientBuilder.build();
            }

            if (s3Client == null) {
                throw new NullPointerException("Client returned was null");
            }

            LOG.info("Connected to S3 in region: " + awsRegion);

        } catch (final Exception ex) {

            LOG.error("Couldn't connect to AWS S3", ex);
            throw ex;
        }
    }

    /**
     * Returns the SEPARATOR.
     *
     * @return the SEPARATOR
     */
    public static String getSeparator() {

        return SEPARATOR;
    }

    /**
     * Returns the project directory.
     *
     * @return the project directory
     */
    public static String getProjectDirectory() {

        return projectDir;
    }

    /**
     * Upload file to S3.
     *
     * @param awsUploadPath the AWS upload path
     * @param localFilePath the local file path
     * @param fileName the file name
     * @throws Exception the exception
     */
    public static void uploadToS3(final String awsUploadPath, final String localFilePath, final String fileName) throws Exception {

        final String filePath = getCorrectAwsFilePath(awsUploadPath);

        try {
            connectToAmazonS3();

            // Upload a file as a new object with ContentType and title specified.
            final PutObjectRequest request =
                new PutObjectRequest(S3ConnectionWrapper.awsBucket, filePath + fileName, new File(localFilePath + File.separator + fileName));
            final ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("plain/text");
            metadata.addUserMetadata("title", fileName);
            request.setMetadata(metadata);
            s3Client.putObject(request);
        } catch (final Exception e) {
            throw new Exception("Failed to upload the file: " + fileName + " locally at: " + localFilePath + " to the awsPath: " + filePath, e);
        }
    }

    /**
     * Indicates whether or not the file is in the S3 cache.
     *
     * @param awsPath the AWS path
     * @param fileName the file name
     * @return <code>true</code> if so, <code>false</code> otherwise
     * @throws Exception the exception
     */
    public static boolean isInS3Cache(final String awsPath, final String fileName) throws Exception {

        connectToAmazonS3();
        final String filePath = getCorrectAwsFilePath(awsPath);
        return (s3Client.doesObjectExist(awsBucket, filePath + fileName));
    }

    /**
     * Returns the S3 url.
     *
     * @param awsFilePath the AWS file path
     * @param fileName the file name
     * @return the S3 url
     * @throws Exception the exception
     */
    public static String getS3Url(final String awsFilePath, final String fileName) throws Exception {

        connectToAmazonS3();
        final String filePath = getCorrectAwsFilePath(awsFilePath);

        try {
            return s3Client.getUrl(awsBucket, filePath + fileName).toExternalForm();
        } catch (final Exception e) {
            throw new Exception("Failed to get the file: " + fileName + " at the expected S3 Path: " + awsFilePath, e);
        }
    }

    /**
     * Download a file from S3.
     *
     * @param awsPath the AWS path
     * @param awsFileName the AWS file name
     * @param downloadLocation the download location
     * @throws Exception the exception
     */
    public static void downloadFileFromS3(final String awsPath, final String awsFileName, final String downloadLocation) throws Exception {

        connectToAmazonS3();
        final String filePath = getCorrectAwsFilePath(awsPath);

        try (S3Object s3Object = s3Client.getObject(awsBucket, filePath + awsFileName)) {

            try (final S3ObjectInputStream s3InputStream = s3Object.getObjectContent()) {

                try (final FileOutputStream fos = new FileOutputStream(new File(downloadLocation))) {

                    final byte[] readBuf = new byte[1024];
                    int readLen = 0;

                    while ((readLen = s3InputStream.read(readBuf)) > 0) {
                        fos.write(readBuf, 0, readLen);
                    }
                }
            }
        }
    }

    /**
     * Delete an object from AWS.
     *
     * @param awsPath the AWS path
     * @return true, if successful
     */
    public static boolean deleteObjectFromAws(final String awsPath) {

        connectToAmazonS3();
        LOG.debug("deleteObjectFromAws: awsPath: " + awsPath);

        final ListObjectsV2Request listRequest = new ListObjectsV2Request().withBucketName(awsBucket).withPrefix(awsPath);
        final ListObjectsV2Result listing = s3Client.listObjectsV2(listRequest);

        final ArrayList<KeyVersion> keys = new ArrayList<KeyVersion>();

        for (final S3ObjectSummary obj : listing.getObjectSummaries()) {

            keys.add(new KeyVersion(obj.getKey()));
            LOG.debug("deleteObjectFromAws: object to delete: " + obj.getKey());
        }

        if (keys.isEmpty()) {
            return true;
        }
        final DeleteObjectsRequest deleteRequest = new DeleteObjectsRequest(awsBucket).withKeys(keys).withQuiet(false);

        s3Client.deleteObjects(deleteRequest);
        final DeleteObjectsResult delObjRes = s3Client.deleteObjects(deleteRequest);

        final int successfulDeletes = delObjRes.getDeletedObjects().size();
        LOG.debug("deleteObjectFromAws: " + successfulDeletes + " objects successfully deleted.");

        return successfulDeletes > 0;
    }

    /**
     * Delete an object from AWS.
     *
     * @param awsPath the AWS path
     * @return the listing of files
     */
    public static List<String> getDirectoryListing(final String awsPath) {

        LOG.debug("getDirectoryListing: awsPath: " + awsPath);
        connectToAmazonS3();

        final ListObjectsV2Request listRequest = new ListObjectsV2Request().withBucketName(awsBucket).withPrefix(awsPath);
        final ListObjectsV2Result listing = s3Client.listObjectsV2(listRequest);
        final List<String> files = new ArrayList<String>();

        for (final S3ObjectSummary obj : listing.getObjectSummaries()) {
            files.add(obj.getKey());
        }

        return files;

    }

    /**
     * Make sure the AWS file path ends with the correct SEPARATOR.
     *
     * @param awsPath the AWS path
     * @return the correct AWS path
     * @throws Exception the exception
     */
    public static String getCorrectAwsFilePath(final String awsPath) throws Exception {

        String filePath = awsPath;

        if (!filePath.endsWith(SEPARATOR)) {
            filePath += SEPARATOR;
        }

        return filePath;
    }

    /**
     * Get the AWS project path with with the end SEPARATOR.
     *
     * @return the AWS project path
     * @throws Exception the exception
     */
    public static String getAwsProjectPath() throws Exception {

        return projectDir + SEPARATOR;
    }

    /**
     * Get the AWS icon path with with the end SEPARATOR.
     *
     * @return the AWS icon path
     * @throws Exception the exception
     */
    public static String getAwsIconPath() throws Exception {

        return getAwsProjectPath() + iconDir + SEPARATOR;
    }

    /**
     * Get the AWS artifact path with with the end SEPARATOR.
     *
     * @return the AWS artifact path
     * @throws Exception the exception
     */
    public static String getAwsArtifactPath() throws Exception {

        return getAwsProjectPath() + artifactDir + SEPARATOR;
    }
}
