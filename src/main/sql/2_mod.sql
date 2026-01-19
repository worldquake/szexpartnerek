-- Make the NALAM/NALAD consistent
INSERT OR IGNORE INTO partner_prop (partner_id, enum_id)
SELECT pp1.partner_id, (SELECT id FROM int_enum WHERE type = 'properties' AND name = 'NALAM_NALAD')
FROM partner_prop pp1
         JOIN int_enum e1 ON pp1.enum_id = e1.id AND e1.type = 'properties' AND e1.name = 'CSAK_NALAD'
         JOIN partner_prop pp2 ON pp1.partner_id = pp2.partner_id
         JOIN int_enum e2 ON pp2.enum_id = e2.id AND e2.type = 'properties' AND e2.name = 'CSAK_NALAM'
WHERE NOT EXISTS (SELECT 1
                  FROM partner_prop pp3
                           JOIN int_enum e3
                                ON pp3.enum_id = e3.id AND e3.type = 'properties' AND e3.name = 'NALAM_NALAD'
                  WHERE pp3.partner_id = pp1.partner_id);

DELETE
FROM partner_prop
WHERE enum_id IN (SELECT id FROM int_enum WHERE type = 'properties' AND name IN ('CSAK_NALAD', 'CSAK_NALAM'))
  AND partner_id IN (SELECT pp1.partner_id
                     FROM partner_prop pp1
                              JOIN int_enum e1
                                   ON pp1.enum_id = e1.id AND e1.type = 'properties' AND e1.name = 'CSAK_NALAD'
                              JOIN partner_prop pp2 ON pp1.partner_id = pp2.partner_id
                              JOIN int_enum e2
                                   ON pp2.enum_id = e2.id AND e2.type = 'properties' AND e2.name = 'CSAK_NALAM'
                              JOIN partner_prop pp3 ON pp1.partner_id = pp3.partner_id
                              JOIN int_enum e3
                                   ON pp3.enum_id = e3.id AND e3.type = 'properties' AND e3.name = 'NALAM_NALAD');

DELETE
FROM partner_prop
WHERE enum_id IN (SELECT id FROM int_enum WHERE type = 'properties' AND name IN ('CSAK_NALAD', 'CSAK_NALAM'))
  AND partner_id IN (SELECT pp.partner_id
                     FROM partner_prop pp
                              JOIN int_enum e
                                   ON pp.enum_id = e.id AND e.type = 'properties' AND e.name = 'NALAM_NALAD');

-- Ensure "NOKET" and "FERFIT" are in partner_looking if prop has "BISZEX"
INSERT OR IGNORE INTO partner_looking (partner_id, enum_id)
SELECT pp.partner_id, e.id
FROM partner_prop pp
         JOIN int_enum e ON e.type = 'looking' AND e.name = 'NOKET'
WHERE pp.enum_id = (SELECT id FROM int_enum WHERE type = 'properties' AND name = 'BISZEX')
UNION
SELECT pp.partner_id, e.id
FROM partner_prop pp
         JOIN int_enum e ON e.type = 'looking' AND e.name = 'FERFIT'
WHERE pp.enum_id = (SELECT id FROM int_enum WHERE type = 'properties' AND name = 'BISZEX');

-- partner_likes: set AKTUS to option='no' if AKTUS_VELEM_KIZART is in partner_prop
INSERT OR
REPLACE
INTO partner_like (partner_id, enum_id, option)
SELECT pp.partner_id, e.id, 'no'
FROM partner_prop pp
         JOIN int_enum e ON e.type = 'likes' AND e.name = 'AKTUS'
WHERE pp.enum_id = (SELECT id FROM int_enum WHERE type = 'properties' AND name = 'AKTUS_VELEM_KIZART');

-- Remove AKTUS from other options for those partners
DELETE
FROM partner_like
WHERE enum_id = (SELECT id FROM int_enum WHERE type = 'likes' AND name = 'AKTUS')
  AND option != 'no'
  AND partner_id IN (SELECT partner_id
                     FROM partner_prop
                     WHERE
                         enum_id = (SELECT id FROM int_enum WHERE type = 'properties' AND name = 'AKTUS_VELEM_KIZART'));

-- If AKTUS is option=yes, ensure SZEXPARTNER is in partner_prop
INSERT OR IGNORE INTO partner_prop (partner_id, enum_id)
SELECT partner_id, (SELECT id FROM int_enum WHERE type = 'properties' AND name = 'SZEXPARTNER')
FROM partner_like
WHERE enum_id = (SELECT id FROM int_enum WHERE type = 'likes' AND name = 'AKTUS')
  AND option = 'yes';

-- If AKTUS is option=no, remove SZEXPARTNER from partner_prop
DELETE
FROM partner_prop
WHERE enum_id = (SELECT id FROM int_enum WHERE type = 'properties' AND name = 'SZEXPARTNER')
  AND partner_id IN (SELECT partner_id
                     FROM partner_like
                     WHERE enum_id = (SELECT id FROM int_enum WHERE type = 'likes' AND name = 'AKTUS')
                       AND option = 'no');

-- Partner pass update (removing/adjusting)
update partner
set pass=TRIM(SUBSTR(pass, INSTR(pass, '"') + 1, INSTR(SUBSTR(pass, INSTR(pass, '"') + 1), '"') - 1))
where pass like '%"%"%';
update partner
set pass=null
where pass = 'null'
   or pass like '%.hu'
   OR (name is not null and pass like name || '%');

-- Delete from partner_prop the locations if WEBCAM ONLY
DELETE
FROM partner_prop
WHERE enum_id IN (SELECT id
                  FROM int_enum
                  WHERE type = 'properties'
                  WHERE type = 'properties'
                    AND name IN ('NALAM_NALAD', 'CSAK_NALAD', 'CSAK_NALAM'))
  AND partner_id IN (SELECT partner_id
                     FROM partner_like
                     WHERE enum_id = (SELECT id FROM int_enum WHERE type = 'likes' AND name = 'CSAK_WEBCAM_SZEX'));

-- Delete from partner_looking (looking) personal meet types if WEBCAM ONLY
DELETE
FROM partner_looking
WHERE enum_id IN (SELECT id
                  FROM int_enum
                  WHERE type = 'looking'
                    AND name IN ('BUCSUBA', 'BULIBA', 'AUTOS_KALAND'))
  AND partner_id IN (SELECT partner_id
                     FROM partner_like
                     WHERE enum_id = (SELECT id FROM int_enum WHERE type = 'likes' AND name = 'CSAK_WEBCAM_SZEX'));
