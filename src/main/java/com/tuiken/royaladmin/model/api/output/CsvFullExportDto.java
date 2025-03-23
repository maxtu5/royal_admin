package com.tuiken.royaladmin.model.api.output;

import lombok.Builder;
import lombok.Data;

@Builder
@Data
public class CsvFullExportDto {
    String thronesPath;
    String monarchsPath;
    String mothersPath;
    String fathersPath;
    String reignsPath;
    String firstsPath;
}
