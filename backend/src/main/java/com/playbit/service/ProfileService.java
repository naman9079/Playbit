package com.playbit.service;
import java.util.*;
import com.playbit.dto.Requests;
import com.playbit.repository.Db;
import com.playbit.util.Json;
import com.playbit.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class ProfileService {
    private final Db db; private final Json json;
    public ProfileService(Db db,Json json) { this.db=db;this.json=json; }
    public void ensure(UUID id) {
        if(db.count("select count(*) from profiles where id=?",id)==0) {
            db.run("insert into profiles(id,username,display_name) values(?,?,?)",id,"p_"+id.toString().replace("-", "").substring(0,18),"New player");
            for(var subject:db.rows("select id from subjects")) db.run("insert into user_subject_ratings(user_id,subject_id) values(?,?)",id,subject.get("id"));
        }
    }
    @Transactional public Map<String,Object> me(UUID id) { db.one("select id from system_locks where id=1 for update"); ensure(id); return get(id); }
    public Map<String,Object> get(UUID id) {
        var result=new LinkedHashMap<>(db.one("select * from profiles where id=?",id));
        result.put("subjects",json.read(Db.str(result,"preferred_subjects")));
        result.put("ratings",db.rows("select r.*,s.name from user_subject_ratings r join subjects s on s.id=r.subject_id where user_id=? order by s.id",id));
        result.put("stats",db.one("select count(*) as played,coalesce(sum(case when p.outcome='win' then 1 else 0 end),0) as wins,coalesce(sum(case when p.outcome='loss' then 1 else 0 end),0) as losses from match_players p join matches m on m.id=p.match_id where p.user_id=? and m.status='completed' and m.mode<>'diagnostic'",id));
        result.put("nextLevelXp",XpService.threshold(Db.integer(result,"level")+1));
        result.put("levelStartXp",XpService.threshold(Db.integer(result,"level")));
        return result;
    }
    @Transactional public Map<String,Object> update(UUID id, Requests.Profile p) {
        db.one("select id from system_locks where id=1 for update"); ensure(id);
        if(new HashSet<>(p.subjects()).size()!=p.subjects().size()) throw new ApiException(400,"Duplicate subject");
        for(var subject:p.subjects()) if(db.count("select count(*) from subjects where id=?",subject)==0) throw new ApiException(400,"Unknown subject");
        db.run("update profiles set username=?,display_name=?,avatar_url=?,language=?,proficiency=?,preferred_subjects=?,onboarded=true where id=?",p.username(),p.displayName(),""+p.avatar(),p.language(),p.proficiency(),json.write(p.subjects()),id);
        return get(id);
    }
}
