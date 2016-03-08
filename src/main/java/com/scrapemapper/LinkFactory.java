package com.scrapemapper;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

/**
 * Created by 162717 on 3/8/16.
 */
public class LinkFactory {

    private String rootUrl;

    public LinkFactory(String rootUrl) {
        this.rootUrl = rootUrl;
    }

    public String getRootUrl() {
        return this.rootUrl;
    }

    public Link getLink(String href, String text) {
        Link retval = new Link(href, text);
        if(isPageLink(href)) {
            retval.setLinkType(LinkType.PAGE);
        } else if(isInternalLink(href)) {
            retval.setLinkType(LinkType.INTERNAL);
        } else {
            retval.setLinkType(LinkType.EXTERNAL);
        }
        return retval;
    }

    /**
     * Determine if the href is an in-page anchor
     * @param href
     * @return
     */
    public boolean isPageLink(String href) {
        boolean retval =
                href.startsWith("#")
                        || href.startsWith("/#")
                        || href.startsWith(String.format("%s#", this.rootUrl))
                        || href.startsWith(String.format("%s/#", this.rootUrl))
                        || href.startsWith(String.format("%s?", this.rootUrl))
                        || href.startsWith(String.format("%s/?", this.rootUrl));
        return retval;
    }

    /**
     * Determine if the href is withing the current root domain
     * @param href
     * @return boolean
     */
    public boolean isInternalLink(String href) {
        return (href.startsWith(this.rootUrl) || href.startsWith("/"))
                && !isPageLink(href);
    }

}
