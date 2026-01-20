-- Drop views
DROP VIEW IF EXISTS partner_view;
DROP VIEW IF EXISTS user_partner_feedback_view;

-- Drop triggers first
DROP TRIGGER IF EXISTS update_user_ingestion_date;
DROP TRIGGER IF EXISTS update_partner_ingestion_date;
DROP TRIGGER IF EXISTS partner_phone_prop_enum_type_check;
DROP TRIGGER IF EXISTS partner_prop_enum_type_check;
DROP TRIGGER IF EXISTS partner_answer_enum_type_check;
DROP TRIGGER IF EXISTS partner_looking_enum_type_check;
DROP TRIGGER IF EXISTS partner_massage_enum_type_check;
DROP TRIGGER IF EXISTS partner_like_enum_type_check;


DROP TRIGGER IF EXISTS upfb_rating_enum_check;
DROP TRIGGER IF EXISTS upfb_gb_enum_check;
DROP TRIGGER IF EXISTS upfb_enum_check;
DROP TRIGGER IF EXISTS upfb_details_enum_check;

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
