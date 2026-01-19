package hu.detox.szexpartnerek.rl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import hu.detox.szexpartnerek.Persister;
import hu.detox.szexpartnerek.TrafoEngine;
import org.jsoup.internal.StringUtil;

import java.io.File;
import java.util.Iterator;
import java.util.function.Function;

public class UserReviews implements TrafoEngine {
    public static final UserReviews INSTANCE = new UserReviews();

    private UserReviews() {
        // Singleton
    }

    @Override
    public Function<String, String> url() {
        return rest -> {
            if (StringUtil.isBlank(rest)) {
                rest = "707194";
            }
            if (StringUtil.isNumeric(rest)) {
                rest = "4layer/user_left_beszamolo.php?id=" + rest + "&status=accepted";
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
        return null;
    }

    @Override
    public Persister persister() {
        return null;
    }

    @Override
    public File in() {
        return new File("src/main/resources/users.txt");
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
