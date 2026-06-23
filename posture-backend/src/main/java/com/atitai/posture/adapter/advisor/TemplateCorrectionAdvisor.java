package com.atitai.posture.adapter.advisor;

import com.atitai.posture.domain.AdviceBlock;
import com.atitai.posture.domain.FormIssue;
import com.atitai.posture.domain.PostureAnalysis;
import com.atitai.posture.domain.PostureJob;
import com.atitai.posture.domain.Verdict;
import com.atitai.posture.port.CorrectionAdvisor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class TemplateCorrectionAdvisor implements CorrectionAdvisor {

    @Override
    public AdviceBlock advise(PostureJob job, PostureAnalysis analysis) {
        AdviceBlock block = new AdviceBlock();
        block.setSummary(buildSummary(job, analysis));
        block.setSuggestions(buildSuggestions(analysis));
        block.setWarnings(buildWarnings(analysis));
        return block;
    }

    private String buildSummary(PostureJob job, PostureAnalysis analysis) {
        if (analysis.getVerdict() == Verdict.LOW_CONFIDENCE) {
            return "视频中的人体关键点稳定性不足，建议在全身入镜、侧视角和光线更好的条件下重新拍摄。";
        }
        if (analysis.getIssues().isEmpty()) {
            return job.getExerciseType().name() + " 动作整体较稳定，当前视频里没有发现明显的体态问题。";
        }
        return "本次 " + job.getExerciseType().name() + " 存在 " + analysis.getIssues().size()
            + " 个可改进点，优先处理高严重度问题会更快提升动作质量。";
    }

    private List<String> buildSuggestions(PostureAnalysis analysis) {
        Set<String> suggestions = new LinkedHashSet<String>();
        for (FormIssue issue : analysis.getIssues()) {
            suggestions.add(toSuggestion(issue.getCode()));
        }
        if (suggestions.isEmpty()) {
            suggestions.add("保持当前节奏，继续录制更多样本以验证稳定性。");
        }
        return new ArrayList<String>(suggestions);
    }

    private List<String> buildWarnings(PostureAnalysis analysis) {
        List<String> warnings = new ArrayList<String>();
        if (analysis.getValidFrameRatio() < 0.7d) {
            warnings.add("有效关键点帧比例较低，当前结论更适合作为参考而不是最终判断。");
        }
        for (FormIssue issue : analysis.getIssues()) {
            if ("PUSH_UP_HIP_SAG".equals(issue.getCode()) || "PLANK_HIP_SAG".equals(issue.getCode())) {
                warnings.add("腰腹稳定性不足时，建议先降低次数或缩短保持时间，避免腰部代偿。");
                break;
            }
        }
        return warnings;
    }

    private String toSuggestion(String issueCode) {
        if ("SQUAT_DEPTH_INSUFFICIENT".equals(issueCode) || "LUNGE_DEPTH_INSUFFICIENT".equals(issueCode)) {
            return "下蹲阶段继续把髋部坐向后下方，直到大腿接近平行地面再起身。";
        }
        if ("SQUAT_TRUNK_LEAN".equals(issueCode) || "LUNGE_TRUNK_LEAN".equals(issueCode)) {
            return "收紧核心并抬起胸骨，避免下蹲时身体过度前倾。";
        }
        if ("SQUAT_HIP_EXTENSION_INCOMPLETE".equals(issueCode) || "LUNGE_EXTENSION_INCOMPLETE".equals(issueCode)) {
            return "起身到顶部时把髋部完全伸展，再开始下一次动作。";
        }
        if ("PUSH_UP_DEPTH_INSUFFICIENT".equals(issueCode)) {
            return "下放时让胸口更接近地面，确保肘部弯曲充分后再推起。";
        }
        if ("PUSH_UP_HIP_SAG".equals(issueCode) || "PLANK_HIP_SAG".equals(issueCode)) {
            return "把肋骨向下收、臀部轻微夹紧，让肩髋踝尽量保持一条直线。";
        }
        if ("PUSH_UP_HIP_PIKE".equals(issueCode) || "PLANK_HIP_PIKE".equals(issueCode)) {
            return "降低臀部高度，避免把动作做成倒 V 字。";
        }
        if ("PUSH_UP_LOCKOUT_INCOMPLETE".equals(issueCode)) {
            return "推起到顶部时让手肘基本伸直，但不要耸肩。";
        }
        if ("PLANK_NECK_MISALIGNED".equals(issueCode)) {
            return "视线看向身体前下方，保持头颈与脊柱延长线一致。";
        }
        if ("SQUAT_HEEL_RISE".equals(issueCode)) {
            return "下蹲时把重心放在中足和脚跟，避免脚跟提前抬起。";
        }
        if ("LUNGE_RHYTHM_UNSTABLE".equals(issueCode)) {
            return "保持前后腿切换节奏一致，每次下蹲和起身都尽量等速完成。";
        }
        return "优先对照证据帧修正关键角度，让每次动作的起点和终点更稳定。";
    }
}
