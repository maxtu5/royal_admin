package com.tuiken.royaladmin.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.tuiken.royaladmin.model.enums.Country;
import org.apache.logging.log4j.util.Strings;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.stream.Collectors;

public class JsonUtils {

    public static List<JSONObject> extendParts(List<JSONObject> infoboxes) {
        List<JSONObject> retval = new ArrayList<>();
        for (JSONObject object : infoboxes) {
            if (object.has("has_parts")) {
                JSONArray array = object.getJSONArray("has_parts");
                for (int j = 0; j < array.length(); j++) {
                    retval.add(array.getJSONObject(j));
                }
            }
            if (object.has("article_sections")) {
                JSONArray array = object.getJSONArray("article_sections");
                for (int j = 0; j < array.length(); j++) {
                    retval.add(array.getJSONObject(j));
                }
            }
            if (object.has("infobox") || object.has("infoboxes")) {
                JSONArray array = object.has("infobox") ? object.getJSONArray("infobox") : object.getJSONArray("infoboxes");
                for (int j = 0; j < array.length(); j++) {
                    retval.add(array.getJSONObject(j));
                }
            }
        }
        return retval;
    }

    public static List<JSONObject> readInfoboxes(JSONArray jsonArray) {
        List<JSONObject> retval = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject o = jsonArray.getJSONObject(i);
            if (o.has("infobox") || o.has("infoboxes")) {
                JSONArray infobox = o.has("infobox") ? o.getJSONArray("infobox") : o.getJSONArray("infoboxes");
                for (int j = 0; j < infobox.length(); j++) {
                    JSONObject jsonObject = infobox.getJSONObject(j);
//                    if (jsonObject.has("name")
//                            && jsonObject.get("name") instanceof String &&
//                            (((String) jsonObject.get("name")).toLowerCase().contains("infobox")                     )
//                    ) {
                        retval.add(jsonObject);
//                    }
                }
            }
        }
        return retval;
    }

    public static List<String> readFromLinks(List<JSONObject> parts, String key) {
        List<JSONArray> arrays = parts.stream()
                .filter(o -> o.has("links"))
                .map(o -> o.getJSONArray("links"))
                .collect(Collectors.toList());
        List<String> retval = new ArrayList<>();
        for (JSONArray a : arrays) {
            for (int i = 0; i < a.length(); i++) {
                JSONObject lnk = a.getJSONObject(i);
                if (lnk.has(key) && Strings.isNotBlank((String) lnk.get(key))) {
                    retval.add((String) lnk.get(key));
                }
            }
        }
        return retval;
    }

    public static List<String> readFromValues(List<JSONObject> parts) {
        List<JSONArray> arrays = parts.stream()
                .filter(o -> o.has("values"))
                .map(o -> o.getJSONArray("values"))
                .collect(Collectors.toList());
        List<String> retval = new ArrayList<>();
        for (JSONArray a : arrays) {
            for (int i = 0; i < a.length(); i++) {
                String lnk = (String) a.get(i);
                List<String> lnks = Arrays.stream(lnk.split("\n")).toList();
                for (String link : lnks) {
                    if (Strings.isNotBlank(link)) {
                        String toAdd = link.trim();
                        toAdd = toAdd.charAt(0) == '-' ? toAdd.substring(1).trim() : toAdd;
                        retval.add(toAdd);
                    }
                }
            }
        }
        return retval;
    }

    public static String readValue(JSONObject o) {
        if (o.has("value")) return (String) o.get("value");
        return null;
    }

    public static List<JSONObject> choosePartsByName(List<JSONObject> parts, String[] keys) {
        return parts.stream()
                .filter(o -> o.has("name") && (Arrays.stream(keys).anyMatch(s -> s.equalsIgnoreCase((String) o.get("name")))))
                .collect(Collectors.toList());
    }

    private static List<JSONObject> choosePartsByType(List<JSONObject> parts, String keys) {
        return parts.stream()
                .filter(o -> o.has("type") && (keys.equalsIgnoreCase((String) o.get("type"))))
                .collect(Collectors.toList());
    }

    public static List<JSONObject> drillForName(List<JSONObject> infoboxes, String... keys) {
        List<JSONObject> items = JsonUtils.extendParts(infoboxes);
        List<JSONObject> retval = JsonUtils.choosePartsByName(items, keys);
        while (!items.isEmpty()) {
            items = JsonUtils.extendParts(items);
            retval.addAll(JsonUtils.choosePartsByName(items, keys));
        }
        return retval;
    }

    public static List<JSONObject> arrayTolist(JSONArray array) {
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.getJSONObject(i);
//            if (object.has("infobox")) {
//                List<JSONObject> inInfobox = JsonUtils.arrayTolist(object.getJSONArray("infobox"));
//                list.addAll(inInfobox);
//            }
//            if (object.has("article_sections")) {
//                List<JSONObject> inInfobox = JsonUtils.arrayTolist(object.getJSONArray("article_sections"));
//                list.addAll(inInfobox);
//            }
            list.add(object);
        }
        return list;
    }

    public static Set<String> readAllLinks(JSONArray jsonArray) {

        Set<String> retval = new HashSet<>();
        List<JSONObject> current = arrayTolist(jsonArray);

        while (!current.isEmpty()) {
            List<String> urls = readFromLinks(current, "url");
            retval.addAll(urls);
            current = extendParts(current);
        }

        return retval;
    }

    public static List<JSONObject> findByNameAndDrill(List<JSONObject> list, Country country, String keyword) {
        list = list.stream().filter(o -> o.has("name") && country.belongs((String) o.get("name"))).collect(Collectors.toList());
        if (list.size() > 1) {
            list = list.stream()
                    .filter(o -> {
                        List<JSONObject> lissi = new ArrayList<>();
                        lissi.add(o);
                        return JsonUtils.drillForName(lissi, keyword).size() > 0;
                    })
                    .collect(Collectors.toList());
        }
        return JsonUtils.drillForName(list, keyword);
    }

    public static JSONObject findImage(List<JSONObject> inf) {
        List<JSONObject> items = JsonUtils.extendParts(inf);
        List<JSONObject> retval = JsonUtils.choosePartsByType(items, "image");
        while (!items.isEmpty()) {
            items = JsonUtils.extendParts(items);
            retval.addAll(JsonUtils.choosePartsByType(items, "image"));
        }
        return retval.isEmpty() ? new JSONObject() : retval.get(0).getJSONArray("images").getJSONObject(0);
    }

    public static boolean hasInfobox(JSONArray rootArray) {
        if (rootArray == null || rootArray.isEmpty()) {
            return false;
        }

        for (int i = 0; i < rootArray.length(); i++) {
            JSONObject obj = rootArray.optJSONObject(i);
            if (obj != null) {
                // Check for single "infobox" object
                if (obj.has("infobox")) {
                    return true;
                }

                // Check for "infoboxes" array or object
                Object infoboxesObj = obj.opt("infoboxes");
                if (infoboxesObj instanceof JSONArray) {
                    JSONArray infoboxes = (JSONArray) infoboxesObj;
                    if (infoboxes.length() > 0) {
                        return true;
                    }
                } else if (infoboxesObj != null) {
                    // If it's not a JSONArray but still exists, treat it as a hit
                    return true;
                }
            }
        }

        return false;
    }
    public static String extractWikiName(JSONArray rootArray) {
        if (rootArray == null || rootArray.length() == 0) {
            return "";
        }

        JSONObject root = rootArray.optJSONObject(0);
        if (root == null) {
            return "";
        }

        String name = root.optString("name");
        return name != null ? name.trim() : "";
    }

    public static List<String> extractWikiText(JSONArray rootArray) {
        List<String> paragraphs = new ArrayList<>();

        if (rootArray == null || rootArray.length() == 0) {
            return paragraphs;
        }

        JSONObject root = rootArray.optJSONObject(0);
        if (root == null) {
            return paragraphs;
        }

        JSONArray sections = root.optJSONArray("sections");
        if (sections == null) {
            return paragraphs;
        }

        for (int i = 0; i < sections.length(); i++) {
            JSONObject section = sections.optJSONObject(i);
            if (section == null) continue;

            JSONArray hasParts = section.optJSONArray("has_parts");
            if (hasParts == null) continue;

            for (int j = 0; j < hasParts.length(); j++) {
                JSONObject part = hasParts.optJSONObject(j);
                if (part == null) continue;

                if ("paragraph".equals(part.optString("type"))) {
                    String value = part.optString("value");
                    if (!value.isEmpty()) {
                        paragraphs.add(value);
                    }
                }
            }
        }

        return paragraphs;
    }

    public static String composeShortText(List<String> paragraphs, int maxLength) {
        StringBuilder result = new StringBuilder();
        int currentLength = 0;

        for (String paragraph : paragraphs) {
            if (currentLength + paragraph.length() > maxLength) {
                break;
            }

            result.append(paragraph).append("\n");
            currentLength = result.length();
        }

        return result.toString().trim(); // Remove trailing newline
    }

    public static Set<String> extractWikiLinks(JSONArray rootArray) {
        Set<String> wikiUrls = new HashSet<>();

        if (rootArray == null || rootArray.length() == 0) {
            return wikiUrls;
        }

        JSONObject root = rootArray.optJSONObject(0);
        if (root == null) {
            return wikiUrls;
        }

        // Top-level URL
        String topUrl = root.optString("url");
        if (topUrl.contains("wikipedia.org")) {
            wikiUrls.add(topUrl);
        }

        // Scan infoboxes
        scanForWikiLinks(root.optJSONArray("infoboxes"), wikiUrls);

        // Optionally scan sections
        scanForWikiLinks(root.optJSONArray("sections"), wikiUrls);

        return wikiUrls;
    }

    private static void scanForWikiLinks(JSONArray array, Set<String> wikiUrls) {
        if (array == null) return;

        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;

            JSONArray hasParts = item.optJSONArray("has_parts");
            scanPartsRecursively(hasParts, wikiUrls);
        }
    }

    private static void scanPartsRecursively(JSONArray parts, Set<String> wikiUrls) {
        if (parts == null) return;

        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.optJSONObject(i);
            if (part == null) continue;

            // Extract links
            JSONArray links = part.optJSONArray("links");
            if (links != null) {
                for (int j = 0; j < links.length(); j++) {
                    JSONObject link = links.optJSONObject(j);
                    if (link == null) continue;

                    String url = link.optString("url");
                    if (url != null && !url.isEmpty()) {
                        if (url.contains("wikipedia.org")) {
                            wikiUrls.add(url);
                        } else if (!url.startsWith("http")) {
                            // Assume it's a relative Wikipedia link
                            wikiUrls.add("https://en.wikipedia.org/wiki/" + url);
                        }
                    }
                }
            }

            // Recurse into nested has_parts
            scanPartsRecursively(part.optJSONArray("has_parts"), wikiUrls);
        }
    }
}
