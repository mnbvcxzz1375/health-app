package com.atitai.posture.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.atitai.posture.domain.ExerciseType;
import com.atitai.posture.domain.PostureAnalysis;
import com.atitai.posture.domain.PostureJob;
import com.atitai.posture.domain.PoseInferenceResult;
import com.atitai.posture.domain.Verdict;
import org.junit.jupiter.api.Test;

class DefaultExerciseEvaluatorTest {

    private final DefaultExerciseEvaluator evaluator = new DefaultExerciseEvaluator();

    @Test
    void shouldDowngradeToLowConfidenceWhenValidFrameRatioTooLow() {
        PostureJob job = new PostureJob();
        job.setExerciseType(ExerciseType.SQUAT);

        PostureAnalysis analysis = new PostureAnalysis();
        analysis.setExerciseType(ExerciseType.SQUAT);
        analysis.setScore(92.0d);
        analysis.setVerdict(Verdict.STANDARD);
        analysis.setValidFrameRatio(0.52d);

        PoseInferenceResult result = new PoseInferenceResult();
        result.setPreliminaryAnalysis(analysis);

        PostureAnalysis evaluated = evaluator.evaluate(job, result);
        assertSame(analysis, evaluated);
        assertEquals(Verdict.LOW_CONFIDENCE, evaluated.getVerdict());
        assertEquals(60.0d, evaluated.getScore());
    }

    @Test
    void shouldCreateFallbackAnalysisWhenProviderReturnsNothing() {
        PostureJob job = new PostureJob();
        job.setExerciseType(ExerciseType.PLANK);

        PoseInferenceResult result = new PoseInferenceResult();
        PostureAnalysis evaluated = evaluator.evaluate(job, result);

        assertEquals(Verdict.LOW_CONFIDENCE, evaluated.getVerdict());
        assertEquals(ExerciseType.PLANK, evaluated.getExerciseType());
        assertEquals(0.0d, evaluated.getScore());
    }
}
