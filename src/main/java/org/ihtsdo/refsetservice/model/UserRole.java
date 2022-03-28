package org.ihtsdo.refsetservice.model;

import java.util.Arrays;
import java.util.List;

/**
 * The Enum UserRole.
 *
 */
public enum UserRole {

	/** The viewer. */
	VIEWER("Viewer"),

	/** The author. */
	AUTHOR("Author"),

	/** The reviewer. */
	REVIEWER("Reviewer"),

    /** The administrator. */
    ADMIN("Admin");

	/** The value. */
	private String value;
	
	/** Enums as list */
	public static List<UserRole> allRoles = Arrays.asList(UserRole.values());

	/**
	 * Instantiates a {@link UserRole} from the specified parameters.
	 *
	 * @param value the value
	 */
	private UserRole(String value) {
		this.value = value;
	}

	/**
	 * Returns the value.
	 *
	 * @return the value
	 */
	public String getValue() {
		return value;
	}
		
//	/**
//	 * Checks for privileges of.
//	 *
//	 * @param role the role
//	 * @return true, if successful
//	 */
//	public boolean hasPrivilegesOf(UserRole role) {
//		// TODO is this needed?
//	    if (this == UserRole.VIEWER && role == UserRole.VIEWER)
//			return true;
//		else if (this == UserRole.AUTHOR && (role == UserRole.VIEWER || role == UserRole.AUTHOR))
//			return true;
//		else if (this == UserRole.REVIEWER && (role == UserRole.VIEWER || role == UserRole.USER
//				|| role == UserRole.AUTHOR || role == UserRole.REVIEWER))
//			return true;
//		else if (this == UserRole.USER && (role == UserRole.VIEWER || role == UserRole.USER || role == UserRole.AUTHOR))
//			return true;
//		else if (this == UserRole.LEAD && (role == UserRole.VIEWER || role == UserRole.USER || role == UserRole.AUTHOR
//				|| role == UserRole.REVIEWER || role == UserRole.LEAD))
//			return true;
//		else if (this == UserRole.ADMIN)
//			return true;
//		else
//			return false;
//	    
//	    return true;
//	}
}
