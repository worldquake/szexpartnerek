PRAGMA foreign_keys = ON;

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
    regd   DATE,
    ts     DATETIME DEFAULT CURRENT_TIMESTAMP
);
CREATE TRIGGER update_user_ingestion_date
    AFTER UPDATE
    ON user
    FOR EACH ROW
BEGIN
    UPDATE user SET ts = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;
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
    looking_age_max SMALLINT,
    ts              DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TRIGGER update_partner_ingestion_date
    AFTER UPDATE
    ON partner
    FOR EACH ROW
BEGIN
    UPDATE partner SET ts = CURRENT_TIMESTAMP WHERE id = NEW.id;
END;
-- Phone props: links phone to int_enum (props)
CREATE TABLE partner_phone_prop
(
    partner_id INTEGER NOT NULL REFERENCES partner (id) ON DELETE CASCADE,
    enum_id    TINYINT NOT NULL REFERENCES int_enum (id) ON DELETE CASCADE,
    PRIMARY KEY (partner_id, enum_id)
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

-- Feedback tables
CREATE TABLE user_partner_feedback
(
    id         INTEGER,
    user_id    INTEGER  NOT NULL REFERENCES user (id) ON DELETE CASCADE,
    enum_id    INTEGER  NOT NULL REFERENCES int_enum (id) ON DELETE CASCADE,
    partner_id INTEGER REFERENCES partner (id) ON DELETE CASCADE,
    name       TEXT,
    age        TINYINT,
    after_name TEXT,
    useful     INTEGER  NOT NULL,
    ts         DATETIME NOT NULL,
    PRIMARY KEY (id, user_id)
);
CREATE TRIGGER upfb_enum_check
    BEFORE INSERT
    ON user_partner_feedback
    FOR EACH ROW
BEGIN
    SELECT CASE
               WHEN (SELECT count(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'fbtype') != 1
                   THEN RAISE(ABORT,
                              'user_partner_feedback: attempted to insert enum_id not found or not unique for type=fbtype')
               END;
END;

CREATE TABLE user_partner_feedback_rating
(
    fbid    INTEGER NOT NULL REFERENCES user_partner_feedback (id) ON DELETE CASCADE,
    enum_id INTEGER NOT NULL, -- references int_enum(id) where int_enum.type='fbrtype'
    val     TINYINT,          -- nullable
    PRIMARY KEY (fbid, enum_id)
);
CREATE TRIGGER upfb_rating_enum_check
    BEFORE INSERT
    ON user_partner_feedback_rating
    FOR EACH ROW
BEGIN
    SELECT CASE
               WHEN (SELECT count(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'fbrtype') != 1
                   THEN RAISE(ABORT,
                              'user_partner_feedback_rating: attempted to insert enum_id not found or not unique for type=fbrtype')
               END;
END;


CREATE TABLE user_partner_feedback_gb
(
    fbid    INTEGER NOT NULL REFERENCES user_partner_feedback (id) ON DELETE CASCADE,
    bad     BOOLEAN NOT NULL, -- true for bad, false for good
    enum_id INTEGER NOT NULL, -- references int_enum(id) where int_enum.type='fbgbtype'
    PRIMARY KEY (fbid, bad, enum_id)
);
CREATE TRIGGER upfb_gb_enum_check
    BEFORE INSERT
    ON user_partner_feedback_gb
    FOR EACH ROW
BEGIN
    SELECT CASE
               WHEN (SELECT count(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'fbgbtype') != 1
                   THEN RAISE(ABORT,
                              'user_partner_feedback_gb: attempted to insert enum_id not found or not unique for type=fbgbtype')
               END;
END;

CREATE TABLE user_partner_feedback_details
(
    fbid    INTEGER NOT NULL REFERENCES user_partner_feedback (id) ON DELETE CASCADE,
    enum_id INTEGER NOT NULL, -- references int_enum(id) where int_enum.type='fbtype'
    val     TEXT    NOT NULL,
    PRIMARY KEY (fbid, enum_id)
);
CREATE TRIGGER upfb_details_enum_check
    BEFORE INSERT
    ON user_partner_feedback_details
    FOR EACH ROW
BEGIN
    SELECT CASE
               WHEN (SELECT count(*) FROM int_enum WHERE id = NEW.enum_id AND type = 'fbdtype') != 1
                   THEN RAISE(ABORT,
                              'user_partner_feedback_details: attempted to insert enum_id not found or not unique for type=fbdtype')
               END;
END;

CREATE VIEW user_partner_feedback_view AS
WITH ratings AS
         (SELECT fb.id,
                 fb.partner_id,
                 GROUP_CONCAT(e.name || ': ' || r.val, ', ') AS rating,
                 AVG(r.val)                                  AS base_rating
          FROM user_partner_feedback fb
                   LEFT JOIN user_partner_feedback_rating r ON r.fbid = fb.id
                   LEFT JOIN int_enum e ON r.enum_id = e.id
              AND e.type = 'fbrtype'
          GROUP BY fb.id),
     gb_counts AS
         (SELECT fb.id,
                 SUM(CASE
                         WHEN gb.bad = 0 THEN 1
                         ELSE 0
                     END) AS good_count,
                 SUM(CASE
                         WHEN gb.bad = 1 THEN 1
                         ELSE 0
                     END) AS bad_count
          FROM user_partner_feedback fb
                   LEFT JOIN user_partner_feedback_gb gb ON gb.fbid = fb.id
          GROUP BY fb.id),
     important_props AS
         (SELECT fb.id,
                 COUNT(DISTINCT e.name) AS prop_count
          FROM user_partner_feedback fb
                   LEFT JOIN partner_prop pp ON pp.partner_id = fb.partner_id
                   LEFT JOIN int_enum e ON pp.enum_id = e.id
              AND e.type = 'properties'
          WHERE e.name IN ('ELLENORZOTT_TELEFON', 'ELLENORZOTT_KEPEK', 'ELLENORZOTT_KOR',
                           'VAN_FRISS_KEPE', 'VAN_ARCOS_KEPE',
                           'NINCS_PANASZ', 'GYAKRAN_BELEP', 'STABIL_HIRDETO',
                           'VALTOZATLAN_VAROS', 'VALTOZATLAN_NEV', 'VALTOZATLAN_SZAM'
              )
          GROUP BY fb.id),
     max_props AS
         (SELECT 11 AS max_count -- total number of important properties
         )
SELECT fb.*,
       rt.rating,                                         -- Good: enum names (bad=0)

       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM user_partner_feedback_gb gb
                 JOIN int_enum e ON gb.enum_id = e.id
            AND e.type = 'fbgbtype'
        WHERE gb.fbid = fb.id
          AND gb.bad = 0)                     AS good,    -- Bad: enum names (bad=1)

       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM user_partner_feedback_gb gb
                 JOIN int_enum e ON gb.enum_id = e.id
            AND e.type = 'fbgbtype'
        WHERE gb.fbid = fb.id
          AND gb.bad = 1)                     AS bad,     -- Details: each as "ENUMNAME: val", separated by double newline

       (SELECT GROUP_CONCAT(e.name || ': ' || d.val, CHAR(10) || CHAR(10))
        FROM user_partner_feedback_details d
                 JOIN int_enum e ON d.enum_id = e.id
            AND e.type = 'fbdtype'
        WHERE d.fbid = fb.id)                 AS details, -- Improved avg_rating: blend of base_rating and good/bad ratio
       ROUND(
               CASE
                   WHEN rt.base_rating IS NULL THEN NULL
                   ELSE
                       MIN(MAX(rt.base_rating * 0.7 +
                               (CASE
                                    WHEN (COALESCE(gc.good_count, 0) + COALESCE(gc.bad_count, 0)) = 0
                                        THEN 0.5
                                    ELSE 1.0 * COALESCE(gc.good_count, 0) /
                                         (COALESCE(gc.good_count, 0) + COALESCE(gc.bad_count, 0))
                                   END) * 5 * 0.3,
                               0), 5) END, 1) AS avg_rating,

       ROUND(CASE
                 WHEN rt.base_rating IS NULL THEN NULL
                 ELSE
                     MIN(MAX((rt.base_rating * 0.7 +
                              (CASE
                                   WHEN (COALESCE(gc.good_count, 0) + COALESCE(gc.bad_count, 0)) = 0
                                       THEN 0.5
                                   ELSE 1.0 * COALESCE(gc.good_count, 0) /
                                        (COALESCE(gc.good_count, 0) + COALESCE(gc.bad_count, 0))
                                  END) * 5 * 0.3) * (COALESCE(ip.prop_count, 0) * 1.0 / mp.max_count * 0.3 + 0.7), 0
                         ), 5) END, 1)        AS recommended_rating
FROM user_partner_feedback fb
         JOIN ratings rt ON fb.id = rt.id
         LEFT JOIN gb_counts gc ON fb.id = gc.id
         LEFT JOIN important_props ip ON fb.id = ip.id
         JOIN max_props mp
WHERE rt.rating IS NOT NULL;