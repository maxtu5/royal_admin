package com.tuiken.royaladmin.model.graphcsv;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.opencsv.bean.CsvBindByPosition;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReignCsvDto {

    @CsvBindByPosition(position = 0)
    private String reignId;
    @CsvBindByPosition(position = 1)
    private String country;
    @CsvBindByPosition(position = 2)
    private String monarchId;
    @CsvBindByPosition(position = 3)
    private String title;
    @CsvBindByPosition(position = 4)
    private LocalDate start;
    @CsvBindByPosition(position = 5)
    private LocalDate end;
    @CsvBindByPosition(position = 6)
    private LocalDate coronation;
    @CsvBindByPosition(position = 7)
    private String predecessorId;

}
