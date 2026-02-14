/*
 * Copyright 2024 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.model.snowstorm;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The Class CodeSystem.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodeSystem {

    /** The name. */
    @JsonProperty("name")
    private String name;

    /** The owner. */
    @JsonProperty("owner")
    private String owner;

    /** The short name. */
    @JsonProperty("shortName")
    private String shortName;

    /** The branch path. */
    @JsonProperty("branchPath")
    private String branchPath;

    /** The dependant version effective time. */
    @JsonProperty("dependantVersionEffectiveTime")
    private int dependantVersionEffectiveTime;

    /** The daily build available. */
    @JsonProperty("dailyBuildAvailable")
    private boolean dailyBuildAvailable;

    /** The country code. */
    @JsonProperty("countryCode")
    private String countryCode;

    /** The default language code. */
    @JsonProperty("defaultLanguageCode")
    private String defaultLanguageCode;

    /** The default language reference sets. */
    @JsonProperty("defaultLanguageReferenceSets")
    private List<String> defaultLanguageReferenceSets;

    /** The maintainer type. */
    @JsonProperty("maintainerType")
    private String maintainerType;

    /** The latest version. */
    @JsonProperty("latestVersion")
    private LatestVersion latestVersion;

    /** The languages. */
    @JsonProperty("languages")
    private Map<String, String> languages;

    /** The modules. */
    @JsonProperty("modules")
    private List<Module> modules;

    /** The user roles. */
    @JsonProperty("userRoles")
    private List<String> userRoles;

    /**
     * Instantiates an empty {@link CodeSystem}.
     */
    public CodeSystem() {

        super();
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String getName() {

        return name;
    }

    /**
     * Sets the name.
     *
     * @param name the name to set
     */
    public void setName(final String name) {

        this.name = name;
    }

    /**
     * Returns the owner.
     *
     * @return the owner
     */
    public String getOwner() {

        return owner;
    }

    /**
     * Sets the owner.
     *
     * @param owner the owner to set
     */
    public void setOwner(final String owner) {

        this.owner = owner;
    }

    /**
     * Returns the short name.
     *
     * @return the shortName
     */
    public String getShortName() {

        return shortName;
    }

    /**
     * Sets the short name.
     *
     * @param shortName the shortName to set
     */
    public void setShortName(final String shortName) {

        this.shortName = shortName;
    }

    /**
     * Returns the branch path.
     *
     * @return the branchPath
     */
    public String getBranchPath() {

        return branchPath;
    }

    /**
     * Sets the branch path.
     *
     * @param branchPath the branchPath to set
     */
    public void setBranchPath(final String branchPath) {

        this.branchPath = branchPath;
    }

    /**
     * Returns the dependant version effective time.
     *
     * @return the dependantVersionEffectiveTime
     */
    public int getDependantVersionEffectiveTime() {

        return dependantVersionEffectiveTime;
    }

    /**
     * Sets the dependant version effective time.
     *
     * @param dependantVersionEffectiveTime the dependantVersionEffectiveTime to set
     */
    public void setDependantVersionEffectiveTime(final int dependantVersionEffectiveTime) {

        this.dependantVersionEffectiveTime = dependantVersionEffectiveTime;
    }

    /**
     * Indicates whether or not daily build available is the case.
     *
     * @return the dailyBuildAvailable
     */
    public boolean isDailyBuildAvailable() {

        return dailyBuildAvailable;
    }

    /**
     * Sets the daily build available.
     *
     * @param dailyBuildAvailable the dailyBuildAvailable to set
     */
    public void setDailyBuildAvailable(final boolean dailyBuildAvailable) {

        this.dailyBuildAvailable = dailyBuildAvailable;
    }

    /**
     * Returns the country code.
     *
     * @return the countryCode
     */
    public String getCountryCode() {

        return countryCode;
    }

    /**
     * Sets the country code.
     *
     * @param countryCode the countryCode to set
     */
    public void setCountryCode(final String countryCode) {

        this.countryCode = countryCode;
    }

    /**
     * Returns the default language code.
     *
     * @return the defaultLanguageCode
     */
    public String getDefaultLanguageCode() {

        return defaultLanguageCode;
    }

    /**
     * Sets the default language code.
     *
     * @param defaultLanguageCode the defaultLanguageCode to set
     */
    public void setDefaultLanguageCode(final String defaultLanguageCode) {

        this.defaultLanguageCode = defaultLanguageCode;
    }

    /**
     * Returns the default language reference sets.
     *
     * @return the defaultLanguageReferenceSets
     */
    public List<String> getDefaultLanguageReferenceSets() {

        return defaultLanguageReferenceSets;
    }

    /**
     * Sets the default language reference sets.
     *
     * @param defaultLanguageReferenceSets the defaultLanguageReferenceSets to set
     */
    public void setDefaultLanguageReferenceSets(final List<String> defaultLanguageReferenceSets) {

        this.defaultLanguageReferenceSets = defaultLanguageReferenceSets;
    }

    /**
     * Returns the maintainer type.
     *
     * @return the maintainerType
     */
    public String getMaintainerType() {

        return maintainerType;
    }

    /**
     * Sets the maintainer type.
     *
     * @param maintainerType the maintainerType to set
     */
    public void setMaintainerType(final String maintainerType) {

        this.maintainerType = maintainerType;
    }

    /**
     * Returns the latest version.
     *
     * @return the latestVersion
     */
    public LatestVersion getLatestVersion() {

        return latestVersion;
    }

    /**
     * Sets the latest version.
     *
     * @param latestVersion the latestVersion to set
     */
    public void setLatestVersion(final LatestVersion latestVersion) {

        this.latestVersion = latestVersion;
    }

    /**
     * Returns the languages.
     *
     * @return the languages
     */
    public Map<String, String> getLanguages() {

        return languages;
    }

    /**
     * Sets the languages.
     *
     * @param languages the languages to set
     */
    public void setLanguages(final Map<String, String> languages) {

        this.languages = languages;
    }

    /**
     * Returns the modules.
     *
     * @return the modules
     */
    public List<Module> getModules() {

        return modules;
    }

    /**
     * Sets the modules.
     *
     * @param modules the modules to set
     */
    public void setModules(final List<Module> modules) {

        this.modules = modules;
    }

    /**
     * Returns the user roles.
     *
     * @return the userRoles
     */
    public List<String> getUserRoles() {

        return userRoles;
    }

    /**
     * Sets the user roles.
     *
     * @param userRoles the userRoles to set
     */
    public void setUserRoles(final List<String> userRoles) {

        this.userRoles = userRoles;
    }

    /**
     * The Class LatestVersion.
     */
    // Nested class for LatestVersion
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LatestVersion {

        /** The short name. */
        @JsonProperty("shortName")
        private String shortName;

        /** The import date. */
        @JsonProperty("importDate")
        private String importDate;

        /** The parent branch path. */
        @JsonProperty("parentBranchPath")
        private String parentBranchPath;

        /** The effective date. */
        @JsonProperty("effectiveDate")
        private int effectiveDate;

        /** The version. */
        @JsonProperty("version")
        private String version;

        /** The description. */
        @JsonProperty("description")
        private String description;

        @JsonProperty("releasePackage")
        private String releasePackage;

        /** The dependant version effective time. */
        @JsonProperty("dependantVersionEffectiveTime")
        private int dependantVersionEffectiveTime;

        /** The branch path. */
        @JsonProperty("branchPath")
        private String branchPath;

        /**
         * Instantiates an empty {@link LatestVersion}.
         */
        public LatestVersion() {

            super();
        }

        /**
         * Returns the short name.
         *
         * @return the shortName
         */
        public String getShortName() {

            return shortName;
        }

        /**
         * Sets the short name.
         *
         * @param shortName the shortName to set
         */
        public void setShortName(final String shortName) {

            this.shortName = shortName;
        }

        /**
         * Returns the import date.
         *
         * @return the importDate
         */
        public String getImportDate() {

            return importDate;
        }

        /**
         * Sets the import date.
         *
         * @param importDate the importDate to set
         */
        public void setImportDate(final String importDate) {

            this.importDate = importDate;
        }

        /**
         * Returns the parent branch path.
         *
         * @return the parentBranchPath
         */
        public String getParentBranchPath() {

            return parentBranchPath;
        }

        /**
         * Sets the parent branch path.
         *
         * @param parentBranchPath the parentBranchPath to set
         */
        public void setParentBranchPath(final String parentBranchPath) {

            this.parentBranchPath = parentBranchPath;
        }

        /**
         * Returns the effective date.
         *
         * @return the effectiveDate
         */
        public int getEffectiveDate() {

            return effectiveDate;
        }

        /**
         * Sets the effective date.
         *
         * @param effectiveDate the effectiveDate to set
         */
        public void setEffectiveDate(final int effectiveDate) {

            this.effectiveDate = effectiveDate;
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
         * @param version the version to set
         */
        public void setVersion(final String version) {

            this.version = version;
        }

        /**
         * Returns the releasePackage.
         *
         * @return the releasePackage
         */
        public String getReleasePackage() {

            return releasePackage;
        }

        /**
         * Sets the releasePackage.
         *
         * @param releasePackage the releasePackage to set
         */
        public void setReleasePackage(final String releasePackage) {

            this.releasePackage = releasePackage;
        }

        /**
         * Returns the description.
         *
         * @return the description
         */
        public String getDescription() {

            return description;
        }

        /**
         * Sets the description.
         *
         * @param description the description to set
         */
        public void setDescription(final String description) {

            this.description = description;
        }

        /**
         * Returns the dependant version effective time.
         *
         * @return the dependantVersionEffectiveTime
         */
        public int getDependantVersionEffectiveTime() {

            return dependantVersionEffectiveTime;
        }

        /**
         * Sets the dependant version effective time.
         *
         * @param dependantVersionEffectiveTime the dependantVersionEffectiveTime to set
         */
        public void setDependantVersionEffectiveTime(final int dependantVersionEffectiveTime) {

            this.dependantVersionEffectiveTime = dependantVersionEffectiveTime;
        }

        /**
         * Returns the branch path.
         *
         * @return the branchPath
         */
        public String getBranchPath() {

            return branchPath;
        }

        /**
         * Sets the branch path.
         *
         * @param branchPath the branchPath to set
         */
        public void setBranchPath(final String branchPath) {

            this.branchPath = branchPath;
        }

        /* see superclass */
        @Override
        public String toString() {

            return "LatestVersion [shortName=" + shortName + ", importDate=" + importDate + ", parentBranchPath=" + parentBranchPath + ", effectiveDate="
                + effectiveDate + ", version=" + version + ", description=" + description + ", releasePackage=" + releasePackage
                + ", dependantVersionEffectiveTime=" + dependantVersionEffectiveTime + ", branchPath=" + branchPath + "]";
        }

    }

    /**
     * The Class Module.
     */
    // Nested class for Module
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Module {

        /** The concept id. */
        @JsonProperty("conceptId")
        private String conceptId;

        /** The active. */
        @JsonProperty("active")
        private boolean active;

        /** The definition status. */
        @JsonProperty("definitionStatus")
        private String definitionStatus;

        /** The module id. */
        @JsonProperty("moduleId")
        private String moduleId;

        /** The effective time. */
        @JsonProperty("effectiveTime")
        private String effectiveTime;

        /** The fsn. */
        @JsonProperty("fsn")
        private FullySpecifiedName fullySpecifiedName;

        /** The pt. */
        @JsonProperty("pt")
        private PerferredTerm perferredTerm;

        /** The id. */
        @JsonProperty("id")
        private String id;

        /** The id and fsn term. */
        @JsonProperty("idAndFsnTerm")
        private String idAndFsnTerm;

        /**
         * Instantiates an empty {@link Module}.
         */
        public Module() {

            super();
        }

        /**
         * Returns the concept id.
         *
         * @return the conceptId
         */
        public String getConceptId() {

            return conceptId;
        }

        /**
         * Sets the concept id.
         *
         * @param conceptId the conceptId to set
         */
        public void setConceptId(final String conceptId) {

            this.conceptId = conceptId;
        }

        /**
         * Indicates whether or not active is the case.
         *
         * @return the active
         */
        public boolean isActive() {

            return active;
        }

        /**
         * Sets the active.
         *
         * @param active the active to set
         */
        public void setActive(final boolean active) {

            this.active = active;
        }

        /**
         * Returns the definition status.
         *
         * @return the definitionStatus
         */
        public String getDefinitionStatus() {

            return definitionStatus;
        }

        /**
         * Sets the definition status.
         *
         * @param definitionStatus the definitionStatus to set
         */
        public void setDefinitionStatus(final String definitionStatus) {

            this.definitionStatus = definitionStatus;
        }

        /**
         * Returns the module id.
         *
         * @return the moduleId
         */
        public String getModuleId() {

            return moduleId;
        }

        /**
         * Sets the module id.
         *
         * @param moduleId the moduleId to set
         */
        public void setModuleId(final String moduleId) {

            this.moduleId = moduleId;
        }

        /**
         * Returns the effective time.
         *
         * @return the effectiveTime
         */
        public String getEffectiveTime() {

            return effectiveTime;
        }

        /**
         * Sets the effective time.
         *
         * @param effectiveTime the effectiveTime to set
         */
        public void setEffectiveTime(final String effectiveTime) {

            this.effectiveTime = effectiveTime;
        }

        /**
         * Returns the fullySpecifiedName.
         *
         * @return the fullySpecifiedName
         */
        public FullySpecifiedName getFullySpecifiedName() {

            return fullySpecifiedName;
        }

        /**
         * Sets the fullySpecifiedName.
         *
         * @param fullySpecifiedName the fullySpecifiedName to set
         */
        public void setFullySpecifiedName(final FullySpecifiedName fullySpecifiedName) {

            this.fullySpecifiedName = fullySpecifiedName;
        }

        /**
         * Returns the perferredTerm.
         *
         * @return the perferredTerm
         */
        public PerferredTerm getPerferredTerm() {

            return perferredTerm;
        }

        /**
         * Sets the perferredTerm.
         *
         * @param perferredTerm the perferredTerm to set
         */
        public void setPerferredTerm(final PerferredTerm perferredTerm) {

            this.perferredTerm = perferredTerm;
        }

        /**
         * Returns the id.
         *
         * @return the id
         */
        public String getId() {

            return id;
        }

        /**
         * Sets the id.
         *
         * @param id the id to set
         */
        public void setId(final String id) {

            this.id = id;
        }

        /**
         * Returns the id and fsn term.
         *
         * @return the idAndFsnTerm
         */
        public String getIdAndFsnTerm() {

            return idAndFsnTerm;
        }

        /**
         * Sets the id and fsn term.
         *
         * @param idAndFsnTerm the idAndFsnTerm to set
         */
        public void setIdAndFsnTerm(final String idAndFsnTerm) {

            this.idAndFsnTerm = idAndFsnTerm;
        }

        /**
         * The Class FullySpecifiedName.
         */
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class FullySpecifiedName {

            /** The term. */
            @JsonProperty("term")
            private String term;

            /** The lang. */
            @JsonProperty("lang")
            private String lang;

            /**
             * Instantiates an empty {@link FullySpecifiedName}.
             */
            public FullySpecifiedName() {

                super();
            }

            /**
             * Returns the term.
             *
             * @return the term
             */
            public String getTerm() {

                return term;
            }

            /**
             * Sets the term.
             *
             * @param term the term to set
             */
            public void setTerm(final String term) {

                this.term = term;
            }

            /**
             * Returns the lang.
             *
             * @return the lang
             */
            public String getLang() {

                return lang;
            }

            /**
             * Sets the lang.
             *
             * @param lang the lang to set
             */
            public void setLang(final String lang) {

                this.lang = lang;
            }

            /* see superclass */
            @Override
            public String toString() {

                return "FullySpecifiedName [term=" + term + ", lang=" + lang + "]";
            }

        }

        /**
         * The Class Pt.
         */
        // Nested class for PerferredTerm
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class PerferredTerm {

            /** The term. */
            @JsonProperty("term")
            private String term;

            /** The lang. */
            @JsonProperty("lang")
            private String lang;

            /**
             * Instantiates an empty {@link PerferredTerm}.
             */
            public PerferredTerm() {

                super();
            }

            /**
             * Returns the term.
             *
             * @return the term
             */
            public String getTerm() {

                return term;
            }

            /**
             * Sets the term.
             *
             * @param term the term to set
             */
            public void setTerm(final String term) {

                this.term = term;
            }

            /**
             * Returns the lang.
             *
             * @return the lang
             */
            public String getLang() {

                return lang;
            }

            /**
             * Sets the lang.
             *
             * @param lang the lang to set
             */
            public void setLang(final String lang) {

                this.lang = lang;
            }

            /* see superclass */
            @Override
            public String toString() {

                return "Pt [term=" + term + ", lang=" + lang + "]";
            }

        }

        /* see superclass */
        @Override
        public String toString() {

            return "Module [conceptId=" + conceptId + ", active=" + active + ", definitionStatus=" + definitionStatus + ", moduleId=" + moduleId
                + ", effectiveTime=" + effectiveTime + ", fullySpecifiedName=" + fullySpecifiedName + ", perferredTerm=" + perferredTerm + ", id=" + id
                + ", idAndFsnTerm=" + idAndFsnTerm + "]";
        }

    }

    /* see superclass */
    @Override
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((branchPath == null) ? 0 : branchPath.hashCode());
        result = prime * result + ((countryCode == null) ? 0 : countryCode.hashCode());
        result = prime * result + (dailyBuildAvailable ? 1231 : 1237);
        result = prime * result + ((defaultLanguageCode == null) ? 0 : defaultLanguageCode.hashCode());
        result = prime * result + ((defaultLanguageReferenceSets == null) ? 0 : defaultLanguageReferenceSets.hashCode());
        result = prime * result + dependantVersionEffectiveTime;
        result = prime * result + ((languages == null) ? 0 : languages.hashCode());
        result = prime * result + ((latestVersion == null) ? 0 : latestVersion.hashCode());
        result = prime * result + ((maintainerType == null) ? 0 : maintainerType.hashCode());
        result = prime * result + ((modules == null) ? 0 : modules.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        result = prime * result + ((owner == null) ? 0 : owner.hashCode());
        result = prime * result + ((shortName == null) ? 0 : shortName.hashCode());
        result = prime * result + ((userRoles == null) ? 0 : userRoles.hashCode());
        return result;
    }

    /* see superclass */
    @Override
    public boolean equals(final Object obj) {

        if (this == obj) {
            return true;
        }
        if (!(obj instanceof CodeSystem)) {
            return false;
        }
        final CodeSystem other = (CodeSystem) obj;
        if (branchPath == null) {
            if (other.branchPath != null) {
                return false;
            }
        } else if (!branchPath.equals(other.branchPath)) {
            return false;
        }
        if (countryCode == null) {
            if (other.countryCode != null) {
                return false;
            }
        } else if (!countryCode.equals(other.countryCode)) {
            return false;
        }
        if (dailyBuildAvailable != other.dailyBuildAvailable) {
            return false;
        }
        if (defaultLanguageCode == null) {
            if (other.defaultLanguageCode != null) {
                return false;
            }
        } else if (!defaultLanguageCode.equals(other.defaultLanguageCode)) {
            return false;
        }
        if (defaultLanguageReferenceSets == null) {
            if (other.defaultLanguageReferenceSets != null) {
                return false;
            }
        } else if (!defaultLanguageReferenceSets.equals(other.defaultLanguageReferenceSets)) {
            return false;
        }
        if (dependantVersionEffectiveTime != other.dependantVersionEffectiveTime) {
            return false;
        }
        if (languages == null) {
            if (other.languages != null) {
                return false;
            }
        } else if (!languages.equals(other.languages)) {
            return false;
        }
        if (latestVersion == null) {
            if (other.latestVersion != null) {
                return false;
            }
        } else if (!latestVersion.equals(other.latestVersion)) {
            return false;
        }
        if (maintainerType == null) {
            if (other.maintainerType != null) {
                return false;
            }
        } else if (!maintainerType.equals(other.maintainerType)) {
            return false;
        }
        if (modules == null) {
            if (other.modules != null) {
                return false;
            }
        } else if (!modules.equals(other.modules)) {
            return false;
        }
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (owner == null) {
            if (other.owner != null) {
                return false;
            }
        } else if (!owner.equals(other.owner)) {
            return false;
        }
        if (shortName == null) {
            if (other.shortName != null) {
                return false;
            }
        } else if (!shortName.equals(other.shortName)) {
            return false;
        }
        if (userRoles == null) {
            if (other.userRoles != null) {
                return false;
            }
        } else if (!userRoles.equals(other.userRoles)) {
            return false;
        }
        return true;
    }

    /* see superclass */
    @Override
    public String toString() {

        return "CodeSystem [name=" + name + ", owner=" + owner + ", shortName=" + shortName + ", branchPath=" + branchPath + ", dependantVersionEffectiveTime="
            + dependantVersionEffectiveTime + ", dailyBuildAvailable=" + dailyBuildAvailable + ", countryCode=" + countryCode + ", defaultLanguageCode="
            + defaultLanguageCode + ", defaultLanguageReferenceSets=" + defaultLanguageReferenceSets + ", maintainerType=" + maintainerType + ", latestVersion="
            + latestVersion + ", languages=" + languages + ", modules=" + modules + ", userRoles=" + userRoles + "]";
    }

}