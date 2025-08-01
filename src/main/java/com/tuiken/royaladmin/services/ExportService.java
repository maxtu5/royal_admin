package com.tuiken.royaladmin.services;

import com.opencsv.bean.ColumnPositionMappingStrategy;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.entities.Provenence;
import com.tuiken.royaladmin.model.entities.Reign;
import com.tuiken.royaladmin.model.entities.Throne;
import com.tuiken.royaladmin.model.graphcsv.*;
import com.tuiken.royaladmin.utils.Converters;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Service;

import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ExportService {

    private static final String TARGET_PATH = "C:\\Users\\MT\\IdeaProjects\\royal_admin\\data\\csv\\";
    private static final String THRONES_FILE = "thrones.csv";
    private static final String MONARCHS_FILE = "monarchs.csv";
    private static final String REIGNS_FILE = "reigns.csv";
    private static final String FATHERS_FILE = "fathers.csv";
    private static final String MOTHERS_FILE = "mothers.csv";
    private static final String FIRST_REIGNS_FILE = "firsts.csv";


    private final MonarchService monarchService;
    private final ThroneService throneRoom;
    private final ProvenanceService provenanceService;
    private final CsvUploadService csvUploadService;

    @Transactional
    public void exportAll() {
        List<Throne> thrones = throneRoom.loadAllThrones();
        List<ThroneCsvDto> throneDtos = thrones.stream()
                .map(this::toThroneCsvDto)
                .collect(Collectors.toList());
        saveToCSV(throneDtos, TARGET_PATH + THRONES_FILE);

        List<Monarch> monarchs = monarchService.loadAllMonarchs();
        List<MonarchCsvDto> monarchDtos = monarchs.stream()
                .map(this::toMonarchCsvDto).toList();
        saveToCSV(monarchDtos, TARGET_PATH + MONARCHS_FILE);

        List<ReignCsvDto> reignDtos = new ArrayList<>();
        for (Throne throne : thrones) {
            reignDtos.addAll(buildReignDtos(throne, monarchs));
        }
        saveToCSV(reignDtos, TARGET_PATH + REIGNS_FILE);

        List<FirstReignCsvDto> firtstReignsDtos = thrones.stream()
                .map(t->FirstReignCsvDto.builder()
                        .reignId(t.getReigns().get(0).getId().toString())
                        .throneId(t.getId().toString())
                        .build())
                .toList();
        saveToCSV(firtstReignsDtos, TARGET_PATH + FIRST_REIGNS_FILE);

        List<Provenence> provenences = provenanceService.findAllProvenances();
        List<ProvenenceCsvDto> fatherDtos = provenences.stream()
                .map(p -> p.getFather() != null ?
                        ProvenenceCsvDto.builder()
                                .id(p.getId().toString())
                                .parent(p.getFather().toString())
                                .build()
                        : null)
                .filter(Objects::nonNull).toList();
        List<ProvenenceCsvDto> motherDtos = provenences.stream()
                .map(p -> p.getMother() != null ?
                        ProvenenceCsvDto.builder()
                                .id(p.getId().toString())
                                .parent(p.getMother().toString())
                                .build()
                        : null)
                .filter(Objects::nonNull).toList();

        saveToCSV(fatherDtos, TARGET_PATH + FATHERS_FILE);
        saveToCSV(motherDtos, TARGET_PATH + MOTHERS_FILE);
    }

    private List<ReignCsvDto> buildReignDtos(Throne throne, List<Monarch> monarchs) {
        List<ReignCsvDto> reignDtos = new ArrayList<>();
        Map<String, Integer> doubleRulers = new HashMap<>();
        for (int i = 0; i < throne.getReigns().size(); i++) {
            int j = i;
            Monarch monarch = monarchs.stream()
                    .filter(m -> m.getReignIds().contains(throne.getReigns().get(j).getId()))
                    .findFirst()
                    .orElse(null);
            if (monarch==null)
            {
                System.out.println(throne.getName() + " " + throne.getReigns().get(j).getStart().toString());
            }
            reignDtos.add(
                    toReignCsvDto(
                            throne.getReigns().get(j),
                            monarch.getId().toString(),
                            j==throne.getReigns().size()-1 ? null : throne.getReigns().get(j+1).getId().toString())
            );
        }
        return reignDtos;
    }

    private <T> void saveToCSV(List<T> dtos, String fileName) {
        Class targetClass = dtos.size() > 0 ? dtos.get(0).getClass() : null;
        try (FileWriter writer = new FileWriter(fileName)) {
            CustomColumnPositionStrategy<T> strategy = new CustomColumnPositionStrategy<>();
            strategy.setType(targetClass);
            StatefulBeanToCsv<T> builder = new StatefulBeanToCsvBuilder<T>(writer)
                    .withApplyQuotesToAll(true)
                    .withMappingStrategy(strategy)
                    .withSeparator(',')
                    .build();
            builder.write(dtos);
        } catch (CsvRequiredFieldEmptyException | CsvDataTypeMismatchException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private ThroneCsvDto toThroneCsvDto(Throne throne) {
        return ThroneCsvDto.builder()
                .id(throne.getId().toString())
                .name(throne.getName())
                .country(throne.getCountry().toString())
                .flagUrl(throne.getFlagUrl())
                .build();
    }

    private MonarchCsvDto toMonarchCsvDto(Monarch monarch) {
        return MonarchCsvDto.builder()
                .id(monarch.getId().toString())
                .name(monarch.getName().replace(',', '|'))
                .gender(monarch.getGender() != null ? monarch.getGender().toString() : null)
                .birth(Converters.toLocalDate(monarch.getBirth()))
                .death(Converters.toLocalDate(monarch.getDeath()))
                .url(monarch.getUrl())
                .status(monarch.getStatus())
                .imageUrl(Strings.isBlank(monarch.getImageUrl()) ? "" : monarch.getImageUrl())
                .imageCaption(Strings.isBlank(monarch.getImageCaption())? "": monarch.getImageCaption().replace(',', '|'))
                .description(monarch.getDescription()==null ? "" : monarch.getDescription().replace(',', '|'))
                .build();
    }

    private ReignCsvDto toReignCsvDto(Reign reign, String monarchId, String predecessorId) {
        return ReignCsvDto.builder()
                .reignId(reign.getId().toString())
                .monarchId(monarchId)
                .title(reign.getTitle())
                .start(Converters.toLocalDate(reign.getStart()))
                .end(Converters.toLocalDate(reign.getEnd()))
                .coronation(Converters.toLocalDate(reign.getCoronation()))
                .country(reign.getCountry().toString())
                .predecessorId(predecessorId)
                .build();
    }

    public static class CustomColumnPositionStrategy<T> extends ColumnPositionMappingStrategy<T> {

        @Override
        public String[] generateHeader(T bean) throws CsvRequiredFieldEmptyException {
            super.generateHeader(bean);
            return super.getColumnMapping();
        }
    }

}
