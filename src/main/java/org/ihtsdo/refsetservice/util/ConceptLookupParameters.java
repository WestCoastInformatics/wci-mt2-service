
package org.ihtsdo.refsetservice.util;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/**
 * Represents parameters for a taxonomy call.
 */
@JsonInclude(Include.NON_EMPTY)
public class ConceptLookupParameters {

	private boolean singleConceptRequest = false;

	private boolean getDescriptions = false;

    private boolean getParents = false;
    
    private boolean getChildren = false;

    private boolean getRoleGroups = false;

    private boolean getMembershipInformation = false;

    private List<String> nonDefaultPreferredTerms = new ArrayList<>();

    /**
     * Instantiates an empty {@link ConceptLookupParameters}.
     */
    public ConceptLookupParameters() {
        // n/a
    }

    public boolean isSingleConceptRequest() {
		return singleConceptRequest;
	}

	public void setSingleConceptRequest(boolean singleConceptRequest) {
		this.singleConceptRequest = singleConceptRequest;
	}

    public boolean isGetDescriptions() {
		return getDescriptions;
	}

	public void setGetDescriptions(boolean getDescriptions) {
		this.getDescriptions = getDescriptions;
	}

	public boolean isGetParents() {
		return getParents;
	}

	public void setGetParents(boolean getParents) {
		this.getParents = getParents;
	}
	
	public boolean isGetChildren() {
	    return getChildren;
	}
	
	public void setGetChildren(boolean getChildren) {
	    this.getChildren = getChildren;
	}

	public boolean isGetRoleGroups() {
		return getRoleGroups;
	}

	public void setGetRoleGroups(boolean getRoleGroups) {
		this.getRoleGroups = getRoleGroups;
	}

	public boolean isGetMembershipInformation() {
		return getMembershipInformation;
	}

	public void setGetMembershipInformation(boolean getMembershipInformation) {
		this.getMembershipInformation = getMembershipInformation;
	}

	public List<String> getNonDefaultPreferredTerms() {
		return nonDefaultPreferredTerms;
	}

	public void setNonDefaultPreferredTerms(List<String> nonDefaultPreferredTerms) {
		this.nonDefaultPreferredTerms = nonDefaultPreferredTerms;
	}

    /**
     * Instantiates a {@link ConceptLookupParameters} from the specified parameters.
     *
     * @param other the other
     */
    public ConceptLookupParameters(final ConceptLookupParameters other) {
        populateFrom(other);
    }

    /**
     * Populate from.
     *
     * @param other the other
     */
    public void populateFrom(final ConceptLookupParameters other) {
        
    	singleConceptRequest = other.isSingleConceptRequest();
    	getDescriptions = other.isGetDescriptions();
    	getParents = other.isGetParents();
    	getChildren = other.isGetChildren();
        getRoleGroups = other.isGetRoleGroups();
        getMembershipInformation = other.isGetMembershipInformation();
        nonDefaultPreferredTerms = other.getNonDefaultPreferredTerms();
    }

    
    /**
     * Sets the sort ascending.
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
        
        final ConceptLookupParameters other = (ConceptLookupParameters) obj;
        
        if (nonDefaultPreferredTerms == null) {
            
            if (other.nonDefaultPreferredTerms != null) {
                return false;
            }
            
        } else if (!nonDefaultPreferredTerms.equals(other.nonDefaultPreferredTerms)) {
            return false;
        }
        
        if (singleConceptRequest != other.singleConceptRequest) {
        	return false;
        }
        
        if (getDescriptions != other.getDescriptions) {
            return false;
        }
        
        if (getParents != other.getParents) {
            return false;
        }
        
        if (getChildren != other.getChildren) {
            return false;
        }
        
        if (getRoleGroups != other.getRoleGroups) {
            return false;
        }
        
        if (getMembershipInformation != other.getMembershipInformation) {
            return false;
        }
        
        return true;
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
        result = prime * result + ((nonDefaultPreferredTerms == null) ? 0 : nonDefaultPreferredTerms.hashCode());
        result = prime * result + (singleConceptRequest ? 1 : 0);
        result = prime * result + (getDescriptions ? 1 : 0);
        result = prime * result + (getParents ? 1 : 0);
        result = prime * result + (getChildren ? 1 : 0);
        result = prime * result + (getRoleGroups ? 1 : 0);
        result = prime * result + (getMembershipInformation ? 1 : 0);
        return result;
    }

    /**
     * To string.
     *
     * @return the string
     */
    /* see superclass */
    @Override
    public String toString() {
        
        try {
            return ModelUtility.toJson(this);
        } catch (final Exception e) {
            return e.getMessage();
        }
    }
}
