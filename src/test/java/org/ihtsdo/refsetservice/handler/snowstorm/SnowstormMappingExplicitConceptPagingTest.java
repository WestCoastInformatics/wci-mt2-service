package org.ihtsdo.refsetservice.handler.snowstorm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ihtsdo.refsetservice.util.SearchParameters;
import org.junit.jupiter.api.Test;

/**
 * Explicit concept reads must page past the mappings-grid limit. Stopping after that page returns later concepts with an empty target.
 */
public class SnowstormMappingExplicitConceptPagingTest {

    @Test
    public void raisesTheGridPageSizeAndStartsAtTheFirstMember() {

        final SearchParameters paging = new SearchParameters();
        paging.setLimit(100);
        paging.setOffset(20);
        paging.setSearchAfter("already-set");

        SnowstormMapping.prepareExplicitConceptMemberSearch(paging);

        assertEquals(SnowstormMapping.EXPLICIT_CONCEPT_MEMBER_PAGE_SIZE, paging.getLimit());
        assertEquals(0, paging.getOffset());
        assertNull(paging.getSearchAfter());
    }

    @Test
    public void keepsALargerRequestedPageAndCapsAtTheSnowstormWindow() {

        final SearchParameters exportPaging = new SearchParameters();
        exportPaging.setLimit(10_000);
        SnowstormMapping.prepareExplicitConceptMemberSearch(exportPaging);
        assertEquals(10_000, exportPaging.getLimit());

        final SearchParameters oversized = new SearchParameters();
        oversized.setLimit(50_000);
        SnowstormMapping.prepareExplicitConceptMemberSearch(oversized);
        assertEquals(10_000, oversized.getLimit());
    }

    @Test
    public void followsTheNextCursorWhenThePageIsFullAndMembersRemain() {

        final SearchParameters paging = pagingAt(1_000, 0, null);

        final boolean anotherPage = SnowstormMapping.continueExplicitConceptMemberSearch(paging, 1_000, 1_000, 2_500, "cursor-2", 1);

        assertTrue(anotherPage);
        assertEquals("cursor-2", paging.getSearchAfter());
        assertEquals(0, paging.getOffset());
    }

    @Test
    public void stopsWhenThePageIsShortOrTheReportedTotalIsReached() {

        final SearchParameters shortPage = pagingAt(1_000, 0, null);
        assertFalse(SnowstormMapping.continueExplicitConceptMemberSearch(shortPage, 150, 150, 150, "cursor-2", 1));
        assertNull(shortPage.getSearchAfter());

        final SearchParameters exactTotal = pagingAt(1_000, 0, null);
        assertFalse(SnowstormMapping.continueExplicitConceptMemberSearch(exactTotal, 1_000, 1_000, 1_000, "cursor-2", 1));
        assertNull(exactTotal.getSearchAfter());
    }

    @Test
    public void stopsWhenTheCursorDoesNotAdvanceOrThePageCapIsHit() {

        final SearchParameters stuck = pagingAt(1_000, 0, "cursor-1");
        assertFalse(SnowstormMapping.continueExplicitConceptMemberSearch(stuck, 1_000, 2_000, 5_000, "cursor-1", 2));
        assertEquals("cursor-1", stuck.getSearchAfter());

        final SearchParameters capped = pagingAt(1_000, 0, "cursor-1");
        assertFalse(SnowstormMapping.continueExplicitConceptMemberSearch(capped, 1_000, 500_000, 600_000, "cursor-2",
            SnowstormMapping.MAX_EXPLICIT_CONCEPT_MEMBER_PAGES));
        assertEquals("cursor-1", capped.getSearchAfter());
    }

    @Test
    public void usesOffsetWhenSnowstormOmitsACursor() {

        final SearchParameters paging = pagingAt(1_000, 0, null);

        assertTrue(SnowstormMapping.continueExplicitConceptMemberSearch(paging, 1_000, 1_000, 2_500, null, 1));
        assertEquals(1_000, paging.getOffset());

        final SearchParameters atWindow = pagingAt(1_000, 9_000, null);
        assertFalse(SnowstormMapping.continueExplicitConceptMemberSearch(atWindow, 1_000, 10_000, 12_000, null, 2));
        assertEquals(9_000, atWindow.getOffset());
    }

    private static SearchParameters pagingAt(final int limit, final int offset, final String searchAfter) {

        final SearchParameters paging = new SearchParameters();
        paging.setLimit(limit);
        paging.setOffset(offset);
        paging.setSearchAfter(searchAfter);
        return paging;
    }
}
