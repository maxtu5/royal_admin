package com.tuiken.royaladmin.utils;

import com.tuiken.royaladmin.model.enums.House;
import org.apache.logging.log4j.util.Strings;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class HouseParser {

    private static final Set<String> DELETE_CAPTIONS = Set.of(
            "House",
            "Noble family",
            "Family",
            "agnatic",
            "Dynasty",
            "family",
            "Noble",
            "of",
            "de",
            "See "
    );

    private static final String UNSAVED_HOUSES_FILE = "C:\\Users\\MT\\IdeaProjects\\royal_admin\\data\\houses.txt";

    public static Set<House> parseHouse(String houseStr) {
        return Optional.ofNullable(fromString(houseStr))
                .map(Set::of)
                .orElse(Set.of());
    }

    private static House fromString(String input) {
        String normalized = normalize(input);

        if (Strings.isBlank(normalized)) return null;

        for (House h : House.values()) {
            if (h.getLabel().equalsIgnoreCase(normalized)) {
                return h;
            }
        }

        FileWriter writer = null; // true for append mode
        try {
            writer = new FileWriter(UNSAVED_HOUSES_FILE, true);
            writer.write(String.format("%s(\"%s\"),\n", normalized.toUpperCase(), normalized));
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return null; // or throw exception
    }

    private static String normalize(String raw) {
        String s = raw;

        // Remove captions
        for (String cap : DELETE_CAPTIONS) {
            s = s.replaceAll("(?i)\\b" + cap + "\\b", "");
        }

        // Trim leftover spaces
        s = s.trim().replaceAll("\\s{2,}", " ").trim();

        // Capitalize first letter of each segment
        s = Arrays.stream(s.split("-"))
                .map(part -> part.isEmpty()
                        ? part
                        : Character.toUpperCase(part.charAt(0)) + part.substring(1).toLowerCase())
                .collect(Collectors.joining("-"));

        return s;
    }
}
