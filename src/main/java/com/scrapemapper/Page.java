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
    public List<String> internalLinks;
    public List<String> externalLinks;
    public List<String> pageLinks;
    public List<String> mediaSources;
}
