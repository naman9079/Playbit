package com.playbit.service;
import java.util.*;
import org.springframework.stereotype.Service;
import com.playbit.repository.Db;
@Service
public class AdaptiveLearningService {
    private final Db db;
    public AdaptiveLearningService(Db db) { this.db=db; }
    public int difficulty(UUID user,String subject,String topic) {
        var ratings=db.rows("select rating from user_subject_ratings where user_id=? and subject_id=?",user,subject);
        int rating=ratings.isEmpty()?1000:Db.integer(ratings.getFirst(),"rating");
        var mastery=db.rows("select mastery_score from user_topic_mastery u join topics t on t.id=u.topic_id where u.user_id=? and t.subject_id=? and (cast(? as varchar) is null or topic_id=?)",user,subject,topic,topic);
        double score=mastery.stream().mapToDouble(m->((Number)m.get("mastery_score")).doubleValue()).average().orElse(50);
        var recent=db.rows("select a.is_correct,a.response_time_ms from question_attempts a join questions q on q.id=a.question_id where a.user_id=? and q.subject_id=? order by a.created_at desc limit 10",user,subject);
        double accuracy=recent.stream().mapToInt(a->Boolean.TRUE.equals(a.get("is_correct"))?1:0).average().orElse(.5);
        double speed=recent.stream().mapToInt(a->Db.integer(a,"response_time_ms")).average().orElse(10000);
        if(score<40 || (recent.size()>=3 && accuracy<.4)) return 1;
        if(score>75 && accuracy>=.8 && speed<10000) return 3;
        return rating>=1200?3:rating<900?1:2;
    }
    public void record(UUID user,String topic,boolean correct,int millis) {
        var rows=db.rows("select * from user_topic_mastery where user_id=? and topic_id=?",user,topic);
        double old=rows.isEmpty()?50:((Number)rows.getFirst().get("mastery_score")).doubleValue();
        double target=correct?(millis<=10000?100:85):0;
        double next=Math.round((old*.8+target*.2)*10)/10.0;
        if(rows.isEmpty()) db.run("insert into user_topic_mastery(user_id,topic_id,mastery_score,attempts_count) values(?,?,?,1)",user,topic,next);
        else db.run("update user_topic_mastery set mastery_score=?,attempts_count=attempts_count+1,updated_at=CURRENT_TIMESTAMP where user_id=? and topic_id=?",next,user,topic);
    }
}
