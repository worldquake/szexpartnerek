-- These values will be refreshed by lists from scratch
DELETE
FROM partner_prop
WHERE enum_id IN (SELECT id FROM int_enum WHERE type = 'props' AND name IN ('AJANLOTT', 'BARATNOVEL'));

INSERT OR IGNORE INTO partner_prop (partner_id, enum_id)
SELECT pp1.partner_id, (SELECT id FROM int_enum WHERE type = 'props' AND name = 'NALAM_NALAD')
FROM partner_prop pp1
         JOIN int_enum e1 ON pp1.enum_id = e1.id AND e1.type = 'props' AND e1.name = 'CSAK_NALAD'
         JOIN partner_prop pp2 ON pp1.partner_id = pp2.partner_id
         JOIN int_enum e2 ON pp2.enum_id = e2.id AND e2.type = 'props' AND e2.name = 'CSAK_NALAM'
WHERE NOT EXISTS (SELECT 1
                  FROM partner_prop pp3
                           JOIN int_enum e3 ON pp3.enum_id = e3.id AND e3.type = 'props' AND e3.name = 'NALAM_NALAD'
                  WHERE pp3.partner_id = pp1.partner_id);

DELETE
FROM partner_prop
WHERE enum_id IN (SELECT id FROM int_enum WHERE type = 'props' AND name IN ('CSAK_NALAD', 'CSAK_NALAM'))
  AND partner_id IN (SELECT pp1.partner_id
                     FROM partner_prop pp1
                              JOIN int_enum e1 ON pp1.enum_id = e1.id AND e1.type = 'props' AND e1.name = 'CSAK_NALAD'
                              JOIN partner_prop pp2 ON pp1.partner_id = pp2.partner_id
                              JOIN int_enum e2 ON pp2.enum_id = e2.id AND e2.type = 'props' AND e2.name = 'CSAK_NALAM'
                              JOIN partner_prop pp3 ON pp1.partner_id = pp3.partner_id
                              JOIN int_enum e3
                                   ON pp3.enum_id = e3.id AND e3.type = 'props' AND e3.name = 'NALAM_NALAD');

DELETE
FROM partner_prop
WHERE enum_id IN (SELECT id FROM int_enum WHERE type = 'props' AND name IN ('CSAK_NALAD', 'CSAK_NALAM'))
  AND partner_id IN (SELECT pp.partner_id
                     FROM partner_prop pp
                              JOIN int_enum e ON pp.enum_id = e.id AND e.type = 'props' AND e.name = 'NALAM_NALAD');

-- Now refresh props based on lists
INSERT OR IGNORE INTO partner_prop (partner_id, enum_id)
SELECT pl.id, ie.id
FROM partner_list pl
         JOIN int_enum ie ON ie.type = 'props' AND ie.name = pl.tag;

-- Ensure "NOKET" and "FERFIT" are in partner_looking if prop has "BISZEX"
INSERT OR IGNORE INTO partner_looking (partner_id, enum_id)
SELECT pp.partner_id, e.id
FROM partner_prop pp
         JOIN int_enum e ON e.type = 'looking' AND e.name = 'NOKET'
WHERE pp.enum_id = (SELECT id FROM int_enum WHERE type = 'props' AND name = 'BISZEX')
UNION
SELECT pp.partner_id, e.id
FROM partner_prop pp
         JOIN int_enum e ON e.type = 'looking' AND e.name = 'FERFIT'
WHERE pp.enum_id = (SELECT id FROM int_enum WHERE type = 'props' AND name = 'BISZEX');

-- partner_likes: set AKTUS to option='no' if AKTUS_VELEM_KIZART is in partner_prop
INSERT OR
REPLACE
INTO partner_like (partner_id, enum_id, option)
SELECT pp.partner_id, e.id, 'no'
FROM partner_prop pp
         JOIN int_enum e ON e.type = 'likes' AND e.name = 'AKTUS'
WHERE pp.enum_id = (SELECT id FROM int_enum WHERE type = 'props' AND name = 'AKTUS_VELEM_KIZART');

-- Remove AKTUS from other options for those partners
DELETE
FROM partner_like
WHERE enum_id = (SELECT id FROM int_enum WHERE type = 'likes' AND name = 'AKTUS')
  AND option != 'no'
  AND partner_id IN (SELECT partner_id
                     FROM partner_prop
                     WHERE enum_id = (SELECT id FROM int_enum WHERE type = 'props' AND name = 'AKTUS_VELEM_KIZART'));

-- If AKTUS is option=yes, ensure SZEXPARTNER is in partner_prop
INSERT OR IGNORE INTO partner_prop (partner_id, enum_id)
SELECT partner_id, (SELECT id FROM int_enum WHERE type = 'props' AND name = 'SZEXPARTNER')
FROM partner_like
WHERE enum_id = (SELECT id FROM int_enum WHERE type = 'likes' AND name = 'AKTUS')
  AND option = 'yes';

-- If AKTUS is option=no, remove SZEXPARTNER from partner_prop
DELETE
FROM partner_prop
WHERE enum_id = (SELECT id FROM int_enum WHERE type = 'props' AND name = 'SZEXPARTNER')
  AND partner_id IN (SELECT partner_id
                     FROM partner_like
                     WHERE enum_id = (SELECT id FROM int_enum WHERE type = 'likes' AND name = 'AKTUS')
                       AND option = 'no');

-- Partner pass update (removing unnecessary)
update partner
set pass=null
where pass = 'null'
   or pass like '%.hu'
   OR (name is not null and pass like name || '%');

update partner
set pass=TRIM(SUBSTR(pass, INSTR(pass, '"') + 1, INSTR(SUBSTR(pass, INSTR(pass, '"') + 1), '"') - 1))
where pass like '%"%"%';

-- Delete from partner_prop (props)
DELETE
FROM partner_prop
WHERE enum_id IN (SELECT id
                  FROM int_enum
                  WHERE type = 'props'
                    AND name IN ('NALAM_NALAD', 'CSAK_NALAD', 'CSAK_NALAM'))
  AND partner_id IN (SELECT partner_id
                     FROM partner_like
                     WHERE enum_id = (SELECT id FROM int_enum WHERE type = 'likes' AND name = 'CSAK_WEBCAM_SZEX'));

-- Delete from partner_looking (looking)
DELETE
FROM partner_looking
WHERE enum_id IN (SELECT id
                  FROM int_enum
                  WHERE type = 'looking'
                    AND name IN ('BUCSUBA', 'BULIBA', 'AUTOS_KALAND'))
  AND partner_id IN (SELECT partner_id
                     FROM partner_like
                     WHERE enum_id = (SELECT id FROM int_enum WHERE type = 'likes' AND name = 'CSAK_WEBCAM_SZEX'));

DROP VIEW partner_view;
CREATE VIEW partner_view AS
SELECT p.*,
       -- All props
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id AND e.type = 'props'
        WHERE pp.partner_id = p.id)    AS prop,
       -- All likes
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_like pl
                 JOIN int_enum e ON pl.enum_id = e.id AND e.type = 'likes'
        WHERE pl.partner_id = p.id)    AS like,
       -- All massages
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_massage pm
                 JOIN int_enum e ON pm.enum_id = e.id AND e.type = 'massage'
        WHERE pm.partner_id = p.id)    AS massage,
       -- All languages
       (SELECT GROUP_CONCAT(plang.lang, ', ')
        FROM partner_lang plang
        WHERE plang.partner_id = p.id) AS lang,
       -- All looking
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_looking plook
                 JOIN int_enum e ON plook.enum_id = e.id AND e.type = 'looking'
        WHERE plook.partner_id = p.id) AS looking,
       -- All open hours
       (SELECT GROUP_CONCAT(onday || ': ' || hours, ', ')
        FROM partner_open_hour poh
        WHERE poh.partner_id = p.id)   AS open_hour
FROM partner p;

DROP VIEW partner_ext_view;
DROP TABLE IF EXISTS partner_ext;

CREATE TABLE partner_ext AS
SELECT p.*,
       -- Haj prefix and postfix
       (SELECT GROUP_CONCAT(core, '_') AS hair
        FROM (SELECT REPLACE(REPLACE(e.name, '_HAJ', ''), 'HAJ_', '') AS core
              FROM partner_prop pp
                       JOIN int_enum e ON pp.enum_id = e.id
              WHERE pp.partner_id = p.id
                AND e.type = 'props'
                AND (e.name LIKE 'HAJ_%' OR e.name LIKE '%_HAJ')
              ORDER BY e.name LIKE 'HAJ_%' DESC, e.name
              LIMIT 2))                                         AS hair,
       -- Eye (szem): remove _SZEM postfix
       (SELECT REPLACE(e.name, '_SZEM', '')
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id
        WHERE pp.partner_id = p.id
          AND e.type = 'props'
          AND e.name LIKE '%_SZEM'
        LIMIT 1)                                                AS eye,
       -- Breast (cici): remove _CICI postfix
       (SELECT REPLACE(e.name, '_CICI', '')
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id
        WHERE pp.partner_id = p.id
          AND e.type = 'props'
          AND e.name LIKE '%_CICI'
        LIMIT 1)                                                AS cici,
       -- Body type (alkat): remove _ALKAT postfix
       (SELECT REPLACE(e.name, '_ALKAT', '')
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id
        WHERE pp.partner_id = p.id
          AND e.type = 'props'
          AND e.name LIKE '%_ALKAT'
        LIMIT 1)                                                AS body,
       -- Intim: remove INTIM_ prefix
       (SELECT REPLACE(e.name, 'INTIM_', '')
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id
        WHERE pp.partner_id = p.id
          AND e.type = 'props'
          AND e.name LIKE 'INTIM_%'
        LIMIT 1)                                                AS intim,
       -- Orientation: HETERO, HOMO, BISZEX (no prefix/postfix)
       (SELECT e.name
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id
        WHERE pp.partner_id = p.id
          AND e.type = 'props'
          AND e.name IN ('HETERO', 'HOMO', 'BISZEX')
        LIMIT 1)                                                AS orientation,
       -- Gender: LANY, FIU, PAR, TRANSZSZEXUALIS (no prefix/postfix)
       (SELECT e.name
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id
        WHERE pp.partner_id = p.id
          AND e.type = 'props'
          AND e.name IN ('LANY', 'PAR', 'FIU', 'TRANSZSZEXUALIS')
        LIMIT 1)                                                AS gender,
       -- sex: combine DOMINA, SZEXPARTNER, AKTUS_VELEM_KIZART, MASSZAZS, CSAK_MASSZAZS from props
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_prop pp
                 JOIN int_enum e ON pp.enum_id = e.id
        WHERE pp.partner_id = p.id
          AND e.type = 'props'
          AND (e.name LIKE '%DOMINA%'
            OR e.name IN ('SZEXPARTNER', 'AKTUS_VELEM_KIZART', 'MASSZAZS', 'CSAK_MASSZAZS'))
        ORDER BY e.name)                                        AS act,
       -- Aircond
       CASE
           WHEN EXISTS (SELECT 1
                        FROM partner_prop pp
                                 JOIN int_enum e ON pp.enum_id = e.id
                        WHERE pp.partner_id = p.id
                          AND e.type = 'props'
                          AND e.name = 'LEGKONDICIONALT_LAKAS') THEN 1
           WHEN EXISTS (SELECT 1
                        FROM partner_prop pp
                                 JOIN int_enum e ON pp.enum_id = e.id
                        WHERE pp.partner_id = p.id
                          AND e.type = 'props'
                          AND e.name = 'NEM_LEGKONDICIONALT_LAKAS') THEN 0
           ELSE NULL
           END                                                  AS aircond,
       -- Smoker
       CASE
           WHEN EXISTS (SELECT 1
                        FROM partner_prop pp
                                 JOIN int_enum e ON pp.enum_id = e.id
                        WHERE pp.partner_id = p.id
                          AND e.type = 'props'
                          AND e.name = 'DOHANYZOM') THEN 1
           WHEN EXISTS (SELECT 1
                        FROM partner_prop pp
                                 JOIN int_enum e ON pp.enum_id = e.id
                        WHERE pp.partner_id = p.id
                          AND e.type = 'props'
                          AND e.name = 'NEM_DOHANYZOM') THEN 0
           ELSE NULL
           END                                                  AS smoker,
       -- SMS
       CASE
           WHEN EXISTS (SELECT 1
                        FROM partner_prop pp
                                 JOIN int_enum e ON pp.enum_id = e.id
                        WHERE pp.partner_id = p.id
                          AND e.type = 'props'
                          AND e.name = 'SMSRE_VALASZOLOK') THEN 1
           WHEN EXISTS (SELECT 1
                        FROM partner_prop pp
                                 JOIN int_enum e ON pp.enum_id = e.id
                        WHERE pp.partner_id = p.id
                          AND e.type = 'props'
                          AND e.name = 'SMSRE_NEM_VALASZOLOK') THEN 0
           ELSE NULL
           END                                                  AS sms,
       -- hours: join *_ORARA in looking, remove postfix
       (SELECT GROUP_CONCAT(REPLACE(e.name, '_ORARA', ''), ', ')
        FROM partner_looking pl
                 JOIN int_enum e ON pl.enum_id = e.id
        WHERE pl.partner_id = p.id
          AND e.type = 'looking'
          AND (e.name LIKE '%_ORARA' OR e.name = 'TOBB_NAPRA')) AS hours,
       -- francia: join FRANCIA_* in likes, remove prefix
       (SELECT GROUP_CONCAT(REPLACE(e.name, 'FRANCIA_', ''), ', ')
        FROM partner_like pl
                 JOIN int_enum e ON pl.enum_id = e.id
        WHERE pl.partner_id = p.id
          AND e.type = 'likes'
          AND e.name LIKE 'FRANCIA_%')                          AS francia,
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
                AND e.type = 'props'
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
                                  AND e.type = 'props'
                                  AND e.name = 'BISZEX')
                       AND EXISTS (SELECT 1
                                   FROM partner_prop pp
                                            JOIN int_enum e ON pp.enum_id = e.id
                                   WHERE pp.partner_id = p.id
                                     AND e.type = 'props'
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
                                  AND e.type = 'props'
                                  AND e.name = 'HOMO')
                       AND EXISTS (SELECT 1
                                   FROM partner_prop pp
                                            JOIN int_enum e ON pp.enum_id = e.id
                                   WHERE pp.partner_id = p.id
                                     AND e.type = 'props'
                                     AND e.name = 'FIU')
                       THEN 'FERFIT'
                   -- HOMO + (LANY vagy TRANSZSZEXUALIS) -> lanyt
                   WHEN EXISTS (SELECT 1
                                FROM partner_prop pp
                                         JOIN int_enum e ON pp.enum_id = e.id
                                WHERE pp.partner_id = p.id
                                  AND e.type = 'props'
                                  AND e.name = 'HOMO')
                       AND EXISTS (SELECT 1
                                   FROM partner_prop pp
                                            JOIN int_enum e ON pp.enum_id = e.id
                                   WHERE pp.partner_id = p.id
                                     AND e.type = 'props'
                                     AND e.name IN ('LANY', 'TRANSZSZEXUALIS'))
                       THEN 'NOT'
                   -- HETERO + FIU -> lanyt
                   WHEN EXISTS (SELECT 1
                                FROM partner_prop pp
                                         JOIN int_enum e ON pp.enum_id = e.id
                                WHERE pp.partner_id = p.id
                                  AND e.type = 'props'
                                  AND e.name = 'HETERO')
                       AND EXISTS (SELECT 1
                                   FROM partner_prop pp
                                            JOIN int_enum e ON pp.enum_id = e.id
                                   WHERE pp.partner_id = p.id
                                     AND e.type = 'props'
                                     AND e.name = 'FIU')
                       THEN 'NOT'
                   -- HETERO + (LANY vagy TRANSZSZEXUALIS) -> fiut
                   WHEN EXISTS (SELECT 1
                                FROM partner_prop pp
                                         JOIN int_enum e ON pp.enum_id = e.id
                                WHERE pp.partner_id = p.id
                                  AND e.type = 'props'
                                  AND e.name = 'HETERO')
                       AND EXISTS (SELECT 1
                                   FROM partner_prop pp
                                            JOIN int_enum e ON pp.enum_id = e.id
                                   WHERE pp.partner_id = p.id
                                     AND e.type = 'props'
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
                 JOIN int_enum e ON pp.enum_id = e.id AND e.type = 'props'
            AND NOT (
                name LIKE '%_HAJ' OR name LIKE 'HAJ_%'
                    OR name LIKE '%_SZEM' OR name LIKE '%_CICI'
                    OR name LIKE '%_ALKAT' OR name LIKE 'INTIM_%'
                    OR name IN ('HETERO', 'HOMO', 'BISZEX')
                    OR name IN ('LANY', 'PAR', 'FIU', 'TRANSZSZEXUALIS')
                    OR name IN ('LEGKONDICIONALT_LAKAS', 'NEM_LEGKONDICIONALT_LAKAS')
                    OR name IN ('DOHANYZOM', 'NEM_DOHANYZOM')
                    OR name IN ('SMSRE_VALASZOLOK', 'SMSRE_NEM_VALASZOLOK')
                    OR name IN ('SZEXPARTNER', 'DOMINA', 'AKTUS_VELEM_KIZART', 'MASSZAZS', 'CSAK_MASSZAZS')
                    OR name IN ('CSAK_NALAD', 'CSAK_NALAM', 'NALAM_NALAD')
                )
        WHERE pp.partner_id = p.id)                                                    AS prop,
       -- Likes (filter out FRANCIA_* and handled keys)
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_like pl
                 JOIN int_enum e ON pl.enum_id = e.id AND e.type = 'likes'
        WHERE pl.partner_id = p.id
          AND NOT (e.name LIKE 'FRANCIA_%' OR e.name LIKE '%WEBCAM%'))                 AS like,
       -- Massages
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_massage pm
                 JOIN int_enum e ON pm.enum_id = e.id AND e.type = 'massage'
        WHERE pm.partner_id = p.id)                                                    AS massage,
       -- Languages
       (SELECT GROUP_CONCAT(plang.lang, ', ')
        FROM partner_lang plang
        WHERE plang.partner_id = p.id)                                                 AS lang,
       -- Looking (filter out *_ORARA and handled keys)
       (SELECT GROUP_CONCAT(e.name, ', ')
        FROM partner_looking plook
                 JOIN int_enum e ON plook.enum_id = e.id AND e.type = 'looking'
        WHERE plook.partner_id = p.id
          AND NOT (e.name LIKE '%_ORARA' OR e.name = 'TOBB_NAPRA')
          AND e.name NOT IN ('AUTOS_KALAND', 'BULIBA', 'BUCSUBA', 'CSAK_WEBCAM_SZEX')) AS looking,
       -- Open hours
       (SELECT GROUP_CONCAT(onday || ': ' || hours, ', ')
        FROM partner_open_hour poh
        WHERE poh.partner_id = p.id)                                                   AS open_hour
FROM partner_ext p;