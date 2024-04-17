/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model;

/**
 * The Class SendCommunicationEmailInfo.
 */
public class SendCommunicationEmailInfo {

  /** The recipient. */
  private String recipient;

  /** The additional message. */
  private String additionalMessage;

  /**
   * Returns the recipient.
   *
   * @return the recipient
   */
  public String getRecipient() {

    return recipient;
  }

  /**
   * Sets the recipient.
   *
   * @param recipient the recipient
   */
  public void setRecipient(final String recipient) {

    this.recipient = recipient;
  }

  /**
   * Returns the additional message.
   *
   * @return the additional message
   */
  public String getAdditionalMessage() {

    return additionalMessage;
  }

  /**
   * Sets the additional message.
   *
   * @param additionalMessage the additional message
   */
  public void setAdditionalMessage(final String additionalMessage) {

    this.additionalMessage = additionalMessage;
  }

}
