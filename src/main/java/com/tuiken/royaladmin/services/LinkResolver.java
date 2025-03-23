package com.tuiken.royaladmin.services;

import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLDecoder;
import java.util.List;

@Service
public class LinkResolver {

    private static final String REDIRECT_URL = "https://en.wikipedia.org/w/api.php?action=query&format=json&titles=%s&redirects=1&formatversion=2";

    public String resolve(String url) {
        String decodedUrl = URLDecoder.decode(url);
        RestTemplate restTemplate = new RestTemplate();
        String title = extractTitle(decodedUrl);
        String redirectUrl = String.format(REDIRECT_URL, title);
        RedirectsResponse response = restTemplate.getForObject(redirectUrl, RedirectsResponse.class);
        String newTitle = normalize(response.query.pages.get(0).title);
        if (newTitle.equals(title)) {
            return decodedUrl;
        }
        return replaceTitle(decodedUrl, newTitle);
    }

    private String replaceTitle(String url, String newTitle) {
        String[] tokens = url.split("/");
        tokens[tokens.length-1] = newTitle;
        return String.join("/", tokens);
    }

    private String normalize(String title) {
        return title.replace(' ', '_');
    }

    private String extractTitle(String url) {
        String[] tokens = url.split("/");
        return tokens[tokens.length-1];
    }

    public static class RedirectsResponse {
        public RedirectsResponseContents query;
    }

    public static class RedirectsResponseContents {
        public List<RedirectResponsePage> pages;
    }

    public static class RedirectResponsePage {
        public long pageid;
        public String title;
    }

}
