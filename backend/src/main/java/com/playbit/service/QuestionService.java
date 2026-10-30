package com.playbit.service;
import java.util.*;
import org.springframework.stereotype.Service;
import com.playbit.repository.Db;
import com.playbit.util.Json;
import com.playbit.exception.ApiException;
@Service
public class QuestionService {
    private final Db db; private final Json json; private final QuestionGenerationService generator; private final CurriculumService curriculum;
    public QuestionService(Db db,Json json,QuestionGenerationService generator,CurriculumService curriculum) { this.db=db; this.json=json; this.generator=generator; this.curriculum=curriculum; }
    public List<UUID> select(String subject,Integer classLevel,String chapter,String topic,int baseDifficulty,int count,List<UUID> users,boolean diagnostic) {
        var allowedTopics=curriculum.selectedTopics(subject,classLevel,chapter,topic);
        var topics=allowedTopics.stream().map(id->Map.<String,Object>of("id",id)).toList();
        if(topics.isEmpty()) throw new ApiException(400,"Topic does not belong to this subject");
        var seen=new HashSet<String>();
        for(var user:users) for(var row:db.rows("select q.fingerprint from question_attempts a join questions q on q.id=a.question_id where a.user_id=? order by a.created_at desc limit 150",user)) seen.add(Db.str(row,"fingerprint"));
        var selected=new ArrayList<UUID>(); var rng=new Random(); int offset=rng.nextInt(topics.size());
        var weaknesses=users.size()==1 && !diagnostic && topic==null
            ? db.rows("select t.id,coalesce(u.mastery_score,50) as mastery from topics t left join user_topic_mastery u on u.topic_id=t.id and u.user_id=? where t.subject_id=? order by mastery,t.id",users.getFirst(),subject).stream().filter(row->allowedTopics.contains(Db.str(row,"id"))).toList()
            : List.<Map<String,Object>>of();
        for(int i=0;i<count;i++) {
            int difficulty=diagnostic?Math.max(1,Math.min(3,baseDifficulty+(i%3)-1)):baseDifficulty;
            String t=Db.str(topics.get((i+offset)%topics.size()),"id");
            if(i>=topics.size() && !weaknesses.isEmpty()) t=Db.str(weaknesses.get((i-topics.size())%Math.min(2,weaknesses.size())),"id");
            QuestionGenerationService.Question q=null;
            for(int retry=0;retry<1000;retry++) { var candidate=generator.generate(t,difficulty,classLevel,rng); if(!seen.contains(candidate.fingerprint())) {q=candidate; break;} }
            // Finite template domains can exhaust. Prefer the oldest seen verified bank item,
            // never duplicating a question within this match. Exact repetition is possible here.
            if(q==null) {
                var bank=db.rows("select q.id from questions q left join question_attempts a on a.question_id=q.id where q.topic_id=? and q.difficulty=? and q.verified=true and (cast(? as smallint) is null or q.class_level=? or q.class_level is null) group by q.id order by max(a.created_at) asc nulls first",t,difficulty,classLevel,classLevel);
                var fallback=bank.stream().map(row->UUID.fromString(Db.str(row,"id"))).filter(candidate->!selected.contains(candidate)).findFirst();
                if(fallback.isPresent()) { selected.add(fallback.get()); continue; }
                throw new ApiException(503,"Question pool is busy. Try another topic.");
            }
            seen.add(q.fingerprint());
            var existing=db.rows("select id from questions where fingerprint=?",q.fingerprint()); UUID id;
            if(!existing.isEmpty()) id=UUID.fromString(Db.str(existing.getFirst(),"id"));
            else { id=UUID.randomUUID(); db.run("insert into questions(id,subject_id,topic_id,difficulty,class_level,question_text,options_json,correct_answer,explanation,formula,steps_json,fingerprint,verified,generation_source) values(?,?,?,?,?,?,?,?,?,?,?,?,true,'template')",id,subject,t,difficulty,classLevel,q.text(),json.write(q.options()),q.answer(),q.explanation(),q.formula(),json.write(q.steps()),q.fingerprint()); }
            selected.add(id);
        }
        return selected;
    }
}
