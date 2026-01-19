package com.tuiken.royaladmin.model.workflows;

import lombok.ToString;

import java.util.List;

@ToString
public class AiEnrichment {
    public String motherUrl;
    public String fatherUrl;
    public List<String> childUrls;
    public String house;
    public String title;
}
