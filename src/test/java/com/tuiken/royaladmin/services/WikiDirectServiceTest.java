package com.tuiken.royaladmin.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureMockMvc

class WikiDirectServiceTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WikiDirectService wikiDirectService;

    @Test
    void allLinks() throws IOException {
        wikiDirectService.allLinks("https://en.wikipedia.org/wiki/Philip,_Duke_of_Schleswig-Holstein-Sonderburg-Glucksburg");
    }

    @Test
    void parse() throws Exception {

//        wikiDirectService.parse("https://en.wikipedia.org/wiki/Adam_Stanis%C5%82aw_Sapieha");
    }
}