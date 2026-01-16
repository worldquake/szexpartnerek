package hu.detox.szexpartnerek.rl;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.detox.szexpartnerek.Serde;
import hu.detox.szexpartnerek.TrafoEngine;
import hu.detox.szexpartnerek.Utils;
import okhttp3.Response;
import org.jsoup.Jsoup;
import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.Element;

import java.io.*;
import java.util.*;
import java.util.function.Function;

public class Lista implements TrafoEngine {
    private Set<Integer> ids = new HashSet<>();

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
    public File in() {
        return new File("src/main/resources/listak.txt");
    }

    @Override
    public int page() {
        ids.clear();
        return 1;
    }

    @Override
    public File out() {
        return new File("target/listak.txt");
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

    public ObjectNode combineAll() throws IOException {
        ObjectNode on = Serde.OM.createObjectNode();
        Map<String, String> map = Utils.map("src/main/resources/list-mapping.kv");
        try (Serde cont = new Serde(null, new BufferedReader(new FileReader(out())))) {
            Response r;
            while ((r = cont.next()) != null) {
                ObjectNode n = (ObjectNode) Serde.OM.readTree(r.body().string());
                ArrayNode ian = (ArrayNode) n.get("list");
                String tit = n.get("title").asText();
                tit = Utils.toEnumLike(map.getOrDefault(tit, tit));
                ArrayNode can = (ArrayNode) on.get(tit);
                if (can == null) {
                    can = Serde.OM.createArrayNode();
                    on.put(tit, can);
                }
                can.addAll(ian);
            }
        }
        try (PrintStream ps = new PrintStream(out().getPath() + ".json")) {
            Serde.OM.writeValue(ps, on);
        }
        return on;
    }
}
