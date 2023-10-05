package org.ihtsdo.refsetservice.util;
//
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.FileOutputStream;
//import java.io.IOException;
//import java.security.KeyFactory;
//import java.security.KeyPair;
//import java.security.KeyPairGenerator;
//import java.security.MessageDigest;
//import java.security.NoSuchAlgorithmException;
//import java.security.PrivateKey;
//import java.security.PublicKey;
//import java.security.spec.PKCS8EncodedKeySpec;
//import java.security.spec.X509EncodedKeySpec;
//import java.util.Base64;
//
//import javax.crypto.Cipher;
//
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
///**
// * Utility class for interacting with files.
// */
//public final class CryptUtility {
//
//    /** The logger. */
//    @SuppressWarnings("unused")
//    private static Logger logger = LoggerFactory.getLogger(CryptUtility.class);
//
//    /**
//     * Instantiates an empty {@link CryptUtility}.
//     */
//    private CryptUtility() {
//
//        // n/a
//    }
//
//    /**
//     * To SHA 1.
//     *
//     * @param convertme the convertme
//     * @return the string
//     * @throws Exception the exception
//     */
//    public static String sha1Base64(final byte[] convertme) throws Exception {
//
//        return Base64.getEncoder().encodeToString((MessageDigest.getInstance("SHA-1").digest(convertme)));
//    }
//
//    /**
//     * Sign hash.
//     *
//     * @param hash the hash
//     * @param key the key
//     * @return the string
//     * @throws Exception the exception
//     */
//    public static byte[] signWithPrivateKey(final String hash, final PrivateKey key) throws Exception {
//
//        final byte[] data = hash.getBytes("UTF8");
//        final Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
//        cipher.init(Cipher.ENCRYPT_MODE, key);
//        return cipher.doFinal(data);
//    }
//
//    /**
//     * Sign with public key.
//     *
//     * @param hash the hash
//     * @param key the key
//     * @return the byte[]
//     * @throws Exception the exception
//     */
//    public static byte[] signWithPublicKey(final String hash, final PublicKey key) throws Exception {
//
//        final byte[] data = hash.getBytes("UTF8");
//        final Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
//        cipher.init(Cipher.ENCRYPT_MODE, key);
//        return cipher.doFinal(data);
//    }
//
//    /**
//     * Verify with public key key.
//     *
//     * @param hash the hash
//     * @param data the data
//     * @param key the key
//     * @return the string
//     * @throws Exception the exception
//     */
//    public static boolean verifyWithPublicKey(final String hash, final byte[] data, final PublicKey key) throws Exception {
//
//        final Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
//        cipher.init(Cipher.DECRYPT_MODE, key);
//        final byte[] decryptedData = cipher.doFinal(data);
//        final String decryptedHash = new String(decryptedData, "UTF-8");
//        return hash.equals(decryptedHash);
//    }
//
//    /**
//     * Verify with private key.
//     *
//     * @param hash the hash
//     * @param data the data
//     * @param key the key
//     * @return true, if successful
//     * @throws Exception the exception
//     */
//    public static boolean verifyWithPrivateKey(final String hash, final byte[] data, final PrivateKey key) throws Exception {
//
//        final Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
//        cipher.init(Cipher.DECRYPT_MODE, key);
//        final byte[] decryptedData = cipher.doFinal(data);
//        final String decryptedHash = new String(decryptedData, "UTF-8");
//        return hash.equals(decryptedHash);
//    }
//
//    /**
//     * Read private key.
//     *
//     * @param path the path
//     * @return the private key
//     * @throws Exception the exception
//     */
//    public static PrivateKey readPrivateRsaKey(final String path) throws Exception {
//
//        final File filePrivateKey = new File(path + "/private.key");
//        try (final FileInputStream fis = new FileInputStream(path + "/private.key")) {
//            final byte[] encodedPrivateKey = new byte[(int) filePrivateKey.length()];
//            fis.read(encodedPrivateKey);
//            final KeyFactory keyFactory = KeyFactory.getInstance("RSA");
//            final PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(encodedPrivateKey);
//            return keyFactory.generatePrivate(privateKeySpec);
//        }
//
//    }
//
//    /**
//     * Read public rsa key.
//     *
//     * @param path the path
//     * @return the public key
//     * @throws Exception the exception
//     */
//    public static PublicKey readPublicRsaKey(final String path) throws Exception {
//
//        final File filePublicKey = new File(path + "/public.key");
//        try (final FileInputStream fis = new FileInputStream(path + "/public.key")) {
//            final byte[] encodedPublicKey = new byte[(int) filePublicKey.length()];
//            fis.read(encodedPublicKey);
//
//            final KeyFactory keyFactory = KeyFactory.getInstance("RSA");
//            final X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(encodedPublicKey);
//            return keyFactory.generatePublic(publicKeySpec);
//        }
//
//    }
//
//    /**
//     * Returns the key pair.
//     *
//     * @param algorithm the algorithm, e.g. "RSA"
//     * @param length the length, e.g. 2048
//     * @return the key pair
//     * @throws NoSuchAlgorithmException the no such algorithm exception
//     */
//    public static KeyPair getKeyPair(final String algorithm, final int length) throws NoSuchAlgorithmException {
//
//        final KeyPairGenerator kpg = KeyPairGenerator.getInstance(algorithm);
//        kpg.initialize(length);
//        return kpg.genKeyPair();
//    }
//
//    /**
//     * Save key pair.
//     *
//     * @param path the path
//     * @param keyPair the key pair
//     * @throws IOException Signals that an I/O exception has occurred.
//     */
//    public static void saveKeyPair(final String path, final KeyPair keyPair) throws IOException {
//
//        final PrivateKey privateKey = keyPair.getPrivate();
//        final PublicKey publicKey = keyPair.getPublic();
//
//        // Store Public Key.
//        final X509EncodedKeySpec x509EncodedKeySpec = new X509EncodedKeySpec(publicKey.getEncoded());
//        try (final FileOutputStream fos = new FileOutputStream(path + "/public.key")) {
//            fos.write(x509EncodedKeySpec.getEncoded());
//        }
//
//        // Store Private Key.
//        final PKCS8EncodedKeySpec pkcs8EncodedKeySpec = new PKCS8EncodedKeySpec(privateKey.getEncoded());
//        try (final FileOutputStream fos = new FileOutputStream(path + "/private.key")) {
//            fos.write(pkcs8EncodedKeySpec.getEncoded());
//        }
//    }
//
//}
