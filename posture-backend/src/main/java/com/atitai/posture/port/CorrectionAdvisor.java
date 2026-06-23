package com.atitai.posture.port;

import com.atitai.posture.domain.AdviceBlock;
import com.atitai.posture.domain.PostureAnalysis;
import com.atitai.posture.domain.PostureJob;

public interface CorrectionAdvisor {

    AdviceBlock advise(PostureJob job, PostureAnalysis analysis);
}

