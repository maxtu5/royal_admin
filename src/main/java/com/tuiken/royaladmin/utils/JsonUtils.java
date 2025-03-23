package com.tuiken.royaladmin.utils;

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
}
