package io.gsp26se16.moni.content.dto.request;

import java.util.List;

import lombok.Data;

@Data
public class CreateFullTestRequest {
    String title;
    String skill; // READING, LISTENING, WRITING, SPEAKING
    Integer duration; // in seconds, optional - auto-set if null
    List<Integer> stimulusIds; // ordered by section
}
