-- Now refresh props based on lists
INSERT OR IGNORE INTO partner_prop (partner_id, enum_id)
SELECT pl.id, ie.id
FROM partner_list pl
         JOIN int_enum ie ON ie.type = 'props' AND ie.name = pl.tag;

