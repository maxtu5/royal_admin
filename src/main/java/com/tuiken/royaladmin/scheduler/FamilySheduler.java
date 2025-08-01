package com.tuiken.royaladmin.scheduler;

import com.tuiken.royaladmin.model.api.output.MonarchApiDto;
import com.tuiken.royaladmin.model.enums.Country;
import com.tuiken.royaladmin.services.WikiLoaderService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
@EnableScheduling
@RequiredArgsConstructor
public class FamilySheduler {

    private final WikiLoaderService wikiLoaderService;
    private int order = 16;

//    @Scheduled(fixedRate = 90000)
    public void loadFamilyData() {
        Country country = Country.values()[order];
        try {
            List<MonarchApiDto> monarchApiDtos = wikiLoaderService.loadRulersFamilyMembers(country, 5, 5);
            if (monarchApiDtos.isEmpty()) {
                System.out.println("No monarch api found");
                order++;
            }
        } catch (Exception ex) {
            order++;
        } finally {
            System.out.println(country);
        }
    }

}
