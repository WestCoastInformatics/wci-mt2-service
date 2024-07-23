/*
 * Copyright 2024 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model;

import java.util.Objects;

/**
 * The Class Description.
 */

public class Description {

  public enum Field {
    ACTIVE("active"), MODULE_ID("moduleId"), RELEASED("released"), RELEASED_EFFECTIVE_TIME(
        "releasedEffectiveTime"), DESCRIPTION_ID("descriptionId"), TERM(
            "term"), CONCEPT_ID("conceptId"), TYPE_ID("typeId"), ACCEPTABILITY_MAP(
                "acceptabilityMap"), TYPE("type"), LANG(
                    "lang"), CASE_SIGNIFICANCE("caseSignificance"), EFFECTIVE_TIME("effectiveTime");

    private final String value;

    Field(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }
  }

  // Example JSON:
  // {
  // "active": true,
  // "moduleId": "900000000000207008",
  // "released": true,
  // "releasedEffectiveTime": 20170731,
  // "descriptionId": "2901894013",
  // "term": "Eccentric opening of tricuspid aortic valve",
  // "conceptId": "449123008",
  // "typeId": "900000000000013009",
  // "acceptabilityMap": {
  // "900000000000509007": "PREFERRED",
  // "900000000000508004": "PREFERRED"
  // },
  // "type": "SYNONYM",
  // "lang": "en",
  // "caseSignificance": "CASE_INSENSITIVE",
  // "effectiveTime": "20170731"
  // }

  /** The active. */
  private Boolean active;

  /** The module id. */
  private String moduleId;

  /** The released. */
  private Boolean released;

  /** The released effective time. */
  private Long releasedEffectiveTime;

  /** The description id. */
  private String descriptionId;

  /** The term. */
  private String term;

  /** The concept id. */
  private String conceptId;

  /** The type. */
  private String type;

  /** The type name. */
  private String typeName;

  /** The language */
  private String language;

  /** The language Id. */
  private String languageId;

  /** The language code. */
  private String languageCode;

  /** The language name. */
  private String languageName;

  /** The case significance. */
  private String caseSignificance;

  /** The effective time. */
  private String effectiveTime;

  /**
   * Instantiates a new description.
   */
  public Description() {
    // Do nothing
  }

  /**
   * Gets the active.
   *
   * @return the active
   */
  public Boolean getActive() {
    return active;
  }

  /**
   * Sets the active.
   *
   * @param active the new active
   */
  public void setActive(Boolean active) {
    this.active = active;
  }

  /**
   * Gets the module id.
   *
   * @return the module id
   */
  public String getModuleId() {
    return moduleId;
  }

  /**
   * Sets the module id.
   *
   * @param moduleId the new module id
   */
  public void setModuleId(String moduleId) {
    this.moduleId = moduleId;
  }

  /**
   * Gets the released.
   *
   * @return the released
   */
  public Boolean getReleased() {
    return released;
  }

  /**
   * Sets the released.
   *
   * @param released the new released
   */
  public void setReleased(Boolean released) {
    this.released = released;
  }

  /**
   * Gets the released effective time.
   *
   * @return the released effective time
   */
  public Long getReleasedEffectiveTime() {
    return releasedEffectiveTime;
  }

  /**
   * Sets the released effective time.
   *
   * @param releasedEffectiveTime the new released effective time
   */
  public void setReleasedEffectiveTime(Long releasedEffectiveTime) {
    this.releasedEffectiveTime = releasedEffectiveTime;
  }

  /**
   * Gets the description id.
   *
   * @return the description id
   */
  public String getDescriptionId() {
    return descriptionId;
  }

  /**
   * Sets the description id.
   *
   * @param descriptionId the new description id
   */
  public void setDescriptionId(String descriptionId) {
    this.descriptionId = descriptionId;
  }

  /**
   * Gets the term.
   *
   * @return the term
   */
  public String getTerm() {
    return term;
  }

  /**
   * Sets the term.
   *
   * @param term the new term
   */
  public void setTerm(String term) {
    this.term = term;
  }

  /**
   * Gets the concept id.
   *
   * @return the concept id
   */
  public String getConceptId() {
    return conceptId;
  }

  /**
   * Sets the concept id.
   *
   * @param conceptId the new concept id
   */
  public void setConceptId(String conceptId) {
    this.conceptId = conceptId;
  }

  /**
   * Gets the type.
   *
   * @return the type
   */
  public String getType() {
    return type;
  }

  /**
   * Sets the type.
   *
   * @param type the new type
   */
  public void setType(String type) {
    this.type = type;
  }

  /**
   * Gets the type name.
   *
   * @return the typeName
   */
  public String getTypeName() {
    return typeName;
  }

  /**
   * Sets the type name.
   *
   * @param typeName the new type name
   */
  public void setTypeName(String typeName) {
    this.typeName = typeName;
  }

  /**
   * Gets the language.
   *
   * @return the language
   */
  public String getLanguage() {
    return language;
  }

  /**
   * Sets the language.
   *
   * @param languageId the new language
   */
  public void setLanguage(String language) {
    this.language = language;
  }

  /**
   * Gets the language id.
   *
   * @return the language id
   */
  public String getLanguageId() {
    return languageId;
  }

  /**
   * Sets the language id.
   *
   * @param languageId the new language id
   */
  public void setLanguageId(String languageId) {
    this.languageId = languageId;
  }

  /**
   * Gets the language code.
   *
   * @return the language code
   */
  public String getLanguageCode() {
    return languageCode;
  }

  /**
   * Sets the language code.
   *
   * @param languageCode the new language code
   */
  public void setLanguageCode(String languageCode) {
    this.languageCode = languageCode;
  }

  /**
   * Gets the language name.
   *
   * @return the language name
   */
  public String getLanguageName() {
    return languageName;
  }

  /**
   * Sets the language name.
   *
   * @param languageId the new language name
   */
  public void setLanguageName(String languageName) {
    this.languageName = languageName;
  }

  /**
   * Gets the case significance.
   *
   * @return the case significance
   */
  public String getCaseSignificance() {
    return caseSignificance;
  }

  /**
   * Sets the case significance.
   *
   * @param caseSignificance the new case significance
   */
  public void setCaseSignificance(String caseSignificance) {
    this.caseSignificance = caseSignificance;
  }

  /**
   * Gets the effective time.
   *
   * @return the effective time
   */
  public String getEffectiveTime() {
    return effectiveTime;
  }

  /**
   * Sets the effective time.
   *
   * @param effectiveTime the new effective time
   */
  public void setEffectiveTime(String effectiveTime) {
    this.effectiveTime = effectiveTime;
  }

  @Override
  public int hashCode() {
    return Objects.hash(active, caseSignificance, conceptId, descriptionId, effectiveTime, language,
        languageCode, languageId, languageName, moduleId, released, releasedEffectiveTime, term,
        type, typeName);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Description other = (Description) obj;
    return Objects.equals(active, other.active)
        && Objects.equals(caseSignificance, other.caseSignificance)
        && Objects.equals(conceptId, other.conceptId)
        && Objects.equals(descriptionId, other.descriptionId)
        && Objects.equals(effectiveTime, other.effectiveTime)
        && Objects.equals(language, other.language)
        && Objects.equals(languageCode, other.languageCode)
        && Objects.equals(languageId, other.languageId)
        && Objects.equals(languageName, other.languageName)
        && Objects.equals(moduleId, other.moduleId) && Objects.equals(released, other.released)
        && Objects.equals(releasedEffectiveTime, other.releasedEffectiveTime)
        && Objects.equals(term, other.term) && Objects.equals(type, other.type)
        && Objects.equals(typeName, other.typeName);
  }

  @Override
  public String toString() {
    return "Description [active=" + active + ", moduleId=" + moduleId + ", released=" + released
        + ", releasedEffectiveTime=" + releasedEffectiveTime + ", descriptionId=" + descriptionId
        + ", term=" + term + ", conceptId=" + conceptId + ", type=" + type + ", typeName="
        + typeName + ", language=" + language + ", languageId=" + languageId + ", languageCode="
        + languageCode + ", languageName=" + languageName + ", caseSignificance=" + caseSignificance
        + ", effectiveTime=" + effectiveTime + "]";
  }

}
