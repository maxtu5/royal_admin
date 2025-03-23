package com.tuiken.royaladmin.utils;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DatesParser {

    public static Instant[] findTwoDates(String source) {
        String regex = "\\d{1,2}\\s(January|February|March|April|May|June|July|August|September|October|November|December)\\s\\d{3,4}";

        List<String> allMatches = new ArrayList<>();
        Matcher matcher = Pattern.compile(regex).matcher(source);

        while (matcher.find()) {
            allMatches.add(matcher.group());
        }
        Instant[] dates = new Instant[2];

        if (allMatches.size() == 2) {
            String s = allMatches.get(0);
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("d MMMM y", Locale.ENGLISH);
            LocalDate ld = LocalDate.parse(s, dtf);
            ZonedDateTime zdt = ZonedDateTime.of(ld, LocalTime.of(12, 0), ZoneId.of("America/Toronto"));
            dates[0] = zdt.toInstant();

            s = allMatches.get(1);
            dtf = DateTimeFormatter.ofPattern("d MMMM y", Locale.ENGLISH);
            ld = LocalDate.parse(s, dtf);
            zdt = ZonedDateTime.of(ld, LocalTime.of(12, 0), ZoneId.of("America/Toronto"));
            dates[1] = zdt.toInstant();
            return dates;
        }

        if (allMatches.size() == 0) {
            regex = "\\d{3,4}";
            List<String> allMatches1 = new ArrayList<>();
            Matcher matcher1 = Pattern.compile(regex).matcher(source);

            while (matcher1.find()) {
                allMatches1.add(matcher1.group());
            }

            if (allMatches1.size() == 2) {             // case 1016–1035
                String ys = allMatches1.get(0);
                LocalDate ld = LocalDate.of(Integer.parseInt(ys), 1, 1);
                ZonedDateTime zdt = ZonedDateTime.of(ld, LocalTime.of(12, 0), ZoneId.of("America/Toronto"));
                dates[0] = zdt.toInstant();

                ys = allMatches1.get(1);
                ld = LocalDate.of(Integer.parseInt(ys), 1, 1);
                zdt = ZonedDateTime.of(ld, LocalTime.of(12, 0), ZoneId.of("America/Toronto"));
                dates[1] = zdt.toInstant();
            }

        }

        if (allMatches.size() == 1) {

            String s = allMatches.get(0);
            String before = source.substring(0, source.indexOf(s));

            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("d MMMM y", Locale.ENGLISH);
            LocalDate ld = LocalDate.parse(s, dtf);
            ZonedDateTime zdt = ZonedDateTime.of(ld, LocalTime.of(12, 0), ZoneId.of("America/Toronto"));
            Instant oneDate = zdt.toInstant();

            regex = "\\d{3,4}";
            List<String> allMatches1 = new ArrayList<>();
            Matcher matcher1 = Pattern.compile(regex).matcher(before);

            while (matcher1.find()) {
                allMatches1.add(matcher1.group());
            }

            if (allMatches1.size() == 1) { // case 1035 – 8 June 1042
                String ys = allMatches1.get(0);
                ld = LocalDate.of(Integer.parseInt(ys), 1, 1);
                zdt = ZonedDateTime.of(ld, LocalTime.of(12, 0), ZoneId.of("America/Toronto"));
                dates[0] = zdt.toInstant();
                dates[1] = oneDate;
                return dates;
            }

            regex = "(\\d{1,2}\\s(January|February|March|April|May|June|July|August|September|October|November|December))";
            allMatches1 = new ArrayList<>();
            matcher1 = Pattern.compile(regex).matcher(before);

            while (matcher1.find()) {
                allMatches1.add(matcher1.group());
            }

            if (allMatches1.size() == 1) { // case 23 April – 30 November 1016
                zdt = oneDate.atZone(ZoneId.of("America/Toronto"));
                String ys = allMatches1.get(0) + " " + zdt.getYear();
                ld = LocalDate.parse(ys, dtf);
                zdt = ZonedDateTime.of(ld, LocalTime.of(12, 0), ZoneId.of("America/Toronto"));
                dates[0] = zdt.toInstant();
                dates[1] = oneDate;
                return dates;
            }

            String after = source.substring(source.indexOf(s) + s.length());
            regex = "\\d{3,4}";
            allMatches1 = new ArrayList<>();
            matcher1 = Pattern.compile(regex).matcher(after);

            while (matcher1.find()) {
                allMatches1.add(matcher1.group());
            }

            if (allMatches1.size() == 1) { // case 18 March 978 – 1013
                String ys = allMatches1.get(0);
                ld = LocalDate.of(Integer.parseInt(ys), 1, 1);
                zdt = ZonedDateTime.of(ld, LocalTime.of(12, 0), ZoneId.of("America/Toronto"));
                dates[0] = oneDate;
                dates[1] = zdt.toInstant();
                return dates;
            }

            dates[0] = oneDate;
        }
        return dates;

    }

    public static Instant findDate(String source) {
        if (source == null) return null;
        String regex = "(\\d{1,2}\\s(January|February|March|April|May|June|July|August|September|October|November|December)\\s\\d{4})";
        Matcher matcher = Pattern.compile(regex).matcher(source);
        if (matcher.find()) { // case 21 February 1845
            String s = matcher.group(1);
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("d MMMM y", Locale.ENGLISH);
            LocalDate ld = LocalDate.parse(s, dtf);
            ZonedDateTime zdt = ZonedDateTime.of(ld, LocalTime.of(12, 0), ZoneId.of("America/Toronto"));
            return zdt.toInstant();
        } else {
            regex = "(\\d{3,4})";
            matcher = Pattern.compile(regex).matcher(source);
            if (matcher.find()) { // case 1845
                String s = matcher.group(1);
                LocalDate ld = LocalDate.of(Integer.parseInt(s), 1, 1);
                ZonedDateTime zdt = ZonedDateTime.of(ld, LocalTime.of(12, 0), ZoneId.of("America/Toronto"));
                return zdt.toInstant();
            }
        }
        return null;
    }

}
