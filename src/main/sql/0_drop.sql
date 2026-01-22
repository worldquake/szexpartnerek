-- Drop views
DROP VIEW IF EXISTS partner_view;
DROP VIEW IF EXISTS user_partner_feedback_view;

-- Drop triggers
DROP TRIGGER IF EXISTS update_user_ingestion_date;
DROP TRIGGER IF EXISTS update_partner_ingestion_date;
DROP TRIGGER IF EXISTS ppropetchk_insert;
DROP TRIGGER IF EXISTS ppropetchk_update;
DROP TRIGGER IF EXISTS paetchk_insert;
DROP TRIGGER IF EXISTS paetchk_update;
DROP TRIGGER IF EXISTS plooketchk_insert;
DROP TRIGGER IF EXISTS plooketchk_update;
DROP TRIGGER IF EXISTS pmassetchk_insert;
DROP TRIGGER IF EXISTS pmassetchk_update;
DROP TRIGGER IF EXISTS plikeetchk_insert;
DROP TRIGGER IF EXISTS plikeetchk_update;
DROP TRIGGER IF EXISTS pphonepetchk_insert;
DROP TRIGGER IF EXISTS pphonepetchk_update;
DROP TRIGGER IF EXISTS upfbechk_insert;
DROP TRIGGER IF EXISTS upfbechk_update;
DROP TRIGGER IF EXISTS upfb_ratingechk_insert;
DROP TRIGGER IF EXISTS upfb_ratingechk_update;
DROP TRIGGER IF EXISTS upfb_gbechk_insert;
DROP TRIGGER IF EXISTS upfb_gbechk_update;
DROP TRIGGER IF EXISTS upfb_detailsechk_insert;
DROP TRIGGER IF EXISTS upfb_detailsechk_update;

-- Drop tables (children first, then parents)
DROP TABLE IF EXISTS user_partner_feedback;
DROP TABLE IF EXISTS user_partner_feedback_rating;
DROP TABLE IF EXISTS user_partner_feedback_gb;
DROP TABLE IF EXISTS user_partner_feedback_details;

DROP TABLE IF EXISTS tmp_user_likes;
DROP TABLE IF EXISTS user;
DROP TABLE IF EXISTS partner_list;
DROP TABLE IF EXISTS partner_activity;
DROP TABLE IF EXISTS partner_img;
DROP TABLE IF EXISTS partner_like;
DROP TABLE IF EXISTS partner_massage;
DROP TABLE IF EXISTS partner_looking;
DROP TABLE IF EXISTS partner_answer;
DROP TABLE IF EXISTS partner_lang;
DROP TABLE IF EXISTS partner_open_hour;
DROP TABLE IF EXISTS partner_prop;
DROP TABLE IF EXISTS partner_phone_prop;
DROP TABLE IF EXISTS partner;
DROP TABLE IF EXISTS int_enum;
