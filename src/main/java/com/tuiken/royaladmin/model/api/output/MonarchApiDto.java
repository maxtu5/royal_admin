package com.tuiken.royaladmin.model.api.output;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.tuiken.royaladmin.model.enums.Gender;
import com.tuiken.royaladmin.model.enums.House;
import com.tuiken.royaladmin.model.enums.PersonStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.lang.NonNull;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Data
@AllArgsConstructor
@Builder
public class MonarchApiDto {
    @NonNull
    private UUID id;
    private String url;
    private String name;
    private Gender gender;
    private Set<House> house = new HashSet<>();
    @JsonFormat(pattern="yyyy-MM-dd")
    private LocalDate birth;
    @JsonFormat(pattern="yyyy-MM-dd")
    private LocalDate death;
    private PersonStatus status;
    private List<ReignDto> reigns;
    private FamilyDto family;
}
