package io.gsp26se16.moni.practice.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SaveProgressRequest {
    @NotNull
    Integer sessionId;

    @NotNull
    @Size(min = 1)
    @Valid
    List<AnswerRequest> answers;
}
