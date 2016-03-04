package com.scrapemapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
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

    public static final String ATTR_HREF = "abs:href";
    public static final String ATTR_SRC = "abs:src";

    public static final Integer RATE_LIMIT_DEFAULT = 1;
    public ScrapeMapper() {
    }

    public static void main(String[] args) {
        String rootUrl = (args.length > 0) ? args[0] : null;
        String ratePerSecondStr = (args.length > 1) ? args[1] : null;
        Integer ratePerSecond;
        try {
            ratePerSecond = new Integer(ratePerSecondStr);
        } catch(NumberFormatException e) {
            logger.error(String.format("Invalid rate value %s. Defaulting to 1.", ratePerSecondStr));
            ratePerSecond = RATE_LIMIT_DEFAULT;
        }

        ScrapeMapper scrapeMapper = new ScrapeMapper();
        List<Page> results = scrapeMapper.scrape(rootUrl, ratePerSecond);
        try {
            File file = new File("sitemap.json");
            if(file.exists()) {
                file.delete();
            }
            new ObjectMapper().writeValue(new File("sitemap.json"), results);
        } catch (IOException e) {
            logger.error("CCOULD NOT CREATE SITEMAP FILE.", e);
        }
    }

    public String getSiteMap(String rootURL) {
        if(rootURL==null) {
            return "URL NOT PROVIDED";
        }
        List<Page> results = scrape(rootURL);
        try {
            return new ObjectMapper().writeValueAsString(results);
        } catch (JsonProcessingException e) {
            return "COULD NOT PROCESS REQUEST";
        }
    }

    public static List<Page> scrape(String rootUrl, Integer ratePerSecond) {
        RateLimiter limiter = RateLimiter.create(ratePerSecond);
        List<String> disallowed = getDisallowedPaths(rootUrl);
        List<String> visited = new ArrayList<>();
        List<Page> results = new ArrayList<>();
        return scrape(rootUrl, results, visited, disallowed, limiter);
    }
    public static List<Page> scrape(String rootUrl) {
        return scrape(rootUrl, RATE_LIMIT_DEFAULT);
    }

    private static List<Page> scrape(String url, List<Page> results, List<String> visited, List<String> disallowed, RateLimiter limiter) {
        logger.info(String.format("SCRAPING: %s", url));

        if(url == null) {
            logger.error("URL is null.");
            return results;
        }

        for(String path : disallowed) {
            if (url.contains(path)) {
                logger.info(String.format("Scraping path % is disallowed.", path));
                return results;
            }
        }

        // create a page object to encabulate href and src data.
        Page page = new Page(url);

        // connect to the url and create a jsoup document
        Document doc = null;
        try {
            doc = Jsoup.connect(url).get();
        } catch (IOException e) {
            logger.error(String.format("ERROR Connecting to url %s", url), e);
            return results;
        }

        if(doc != null) {

            page.title = doc.getElementsByTag("title").text();
            Elements hrefs = doc.select("a[href]");
            Elements staticFiles = doc.select("[src]");

            // collect internal links
            hrefs.stream().filter(elem -> {
                        String href = elem.attr(ATTR_HREF);
                        return isInternalLink(href, url);
                    }
            ).forEach(elem -> addToList(elem.attr(ATTR_HREF), page.internalLinks));

            // collect external links
            hrefs.stream().filter(elem -> {
                String href = elem.attr(ATTR_HREF);
                return !isInternalLink(href, url) && !isPageLink(href, url);
            }).forEach(elem -> addToList(elem.attr(ATTR_HREF), page.externalLinks));

            // collect in-page links
            hrefs.stream().filter(elem -> {
                String href = elem.attr(ATTR_HREF);
                return isPageLink(href, url);
            }).forEach(elem -> addToList(elem.attr(ATTR_HREF), page.pageLinks));

            // collect file src
            staticFiles.stream().forEach(elem -> page.mediaSources.add(elem.attr(ATTR_SRC)));

            // append the page to the results
            results.add(page);
            visited.add(url);

            // recurse through the unvisited internal links
            for(String internalLink: page.internalLinks) {
                if(!isVisited(internalLink, visited)) {
                    // the ratelimiter will block until space opens up to run the recursive commnas
                    limiter.acquire();
                    // recurse!!
                    scrape(internalLink, results, visited, disallowed, limiter);
                }
            }
        }
        return results;
    }

    /**
     * Call robots.txt file to collect disallowed paths.
     */
    public static List<String> getDisallowedPaths(String url) {
        List<String> retval = new ArrayList<>();
        // Add standard wordpress admin path.
        retval.add("/wp-admin/");
        //TODO: CALL url/robots.txt and add disallowed paths to retrun value.
        return retval;
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
