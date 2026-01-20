-- Partner related extensions
DROP VIEW IF EXISTS partner_ext_view;
DROP TABLE IF EXISTS partner_ext;

CREATE TABLE partner_ext AS
SELECT p.*,
       -- Haj prefix and postfix
       (SELECT GROUP_CONCAT(core, '_') AS hair
        FROM (SELECT REPLACE(REPLACE(e.name, '_HAJ', ''), 'HAJ_', '') AS core
              FROM partner_prop pp
                       JOIN int_enum e ON pp.enum_id = e.id
              WHERE pp.partner_id = p.id
                AND e.type = 'properties'
                AND (e.name LIKE 'HAJ_%' OR e.name LIKE '%_HAJ')
              ORDER BY e.name LIKE 'HAJ_%' DESC, e.name
              LIMIT 2))                                         AS hair,
       -- Eye (szem): remove _SZEM postfix
       (SELECT REPLACE(e.name, '_SZEM', '')
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id
        WHERE pp.partner_id = p.id
          AND e.type = 'properties'
          AND e.name LIKE '%_SZEM'
        LIMIT 1)                                                AS eyes,
       -- Breast (cici): remove _CICI postfix
       (SELECT REPLACE(e.name, '_CICI', '')
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id
        WHERE pp.partner_id = p.id
          AND e.type = 'properties'
          AND e.name LIKE '%_CICI'
        LIMIT 1)                                                AS breasts,
       -- Body type (alkat): remove _ALKAT postfix
       (SELECT REPLACE(e.name, '_ALKAT', '')
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id
        WHERE pp.partner_id = p.id
          AND e.type = 'properties'
          AND e.name LIKE '%_ALKAT'
        LIMIT 1)                                                AS body,
       -- Intim: remove INTIM_ prefix
       (SELECT REPLACE(e.name, 'INTIM_', '')
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id
        WHERE pp.partner_id = p.id
          AND e.type = 'properties'
          AND e.name LIKE 'INTIM_%'
        LIMIT 1)                                                AS intim,
       -- Orientation: HETERO, HOMO, BISZEX (no prefix/postfix)
       (SELECT e.name
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id
        WHERE pp.partner_id = p.id
          AND e.type = 'properties'
          AND e.name IN ('HETERO', 'HOMO', 'BISZEX')
        LIMIT 1)                                                AS orientation,
       -- Gender: LANY, FIU, PAR, TRANSZSZEXUALIS (no prefix/postfix)
       (SELECT e.name
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id
        WHERE pp.partner_id = p.id
          AND e.type = 'properties'
          AND e.name IN ('LANY', 'PAR', 'FIU', 'TRANSZSZEXUALIS')
        LIMIT 1)                                                AS gender,
       -- sex: combine DOMINA, SZEXPARTNER, AKTUS_VELEM_KIZART, MASSZAZS, CSAK_MASSZAZS from props
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id
        WHERE pp.partner_id = p.id
          AND e.type = 'properties'
          AND (e.name LIKE '%DOMINA%'
            OR e.name IN ('SZEXPARTNER', 'AKTUS_VELEM_KIZART', 'MASSZAZS', 'CSAK_MASSZAZS'))
        ORDER BY e.name)                                        AS act,
       -- Aircond
       CASE
           WHEN EXISTS (SELECT 1
                        FROM partner_prop pp
                                 JOIN int_enum e ON pp.enum_id = e.id
                        WHERE pp.partner_id = p.id
                          AND e.type = 'properties'
                          AND e.name = 'LEGKONDICIONALT_LAKAS') THEN 1
           WHEN EXISTS (SELECT 1
                        FROM partner_prop pp
                                 JOIN int_enum e ON pp.enum_id = e.id
                        WHERE pp.partner_id = p.id
                          AND e.type = 'properties'
                          AND e.name = 'NEM_LEGKONDICIONALT_LAKAS') THEN 0
           ELSE NULL
           END                                                  AS aircond,
       -- Smoker
       CASE
           WHEN EXISTS (SELECT 1
                        FROM partner_prop pp
                                 JOIN int_enum e ON pp.enum_id = e.id
                        WHERE pp.partner_id = p.id
                          AND e.type = 'properties'
                          AND e.name = 'DOHANYZOM') THEN 1
           WHEN EXISTS (SELECT 1
                        FROM partner_prop pp
                                 JOIN int_enum e ON pp.enum_id = e.id
                        WHERE pp.partner_id = p.id
                          AND e.type = 'properties'
                          AND e.name = 'NEM_DOHANYZOM') THEN 0
           ELSE NULL
           END                                                  AS smoker,
       -- SMS
       CASE
           WHEN EXISTS (SELECT 1
                        FROM partner_prop pp
                                 JOIN int_enum e ON pp.enum_id = e.id
                        WHERE pp.partner_id = p.id
                          AND e.type = 'properties'
                          AND e.name = 'SMSRE_VALASZOLOK') THEN 1
           WHEN EXISTS (SELECT 1
                        FROM partner_prop pp
                                 JOIN int_enum e ON pp.enum_id = e.id
                        WHERE pp.partner_id = p.id
                          AND e.type = 'properties'
                          AND e.name = 'SMSRE_NEM_VALASZOLOK') THEN 0
           ELSE NULL
           END                                                  AS sms,
       -- length: join *_ORARA in looking, remove postfix
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_looking pl
                 JOIN int_enum e ON pl.enum_id = e.id
        WHERE pl.partner_id = p.id
          AND e.type = 'looking'
          AND (e.name LIKE '%_ORARA' OR e.name = 'TOBB_NAPRA')) AS lengths,
       -- francia: join FRANCIA_* in likes, remove prefix
       (SELECT GROUP_CONCAT(REPLACE(e.name, 'FRANCIA_', ''), ', ')
        FROM partner_like pl
                 JOIN int_enum e ON pl.enum_id = e.id
        WHERE pl.partner_id = p.id
          AND e.type = 'likes'
          AND e.name LIKE 'FRANCIA_%')                          AS french,
       -- place: combine (CSAK_)WEBCAM_SZEX, CSAK_NALAD, CSAK_NALAM, NALAM_NALAD from props and AUTOS_KALAND, BULIBA, BUCSUBA, CSAK_WEBCAM_SZEX from looking
       (SELECT GROUP_CONCAT(placeval, ', ')
        FROM (SELECT e.name AS placeval
              FROM main.partner_like pli
                       JOIN int_enum e ON pli.enum_id = e.id
              WHERE pli.partner_id = p.id
                AND e.type = 'likes'
                AND e.name LIKE '%WEBCAM%'
              UNION ALL
              SELECT e.name AS placeval
              FROM partner_prop pp
                       JOIN int_enum e ON pp.enum_id = e.id
              WHERE pp.partner_id = p.id
                AND e.type = 'properties'
                AND e.name IN ('CSAK_NALAD', 'CSAK_NALAM', 'NALAM_NALAD')
              UNION ALL
              SELECT e.name AS placeval
              FROM partner_looking pl
                       JOIN int_enum e ON pl.enum_id = e.id
              WHERE pl.partner_id = p.id
                AND (e.type = 'looking'
                  AND e.name IN ('AUTOS_KALAND', 'BULIBA', 'BUCSUBA')))
        order by placeval asc)                                  AS place,
       -- client_type logic
       COALESCE(
           -- 1. If any of TOBBEST, NOT, FERFIT, PART in looking, use those
               (SELECT GROUP_CONCAT(e.name, ', ')
                FROM partner_looking pl
                         JOIN int_enum e ON pl.enum_id = e.id
                WHERE pl.partner_id = p.id
                  AND e.type = 'looking'
                  AND e.name IN ('TOBBEST', 'NOT', 'FERFIT', 'PART')),
           -- 2. If BISZEX and PAR in props, set to 'NOT, FERFIT'
               CASE
                   WHEN EXISTS (SELECT 1
                                FROM partner_prop pp
                                         JOIN int_enum e ON pp.enum_id = e.id
                                WHERE pp.partner_id = p.id
                                  AND e.type = 'properties'
                                  AND e.name = 'BISZEX')
                       AND EXISTS (SELECT 1
                                   FROM partner_prop pp
                                            JOIN int_enum e ON pp.enum_id = e.id
                                   WHERE pp.partner_id = p.id
                                     AND e.type = 'properties'
                                     AND e.name = 'PAR')
                       THEN 'NOT, FERFIT'
                   ELSE NULL
                   END,
           -- 3. HOMO/HETERO + FIU/LANY/TRANSZSZEXUALIS
               CASE
                   -- HOMO + FIU -> fiut
                   WHEN EXISTS (SELECT 1
                                FROM partner_prop pp
                                         JOIN int_enum e ON pp.enum_id = e.id
                                WHERE pp.partner_id = p.id
                                  AND e.type = 'properties'
                                  AND e.name = 'HOMO')
                       AND EXISTS (SELECT 1
                                   FROM partner_prop pp
                                            JOIN int_enum e ON pp.enum_id = e.id
                                   WHERE pp.partner_id = p.id
                                     AND e.type = 'properties'
                                     AND e.name = 'FIU')
                       THEN 'FERFIT'
                   -- HOMO + (LANY vagy TRANSZSZEXUALIS) -> lanyt
                   WHEN EXISTS (SELECT 1
                                FROM partner_prop pp
                                         JOIN int_enum e ON pp.enum_id = e.id
                                WHERE pp.partner_id = p.id
                                  AND e.type = 'properties'
                                  AND e.name = 'HOMO')
                       AND EXISTS (SELECT 1
                                   FROM partner_prop pp
                                            JOIN int_enum e ON pp.enum_id = e.id
                                   WHERE pp.partner_id = p.id
                                     AND e.type = 'properties'
                                     AND e.name IN ('LANY', 'TRANSZSZEXUALIS'))
                       THEN 'NOT'
                   -- HETERO + FIU -> lanyt
                   WHEN EXISTS (SELECT 1
                                FROM partner_prop pp
                                         JOIN int_enum e ON pp.enum_id = e.id
                                WHERE pp.partner_id = p.id
                                  AND e.type = 'properties'
                                  AND e.name = 'HETERO')
                       AND EXISTS (SELECT 1
                                   FROM partner_prop pp
                                            JOIN int_enum e ON pp.enum_id = e.id
                                   WHERE pp.partner_id = p.id
                                     AND e.type = 'properties'
                                     AND e.name = 'FIU')
                       THEN 'NOT'
                   -- HETERO + (LANY vagy TRANSZSZEXUALIS) -> fiut
                   WHEN EXISTS (SELECT 1
                                FROM partner_prop pp
                                         JOIN int_enum e ON pp.enum_id = e.id
                                WHERE pp.partner_id = p.id
                                  AND e.type = 'properties'
                                  AND e.name = 'HETERO')
                       AND EXISTS (SELECT 1
                                   FROM partner_prop pp
                                            JOIN int_enum e ON pp.enum_id = e.id
                                   WHERE pp.partner_id = p.id
                                     AND e.type = 'properties'
                                     AND e.name IN ('LANY', 'TRANSZSZEXUALIS'))
                       THEN 'FERFIT'
                   ELSE NULL
                   END
       )                                                        AS client_type
FROM partner p;

CREATE VIEW partner_ext_view AS
SELECT p.*,
       -- Props
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id
            AND e.type = 'properties'
            AND NOT (name LIKE '%_HAJ' OR name LIKE 'HAJ_%' OR name LIKE '%_SZEM'
                OR name LIKE '%_CICI' OR name LIKE '%_ALKAT'
                OR name LIKE 'INTIM_%' OR name IN ('HETERO', 'HOMO', 'BISZEX')
                OR name IN ('LANY', 'PAR', 'FIU', 'TRANSZSZEXUALIS')
                OR name IN ('LEGKONDICIONALT_LAKAS', 'NEM_LEGKONDICIONALT_LAKAS')
                OR name IN ('DOHANYZOM', 'NEM_DOHANYZOM')
                OR name IN ('SMSRE_VALASZOLOK', 'SMSRE_NEM_VALASZOLOK')
                OR name IN ('SZEXPARTNER', 'DOMINA', 'AKTUS_VELEM_KIZART', 'MASSZAZS', 'CSAK_MASSZAZS')
                OR name IN ('CSAK_NALAD', 'CSAK_NALAM', 'NALAM_NALAD'))
        WHERE pp.partner_id = p.id)                                                    AS properties,
       -- Likes (filter out FRANCIA_* and handled keys)
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_like pl
                 JOIN int_enum e ON pl.enum_id = e.id
            AND e.type = 'likes'
        WHERE pl.partner_id = p.id
          AND NOT (e.name LIKE 'FRANCIA_%'
            OR e.name LIKE '%WEBCAM%'))                                                AS likes,
       -- Massages
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_massage pm
                 JOIN int_enum e ON pm.enum_id = e.id
            AND e.type = 'massage'
        WHERE pm.partner_id = p.id)                                                    AS massages,
       -- Languages
       (SELECT GROUP_CONCAT(plang.lang, ', ')
        FROM partner_lang plang
        WHERE plang.partner_id = p.id)                                                 AS extra_languages,
       -- Looking (filter out *_ORARA and handled keys)
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_looking plook
                 JOIN int_enum e ON plook.enum_id = e.id
            AND e.type = 'looking'
        WHERE plook.partner_id = p.id
          AND NOT (e.name LIKE '%_ORARA'
            OR e.name = 'TOBB_NAPRA')
          AND e.name NOT IN ('AUTOS_KALAND', 'BULIBA', 'BUCSUBA', 'CSAK_WEBCAM_SZEX')) AS looking,
       -- Open hours
       (SELECT GROUP_CONCAT(onday || ': ' || hours, ', ')
        FROM partner_open_hour poh
        WHERE poh.partner_id = p.id)                                                   AS open_hours,
       -- Average rating from user_partner_feedback_view
       (SELECT ROUND(AVG(upfv.avg_rating), 2)
        FROM user_partner_feedback_view upfv
        WHERE upfv.partner_id = p.id
          AND upfv.avg_rating IS NOT NULL)                                             AS avg_rating,
       -- Recommended rating: average of recommended_rating from user_partner_feedback_view
       (SELECT ROUND(AVG(upfv.recommended_rating), 2)
        FROM user_partner_feedback_view upfv
        WHERE upfv.partner_id = p.id
          AND upfv.recommended_rating IS NOT NULL)                                     AS recommended_rating
FROM partner_ext p;

-- Create user_likes related extensions
DROP VIEW IF EXISTS user_likes_view;
DROP VIEW IF EXISTS user_view;
DROP TABLE IF EXISTS user_likes;

CREATE TABLE user_likes
(
    user_id INTEGER NOT NULL REFERENCES user (id),
    like_id TINYINT NOT NULL REFERENCES int_enum (id),
    PRIMARY KEY (user_id, like_id)
);
INSERT INTO user_likes (user_id, like_id)
SELECT ul.user_id, ie.id
FROM tmp_user_likes ul
         JOIN int_enum ie ON ie.type = 'likes' AND ie.name = ul.like;


CREATE VIEW user_likes_view AS
SELECT user_id,
       GROUP_CONCAT(ie.name, ', ') AS likes
FROM user_likes ul
         JOIN int_enum ie ON ul.like_id = ie.id AND ie.type = 'likes'
GROUP BY user_id;
CREATE VIEW user_view AS
SELECT u.*,
       ulv.likes
FROM user u
         LEFT JOIN user_likes_view ulv ON u.id = ulv.user_id;
