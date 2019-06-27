
CREATE TABLE IF NOT EXISTS unique_id (
id INT PRIMARY KEY;
)


CREATE TABLE IF NOT EXISTS member (
id INT REFERENCES unique_id(id),
password VARCHAR(128) NOT NULL,
latest_activity TIMESTAMP,
is_leader BIT,
upvotes INT DEFAULT 0,
downvotes INT DEFAULT 0
)


CREATE TABLE IF NOT EXISTS vote (
actionid INT REFERENCES action(id),
memberid INT REFERENCES member(id),
type VARCHAR(32)
)


CREATE TABLE IF NOT EXISTS action (
id INT REFERENCES unique_id(id),
memberid INT REFERENCES member(id),
projectid INT REFERENCES project(id),
type VARCHAR(32)
)


CREATE TABLE IF NOT EXISTS project (
id INT REFERENCES unique_id(id),
authorityid INT REFERENCES authority(id)
)


CREATE TABLE IF NOT EXISTS authority (
id INT REFERENCES unique_id(id)
)


-- FUNCTIONS --

CREATE OR REPLACE FUNCTION leader

CREATE OR REPLACE FUNCTION support

CREATE OR REPLACE FUNCTION protest



CREATE OR REPLACE FUNCTION upvote(
timestamp TIMESTAMP,
member_id integer,
password text,
action_id integer
) RETURNS boolean AS $$
DECLARE var_var type_type;
BEGIN
SELECT fsafsafsa INTO fasfsaf;

IF NOT fasfsa THEN
fafa;
END IF;

RETURN asfsaf(fsagsaga,sagsag);
END;
$$ LANGUAGE plpgsql security definer;



CREATE OR REPLACE FUNCTION downvote

CREATE OR REPLACE FUNCTION actions

CREATE OR REPLACE FUNCTION projects

CREATE OR REPLACE FUNCTION votes

CREATE OR REPLACE FUNCTION trolls


-- utworz uzytkownika app, uprawnienia + hasło (pgcrypto)
CREATE USER app WITH LOGIN ENCRYPTED PASSWORD "qwerty";
GRANT EXECUTE ON FUNCTION * TO app;
CREATE EXTENSION pgcrypto; -- możliwe że trzeba dodać na początek