package com.playbit.service;

import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.playbit.dto.Requests;
import com.playbit.exception.ApiException;
import com.playbit.repository.Db;
import com.playbit.util.Json;

@Service
public class MatchService {
    public static final int ROUND_SECONDS=15;
    private final Db db; private final Clock clock; private final ProfileService profiles; private final QuestionService questions; private final CurriculumService curriculum;
    private final AdaptiveLearningService adaptive; private final RatingService ratings; private final XpService xp; private final ReportService reports; private final Json json;
    public MatchService(Db db,Clock clock,ProfileService profiles,QuestionService questions,CurriculumService curriculum,AdaptiveLearningService adaptive,RatingService ratings,XpService xp,ReportService reports,Json json) {
        this.db=db;this.clock=clock;this.profiles=profiles;this.questions=questions;this.curriculum=curriculum;this.adaptive=adaptive;this.ratings=ratings;this.xp=xp;this.reports=reports;this.json=json;
    }
    void lock() { db.one("select id from system_locks where id=1 for update"); }
    private static Integer nullableInteger(Object value) { return value instanceof Number number?number.intValue():null; }
    public static Instant instant(Object value) { if(value instanceof OffsetDateTime d) return d.toInstant(); if(value instanceof Timestamp t) return t.toInstant(); return Instant.parse(value.toString()); }
    public void validateCurriculum(String subject,Integer classLevel,String chapter,String topic) { curriculum.selectedTopics(subject,classLevel,chapter,topic); }
    public void participant(UUID user,UUID match) { if(db.count("select count(*) from match_players where user_id=? and match_id=?",user,match)==0) throw new ApiException(403,"You are not a participant in this match"); }
    void eligible(UUID user,String subject,boolean diagnostic) {
        profiles.ensure(user);
        var profile=db.one("select * from profiles where id=?",user);
        if(!Boolean.TRUE.equals(profile.get("onboarded"))) throw new ApiException(409,"Complete your player profile first");
        var rating=db.one("select * from user_subject_ratings where user_id=? and subject_id=?",user,subject);
        if(!diagnostic && !Boolean.TRUE.equals(rating.get("assessed"))) throw new ApiException(409,"Complete this subject's placement first");
        if(diagnostic && Boolean.TRUE.equals(rating.get("assessed"))) throw new ApiException(409,"Placement is already complete for this subject");
    }
    void available(UUID user) {
        for(var r:db.rows("select m.id from matches m join match_players p on p.match_id=m.id where p.user_id=? and m.status in ('active','waiting')",user)) {
            UUID id=UUID.fromString(Db.str(r,"id")); tick(id);
            if(!Db.str(db.one("select status from matches where id=?",id),"status").equals("completed")) throw new ApiException(409,"Finish or leave your existing match first: "+id);
        }
    }
    @Transactional public Map<String,Object> start(UUID user,Requests.Start request,String mode) {
        lock(); eligible(user,request.subjectId(),mode.equals("diagnostic")); available(user);
        curriculum.selectedTopics(request.subjectId(),request.classLevel(),request.chapterId(),request.topicId());
        db.run("delete from matchmaking_queue where user_id=?",user);
        int difficulty=request.difficulty()==0?adaptive.difficulty(user,request.subjectId(),request.topicId()):request.difficulty();
        if(mode.equals("diagnostic")) difficulty=switch(Db.str(db.one("select proficiency from profiles where id=?",user),"proficiency")) {case "advanced" -> 3; case "intermediate" -> 2; default -> 1;};
        UUID id=create(user,request.subjectId(),request.classLevel(),request.chapterId(),request.topicId(),difficulty,mode,null,mode.equals("recovery")?3:5);
        activate(id); return state(user,id);
    }
    UUID create(UUID user,String subject,Integer classLevel,String chapter,String topic,int difficulty,String mode,String code,int count) {
        UUID id=UUID.randomUUID();
        curriculum.selectedTopics(subject,classLevel,chapter,topic);
        db.run("insert into matches(id,subject_id,mode,status,host_id,difficulty,topic_id,class_level,chapter_id,room_code,question_count) values(?,?,?,'waiting',?,?,?,?,?,?,?)",id,subject,mode,user,difficulty,topic,classLevel,chapter,code,count);
        addPlayer(id,user,subject); return id;
    }
    void addPlayer(UUID match,UUID user,String subject) {
        int rating=Db.integer(db.one("select rating from user_subject_ratings where user_id=? and subject_id=?",user,subject),"rating");
        db.run("insert into match_players(match_id,user_id,rating_before,rating_after) values(?,?,?,?)",match,user,rating,rating);
    }
    void activate(UUID id) {
        var match=db.one("select * from matches where id=?",id);
        var users=db.rows("select user_id from match_players where match_id=? order by user_id",id).stream().map(p->UUID.fromString(Db.str(p,"user_id"))).toList();
        var selected=questions.select(Db.str(match,"subject_id"),nullableInteger(match.get("class_level")),(String)match.get("chapter_id"),(String)match.get("topic_id"),Db.integer(match,"difficulty"),Db.integer(match,"question_count"),users,Db.str(match,"mode").equals("diagnostic"));
        Instant start=clock.instant().plusSeconds(3); var rng=new Random(id.getLeastSignificantBits());
        for(int i=0;i<selected.size();i++) {
            int delay=3+rng.nextInt(10); int botPoints=rng.nextDouble()<(.48+.12*Db.integer(match,"difficulty"))?100+(int)Math.floor(20*(1-delay/15.0)):0;
            Instant round=start.plusSeconds((long)i*ROUND_SECONDS);
            db.run("insert into match_questions(match_id,question_id,question_order,starts_at,deadline_at,bot_answer_at,bot_points) values(?,?,?,?,?,?,?)",id,selected.get(i),i,Timestamp.from(round),Timestamp.from(round.plusSeconds(ROUND_SECONDS)),Timestamp.from(round.plusSeconds(delay)),botPoints);
        }
        db.run("update matches set status='active',started_at=?,version=version+1 where id=?",Timestamp.from(start),id);
    }
    @Transactional(readOnly=true) public Map<String,Object> get(UUID user,UUID id) { return state(user,id); }
    public Map<String,Object> state(UUID user,UUID id) {
        participant(user,id); var match=new LinkedHashMap<>(db.one("select * from matches where id=?",id)); Instant now=clock.instant();
        match.put("roundSeconds",ROUND_SECONDS); match.put("maxSpeedBonus",20); match.put("correctPoints",100);
        match.put("chapter_name",curriculum.chapterName(Db.str(match,"subject_id"),nullableInteger(match.get("class_level")),(String)match.get("chapter_id")));
        match.put("players",db.rows("select p.*,u.display_name,u.username,u.avatar_url from match_players p join profiles u on u.id=p.user_id where p.match_id=? order by u.id",id));
        var current=db.rows("select q.id,q.question_text,q.options_json,q.topic_id,q.difficulty,mq.question_order,mq.starts_at,mq.deadline_at,a.submitted_answer as submitted from match_questions mq join questions q on q.id=mq.question_id left join question_attempts a on a.match_id=mq.match_id and a.question_id=q.id and a.user_id=? where mq.match_id=? and mq.starts_at<=? and mq.deadline_at>?",user,id,Timestamp.from(now),Timestamp.from(now));
        if(!current.isEmpty() && Db.str(match,"status").equals("active")) {
            var q=new LinkedHashMap<>(current.getFirst()); q.put("options",json.read(Db.str(q,"options_json")));q.remove("options_json");
            match.put("question",q);
        } else match.put("question",null);
        var past=db.rows("select q.id,q.correct_answer,a.submitted_answer,a.is_correct,a.awarded_points from match_questions mq join questions q on q.id=mq.question_id left join question_attempts a on a.match_id=mq.match_id and a.question_id=q.id and a.user_id=? where mq.match_id=? and mq.deadline_at<=? order by mq.question_order",user,id,Timestamp.from(now));
        // Timestamp at response preparation, excluding database work from the client countdown.
        match.put("serverNow",clock.instant()); match.put("feedback",past); return match;
    }
    @Transactional public Map<String,Object> answer(UUID user,UUID id,Requests.Answer answer) {
        lock(); participant(user,id);
        var match=db.one("select * from matches where id=?",id); Instant now=clock.instant();
        if(!Db.str(match,"status").equals("active")) throw new ApiException(409,"Match is not active");
        var q=db.one("select q.*,mq.starts_at,mq.deadline_at from match_questions mq join questions q on q.id=mq.question_id where mq.match_id=? and q.id=?",id,UUID.fromString(answer.questionId()));
        if(now.isBefore(instant(q.get("starts_at"))) || !now.isBefore(instant(q.get("deadline_at")))) throw new ApiException(409,"This question is outside its answer window");
        if(db.count("select count(*) from question_attempts where match_id=? and user_id=? and question_id=?",id,user,q.get("id"))>0) throw new ApiException(409,"Answer already submitted");
        if(!((List<?>)json.read(Db.str(q,"options_json"))).contains(answer.answer())) throw new ApiException(400,"Choose one of the supplied options");
        int elapsed=(int)Duration.between(instant(q.get("starts_at")),now).toMillis(); boolean correct=Db.str(q,"correct_answer").equals(answer.answer()); int points=score(correct,elapsed);
        db.run("insert into question_attempts(id,match_id,user_id,question_id,submitted_answer,is_correct,response_time_ms,awarded_points) values(?,?,?,?,?,?,?,?)",UUID.randomUUID(),id,user,q.get("id"),answer.answer(),correct,elapsed,points);
        // Scores and correctness are published at the deadline, so they cannot reveal an answer early.
        db.run("update matches set version=version+1 where id=?",id);
        return Map.of("accepted",true,"questionId",answer.questionId());
    }
    public static int score(boolean correct,int elapsed) { return correct?100+Math.max(0,(int)Math.floor(20*(1-elapsed/15000.0))):0; }
    @Transactional public void tickAll() {
        Instant now=clock.instant(); Timestamp timestamp=Timestamp.from(now);
        var due=db.rows("""
            select m.id from matches m where m.status='active' and (
              exists(select 1 from match_questions q where q.match_id=m.id and q.question_order>=m.settled_rounds and q.deadline_at<=?)
              or (m.mode='bot' and m.bot_score<>coalesce((
                select sum(q.bot_points) from match_questions q where q.match_id=m.id and q.bot_answer_at<=?
              ),0))
            )
            """,timestamp,timestamp);
        if(due.isEmpty()) return;
        lock();
        for(var m:due) tick(UUID.fromString(Db.str(m,"id")));
    }
    void tick(UUID id) {
        var m=db.one("select * from matches where id=?",id); if(!Db.str(m,"status").equals("active")) return;
        Instant now=clock.instant();
        int settled=Db.integer(m,"settled_rounds");
        var ended=db.rows("select mq.* from match_questions mq where mq.match_id=? and mq.question_order>=? and mq.deadline_at<=? order by mq.question_order",id,settled,Timestamp.from(now));
        int botScore=Db.str(m,"mode").equals("bot")?db.count("select coalesce(sum(bot_points),0) from match_questions where match_id=? and bot_answer_at<=?",id,Timestamp.from(now)):0;
        if(ended.isEmpty() && botScore==Db.integer(m,"bot_score")) return;
        // Bot score changes alone do not require recalculating every human player's score.
        for(var p:ended.isEmpty()?List.<Map<String,Object>>of():db.rows("select * from match_players where match_id=? order by user_id",id)) {
            UUID user=UUID.fromString(Db.str(p,"user_id"));
            for(var q:ended)
                db.run("insert into question_attempts(id,match_id,user_id,question_id,submitted_answer,is_correct,response_time_ms,awarded_points) select ?,?,?,?, ?,false,15000,0 where not exists (select 1 from question_attempts where match_id=? and user_id=? and question_id=?)",UUID.randomUUID(),id,user,q.get("question_id"),"",id,user,q.get("question_id"));
            int score=db.count("select coalesce(sum(a.awarded_points),0) from question_attempts a join match_questions mq on mq.match_id=a.match_id and mq.question_id=a.question_id where a.match_id=? and a.user_id=? and mq.deadline_at<=?",id,user,Timestamp.from(now));
            db.run("update match_players set score=? where match_id=? and user_id=?",score,id,user);
        }
        int completed=settled+ended.size();
        db.run("update matches set bot_score=?,settled_rounds=?,version=version+1 where id=?",botScore,completed,id);
        if(completed==Db.integer(m,"question_count")) finish(m,botScore,now);
    }
    void finish(Map<String,Object> m,int botScore,Instant now) {
        UUID id=UUID.fromString(Db.str(m,"id")); String mode=Db.str(m,"mode"),subject=Db.str(m,"subject_id");
        if(db.run("update matches set status='completed',completed_at=? where id=? and status='active'",Timestamp.from(now),id)==0) return;
        db.run("delete from matchmaking_queue where match_id=?",id);
        var players=db.rows("select * from match_players where match_id=? order by user_id",id);
        for(var p:players) {
            UUID user=UUID.fromString(Db.str(p,"user_id")); int own=Db.integer(p,"score"), opponent=players.size()==2?Db.integer(players.stream().filter(r->!r.get("user_id").equals(p.get("user_id"))).findFirst().orElseThrow(),"score"):botScore;
            String outcome=mode.equals("solo")||mode.equals("diagnostic")||mode.equals("recovery")?"complete":own==opponent?"draw":own>opponent?"win":"loss";
            int correct=db.count("select count(*) from question_attempts where match_id=? and user_id=? and is_correct=true",id,user);
            int after=Db.integer(p,"rating_before");
            if(mode.equals("ranked")) {
                var other=players.stream().filter(r->!r.get("user_id").equals(p.get("user_id"))).findFirst().orElseThrow();
                after+=ratings.delta(after,Db.integer(other,"rating_before"),outcome.equals("draw")?.5:outcome.equals("win")?1:0);
                db.run("update user_subject_ratings set rating=?,ranked_matches_played=ranked_matches_played+1,provisional=(ranked_matches_played+1<5) where user_id=? and subject_id=?",after,user,subject);
            }
            if(mode.equals("diagnostic")) {
                after=ratings.diagnostic(Db.str(db.one("select proficiency from profiles where id=?",user),"proficiency"),correct);
                db.run("update user_subject_ratings set rating=?,assessed=true where user_id=? and subject_id=? and assessed=false",after,user,subject);
            }
            for(var a:db.rows("select a.*,q.topic_id from question_attempts a join questions q on q.id=a.question_id where a.match_id=? and a.user_id=?",id,user)) adaptive.record(user,Db.str(a,"topic_id"),Boolean.TRUE.equals(a.get("is_correct")),Db.integer(a,"response_time_ms"));
            int earned=xp.award(user,id,mode,correct,outcome,now);
            db.run("update match_players set outcome=?,rating_after=?,xp_earned=? where match_id=? and user_id=?",outcome,after,earned,id,user);
            reports.create(user,id,opponent);
        }
    }
    @Transactional public Map<String,Object> recovery(UUID user,UUID source) {
        lock(); participant(user,source);
        if(!Db.str(db.one("select status from matches where id=?",source),"status").equals("completed")) throw new ApiException(409,"Finish the source match first");
        var claimed=db.rows("select recovery_match_id from recovery_claims where user_id=? and source_match_id=?",user,source);
        if(!claimed.isEmpty()) return state(user,UUID.fromString(Db.str(claimed.getFirst(),"recovery_match_id")));
        var weak=db.one("select q.subject_id,q.topic_id,avg(case when a.is_correct then 1.0 else 0.0 end) as accuracy from question_attempts a join questions q on q.id=a.question_id where a.match_id=? and a.user_id=? group by q.subject_id,q.topic_id order by accuracy,q.topic_id limit 1",source,user);
        var sourceMatch=db.one("select class_level,chapter_id from matches where id=?",source);
        Integer classLevel=nullableInteger(sourceMatch.get("class_level"));
        var match=start(user,new Requests.Start(Db.str(weak,"subject_id"),Db.str(weak,"topic_id"),1,classLevel,(String)sourceMatch.get("chapter_id")),"recovery");
        db.run("insert into recovery_claims(user_id,source_match_id,recovery_match_id) values(?,?,?)",user,source,match.get("id")); return match;
    }
}
