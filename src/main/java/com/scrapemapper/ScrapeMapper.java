package com.scrapemapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ScrapeMapper {

    private static Logger logger = LoggerFactory.getLogger(ScrapeMapper.class);

    private final String rootUrl;
    private List<Page> results;
    private List<String> visited;

    public static final String ATTR_HREF = "abs:href";
    public static final String ATTR_SRC = "abs:src";

    public ScrapeMapper(String url) {
        this.rootUrl = url;
        this.results = new ArrayList<>();
        this.visited = new ArrayList<>();
    }

    public static void main(String[] args) {
        String rootUrl = args[0];
        ScrapeMapper scrapeMapper = new ScrapeMapper(rootUrl);
        scrapeMapper.start();
    }

    public void start() {
        scrape(this.rootUrl);
        try {
            File file = new File("sitemap.json");
            if(file.exists()) {
                file.delete();
            }
            new ObjectMapper().writeValue(new File("sitemap.json"), this.results);
        } catch (IOException e) {
            logger.error("Cannot create sitemap file.", e);
        }
    }

    private void scrape(String url) {

        logger.info(String.format("SCRAPING: %s", url));

        // create a page object to encabulate href and src data.
        Page page = new Page(url);

        // connect to the url and create a jsoup document
        Document doc = null;
        try {
            doc = Jsoup.connect(this.rootUrl).get();
        } catch (IOException e) {
            logger.error(String.format("ERROR Connecting to url %s", url), e);
        }

        if(doc != null) {

            page.title = doc.getElementsByTag("title").text();
            Elements hrefs = doc.select("a[href]");
            Elements staticFiles = doc.select("[src]");

            // collect internal links
            hrefs.stream().filter(elem -> {
                        String href = elem.attr(ATTR_HREF);
                        return isInternalLink(href, this.rootUrl);
                    }
            ).forEach(elem -> addToList(elem.attr(ATTR_HREF), page.internalLinks));

            // collect external links
            hrefs.stream().filter(elem -> {
                String href = elem.attr(ATTR_HREF);
                return !isInternalLink(href, rootUrl) && !isPageLink(href, rootUrl);
            }).forEach(elem -> addToList(elem.attr(ATTR_HREF), page.externalLinks));

            // collect in-page links
            hrefs.stream().filter(elem -> {
                String href = elem.attr(ATTR_HREF);
                return isPageLink(href, this.rootUrl);
            }).forEach(elem -> addToList(elem.attr(ATTR_HREF), page.pageLinks));

            // collect file src
            staticFiles.stream().forEach(elem -> page.mediaSources.add(elem.attr(ATTR_SRC)));

            // append the page to the results
            results.add(page);
            visited.add(url);

            // recurse through the unvisited internal links
            for(String internalLink: page.internalLinks) {
                if(!isVisited(internalLink, this.visited)) {
                    scrape(internalLink);
                }
            }
        }
    }

    /**
     * Determine if the href is an in-page anchor
     * @param href
     * @param rootUrl
     * @return
     */
    public static boolean isPageLink(String href, String rootUrl) {
        boolean retval =
                href.startsWith("#")
                || href.startsWith("/#")
                || href.startsWith(String.format("%s#", rootUrl))
                || href.startsWith(String.format("%s/#", rootUrl))
                || href.startsWith(String.format("%s?", rootUrl))
                || href.startsWith(String.format("%s/?", rootUrl));
        return retval;
    }

    /**
     * Determine if the href is withing the current root domain
     * @param href
     * @param rootUrl
     * @return
     */
    public static boolean isInternalLink(String href, String rootUrl) {
        return (href.startsWith(rootUrl) || href.startsWith("/"))
                && !isPageLink(href, rootUrl);
    }

    /**
     * Chek if the value exists in the list of previously-scraped urls
     * @param href
     * @return
     */
    public static boolean isVisited(String href, List<String> visited) {
        return visited.contains(href) || visited.contains(href + "/");
    }

    /**
     * Add a value to the list if it doesn't already exist
     * @param val
     * @param list
     */
    public static void addToList(String val, List<String> list) {
        if(!list.contains(val)) {
            list.add(val);
        }
    }
}
