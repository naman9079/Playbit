package com.playbit.dto;
import java.util.List;
import jakarta.validation.constraints.*;
public class Requests {
    public record Profile(@Pattern(regexp="[a-zA-Z0-9_]{3,20}") @NotBlank String username, @NotBlank @Size(max=40) String displayName,
        @Min(0) @Max(7) int avatar, @NotBlank @Pattern(regexp="English|Hindi") String language,
        @NotBlank @Pattern(regexp="beginner|intermediate|advanced") String proficiency, @NotEmpty @Size(max=3) List<String> subjects) {}
    public record Start(@NotBlank String subjectId, String topicId, @Min(0) @Max(3) int difficulty) {}
    public record Answer(@NotBlank String questionId, @NotNull @Size(max=100) String answer) {}
    public record Join(@NotBlank @Pattern(regexp="[A-Za-z0-9]{6}") String code) {}
    public record Recovery(@NotBlank String matchId) {}
    public record Hint(@NotBlank String matchId, @NotBlank String questionId) {}
}
