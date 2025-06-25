package com.tuiken.royaladmin.services;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class WikiDirectServiceTest {

    @Test
    void allLinks() throws IOException {
        WikiDirectService service = new WikiDirectService();
        service.allLinks("https://en.wikipedia.org/wiki/Philip,_Duke_of_Schleswig-Holstein-Sonderburg-Glucksburg");
    }
}