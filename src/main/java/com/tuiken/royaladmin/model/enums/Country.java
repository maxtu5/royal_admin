package com.tuiken.royaladmin.model.enums;

import lombok.Getter;

public enum Country {
    ENGLAND(new String[]{"United Kingdom", "England", "Great Britain", "English"}),
    BELGIUM(new String[]{"Belgians"}),
    NORWAY(new String[]{"Norway"}),
    DENMARK(new String[]{"Denmark"}),
    NETHERLANDS(new String[]{"Netherlands"}),
    SPAIN(new String[]{"Spain", "Spanish"}),
    SWEDEN(new String[]{"Sweden"}),
    HOLY_ROMAN_EMPIRE(new String[]{"Holy Roman Emperor"}),
    TUSCANY(new String[]{"Tuscany", "Etruria"}),
    PORTUGAL(new String[]{"Portugal"}),
    PRUSSIA(new String[]{"Prussia"}),
    FRANCE(new String[]{"France", "French", "Franks", "West Francia"}),
    SICILY(new String[]{"Two Sicilies", "Sicily"}),
    POLAND(new String[] {"Poland"}),
    BAVARIA(new String[] {"Bavaria"}),
    SAXONY(new String[] {"Saxony"}),
    SCOTLAND(new String[] {"Scotland", "Scots", "Alba", "Picts"}),
    BOHEMIA(new String[] {"Bohemia"}),
    AUSTRIA(new String[] {"Austria"}),
    ITALY(new String[] {"Italy"}),
    SARDINIA(new String[] {"Sardinia"}),
    JERUSALEM(new String[] {"Jerusalem", "Holy Sepulchre"}),
    CYPRUS(new String[]{"Cyprus"}),
    SAVOY(new String[]{"Savoy"}),
    WURTTEMBERG(new String[]{"Württemberg"}),
    HUNGARY(new String[]{"Hungary","Hungarians"}),
    LUXEMBOURG(new String[]{"Duke of Luxembourg", "Duchess of Luxembourg"}),
    BURGUNDY(new String[]{"Burgundy"}),
    LIECHTENSTEIN(new String[]{"Liechtenstein"}),
    BRUNSWICK(new String[]{"Brunswick"}),
    GREECE(new String[]{"Hellenes", "Greece"}),
    NAPLES(new String[]{"Naples"}),
    BADEN(new String[]{"Duke of Baden"}),
    RUSSIA(new String[]{"Russia", "Moscow"});

    @Getter
    private final String[] keywords;

    Country(String[] keywords) {
        this.keywords = keywords;
    }

    public boolean belongs(String title) {
        String titleUpper = title.toUpperCase();
        for(String key : this.keywords) {
            if (titleUpper.contains(key.toUpperCase())) {
                return true;
            }
        }
        return false;
    }
}
