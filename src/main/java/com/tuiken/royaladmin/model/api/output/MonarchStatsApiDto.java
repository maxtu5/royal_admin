package com.tuiken.royaladmin.model.api.output;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MonarchStatsApiDto {
    long totalMonarchs;
    long resolvedMonarchs;
    long totalRelations;
    long nullRelations;
    long badGenderRelations;
    List<ThroneStatsApiDto> resolvedByThrones;
}
