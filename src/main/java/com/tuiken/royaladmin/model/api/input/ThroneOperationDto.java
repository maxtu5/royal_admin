package com.tuiken.royaladmin.model.api.input;

import com.tuiken.royaladmin.model.enums.Country;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class ThroneOperationDto {
    Country country;
    String name;
    String latestMonarchUrl;
}
