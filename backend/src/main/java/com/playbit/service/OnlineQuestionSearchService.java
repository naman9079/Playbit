package com.playbit.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playbit.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Retrieves existing exam questions from public web pages; it never asks an LLM to write questions. */
@Service
public class OnlineQuestionSearchService {
    private static final Pattern QUESTION_START = Pattern.compile("(?i)^(?:define|state|explain|compare|differentiate|calculate|solve|find|determine|evaluate|simplify|prove|show|derive|identify|describe|give|write|name|list|which|what|why|how|when|where|choose|select|construct|draw|complete|fill)\\b.*");
    private static final Pattern OPTION_LINE = Pattern.compile("(?i)^(?:\\(?[a-d]\\)?[.)]|\\(?[1-4]\\)?[.)])\\s+.+$");
    private final ObjectMapper mapper;
    private final String apiKey;

    public OnlineQuestionSearchService(ObjectMapper mapper, @Value("${playbit.search.tavily-key:}") String apiKey) {
        this.mapper = mapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public Map<String,Object> search(String chapterQuery, List<String> seenQuestions) {
        if (apiKey.isBlank()) throw new ApiException(503, "Internet question search is not configured yet. Add TAVILY_API_KEY to the Render backend environment.");
        String query = chapterQuery == null ? "" : chapterQuery.trim();
        if (query.length() < 2) throw new ApiException(400, "Enter a chapter name or topic to search for existing online questions.");
        query = query + " textbook questions exam paper past paper practice questions";

        try {
            var body = Map.of(
                "query", query,
                "search_depth", "basic",
                "topic", "general",
                "max_results", 8,
                "include_raw_content", true,
                "include_answer", false,
                "include_images", false,
                "safe_search", true,
                "language", "en"
            );
            var request = HttpRequest.newBuilder(URI.create("https://api.tavily.com/search"))
                .timeout(Duration.ofSeconds(25))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
            HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
                .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) throw new ApiException(503, "The online question search key is invalid. Check TAVILY_API_KEY in Render.");
            if (response.statusCode() == 429 || response.statusCode() == 432) throw new ApiException(503, "The online question search limit has been reached. Try again after the search credits reset.");
            if (response.statusCode() != 200) throw new ApiException(502, "The web question search service is temporarily unavailable. Try again shortly.");
            return collect(mapper.readTree(response.body()), seenQuestions == null ? List.of() : seenQuestions, query);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException(502, "Could not reach online question sources. Check the backend connection and try again.");
        }
    }

    private Map<String,Object> collect(JsonNode response, List<String> seenQuestions, String query) {
        var seen = new HashSet<String>();
        for (String question : seenQuestions) if (question != null) seen.add(canonical(question));
        var questions = new ArrayList<Map<String,String>>();
        var sources = new ArrayList<Map<String,String>>();
        var sourceUrls = new HashSet<String>();
        var sourceResults = new LinkedHashMap<String,JsonNode>();
        for (JsonNode result : response.path("results")) {
            int beforeSource = questions.size();
            String title = clean(result.path("title").asText("Online source"));
            String url = result.path("url").asText("").trim();
            if (!url.startsWith("https://") || !sourceUrls.add(url)) continue;
            String domain;
            try { domain = URI.create(url).getHost(); } catch (Exception ex) { continue; }
            if (domain == null) continue;
            String snippet = clean(result.path("content").asText(""));
            sources.add(Map.of("title", truncate(title, 180), "url", url, "domain", domain, "snippet", truncate(snippet, 500)));
            sourceResults.put(url,result);

            String raw = result.path("raw_content").asText("");
            var candidates = new LinkedHashSet<String>();
            candidates.addAll(questionLines(raw));
            candidates.addAll(questionLines(snippet));
            for (String candidate : candidates) {
                String normalized = canonical(candidate);
                if (normalized.length() < 20 || !seen.add(normalized)) continue;
                questions.add(Map.of("question", truncate(candidate, 700), "sourceTitle", truncate(title, 180), "sourceUrl", url, "sourceDomain", domain));
                if (questions.size() >= 20 || questions.size() - beforeSource >= 3) break;
            }
            if (questions.size() >= 20 || sources.size() >= 8) break;
        }
        if (questions.isEmpty() && !sourceResults.isEmpty()) {
            questions.addAll(extractQuestionPages(sourceResults, query, seen));
        }
        String message = questions.isEmpty()
            ? "No question text could be extracted from the search snippets or full linked pages. Some pages are scanned images or block extraction; try a more specific chapter/topic or open the original source."
            : "Questions below are excerpts found on the linked pages. Playbit has not generated or rewritten them.";
        return Map.of("query", query, "questions", questions, "sources", sources, "message", message, "source", "web");
    }

    private List<Map<String,String>> extractQuestionPages(Map<String,JsonNode> sourceResults, String query, Set<String> seen) {
        var urls = sourceResults.keySet().stream().limit(5).toList();
        try {
            var body = Map.of("urls",urls,"query",query,"chunks_per_source",3,"extract_depth","advanced","format","markdown");
            var request = HttpRequest.newBuilder(URI.create("https://api.tavily.com/extract"))
                .timeout(Duration.ofSeconds(35))
                .header("Authorization","Bearer "+apiKey)
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
            HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build()
                .send(request,HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429 || response.statusCode() == 432)
                throw new ApiException(503,"The online question search limit has been reached while opening source pages. Try again after the search credits reset.");
            if (response.statusCode() != 200) return List.of();

            var found = new ArrayList<Map<String,String>>();
            for (JsonNode page : mapper.readTree(response.body()).path("results")) {
                String url = page.path("url").asText("").trim();
                JsonNode source = sourceResults.get(url);
                if (source == null) continue;
                String title = clean(source.path("title").asText("Online source"));
                String domain;
                try { domain = URI.create(url).getHost(); } catch (Exception ex) { continue; }
                if (domain == null) continue;
                for (String candidate : questionLines(page.path("raw_content").asText(""))) {
                    String normalized = canonical(candidate);
                    if (normalized.length() < 20 || !seen.add(normalized)) continue;
                    found.add(Map.of("question",truncate(candidate,700),"sourceTitle",truncate(title,180),"sourceUrl",url,"sourceDomain",domain));
                    if (found.size() >= 20) return List.copyOf(found);
                }
            }
            return List.copyOf(found);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> questionLines(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) return List.of();
        String normalized = sourceText.replace("\r", "\n")
            .replaceAll("(?i)<br\\s*/?>", "\n")
            .replaceAll("(?i)</(?:p|div|li|tr|h[1-6]|section|article|blockquote|table)>|<(?:p|div|li|tr|h[1-6]|section|article|blockquote|table)(?:\\s[^>]*)?>", "\n")
            .replaceAll("<[^>]+>", " ")
            .replaceAll("(?i)&nbsp;|&#160;|&#xA0;", " ").replaceAll("(?i)&amp;", "&")
            .replaceAll("(?i)&quot;|&#34;", "\"").replaceAll("(?i)&#39;|&apos;", "'")
            .replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
            .replaceAll("[\\t ]+", " ");
        var found = new LinkedHashSet<String>();
        String[] lines = normalized.split("\\n+|(?<=\\?)\\s+(?=(?:(?:Q(?:uestion)?\\.?\\s*)?\\d{1,3}[.)]|What|Why|How|Which|When|Where|Define|Explain|Calculate|Solve|Find|State|Show|Prove)\\b)|(?<=\\.)\\s+(?=\\d{1,3}[.)]\\s+)");
        for (int i = 0; i < lines.length; i++) {
            String original = lines[i].trim().replaceAll("^[\\s•*#]+", "");
            if (original.length() < 20 || original.length() > 700) continue;
            String stem = original.replaceFirst("(?i)^(?:(?:question|q)\\.?\\s*)?\\d{1,3}[.)\\]:-]\\s*", "").trim();
            boolean blankMarker = stem.matches("(?is).*_{3,}.*") || stem.matches("(?is).*(?:\\\\_\\s*){3,}.*");
            boolean hasQuestionShape = stem.endsWith("?") || blankMarker || QUESTION_START.matcher(stem).matches();
            boolean markLabel = stem.matches("(?is).{20,}\\s*\\[\\s*\\d+\\s*(?:marks?|points?)\\s*].*");
            boolean followedByOptions = i + 1 < lines.length && OPTION_LINE.matcher(lines[i + 1].trim()).matches();

            String candidate = stem;
            if (!hasQuestionShape && !markLabel && !followedByOptions) continue;
            if ((markLabel || followedByOptions) && i + 1 < lines.length) {
                var parts = new ArrayList<String>();
                parts.add(stem);
                int cursor = i + 1;
                while (cursor < lines.length && parts.size() <= 5 && OPTION_LINE.matcher(lines[cursor].trim()).matches()) {
                    parts.add(lines[cursor].trim());
                    cursor++;
                }
                if (parts.size() > 1) { candidate = String.join(" ", parts); i = cursor - 1; }
            }
            if (candidate.length() >= 20 && candidate.length() <= 700) found.add(candidate);
            if (found.size() >= 8) break;
        }
        return List.copyOf(found);
    }

    private String canonical(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim().replaceAll("\\s+", " ");
    }
    private String clean(String text) { return text == null ? "" : text.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim(); }
    private String truncate(String text, int max) { return text.length() <= max ? text : text.substring(0, max); }
}
