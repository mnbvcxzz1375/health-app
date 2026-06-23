package com.atitai.posture.port;

import com.atitai.posture.domain.PostureAnalysis;
import com.atitai.posture.domain.PostureJob;
import com.atitai.posture.domain.PoseInferenceResult;

public interface ExerciseEvaluator {

    PostureAnalysis evaluate(PostureJob job, PoseInferenceResult inferenceResult);
}

