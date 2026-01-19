-- ENUM table for all referenced types
CREATE TABLE int_enum
(
    id       TINYINT NOT NULL,
    parentid TINYINT REFERENCES int_enum (id) ON DELETE SET NULL,
    type     TEXT    NOT NULL, -- e.g. 'properties', 'likes', 'looking', 'massage', 'answers'
    name     TEXT    NOT NULL,
    PRIMARY KEY (id, type)
);

-- Create user tables (not the advertisers, but the people who use services)
CREATE TABLE user
(
    id     INTEGER PRIMARY KEY,
    name   TEXT NOT NULL,
    age    TEXT,
    height INTEGER,
    weight INTEGER,
    size   INTEGER,
    gender TEXT,
    regd   DATE
);

CREATE TABLE tmp_user_likes
(
    user_id INTEGER NOT NULL REFERENCES user (id),
    like    TEXT    NOT NULL,
    PRIMARY KEY (user_id, like)
);
-- Main partner table
CREATE TABLE partner
(
    id              INTEGER PRIMARY KEY,
    call_number     TEXT,          -- nullable if inactive
    name            TEXT NOT NULL,
    pass            TEXT,
    about           TEXT,
    active_info     TEXT NOT NULL, -- date, true or false
    expect          TEXT,
    age             TEXT,          -- can be "25+"
    height          SMALLINT,
    weight          SMALLINT,
    breast          SMALLINT,
    waist           SMALLINT,
    hips            SMALLINT,
    city            TEXT,
    location_extra  TEXT,
    latitude        REAL,
    longitude       REAL,
    looking_age_min SMALLINT,
    looking_age_max SMALLINT
);

-- Phone props: links phone to int_enum (props)
CREATE TABLE partner_phone_prop
(
    phone_id INTEGER NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    enum_id  TINYINT NOT NULL REFERENCES int_enum (id) ON DELETE CASCADE,
    PRIMARY KEY (phone_id, enum_id)
);


-- Partner props: links partner to int_enum (props)
CREATE TABLE partner_prop
(
    partner_id INTEGER NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    enum_id    TINYINT NOT NULL REFERENCES int_enum (id) ON DELETE CASCADE,
    PRIMARY KEY (partner_id, enum_id)
);

-- Open hours table
CREATE TABLE partner_open_hour
(
    id         INTEGER PRIMARY KEY,
    partner_id INTEGER NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    onday      TINYINT NOT NULL, -- 0=Monday, 6=Sunday
    hours      TEXT    NOT NULL
);

-- Languages table
CREATE TABLE partner_lang
(
    partner_id INTEGER NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    lang       TEXT    NOT NULL,
    PRIMARY KEY (partner_id, lang)
);

-- Answers table: one partner can have many answers, each answer is linked to int_enum (answers)
CREATE TABLE partner_answer
(
    id         INTEGER PRIMARY KEY,
    partner_id INTEGER NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    enum_id    TINYINT NOT NULL REFERENCES int_enum (id) ON DELETE CASCADE,
    answer     TEXT    NOT NULL
);

-- Looking table: links partner to int_enum (looking)
CREATE TABLE partner_looking
(
    partner_id INTEGER NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    enum_id    TINYINT NOT NULL REFERENCES int_enum (id) ON DELETE CASCADE,
    PRIMARY KEY (partner_id, enum_id)
);

-- Massages table: links partner to int_enum (massage)
CREATE TABLE partner_massage
(
    partner_id INTEGER NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    enum_id    TINYINT NOT NULL REFERENCES int_enum (id) ON DELETE CASCADE,
    PRIMARY KEY (partner_id, enum_id)
);


-- Likes table: links partner to int_enum (likes) with status (no, yes, ask)
CREATE TABLE partner_like
(
    partner_id INTEGER NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    enum_id    TINYINT NOT NULL REFERENCES int_enum (id) ON DELETE CASCADE,
    option     TEXT    NOT NULL CHECK (option IN ('no', 'yes', 'ask')),
    PRIMARY KEY (partner_id, enum_id)
);


-- Images table: one partner can have many images
CREATE TABLE partner_img
(
    id         INTEGER PRIMARY KEY,
    partner_id INTEGER NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    ondate     DATE, -- nullable
    path       TEXT    NOT NULL
);

-- Activity log table
CREATE TABLE partner_activity
(
    id          INTEGER PRIMARY KEY,
    partner_id  INTEGER NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    ondate      DATE    NOT NULL,
    description TEXT    NOT NULL
);

CREATE TRIGGER partner_prop_enum_type_check
    BEFORE INSERT
    ON partner_prop
    FOR EACH ROW
BEGIN
    SELECT CASE
               WHEN (SELECT count(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'properties') != 1
                   THEN RAISE(ABORT, 'partner_prop: attempted to insert enum_id not found or not unique for type=props')
               END;
END;

CREATE TRIGGER partner_answer_enum_type_check
    BEFORE INSERT
    ON partner_answer
    FOR EACH ROW
BEGIN
    SELECT CASE
               WHEN (SELECT count(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'answers') != 1
                   THEN RAISE(ABORT,
                              'partner_answer: attempted to insert enum_id not found or not unique for type=answers')
               END;
END;

CREATE TRIGGER partner_looking_enum_type_check
    BEFORE INSERT
    ON partner_looking
    FOR EACH ROW
BEGIN
    SELECT CASE
               WHEN (SELECT count(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'looking') != 1
                   THEN RAISE(ABORT,
                              'partner_looking: attempted to insert enum_id not found or not unique for type=looking')
               END;
END;

CREATE TRIGGER partner_massage_enum_type_check
    BEFORE INSERT
    ON partner_massage
    FOR EACH ROW
BEGIN
    SELECT CASE
               WHEN (SELECT count(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'massage') != 1
                   THEN RAISE(ABORT,
                              'partner_massage: attempted to insert enum_id not found or not unique for type=massage')
               END;
END;

CREATE TRIGGER partner_like_enum_type_check
    BEFORE INSERT
    ON partner_like
    FOR EACH ROW
BEGIN
    SELECT CASE
               WHEN (SELECT count(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'likes') != 1
                   THEN RAISE(ABORT, 'partner_like: attempted to insert enum_id not found or not unique for type=likes')
               END;
END;

CREATE TABLE IF NOT EXISTS partner_list
(
    tag   TEXT    NOT NULL,
    id    INTEGER NOT NULL,
    name  TEXT    NOT NULL,
    age   TEXT,
    image TEXT,
    PRIMARY KEY (tag, id)
);

CREATE VIEW partner_view AS
SELECT p.*,
       -- All props
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id AND e.type = 'properties'
        WHERE pp.partner_id = p.id)    AS properties,
       -- All likes
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_like pl
                 JOIN int_enum e ON pl.enum_id = e.id AND e.type = 'likes'
        WHERE pl.partner_id = p.id)    AS likes,
       -- All massages
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_massage pm
                 JOIN int_enum e ON pm.enum_id = e.id AND e.type = 'massage'
        WHERE pm.partner_id = p.id)    AS massages,
       -- All languages
       (SELECT GROUP_CONCAT(plang.lang, ', ')
        FROM partner_lang plang
        WHERE plang.partner_id = p.id) AS languages,
       -- All looking
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_looking plook
                 JOIN int_enum e ON plook.enum_id = e.id AND e.type = 'looking'
        WHERE plook.partner_id = p.id) AS looking,
       -- All open hours
       (SELECT GROUP_CONCAT(onday || ': ' || hours, ', ')
        FROM partner_open_hour poh
        WHERE poh.partner_id = p.id)   AS open_hours
FROM partner p;
