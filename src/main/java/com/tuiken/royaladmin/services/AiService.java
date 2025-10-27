package com.tuiken.royaladmin.services;

import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.enums.PersonStatus;
import org.json.JSONArray;

public interface AiService {
    Monarch generateMonarch(String url);
    Monarch generateMonarch(String url, String source);
    String findGender(String name);
}
