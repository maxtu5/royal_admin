package com.tuiken.royaladmin.services;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WikiDirectService {

    public Map<String, List<String>> allLinks(String url) {
        Map<String, List<String>> goodLinks = new HashMap<>();
        Document doc;
        try {
            doc = Jsoup.connect(url).get();
        } catch (IOException e) {
            System.out.println("wiki request failed");
            return goodLinks;
        }
        Elements links = doc.select("a");
        links.stream().forEach(link -> {
            if (link.hasAttr("href") && link.attr("href").startsWith("/wiki") && !link.attr("href").contains(":")) {
                List<String> r = new ArrayList<>();
                r.add(link.text());
                goodLinks.merge(link.attr("href"), r, (l1, l2) -> {l1.addAll(l2);return l1;});
            }
        });
        return goodLinks;
    }
}