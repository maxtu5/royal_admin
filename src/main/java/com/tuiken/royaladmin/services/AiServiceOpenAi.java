package com.tuiken.royaladmin.services;


import com.tuiken.royaladmin.ai.Prompts;
import com.tuiken.royaladmin.datalayer.MonarchRepository;
import com.tuiken.royaladmin.datalayer.WikiCacheRecordRepository;
import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.enums.Gender;
import com.tuiken.royaladmin.model.enums.PersonStatus;
import com.tuiken.royaladmin.utils.JsonUtils;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.ai.chat.ChatClient;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatterBuilder;
import java.net.URLDecoder;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiServiceOpenAi implements AiService {

    private final ChatClient aiClient;
    private final WikiCacheRecordRepository wikiCacheRecordRepository;
    private final MonarchRepository monarchRepository;
    private final WikiService wikiService;


    private String sendRequest(String prompt) {
//                System.out.println(prompt);
        String response = aiClient.call(prompt);
        try {
            JSONObject urlObject = new JSONObject(response);
            response = urlObject.getString("link");
            response = response.trim();
            if (response.charAt(response.length() - 1) == ')') {
                int ind = response.lastIndexOf('(');
                response = response.substring(0, ind);
                if (response.charAt(response.length() - 1) == '_') {
                    response = response.substring(0, response.length() - 1);
                }
            }
            response = URLDecoder.decode(response);
            System.out.println(response);
        } catch (JSONException e) {
            response = "";
        }
        return response;
    }

    @Override
    public String findGender(String name) {

        String promtTemplate = """
                        Tell me if %s is male or female.
                        Provide response in JSON format only. 
                        The format should be a JSON object like {"gender": "MALE"} or {"gender": "MALE"}.
                        Return {"gender": "UNKNOWN"} if you can't decide. 
                        Make sure there are no newline characters in the JSON object response. 
                """;
        String prompt = String.format(promtTemplate, name);

//        System.out.println(prompt);
        String response = aiClient.call(prompt);
        try {
            JSONObject urlObject = new JSONObject(response);
            response = urlObject.getString("gender");
//            System.out.println(response);
        } catch (JSONException e) {
            response = "";
        }
        return response;
    }

    public String createDescription(String name) {
        String promtTemplate = "give me 500 chars text about %s";
        String prompt = String.format(promtTemplate, name);
        String response = aiClient.call(prompt);
        return response;
    }

    @Override
    public Monarch generateMonarch(String url) {
        Monarch monarch = monarchRepository.findByUrl(url).orElse(new Monarch(url));
        if (monarch.getId() != null && monarch.getStatus() == PersonStatus.EPHEMERAL) {
            System.out.println("Ephemeral already exists");
            return monarch;
        }

        JSONArray rootArray = extractRootArray(url);
        JSONObject obj = queryForMonarch(rootArray);
        if (obj == null) {return null;}

        monarch.setName(JsonUtils.extractWikiName(rootArray));

        List<String> allowedGenders = Arrays.stream(Gender.values())
                .map(Enum::name)
                .collect(Collectors.toList());
        String genderStr = obj.optString("gender", "UNKNOWN").toUpperCase();
        if (allowedGenders.contains(genderStr)) {
            monarch.setGender(Gender.valueOf(genderStr));
        }

        monarch.setBirth(parseDate(obj.optString("birth")));
        monarch.setDeath(parseDate(obj.optString("death")));

        monarch.setStatus(PersonStatus.NEW_AI);
        monarch.setImageUrl(wikiService.findMainImage(url));
//        if (monarch.getImageUrl() != null) {
//            monarch.setImageCaption(tryForCaption(monarch.getImageUrl(), rootArray));
//        }
        monarch.setDescription(obj.optString("description", null));
        return monarch;
    }

    @Override
    public Monarch generateMonarch(String url, String source) {
        return null;
    }

    private String tryForCaption(String imageUrl, JSONArray jsonArray) {
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject entry = jsonArray.getJSONObject(i);
            JSONObject imageObj = entry.optJSONObject("image");

            if (imageObj != null && imageUrl.equals(imageObj.optString("content_url"))) {
                JSONArray references = entry.optJSONArray("references");
                if (references != null) {
                    for (int j = 0; j < references.length(); j++) {
                        JSONObject ref = references.getJSONObject(j);
                        JSONObject metadata = ref.optJSONObject("metadata");
                        if (metadata != null && metadata.has("title")) {
                            return metadata.getString("title");
                        }

                        JSONObject text = ref.optJSONObject("text");
                        if (text != null && text.has("value")) {
                            return text.getString("value");
                        }
                    }
                }
            }
        }
        return null; // No caption found
    }

    private JSONObject queryForMonarch(JSONArray rootArray) {
        List<String> wikiContent = JsonUtils.extractWikiText(rootArray);
        String searchText = JsonUtils.composeShortText(wikiContent, 3000);
        int contentUsage = (int) (searchText.length() * 100.00 / wikiContent.stream().mapToInt(String::length).sum());
        String prompt = String.format(
                contentUsage > 50 ? Prompts.PERSON_ALL_WDESC.getText() : Prompts.PERSON_ALL.getText(),
                wikiContent);
        System.out.println("!!! Expensive AI generation");
        String responseJson = aiClient.call(prompt);
        if (responseJson == null || responseJson.trim().equalsIgnoreCase("null")) {
            System.out.println("wowow response null");
            return null;
        }
        try {
            return new JSONObject(responseJson);
        } catch (Exception e) {
            System.out.println("wowow response exception");return null;
        }
    }

    private JSONArray extractRootArray(String url) {
        String jsonString = wikiCacheRecordRepository.findByUrl(url)
                .orElseThrow(() -> new RuntimeException("No record found for URL: " + url))
                .getBody();
        try {
            return new JSONArray(jsonString);
        } catch (JSONException e) {
            throw new RuntimeException("Failed to parse JSON", e);
        }
    }

    public static Instant parseDate(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        try {
            DateTimeFormatter formatter = new DateTimeFormatterBuilder()
                    .appendValue(ChronoField.YEAR, 1, 10, SignStyle.NORMAL)
                    .appendLiteral('-')
                    .appendValue(ChronoField.MONTH_OF_YEAR, 2)
                    .appendLiteral('-')
                    .appendValue(ChronoField.DAY_OF_MONTH, 2)
                    .appendLiteral('T')
                    .appendValue(ChronoField.HOUR_OF_DAY, 2)
                    .appendLiteral(':')
                    .appendValue(ChronoField.MINUTE_OF_HOUR, 2)
                    .appendLiteral(':')
                    .appendValue(ChronoField.SECOND_OF_MINUTE, 2)
                    .appendOffsetId()
                    .toFormatter();

            OffsetDateTime odt = OffsetDateTime.parse(date, formatter);
            return odt.toInstant();
        } catch (DateTimeParseException e) {
            System.err.println("Invalid date format: " + date);
            return null;
        }
    }
}
