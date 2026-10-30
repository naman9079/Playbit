package com.playbit.dto;
import java.util.List;
import jakarta.validation.constraints.*;
public class Requests {
    public record Profile(@Pattern(regexp="[a-zA-Z0-9_]{3,20}") @NotBlank String username, @NotBlank @Size(max=40) String displayName,
        @Min(0) @Max(7) int avatar, @NotBlank @Pattern(regexp="English|Hindi") String language,
        @NotBlank @Pattern(regexp="beginner|intermediate|advanced") String proficiency, @NotEmpty @Size(max=3) List<String> subjects) {}
    public record Start(@NotBlank String subjectId, String topicId, @Min(0) @Max(3) int difficulty,
        @Min(1) @Max(12) Integer classLevel, String chapterId) {
        public Start(String subjectId, String topicId, int difficulty) {
            this(subjectId, topicId, difficulty, null, null);
        }
    }
    public record Answer(@NotBlank String questionId, @NotNull @Size(max=100) String answer) {}
    public record Join(@NotBlank @Pattern(regexp="[A-Z0-9]{6}") String code) {
        public Join {
            if (code != null) code = code.replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
        }
    }
    public record Recovery(@NotBlank String matchId) {}
    public record Hint(@NotBlank String matchId, @NotBlank String questionId) {}
    public record StudyChat(@NotBlank @Size(max=1200) String question, @Size(max=24000) String context) {}
    public record StudyQuestionHistory(@Size(max=260) String question, @Size(max=100) String topic, @Size(max=360) String evidence) {}
    public record StudyQuiz(@NotBlank @Size(max=24000) String context, @Size(max=10) java.util.List<String> focusTopics, @Size(max=40) java.util.List<StudyQuestionHistory> recentQuestions) {}
}
