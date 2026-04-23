package io.gsp26se16.moni.content.dto.request;

import java.util.List;

import lombok.Data;

@Data
public class CreateFullTestRequest {
    String title;
    String skill; // READING, LISTENING, WRITING, SPEAKING
    String testType; // ACADEMIC, GENERAL_TRAINING, BOTH, FULL_TEST, PRACTICE
    Integer duration; // in seconds, optional - auto-set if null
    String status; // DRAFT, PUBLISHED, HIDDEN
    Boolean isPlacement;
    List<Integer> stimulusIds; // ordered by section
}
