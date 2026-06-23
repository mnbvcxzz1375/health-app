package com.atitai.posture.port;

import com.atitai.posture.domain.PostureJob;
import java.util.Optional;

public interface PostureJobRepository {

    PostureJob save(PostureJob job);

    Optional<PostureJob> findById(String jobId);
}
