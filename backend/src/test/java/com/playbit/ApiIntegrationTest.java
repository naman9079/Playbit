package com.playbit;

import java.time.*;
import java.sql.Timestamp;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import com.fasterxml.jackson.databind.*;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.playbit.repository.Db;
import com.playbit.service.MatchService;
import com.playbit.service.MatchTicker;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class ApiIntegrationTest {
    @Autowired MockMvc mvc; @Autowired ObjectMapper mapper; @Autowired Db db; @Autowired MatchService matches;
    @MockitoBean Clock clock;
    // Tests advance a fake clock and invoke tickAll explicitly; exclude wall-clock ticks.
    @MockitoBean MatchTicker ticker;
    UUID alice,bob;Instant now;
    @BeforeEach void setup() throws Exception {now=Instant.parse("2030-01-01T12:00:00Z");when(clock.instant()).thenAnswer(i->now);when(clock.getZone()).thenReturn(ZoneOffset.UTC);alice=UUID.randomUUID();bob=UUID.randomUUID();profile(alice);profile(bob);}
    MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder request,UUID user) {return request.with(jwt().jwt(j->j.subject(user.toString()).claim("role","authenticated").audience(List.of("authenticated"))));}
    JsonNode request(MockHttpServletRequestBuilder request,UUID user) throws Exception {return mapper.readTree(mvc.perform(as(request,user)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request,Object value) throws Exception {return request.contentType("application/json").content(mapper.writeValueAsString(value));}
    void profile(UUID user) throws Exception {request(get("/api/me"),user);request(body(put("/api/me"),Map.of("username","u"+user.toString().replace("-", "").substring(0,15),"displayName","Player "+user.toString().substring(0,5),"avatar",2,"language","English","proficiency","intermediate","subjects",List.of("math","quant","logic"))),user);}
    void assessed(UUID user) {db.run("update user_subject_ratings set assessed=true where user_id=?",user);}
    JsonNode start(UUID user,String mode,String subject) throws Exception {return request(body(post("/api/"+(mode.equals("diagnostic")?"diagnostics/start":"matches/"+mode)),Map.of("subjectId",subject,"difficulty",2)),user);}
    List<Map<String,Object>> questions(String id) {return db.rows("select q.*,mq.starts_at,mq.deadline_at from questions q join match_questions mq on mq.question_id=q.id where mq.match_id=? order by mq.question_order",UUID.fromString(id));}
    void answerAll(UUID user,String id,boolean correct) throws Exception {for(var q:questions(id)){now=MatchService.instant(q.get("starts_at")).plusSeconds(2);String answer=correct?Db.str(q,"correct_answer"):mapper.readTree(Db.str(q,"options_json")).findValuesAsText("unused").stream().findFirst().orElse("");if(!correct)for(var option:mapper.readTree(Db.str(q,"options_json")))if(!option.asText().equals(Db.str(q,"correct_answer"))){answer=option.asText();break;}request(body(post("/api/matches/"+id+"/answers"),Map.of("questionId",q.get("id").toString(),"answer",answer)),user);}}
    void finish(String id) {var q=questions(id);now=MatchService.instant(q.getLast().get("deadline_at")).plusSeconds(1);matches.tickAll();}
    void duel(String id) throws Exception {for(var q:questions(id)){now=MatchService.instant(q.get("starts_at")).plusSeconds(2);String correct=Db.str(q,"correct_answer"),wrong="";for(var o:mapper.readTree(Db.str(q,"options_json")))if(!o.asText().equals(correct)){wrong=o.asText();break;}request(body(post("/api/matches/"+id+"/answers"),Map.of("questionId",q.get("id").toString(),"answer",correct)),alice);request(body(post("/api/matches/"+id+"/answers"),Map.of("questionId",q.get("id").toString(),"answer",wrong)),bob);}}
    @Test void authenticationRejectsMissingForgedWrongAudienceAndExpiredTokens() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());mvc.perform(get("/api/me").header("Authorization","Bearer fake.jwt.token")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization","Bearer "+token(alice,"authenticated",Instant.now().plusSeconds(300),"wrong-signing-secret-12345678901234567890"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization","Bearer "+token(alice,"wrong",Instant.now().plusSeconds(300),null))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization","Bearer "+token(alice,"authenticated",Instant.now().minusSeconds(300),null))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").header("Authorization","Bearer "+token(alice,"authenticated",Instant.now().plusSeconds(300),null))).andExpect(status().isOk());
    }
    String token(UUID id,String audience,Instant expiry,String secret){String key=secret==null?"this-is-a-test-only-signing-key-with-at-least-32-bytes":secret;var encoder=new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(key.getBytes(),"HmacSHA256")));var claims=JwtClaimsSet.builder().subject(id.toString()).issuer("https://wwekwmfjckgghbewptly.supabase.co/auth/v1").audience(List.of(audience)).claim("role","authenticated").issuedAt(Instant.now().minusSeconds(600)).expiresAt(expiry).build();return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();}
    @Test void profileValidationAndUserIsolation() throws Exception {var a=request(get("/api/me"),alice);assertEquals(alice.toString(),a.path("id").asText());assertNotEquals(a.path("username"),request(get("/api/me"),bob).path("username"));mvc.perform(as(body(put("/api/me"),Map.of("username","bad spaces","displayName","X","avatar",20,"language","English","proficiency","beginner","subjects",List.of("math"))),alice)).andExpect(status().isBadRequest());}
    @Test void diagnosticInitializesOnlyItsSubjectAndCannotBeRepeated() throws Exception {
        var m=start(alice,"diagnostic","math");String id=m.path("id").asText();answerAll(alice,id,true);finish(id);
        var report=request(get("/api/reports/"+id),alice);assertEquals(1160,report.path("summary").path("rating_after").asInt());assertEquals(0,report.path("summary").path("xp_earned").asInt());
        assertEquals(1,db.count("select count(*) from user_subject_ratings where user_id=? and assessed=true",alice));
        mvc.perform(as(body(post("/api/diagnostics/start"),Map.of("subjectId","math","difficulty",0)),alice)).andExpect(status().isConflict());
    }
    @Test void botAnswersAreTimedAuthorizedHiddenAndIdempotent() throws Exception {
        assessed(alice);String id=start(alice,"bot","math").path("id").asText();var first=questions(id).getFirst();var payload=Map.of("questionId",first.get("id").toString(),"answer",first.get("correct_answer"));
        mvc.perform(as(body(post("/api/matches/"+id+"/answers"),payload),alice)).andExpect(status().isConflict());now=MatchService.instant(first.get("starts_at")).plusSeconds(2);
        var current=request(get("/api/matches/"+id),alice);assertFalse(current.path("question").has("correct_answer"));assertFalse(current.path("question").has("explanation"));assertFalse(current.path("question").has("steps"));
        mvc.perform(as(get("/api/matches/"+id),bob)).andExpect(status().isForbidden());mvc.perform(as(body(post("/api/matches/"+id+"/answers"),payload),bob)).andExpect(status().isForbidden());
        request(body(post("/api/matches/"+id+"/answers"),payload),alice);mvc.perform(as(body(post("/api/matches/"+id+"/answers"),payload),alice)).andExpect(status().isConflict());
        assertTrue(request(get("/api/matches/"+id),alice).path("feedback").isEmpty());assertEquals(0,request(get("/api/matches/"+id),alice).path("players").get(0).path("score").asInt());
        mvc.perform(as(get("/api/reports/"+id),alice)).andExpect(status().isConflict());
        mvc.perform(as(body(post("/api/ai/hint"),Map.of("matchId",id,"questionId",first.get("id").toString())),alice)).andExpect(status().isConflict());
        now=MatchService.instant(first.get("deadline_at"));mvc.perform(as(body(post("/api/matches/"+id+"/answers"),payload),alice)).andExpect(status().isConflict());finish(id);
        var report=request(get("/api/reports/"+id),alice);assertEquals(5,report.path("questions").size());assertEquals(1,report.path("summary").path("correct").asInt());assertEquals(117,report.path("summary").path("score").asInt());assertEquals(1000,report.path("summary").path("rating_after").asInt());
        int total=Db.integer(db.one("select total_xp from profiles where id=?",alice),"total_xp");matches.tickAll();request(get("/api/matches/"+id+"/results"),alice);assertEquals(total,Db.integer(db.one("select total_xp from profiles where id=?",alice),"total_xp"));assertEquals(1,db.count("select count(*) from xp_events where user_id=? and source_id=?",alice,UUID.fromString(id)));
    }
    @Test void roundsAdvanceWithoutTickerAndSettledRoundsAreNotWrittenAgain() throws Exception {
        assessed(alice);
        String id=start(alice,"solo","math").path("id").asText();
        UUID match=UUID.fromString(id);
        var rounds=questions(id);
        now=MatchService.instant(rounds.getFirst().get("deadline_at"));
        // The next question is available at its scheduled boundary, even before settlement.
        var state=request(get("/api/matches/"+id),alice);
        assertEquals(rounds.get(1).get("id").toString(),state.path("question").path("id").asText());
        assertFalse(state.path("question").has("correct_answer"));
        matches.tickAll();
        int version=Db.integer(db.one("select version from matches where id=?",match),"version");
        assertEquals(1,db.count("select settled_rounds from matches where id=?",match));
        matches.tickAll();
        assertEquals(version,Db.integer(db.one("select version from matches where id=?",match),"version"));
        assertEquals(1,db.count("select count(*) from question_attempts where match_id=?",match));
        // Catch up across multiple deadlines and finish exactly once.
        finish(id);
        assertEquals(5,db.count("select settled_rounds from matches where id=?",match));
        assertEquals(5,db.count("select count(*) from question_attempts where match_id=?",match));
        assertEquals("completed",request(get("/api/matches/"+id),alice).path("status").asText());
    }
    @Test void freshQuestionsRecoveryReportsAndLeaderboardPersist() throws Exception {
        assessed(alice);String id=start(alice,"solo","quant").path("id").asText();answerAll(alice,id,true);finish(id);
        var report=request(get("/api/reports/"+id),alice);assertEquals(100,report.path("summary").path("accuracy").asInt());assertEquals(80,report.path("summary").path("xp_earned").asInt());
        var recovery=request(body(post("/api/recovery/start"),Map.of("matchId",id)),alice);String rid=recovery.path("id").asText();assertEquals(3,questions(rid).size());
        assertEquals(rid,request(body(post("/api/recovery/start"),Map.of("matchId",id)),alice).path("id").asText());
        var old=new HashSet<>(questions(id).stream().map(q->q.get("fingerprint")).toList());assertTrue(questions(rid).stream().noneMatch(q->old.contains(q.get("fingerprint"))));answerAll(alice,rid,true);finish(rid);
        assertEquals(80,request(get("/api/reports/"+rid),alice).path("summary").path("xp_earned").asInt());assertEquals(160,request(get("/api/me"),alice).path("total_xp").asInt());
        assertEquals(2,request(get("/api/me"),alice).path("level").asInt());assertEquals(8,request(get("/api/progress"),alice).path("totals").path("attempts").asInt());
        assertEquals(160,request(get("/api/leaderboards/overall"),alice).path("me").get(0).path("total_xp").asInt());assertTrue(request(get("/api/leaderboards/subject/quant"),alice).path("me").isEmpty());
        assertEquals("verified-fallback",request(post("/api/ai/reports/"+id),alice).path("source").asText());
    }
    @Test void friendDuelHasSameQuestionsRecoversAndRejectsThirdPlayer() throws Exception {
        var room=request(body(post("/api/rooms/create"),Map.of("subjectId","math","difficulty",2)),alice);String id=room.path("id").asText();String code=room.path("room_code").asText();request(body(post("/api/rooms/join"),Map.of("code",code)),bob);
        UUID third=UUID.randomUUID();profile(third);mvc.perform(as(body(post("/api/rooms/join"),Map.of("code",code)),third)).andExpect(status().isConflict());
        request(post("/api/rooms/"+id+"/ready"),alice);request(post("/api/rooms/"+id+"/ready"),bob);
        now=MatchService.instant(questions(id).getFirst().get("starts_at")).plusSeconds(1);
        assertEquals(request(get("/api/matches/"+id),alice).path("question").path("id"),request(get("/api/matches/"+id),bob).path("question").path("id"));
        duel(id);finish(id);
        assertEquals("win",request(get("/api/reports/"+id),alice).path("summary").path("outcome").asText());assertEquals("loss",request(get("/api/reports/"+id),bob).path("summary").path("outcome").asText());
        assertEquals(1000,request(get("/api/reports/"+id),alice).path("summary").path("rating_after").asInt());assertEquals(130,request(get("/api/reports/"+id),alice).path("summary").path("xp_earned").asInt());
        assertEquals(0,db.count("select count(*) from user_subject_ratings where user_id in (?,?) and assessed=true",alice,bob));
        mvc.perform(as(body(post("/api/matchmaking/join"),Map.of("subjectId","math","difficulty",0)),alice)).andExpect(status().isConflict());
        // A private game must not consume or complete the player's placement.
        assertEquals("diagnostic",start(alice,"diagnostic","math").path("mode").asText());
    }
    @Test void friendRoomsStillRequireProfilesAndOnlyOneUnfinishedMatch() throws Exception {
        UUID newcomer=UUID.randomUUID();
        mvc.perform(as(body(post("/api/rooms/create"),Map.of("subjectId","math","difficulty",2)),newcomer)).andExpect(status().isConflict());
        var room=request(body(post("/api/rooms/create"),Map.of("subjectId","math","difficulty",2)),alice);
        var invite=Map.of("code",room.path("room_code").asText());
        mvc.perform(as(body(post("/api/rooms/join"),invite),newcomer)).andExpect(status().isConflict());
        var other=request(body(post("/api/rooms/create"),Map.of("subjectId","logic","difficulty",2)),bob);
        mvc.perform(as(body(post("/api/rooms/join"),invite),bob)).andExpect(status().isConflict());
        request(post("/api/rooms/"+other.path("id").asText()+"/leave"),bob);
        assertEquals(room.path("id"),request(body(post("/api/rooms/join"),invite),bob).path("id"));
    }
    @Test void roomInvitesNormalizeCopiedCodesAndExplainMissingOrClosedRooms() throws Exception {
        var room=request(body(post("/api/rooms/create"),Map.of("subjectId","math","difficulty",2)),alice);
        String id=room.path("id").asText(),code=room.path("room_code").asText().toLowerCase(Locale.ROOT);
        assertEquals(id,request(body(post("/api/rooms/join"),Map.of("code","  "+code.substring(0,3)+" "+code.substring(3)+"\n")),bob).path("id").asText());
        // Reopening an invite for a current participant is idempotent.
        assertEquals(id,request(body(post("/api/rooms/join"),Map.of("code",code)),bob).path("id").asText());
        mvc.perform(as(body(post("/api/rooms/join"),Map.of("code","000000")),bob))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("No room matches this code")));
        request(post("/api/rooms/"+id+"/leave"),alice);
        mvc.perform(as(body(post("/api/rooms/join"),Map.of("code",code)),bob))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith("The host closed this room")));
        mvc.perform(as(body(post("/api/rooms/join"),Map.of("code","abc")),bob)).andExpect(status().isBadRequest());
    }
    @Test void rankedQueueIsAtomicAndRatingUpdatesAreZeroSum() throws Exception {
        assessed(alice);assessed(bob);request(body(post("/api/matchmaking/join"),Map.of("subjectId","logic","difficulty",0)),alice);var paired=request(body(post("/api/matchmaking/join"),Map.of("subjectId","logic","difficulty",0)),bob);String id=paired.path("matchId").asText();assertFalse(id.isEmpty());
        assertEquals(id,request(body(post("/api/matchmaking/join"),Map.of("subjectId","logic","difficulty",0)),alice).path("matchId").asText());assertEquals(id,request(post("/api/matchmaking/cancel"),alice).path("matchId").asText());
        duel(id);finish(id);
        assertEquals(1016,request(get("/api/reports/"+id),alice).path("summary").path("rating_after").asInt());assertEquals(984,request(get("/api/reports/"+id),bob).path("summary").path("rating_after").asInt());assertEquals(0,db.count("select count(*) from matchmaking_queue where match_id=?",UUID.fromString(id)));
    }
    @Test void botFarmingCapDoesNotStopLearningOrGrantDuplicateXp() throws Exception {
        assessed(alice);
        for(int i=0;i<11;i++) {
            String id=start(alice,"bot","math").path("id").asText();finish(id);
            int earned=request(get("/api/reports/"+id),alice).path("summary").path("xp_earned").asInt();
            if(i<10) assertTrue(earned>=30);else assertEquals(0,earned);
        }
        assertEquals(55,db.count("select count(*) from question_attempts where user_id=?",alice));
        assertEquals(10,db.count("select count(*) from xp_events where user_id=? and xp_amount>0",alice));
    }
    @Test void staleQueueExpiresAndCancelledRoomCannotBeJoined() throws Exception {
        assessed(alice);assessed(bob);
        request(body(post("/api/matchmaking/join"),Map.of("subjectId","math","difficulty",0)),alice);
        now=now.plusSeconds(21);
        assertEquals("searching",request(body(post("/api/matchmaking/join"),Map.of("subjectId","math","difficulty",0)),bob).path("status").asText());
        assertEquals(0,db.count("select count(*) from matchmaking_queue where user_id=?",alice));request(post("/api/matchmaking/cancel"),bob);
        var room=request(body(post("/api/rooms/create"),Map.of("subjectId","math","difficulty",1)),alice);
        request(post("/api/rooms/"+room.path("id").asText()+"/leave"),alice);
        mvc.perform(as(body(post("/api/rooms/join"),Map.of("code",room.path("room_code").asText())),bob)).andExpect(status().isConflict());
    }
}
