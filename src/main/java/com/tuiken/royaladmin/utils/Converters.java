package com.tuiken.royaladmin.utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

public class Converters {

    public static LocalDate toLocalDate(Instant source) {
        return source != null ? source.atZone(ZoneId.systemDefault()).toLocalDate() : null;
    }

    public static Instant toInstant(LocalDate date) {
        return date==null? null : date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }
}
