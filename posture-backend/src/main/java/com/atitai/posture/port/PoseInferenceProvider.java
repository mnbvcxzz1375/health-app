package com.atitai.posture.port;

import com.atitai.posture.domain.PoseInferenceRequest;
import com.atitai.posture.domain.PoseInferenceResult;

public interface PoseInferenceProvider {

    PoseInferenceResult analyze(PoseInferenceRequest request);
}

