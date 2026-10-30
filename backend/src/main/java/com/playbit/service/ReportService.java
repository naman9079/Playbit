package com.playbit.service;
import java.util.*;
import com.playbit.repository.Db;
import com.playbit.util.Json;
import com.playbit.exception.ApiException;
import org.springframework.stereotype.Service;
@Service
public class ReportService {
    private final Db db; private final Json json;
    public ReportService(Db db,Json json) { this.db=db;this.json=json; }
    public List<Map<String,Object>> attempts(UUID user,UUID match) {
        var rows=db.rows("select q.id,q.question_text,q.correct_answer,q.topic_id,t.name as topic,q.difficulty,q.formula,q.steps_json,q.explanation,a.submitted_answer,a.is_correct,a.response_time_ms,a.awarded_points from question_attempts a join questions q on q.id=a.question_id join topics t on t.id=q.topic_id join match_questions mq on mq.match_id=a.match_id and mq.question_id=q.id where a.user_id=? and a.match_id=? order by mq.question_order",user,match);
        for(var a:rows) { a.put("steps",json.read(Db.str(a,"steps_json"))); a.remove("steps_json"); a.put("possibleMisconception",Boolean.TRUE.equals(a.get("is_correct"))?"":Db.str(a,"submitted_answer").isEmpty()?"No answer was recorded before the deadline.":"Review this rule: "+a.get("explanation")+" This is a possible gap, not a diagnosis."); }
        return rows;
    }
    public void create(UUID user,UUID match,int opponent) {
        var attempts=attempts(user,match); int correct=(int)attempts.stream().filter(a->Boolean.TRUE.equals(a.get("is_correct"))).count();
        var player=db.one("select * from match_players where user_id=? and match_id=?",user,match);
        var summary=new LinkedHashMap<String,Object>(); summary.put("correct",correct);summary.put("incorrect",attempts.size()-correct);summary.put("accuracy",Math.round(correct*100.0/attempts.size()));
        summary.put("averageResponseMs",Math.round(attempts.stream().mapToInt(a->Db.integer(a,"response_time_ms")).average().orElse(0))); summary.put("opponentScore",opponent);summary.putAll(player);
        var topics=db.rows("select q.topic_id,t.name,count(*) as attempts,sum(case when a.is_correct then 1 else 0 end) as correct from question_attempts a join questions q on q.id=a.question_id join topics t on t.id=q.topic_id where a.user_id=? and a.match_id=? group by q.topic_id,t.name order by (1.0*sum(case when a.is_correct then 1 else 0 end)/count(*)),q.topic_id",user,match);
        var rec=new LinkedHashMap<String,Object>(); rec.put("source","verified-rules");rec.put("topics",topics);rec.put("weakTopic",topics.getFirst().get("name"));rec.put("strongTopic",topics.getLast().get("name"));
        rec.put("message","Start with "+topics.getFirst().get("name")+". Review the worked solutions, then complete three fresh recovery questions. Accuracy comes before speed.");
        db.run("insert into reports(id,match_id,user_id,summary_json,recommendations_json) values(?,?,?,?,?)",UUID.randomUUID(),match,user,json.write(summary),json.write(rec));
    }
    public Map<String,Object> get(UUID user,UUID match) {
        if(db.count("select count(*) from match_players where user_id=? and match_id=?",user,match)==0) throw new ApiException(403,"Not your match");
        if(!Db.str(db.one("select status from matches where id=?",match),"status").equals("completed")) throw new ApiException(409,"Report unlocks when the match ends");
        var report=db.one("select * from reports where user_id=? and match_id=?",user,match);
        return Map.of("summary",json.read(Db.str(report,"summary_json")),"recommendations",json.read(Db.str(report,"recommendations_json")),"questions",attempts(user,match),"match",db.one("select * from matches where id=?",match));
    }
}
