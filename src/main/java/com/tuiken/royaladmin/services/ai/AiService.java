package com.tuiken.royaladmin.services.ai;

import com.tuiken.royaladmin.model.entities.Monarch;

public interface AiService {
    Monarch generateMonarch(String url);
    Monarch generateMonarch(String url, String source);
    String findGender(String name);
    String createDescription(String name);

    }
