ALTER TABLE matches ADD COLUMN class_level smallint CHECK (class_level BETWEEN 6 AND 12);
ALTER TABLE matches ADD COLUMN chapter_id varchar(40);
ALTER TABLE questions ADD COLUMN class_level smallint CHECK (class_level BETWEEN 6 AND 12);
ALTER TABLE matchmaking_queue ADD COLUMN class_level smallint CHECK (class_level BETWEEN 6 AND 12);
ALTER TABLE matchmaking_queue ADD COLUMN chapter_id varchar(40);
ALTER TABLE matchmaking_queue ADD COLUMN topic_id varchar(40) REFERENCES topics(id);
CREATE INDEX queue_curriculum ON matchmaking_queue(subject_id,class_level,chapter_id,topic_id,joined_at);
