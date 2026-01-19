package hu.detox.szexpartnerek.rl;

import com.fasterxml.jackson.databind.JsonNode;
import hu.detox.szexpartnerek.Persister;
import hu.detox.szexpartnerek.TrafoEngine;
import org.jsoup.Jsoup;
import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;

public class Lista implements TrafoEngine {
    public static final Lista INSTANCE = new Lista();
    private static final TrafoEngine[] SUB = new TrafoEngine[]{Advertiser.INSTANCE};
    private Set<Integer> ids;
    private transient ListaPersister persister;

    private Lista() {
        try {
            persister = new ListaPersister();
        } catch (SQLException | IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        ids = new HashSet<>();
    }

    @Override
    public Function<String, String> url() {
        return rest -> {
            if (StringUtil.isBlank(rest)) {
                rest = "40";
            }
            if (StringUtil.isNumeric(rest)) {
                rest = "rosszlanyok.php?pid=szexpartner-lista&htext=" + rest;
            }
            return rest;
        };
    }

    @Override
    public Iterator<?> input(JsonNode parent) {
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
                "src/main/resources/lists.txt"
        };
    }

    @Override
    public int page() {
        ids.clear();
        return 1;
    }

    @Override
    public File out() {
        return new File("target/lists.txt");
    }

    @Override
    public Object apply(String resp) {
        var soup = Jsoup.parse(resp);
        var list = soup.select("div.listOuterDiv");
        List res = new LinkedList();
        var titles = soup.select("h1#listTitleH1,h2.subTitle,title");
        String title = null;
        for (Element el : titles) {
            String text = el.text();
            if (title == null || (text.length() < title.length() && text.length() > 1)) {
                title = text;
            }
        }
        boolean idChanged = false;
        for (Element mainDiv : list) {
            var hn = mainDiv.selectFirst("div.hiddenNev");
            if (hn == null) continue;
            Integer id = Integer.parseInt(mainDiv.attr("data-memberthumb"));
            String hiddenNev = hn.text();
            if (!ids.add(id)) continue;
            idChanged = true;
            String age = mainDiv.selectFirst("div.nev").text().replaceAll(".+\\(([0-9+]+)\\)", "$1");
            var lti = mainDiv.select("img.listTagImage");
            String pic = lti.attr("data-src");
            if (pic.isEmpty()) {
                pic = lti.attr("src");
            }
            res.add(new Object[]{id, hiddenNev, age, pic});
        }
        if (!idChanged) {
            ids.clear();
        }
        return idChanged ? Map.of("title", title, "list", res) : null;
    }
}
