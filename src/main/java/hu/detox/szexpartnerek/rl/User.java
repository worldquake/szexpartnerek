package hu.detox.szexpartnerek.rl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import hu.detox.szexpartnerek.Persister;
import hu.detox.szexpartnerek.TrafoEngine;
import org.jsoup.internal.StringUtil;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.function.Function;

public class User implements TrafoEngine {
    public static final User INSTANCE = new User();
    private static final TrafoEngine[] SUB = new TrafoEngine[]{UserReviews.INSTANCE};
    private transient UserPersister persister;

    private User() {
        try {
            persister = new UserPersister();
        } catch (SQLException | IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public Function<String, String> url() {
        return rest -> {
            if (StringUtil.isBlank(rest)) {
                rest = "707194";
            }
            if (StringUtil.isNumeric(rest)) {
                rest = "rosszlanyok.php?pid=user-data&id=" + rest;
            }
            return rest;
        };
    }

    @Override
    public Iterator<?> input(JsonNode parent) {
        ArrayNode an = (ArrayNode) parent.get(New.USERS);
        if (an != null) {
            return an.iterator();
        }
        return null;
    }

    @Override
    public TrafoEngine[] subTrafos() {
        return SUB;
    }

    @Override
    public Persister persister() {
        return persister;
    }

    @Override
    public String[] in() {
        return new String[]{
                "src/main/resources/users.txt",
                "target/users.jsonl",
        };
    }

    @Override
    public File out() {
        return new File("target/users.txt");
    }

    @Override
    public int page() {
        return 0;
    }

    @Override
    public Object apply(String s) {
        return null;
    }
}
