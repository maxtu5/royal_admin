package com.tuiken.royaladmin.controllers;

import com.tuiken.royaladmin.model.api.output.CsvFullExportDto;
import com.tuiken.royaladmin.services.ExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/data/export")
@Slf4j
@CrossOrigin
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @GetMapping(path = "/")
    public CsvFullExportDto exportAllToCsv() {
        return exportService.exportAll();
    }
}
