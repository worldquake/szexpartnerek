package hu.detox.szexpartnerek.rl;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.detox.szexpartnerek.*;
import org.jsoup.Jsoup;
import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.Comment;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.File;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class UserReview extends Mapper {
    public static final UserReview INSTANCE = new UserReview();
    public static final Pattern P_AGE = Pattern.compile("\\(([0-9]+)\\|");
    private static String[] SMODES = "accepted received questioned hidden".split(" ");
    private static String[] RATES = "Környezet Külső Hozzáállás Technika Összkép".split(" ");
    private static String[] TEXTS = "Kapcsolatfelvétel,Lakás,Külső,Hozzáállás,Együttlét,Elégedettség és Ajánlás".split(",");
    private static Pattern MODEP;
    public final UserReviewPersister persister;

    static {
        String[] arr = "Elfogadott Kapott|Írt Kérdőjeles Elutasított".split(" ");
        MODEP = Pattern.compile("(" + StringUtil.join(arr, "|") + ") \\(([0-9]+)\\)");
    }

    protected UserReview() {
        super("src/main/resources/prop-mapping.kv");
        try {
            persister = new UserReviewPersister(Main.APP.getConn());
        } catch (SQLException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public boolean post() {
        return true;
    }

    @Override
    public Function<String, String> url() {
        return rest -> {
            if (StringUtil.isBlank(rest)) return null;
            if (StringUtil.isNumeric(rest)) {
                rest = "4layer/user_left_beszamolo.php?id=" + rest;
            }
            return rest;
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
        return persister;
    }

    @Override
    public String[] in() {
        return new String[]{
                "src/main/resources/users-reviews.txt"
        };
    }

    @Override
    public File out() {
        return new File("target/gen-user-reviews.jsonl");
    }

    protected int pageSize() {
        return 25;
    }

    @Override
    public Pager pager() {
        return new Pager() {
            private transient int[] max;
            private transient int[] curr;
            private int offset;
            private int mode;

            @Override
            public void reset() {
                max = null;
                curr = null;
                offset = 0;
                mode = 0;
            }

            @Override
            public void first(JsonNode node) {
                try {
                    max = Serde.OM.treeToValue(node.get("pager"), int[].class);
                    curr = new int[max.length];
                } catch (JsonProcessingException e) {
                    throw new IllegalStateException(e);
                }
            }

            @Override
            public int current(JsonNode node) {
                if (max == null) first(node);
                int cr = 0, cc = 0;
                for (String sm : SMODES) {
                    String key = addProp(fbtype, null, null, sm).toString();
                    JsonNode nd = node.get(key);
                    cc += curr[cr++] += nd == null ? 0 : nd.size();
                }
                boolean cont = hasNext();
                if (cont) nextMode();
                return cont ? cc : -1;
            }

            @Override
            public boolean hasNext() {
                return mode < SMODES.length;
            }

            private void nextMode() {
                if (max == null || max[mode] > curr[mode]) return;
                int retm = max.length;
                for (int mxi = mode + 1; mxi < max.length; mxi++) {
                    if (max[mxi] > 0) {
                        retm = mxi;
                        offset = 0;
                        break;
                    }
                }
                mode = retm;
            }

            @Override
            public String next() {
                String ret = Integer.toString(offset);
                ret = "offset=" + ret + "&status=" + SMODES[mode];
                offset += pageSize();
                return ret;
            }
        };
    }

    protected ObjectNode readSingle(Integer idp, Element elem) {
        var ret = Serde.OM.createObjectNode();
        ret.put("user_id", idp);

        // 1. "ts" field: date from onmouseover attribute in the right dateDiv
        Element dateDiv = elem.selectFirst("div[id^=dateDiv]");
        Element a = elem.selectFirst("a[href], a[onclick]");
        if (dateDiv == null || a == null) return null;
        String onMouseOver = dateDiv.attr("onmouseover");
        Integer id = Integer.parseInt(dateDiv.id().replace("dateDiv", ""));
        ret.put("id", id);
        Matcher m = Pattern
                .compile("'(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})'").matcher(onMouseOver);
        if (m.find()) {
            ret.put("ts", m.group(1));
        }

        // 2. "name" and "partner_id"
        String name = Utils.normalize(a.text());
        if (!"Törölt_Adatlap".equals(name)) ret.put("name", name);
        String href = a.attr("href");
        Matcher idMatch = Pattern.compile("id=(\\d+)").matcher(href);
        if (idMatch.find()) {
            ret.put("partner_id", Integer.parseInt(idMatch.group(1)));
        }

        // 3. "after_name" (text after <a>, cleaned)
        if (a.parent() != null) {
            String after = Utils.normalize(a.parent().ownText()
                    .replace("(inaktív)", "")
                    .replace(')', '|'));
            if (after != null) {
                m = P_AGE.matcher(after);
                if (m.find()) {
                    ret.put("age", Integer.parseInt(m.group(1)));
                    after = after.substring(m.end()).trim();
                }
                if (after.startsWith("|")) after = after.substring(1);
                after = Utils.normalize(after);
                if (after != null) ret.put("after_name", after);
            }
        }

        // 4. "useful" from hasznosDiv
        Element hasznosDiv = elem.selectFirst("#hasznosDiv");
        int haszn = 0;
        if (hasznosDiv != null) {
            m = Pattern.compile("\\((\\d+)\\)").matcher(hasznosDiv.text());
            if (m.find()) {
                haszn = Integer.parseInt(m.group(1));
            }
        }
        ret.put("useful", haszn);

        // 5. "rates" array
        int[] ratesArr;
        Elements ratingLabels = elem.select(".ratingLabel");
        Elements ratingStars = elem.select(".ratingStars img[alt]");
        if (ratingLabels.size() == RATES.length) {
            ratesArr = new int[RATES.length];
            for (int i = 0; i < RATES.length; i++) {
                int rate = -1;
                Element rl = ratingLabels.get(i);
                int idx = addProp(fbrtype, null, null, rl.text());
                if (i < ratingStars.size()) {
                    String alt = ratingStars.get(i).attr("alt");
                    try {
                        rate = Integer.parseInt(alt);
                    } catch (Exception ignore) {
                    }
                }
                ratesArr[idx] = rate;
            }
            ret.put("rates", Serde.OM.valueToTree(ratesArr));
        }

        // 6. "good" and "bad" arrays
        ArrayNode goodArr = ret.putArray("good");
        ArrayNode badArr = ret.putArray("bad");
        // Good: border-color: #5AEA28 or rgb(90,234,40)
        for (Element div : elem.select("div[style]")) {
            String style = div.attr("style");
            ArrayNode an = style.contains("5AEA28") ? goodArr : style.contains("FF0000") ? badArr : null;
            if (an != null) for (String el : Utils.normalize(div.text()).split(", ")) {
                addProp(fbgbtype, an, null, el);
            }
        }

        // 7. "details" object
        ObjectNode details = ret.putObject("details");
        Element detailsDiv = elem.selectFirst("div[style*=font-size: 11px]");
        if (detailsDiv != null) {
            for (String label : TEXTS) {
                String val = null;
                for (Element p : detailsDiv.select("p")) {
                    Element font = p.selectFirst("font");
                    if (font != null && font.text().replace(":", "").trim().equals(label)) {
                        val = Utils.normalize(p.ownText());
                        break;
                    }
                }
                if (val != null) {
                    id = addProp(fbdtype, null, null, label);
                    details.put(id.toString(), val);
                }
            }
        }

        return ret;
    }

    protected String[] selectors() {
        return new String[]{"div#beszamoloMainContent>div"};
    }

    @Override
    public ObjectNode apply(String s) {
        Document soup = Jsoup.parse(s);
        String[] sels = selectors();
        Comment cmt = ((Comment) soup.childNode(0));
        Matcher m = Advertiser.IDP.matcher(cmt.getData());
        Integer idp = null;
        if (m.find()) idp = Integer.parseInt(m.group(2));
        Element hel = soup.selectFirst("div#beszamoloHeaderDiv");
        m = MODEP.matcher(hel.text());
        Elements chds = hel.children();
        int key = IntStream.range(0, chds.size())
                .filter(i -> chds.get(i).hasClass("bHTabAct"))
                .findFirst()
                .orElse(-1);
        ObjectNode res = Serde.OM.createObjectNode();
        ArrayNode pg = Serde.OM.createArrayNode();
        while (m.find()) {
            pg.add(Integer.parseInt(m.group(2)));
        }
        res.put("pager", pg);
        String okey = addProp(fbtype, null, null, SMODES[key]).toString();
        ArrayNode an = (ArrayNode) res.get(okey);
        if (an == null) {
            an = Serde.OM.createArrayNode();
        }
        for (Element iel : soup.select(sels[0])) {
            var con = readSingle(idp, iel);
            if (con == null) break;
            an.add(con);
        }
        if (!an.isEmpty()) res.put(okey, an);
        return res;
    }

    @Override
    protected Integer onEnum(Map.Entry<String, Properties> map, ArrayNode arr, ArrayNode propsArr, AtomicReference<String> en) {
        return null;
    }
}
