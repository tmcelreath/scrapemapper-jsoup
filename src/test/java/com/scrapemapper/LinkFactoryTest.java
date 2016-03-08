package com.scrapemapper;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LinkFactoryTest {

    LinkFactory linkFactory;
    String root = "http://www.something.com";

    @Before
    public void setup() {
        this.linkFactory = new LinkFactory(root);
    }

    @Test
    public void testIsInternalLink_FAIL_Whatever() {
        String rootUrl = "http://test.com";
        assertTrue(linkFactory.isInternalLink(root+"/a"));
        assertFalse(linkFactory.isInternalLink("http://somethingelse.com/a"));
    }

    @Test
    public void testIsPageLink() {
        String rootUrl = "http://test.com";
        assertTrue(linkFactory.isPageLink(rootUrl+"/#"));
        assertFalse(linkFactory.isPageLink(rootUrl+"/a"));
    }

}
