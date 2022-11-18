/*
 * Copyright 2022 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.search.engine.backend.types.Projectable;
import org.hibernate.search.engine.backend.types.Searchable;
import org.hibernate.search.engine.backend.types.Sortable;
import org.hibernate.search.mapper.pojo.automaticindexing.ReindexOnUpdate;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.GenericField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexingDependency;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.ObjectPath;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.PropertyValue;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Represents the edition information for a refset.
 */
@Entity
@Table(name = "editions")
@Indexed
public class Edition extends AbstractHasModified {

    /** The name. */
    @Column(nullable = false, length = 4000)
    private String name;

    /** The namespace. */
    @Column(nullable = true, length = 256)
    private String namespace;

    /** The short name. */
    @Column(nullable = true, length = 256)
    private String shortName;

    /** The flag icon URI. */
    @Column(nullable = true, length = 512)
    private String iconUri;

    /** The branch to use when retrieving from a terminology server. */
    @Column(nullable = true)
    private String branch;

    /** The ancestor module concept to the edition's modules. */
    @Column(nullable = true)
    private String topLevelModule;
    
    /** The modules that are part of this edition. */
    @ElementCollection
    @Fetch(FetchMode.JOIN)
    private Set<String> modules = new HashSet<String>();

    /** The default language code. */
    @Column(nullable = true, length = 256)
    private String defaultLanguageCode;

    /** The default language refsets. */
    @ElementCollection
    @Fetch(FetchMode.JOIN)
    private Set<String> defaultLanguageRefsets = new HashSet<String>();

    /** The organization. */
    @ManyToOne(targetEntity = Organization.class)
    @JoinColumn(nullable = true)
    @Fetch(FetchMode.JOIN)
    private Organization organization;

    /**
     * Instantiates an empty {@link Edition}.
     */
    public Edition() {

        // n/a
    }

    /**
     * Instantiates a {@link Edition} from the specified parameters.
     *
     * @param other the other
     */
    public Edition(final Edition other) {

        populateFrom(other);
    }

    /**
     * Instantiates a {@link Edition} from the specified parameters.
     *
     * @param name the value
     */
    public Edition(final String name) {

        this.name = name;
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final Edition other) {

        super.populateFrom(other);
        name = other.getName();
        namespace = other.getNamespace();
        defaultLanguageRefsets = other.getDefaultLanguageRefsets();
        modules = other.getModules();
        defaultLanguageCode = other.getDefaultLanguageCode();
        branch = other.getBranch();
        topLevelModule = other.getTopLevelModule();
        iconUri = other.getIconUri();
        shortName = other.getShortName();
        organization = other.getOrganization();
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "nameSort", searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getName() {

        return name;
    }

    /**
     * Sets the name.
     *
     * @param name the name
     */
    public void setName(final String name) {

        this.name = name;
    }

    /**
     * Gets the namespace.
     *
     * @return the namespace
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "namespaceSort", searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getNamespace() {

        return namespace;
    }

    /**
     * Sets the namespace.
     *
     * @param namespace the namespace
     */
    public void setNamespace(final String namespace) {

        this.namespace = namespace;
    }

    /**
     * Gets the icon uri.
     *
     * @return the iconUri
     */
    public String getIconUri() {

        return iconUri;
    }

    /**
     * Sets the icon uri.
     *
     * @param iconUri the iconUri to set
     */
    public void setIconUri(final String iconUri) {

        this.iconUri = iconUri;
    }

    /**
     * Gets the branch.
     *
     * @return the branch
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "branchSort", searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getBranch() {

        return branch;
    }

    /**
     * Sets the branch.
     *
     * @param branch the branch to set
     */
    public void setBranch(final String branch) {

        this.branch = branch;
    }

    /**
     * Gets the top level module.
     *
     * @return the top level module
     */
    public String getTopLevelModule() {

        return topLevelModule;
    }

    /**
     * Gets the abbreviation version of the name.
     *
     * @return the abbreviation version of the name
     */
    @JsonGetter()
    public String getAbbreviation() {

        String abbreviation = "main";

        if (!shortName.equals("SNOMEDCT")) {

            abbreviation = shortName.replaceFirst("SNOMEDCT-?", "").toLowerCase();
        }

        return abbreviation;
    }

    /**
     * Sets the abbreviation version of the name.
     *
     * @param abbreviation the abbreviation version of the name to set
     */
    public void setAbbreviation(final String abbreviation) {

        // N/A
    }

    /**
     * Sets the top level module.
     *
     * @param topLevelModule the top level module to set
     */
    public void setTopLevelModule(final String topLevelModule) {

        this.topLevelModule = topLevelModule;
    }

    /**
     * Gets the modules that are part of this edition.
     *
     * @return the modules
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    // @IndexedEmbedded
    public Set<String> getModules() {

        if (modules == null) {

            modules = new HashSet<>();
        }

        return modules;
    }
    

    /**
     * Sets the modules that are part of this edition.
     *
     * @param modules the set of modules IDs
     */
    public void setModules(final Set<String> modules) {

        this.modules = modules;
    }
    
    /**
     * Gets the default language refsets.
     *
     * @return the default language refsets
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    // @IndexedEmbedded
    public Set<String> getDefaultLanguageRefsets() {
        
        if (defaultLanguageRefsets == null) {
            
            defaultLanguageRefsets = new HashSet<>();
        }
        
        return defaultLanguageRefsets;
    }

    /**
     * Gets the default language refsets qualified with the language code and types.
     *
     * @return the default language refsets qualified with the language code and types.
     */
    public List<Map<String, String>> getFullyQualifiedLanguageRefsets() {

        final Map<String, String> refsetToLanguagesMap = RefsetMemberService.getRefsetToLanguagesMap();
        final List<Map<String, String>> qualifiedLanguageList = new ArrayList<>();

        for (final String languageRefsetCode : getDefaultLanguageRefsets()) {

            final String languageCode = refsetToLanguagesMap.get(languageRefsetCode);

            if (languageCode == null) {

                continue;
            }

            Map<String, String> languageDetails = new HashMap<>();
            languageDetails.put("languageRefset", languageRefsetCode);
            languageDetails.put("languageCode", languageCode);
            languageDetails.put("qualifiedLanguageRefset", languageRefsetCode + "PT");
            languageDetails.put("qualifiedLanguageCode", languageCode.toUpperCase() + " (PT)");

            // if this is the default language code make sure it is first and
            // add a FSN version
            if (languageCode.equalsIgnoreCase(defaultLanguageCode) || languageCode.equalsIgnoreCase("en")) {

                if (languageCode.equalsIgnoreCase(defaultLanguageCode)) {

                    languageDetails.put("default", "true");
                }

                qualifiedLanguageList.add(0, languageDetails);

                if (languageCode.equals("en")) {

                    qualifiedLanguageList.add(1, Map.of("languageRefset", languageRefsetCode, "languageCode", languageCode, "qualifiedLanguageRefset", languageRefsetCode + "FSN",
                        "qualifiedLanguageCode", languageCode.toUpperCase() + " (FSN)"));
                }

            } else {

                qualifiedLanguageList.add(languageDetails);

            }

        }

        return qualifiedLanguageList;
    }

    /**
     * This is solely for bean validation, method does nothing.
     *
     * @param qualifiedLanguageList the qualified language list
     */
    public void setFullyQualifiedLanguageRefsets(final List<Map<String, String>> qualifiedLanguageList) {

        /* NA */
    }

    /**
     * Sets the default language refsets.
     *
     * @param defaultLanguageRefsets the set of default language refset Ids
     */
    public void setDefaultLanguageRefsets(final Set<String> defaultLanguageRefsets) {

        this.defaultLanguageRefsets = defaultLanguageRefsets;
    }

    /**
     * Gets the default language code.
     *
     * @return the default language code
     */
    public String getDefaultLanguageCode() {

        return defaultLanguageCode;
    }

    /**
     * Sets the default language code.
     *
     * @param defaultLanguageCode the set of default language refset Ids
     */
    public void setDefaultLanguageCode(final String defaultLanguageCode) {

        this.defaultLanguageCode = defaultLanguageCode;
    }

    /**
     * Gets the short name.
     *
     * @return the country
     */
    @FullTextField(analyzer = "standard")
    @GenericField(name = "shortNameSort", searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.YES)
    public String getShortName() {

        return shortName;
    }

    /**
     * Sets the short name.
     *
     * @param shortName the new short name
     */
    public void setShortName(final String shortName) {

        this.shortName = shortName;
    }

    /**
     * Gets the organization.
     *
     * @return the organization
     */
    @JsonSerialize(contentAs = Organization.class)
    @JsonDeserialize(contentAs = Organization.class)
    public Organization getOrganization() {

        return organization;
    }

    /**
     * Sets the organization.
     *
     * @param edition the organization to set
     */
    public void setOrganization(final Organization organization) {

        this.organization = organization;
    }

    /**
     * Returns the organization ID.
     *
     * @return the organization ID
     * @throws Exception
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    @IndexingDependency(derivedFrom = @ObjectPath({
        @PropertyValue(propertyName = "organization")
    }))
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW)
    public String getOrganizationId() throws Exception {

        if (organization != null) {
            return organization.getId();
        } else {
            return null;
        }
    }

    /**
     * Sets the organization ID.
     *
     * @param organizationId the organization ID to set
     */
    public void setOrganizationId(final String organizationId) {

        if (organization != null) {

            this.organization.setId(organizationId);
        } else {

            this.organization = new Organization();
            this.organization.setId(organizationId);
        }
    }
    
    /**
     * Returns the organization name.
     *
     * @return the organization name
     * @throws Exception
     */
    @GenericField(searchable = Searchable.YES, projectable = Projectable.NO, sortable = Sortable.NO)
    @IndexingDependency(derivedFrom = @ObjectPath({
        @PropertyValue(propertyName = "organization")
    }))
    @IndexingDependency(reindexOnUpdate = ReindexOnUpdate.SHALLOW)
    public String getOrganizationName() throws Exception {

        if (organization != null) {
            return organization.getName();
        } else {
            return null;
        }
    }

    /**
     * Sets the organization name.
     *
     * @param organizationName the organization name to set
     */
    public void setOrganizationName(final String organizationName) {

        if (organization != null) {

            this.organization.setName(organizationName);
        } else {

            this.organization = new Organization();
            this.organization.setName(organizationName);
        }
    }

    /**
     * Hash code.
     *
     * @return the int
     */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((namespace == null) ? 0 : namespace.hashCode());
        result = prime * result + ((branch == null) ? 0 : branch.hashCode());
        result = prime * result + ((topLevelModule == null) ? 0 : topLevelModule.hashCode());
        result = prime * result + ((iconUri == null) ? 0 : iconUri.hashCode());
        result = prime * result + ((modules == null) ? 0 : modules.hashCode());
        result = prime * result + ((defaultLanguageRefsets == null) ? 0 : defaultLanguageRefsets.hashCode());
        result = prime * result + ((defaultLanguageCode == null) ? 0 : defaultLanguageCode.hashCode());
        result = prime * result + ((shortName == null) ? 0 : shortName.hashCode());
        result = prime * result + ((organization == null) ? 0 : organization.hashCode());
        return result;
    }

    /**
     * Equals.
     *
     * @param obj the obj
     * @return true, if successful
     */
    @Override
    public boolean equals(final Object obj) {

        if (this == obj) {

            return true;
        }

        if (obj == null) {

            return false;
        }

        if (getClass() != obj.getClass()) {

            return false;
        }

        final Edition other = (Edition) obj;

        if (name == null) {

            if (other.name != null) {

                return false;
            }

        } else if (!name.equals(other.name)) {

            return false;
        }

        if (namespace == null) {

            if (other.namespace != null) {

                return false;
            }

        } else if (!namespace.equals(other.namespace)) {

            return false;
        }

        if (branch == null) {

            if (other.branch != null) {

                return false;
            }

        } else if (!branch.equals(other.branch)) {

            return false;
        }

        if (topLevelModule == null) {

            if (other.topLevelModule != null) {

                return false;
            }

        } else if (!topLevelModule.equals(other.topLevelModule)) {

            return false;
        }

        if (iconUri == null) {

            if (other.iconUri != null) {

                return false;
            }

        } else if (!iconUri.equals(other.iconUri)) {

            return false;
        }

        if (modules == null) {

            if (other.modules != null) {

                return false;
            }

        } else if (!modules.equals(other.modules)) {

            return false;
        }
        
        if (defaultLanguageRefsets == null) {
            
            if (other.defaultLanguageRefsets != null) {
                
                return false;
            }
            
        } else if (!defaultLanguageRefsets.equals(other.defaultLanguageRefsets)) {
            
            return false;
        }

        if (defaultLanguageCode == null) {

            if (other.defaultLanguageCode != null) {

                return false;
            }

        } else if (!defaultLanguageCode.equals(other.defaultLanguageCode)) {

            return false;
        }

        if (shortName == null) {

            if (other.shortName != null) {

                return false;
            }

        } else if (!shortName.equals(other.shortName)) {

            return false;
        }

        if (organization == null) {

            if (other.organization != null) {

                return false;
            }

        } else if (!organization.equals(other.organization)) {

            return false;
        }

        return true;
    }

    /**
     * Lazy init.
     */
    @Override
    public void lazyInit() {
        // TODO Auto-generated method stub

    }
}
