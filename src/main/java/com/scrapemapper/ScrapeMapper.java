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
import java.util.regex.Pattern;

public class ScrapeMapper {

    private static Logger logger = LoggerFactory.getLogger(ScrapeMapper.class);

    private RateLimiter limiter;
    private String rootUrl;
    private List<Page> results;
    private List<Pattern> disallowed;
    private List<String> visited;
    private ObjectMapper objectMapper;
    private LinkFactory linkFactory;

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
        Integer rateLimitPerSecond1 = rateLimitPerSecond != null ? rateLimitPerSecond : RATE_LIMIT_DEFAULT;
        this.limiter = RateLimiter.create(rateLimitPerSecond1);
        this.results = new ArrayList<>();
        this.disallowed = getDisallowedPaths(rootUrl);
        this.visited = new ArrayList<>();
        this.objectMapper = new ObjectMapper();
        this.linkFactory = new LinkFactory(url);
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
        List<Page> results = scrapeMapper.scrape(rootUrl);

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
     * @param rootUrl
     * @return
     */
    public String getSiteMap(String rootUrl) {
        if(rootUrl==null) {
            return "URL NOT PROVIDED";
        }
        List<Page> results = scrape(rootUrl);
        try {
            return new ObjectMapper().writeValueAsString(results);
        } catch (JsonProcessingException e) {
            return "COULD NOT PROCESS REQUEST";
        }
    }

    /**
     * Main sraping recursive method.
     * @param url
     * @return
     */
    private List<Page> scrape(String url) {
        logger.info(String.format("SCRAPING: %s", url));

        if(url == null) {
            logger.error("URL is null.");
            return results;
        }

        // Add url to visited list to block duplicate reads, even if the current read fails.s
        addToList(url, visited);

        if(isDisallowed(url, disallowed)) {
            logger.error(String.format("URL %s is disallowed,", url));
            return results;
        }

        Document doc = getDocument(url);

        if(doc != null) {

            // create a page object to encabulate href and src data.
            Page page = new Page(url);

            page.title = doc.getElementsByTag("title").text();
            Elements hrefs = doc.select("a[href]");
            Elements staticFiles = doc.select("[src]");

            // collect links
            hrefs.stream().forEach(elem -> {
                Link link = linkFactory.getLink(elem.attr(ATTR_HREF), "");
                page.addLink(link);
            });

            // collect file src
            staticFiles.stream().forEach(elem -> {
                page.addSource(elem.attr(ATTR_SRC));
            });

            try {
                logger.debug("PAGE: " + this.objectMapper.writeValueAsString(page));
            } catch(JsonProcessingException e) {
                logger.error(e.getMessage(), e);
            }

            // append the page to the results
            results.add(page);

            // recurse through the unvisited internal links
            for(Link internalLink: page.internalLinks) {
                if(!isVisited(internalLink.getHref(), visited)) {
                    // the ratelimiter will block until space opens up to run the recursive commnas
                    limiter.acquire();
                    // recurse!!
                    scrape(internalLink.getHref());
                }
            }
        }
        return results;
    }

    /**
     * Call robots.txt file to collect disallowed paths.
     */
    public static List<Pattern> getDisallowedPaths(String url) {

        boolean testresult = Pattern.compile(".").matcher("http://www.w.com/ads/something").matches();

        List<Pattern> retval = new ArrayList<>();
        try {
            String formattedUrl = url.trim() + (url.trim().endsWith("/") ? "" : "/") + "robots.txt";
            HttpGet request = new HttpGet(formattedUrl);
            // add request header
            request.addHeader("User-Agent", DEFAULT_USER_AGENT);
            HttpResponse response = HttpClientBuilder.create().build().execute(request);
            BufferedReader rd = new BufferedReader(
                    new InputStreamReader(response.getEntity().getContent()));

            String line = "";
            while ((line = rd.readLine()) != null) {
                if(line.trim().toUpperCase().startsWith("DISALLOW")) {
                    String disallowPath = line.trim().toLowerCase().substring(9).trim();
                    retval.add(Pattern.compile(disallowPath.replace("*","\\S+")));
                }
            }
        } catch (IOException e) {
            logger.error("Error fetching robots.txt", e);
        }

        return retval;
    }

    /**
     * Chek if the value exists in the list of previously-scraped urls
     * @param href
     * @return boolean
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
     * @return boolean
     */
    public static boolean isDisallowed(String url, List<Pattern> disallowed) {
        //TODO: Use regex? Ugh.
        for(Pattern path : disallowed) {
            if (path.matcher(url).matches()) {
                logger.info(String.format("Scraping path %s is disallowed.", path));
                return true;
            }
        }
        return false;
    }

    /**
     * Encode invalid characters in a url
     * @param url
     * @return String
     */
    public static String formatUrl(String url) {
        String retval = url.replace(" ", "%20");
        return retval;
    }

    /**
     * Create a jsoup document from the url. Return null on error.
     * @param url
     * @return Document
     */
    public static Document getDocument(String url) {
        // connect to the url and create a jsoup document
        Document doc = null;
        try {

            //doc = Jsoup.connect(url).get();
            String formattedUrl = formatUrl(url);
            HttpGet request = new HttpGet(formattedUrl);

            // add request header
            request.addHeader("User-Agent", DEFAULT_USER_AGENT);
            HttpResponse response = HttpClientBuilder.create().build().execute(request);

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

    public static void persist(Page page) {

    }
}