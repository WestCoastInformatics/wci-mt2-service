package org.ihtsdo.refsetservice.handler.snowstorm;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ihtsdo.refsetservice.model.MapEntry;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.ihtsdo.refsetservice.util.ThreadLocalMapper;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.node.ObjectNode;

/**
 * Map entries keep the Snowstorm member active flag so an inactive history row is distinguishable from the current row.
 */
public class SnowstormMappingMemberActiveTest {

    @Test
    public void copiesInactiveAndActiveFromTheMember() throws Exception {

        final MapEntry inactive = SnowstormMapping.convertSnowstormMemberToMapEntry(member(false), null, null, "MAIN");
        final MapEntry active = SnowstormMapping.convertSnowstormMemberToMapEntry(member(true), null, null, "MAIN");

        assertFalse(inactive.isActive());
        assertTrue(active.isActive());
    }

    @Test
    public void leavesActiveTrueWhenTheMemberOmitsTheFlag() throws Exception {

        final ObjectNode member = member(true);
        member.remove("active");

        final MapEntry mapEntry = SnowstormMapping.convertSnowstormMemberToMapEntry(member, null, null, "MAIN");

        assertTrue(mapEntry.isActive());
    }

    @Test
    public void includeInactiveOmitsTheActiveMemberFilter() {

        final SearchParameters paging = new SearchParameters();
        final String activeOnly = SnowstormApiPaging.getMemberSearchPagingQueryString(paging);
        assertTrue(activeOnly.contains("active=true"));

        paging.setIncludeInactive(true);
        final String withInactive = SnowstormApiPaging.getMemberSearchPagingQueryString(paging);
        assertFalse(withInactive.contains("active="));
    }

    private static ObjectNode member(final boolean active) {

        final ObjectNode member = ThreadLocalMapper.get().createObjectNode();
        member.put("memberId", "member-1");
        member.put("released", true);
        member.put("moduleId", "449080006");
        member.put("active", active);
        member.putObject("additionalFields");
        return member;
    }
}
