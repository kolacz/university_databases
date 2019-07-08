-- psql -U postgres -c "DROP SCHEMA public CASCADE;"
-- psql -U postgres -c "DROP DATABASE IF EXISTS student;"
-- psql -U postgres -c "DROP USER IF EXISTS app;"
-- psql -U postgres -c "CREATE DATABASE student;"
-- psql -U postgres -c "CREATE SCHEMA public;"


CREATE EXTENSION pgcrypto;

CREATE TYPE action_t AS ENUM ('support', 'protest');
CREATE TYPE vote_t AS ENUM ('upvote', 'downvote');


CREATE TABLE IF NOT EXISTS unique_ids(
    id INTEGER PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS member(
    id INTEGER REFERENCES unique_ids(id) PRIMARY KEY,
    is_leader BOOLEAN,
    password TEXT NOT NULL,
    latest_activity BIGINT,
    upvotes INTEGER DEFAULT 0,
    downvotes INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS authority(
    id INTEGER REFERENCES unique_ids(id) PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS project(
    id INTEGER REFERENCES unique_ids(id) PRIMARY KEY,
    authority_id INTEGER REFERENCES authority(id)
);

CREATE TABLE IF NOT EXISTS action(
    id INTEGER REFERENCES unique_ids(id) PRIMARY KEY,
    project_id INTEGER REFERENCES project(id),
    member_id INTEGER REFERENCES member(id),
    action_type action_t
);

CREATE TABLE IF NOT EXISTS vote(
    action_id INTEGER REFERENCES action(id), 
    member_id INTEGER REFERENCES member(id),
    vote_type vote_t,
    PRIMARY KEY(action_id, member_id)
);


CREATE ROLE app WITH ENCRYPTED PASSWORD 'qwerty';
ALTER ROLE app WITH LOGIN;

REVOKE ALL
ON ALL TABLES IN SCHEMA public
FROM PUBLIC;

GRANT SELECT, INSERT, UPDATE, DELETE
ON ALL TABLES IN SCHEMA public
TO app;