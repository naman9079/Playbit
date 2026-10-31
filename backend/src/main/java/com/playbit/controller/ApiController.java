package com.playbit.controller;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.playbit.dto.Requests;
import com.playbit.repository.*;
import com.playbit.service.*;
@RestController @RequestMapping("/api")
public class ApiController {
    private final Db db;private final SubjectRepository subjects;private final ProfileService profiles;private final MatchService matches;private final MatchmakingService multiplayer;private final ReportService reports;private final AiTutorService ai;private final CurriculumService curriculum;private final OnlineQuestionSearchService onlineQuestions;
    public ApiController(Db db,SubjectRepository subjects,ProfileService profiles,MatchService matches,MatchmakingService multiplayer,ReportService reports,AiTutorService ai,CurriculumService curriculum,OnlineQuestionSearchService onlineQuestions) {this.db=db;this.subjects=subjects;this.profiles=profiles;this.matches=matches;this.multiplayer=multiplayer;this.reports=reports;this.ai=ai;this.curriculum=curriculum;this.onlineQuestions=onlineQuestions;}
    private UUID user(Jwt jwt) {return UUID.fromString(jwt.getSubject());}
    @GetMapping("/health") Object health() {return Map.of("status","ok");}
    @GetMapping("/subjects") Object subjects() {return subjects.findAll();}
    @GetMapping("/subjects/{id}/topics") Object topics(@PathVariable String id) {return db.rows("select * from topics where subject_id=? order by id",id);}
    @GetMapping("/subjects/{id}/curriculum") CurriculumService.Catalog curriculum(@PathVariable String id,@RequestParam(required=false) Integer classLevel) {return curriculum.catalog(id,classLevel);}
    @GetMapping("/me") Object me(@AuthenticationPrincipal Jwt jwt) {return profiles.me(user(jwt));}
    @PutMapping("/me") Object update(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Requests.Profile request) {return profiles.update(user(jwt),request);}
    @GetMapping("/profile/{id}") Object profile(@PathVariable UUID id) {var p=profiles.get(id); p.remove("language");p.remove("preferred_subjects");p.remove("proficiency");return p;}
    @GetMapping("/ratings/me") Object ratings(@AuthenticationPrincipal Jwt jwt) {return db.rows("select * from user_subject_ratings where user_id=? order by subject_id",user(jwt));}
    @PostMapping("/diagnostics/start") Object diagnostic(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Requests.Start request) {return matches.start(user(jwt),request,"diagnostic");}
    @PostMapping("/matches/bot") Object bot(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Requests.Start request) {return matches.start(user(jwt),request,"bot");}
    @PostMapping("/matches/solo") Object solo(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Requests.Start request) {return matches.start(user(jwt),request,"solo");}
    @GetMapping("/matches/{id}") Object match(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) {return matches.get(user(jwt),id);}
    @PostMapping({"/matches/{id}/answers","/diagnostics/{id}/answers"}) Object answer(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody Requests.Answer request) {return matches.answer(user(jwt),id,request);}
    @GetMapping({"/matches/{id}/results","/reports/{id}"}) Object report(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) {matches.get(user(jwt),id);return reports.get(user(jwt),id);}
    @PostMapping("/recovery/start") Object recovery(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Requests.Recovery request) {return matches.recovery(user(jwt),UUID.fromString(request.matchId()));}
    @PostMapping("/rooms/create") Object create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Requests.Start request) {return multiplayer.create(user(jwt),request);}
    @PostMapping("/rooms/join") Object join(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Requests.Join request) {return multiplayer.join(user(jwt),request.code());}
    @PostMapping("/rooms/{id}/ready") Object ready(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) {return multiplayer.ready(user(jwt),id);}
    @PostMapping("/rooms/{id}/leave") Object leave(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) {multiplayer.leave(user(jwt),id);return Map.of("left",true);}
    @PostMapping("/matchmaking/join") Object queue(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Requests.Start request) {return multiplayer.queue(user(jwt),request);}
    @PostMapping("/matchmaking/cancel") Object cancel(@AuthenticationPrincipal Jwt jwt) {return multiplayer.cancel(user(jwt));}
    @GetMapping("/mastery") Object mastery(@AuthenticationPrincipal Jwt jwt) {return db.rows("select u.*,t.name,t.subject_id from user_topic_mastery u join topics t on t.id=u.topic_id where user_id=? order by mastery_score",user(jwt));}
    @GetMapping("/progress") Object progress(@AuthenticationPrincipal Jwt jwt) {
        UUID id=user(jwt);return Map.of("mastery",mastery(jwt),"history",history(jwt,0),"totals",db.one("select count(*) as attempts,coalesce(sum(case when is_correct then 1 else 0 end),0) as correct,coalesce(avg(response_time_ms),0) as average_ms from question_attempts where user_id=?",id),"xp",db.rows("select xp_amount,created_at,reason from xp_events where user_id=? order by created_at desc limit 30",id));
    }
    @GetMapping("/matches/history") Object history(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page) {
        return db.rows("select m.id,m.mode,m.subject_id,m.status,m.created_at,p.score,p.outcome,p.xp_earned,p.rating_after from match_players p join matches m on m.id=p.match_id where p.user_id=? order by m.created_at desc limit 20 offset ?",user(jwt),Math.min(10000,Math.max(0,page))*20);
    }
    @GetMapping("/leaderboards/overall") Object overall(@AuthenticationPrincipal Jwt jwt,@RequestParam(defaultValue="0") int page) {
        String query="select id,username,display_name,avatar_url,total_xp,level,row_number() over(order by total_xp desc,id) as rank from profiles where onboarded=true";
        return Map.of("entries",db.rows(query+" order by total_xp desc,id limit 20 offset ?",Math.min(10000,Math.max(0,page))*20),"me",db.rows("select * from ("+query+") ranked where id=?",user(jwt)));
    }
    @GetMapping("/leaderboards/subject/{subject}") Object board(@AuthenticationPrincipal Jwt jwt,@PathVariable String subject,@RequestParam(defaultValue="0") int page) {
        String query="select p.id,p.username,p.display_name,p.avatar_url,p.level,r.rating,r.provisional,r.ranked_matches_played,row_number() over(order by r.rating desc,p.id) as rank from user_subject_ratings r join profiles p on p.id=r.user_id where r.subject_id=? and r.ranked_matches_played>=5 and p.onboarded=true";
        return Map.of("entries",db.rows(query+" order by rating desc,id limit 20 offset ?",subject,Math.min(10000,Math.max(0,page))*20),"me",db.rows("select * from ("+query+") ranked where id=?",subject,user(jwt)),"eligibility","Complete 5 ranked matches in this subject to qualify.");
    }
    @PostMapping("/ai/hint") Object hint(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Requests.Hint request) {return ai.hint(user(jwt),UUID.fromString(request.matchId()),UUID.fromString(request.questionId()));}
    @PostMapping("/ai/reports/{id}") Object recommend(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id) {return ai.recommendations(user(jwt),id);}
    @PostMapping("/ai/questions/generate") Object generate(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Requests.Start request) {return ai.candidates(user(jwt),request.subjectId());}
    @PostMapping("/study/chat") Object studyChat(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Requests.StudyChat request) {return ai.studyChat(user(jwt),request.question(),request.context(),request.history());}
    @PostMapping(value="/study/extract-image",consumes="multipart/form-data") Object extractStudyImage(@AuthenticationPrincipal Jwt jwt,@RequestPart("file") MultipartFile file) {return ai.extractStudyImage(user(jwt),file);}
    @PostMapping("/study/topic-notes") Object studyTopicNotes(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Requests.StudyTopicNotes request) {return ai.studyTopicNotes(user(jwt),request.title(),request.context());}
    @PostMapping("/study/online-questions") Object onlineQuestions(@Valid @RequestBody Requests.OnlineQuestionSearch request) {return onlineQuestions.search(request.query(),request.seenQuestions());}
}
