package io.gsp26se16.moni.ai.writing.service;

import java.util.List;
import java.util.Map;

public interface PromptManagementService {

    List<Map<String, Object>> listAllPrompts();

    String getActiveContent(String skill, String filename);

    String getVersionContent(String skill, String filename, String version);

    String updatePrompt(String skill, String filename, String content, boolean activateImmediately);

    void activateVersion(String skill, String filename, String version);

    List<String> getVersionList(String skill, String filename);

    Map<String, String> loadActiveVersions();
}
