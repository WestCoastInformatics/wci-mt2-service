/*
 * Copyright 2026 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.io.IOException;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

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
    private static Region awsRegion;

    /** The project directory. */
    private static String projectDir;

    /** The icon directory. */
    private static String iconDir;

    /** The artifact directory. */
    private static String artifactDir;

    /** The S3 client. */
    private static S3Client s3Client;

    /** The S3 SEPARATOR character. */
    private static final String SEPARATOR = "/";

    public static void loadConfigurationFromProperties() {

        awsBucket = PropertyUtility.getProperty("aws.bucket");
        awsRegion = Region.of(PropertyUtility.getProperty("aws.region"));
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
     * Load S3 properties once after PropertyUtility is ready.
     */
    private static void ensureConfigured() {

        if (awsRegion != null) {
            loadConfigurationFromProperties();
        }
    }

    /**
     * Connect to amazon S 3.
     */
    private static void connectToAmazonS3() {

        ensureConfigured();
        if (s3Client != null) {
            return;
        }

        try {

            // Prefer static keys when configured; otherwise use the default chain (instance profile, env, etc.)
            final AwsCredentialsProvider credentialsProvider;
            if (hasStaticCredentials()) {
                credentialsProvider =
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(awsAccessKeyId, awsSecretAccessKey));
            } else {
                credentialsProvider = DefaultCredentialsProvider.builder().build();
            }

            final S3Client client = S3Client.builder().region(awsRegion).credentialsProvider(credentialsProvider).build();
            client.listBuckets();
            s3Client = client;

            LOG.info("Connected to S3 in region: " + awsRegion);

        } catch (final Exception ex) {

            LOG.error("Couldn't connect to AWS S3", ex);
            throw ex;
        }
    }

    /**
     * Indicates whether static AWS credentials are configured.
     *
     * @return true if static credentials are usable
     */
    private static boolean hasStaticCredentials() {

        return awsAccessKeyId != null && !awsAccessKeyId.equals("") && !awsAccessKeyId.equals("none")
            && !awsAccessKeyId.equals("change_me");
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

        ensureConfigured();
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

            final PutObjectRequest request = PutObjectRequest.builder().bucket(awsBucket).key(filePath + fileName)
                .contentType("plain/text").metadata(Map.of("title", fileName)).build();
            s3Client.putObject(request, Path.of(localFilePath + File.separator + fileName));
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

        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(awsBucket).key(filePath + fileName).build());
            return true;
        } catch (final NoSuchKeyException e) {
            return false;
        } catch (final S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }
    /**
     * Checks if the file exists in S3 cache and downloads it if it does.
     *
     * @param awsVersionedPath the AWS versioned path
     * @param fileName the file name
     * @param localFilePath the local file path
     * @return true if file was downloaded from S3, false otherwise
     * @throws IOException if there is an error downloading the file
     */
    public static boolean checkAndDownloadFromS3(final String awsVersionedPath, final String fileName, final String localFilePath) throws IOException {

        try {
            if (isInS3Cache(awsVersionedPath, fileName)) {
                LOG.info("Downloading file from S3: {}", fileName);
                downloadFileFromS3(awsVersionedPath, fileName, localFilePath);
                return true;
            }
            return false;
        } catch (final Exception e) {
            throw new IOException("Failed to check and download from S3", e);
        }
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
            return s3Client.utilities().getUrl(GetUrlRequest.builder().bucket(awsBucket).key(filePath + fileName).build()).toExternalForm();
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

        final GetObjectRequest request = GetObjectRequest.builder().bucket(awsBucket).key(filePath + awsFileName).build();
        s3Client.getObject(request, Path.of(downloadLocation));
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

        final ListObjectsV2Response listing =
            s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(awsBucket).prefix(awsPath).build());

        final List<ObjectIdentifier> keys = new ArrayList<>();

        for (final S3Object obj : listing.contents()) {

            keys.add(ObjectIdentifier.builder().key(obj.key()).build());
            LOG.debug("deleteObjectFromAws: object to delete: " + obj.key());
        }

        if (keys.isEmpty()) {
            return true;
        }

        final DeleteObjectsResponse delObjRes = s3Client.deleteObjects(DeleteObjectsRequest.builder().bucket(awsBucket)
            .delete(Delete.builder().objects(keys).quiet(false).build()).build());

        final int successfulDeletes = delObjRes.deleted().size();
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

        final ListObjectsV2Response listing =
            s3Client.listObjectsV2(ListObjectsV2Request.builder().bucket(awsBucket).prefix(awsPath).build());
        final List<String> files = new ArrayList<>();

        for (final S3Object obj : listing.contents()) {
            files.add(obj.key());
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

        ensureConfigured();
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
