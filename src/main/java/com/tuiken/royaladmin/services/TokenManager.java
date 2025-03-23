package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.exceptions.WikiApiException;
import com.tuiken.royaladmin.model.wikitoken.TokenRequestDto;
import com.tuiken.royaladmin.model.wikitoken.TokenResponseDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.*;

@Component
public class TokenManager {

    private static final String TOKEN_FILE = "src\\main\\resources\\token.txt";
    private final String ENT_WIKI_LOGIN_URL = "https://auth.enterprise.wikimedia.com/v1/login";

    @Value("${MMLKT_WIKI_USER}")
    private String wikiUser;
    @Value("${MMLKT_WIKI_PASSWORD}")
    private String wikiPassword;

    public void refresh() throws WikiApiException {
        RestTemplate restTemplate = new RestTemplate();
        TokenRequestDto request = TokenRequestDto.builder()
                .username(wikiUser)
                .password(wikiPassword)
                .build();
        ResponseEntity<TokenResponseDto> responseEntity = restTemplate
                .postForEntity(ENT_WIKI_LOGIN_URL, request, TokenResponseDto.class);
        TokenResponseDto response = responseEntity.getBody();
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(TOKEN_FILE))) {
            writer.write(response.access_token);
        } catch (IOException e) {
            throw new WikiApiException("Token refresh error", e);
        }
    }

    public String getToken() throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(TOKEN_FILE));
        return reader.readLine();
    }
}
