package com.tuiken.royaladmin.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.enums.Gender;
import com.tuiken.royaladmin.model.enums.PersonStatus;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;

@Service
@Primary
public class AiServiceGemini implements AiService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final String API_KEY = "AIzaSyBCYw2fSkO4KPy671CwgBDe3i95Ahvz6Lk";
    private final String MODEL_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:generateContent?key=" + API_KEY;

    @Override
    public Monarch generateMonarch(String url) {
        String inputText = fetchTextFromUrl(url); // You need to implement this
        return generateMonarch(url, inputText);
    }

    @Override
    public Monarch generateMonarch(String url, String source) {
        String prompt = buildPrompt(source);

        GeminiRequest request = new GeminiRequest(prompt);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<GeminiRequest> entity = new HttpEntity<>(request, headers);
        ResponseEntity<GeminiResponse> response = restTemplate.postForEntity(MODEL_URL, entity, GeminiResponse.class);
        String jsonText = extractJson(response.getBody());
        if (jsonText == null || jsonText.contains("{ null }")) {
            return null;
        }

        Monarch monarch = parseMonarch(jsonText);
        monarch.setUrl(url);
        monarch.setStatus(PersonStatus.NEW_AI);
        return monarch;

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

        GeminiRequest request = new GeminiRequest(prompt);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<GeminiRequest> entity = new HttpEntity<>(request, headers);
        ResponseEntity<GeminiResponse> response = restTemplate.postForEntity(MODEL_URL, entity, GeminiResponse.class);
        String jsonText = extractJson(response.getBody());
        if (jsonText == null || jsonText.contains("{ null }")) {
            return "";
        }

        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode node = mapper.readTree(jsonText);
            return node.get("gender").asText();
        } catch (JsonProcessingException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    private String buildPrompt(String inputText) {
        return "Given the following text, return data as JSON in this format:\n" +
                "{\n" +
                "  \"name\": \"Otto II the Black\",\n" +
                "  \"gender\": \"MALE\",\n" +
                "  \"birth\": \"1467-02-01T00:00:00Z\",\n" +
                "  \"death\": \"1522-02-01T00:00:00Z\",\n" +
                "  \"description\": \"...\" // limit to 500 chars\n" +
                "}\n" +
                "Return { null } if the provided text is not a noble person description.\n\n" +
                "Here comes the text:\n" + inputText;
    }

    private String extractJson(GeminiResponse response) {
        if (response == null || response.candidates == null || response.candidates.isEmpty()) return null;
        String raw = response.candidates.get(0).content.parts.get(0).text;
        return raw.replace("```json", "").replace("```", "").trim();
    }

    private Monarch parseMonarch(String jsonText) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            JsonNode node = mapper.readTree(jsonText);
            Monarch monarch = new Monarch();
            monarch.setName(node.get("name").asText());
            monarch.setGender(Gender.valueOf(node.get("gender").asText()));
            monarch.setBirth(node.get("birth").asText().equals("null") ? null : Instant.parse(node.get("birth").asText()));
            monarch.setDeath(node.get("death").asText().equals("null") ? null : Instant.parse(node.get("death").asText()));
            monarch.setDescription(node.get("description").asText());
            return monarch;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Monarch JSON", e);
        }
    }

    // You need to implement this method to fetch the raw biography text from the URL
    private String fetchTextFromUrl(String url) {
        // Example: use Jsoup or another HTML parser to extract text
        return "...";
    }

    public static class GeminiRequest {
        public List<Content> contents;

        public GeminiRequest(String promptText) {
            Part part = new Part();
            part.text = promptText;

            Content content = new Content();
            content.parts = List.of(part);

            this.contents = List.of(content);
        }

        public static class Content {
            public List<Part> parts;
        }

        public static class Part {
            public String text;
        }
    }

    public static class GeminiResponse {
        public List<Candidate> candidates;

        public static class Candidate {
            public Content content;
        }

        public static class Content {
            public List<Part> parts;
        }

        public static class Part {
            public String text;
        }
    }
}
