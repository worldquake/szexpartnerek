package hu.detox.szexpartnerek.rl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.detox.szexpartnerek.Persister;
import hu.detox.szexpartnerek.Serde;
import hu.detox.szexpartnerek.TrafoEngine;
import okhttp3.HttpUrl;
import org.jsoup.Jsoup;
import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.Element;

import java.io.File;
import java.util.Iterator;
import java.util.function.Function;

public class New implements TrafoEngine {
    public static final New INSTANCE = new New();
    private static final TrafoEngine[] SUB = new TrafoEngine[]{Advertiser.INSTANCE, User.INSTANCE};
    public static final String PARTNERS = "partners";
    public static final String USERS = "users";

    private New() {
        // Singleton
    }

    @Override
    public Function<String, String> url() {
        return rest -> {
            if (StringUtil.isBlank(rest)) {
                rest = "a";
            }
            if (rest.length() == 1) {
                rest = "setFrehContent_ajax.php?fresh_type=" + rest + "&fresh_category=SP";
            }
            return rest;
        };
    }

    @Override
    public TrafoEngine[] subTrafos() {
        return SUB;
    }

    @Override
    public Persister persister() {
        return null;
    }

    @Override
    public Iterator<?> input(JsonNode parent) {
        return null;
    }

    @Override
    public String[] in() {
        return new String[]{
                "src/main/resources/newids.txt"
        };
    }

    @Override
    public File out() {
        return new File("target/newids.txt");
    }

    @Override
    public int page() {
        return 0;
    }

    @Override
    public ObjectNode apply(String s) {
        ObjectNode on = Serde.OM.createObjectNode();
        on.put(PARTNERS, Serde.OM.createArrayNode());
        on.put(USERS, Serde.OM.createArrayNode());
        for (Element e : Jsoup.parse(s).select("a")) {
            var url = HttpUrl.get("http://" + e.attr("href"));
            Integer id = Integer.valueOf(url.queryParameter("id").replaceAll("[^0-9]+", ""));
            switch (url.queryParameter("pid")) {
                case "user-data":
                    ((ArrayNode) on.get(USERS)).add(id);
                case "szexpartner-data":
                    ((ArrayNode) on.get(PARTNERS)).add(id);
            }
        }
        return on;
    }
}
