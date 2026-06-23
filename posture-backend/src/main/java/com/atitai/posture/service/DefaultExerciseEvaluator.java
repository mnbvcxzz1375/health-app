package com.atitai.posture.service;

import com.atitai.posture.domain.PostureAnalysis;
import com.atitai.posture.domain.PostureJob;
import com.atitai.posture.domain.PoseInferenceResult;
import com.atitai.posture.domain.Verdict;
import com.atitai.posture.port.ExerciseEvaluator;
import java.util.Collections;
import org.springframework.stereotype.Service;

@Service
public class DefaultExerciseEvaluator implements ExerciseEvaluator {

    @Override
    public PostureAnalysis evaluate(PostureJob job, PoseInferenceResult inferenceResult) {
        PostureAnalysis analysis = inferenceResult.getPreliminaryAnalysis();
        if (analysis == null) {
            analysis = new PostureAnalysis();
            analysis.setExerciseType(job.getExerciseType());
            analysis.setScore(0d);
            analysis.setVerdict(Verdict.LOW_CONFIDENCE);
            analysis.setSummary("未收到可用于评分的体态分析结果。");
            analysis.setIssues(Collections.emptyList());
            analysis.setReps(Collections.emptyList());
            analysis.setEvidenceFrames(Collections.emptyList());
            analysis.setSuggestions(Collections.emptyList());
            analysis.setWarnings(Collections.emptyList());
            analysis.setValidFrameRatio(0d);
            return analysis;
        }

        analysis.setExerciseType(job.getExerciseType());
        if (analysis.getValidFrameRatio() < 0.7d || analysis.getVerdict() == null) {
            analysis.setVerdict(Verdict.LOW_CONFIDENCE);
        }
        if (analysis.getVerdict() == Verdict.LOW_CONFIDENCE) {
            analysis.setScore(Math.min(analysis.getScore(), 60d));
        }
        return analysis;
    }
}
