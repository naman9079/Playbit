package com.playbit.service;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.playbit.repository.Db;
import com.playbit.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AiTutorService {
    private final Db db; private final ReportService reports; private final ObjectMapper mapper; private final QuestionGenerationService generator;
    private final String base,key,model,admins;
    public AiTutorService(Db db,ReportService reports,ObjectMapper mapper,QuestionGenerationService generator,@Value("${playbit.ai.base-url}") String base,@Value("${playbit.ai.key}") String key,@Value("${playbit.ai.model}") String model,@Value("${playbit.ai.admin-ids}") String admins) {this.db=db;this.reports=reports;this.mapper=mapper;this.generator=generator;this.base=base;this.key=key;this.model=model;this.admins=admins;}
    private com.fasterxml.jackson.databind.JsonNode ask(String instruction,Object data) throws Exception {
        if(key.isBlank()) throw new IllegalStateException("AI is not configured");
        var body=Map.of("model",model,"temperature",.2,"max_tokens",600,"response_format",Map.of("type","json_object"),"messages",List.of(Map.of("role","system","content",instruction),Map.of("role","user","content",mapper.writeValueAsString(data))));
        var request=HttpRequest.newBuilder(URI.create(base+"/chat/completions")).timeout(Duration.ofSeconds(15)).header("Authorization","Bearer "+key).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
        HttpResponse<String> response;
        try(var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) { response=http.send(request,HttpResponse.BodyHandlers.ofString()); }
        if(response.statusCode()!=200) throw new IllegalStateException("AI unavailable");
        return mapper.readTree(mapper.readTree(response.body()).path("choices").get(0).path("message").path("content").asText());
    }
    public Map<String,Object> hint(UUID user,UUID match,UUID question) {
        reports.get(user,match); // A tutor can never leak answers during a live round.
        var q=reports.attempts(user,match).stream().filter(a->Db.str(a,"id").equals(question.toString())).findFirst().orElseThrow(()->new ApiException(404,"Question not found"));
        // Only allow the model to select a verified teaching emphasis. All displayed mathematics comes from the generator.
        try {
            var result=ask("Choose how to teach this completed question. Return JSON {\"focus\":\"formula\"|\"steps\"|\"concept\"}. No other fields. Do not invent facts.",Map.of("question",q,"language",db.one("select language from profiles where id=?",user).get("language")));
            String focus=result.path("focus").asText();if(!Set.of("formula","steps","concept").contains(focus)) throw new IllegalArgumentException();
            return Map.of("source","ai-assisted-verified","focus",focus,"formula",q.get("formula"),"steps",q.get("steps"),"explanation",q.get("explanation"));
        } catch(Exception ex) { return Map.of("source","verified-fallback","focus","steps","formula",q.get("formula"),"steps",q.get("steps"),"explanation",q.get("explanation")); }
    }
    public Map<String,Object> recommendations(UUID user,UUID match) {
        var report=reports.get(user,match);
        try {
            var node=ask("Recommend revision priorities from this real completed report. Return JSON {\"topicIds\":[one or more supplied topic_id values],\"strategy\":\"accuracy\"|\"speed\"|\"revision\"}. No prose or statistics.",report);
            var allowed=new HashSet<String>();for(var q:reports.attempts(user,match)) allowed.add(Db.str(q,"topic_id"));
            var selected=new LinkedHashSet<String>();for(var t:node.path("topicIds")) {if(!allowed.contains(t.asText())) throw new IllegalArgumentException(); selected.add(t.asText());}
            String strategy=node.path("strategy").asText(); if(selected.isEmpty() || !Set.of("accuracy","speed","revision").contains(strategy)) throw new IllegalArgumentException();
            return Map.of("source","ai-assisted-verified","topicIds",selected,"strategy",strategy,"message",switch(strategy) {case "speed" -> "Practice these topics with a timer after reviewing the solution steps."; case "accuracy" -> "Work through each step carefully and check your answer before increasing speed."; default -> "Review these formulas and then try a fresh recovery challenge.";});
        } catch(Exception ex) {return Map.of("source","verified-fallback","recommendations",report.get("recommendations"));}
    }
    public Map<String,Object> candidates(UUID user,String subject) {
        if(!List.of(admins.split(",")).contains(user.toString())) throw new ApiException(403,"Question generation is restricted to configured administrators");
        var allowed=db.rows("select id from topics where subject_id=?",subject).stream().map(t->Db.str(t,"id")).toList();if(allowed.isEmpty()) throw new ApiException(400,"Unknown subject");
        try {
            // AI proposes template parameters; arbitrary model-authored answers are never admitted to competitive play.
            var result=ask("Propose 3 varied verified-template candidates. Return JSON {\"candidates\":[{\"topic\":one supplied topic,\"difficulty\":1|2|3,\"seed\":integer}]}. Only these fields.",allowed);
            var candidates=new ArrayList<QuestionGenerationService.Question>();if(!result.path("candidates").isArray() || result.path("candidates").size()!=3) throw new IllegalArgumentException();
            for(var c:result.path("candidates")) {
                String topic=c.path("topic").asText();int difficulty=c.path("difficulty").asInt();if(!allowed.contains(topic) || !c.path("seed").isIntegralNumber()) throw new IllegalArgumentException();
                candidates.add(generator.generate(topic,difficulty,new Random(c.path("seed").asLong())));
            }
            if(candidates.stream().map(QuestionGenerationService.Question::fingerprint).distinct().count()!=3) throw new IllegalArgumentException();
            return Map.of("source","ai-template-candidates","verified",true,"published",false,"candidates",candidates);
        } catch(Exception ex) { throw new ApiException(503,"AI candidate generation unavailable or failed validation. Live games use verified local generators."); }
    }
}
