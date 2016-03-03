package com.scrapemapper;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static com.scrapemapper.ScrapeMapper.*;

public class ScrapeMapperTest {

    @Test
    public void testIsPageLink() {
        String rootUrl = "http://test.com";
        assertTrue(isPageLink(rootUrl+"/#", rootUrl));
        assertFalse(isPageLink(rootUrl+"/a", rootUrl));
    }

    @Test
    public void testIsVisited() {
        List<String> visited = new ArrayList<>();
        visited.add("a");
        assertTrue(isVisited("a", visited));
        assertFalse(isVisited("b", visited));
    }

    @Test
    public void testIsInternalLink() {
        String rootUrl = "http://test.com";
        assertTrue(isInternalLink(rootUrl+"/a", rootUrl));
        assertFalse(isInternalLink("http://somethingelse.com/a", rootUrl));
    }
}
