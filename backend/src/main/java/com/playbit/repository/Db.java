package com.playbit.repository;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.playbit.exception.ApiException;
@Repository
public class Db {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(Db.class);
    public final JdbcTemplate jdbc;
    public Db(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public int run(String sql, Object... args) { return jdbc.update(sql,args); }
    public List<Map<String,Object>> rows(String sql, Object... args) { return jdbc.queryForList(sql,args); }
    public Map<String,Object> one(String sql, Object... args) {
        var rows=rows(sql,args);
        if(rows.isEmpty()) {
            // Log the query template only, never user data or bound parameters.
            log.warn("Required database row missing for query: {}",sql);
            throw new ApiException(404,"Record not found");
        }
        return rows.getFirst();
    }
    public int count(String sql, Object... args) { return jdbc.queryForObject(sql,Integer.class,args); }
    public static int integer(Map<String,Object> row,String key) { return ((Number)row.get(key)).intValue(); }
    public static String str(Map<String,Object> row,String key) { return Objects.toString(row.get(key),""); }
}
