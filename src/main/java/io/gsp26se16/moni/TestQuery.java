package io.gsp26se16.moni;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import io.gsp26se16.moni.ai.writing.repository.AiEvaluationRepository;
import io.gsp26se16.moni.ai.writing.repository.WritingSubmissionRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TestQuery implements CommandLineRunner {
    private final WritingSubmissionRepository wr;
    private final AiEvaluationRepository ar;

    @Override
    public void run(String... args) {
        ar.findBySubmissionId(49L).forEach(e -> {
            System.out.println("EVAL ID=" + e.getId() + " SCORE=" + e.getOverallScore());
            System.out.println("RESULT=" + e.getAnalysisResult());
        });
    }
}
