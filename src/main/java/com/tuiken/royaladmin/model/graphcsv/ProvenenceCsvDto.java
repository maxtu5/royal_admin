package com.tuiken.royaladmin.model.graphcsv;


import com.opencsv.bean.CsvBindByPosition;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ProvenenceCsvDto {
    @CsvBindByPosition(position = 0)
    String id;
    @CsvBindByPosition(position = 1)
    String parent;
}
