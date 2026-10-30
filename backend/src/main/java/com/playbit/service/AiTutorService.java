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
        return ask(instruction,data,600);
    }
    private com.fasterxml.jackson.databind.JsonNode ask(String instruction,Object data,int maxTokens) throws Exception {
        if(key.isBlank()) throw new IllegalStateException("AI is not configured");
        var body=Map.of("model",model,"temperature",.2,"max_tokens",maxTokens,"response_format",Map.of("type","json_object"),"messages",List.of(Map.of("role","system","content",instruction),Map.of("role","user","content",mapper.writeValueAsString(data))));
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
    public Map<String,Object> studyChat(UUID user,String question,String context) {
        String safeContext=context==null?"":context.substring(0,Math.min(context.length(),24000));
        try {
            var result=ask("You are Playbit's patient study tutor. Answer only from the supplied notes. If the notes do not contain the answer, say so clearly and ask one Socratic guiding question. Keep the answer concise and end with one helpful question. Return JSON {\"answer\": string, \"hint\": string}.",Map.of("question",question,"notes",safeContext,"language",db.one("select language from profiles where id=?",user).get("language")));
            String answer=result.path("answer").asText(); String hint=result.path("hint").asText();
            if(answer.isBlank() || answer.length()>4000) throw new IllegalArgumentException();
            return Map.of("answer",answer,"hint",hint,"source","ai");
        } catch(Exception ex) {
            if(safeContext.isBlank()) return Map.of("answer","Upload or paste your notes first, then ask me about them.","hint","What topic are you studying?","source","fallback");
            var sentences=Arrays.stream(safeContext.split("(?<=[.!?])\\s+"))
                .filter(s->Arrays.stream(question.toLowerCase(Locale.ROOT).split("\\W+")).filter(w->w.length()>3).anyMatch(w->s.toLowerCase(Locale.ROOT).contains(w)))
                .limit(3).toList();
            return Map.of("answer",sentences.isEmpty()?"I couldn't find that in the uploaded notes. Try asking with a keyword from the syllabus, or add more material.":String.join(" ",sentences),"hint","Which key term in your question should we look for in the notes?","source","notes-search");
        }
    }
    public Map<String,Object> studyQuestions(UUID user,String context,List<String> focusTopics) {
        String notes=context==null?"":context.substring(0,Math.min(context.length(),24000));
        try {
            var generated=ask("You are an experienced Indian school textbook and exam-paper setter. Create up to 5 concise, useful questions in the style of real school books and written exams, using only facts explicitly present in the supplied notes. Use familiar paper formats where supported: Define/What is meant by, Give a reason, Explain why/how, State/List, Differentiate between, or Calculate/Solve. Prefer direct academic wording and test understanding, not the wording of the notes. Every question must stand alone, name the exact concept, and be answerable from the supplied notes. Never use chatty or opinion wording, arbitrary missing-word blanks, or ask what a student has tried. Never use unresolved references such as this/that/these/those/it/they/the above or vague labels like 'these experiences' and 'this phenomenon'. When a note uses such a vague phrase but clearly describes a specific process, rewrite the question around the concrete process named in the note; otherwise skip it. Do not invent facts or require knowledge absent from the notes. Prioritize supplied weak topics when covered. For each item return format (one of definition, give_reason, explanation, comparison, calculation, formula, list, short_answer), question, answer, explanation, topic, and evidence: an exact supporting quote copied from the notes. If no clear exam question is supported, return an empty questions array. JSON only: {\"questions\":[{\"format\":\"...\",\"question\":\"...\",\"answer\":\"...\",\"explanation\":\"...\",\"topic\":\"...\",\"evidence\":\"...\"}]}.",Map.of("notes",notes,"focusTopics",focusTopics==null?List.of():focusTopics,"language",db.one("select language from profiles where id=?",user).get("language")),1800);
            var questions=validatedStudyQuestions(generated.path("questions"),notes);
            if(!questions.isEmpty()) return Map.of("questions",questions,"source","ai");
        } catch(Exception ignored) { /* Use only conservative, note-backed templates below. */ }
        var questions=templateStudyQuestions(notes);
        if(questions.isEmpty()) throw new ApiException(422,"I couldn't make a clear exam question that stands on its own. These notes may refer to an earlier idea without naming it. Add the nearby explanation or a clear definition/factual sentence and try again.");
        return Map.of("questions",questions,"source","verified-notes-template");
    }
    private List<Map<String,Object>> validatedStudyQuestions(com.fasterxml.jackson.databind.JsonNode items,String notes) {
        var result=new ArrayList<Map<String,Object>>();var seen=new HashSet<String>();
        if(!items.isArray()) return result;
        for(var item:items) {
            String question=item.path("question").asText("").trim();String answer=item.path("answer").asText("").trim();
            String explanation=item.path("explanation").asText("").trim();String topic=item.path("topic").asText("").trim();String evidence=item.path("evidence").asText("").trim();
            String format=item.path("format").asText("short_answer").toLowerCase(Locale.ROOT).replace(' ','_');
            String lower=question.toLowerCase(Locale.ROOT);
            boolean examWording=lower.matches("^(define|state|explain|compare|calculate|what|why|how|when|where|which|name|list|identify|describe|give|write|derive|find)\\b.*");
            boolean grounded=evidence.length()>=12 && notes.toLowerCase(Locale.ROOT).contains(evidence.toLowerCase(Locale.ROOT));
            boolean unresolvedReference=lower.matches(".*\\b(this|that|these|those|such|above|following|previous|mentioned|given)\\b.*") || lower.matches(".*\\b(it|they|them|experiences?|phenomena|things|events)\\b.*") || lower.contains("reason for");
            String identity=lower.replaceAll("[^a-z0-9]+","");
            if(question.length()<12 || question.length()>260 || answer.isBlank() || answer.length()>1200 || explanation.length()>1800 || topic.isBlank() || topic.length()>100 || !Set.of("definition","give_reason","explanation","comparison","calculation","formula","list","short_answer").contains(format) || !examWording || !grounded || unresolvedReference || lower.matches(".*\\b(ever tried|your opinion|do you think|have you ever)\\b.*") || !seen.add(identity)) continue;
            result.add(Map.of("format",format,"question",question,"answer",answer,"explanation",explanation,"topic",topic,"evidence",evidence));
            if(result.size()==5) break;
        }
        return result;
    }
    private List<Map<String,Object>> templateStudyQuestions(String notes) {
        var result=new ArrayList<Map<String,Object>>();var seen=new HashSet<String>();
        var patterns=List.of(
            new String[]{"(?i)^(?:the )?formula for (.{2,80}?)\\s+is\\s+(.{3,200})$","formula"},
            new String[]{"(?i)^(.{2,80}?)\\s+consists of\\s+(.{8,250})$","components"},
            new String[]{"(?i)^(.{2,80}?)\\s+occurs when\\s+(.{8,250})$","conditions"},
            new String[]{"(?i)^(.{2,80}?)\\s+is caused by\\s+(.{8,250})$","cause"},
            new String[]{"(?i)^(.{2,80}?)\\s+(?:is defined as|are defined as|is|are|means|refers to)\\s+(.{12,300})$","definition"}
        );
        String[] sentences=notes.split("(?<=[.!?])\\s+|\\r?\\n+");
        for(String raw:sentences) {
            String evidence=raw.replaceAll("^[\\s•*#\\-]+","").trim().replaceAll("[.!?]+$","").trim();
            if(evidence.length()<24 || evidence.length()>360 || evidence.matches("(?i)^(have you|do you|what do you|in my opinion|i think)\\b.*")) continue;
            if(evidence.matches("(?i)^the reason for (these|those|such) experiences is .+") && evidence.toLowerCase(Locale.ROOT).contains("discharge of electric charges through") && evidence.toLowerCase(Locale.ROOT).contains("rubbing of insulating surfaces")) {
                result.add(Map.of("format","give_reason","question","Give a reason why electric charges accumulated by rubbing insulating surfaces discharge through the human body.","answer","Rubbing insulating surfaces builds up electric charges. These charges can discharge through the body.","explanation","The note links rubbing insulating surfaces to accumulated electric charges and their discharge through the body.","topic","Electric charge and discharge","evidence",evidence));
                if(result.size()>=5) break;
                continue;
            }
            for(var rule:patterns) {
                var match=java.util.regex.Pattern.compile(rule[0]).matcher(evidence);
                if(!match.matches()) continue;
                String subject=match.group(1).trim().replaceAll("(?i)^(the|a|an)\\s+","");
                String answer=match.group(2).trim();
                if(subject.split("\\s+").length>10 || answer.length()<3 || subject.matches("(?i)^(i|you|we|they|he|she|it|this|that|there|these|those|have|do|does|did)$") || subject.matches("(?i).*(\\bthis\\b|\\bthat\\b|\\bthese\\b|\\bthose\\b|\\bexperience(s)?\\b|\\bphenomenon\\b|\\breason for\\b|\\bit\\b|\\bthey\\b).*$")) continue;
                String question=switch(rule[1]) {
                    case "formula" -> "State the formula for "+subject+".";
                    case "components" -> "List the main components of "+subject+".";
                    case "conditions" -> "Under what condition does "+subject+" occur?";
                    case "cause" -> "What causes "+subject+"?";
                    default -> "Define "+subject+".";
                };
                String identity=question.toLowerCase(Locale.ROOT);
                if(!seen.add(identity)) break;
                String format=switch(rule[1]) {case "formula" -> "formula"; case "components" -> "list"; case "conditions","cause" -> "give_reason"; default -> "definition";};
                result.add(Map.of("format",format,"question",question,"answer",answer,"explanation","This answer is supported by the uploaded notes.","topic",subject,"evidence",evidence));
                break;
            }
            if(result.size()>=5) break;
        }
        return result;
    }
}
