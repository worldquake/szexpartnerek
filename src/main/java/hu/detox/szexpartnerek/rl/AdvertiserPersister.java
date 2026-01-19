package hu.detox.szexpartnerek.rl;

import com.fasterxml.jackson.databind.JsonNode;
import hu.detox.szexpartnerek.Db;
import hu.detox.szexpartnerek.Main;
import hu.detox.szexpartnerek.Persister;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.Flushable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Properties;

public class AdvertiserPersister implements Persister, Flushable {
    private final PreparedStatement enumDel;
    private final PreparedStatement enumAdd;
    private final PreparedStatement partnerStmt;
    private final PreparedStatement phonePropStmt;
    private final PreparedStatement partnerPropStmt;
    private final PreparedStatement openHourStmt;
    private final PreparedStatement langStmt;
    private final PreparedStatement answerStmt;
    private final PreparedStatement lookingStmt;
    private final PreparedStatement massageStmt;
    private final PreparedStatement likeStmt;
    private final PreparedStatement imgStmt;
    private final PreparedStatement activityStmt;
    int batch;

    public AdvertiserPersister() throws SQLException {
        Connection conn = Main.APP.getConn();
        this.enumDel = conn.prepareStatement("DELETE FROM int_enum");
        this.enumAdd = conn.prepareStatement("INSERT INTO int_enum (id, parentid, type, name) VALUES (?, NULL, ?, ?)");
        this.partnerStmt = conn.prepareStatement("INSERT INTO partner (\n" +
                "    id, call_number, name, pass, about, active_info, expect, age, height, weight, breast, waist, hips, city, location_extra, latitude, longitude, looking_age_min, looking_age_max\n" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)\n" +
                "ON CONFLICT(id) DO UPDATE SET\n" +
                "    call_number = CASE WHEN excluded.call_number IS NOT NULL THEN excluded.call_number ELSE partner.call_number END,\n" +
                "    about = CASE WHEN excluded.about IS NOT NULL THEN excluded.about ELSE partner.about END,\n" +
                "    name = excluded.name,\n" +
                "    pass = excluded.pass,\n" +
                "    active_info = excluded.active_info,\n" +
                "    expect = excluded.expect,\n" +
                "    age = excluded.age,\n" +
                "    height = excluded.height,\n" +
                "    weight = excluded.weight,\n" +
                "    breast = excluded.breast,\n" +
                "    waist = excluded.waist,\n" +
                "    hips = excluded.hips,\n" +
                "    city = excluded.city,\n" +
                "    location_extra = excluded.location_extra,\n" +
                "    latitude = excluded.latitude,\n" +
                "    longitude = excluded.longitude,\n" +
                "    looking_age_min = excluded.looking_age_min,\n" +
                "    looking_age_max = excluded.looking_age_max\n");
        this.phonePropStmt = conn.prepareStatement("INSERT OR IGNORE INTO partner_phone_prop (phone_id, enum_id) VALUES (?, ?)");
        this.partnerPropStmt = conn.prepareStatement("INSERT OR IGNORE INTO partner_prop (partner_id, enum_id) VALUES (?, ?)");
        this.openHourStmt = conn.prepareStatement("INSERT OR IGNORE INTO partner_open_hour (partner_id, onday, hours) VALUES (?, ?, ?)");
        this.langStmt = conn.prepareStatement("INSERT OR IGNORE INTO partner_lang (partner_id, lang) VALUES (?, ?)");
        this.answerStmt = conn.prepareStatement("INSERT OR IGNORE INTO partner_answer (partner_id, enum_id, answer) VALUES (?, ?, ?)");
        this.lookingStmt = conn.prepareStatement("INSERT OR IGNORE INTO partner_looking (partner_id, enum_id) VALUES (?, ?)");
        this.massageStmt = conn.prepareStatement("INSERT OR IGNORE INTO partner_massage (partner_id, enum_id) VALUES (?, ?)");
        this.likeStmt = conn.prepareStatement("INSERT OR IGNORE INTO partner_like (partner_id, enum_id, option) VALUES (?, ?, ?)");
        this.imgStmt = conn.prepareStatement("INSERT OR IGNORE INTO partner_img (partner_id, ondate, path) VALUES (?, ?, ?)");
        this.activityStmt = conn.prepareStatement("INSERT OR IGNORE INTO partner_activity (partner_id, ondate, description) VALUES (?, ?, ?)");
    }

    public void saveProps(String file) throws IOException, SQLException {
        Properties props = new Properties();
        props.load(new BufferedReader(new FileReader(file)));
        enumDel.executeUpdate();

        for (String key : props.stringPropertyNames()) {
            String value = props.getProperty(key);
            int eq = key.indexOf('.');
            if (eq < 0) continue; // skip comments or invalid lines
            String type = key.substring(0, eq);
            String name = key.substring(eq + 1);
            int id;
            try {
                id = Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                continue; // skip invalid values
            }
            enumAdd.setInt(1, id);
            enumAdd.setString(2, type);
            enumAdd.setString(3, name);
            enumAdd.addBatch();
        }
        enumAdd.executeBatch();
    }

    @Override
    public void save(JsonNode root) throws IOException, SQLException {

        int partnerId = root.get("id").asInt();
        //System.out.println(partnerId);
        partnerStmt.setInt(1, partnerId);
        JsonNode phn = root.get("phone");
        boolean active = phn != null && !phn.isEmpty();
        if (active) partnerStmt.setString(2, phn.get(0).asText());
        else partnerStmt.setNull(2, Types.VARCHAR);

        partnerStmt.setString(3, root.get("name").asText());
        partnerStmt.setString(4, root.get("pass").asText());
        if (active) partnerStmt.setString(5, root.get("about").asText());
        else partnerStmt.setNull(5, Types.VARCHAR);
        partnerStmt.setString(6, root.get("active").asText());
        if (root.has("expect") && !root.get("expect").isNull())
            partnerStmt.setString(7, root.get("expect").asText());
        else
            partnerStmt.setNull(7, Types.VARCHAR);
        JsonNode msrs = root.get("measures");
        if (msrs == null) {
            partnerStmt.setNull(8, Types.VARCHAR);
            partnerStmt.setNull(9, Types.SMALLINT);
            partnerStmt.setNull(10, Types.SMALLINT);
            partnerStmt.setNull(11, Types.SMALLINT);
            partnerStmt.setNull(12, Types.SMALLINT);
            partnerStmt.setNull(13, Types.SMALLINT);
        } else {
            partnerStmt.setString(8, msrs.has("age") && !msrs.get("age").isNull() ? msrs.get("age").asText() : null);
            partnerStmt.setObject(9, msrs.has("height") && !msrs.get("height").isNull() ? msrs.get("height").asInt() : null, java.sql.Types.INTEGER);
            partnerStmt.setObject(10, msrs.has("weight") && !msrs.get("weight").isNull() ? msrs.get("weight").asInt() : null, java.sql.Types.INTEGER);
            partnerStmt.setObject(11, msrs.has("breast") && !msrs.get("breast").isNull() ? msrs.get("breast").asInt() : null, java.sql.Types.INTEGER);
            partnerStmt.setObject(12, msrs.has("waist") && !msrs.get("waist").isNull() ? msrs.get("waist").asInt() : null, java.sql.Types.INTEGER);
            partnerStmt.setObject(13, msrs.has("hips") && !msrs.get("hips").isNull() ? msrs.get("hips").asInt() : null, java.sql.Types.INTEGER);
        }
        JsonNode loc = root.get("location");
        if (loc == null) { // Webcam only
            partnerStmt.setNull(14, Types.VARCHAR);
            partnerStmt.setNull(15, Types.VARCHAR);
            partnerStmt.setNull(16, Types.REAL);
            partnerStmt.setNull(17, Types.REAL);
        } else {
            partnerStmt.setString(14, loc.get(0).asText());
            partnerStmt.setString(15, loc.get(1).isNull() ? null : root.get("location").get(1).asText());
            if (loc.size() > 2) {
                String[] coords = loc.get(2).asText().split(",");
                partnerStmt.setDouble(16, Double.parseDouble(coords[0]));
                partnerStmt.setDouble(17, Double.parseDouble(coords[1]));
            } else {
                partnerStmt.setNull(16, Types.REAL);
                partnerStmt.setNull(17, Types.REAL);
            }
        }
        JsonNode la = root.get("looking_age");
        if (la != null) {
            partnerStmt.setInt(18, la.get(0).asInt());
            if (la.size() == 1)
                partnerStmt.setNull(19, Types.SMALLINT);
            else
                partnerStmt.setInt(19, la.get(1).asInt());
        } else {
            partnerStmt.setNull(18, Types.SMALLINT);
            partnerStmt.setNull(19, Types.SMALLINT);
        }
        partnerStmt.addBatch();

        for (int i = 1; i < root.get("phone").size(); i++) {
            phonePropStmt.setInt(1, partnerId);
            phonePropStmt.setInt(2, root.get("phone").get(i).asInt());
            phonePropStmt.addBatch();
        }

        for (JsonNode n : root.get("properties")) {
            partnerPropStmt.setInt(1, partnerId);
            partnerPropStmt.setInt(2, n.asInt());
            partnerPropStmt.addBatch();
        }

        for (int i = 0; i < root.get("openHours").size(); i++) {
            openHourStmt.setInt(1, partnerId);
            openHourStmt.setInt(2, i);
            openHourStmt.setString(3, root.get("openHours").get(i).asText());
            openHourStmt.addBatch();
        }

        for (JsonNode n : root.get("langs")) {
            langStmt.setInt(1, partnerId);
            langStmt.setString(2, n.asText());
            langStmt.addBatch();
        }

        JsonNode ans = root.get("answers");
        if (ans != null) {
            Iterator<String> answerKeys2 = ans.fieldNames();
            while (answerKeys2.hasNext()) {
                String key = answerKeys2.next();
                answerStmt.setInt(1, partnerId);
                answerStmt.setInt(2, Integer.parseInt(key));
                answerStmt.setString(3, ans.get(key).asText());
                answerStmt.addBatch();
            }
        }

        JsonNode lkng = root.get("looking");
        if (lkng != null) for (JsonNode n : lkng) {
            lookingStmt.setInt(1, partnerId);
            lookingStmt.setInt(2, n.asInt());
            lookingStmt.addBatch();
        }

        JsonNode masgs = root.get("massages");
        if (masgs != null) for (JsonNode n : masgs) {
            massageStmt.setInt(1, partnerId);
            massageStmt.setInt(2, n.asInt());
            massageStmt.addBatch();
        }

        for (String status : Arrays.asList("no", "yes", "ask")) {
            for (JsonNode n : root.get("likes").get(status)) {
                likeStmt.setInt(1, partnerId);
                likeStmt.setInt(2, n.asInt());
                likeStmt.setString(3, status);
                likeStmt.addBatch();
            }
        }

        for (JsonNode arr : root.get("imgs")) {
            imgStmt.setInt(1, partnerId);
            if (arr.get(0).isNull()) imgStmt.setNull(2, Types.DATE);
            else imgStmt.setString(2, arr.get(0).asText());
            imgStmt.setString(3, arr.get(1).asText());
            imgStmt.addBatch();
        }

        JsonNode activity = root.get("activity");
        if (activity != null) {
            Iterator<String> activityKeys = activity.fieldNames();
            while (activityKeys.hasNext()) {
                String date = activityKeys.next();
                activityStmt.setInt(1, partnerId);
                activityStmt.setString(2, date);
                activityStmt.setString(3, activity.get(date).asText());
                activityStmt.addBatch();
            }
        }
        batch++;
        if (batch >= Db.MAX_BATCH) {
            flush();
        }
    }

    @Override
    public void close() throws SQLException, IOException {
        flush();
        if (partnerStmt != null) partnerStmt.close();
        if (phonePropStmt != null) phonePropStmt.close();
        if (enumDel != null) enumDel.close();
        if (enumAdd != null) enumAdd.close();
        if (partnerPropStmt != null) partnerPropStmt.close();
        if (openHourStmt != null) openHourStmt.close();
        if (langStmt != null) langStmt.close();
        if (answerStmt != null) answerStmt.close();
        if (lookingStmt != null) lookingStmt.close();
        if (massageStmt != null) massageStmt.close();
        if (likeStmt != null) likeStmt.close();
        if (imgStmt != null) imgStmt.close();
        if (activityStmt != null) activityStmt.close();
    }

    @Override
    public void flush() throws IOException {
        if (batch == 0) return;
        try {
            partnerStmt.executeBatch();
            activityStmt.executeBatch();
            phonePropStmt.executeBatch();
            partnerPropStmt.executeBatch();
            openHourStmt.executeBatch();
            langStmt.executeBatch();
            answerStmt.executeBatch();
            lookingStmt.executeBatch();
            massageStmt.executeBatch();
            likeStmt.executeBatch();
            imgStmt.executeBatch();
        } catch (SQLException ex) {
            throw new IOException("Unable to flush " + batch, ex);
        }
        System.err.println("Flushed " + batch + " advertisers");
        batch = 0;
    }
}