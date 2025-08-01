package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.builders.PersonBuilder;
import com.tuiken.royaladmin.exceptions.WikiApiException;
import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.enums.Gender;
import com.tuiken.royaladmin.model.workflows.UnhandledRecord;
import com.tuiken.royaladmin.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class SmartIssueSearchService {

    private static final String WIKI_URL_TEMPLATE = "https://en.wikipedia.org%s";
    private final PersonBuilder personBuilder;
    private final MonarchService monarchService;
    private final AiResolverService aiResolverService;
    private final LinkResolver resolver;
    private final WikiService wikiService;
    private final UnhandledRecordService unhandledRecordService;

    private static final String SIMPLE_URL_PREFIX = "https://simple.wikipedia.org/wiki/";
    private static final String NORMAL_URL_PREFIX = "https://en.wikipedia.org/wiki/";

    List<Monarch> findInAllLinksParentCheck(List<JSONObject> issue, Monarch root, Map<String, List<String>> allLinks) {
        List<String> names = JsonUtils.readFromValues(issue).stream()
                .map(s -> s.replaceAll("Illegitimate:", "").trim())
                .filter(Strings::isNotBlank)
                .toList();

        return names.stream()
                .map(name -> {
                    String url = findInAllLinks(name, allLinks);
                    if (Strings.isBlank(url)) saveUnhandledRecord(name, url, root.getUrl());
                    return url;
                })
                .filter(Strings::isNotBlank)
                .map(resolver::resolve) //лишнее
//                .filter(url -> checkParent(url, root, allLinks))
                .map(url -> personBuilder.findOrCreate(url, null))
                .filter(Objects::nonNull)
                .toList();
    }

    public String convertChildLink(String src) {
        if (src.contains("#") || src.contains("(genealogy)") || !src.startsWith(SIMPLE_URL_PREFIX) && !src.startsWith(NORMAL_URL_PREFIX)) {
            return null;
        }
        String retval = src.contains("?") ? src.substring(0, src.indexOf("?")) : src;
//        retval = Normalizer.normalize(retval, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]", "");
        if (retval.startsWith(SIMPLE_URL_PREFIX)) {
            String[] tokens = retval.split("/");
            retval = NORMAL_URL_PREFIX + tokens[tokens.length - 1];
        }
        return retval;
    }

//    private boolean checkParent(String childUrl, Monarch parent, Map<String, List<String>> allLinks) {
//        JSONArray jsonArray = wikiService.read(childUrl);
//        List<JSONObject> infoboxes = JsonUtils.readInfoboxes(jsonArray);
//        return isRightParent(
//                parent.getGender().equals(Gender.MALE) ? "Father" : "Mother",
//                infoboxes,
//                parent.getUrl(),
//                allLinks);
//    }

    private boolean isRightParent(String parentKey, List<JSONObject> infoboxes, String url, Map<String, List<String>> allLinks) {
        List<JSONObject> parent = JsonUtils.drillForName(infoboxes, parentKey);
        String parentUrl = JsonUtils.readFromLinks(parent, "url").stream()
                .map(this::convertChildLink)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
        parentUrl = parentUrl != null ? parentUrl : findInAllLinks(
                JsonUtils.readFromValues(parent).stream()
                        .findFirst().orElse(null), allLinks);
        return parentUrl != null && url.equals(resolver.resolve(parentUrl));
    }

    public String findInAllLinks(String name, Map<String, List<String>> allNames) {
        return allNames.entrySet().stream()
                .filter(links -> links.getValue().contains(name))
                .findFirst()
                .map(Map.Entry::getKey)
                .map(url -> String.format(WIKI_URL_TEMPLATE, url))
                .orElse(null);
    }

    private void saveUnhandledRecord(String name, String childUrl, String parentUrl) {
        UnhandledRecord unhandledRecord = new UnhandledRecord();
        unhandledRecord.setChild(name);
        unhandledRecord.setParentUrl(parentUrl);
        unhandledRecord.setChildUrl(childUrl);
        unhandledRecordService.save(unhandledRecord);
    }

}
