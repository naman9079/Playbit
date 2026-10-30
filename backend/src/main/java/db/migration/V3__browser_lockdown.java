package db.migration;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
/** Browser access is closed automatically; participant Realtime grants are installed separately. */
public class V3__browser_lockdown extends BaseJavaMigration {
    @Override public void migrate(Context context) throws Exception {
        if(!context.getConnection().getMetaData().getDatabaseProductName().equals("PostgreSQL")) return;
        try(var statement=context.getConnection().createStatement()) {
            String[] tables={"profiles","subjects","topics","user_subject_ratings","user_topic_mastery","questions","matches","match_players","match_questions","question_attempts","xp_events","reports","matchmaking_queue","recovery_claims","system_locks"};
            for(String table:tables) {
                statement.execute("ALTER TABLE public."+table+" ENABLE ROW LEVEL SECURITY");
                statement.execute("DO $$ BEGIN IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='anon') THEN REVOKE ALL ON public."+table+" FROM anon; END IF; IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN REVOKE ALL ON public."+table+" FROM authenticated; END IF; END $$");
            }
        }
    }
}
