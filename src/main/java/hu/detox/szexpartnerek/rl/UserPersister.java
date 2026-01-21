package hu.detox.szexpartnerek.rl;

import com.fasterxml.jackson.databind.JsonNode;
import hu.detox.szexpartnerek.Db;
import hu.detox.szexpartnerek.Main;
import hu.detox.szexpartnerek.Persister;

import java.io.Flushable;
import java.io.IOException;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;

import static hu.detox.szexpartnerek.Utils.getField;

public class UserPersister implements Persister, Flushable {
    private final PreparedStatement userStmt;
    private final PreparedStatement userLikesStmt;
    private final PreparedStatement userLikesDeleteStmt;
    private int batch;

    public UserPersister() throws SQLException, IOException {
        Connection conn = Main.APP.getConn();
        String userSql = "INSERT INTO user (id, name, age, height, weight, gender, regd, size) VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET " +
                "name = excluded.name, " +
                "age = COALESCE(excluded.age, user.age), " +
                "height = COALESCE(excluded.height, user.height), " +
                "weight = COALESCE(excluded.weight, user.weight), " +
                "gender = COALESCE(excluded.gender, user.gender), " +
                "regd = COALESCE(excluded.regd, user.regd), " +
                "size = COALESCE(excluded.size, user.size)";
        this.userStmt = conn.prepareStatement(userSql);

        String likesSql = "INSERT INTO tmp_user_likes (" + User.IDR + ", like) VALUES (?, ?) " +
                "ON CONFLICT(" + User.IDR + ", like) DO NOTHING";
        this.userLikesStmt = conn.prepareStatement(likesSql);

        String likesDeleteSql = "DELETE FROM tmp_user_likes WHERE " + User.IDR + " = ?";
        this.userLikesDeleteStmt = conn.prepareStatement(likesDeleteSql);
    }

    public Set<String> getUntouchableIds() throws SQLException {
        HashSet<String> toProcess = new HashSet<>();
        String untouchedSql = "SELECT id FROM user WHERE ts < datetime('now', '-1 day')";
        ResultSet rs = Main.APP.getConn().createStatement().executeQuery(untouchedSql);
        while (rs.next()) {
            while (rs.next()) {
                toProcess.add(rs.getString(1));
            }
            rs.close();
        }
        return toProcess;
    }

    @Override
    public void save(JsonNode user) throws IOException, SQLException {
        Object idObj = getField(user, "id");
        Object nameObj = getField(user, "name");
        if (idObj == null || nameObj == null) {
            throw new IllegalArgumentException("id and name are mandatory");
        }

        Object ageObj = getField(user, "age");
        Object heightObj = getField(user, "height");
        Object weightObj = getField(user, "weight");
        Object genderObj = getField(user, "gender");
        Object regdObj = getField(user, "regd");
        Object sizeObj = getField(user, "size");

        userStmt.setObject(1, idObj);
        userStmt.setObject(2, nameObj);
        userStmt.setObject(3, ageObj);
        userStmt.setObject(4, heightObj);
        userStmt.setObject(5, weightObj);
        userStmt.setObject(6, genderObj);

        if (regdObj != null && regdObj instanceof String && !((String) regdObj).isEmpty()) {
            userStmt.setDate(7, java.sql.Date.valueOf((String) regdObj));
        } else {
            userStmt.setNull(7, Types.DATE);
        }

        if (sizeObj != null) {
            userStmt.setObject(8, sizeObj);
        } else {
            userStmt.setNull(8, Types.INTEGER);
        }

        userStmt.addBatch();

        // First, delete all likes for this user
        userLikesDeleteStmt.setObject(1, idObj);
        userLikesDeleteStmt.addBatch();

        // Then, re-insert likes
        JsonNode likesNode = user.get("likes");
        if (likesNode != null && likesNode.isArray()) {
            for (JsonNode likeNode : likesNode) {
                String like = likeNode.asText();
                if (like != null && !like.isEmpty()) {
                    userLikesStmt.setObject(1, idObj);
                    userLikesStmt.setString(2, like);
                    userLikesStmt.addBatch();
                }
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
        if (userStmt != null) userStmt.close();
        if (userLikesStmt != null) userLikesStmt.close();
        if (userLikesDeleteStmt != null) userLikesDeleteStmt.close();
    }

    @Override
    public void flush() throws IOException {
        if (batch == 0) return;
        try {
            userStmt.executeBatch();
            userLikesDeleteStmt.executeBatch();
            userLikesStmt.executeBatch();
        } catch (SQLException ex) {
            throw new IOException("Unable to flush " + batch, ex);
        }
        System.err.println("Flushed " + batch + " user");
        batch = 0;
    }
}