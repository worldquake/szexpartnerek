package hu.detox.szexpartnerek.rl;

import com.fasterxml.jackson.databind.JsonNode;
import hu.detox.szexpartnerek.Db;

import java.io.Flushable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Iterator;

public class ListaPersister implements AutoCloseable, Flushable {
    private final PreparedStatement partnerListStmt;
    private int batch;

    public ListaPersister(Connection conn) throws SQLException {
        conn.createStatement().executeUpdate("DELETE FROM partner_list");
        this.partnerListStmt = conn.prepareStatement(
                "INSERT OR IGNORE INTO partner_list (tag, id, name, age, image) VALUES (?, ?, ?, ?, ?)"
        );
    }

    public void save(JsonNode root) throws Exception {
        Iterator<String> tags = root.fieldNames();
        while (tags.hasNext()) {
            String tag = tags.next();
            JsonNode arr = root.get(tag);
            for (JsonNode item : arr) {
                partnerListStmt.setString(1, tag);
                partnerListStmt.setInt(2, item.get(0).asInt());
                partnerListStmt.setString(3, item.get(1).asText());
                partnerListStmt.setString(4, item.get(2).isNull() ? null : item.get(2).asText());
                partnerListStmt.setString(5, item.get(3).isNull() ? null : item.get(3).asText());
                partnerListStmt.addBatch();
                batch++;
                if (batch >= Db.MAX_BATCH) flush();
            }
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
        batch = 0;
    }

    @Override
    public void close() throws SQLException, IOException {
        flush();
        if (partnerListStmt != null) partnerListStmt.close();
    }
}