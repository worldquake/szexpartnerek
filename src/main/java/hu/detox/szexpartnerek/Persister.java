package hu.detox.szexpartnerek;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.sql.SQLException;

public interface Persister extends AutoCloseable {
    void save(JsonNode node) throws IOException, SQLException;
}
