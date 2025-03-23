package com.tuiken.royaladmin.model.graphcsv;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencsv.bean.CsvBindByPosition;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FirstReignCsvDto {

    @CsvBindByPosition(position = 0)
    private String reignId;
    @CsvBindByPosition(position = 1)
    private String throneId;

}
