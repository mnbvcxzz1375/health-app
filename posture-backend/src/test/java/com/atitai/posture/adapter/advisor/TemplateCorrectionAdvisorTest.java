package com.atitai.posture.adapter.advisor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.atitai.posture.domain.ExerciseType;
import com.atitai.posture.domain.FormIssue;
import com.atitai.posture.domain.PostureAnalysis;
import com.atitai.posture.domain.PostureJob;
import com.atitai.posture.domain.Severity;
import com.atitai.posture.domain.Verdict;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class TemplateCorrectionAdvisorTest {

    private final TemplateCorrectionAdvisor advisor = new TemplateCorrectionAdvisor();

    @Test
    void shouldReturnSuggestionForKnownIssueCode() {
        PostureJob job = new PostureJob();
        job.setExerciseType(ExerciseType.PUSH_UP);

        FormIssue issue = new FormIssue();
        issue.setCode("PUSH_UP_HIP_SAG");
        issue.setSeverity(Severity.MAJOR);

        PostureAnalysis analysis = new PostureAnalysis();
        analysis.setVerdict(Verdict.NEEDS_IMPROVEMENT);
        analysis.setValidFrameRatio(0.9d);
        analysis.setIssues(Collections.singletonList(issue));

        assertFalse(advisor.advise(job, analysis).getSuggestions().isEmpty());
        assertTrue(advisor.advise(job, analysis).getWarnings().get(0).contains("腰腹稳定性"));
    }
}
