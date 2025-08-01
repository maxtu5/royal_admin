package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.datalayer.WikiCacheRecordRepository;
import com.tuiken.royaladmin.exceptions.WikiApiException;
import com.tuiken.royaladmin.model.cache.WikiCacheRecord;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class WikiService {

    private final WikiCacheRecordRepository wikiCacheRecordRepository;
    private final TokenManager tokenManager;
    private final LinkResolver linkResolver;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String ENT_WIKI_STRUCTURED_URL = "https://api.enterprise.wikimedia.com/v2/structured-contents/%s";
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

        String[] tokens = url.split("/");
        String requestUrl = String.format(ENT_WIKI_STRUCTURED_URL, tokens[tokens.length - 1]);
        String resolvedUrl = linkResolver.resolve(requestUrl); //лишнее

        // try to find in cache
        WikiCacheRecord cacheRecord = wikiCacheRecordRepository
                .findByUrl(resolvedUrl.replace(REQUEST_URL_PREFIX, NORMAL_URL_PREFIX))
                .orElse(new WikiCacheRecord(resolvedUrl.replace(REQUEST_URL_PREFIX, NORMAL_URL_PREFIX)));
        if (cacheRecord.getCacheId() != null) {
            return new JSONArray(cacheRecord.getBody());
        }
        // not found in cache, retrieve from wiki API
        String rawResponse = null;
        try {
            rawResponse = loadFromWikiApi(resolvedUrl);
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

}