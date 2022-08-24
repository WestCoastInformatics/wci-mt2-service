package org.ihtsdo.refsetservice.model;

public class ShareRefsetEmailInfo {

    private String recipient;

    private String additionalMessage;

    public String getRecipient() {

        return recipient;
    }

    public void setRecipient(String recipient) {

        this.recipient = recipient;
    }

    public String getAdditionalMessage() {

        return additionalMessage;
    }

    public void setAdditionalMessage(String additionalMessage) {

        this.additionalMessage = additionalMessage;
    }

}
