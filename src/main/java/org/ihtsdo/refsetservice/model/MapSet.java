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

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The Class MapSet.
 */
@Entity
@Schema(description = "Represents a map set")
@Table(name = "map_sets", uniqueConstraints = {
    @UniqueConstraint(columnNames = {
        "name"
    })
})
@Indexed
@JsonIgnoreProperties(ignoreUnknown = true)
public class MapSet extends AbstractHasModified {

  /** The ref set code. */
  private String refSetCode;

  /** The ref set name. */
  private String refSetName;

  /** The module id. */
  @Column(nullable = true)
  private String moduleId;

  /** The name. */
  @Column(nullable = false)
  private String name;

  /** The version status. */
  @Column(nullable = false, length = 256)
  private String versionStatus;

  /** The branch path. */
  @Column(nullable = false)
  private String branchPath;

  /** The version. */
  @Column(nullable = false)
  private String version;

  /** The branch path. */
  @Column(nullable = false)
  private String fromTerminology;

  /** The from version. */
  @Column(nullable = false)
  private String fromVersion;

  /** The from branch path. */
  @Column(nullable = false)
  private String fromBranchPath;

  /** The to terminology. */
  @Column(nullable = false)
  private String toTerminology;

  /** The to version. */
  @Column(nullable = false)
  private String toVersion;

  /** The to branch path. */
  @Column(nullable = false)
  private String toBranchPath;

  /**
   * Default constructor.
   */
  public MapSet() {

    // n/a
  }

  /**
   * Instantiates a {@link MapSet} from the specified parameters.
   *
   * @param mapSet the map set
   */
  public MapSet(final MapSet mapSet) {

    super();
    super.setId(mapSet.getId());
    this.refSetCode = mapSet.getRefSetCode();
    this.refSetName = mapSet.getRefSetName();
    this.moduleId = mapSet.getModuleId();
    this.name = mapSet.getName();
    this.branchPath = mapSet.getBranchPath();
    this.fromTerminology = mapSet.getFromTerminology();
    this.fromVersion = mapSet.getFromVersion();
    this.fromBranchPath = mapSet.getFromBranchPath();
    this.toTerminology = mapSet.getToTerminology();
    this.toVersion = mapSet.getToVersion();
    this.toBranchPath = mapSet.getToBranchPath();
  }

  /**
   * Gets the name.
   *
   * @return the name
   */
  @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
  public String getName() {

    return name;
  }

  /**
   * Sets the name.
   *
   * @param name the new name
   */
  public void setName(final String name) {

    this.name = name;
  }

  /**
   * Returns the version status.
   *
   * @return the version status
   */
  @FullTextField(analyzer = "standard")
  @GenericField(name = "versionStatusSort", searchable = Searchable.YES,
      projectable = Projectable.NO, sortable = Sortable.YES)
  public String getVersionStatus() {

    return versionStatus;
  }

  /**
   * Sets the version.
   *
   * @param versionStatus the version status
   */
  public void setVersionStatus(final String versionStatus) {

    this.versionStatus = versionStatus;
  }

  /**
   * Returns the version.
   *
   * @return the version
   */
  public String getVersion() {

    return version;
  }

  /**
   * Sets the version.
   *
   * @param version the version
   */
  public void setVersion(final String version) {

    this.version = version;
  }

  /**
   * Gets the ref set name.
   *
   * @return the ref set name
   */
  public String getRefSetName() {

    return this.refSetName;
  }

  /**
   * Sets the ref set name.
   *
   * @param refSetName the new ref set name
   */
  public void setRefSetName(final String refSetName) {

    this.refSetName = refSetName;

  }

  /**
   * Returns the ref set code.
   *
   * @return the ref set code
   */
  @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
  public String getRefSetCode() {

    return refSetCode;
  }

  /**
   * Sets the ref set code.
   *
   * @param refSetCode the ref set code
   */
  public void setRefSetCode(final String refSetCode) {

    this.refSetCode = refSetCode;
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
  public void setModuleId(final String moduleId) {

    this.moduleId = moduleId;
  }

  /**
   * Gets the branch path.
   *
   * @return the branch path
   */
  public String getBranchPath() {

    return branchPath;
  }

  /**
   * Sets the branch path.
   *
   * @param branchPath the new branch path
   */
  public void setBranchPath(final String branchPath) {

    this.branchPath = branchPath;
  }

  /**
   * Gets the from terminology.
   *
   * @return the from terminology
   */
  public String getFromTerminology() {

    return fromTerminology;
  }

  /**
   * Sets the from terminology.
   *
   * @param fromTerminology the new from terminology
   */
  public void setFromTerminology(final String fromTerminology) {

    this.fromTerminology = fromTerminology;
  }

  /**
   * Gets the from version.
   *
   * @return the from version
   */
  public String getFromVersion() {

    return fromVersion;
  }

  /**
   * Sets the from version.
   *
   * @param fromVersion the new from version
   */
  public void setFromVersion(final String fromVersion) {

    this.fromVersion = fromVersion;
  }

  /**
   * Gets the from branch path.
   *
   * @return the from branch path
   */
  public String getFromBranchPath() {

    return fromBranchPath;
  }

  /**
   * Sets the from branch path.
   *
   * @param fromBranchPath the new from branch path
   */
  public void setFromBranchPath(final String fromBranchPath) {

    this.fromBranchPath = fromBranchPath;
  }

  /**
   * Gets the to terminology.
   *
   * @return the to terminology
   */
  public String getToTerminology() {

    return toTerminology;
  }

  /**
   * Sets the to terminology.
   *
   * @param toTerminology the new to terminology
   */
  public void setToTerminology(final String toTerminology) {

    this.toTerminology = toTerminology;
  }

  /**
   * Gets the to version.
   *
   * @return the to version
   */
  public String getToVersion() {

    return toVersion;
  }

  /**
   * Sets the to version.
   *
   * @param toVersion the new to version
   */
  public void setToVersion(final String toVersion) {

    this.toVersion = toVersion;
  }

  /**
   * Gets the to branch path.
   *
   * @return the to branch path
   */
  public String getToBranchPath() {

    return toBranchPath;
  }

  /**
   * Sets the to branch path.
   *
   * @param toBranchPath the new to branch path
   */
  public void setToBranchPath(final String toBranchPath) {

    this.toBranchPath = toBranchPath;
  }

  /**
   * Lazy init.
   */
  @Override
  public void lazyInit() {

    // n/a
  }

}
