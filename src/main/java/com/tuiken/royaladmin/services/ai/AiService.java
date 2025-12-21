package com.tuiken.royaladmin.services.ai;

import com.tuiken.royaladmin.model.entities.Monarch;
import com.tuiken.royaladmin.model.workflows.AiEnrichment;

public interface AiService {
    Monarch generateMonarch(String url);
    Monarch generateMonarch(String url, String source);
    String findGender(String name);
    String createDescription(String name);
    AiEnrichment enrichAiMonarch(String wikiData);
}
