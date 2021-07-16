package org.ihtsdo.refsetservice.terminologyservice;

import java.io.File;
import java.io.FileOutputStream;
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
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;

/**
 * Class to handle making calls to Snowstorm.
 */
public class S3ConnectionWrapper {

    /** The config properties. */
    private final Properties properties = PropertyUtility.getProperties();
    
    /** The snowstorm url. */
    public static String ID;

    /** The snowstorm url for performing write or update actions. */
    public static String KEY;

    public static String BUCKET;

    public static Regions REGION;
    
    public static String FOLDER_DIRECTORY;

    private static AmazonS3 s3Client;

    /** The logger. */
    private static final Logger logger = LoggerFactory.getLogger(S3ConnectionWrapper.class);

    /** Static initialization. */
    static {

        BUCKET = PropertyUtility.getProperty("aws.bucket");
        REGION = Regions.fromName(PropertyUtility.getProperty("aws.region"));
        FOLDER_DIRECTORY = PropertyUtility.getProperty("aws.folder_directory");
        ID = PropertyUtility.getProperty("aws.access.key.id");
        KEY = PropertyUtility.getProperty("aws.secret.access.key");
    }

    /**
     * Connect to amazon S 3.
     *
     * @return the amazon S 3
     */
    static public void connectToAmazonS3() {
        if (s3Client != null) {
            return;
        }

        try {
            // Connect to server using instance profile credentials
            s3Client = AmazonS3ClientBuilder.standard().withRegion(REGION)
                    .withCredentials(new InstanceProfileCredentialsProvider(false)).build();

            // Check if connection was successful. If not, try to connect with
            // static
            // keys instead
            try {
                s3Client.listBuckets();
            } catch (SdkClientException e) {
                // Connect to server with static keys
                BasicAWSCredentials awsCreds = new BasicAWSCredentials(ID, KEY);
                s3Client = AmazonS3ClientBuilder.standard().withRegion(REGION)
                        .withCredentials(new AWSStaticCredentialsProvider(awsCreds)).build();

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

    public static void uploadToS3(String awsUploadPath, String localFilePath, String fileName)
        throws Exception {
        try {
            // Upload a file as a new object with ContentType and title
            // specified.
            PutObjectRequest request = new PutObjectRequest(S3ConnectionWrapper.BUCKET,
                    awsUploadPath + "/" + fileName, new File(localFilePath + "/" + fileName));
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("plain/text");
            metadata.addUserMetadata("title", fileName);
            request.setMetadata(metadata);
            s3Client.putObject(request);
        } catch (Exception e) {
            throw new Exception("Failed to upload the file: " + fileName + " locally at: "
                    + localFilePath + " to the awsPath: " + awsUploadPath, e);
        }
    }

    public static boolean isInS3Cache(String awsPath, String versionFileName) throws Exception {
        return (s3Client.doesObjectExist(BUCKET, awsPath + "/" + versionFileName));
    }

    public static String getS3Url(String awsFilePath, String fileName) throws Exception {

        try {
            return s3Client.getUrl(BUCKET, awsFilePath + "/" + fileName).toExternalForm();
        } catch (Exception e) {
            throw new Exception("Failed to get the file: " + fileName + " at the expected S3 Path: "
                    + awsFilePath, e);
        }
    }

    public static void downloadSnowFromS3(String awsPath, String awsFileName,
        String downloadLocation) throws Exception {
        S3Object o = s3Client.getObject(BUCKET, awsPath + "/" + awsFileName);
        S3ObjectInputStream s3is = o.getObjectContent();

        FileOutputStream fos = new FileOutputStream(

                new File(downloadLocation));
        byte[] readBuf = new byte[1024];
        int readLen = 0;
        while ((readLen = s3is.read(readBuf)) > 0) {
            fos.write(readBuf, 0, readLen);
        }
        s3is.close();
        fos.close();
    }

}
