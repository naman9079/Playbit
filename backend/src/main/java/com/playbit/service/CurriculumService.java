package com.playbit.service;

import java.util.*;
import org.springframework.stereotype.Service;
import com.playbit.exception.ApiException;
import com.playbit.repository.Db;

@Service
public class CurriculumService {
    public record Topic(String id, String name) {}
    public record Chapter(String id, String name, List<Topic> topics) {}
    public record Catalog(List<Integer> classLevels, List<Chapter> chapters) {}

    private record ChapterSpec(String id, String name, List<String> topicIds) {}
    private static final List<Integer> CLASS_LEVELS = java.util.stream.IntStream.rangeClosed(6, 12).boxed().toList();
    private static final Map<String, List<ChapterSpec>> CHAPTERS = Map.of(
        "math", List.of(
            new ChapterSpec("number-sense", "Number Sense", List.of("fractions")),
            new ChapterSpec("algebra", "Algebra and Powers", List.of("algebra", "exponents"))
        ),
        "quant", List.of(
            new ChapterSpec("business-math", "Percentages and Profit or Loss", List.of("percentages", "profit-loss")),
            new ChapterSpec("work-rates", "Work and Rates", List.of("time-work"))
        ),
        "logic", List.of(
            new ChapterSpec("patterns", "Patterns and Codes", List.of("number-series", "coding")),
            new ChapterSpec("spatial-reasoning", "Direction and Spatial Reasoning", List.of("directions"))
        )
    );

    private final Db db;
    public CurriculumService(Db db) { this.db = db; }

    public Catalog catalog(String subject) {
        var names = new HashMap<String, String>();
        for (var row : db.rows("select id,name from topics where subject_id=? order by id", subject))
            names.put(Db.str(row, "id"), Db.str(row, "name"));
        var specs = CHAPTERS.get(subject);
        if (specs == null || names.isEmpty()) throw new ApiException(404, "Subject not found");
        var chapters = specs.stream().map(chapter -> new Chapter(
            chapter.id(), chapter.name(), chapter.topicIds().stream()
                .filter(names::containsKey)
                .map(id -> new Topic(id, names.get(id))).toList()
        )).filter(chapter -> !chapter.topics().isEmpty()).toList();
        return new Catalog(CLASS_LEVELS, chapters);
    }

    public List<String> selectedTopics(String subject, Integer classLevel, String chapterId, String topicId) {
        if (classLevel != null && !CLASS_LEVELS.contains(classLevel))
            throw new ApiException(400, "Choose a class from 6 to 12");
        var catalog = catalog(subject);
        var chapters = catalog.chapters();
        if (chapterId != null) {
            chapters = chapters.stream().filter(chapter -> chapter.id().equals(chapterId)).toList();
            if (chapters.isEmpty()) throw new ApiException(400, "Chapter does not belong to this subject");
        }
        var topics = chapters.stream().flatMap(chapter -> chapter.topics().stream()).toList();
        if (topicId != null) {
            topics = topics.stream().filter(topic -> topic.id().equals(topicId)).toList();
            if (topics.isEmpty()) throw new ApiException(400, "Topic does not belong to this chapter");
        }
        return topics.stream().map(Topic::id).toList();
    }
}
