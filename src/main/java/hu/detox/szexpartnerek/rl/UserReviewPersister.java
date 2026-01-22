package hu.detox.szexpartnerek.rl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.detox.szexpartnerek.Db;
import hu.detox.szexpartnerek.Persister;
import hu.detox.szexpartnerek.TrafoEngine;

import java.io.Flushable;
import java.io.IOException;
import java.sql.*;
import java.util.Iterator;
import java.util.Map;

import static hu.detox.szexpartnerek.Utils.getField;


public class UserReviewPersister implements Persister, Flushable {
    private static final TrafoEngine[] PRE = new TrafoEngine[]{Partner.INSTANCE};
    private final PreparedStatement maxDateStmt;
    private final PreparedStatement feedbackStmt;
    private final PreparedStatement ratingStmt;
    private final PreparedStatement gbStmt;
    private final PreparedStatement detailsStmt;
    private int batch;

    public UserReviewPersister(Connection conn) throws SQLException {
        maxDateStmt = conn.prepareStatement(
                "SELECT MAX(ts)|| ':00' FROM user_partner_feedback"
        );
        feedbackStmt = conn.prepareStatement(
                "INSERT INTO user_partner_feedback (id, " + User.IDR + ", " + Partner.IDR + ", " + Persister.ENUM_IDR + ", name, after_name, useful, age, ts) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT(id) DO UPDATE SET " +
                        User.IDR + " = COALESCE(user_partner_feedback." + User.IDR + ", excluded." + User.IDR + "), " +
                        Partner.IDR + " = COALESCE(user_partner_feedback." + Partner.IDR + ", excluded." + Partner.IDR + "), " +
                        Persister.ENUM_IDR + " = COALESCE(user_partner_feedback." + Persister.ENUM_IDR + ", excluded." + Persister.ENUM_IDR + "), " +
                        "name = excluded.name, after_name = excluded.after_name, " +
                        "useful = COALESCE(user_partner_feedback.useful, excluded.useful), " +
                        "age = COALESCE(user_partner_feedback.age, excluded.age), " +
                        "ts = COALESCE(user_partner_feedback.ts, excluded.ts)"
        );
        ratingStmt = conn.prepareStatement(
                "INSERT INTO user_partner_feedback_rating (fbid, " + Persister.ENUM_IDR + ", val) VALUES (?, ?, ?) " +
                        "ON CONFLICT(fbid, " + Persister.ENUM_IDR + ") DO UPDATE SET val=excluded.val"
        );
        gbStmt = conn.prepareStatement(
                "INSERT INTO user_partner_feedback_gb (fbid, " + Persister.ENUM_IDR + ", bad) VALUES (?, ?, ?) " +
                        "ON CONFLICT(fbid, " + Persister.ENUM_IDR + ") DO NOTHING"
        );
        detailsStmt = conn.prepareStatement(
                "INSERT INTO user_partner_feedback_details (fbid, " + Persister.ENUM_IDR + ", val) VALUES (?, ?, ?) " +
                        "ON CONFLICT(fbid, " + Persister.ENUM_IDR + ") DO UPDATE SET val=excluded.val"
        );
    }

    TrafoEngine[] preTrafos() {
        return PRE;
    }

    public void saveSingle(ObjectNode item, Integer enumId) throws SQLException, IOException {
        // Insert main feedback
        int fbid = item.get("id").intValue();
        feedbackStmt.setInt(1, fbid);
        feedbackStmt.setObject(2, getField(item, User.IDR));
        feedbackStmt.setObject(3, getField(item, Partner.IDR));
        feedbackStmt.setInt(4, enumId);
        feedbackStmt.setObject(5, getField(item, "name"));
        feedbackStmt.setObject(6, getField(item, "after_name"));
        feedbackStmt.setObject(7, getField(item, "useful"), Types.INTEGER);
        feedbackStmt.setObject(8, getField(item, "age"), Types.INTEGER);
        feedbackStmt.setObject(9, getField(item, "ts"), Types.TIMESTAMP);
        feedbackStmt.addBatch();
        batch++;

        // Insert ratings
        JsonNode rates = item.get("rates");
        if (rates != null && rates.isArray()) {
            for (int i = 0; i < rates.size(); i++) {
                JsonNode valNode = rates.get(i);
                if (!valNode.isNull()) {
                    ratingStmt.setInt(1, fbid);
                    ratingStmt.setInt(2, i);
                    ratingStmt.setInt(3, valNode.asInt());
                    ratingStmt.addBatch();
                    batch++;
                }
            }
        }
        // Insert good/bad
        for (String key : new String[]{"good", "bad"}) {
            JsonNode arr = item.get(key);
            if (arr != null && arr.isArray()) {
                boolean isBad = key.equals("bad");
                for (JsonNode valNode : arr) {
                    gbStmt.setInt(1, fbid);
                    gbStmt.setInt(2, valNode.intValue());
                    gbStmt.setBoolean(3, isBad);
                    gbStmt.addBatch();
                    batch++;
                }
            }
        }
        // Insert details
        JsonNode details = item.get("details");
        if (details != null && details.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = details.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                detailsStmt.setInt(1, fbid);
                detailsStmt.setInt(2, Integer.parseInt(entry.getKey()));
                detailsStmt.setString(3, entry.getValue().asText());
                detailsStmt.addBatch();
                batch++;
            }
        }
        if (batch >= Db.MAX_BATCH) {
            flush();
        }
    }

    @Override
    public void save(JsonNode root) throws SQLException, IOException {
        var itemi = root.fields();
        while (itemi.hasNext()) {
            var eitem = itemi.next();
            Integer enumId = null;
            try {
                enumId = Integer.parseInt(eitem.getKey());
            } catch (NumberFormatException nfe) {
                continue;
            }
            for (var item : eitem.getValue())
                saveSingle((ObjectNode) item, enumId);
        }
    }

    public Timestamp maxTs() throws SQLException {
        Timestamp res = maxDateStmt.executeQuery().getTimestamp(1);
        return res == null ? new Timestamp(0) : res;
    }

    @Override
    public void flush() throws IOException {
        if (batch == 0) return;
        try {
            feedbackStmt.executeBatch();
            ratingStmt.executeBatch();
            gbStmt.executeBatch();
            detailsStmt.executeBatch();
        } catch (SQLException ex) {
            throw new IOException("Unable to flush " + batch + " feedback items", ex);
        }
        System.err.println("Flushed " + batch + " review related rows");
        batch = 0;
    }

    @Override
    public void close() throws SQLException, IOException {
        flush();
        feedbackStmt.close();
        ratingStmt.close();
        gbStmt.close();
        detailsStmt.close();
        maxDateStmt.close();
    }
}