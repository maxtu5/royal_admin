package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.enums.House;
import com.tuiken.royaladmin.model.enums.PersonStatus;
import com.tuiken.royaladmin.utils.DatesParser;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WikiDirectService {
    private final AiResolverService aiResolverService;


    public Map<String, List<String>> allLinks(String url) {
        Map<String, List<String>> goodLinks = new HashMap<>();
        Document doc;
        try {
            doc = Jsoup.connect(url)
                    .userAgent("RoyalAdmin/0.1 (contact: maximtu@gmail.com)")
                    .get();
        } catch (IOException e) {
            System.out.println("wiki request failed");
            return goodLinks;
        }
        Elements links = doc.select("a");
        links.forEach(link -> {
            if (link.hasAttr("href") && link.attr("href").startsWith("/wiki") && !link.attr("href").contains(":")) {
                List<String> r = new ArrayList<>();
                r.add(link.text());
                goodLinks.merge(link.attr("href"), r, (l1, l2) -> {
                    l1.addAll(l2);
                    return l1;
                });
            }
        });
        return goodLinks;
    }

//    public Monarch parse(String url) {
//        Document doc;
//        try {
//            doc = Jsoup.connect(url).get();
//        } catch (IOException e) {
//            return null;
//        }
//
//        Element infobox = doc.selectFirst("table.infobox");
//        Element content = doc.selectFirst("div.mw-parser-output");
//
//        if (infobox != null) {
//            return parseFromInfobox(url, infobox);
//        }
//
//        if (content != null) {
////            String rawText = content.select("p, li").text();
//            List<String> textList = content.select("p, li")
//                    .stream()
//                    .map(Element::text)
//                    .collect(Collectors.toList());
//             Monarch monarch = aiResolverService.fullyGenerate(url, textList, "");
//            return monarch;
//        }
//
//        return null;
//    }

    private Monarch parseFromInfobox(String url, Element infobox) {
        Monarch monarch = new Monarch(url);

        // Name
        String name = Optional.ofNullable(infobox.selectFirst("caption"))
                .map(Element::text)
                .orElseGet(() -> {
                    Element header = infobox.selectFirst("tr th");
                    return header != null ? header.text() : null;
                });
        monarch.setName(name);

        // Image
        Element img = infobox.selectFirst("img");
        if (img != null) {
            monarch.setImageUrl("https:" + img.attr("src"));
            Element imgCaption = img.parent().parent().selectFirst("div");
            if (imgCaption != null) {
                monarch.setImageCaption(imgCaption.text());
            }
        }

        // Infobox rows
        for (Element row : infobox.select("tr")) {
            Element th = row.selectFirst("th");
            Element td = row.selectFirst("td");
            if (th == null || td == null) continue;

            String header = th.text().toLowerCase();
            String data = td.text();

            switch (header) {
                case "born" -> monarch.setBirth(DatesParser.findDate(data));
                case "died" -> monarch.setDeath(DatesParser.findDate(data));
                case "noble family" -> monarch.getHouse().add(House.HouseFromBeginningOfString(data));
            }
        }

        monarch.setStatus(PersonStatus.NEW_WEB);
        return monarch.getName() == null ? null : monarch;
    }

}