package com.playbit.service;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.playbit.repository.Db;
@Service
public class XpService {
    private final Db db; private final int completion,correct,win,recovery,botLimit;
    public XpService(Db db,@Value("${playbit.xp.completion}") int completion,@Value("${playbit.xp.correct}") int correct,@Value("${playbit.xp.win}") int win,@Value("${playbit.xp.recovery}") int recovery,@Value("${playbit.xp.bot-daily-limit}") int botLimit) { this.db=db;this.completion=completion;this.correct=correct;this.win=win;this.recovery=recovery;this.botLimit=botLimit; }
    public static int threshold(int level) { return 25*(level-1)*(level+2); }
    public static int level(int xp) { int n=1; while(threshold(n+1)<=xp) n++; return n; }
    public int award(UUID user,UUID match,String mode,int count,String outcome,java.time.Instant now) {
        if(db.count("select count(*) from xp_events where user_id=? and source_id=?",user,match)>0) return 0;
        int reward=completion+correct*count+(outcome.equals("win")?win:0)+(mode.equals("recovery")?recovery:0);
        if(mode.equals("diagnostic")) reward=0;
        if(mode.equals("bot") && db.count("select count(*) from xp_events e join matches m on e.source_id=m.id where e.user_id=? and m.mode='bot' and e.xp_amount>0 and e.created_at>=?",user,java.sql.Timestamp.from(now.atOffset(java.time.ZoneOffset.UTC).toLocalDate().atStartOfDay().toInstant(java.time.ZoneOffset.UTC)))>=botLimit) reward=0;
        db.run("insert into xp_events(id,user_id,source_id,reason,xp_amount,created_at) values(?,?,?,?,?,?)",UUID.randomUUID(),user,match,"match-completed",reward,java.sql.Timestamp.from(now));
        int total=Db.integer(db.one("select total_xp from profiles where id=?",user),"total_xp")+reward;
        db.run("update profiles set total_xp=?,level=? where id=?",total,level(total),user); return reward;
    }
}
