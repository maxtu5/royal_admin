package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.datalayer.WikiCacheRecordRepository;
import com.tuiken.royaladmin.exceptions.NotPersonWikiApiException;
import com.tuiken.royaladmin.exceptions.WikiApiException;
import com.tuiken.royaladmin.model.cache.WikiCacheRecord;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

@Service
@RequiredArgsConstructor
public class WikiService {

    private final WikiCacheRecordRepository wikiCacheRecordRepository;
    private final TokenManager tokenManager;
    private final LinkResolver linkResolver;

    @Autowired
    private final RestTemplate restTemplate;

    private static final String NORMAL_URL_PREFIX = "https://en.wikipedia.org/wiki/";
    private static final String REQUEST_URL_PREFIX = "https://api.enterprise.wikimedia.com/v2/structured-contents/";

    private static final String WIKI_API_REQUEST = """
            {
              "filters": [
                {"field":"is_part_of.identifier","value":"enwiki"}
              ]
            }
            """;

    @Transactional
    public JSONArray read(String url) {

        // try to find in cache
        WikiCacheRecord cacheRecord = wikiCacheRecordRepository
                .findByUrl(url)
                .orElse(new WikiCacheRecord(url));
        if (cacheRecord.getCacheId() != null) {
            return new JSONArray(cacheRecord.getBody());
        }
        // not found in cache, retrieve from wiki API
        String[] tokens = url.split("/");
        String requestUrl = REQUEST_URL_PREFIX + tokens[tokens.length - 1];
        String rawResponse = null;
        try {
            rawResponse = loadFromWikiApi(requestUrl);
        } catch (WikiApiException e) {
            System.out.println("WikiApiException: " + e.getMessage());
            return null;
        }
        if (rawResponse == null) return null;
        JSONArray retval = new JSONArray(rawResponse);
        // save in cache
        cacheRecord.setBody(rawResponse);
        wikiCacheRecordRepository.save(cacheRecord);
        return retval;
    }

    @Transactional
    public JSONArray read(String url, boolean resolve) throws NotPersonWikiApiException {
        String title = extractTitleFromUrl(url);
        String requestUrl = REQUEST_URL_PREFIX + title;

        String resolvedUrl = resolve ? linkResolver.resolve(requestUrl) : requestUrl;
        String normalizedUrl = resolvedUrl.replace(REQUEST_URL_PREFIX, NORMAL_URL_PREFIX);

        WikiCacheRecord cacheRecord = wikiCacheRecordRepository
                .findByUrl(normalizedUrl)
                .orElse(new WikiCacheRecord(normalizedUrl));

        if (cacheRecord.getCacheId() != null) {
            try {

                JSONArray retval = new JSONArray(cacheRecord.getBody());
                if (retval.getJSONObject(0).has("error") && retval.getJSONObject(0).getString("error").equals("not a person"))
                    throw new NotPersonWikiApiException("not person");
            } catch (JSONException e) {
                System.err.println("Invalid cached JSON for URL: " + normalizedUrl);
            }
        }

        String rawResponse;
        try {
            rawResponse = loadFromWikiApi(resolvedUrl);
        } catch (WikiApiException e) {
            System.err.println("WikiApiException: " + e.getMessage());
            return null;
        }

        if (rawResponse == null || rawResponse.isBlank()) return null;

        JSONArray result;
        try {
            result = new JSONArray(rawResponse);
        } catch (JSONException e) {
            System.err.println("Failed to parse Wiki API response for URL: " + resolvedUrl);
            return null;
        }

        cacheRecord.setBody(rawResponse);
        wikiCacheRecordRepository.save(cacheRecord);

        return result;
    }

    private String extractTitleFromUrl(String url) {
        String[] tokens = url.split("/");
        return tokens.length > 0 ? tokens[tokens.length - 1] : "";
    }

    private String loadFromWikiApi(String url) throws WikiApiException {

        HttpHeaders headers = new HttpHeaders();
        try {
            String token = tokenManager.getToken();
            headers.setBearerAuth(token);
        } catch (IOException e) {
            tokenManager.refresh();
            try {
                String token = tokenManager.getToken();
                headers.setBearerAuth(token);
            } catch (IOException e0) {
                throw new WikiApiException("Error reading token", e0);
            }
        }

        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> requestEntity = new HttpEntity<>(WIKI_API_REQUEST, headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, requestEntity, String.class);
            return response.getBody();
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().equals(HttpStatus.UNAUTHORIZED)) {
                tokenManager.refresh();
                try {
                    ResponseEntity<String> response = restTemplate.exchange(
                            url, HttpMethod.POST, requestEntity, String.class);
                    return response.getBody();
                } catch (RestClientResponseException e1) {
                    throw new WikiApiException("Error reading from wiki API", e1);
                }
            } else {
                throw new WikiApiException("Error reading from wiki API", e);
            }
        }
    }

    public JSONArray readIfInCache(String url) {
        return wikiCacheRecordRepository.findByUrl(url)
                .map(record -> {
                    String body = record.getBody();
                    try {
                        return new JSONArray(body);
                    } catch (JSONException e) {
                        throw new RuntimeException("Invalid JSON in cache for URL: " + url, e);
                    }
                })
                .orElse(null);
    }

    public String findMainImage(String url) {
        try {
            String title = url.substring(url.lastIndexOf("/") + 1);
            String apiUrl = "https://en.wikipedia.org/w/api.php?action=query&titles=" +
                    title + "&prop=pageimages&format=json&pithumbsize=2500";

            ResponseEntity<String> response = restTemplate.getForEntity(apiUrl, String.class);

            // Parse JSON response
            JSONObject json = new JSONObject(response.getBody());
            JSONObject pages = json.getJSONObject("query").getJSONObject("pages");

            for (String key : pages.keySet()) {
                JSONObject page = pages.getJSONObject(key);
                if (page.has("thumbnail")) {
                    System.out.println(page.getJSONObject("thumbnail").getString("source"));
                    return page.getJSONObject("thumbnail").getString("source");
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        // No image found
        return null;

    }
}