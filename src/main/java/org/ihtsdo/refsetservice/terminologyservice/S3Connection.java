package org.ihtsdo.refsetservice.terminologyservice;

import java.io.File;

import org.ihtsdo.refsetservice.util.FileUtility;
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
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;

/**
 * Class to handle making calls to Snowstorm.
 */
public class S3Connection {

    /** The snowstorm url. */
    public static String ID;

    /** The snowstorm url for performing write or update actions. */
    public static String KEY;

    public static String BUCKET = "wci2";

    public static Regions REGION = Regions.US_EAST_1;

    /** The logger. */
    private static final Logger logger = LoggerFactory.getLogger(S3Connection.class);

    /** Static initialization. */
    static {

        ID = PropertyUtility.getProperty("aws.access.key.id");
        KEY = PropertyUtility.getProperty("aws.secret.access.key");
    }

    /**
     * Connect to amazon S 3.
     *
     * @return the amazon S 3
     */
    static public AmazonS3 connectToAmazonS3() {
        
        // Connect to server using instance profile credentials
        AmazonS3 s3Client = AmazonS3ClientBuilder.standard().withRegion(REGION)
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

            // Check connection again. If this fails as well, it will throw the
            // exception to the calling method
        }

        logger.info("Connected to S3 in region: " + REGION);

        return s3Client;
    }

    public static void uploadToS3(AmazonS3 s3Client, String awsUploadPath, String localFilePath,
        String fileName) throws Exception {

        // Upload a file as a new object with ContentType and title specified.
        PutObjectRequest request = new PutObjectRequest(S3Connection.BUCKET,
                awsUploadPath + "/" + fileName, new File(localFilePath + "/" + fileName));
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentType("plain/text");
        metadata.addUserMetadata("title", fileName);
        request.setMetadata(metadata);
        s3Client.putObject(request);
    }

    public static String getS3Path(AmazonS3 s3Client, String awsFilePath, String fileName) {

        try {
            // Generate the filepath of where the cached Zip file would be
            if (s3Client.doesObjectExist(BUCKET, awsFilePath + "/" + fileName)) {
                return s3Client.getUrl(BUCKET, awsFilePath + "/" + fileName).toExternalForm();
            } else {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
