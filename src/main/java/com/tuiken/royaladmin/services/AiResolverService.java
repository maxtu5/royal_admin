package com.tuiken.royaladmin.services;


import com.tuiken.royaladmin.model.enums.Country;
import com.tuiken.royaladmin.model.enums.Gender;
import lombok.RequiredArgsConstructor;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.ai.chat.ChatClient;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;

@Service
@RequiredArgsConstructor
public class AiResolverService {

    private final ChatClient aiClient;

    public String findChild(String child, String parent, Country country) {
        return "http://com.com";
    }

    public String findChild1(String child, String parent, Country country) {
        Gender childGender = Gender.fromTitle(child);
        String childType = childGender == null ?
                "child" :
                childGender.toString().equalsIgnoreCase("MALE") ? "son" : "daughter";

        String promtTemplate = """
                        Return me a link to a  wikipedia page for %s, %s of %s, monarch of %s.
                        Provide response in JSON format only. 
                        The format should be a JSON object like {"link": "https://..."}. Provide no other commentary. 
                        Make sure there are no newline characters in the JSON object response. 
                """;
        String prompt = String.format(promtTemplate, child, childType, parent, country.toString());
        String s = sendRequest(prompt);
        return s.toLowerCase().contains("#marriage_and_issue") ? "{\"link\":\"\"}" : s;
    }

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

    public String findGender(String name) {
        return "UNKNOWN";
    }

    public String findGender1(String name) {

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
}
