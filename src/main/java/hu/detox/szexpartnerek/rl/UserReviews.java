package hu.detox.szexpartnerek.rl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.detox.szexpartnerek.Persister;
import hu.detox.szexpartnerek.TrafoEngine;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;

public class UserReviews implements TrafoEngine {
    public static final UserReviews INSTANCE = new UserReviews();

    private UserReviews() {
        // Singleton
    }

    @Override
    public Function<String, String> url() {
        return rest -> {
            /*
            if (StringUtil.isBlank(rest)) {
                rest = "707194";
            }
            if (StringUtil.isNumeric(rest)) {
                rest = "4layer/user_left_beszamolo.php?id=" + rest + "&status=accepted";
            }
            return rest;*/
            return null;
        };
    }

    @Override
    public Iterator<?> input(JsonNode parent) {
        ArrayNode an = (ArrayNode) parent.get(New.USERS);
        if (an != null) {
            return an.iterator();
        } else if (parent instanceof ObjectNode) {
            return List.of(parent.get("id")).iterator();
        }
        return null;
    }

    @Override
    public TrafoEngine[] subTrafos() {
        return null;
    }

    @Override
    public Persister persister() {
        return null;
    }

    @Override
    public String[] in() {
        return new String[]{
                "src/main/resources/users-reviews.txt"
        };
    }

    @Override
    public File out() {
        return new File("target/users.txt");
    }

    @Override
    public int page() {
        return 25;
    }

    @Override
    public Object apply(String s) {
        return null;
    }
}
