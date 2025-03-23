package com.tuiken.royaladmin.model.workflows;

import com.tuiken.royaladmin.model.enums.Gender;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class LoadFamilyConfiguration {

    UUID rootId;
    String rootUrl;
    Gender rootGender;
    UUID fatherId;
    String fatherUrl;
    UUID motherId;
    String motherUrl;
    List<UUID> issueIds;
    List<String> issueUrls;

    public void print() {
        System.out.println("=== PRE LOAD CONFIGURATION ===");
        System.out.println("=== Monarch: " + getRootUrl());
        String knownLine = "== Known: " +
                (motherId!=null ? "mother, " : "") +
                (fatherId!=null ? "father, " : "") +
                (issueIds!=null && !issueIds.isEmpty() ? issueIds.size() + " children" : "");
        System.out.println(knownLine);
        System.out.println("== In wiki:");
        if (fatherUrl!=null) System.out.println("= Father: " + fatherUrl);
        if (motherUrl!=null) System.out.println("= Mother: " + motherUrl);
        if (issueUrls!=null && !issueUrls.isEmpty()) {
            System.out.println("= Children: " + issueUrls.size());
            issueUrls.forEach(s-> System.out.println("= Child: " + s));
        }
    }

}
