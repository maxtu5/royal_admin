package com.tuiken.royaladmin.model.api.output;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tuiken.royaladmin.model.enums.Country;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class ReignDto {
    private String id;
    private String title;
    @JsonFormat(pattern="yyyy-MM-dd")
    private LocalDate start;
    private LocalDate end;
    private LocalDate coronation;
    private Country country;
    private MonarchApiDto monarch;
}
