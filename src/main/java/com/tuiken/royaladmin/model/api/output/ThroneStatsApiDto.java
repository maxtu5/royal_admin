package com.tuiken.royaladmin.model.api.output;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ThroneStatsApiDto {
    String name;
    long totalMonarchs;
    long resolvedLevel;
}
