package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.builders.PersonBuilder;
import com.tuiken.royaladmin.exceptions.WikiApiException;
import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.enums.Country;
import com.tuiken.royaladmin.model.enums.Gender;
import com.tuiken.royaladmin.model.workflows.UnhandledRecord;
import com.tuiken.royaladmin.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SmartIssueSearchService {

    private final PersonBuilder personBuilder;
    private final MonarchService monarchService;
    private final AiResolverService aiResolverService;
    private final LinkResolver resolver;
    private final WikiService wikiService;
    private final UnhandledRecordService unhandledRecordService;

    private static final String SIMPLE_URL_PREFIX = "https://simple.wikipedia.org/wiki/";
    private static final String NORMAL_URL_PREFIX = "https://en.wikipedia.org/wiki/";

    List<Monarch> smartExtractWithCreate(JSONArray jsonArray, Monarch root, Country rootCountry) {
        List<JSONObject> infoboxes = JsonUtils.readInfoboxes(jsonArray);
        List<JSONObject> issue = JsonUtils.drillForName(infoboxes, "Issue detail", "Issue", "Issue more...", "Illegitimate children Detail", "Issue among others...", "Illegitimate children more...");
        Set<String> allLinks = JsonUtils.readAllLinks(jsonArray).stream()
                .map(this::convertChildLink)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        List<String> names = JsonUtils.readFromValues(issue).stream()
                .filter(s -> !s.equals("Illegitimate:"))
                .collect(Collectors.toList());

        int fuzzy = 0;
        int ai = 0;

        List<Monarch> retval = new ArrayList<>();

        for (String name : names) {
            String foundUrl = fuzzyFind(name, allLinks);
            if (Strings.isBlank(foundUrl)) {
                foundUrl = aiResolverService.findChild(name, root.getName(), rootCountry);
            }
            if (!Strings.isBlank(foundUrl)) {
                Monarch newPerson = personBuilder.findOrCreate(foundUrl, null);
                if (newPerson != null
                        && checkParent(newPerson, root)
                ) {
                    retval.add(newPerson);
                } else {
                    saveUnhandledRecord(name, foundUrl, root.getUrl());
                }
            } else {
                saveUnhandledRecord(name, foundUrl, root.getUrl());
            }
        }
        System.out.println("* fuzzy " + fuzzy + "/" + names.size() + ", ai " + ai + "/" + names.size());
        return retval;
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

    private boolean checkParent(Monarch monarch, Monarch parent) {
        JSONArray jsonArray = new JSONArray();
        try {
            jsonArray=wikiService.read(monarch.getUrl());
        } catch (WikiApiException e) {
            System.out.println("Oiiii wiki geve error");
        }
        List<JSONObject> infoboxes = JsonUtils.readInfoboxes(jsonArray);
        if (parent.getGender().equals(Gender.MALE)) {
            List<JSONObject> father = JsonUtils.drillForName(infoboxes, "Father");
            String fatherUrl = JsonUtils.readFromLinks(father, "url").stream()
                    .map(this::convertChildLink)
                    .filter(Objects::nonNull)
                    .findFirst().orElse(null);
            if (fatherUrl != null) {
                fatherUrl = resolver.resolve(fatherUrl);
                return parent.getUrl().equals(fatherUrl);
            } else {
                return false;
            }
        } else {
            List<JSONObject> mother = JsonUtils.drillForName(infoboxes, "Mother");
            String motherUrl = JsonUtils.readFromLinks(mother, "url").stream()
                    .map(this::convertChildLink)
                    .filter(Objects::nonNull)
                    .findFirst().orElse(null);
            if (motherUrl != null) {
                motherUrl = resolver.resolve(motherUrl);
                return parent.getUrl().equals(motherUrl);
            } else {
                return false;
            }
        }
    }

    private String fuzzyFind(String name, Set<String> allNames) {

        String[] tokens_sample = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .replaceAll(",", "")
                .split(" ");

        for (String path : allNames) {
            String[] searched = Normalizer.normalize(path, Normalizer.Form.NFD)
                    .replaceAll("[^\\p{ASCII}]", "")
                    .replace(NORMAL_URL_PREFIX, "")
                    .replaceAll(",", "")
                    .split("_");

            int notFound = 0;
            for (int i = 0; i < tokens_sample.length; i++) {
                String tofind = tokens_sample[i];
                if (Arrays.stream(searched).noneMatch(ss -> ss.equalsIgnoreCase(tofind))) {
                    notFound++;
                }
            }
            if (notFound < 1) return path;
        }
        return null;
    }

    private void saveUnhandledRecord(String name, String childUrl, String parentUrl) {
        UnhandledRecord unhandledRecord = new UnhandledRecord();
        unhandledRecord.setChild(name);
        unhandledRecord.setParentUrl(parentUrl);
        unhandledRecord.setChildUrl(childUrl);
        unhandledRecordService.save(unhandledRecord);
    }

}
