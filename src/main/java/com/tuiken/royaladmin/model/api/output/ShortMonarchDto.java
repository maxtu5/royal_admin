package com.tuiken.royaladmin.model.api.output;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
public class ShortMonarchDto {
    private String name;
    private String url;
}