CREATE TABLE bans
(
    subreddit TEXT
);

CREATE TABLE matches 
(
    user_comment_id     TEXT    PRIMARY KEY
);

CREATE INDEX matches_index on matches (user_comment_id);

CREATE TABLE polls 
(
    user_comment_id     TEXT    PRIMARY KEY,
    poll_comment_id     TEXT,
    poll_id             INTEGER,
    title               TEXT
);

CREATE INDEX polls_index on polls (user_comment_id);

CREATE TABLE poll_options 
(
    poll_id             INTEGER,
    option_num          INTEGER,
    option_value        TEXT,
    
    FOREIGN KEY(poll_id) REFERENCES polls(poll_id) 
);

CREATE TABLE poll_votes 
(
    poll_id             INTEGER, 
    option_num          INTEGER,
    username            TEXT,

    FOREIGN KEY(poll_id) REFERENCES polls(poll_id) 
);


