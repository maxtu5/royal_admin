package com.tuiken.royaladmin.model.api.output;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class FamilyDto {
    private ShortMonarchDto mother;
    private ShortMonarchDto father;
    private List<ShortMonarchDto> children;
}