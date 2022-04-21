/*
 * Copyright 2022 SNOMED International - All Rights Reserved.
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
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Properties;

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
import com.amazonaws.services.s3.model.AmazonS3Exception;
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
public class S3ConnectionWrapper {

    /** The logger. */
    private static final Logger logger = LoggerFactory.getLogger(S3ConnectionWrapper.class);

    /** The config properties. */
    private final Properties properties = PropertyUtility.getProperties();

    /** The snowstorm url. */
    public static String ID;

    /** The snowstorm url for performing write or update actions. */
    public static String KEY;

    /** The bucket. */
    public static String BUCKET;

    /** The region. */
    public static Regions REGION;

    /** The folder directory. */
    public static String FOLDER_DIRECTORY;

    /** The S3 client. */
    private static AmazonS3 s3Client;

    /** Static initialization. */
    static {

        BUCKET = PropertyUtility.getProperty("aws.bucket");
        REGION = Regions.fromName(PropertyUtility.getProperty("aws.region"));
        FOLDER_DIRECTORY = PropertyUtility.getProperty("aws.folder_directory");
        ID = PropertyUtility.getProperty("aws.access.key.id");
        KEY = PropertyUtility.getProperty("aws.secret.access.key");
    }

    /**
     * Connect to amazon S3.
     *
     * @return the amazon S3
     */
    static public void connectToAmazonS3() {

        if (s3Client != null) {
            return;
        }

        try {
            // Connect to server using instance profile credentials
            s3Client = AmazonS3ClientBuilder.standard().withRegion(REGION).withCredentials(new InstanceProfileCredentialsProvider(false)).build();

            // Check if connection was successful. If not, try to connect with
            // static
            // keys instead
            try {
                s3Client.listBuckets();
            } catch (SdkClientException e) {
                // Connect to server with static keys

                AmazonS3ClientBuilder clientBuilder = AmazonS3ClientBuilder.standard().withRegion(REGION);

                if (ID != null && !ID.equals("") && !ID.equals("none") && !ID.equals("change_me")) {

                    final BasicAWSCredentials awsCreds = new BasicAWSCredentials(ID, KEY);
                    clientBuilder = clientBuilder.withCredentials(new AWSStaticCredentialsProvider(awsCreds));
                }

                s3Client = clientBuilder.build();

                // Check connection again. If this fails as well, it will throw
                // the
                // exception to the calling method
            }

            if (s3Client == null) {
                throw new NullPointerException("Client returned was null");
            }

            logger.info("Connected to S3 in region: " + REGION);
        } catch (Exception ex) {
            logger.error("Couldn't connect to AWS S3", ex);
            throw ex;
        }
    }

    /**
     * Upload file to S3.
     *
     * @param awsUploadPath the aws upload path
     * @param localFilePath the local file path
     * @param fileName the file name
     * @throws Exception the exception
     */
    public static void uploadToS3(final String awsUploadPath, final String localFilePath, final String fileName) throws Exception {

        try {
            // Upload a file as a new object with ContentType and title
            // specified.
            final PutObjectRequest request = new PutObjectRequest(S3ConnectionWrapper.BUCKET, awsUploadPath + "/" + fileName, new File(localFilePath + "/" + fileName));
            final ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("plain/text");
            metadata.addUserMetadata("title", fileName);
            request.setMetadata(metadata);
            s3Client.putObject(request);
        } catch (Exception e) {
            throw new Exception("Failed to upload the file: " + fileName + " locally at: " + localFilePath + " to the awsPath: " + awsUploadPath, e);
        }
    }

    /**
     * Upload file to S3.
     *
     * @param uri the uri
     * @param is the is
     * @throws AmazonS3Exception the amazon S 3 exception
     * @throws Exception the exception
     */
    public static void uploadImageToS3(final String uri, final InputStream is, final String contentType) throws AmazonS3Exception, Exception {

        if (uri.matches("s3\\://.+/.+")) {

            final String bucketName = getBucketName(uri);
            final String objectName = getObjectName(uri);

            try {

                final ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentLength(is.available());
                metadata.setContentType(contentType);
                metadata.addUserMetadata("title", objectName);

                connectToAmazonS3();
                s3Client.putObject(new PutObjectRequest(bucketName, objectName, is, metadata));

            } catch (AmazonS3Exception awse) {
                logger.error("Failed to upload the icon file: " + objectName + " to the aws bucket: " + bucketName, awse);
                logger.error(awse.getErrorMessage());
                throw awse;
            } catch (Exception e) {
                throw new Exception("Failed to upload the icon file: " + objectName + " to the aws bucket: " + bucketName, e);
            }
        } else {
            throw new Exception("Bad S3 URI = " + uri);
        }
    }

    /**
     * Indicates whether or not in S3 cache is the case.
     *
     * @param awsPath the aws path
     * @param versionFileName the version file name
     * @return <code>true</code> if so, <code>false</code> otherwise
     * @throws Exception the exception
     */
    public static boolean isInS3Cache(final String awsPath, final String versionFileName) throws Exception {

        return (s3Client.doesObjectExist(BUCKET, awsPath + "/" + versionFileName));
    }

    /**
     * Returns the S3 url.
     *
     * @param awsFilePath the aws file path
     * @param fileName the file name
     * @return the S3 url
     * @throws Exception the exception
     */
    public static String getS3Url(final String awsFilePath, final String fileName) throws Exception {

        try {
            return s3Client.getUrl(BUCKET, awsFilePath + "/" + fileName).toExternalForm();
        } catch (Exception e) {
            throw new Exception("Failed to get the file: " + fileName + " at the expected S3 Path: " + awsFilePath, e);
        }
    }

    /**
     * Download snow from S3.
     *
     * @param awsPath the aws path
     * @param awsFileName the aws file name
     * @param downloadLocation the download location
     * @throws Exception the exception
     */
    public static void downloadSnowFromS3(final String awsPath, final String awsFileName, final String downloadLocation) throws Exception {

        final S3Object o = s3Client.getObject(BUCKET, awsPath + "/" + awsFileName);
        final S3ObjectInputStream s3is = o.getObjectContent();

        final FileOutputStream fos = new FileOutputStream(

            new File(downloadLocation));
        final byte[] readBuf = new byte[1024];
        int readLen = 0;
        while ((readLen = s3is.read(readBuf)) > 0) {
            fos.write(readBuf, 0, readLen);
        }
        s3is.close();
        fos.close();
    }

    /**
     * Delete refset from aws.
     *
     * @param awsPath the aws path
     * @return true, if successful
     */
    public static boolean deleteRefsetFromAws(final String awsPath) {

        final ListObjectsV2Request listRequest = new ListObjectsV2Request().withBucketName(BUCKET).withPrefix(awsPath);
        final ListObjectsV2Result listing = s3Client.listObjectsV2(listRequest);

        final ArrayList<KeyVersion> keys = new ArrayList<KeyVersion>();
        for (S3ObjectSummary obj : listing.getObjectSummaries()) {
            keys.add(new KeyVersion(obj.getKey()));
        }

        if (keys.isEmpty()) {
            return true;
        }
        final DeleteObjectsRequest deleteRequest = new DeleteObjectsRequest(BUCKET).withKeys(keys).withQuiet(false);

        s3Client.deleteObjects(deleteRequest);
        final DeleteObjectsResult delObjRes = s3Client.deleteObjects(deleteRequest);

        final int successfulDeletes = delObjRes.getDeletedObjects().size();
        System.out.println(successfulDeletes + " objects successfully deleted.");

        return successfulDeletes > 0;
    }

    /**
     * Returns the bucket name.
     *
     * @param uri the uri
     * @return the bucket name
     */
    private static String getBucketName(final String uri) {

        return uri.replaceFirst("s3\\://", "").replaceFirst("/.*", "");
    }

    /**
     * Returns the object name.
     *
     * @param uri the uri
     * @return the object name
     */
    private static String getObjectName(final String uri) {

        return uri.replaceFirst("s3\\://", "").replaceFirst("[^/]+/(.*)", "$1");

    }

}
