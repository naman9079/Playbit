CREATE TABLE profiles (
 id uuid PRIMARY KEY, username varchar(20) UNIQUE NOT NULL, display_name varchar(40) NOT NULL,
 avatar_url varchar(100) NOT NULL DEFAULT '0', language varchar(20) NOT NULL DEFAULT 'English',
 proficiency varchar(20) NOT NULL DEFAULT 'beginner', preferred_subjects text NOT NULL DEFAULT '[]',
 onboarded boolean NOT NULL DEFAULT false, total_xp integer NOT NULL DEFAULT 0 CHECK(total_xp>=0),
 level integer NOT NULL DEFAULT 1, created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE subjects (id varchar(30) PRIMARY KEY, name varchar(80) NOT NULL, slug varchar(40) UNIQUE NOT NULL);
CREATE TABLE topics (id varchar(40) PRIMARY KEY, subject_id varchar(30) NOT NULL REFERENCES subjects(id), name varchar(80) NOT NULL, difficulty_metadata text NOT NULL DEFAULT '{}');
CREATE TABLE user_subject_ratings (
 user_id uuid REFERENCES profiles(id), subject_id varchar(30) REFERENCES subjects(id), rating integer NOT NULL DEFAULT 1000,
 provisional boolean NOT NULL DEFAULT true, ranked_matches_played integer NOT NULL DEFAULT 0, assessed boolean NOT NULL DEFAULT false,
 PRIMARY KEY(user_id,subject_id)
);
CREATE TABLE user_topic_mastery (
 user_id uuid REFERENCES profiles(id), topic_id varchar(40) REFERENCES topics(id), mastery_score double precision NOT NULL DEFAULT 50,
 attempts_count integer NOT NULL DEFAULT 0, updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP, PRIMARY KEY(user_id,topic_id)
);
CREATE TABLE questions (
 id uuid PRIMARY KEY, subject_id varchar(30) NOT NULL REFERENCES subjects(id), topic_id varchar(40) NOT NULL REFERENCES topics(id),
 difficulty integer NOT NULL CHECK(difficulty BETWEEN 1 AND 3), question_text text NOT NULL, options_json text NOT NULL,
 correct_answer varchar(100) NOT NULL, explanation text NOT NULL, formula text NOT NULL, steps_json text NOT NULL,
 fingerprint varchar(64) UNIQUE NOT NULL, verified boolean NOT NULL, generation_source varchar(40) NOT NULL
);
CREATE TABLE matches (
 id uuid PRIMARY KEY, subject_id varchar(30) NOT NULL REFERENCES subjects(id), mode varchar(20) NOT NULL,
 status varchar(20) NOT NULL, room_code varchar(6) UNIQUE, host_id uuid NOT NULL REFERENCES profiles(id),
 difficulty integer NOT NULL, topic_id varchar(40) REFERENCES topics(id), question_count integer NOT NULL DEFAULT 5,
 started_at timestamp with time zone, completed_at timestamp with time zone, created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 bot_score integer NOT NULL DEFAULT 0, version integer NOT NULL DEFAULT 0
);
CREATE TABLE match_players (
 match_id uuid REFERENCES matches(id), user_id uuid REFERENCES profiles(id), score integer NOT NULL DEFAULT 0,
 ready boolean NOT NULL DEFAULT false, outcome varchar(20), rating_before integer NOT NULL DEFAULT 1000,
 rating_after integer NOT NULL DEFAULT 1000, xp_earned integer NOT NULL DEFAULT 0, PRIMARY KEY(match_id,user_id)
);
CREATE TABLE match_questions (
 match_id uuid REFERENCES matches(id), question_id uuid REFERENCES questions(id), question_order integer NOT NULL,
 starts_at timestamp with time zone NOT NULL, deadline_at timestamp with time zone NOT NULL,
 bot_answer_at timestamp with time zone, bot_points integer NOT NULL DEFAULT 0,
 PRIMARY KEY(match_id,question_id), UNIQUE(match_id,question_order)
);
CREATE TABLE question_attempts (
 id uuid PRIMARY KEY, match_id uuid NOT NULL REFERENCES matches(id), user_id uuid NOT NULL REFERENCES profiles(id),
 question_id uuid NOT NULL REFERENCES questions(id), submitted_answer varchar(100) NOT NULL, is_correct boolean NOT NULL,
 response_time_ms integer NOT NULL, awarded_points integer NOT NULL, created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(match_id,user_id,question_id), FOREIGN KEY(match_id,user_id) REFERENCES match_players(match_id,user_id),
 FOREIGN KEY(match_id,question_id) REFERENCES match_questions(match_id,question_id)
);
CREATE TABLE xp_events (
 id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES profiles(id), source_id uuid NOT NULL REFERENCES matches(id),
 reason varchar(30) NOT NULL, xp_amount integer NOT NULL CHECK(xp_amount>=0), created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(user_id,source_id,reason)
);
CREATE TABLE reports (
 id uuid PRIMARY KEY, match_id uuid NOT NULL REFERENCES matches(id), user_id uuid NOT NULL REFERENCES profiles(id),
 summary_json text NOT NULL, recommendations_json text NOT NULL, UNIQUE(match_id,user_id)
);
CREATE TABLE matchmaking_queue (
 user_id uuid PRIMARY KEY REFERENCES profiles(id), subject_id varchar(30) NOT NULL REFERENCES subjects(id),
 rating integer NOT NULL, joined_at timestamp with time zone NOT NULL, heartbeat_at timestamp with time zone NOT NULL, match_id uuid REFERENCES matches(id)
);
CREATE TABLE recovery_claims (
 user_id uuid REFERENCES profiles(id), source_match_id uuid REFERENCES matches(id), recovery_match_id uuid NOT NULL REFERENCES matches(id),
 PRIMARY KEY(user_id,source_match_id)
);
CREATE TABLE system_locks (id integer PRIMARY KEY);
INSERT INTO system_locks(id) VALUES(1);
CREATE INDEX attempts_recent ON question_attempts(user_id,created_at DESC);
CREATE INDEX players_history ON match_players(user_id,match_id);
CREATE INDEX questions_selection ON questions(subject_id,topic_id,difficulty);
CREATE INDEX leaderboard_xp ON profiles(total_xp DESC,id);
CREATE INDEX leaderboard_rating ON user_subject_ratings(subject_id,rating DESC,user_id);
CREATE INDEX active_matches ON matches(status,started_at);
CREATE INDEX queue_subject ON matchmaking_queue(subject_id,joined_at);
