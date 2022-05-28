package org.ihtsdo.refsetservice.util;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for interacting with email.
 */
public final class EmailUtility {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(EmailUtility.class);
    
    /** The email SMTP user. */
    private static String SMPT_USER;
    
    /** The email SMTP password. */
    private static String SMPT_PASSWORD;
    
    /** The email SMTP host. */
    public static String SMPT_HOST;
    
    /** The email SMTP port. */
    public static String SMPT_PORT;
    
    /** The email to send errors to. */
    public static String ERROR_TO_EMAIL;
    
    /** The email address sent emails are from. */
    public static String EMAIL_FROM;

    /** Static initialization. */
    static {
        
        SMPT_USER = PropertyUtility.getProperty("mail.smtp.user");
        SMPT_PASSWORD = PropertyUtility.getProperty("mail.smtp.password");
        SMPT_HOST = PropertyUtility.getProperty("mail.smtp.host");
        SMPT_PORT = PropertyUtility.getProperty("mail.smtp.port");
        ERROR_TO_EMAIL = PropertyUtility.getProperty("mail.smtp.error.to");
        EMAIL_FROM = PropertyUtility.getProperty("mail.smtp.from");
    }
    
    /**
     * Instantiates an empty {@link EmailUtility}.
     */
    private EmailUtility() {
        // n/a
    }

    /**
     * Sends email.
     *
     * @param subject the subject
     * @param from the from
     * @param recipients the recipients
     * @param body the body
     * @param details the details
     * @throws Exception the exception
     */
    public static void sendEmail(final String subject, final String from, final Set<String> recipients, final String body) throws Exception {

        if (recipients == null || recipients.isEmpty()) {
            throw new Exception("Email must have recipients");
        }

        // avoid sending mail if disabled
        if ("false".equals(PropertyUtility.getProperty("mail.enabled"))) {
            return;
        }
        
        Session session = Session.getInstance(PropertyUtility.getProperties(), new Authenticator() {

            /* see superclass */
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SMPT_USER, SMPT_PASSWORD);
            }
        });

        final MimeMessage message = new MimeMessage(session);
        
        if (body.contains("<html")) {
            message.setContent(body.toString(), "text/html; charset=utf-8");
        } else {
            message.setText(body.toString());
        }
        
        message.setSubject(subject);
        message.setFrom(new InternetAddress(from));
        
        for (final String recipient : recipients) {
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(recipient));
        }
        
        Transport.send(message);
    }

    /**
     * Sends email. Convert recipients with either semi-colon or comma delimiter to List<String>.
     *
     * @param subject the subject
     * @param from the from
     * @param recipients the recipients
     * @param body the body
     * @param details the details
     * @throws Exception the exception
     */
    public static void sendEmail(final String subject, final String from, final String recipients, final String body) throws Exception {

        if (recipients != null && StringUtils.isNotBlank(recipients)) {
            
            Set<String> recipientList = new HashSet<>();
            
            if (recipients.contains(";")) {
                recipientList = FieldedStringTokenizer.splitAsSet(recipients, ";");
            } else if (recipients.contains(",")) {
                recipientList = FieldedStringTokenizer.splitAsSet(recipients, ",");
            }
            
            sendEmail(subject, from, recipientList, body);
        } else {
            throw new Exception("Email must have recipients");
        }
    }

    /**
     * SMTPAuthenticator.
     */
    public static class SMTPAuthenticator extends Authenticator {

        /**
         * Returns the password authentication.
         *
         * @return the password authentication
         */
        /* see superclass */
        @Override
        public PasswordAuthentication getPasswordAuthentication() {
            
            if (SMPT_PASSWORD == null) {
                return null;
            } else {
                return new PasswordAuthentication(SMPT_USER, SMPT_PASSWORD);
            }
        }
    }
}
