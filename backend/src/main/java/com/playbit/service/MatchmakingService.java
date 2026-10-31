package com.playbit.service;
import java.time.*;
import java.sql.Timestamp;
import java.security.SecureRandom;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.playbit.dto.Requests;
import com.playbit.exception.ApiException;
import com.playbit.repository.Db;

@Service
public class MatchmakingService {
    private static final int INITIAL_RATING_WINDOW = 150;
    private static final int MAX_RATING_WINDOW = 600;
    private static final int RATING_WINDOW_RAMP_SECONDS = 2;
    private static final int MATCHMAKING_SETTLE_SECONDS = 2;
    private final Db db; private final MatchService matches; private final Clock clock;
    public MatchmakingService(Db db,MatchService matches,Clock clock) {this.db=db;this.matches=matches;this.clock=clock;}
    private boolean sameLevel(Object stored,Integer requested) {
        return requested==null?stored==null:stored instanceof Number number && number.intValue()==requested;
    }
    private int ratingWindow(long elapsedSeconds) {
        double progress=Math.min(1.0,Math.max(0,elapsedSeconds)/(double)RATING_WINDOW_RAMP_SECONDS);
        return (int)Math.round(INITIAL_RATING_WINDOW+(MAX_RATING_WINDOW-INITIAL_RATING_WINDOW)*progress);
    }
    @Transactional public Map<String,Object> create(UUID user,Requests.Start r) {
        matches.lock();matches.eligibleForFriend(user,r.subjectId());matches.available(user);
        db.run("delete from matchmaking_queue where user_id=?",user);
        String code; var random=new SecureRandom(); String alphabet="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        do { var s=new StringBuilder(); for(int i=0;i<6;i++) s.append(alphabet.charAt(random.nextInt(alphabet.length())));code=s.toString(); } while(db.count("select count(*) from matches where room_code=?",code)>0);
        UUID id=matches.create(user,r.subjectId(),r.classLevel(),r.chapterId(),r.topicId(),r.difficulty()==0?2:r.difficulty(),"friend",code,5); return matches.state(user,id);
    }
    @Transactional public Map<String,Object> join(UUID user,String code) {
        matches.lock();
        var rooms=db.rows("select * from matches where room_code=? and mode='friend'",code.toUpperCase(Locale.ROOT));
        if(rooms.isEmpty()) throw new ApiException(404,"No room matches this code. Ask your friend to copy the invite link from their room and open that link.");
        var room=rooms.getFirst(); UUID id=UUID.fromString(Db.str(room,"id"));
        if(Db.str(room,"status").equals("cancelled")) throw new ApiException(409,"The host closed this room. Ask your friend to create a new room and share its invite.");
        if(Db.str(room,"status").equals("completed")) throw new ApiException(409,"This game has finished. Ask your friend to create a new room.");
        if(db.count("select count(*) from match_players where match_id=? and user_id=?",id,user)>0) return matches.state(user,id);
        matches.eligibleForFriend(user,Db.str(room,"subject_id")); matches.available(user);
        if(!Db.str(room,"status").equals("waiting") || db.count("select count(*) from match_players where match_id=?",id)>=2) throw new ApiException(409,"This room is full or has already started");
        db.run("delete from matchmaking_queue where user_id=?",user);matches.addPlayer(id,user,Db.str(room,"subject_id"));
        db.run("update matches set version=version+1 where id=?",id);return matches.state(user,id);
    }
    @Transactional public Map<String,Object> ready(UUID user,UUID id) {
        matches.lock();matches.participant(user,id);var room=db.one("select * from matches where id=?",id);
        if(!Db.str(room,"status").equals("waiting") || !Db.str(room,"mode").equals("friend")) throw new ApiException(409,"Room is not waiting");
        db.run("update match_players set ready=true where user_id=? and match_id=?",user,id);
        if(db.count("select count(*) from match_players where match_id=? and ready=true",id)==2) matches.activate(id);
        db.run("update matches set version=version+1 where id=?",id);return matches.state(user,id);
    }
    @Transactional public void leave(UUID user,UUID id) {
        matches.lock();matches.participant(user,id);var room=db.one("select * from matches where id=?",id);
        if(!Db.str(room,"status").equals("waiting")) throw new ApiException(409,"An active match must finish");
        if(Db.str(room,"host_id").equals(user.toString())) db.run("update matches set status='cancelled',version=version+1 where id=?",id);
        else {db.run("delete from match_players where match_id=? and user_id=?",id,user);db.run("update matches set version=version+1 where id=?",id);}
    }
    @Transactional public Map<String,Object> queue(UUID user,Requests.Start request) {
        String subject=request.subjectId();
        matches.lock(); matches.eligible(user,subject,false);
        matches.validateCurriculum(subject,request.classLevel(),request.chapterId(),request.topicId());
        Instant now=clock.instant();
        var existing=db.rows("select * from matchmaking_queue where user_id=?",user);
        if(!existing.isEmpty() && existing.getFirst().get("match_id")!=null) return Map.of("matchId",existing.getFirst().get("match_id"));
        matches.available(user);
        db.run("delete from matchmaking_queue where heartbeat_at<? and match_id is null",Timestamp.from(now.minusSeconds(20)));
        existing=db.rows("select * from matchmaking_queue where user_id=?",user);
        int rating=Db.integer(db.one("select rating from user_subject_ratings where user_id=? and subject_id=?",user,subject),"rating");
        if(existing.isEmpty()) db.run("insert into matchmaking_queue(user_id,subject_id,rating,joined_at,heartbeat_at,class_level,chapter_id,topic_id) values(?,?,?,?,?,?,?,?)",user,subject,rating,Timestamp.from(now),Timestamp.from(now),request.classLevel(),request.chapterId(),request.topicId());
        else if(!subject.equals(Db.str(existing.getFirst(),"subject_id"))
            || !sameLevel(existing.getFirst().get("class_level"),request.classLevel())
            || !Objects.equals(existing.getFirst().get("chapter_id"),request.chapterId())
            || !Objects.equals(existing.getFirst().get("topic_id"),request.topicId()))
            throw new ApiException(409,"Cancel your current search before changing its class, chapter, or topic");
        db.run("update matchmaking_queue set heartbeat_at=? where user_id=?",Timestamp.from(now),user);
        var own=db.one("select * from matchmaking_queue where user_id=?",user);long elapsed=Duration.between(MatchService.instant(own.get("joined_at")),now).toSeconds();
        // Allow simultaneous arrivals to settle, then prioritize the closest eligible rating.
        if(elapsed>=MATCHMAKING_SETTLE_SECONDS) {
            var candidates=db.rows("select * from matchmaking_queue where subject_id=? and user_id<>? and match_id is null and class_level is not distinct from cast(? as smallint) and chapter_id is not distinct from cast(? as varchar(40)) and topic_id is not distinct from cast(? as varchar(40)) and rating between ? and ?",subject,user,request.classLevel(),request.chapterId(),request.topicId(),rating-MAX_RATING_WINDOW,rating+MAX_RATING_WINDOW);
            candidates.sort(Comparator
                .comparingInt((Map<String,Object> candidate)->Math.abs(Db.integer(candidate,"rating")-rating))
                .thenComparing(candidate->MatchService.instant(candidate.get("joined_at")))
                .thenComparing(candidate->Db.str(candidate,"user_id")));
            for(var candidate:candidates) {
                long candidateElapsed=Math.max(0,Duration.between(MatchService.instant(candidate.get("joined_at")),now).toSeconds());
                int sharedWindow=Math.min(ratingWindow(elapsed),ratingWindow(candidateElapsed));
                if(candidateElapsed<MATCHMAKING_SETTLE_SECONDS || Math.abs(Db.integer(candidate,"rating")-rating)>sharedWindow) continue;
                UUID other=UUID.fromString(Db.str(candidate,"user_id"));
                if(db.count("select count(*) from match_players p join matches m on m.id=p.match_id where p.user_id=? and m.status in ('active','waiting')",other)>0) continue;
                int avg=(rating+Db.integer(candidate,"rating"))/2;UUID id=matches.create(user,subject,request.classLevel(),request.chapterId(),request.topicId(),avg<900?1:avg>=1200?3:2,"ranked",null,5);
                matches.addPlayer(id,other,subject); matches.activate(id);
                db.run("update matchmaking_queue set match_id=? where user_id=? or user_id=?",id,user,other);return Map.of("matchId",id);
            }
        }
        return Map.of("status","searching","elapsed",elapsed,"tolerance",ratingWindow(elapsed),"rating",rating);
    }
    @Transactional public Map<String,Object> cancel(UUID user) {
        matches.lock();var existing=db.rows("select match_id from matchmaking_queue where user_id=?",user);
        if(!existing.isEmpty() && existing.getFirst().get("match_id")!=null) return Map.of("matchId",existing.getFirst().get("match_id"));
        db.run("delete from matchmaking_queue where user_id=?",user);return Map.of("cancelled",true);
    }
}
