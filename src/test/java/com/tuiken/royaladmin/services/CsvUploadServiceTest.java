package com.tuiken.royaladmin.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CsvUploadServiceTest {

    private static final String THRONES_FILE = "monarchs.csv";

    @Autowired
    private CsvUploadService csvUploadService;

    @Test
    void uploadToDrive() {
        csvUploadService.uploadToDrive(THRONES_FILE);
    }
}