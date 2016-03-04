package com.scrapemapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class ScrapeMapper {

    private static Logger logger = LoggerFactory.getLogger(ScrapeMapper.class);

    private Integer rateLimitPerSecond;
    private RateLimiter limiter;
    private String rootUrl;
    private List<Page> results;
    private List<String> disallowed;
    private List<String> visited;
    private ObjectMapper objectMapper;

    // Jsoup attribute query strings
    public static final String ATTR_HREF = "abs:href";
    public static final String ATTR_SRC = "abs:src";

    // Default http request rate limit
    public static final Integer RATE_LIMIT_DEFAULT = 1;

    // Output file name
    public static final String RESULTS_FILE_NAME = "sitemap.json";

    // Default user agent
    public static final String DEFAULT_USER_AGENT = "W3C-checklink/4.5 [4.160] libwww-perl/5.823";

    public ScrapeMapper(String url) {
        this(url, RATE_LIMIT_DEFAULT);
    }

    public ScrapeMapper(String url, Integer rateLimitPerSecond) {
        this.rootUrl = url;
        this.rateLimitPerSecond = rateLimitPerSecond != null ? rateLimitPerSecond : RATE_LIMIT_DEFAULT;
        this.limiter = RateLimiter.create(this.rateLimitPerSecond);
        this.results = new ArrayList<>();
        this.disallowed = new ArrayList<>();
        this.visited = new ArrayList<>();
        this.objectMapper = new ObjectMapper();
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

        ScrapeMapper scrapeMapper = new ScrapeMapper(rootUrl, ratePerSecond);
        List<Page> results = scrapeMapper.scrape();

        try {
            File file = new File(RESULTS_FILE_NAME);
            if(file.exists()) {
                file.delete();
            }
            new ObjectMapper().writeValue(new File(RESULTS_FILE_NAME), results);
        } catch (IOException e) {
            logger.error("COULD NOT CREATE SITEMAP FILE.", e);
        }
    }

    /**
     * Return a JSON string representing the site map of a root URL.
     * Individual page records will contain the following data:
     *
     *  -- url: URL
     *  -- pageTitle: Page Title
     *  -- internalLinks: List of Internal Links (within the current domain)
     *  -- externalLinks: List of External Links (outside of the current domain)
     *  -- mediaSources: List of resource locations (images, script files, etc.)
     *
     *  The scraper will utilize the robots.txt of the domain where available to
     *  skip any disallowed directories.
     *
     * @param rootURL
     * @return
     */
    public String getSiteMap(String rootURL) {
        if(rootURL==null) {
            return "URL NOT PROVIDED";
        }
        List<Page> results = scrape();
        try {
            return new ObjectMapper().writeValueAsString(results);
        } catch (JsonProcessingException e) {
            return "COULD NOT PROCESS REQUEST";
        }
    }

    private List<Page> scrape() {
        return scrape(this.rootUrl, this.results, this.visited, this.disallowed, this.limiter);
    }

    public List<Page> scrape(String rootUrl) {
        return scrape(rootUrl, RATE_LIMIT_DEFAULT);
    }

    public List<Page> scrape(String rootUrl, Integer ratePerSecond) {
        RateLimiter limiter = RateLimiter.create(ratePerSecond);
        List<String> disallowed = getDisallowedPaths(rootUrl);
        List<String> visited = new ArrayList<>();
        List<Page> results = new ArrayList<>();
        return scrape(rootUrl, results, visited, disallowed, limiter);
    }

    /**
     * Main sraping recursive method.
     * @param url
     * @param results
     * @param visited
     * @param disallowed
     * @param limiter
     * @return
     */
    private List<Page> scrape(String url, List<Page> results, List<String> visited, List<String> disallowed, RateLimiter limiter) {
        logger.info(String.format("SCRAPING: %s", url));

        if(url == null) {
            logger.error("URL is null.");
            return results;
        }

        // Add url to visited list to block duplicate reads, even if the current read fails.s
        addToList(url, visited);

        if(isDisallowed(url, disallowed)) {
            logger.error(String.format("URL % is disallowed,", url));
            return results;
        }

        Document doc = getDocument(url);

        if(doc != null) {

            // create a page object to encabulate href and src data.
            Page page = new Page(url);

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
                return !isInternalLink(href, url) && !isPageLink(href, url);
            }).forEach(elem -> addToList(elem.attr(ATTR_HREF), page.externalLinks));

            // collect in-page links
            hrefs.stream().filter(elem -> {
                String href = elem.attr(ATTR_HREF);
                return isPageLink(href, url);
            }).forEach(elem -> addToList(elem.attr(ATTR_HREF), page.pageLinks));

            // collect file src
            staticFiles.stream().forEach(elem -> page.mediaSources.add(elem.attr(ATTR_SRC)));

            try {
                logger.info("PAGE: " + this.objectMapper.writeValueAsString(page));
            } catch(JsonProcessingException e) {
                logger.error(e.getMessage(), e);
            }

            // append the page to the results
            results.add(page);

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

    /**
     * Determine if the url matches the provided list of disallowed paths
     * @param url
     * @param disallowed
     * @return
     */
    public static boolean isDisallowed(String url, List<String> disallowed) {
        //TODO: Use regex? Ugh.
        for(String path : disallowed) {
            if (url.contains(path)) {
                logger.info(String.format("Scraping path % is disallowed.", path));
                return true;
            }
        }
        return false;
    }

    /**
     * Encode invalid characters in a url
     * @param url
     * @return
     */
    public static String formatUrl(String url) {
        String retval = url.replace(" ", "%20");
        return retval;
    }

    /**
     * Create a jsoup document from the url. Return null on error.
     * @param url
     * @return
     */
    public static Document getDocument(String url) {
        // connect to the url and create a jsoup document
        Document doc = null;
        try {

            //doc = Jsoup.connect(url).get();
            String formattedUrl = formatUrl(url);
            HttpClient client = HttpClientBuilder.create().build();
            HttpGet request = new HttpGet(formattedUrl);

            // add request header
            request.addHeader("User-Agent", DEFAULT_USER_AGENT);
            HttpResponse response = client.execute(request);

            logger.debug(String.format(
                    "Response Code : %d", response.getStatusLine().getStatusCode()));

            BufferedReader rd = new BufferedReader(
                    new InputStreamReader(response.getEntity().getContent()));

            StringBuffer result = new StringBuffer();
            String line = "";

            while ((line = rd.readLine()) != null) {
                result.append(line);
            }

            String resultStr = result.toString();

            doc = Jsoup.parse(resultStr);

        } catch (Exception e) {
            logger.error(String.format("ERROR Connecting to url %s", url), e);
        }
        return doc;
    }
}