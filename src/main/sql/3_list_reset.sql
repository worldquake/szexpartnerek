-- These values will be refreshed by lists from scratch
DELETE
FROM partner_prop
WHERE enum_id IN (SELECT id FROM int_enum WHERE type = 'properties' AND name IN ('AJANLOTT', 'BARATNOVEL'));
