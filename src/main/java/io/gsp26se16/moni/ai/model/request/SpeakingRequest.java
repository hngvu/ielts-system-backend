package io.gsp26se16.moni.ai.model.request;

import org.springframework.web.multipart.MultipartFile;

import lombok.Data;

@Data
public class SpeakingRequest {
    private MultipartFile audio;
    private String question;
}
