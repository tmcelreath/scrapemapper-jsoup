package com.scrapemapper;

import java.util.ArrayList;
import java.util.List;

public class Page {

    public Page(String url) {
        this.url = url;
        this.internalLinks = new ArrayList<>();
        this.externalLinks = new ArrayList<>();
        this.pageLinks = new ArrayList<>();
        this.mediaSources = new ArrayList<>();
    }

    public String url;
    public String title;
    public List<Link> internalLinks;
    public List<Link> externalLinks;
    public List<Link> pageLinks;
    public List<String> mediaSources;

    public void addLink(Link link) {
        switch (link.getLinkType()) {
            case EXTERNAL:
                this.externalLinks.add(link);
                break;
            case INTERNAL:
                this.internalLinks.add(link);
                break;
            case PAGE:
                this.pageLinks.add(link);
                break;
        }
    }

    public void addSource(String src) {
        this.mediaSources.add(src);
    }
}
