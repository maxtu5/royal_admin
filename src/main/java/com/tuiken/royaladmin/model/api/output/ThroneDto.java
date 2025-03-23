package com.tuiken.royaladmin.model.api.output;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Builder
@Data
public class ThroneDto {
    String id;
    String name;
    String country;
    List<ReignDto> reigns;
}
