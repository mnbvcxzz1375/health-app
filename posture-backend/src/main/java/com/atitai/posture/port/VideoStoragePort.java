package com.atitai.posture.port;

import com.atitai.posture.domain.StoredVideo;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

public interface VideoStoragePort {

    StoredVideo store(String jobId, MultipartFile file) throws IOException;
}

