package hu.detox.szexpartnerek.rl;

import com.fasterxml.jackson.databind.JsonNode;
import hu.detox.szexpartnerek.Db;
import hu.detox.szexpartnerek.Main;
import hu.detox.szexpartnerek.Persister;
import hu.detox.szexpartnerek.Utils;

import java.io.Flushable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Map;

public class ListaPersister implements Persister, Flushable {
    private final PreparedStatement partnerListStmt;
    private final Connection conn;
    private int batch;
    private Map<String, String> map;

    public ListaPersister() throws SQLException, IOException {
        this.conn = Main.APP.getConn();
        map = Utils.map("src/main/resources/list-mapping.kv");
        conn.createStatement().executeUpdate("DELETE FROM partner_list");
        // Delete from partner_prop for dynamic lists
        conn.createStatement().executeUpdate(
                "DELETE FROM partner_prop " +
                        "WHERE enum_id IN (SELECT id FROM int_enum WHERE type = 'properties' AND name IN ('AJANLOTT', 'BARATNOVEL'))"
        );
        this.partnerListStmt = conn.prepareStatement(
                "INSERT OR IGNORE INTO partner_list (tag, id, name, age, image) VALUES (?, ?, ?, ?, ?)"
        );
    }

    @Override
    public void save(JsonNode root) throws IOException, SQLException {
        String title = root.get("title").asText();
        title = map.getOrDefault(title, title);
        for (JsonNode item : root.get("list")) {
            partnerListStmt.setString(1, title);
            partnerListStmt.setInt(2, item.get(0).asInt());
            partnerListStmt.setString(3, item.get(1).asText());
            partnerListStmt.setString(4, item.get(2).isNull() ? null : item.get(2).asText());
            partnerListStmt.setString(5, item.get(3).isNull() ? null : item.get(3).asText());
            partnerListStmt.addBatch();
            batch++;
            if (batch >= Db.MAX_BATCH) flush();
        }
    }

    @Override
    public void flush() throws IOException {
        if (batch == 0) return;
        try {
            partnerListStmt.executeBatch();
        } catch (SQLException ex) {
            throw new IOException("Unable to flush partner_list batch", ex);
        }
        System.err.println("Flushed " + batch + " lists");
        batch = 0;
    }

    @Override
    public void close() throws SQLException, IOException {
        flush();
        // Insert or ignore into partner_prop based on lists
        conn.createStatement().executeUpdate(
                "INSERT OR IGNORE INTO partner_prop (partner_id, enum_id) " +
                        "SELECT pl.id, ie.id " +
                        "FROM partner_list pl " +
                        "JOIN int_enum ie ON ie.type = 'properties' AND ie.name = pl.tag"
        );
        if (partnerListStmt != null) partnerListStmt.close();
    }
}