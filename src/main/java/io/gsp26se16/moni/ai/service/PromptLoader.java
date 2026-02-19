package io.gsp26se16.moni.ai.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PromptLoader {

    private final Map<String, String> promptCache = new HashMap<>();

    public String loadPrompt(String filename) {
        return promptCache.computeIfAbsent(filename, this::readFromClasspath);
    }

    public String loadPrompt(String filename, Map<String, String> placeholders) {
        String template = loadPrompt(filename);
        String result = template;

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        return result;
    }

    private String readFromClasspath(String filename) {
        try {
            ClassPathResource resource = new ClassPathResource("prompts/" + filename);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load prompt: " + filename, e);
        }
    }
}
