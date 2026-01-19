package hu.detox.szexpartnerek.rl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.detox.szexpartnerek.Persister;
import hu.detox.szexpartnerek.Serde;
import hu.detox.szexpartnerek.TrafoEngine;
import hu.detox.szexpartnerek.Utils;
import okhttp3.HttpUrl;
import org.jetbrains.annotations.NotNull;
import org.jsoup.Jsoup;
import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.sql.SQLException;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Advertiser implements TrafoEngine, Flushable {
    public static final Advertiser INSTANCE = new Advertiser();
    private static final String EMAILP = "mailto:";
    private static final String DATEP = "\\d{4}-\\d{2}-\\d{2}";
    private static final String ENUMS = "src/main/resources/enums.properties";
    private static final Pattern IDP = Pattern.compile("(id=|member/|adatlap/)([0-9]+)");
    private static final Pattern DATEF = Pattern.compile(DATEP);
    private static final Pattern LOOKING_AGE = Pattern.compile("(\\d+)\\D*(felett)?\\D*(\\d+)?\\D*(alatt)?");
    private static final Pattern READING = Pattern.compile("(\\d+) levél, (\\d+) olvasatlan");
    private static final Pattern MEASUERS = Pattern.compile("(\\d+\\+?)\\s*(éves|kg|mell|derék|csípő|cm)");

    private transient AdvertiserPersister persister;
    private final Map.Entry<String, Properties> props = new AbstractMap.SimpleEntry<>("properties", new Properties());
    private final Map.Entry<String, Properties> massages = new AbstractMap.SimpleEntry<>("massage", new Properties());
    private final Map.Entry<String, Properties> likes = new AbstractMap.SimpleEntry<>("likes", new Properties());
    private final Map.Entry<String, Properties> lookings = new AbstractMap.SimpleEntry<>("looking", new Properties());
    private final Map.Entry<String, Properties> answers = new AbstractMap.SimpleEntry<>("answers", new Properties());
    private int changedProps;
    private Map<String, String> map;
    private Map<String, String> massage;
    private Map<String, String> looking;
    private Map<String, String> massageReverse = new HashMap<>();

    private Advertiser() {
        try {
            persister = new AdvertiserPersister();
            loadEnums();
            map = Utils.map("src/main/resources/adv-mapping.kv");
            massage = Utils.map("src/main/resources/massage-mapping.kv");
            looking = Utils.map("src/main/resources/looking-mapping.kv");
            for (Map.Entry<String, String> me : massage.entrySet()) {
                for (String altm : me.getValue().split(",")) {
                    massageReverse.put(altm, me.getKey());
                }
            }
            Map<String, String> lm = Utils.map("src/main/resources/list-mapping.kv");
            for (Map.Entry<String, String> me : lm.entrySet()) {
                addProp(props, null, null, Utils.normalize(me.getValue()));
            }
        } catch (IOException | SQLException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @Override
    public int page() {
        return 0;
    }

    @Override
    public Function<String, String> url() {
        return s -> "rosszlanyok.php?pid=szexpartner-data&id=" + s + "&instantStat=0";
    }

    @Override
    public Iterator<?> input(JsonNode parent) {
        ArrayNode an = (ArrayNode) parent.get(New.PARTNERS);
        if (an != null) {
            return an.iterator();
        } else {
            ArrayList<Integer> res = new ArrayList<>(2000);
            for (JsonNode ian : parent) {
                for (JsonNode ien : ian) {
                    res.add(ien.get("id").asInt());
                }
            }
            return res.iterator();
        }
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
    public File in() {
        return new File("src/main/resources/partnerek.txt");
    }

    @Override
    public File out() {
        return new File("target/partnerek.txt");
    }

    private void loadEnums() throws IOException {
        Properties fileProps = new Properties();
        try (FileInputStream fis = new FileInputStream(ENUMS)) {
            fileProps.load(fis);
        } catch (FileNotFoundException fnf) {
            // No worries
        }
        for (Map.Entry<Object, Object> keyVal : fileProps.entrySet()) {
            String[] parts = ((String) keyVal.getKey()).split("\\.");
            String enumType = parts[0];
            String value = (String) keyVal.getValue();
            if (enumType.equals(props.getKey()))
                props.getValue().put(keyVal.getKey(), value);
            else if (enumType.equals(lookings.getKey()))
                lookings.getValue().put(keyVal.getKey(), value);
            else if (enumType.equals(likes.getKey()))
                likes.getValue().put(keyVal.getKey(), value);
            else if (enumType.equals(answers.getKey()))
                answers.getValue().put(keyVal.getKey(), value);
            else if (enumType.equals(massages.getKey()))
                massages.getValue().put(keyVal.getKey(), value);
        }
    }

    private String text(Element el, String... attrs) {
        if (el == null) return null;
        String data = null;
        for (String att : attrs) {
            data = Utils.normalize(el.attr(att));
            if (data != null) break;
        }
        if (data == null) data = Utils.normalize(el.text());
        return data;
    }

    private Element html(Element el, String repl) {
        if (repl == null) repl = ",";
        return Jsoup.parseBodyFragment(el.html().replaceAll("<br\\s*/?>", repl)).body();
    }

    private boolean addMassage(ArrayNode arr, String prp) {
        String enm = Utils.toEnumLike(prp);
        boolean anyMass = enm != null && enm.contains("MASSZAZS") && !enm.equals("MASSZAZS");
        enm = massageReverse.get(enm);
        boolean ret = enm != null || anyMass;
        if (ret) {
            if (enm == null) {
                enm = massage.keySet().iterator().next();
            }
            addProp(massages, arr, null, enm);
        }
        return ret;
    }

    private Integer doAddProp(Map.Entry<String, Properties> map, ArrayNode arr, String prp) {
        Integer res = null;
        if (prp != null) {
            res = Integer.parseInt((String) map.getValue().computeIfAbsent(map.getKey() + "." + prp,
                    k -> {
                        changedProps++;
                        System.err.println("** Property " + map.getKey() + "." + prp + " added");
                        return "" + map.getValue().size();
                    }));
            if (arr != null) {
                boolean alreadyPresent = false;
                for (JsonNode node : arr) {
                    if (node.isInt() && node.intValue() == res) {
                        alreadyPresent = true;
                        break;
                    }
                }
                if (!alreadyPresent) arr.add(res);
            }
        }
        return res;
    }

    private Integer addProp(Map.Entry<String, Properties> map, ArrayNode arr, ArrayNode propsArr, String prp) {
        String enm = Utils.toEnumLike(prp);
        enm = this.map.getOrDefault(enm, enm);
        if (enm == null) return null;
        Integer res = null;
        if (map == lookings) {
            String nenm = looking.get(enm);
            if (nenm == null) {
                for (String w : looking.get("MINDEN").split(",")) {
                    if (enm.contains(w)) {
                        res = doAddProp(map, arr, w);
                    }
                }
            } else if (nenm.equals("-")) {
                if (propsArr != null) doAddProp(props, propsArr, enm);
                return null;
            }
            enm = nenm;
        }
        if (res == null) res = doAddProp(map, arr, enm);
        return res;
    }

    private void quality(Element dataCol, ArrayNode propsArr, ObjectNode result) {
        for (Element li : dataCol.select("li.check")) {
            String txt = text(li);
            if (txt == null
                    || txt.contains("adatlap kitöltés") || txt.contains("beigazolódott")
                    || txt.contains("Belépett")) continue;
            addProp(props, propsArr, null, txt);
        }
    }

    private void secondData(Element dataCol, ArrayNode propsArr, ArrayNode massageArr, ObjectNode result) {
        ArrayNode openArr = Serde.OM.createArrayNode();
        Elements rows = dataCol.select("table.dataUpperRightTable tr");
        Map<String, Integer> dayMap = Map.of(
                "hétfő", 0, "kedd", 1, "szerda", 2, "csütörtök", 3, "péntek", 4, "szombat", 5, "vasárnap", 6
        );
        String[] openHours = new String[7];
        Arrays.fill(openHours, null);
        for (Element row : rows) {
            String days = text(row.selectFirst("td[align=right]"));
            String time = text(row.select("td").get(1));
            if (time != null) for (String d : dayMap.keySet()) {
                if (days.contains(d)) openHours[dayMap.get(d)] = time;
            }
        }
        for (String oh : openHours) openArr.add(oh);
        result.set("openHours", openArr);

        ArrayNode langsArr = Serde.OM.createArrayNode();
        for (Element img : dataCol.select("img[src*=flags]")) {
            String src = img.attr("src");
            Matcher m = Pattern.compile("flags/(\\w+)\\.").matcher(src);
            if (m.find()) langsArr.add(m.group(1));
        }
        result.set("langs", langsArr);

        Element msgStat = dataCol.selectFirst("div#msgLoginStatMiddle");
        if (msgStat != null) {
            Matcher m = READING.matcher(msgStat.text());
            if (m.find()) {
                ArrayNode msgsArr = Serde.OM.createArrayNode();
                msgsArr.add(Integer.parseInt(m.group(1)));
                msgsArr.add(Integer.parseInt(m.group(2)));
                result.set("msgs", msgsArr);
            }
        }

        // PROPS enum (br-ek alapján, továbbiak)
        String txt;
        try {
            var dc = dataCol.child(0);
            txt = html(dc, null).ownText();
            for (String prop : txt.split(",")) {
                prop = Jsoup.parseBodyFragment(prop).text();
                if (!addMassage(massageArr, prop)) {
                    addProp(props, propsArr, null, prop);
                }
            }
        } catch (IndexOutOfBoundsException ioob) {
            // Ok, no props
        }
        txt = text(dataCol.selectFirst("a[href^=" + EMAILP + "]"), "href");
        if (txt != null) result.put("email", txt.replaceFirst(EMAILP, ""));
    }

    private ArrayNode firstData(Element dataCol, ArrayNode propsArr, ObjectNode result) {
        Element seg = html(dataCol, "⚠️");
        Element a = seg.selectFirst("a");
        if (a != null) a.append("⚠️");
        String txt = text(seg).replaceAll("Fontos:", "⚠️");

        int idx2 = txt.indexOf("⚠️");
        ArrayNode loc = null;
        if (!txt.contains("Nincs személyes találkozás")) {
            loc = Serde.OM.createArrayNode();
            int idx = txt.indexOf("⚠️"); // Remove the location
            loc.add(Utils.normalize(txt.substring(0, idx)));
            idx2 = txt.indexOf("⚠️", idx + 1);
            loc.add(Utils.normalize(txt.substring(idx + 3, idx2).replace("Térkép", "")));
            result.put("location", loc);
        }
        txt = parseOutMeasures(result, txt.substring(idx2 + 1));

        for (String prop : txt.split("[,⚠️]\\s*")) {
            addProp(props, propsArr, null, prop);
        }
        result.set("properties", propsArr);
        return loc;
    }

    @NotNull
    private void parseOutMyInfo(ObjectNode result, ArrayNode propsArr, String txt) {
        var arr = Serde.OM.createArrayNode();
        Map.Entry<String, Properties> props = null;
        var answerMap = Serde.OM.createObjectNode();
        String enumOfQ = null;
        for (String val : txt.split("⚠️")) {
            if (val.contains("💕")) {
                if (val.contains("it keresek")) props = lookings;
                else if (val.contains("Amilyen vagyok")) props = answers;
                else if (val.contains("Amit még")) {
                    int cln = val.indexOf(':');
                    result.put("knowit", Utils.normalize(val.substring(cln + 1)));
                }
                continue;
            }
            if (props == lookings) {
                val = val.replace("Negyed (SOS francia)", "SOS")
                        .replace("órára", "").replace("akár", "");
                var am = LOOKING_AGE.matcher(val);
                if (val.length() > 40) {
                    result.put("expect", Utils.normalize(val));
                } else if (am.find()) {
                    Integer from = Integer.valueOf(am.group(1));
                    Integer to;
                    ArrayNode ara = Serde.OM.createArrayNode();
                    if (StringUtil.isNumeric(am.group(3))) {
                        to = Integer.valueOf(am.group(3));
                        if (from < to) {
                            ara.add(from);
                            ara.add(to);
                        } else {
                            ara.add(to);
                            ara.add(from);
                        }
                    } else {
                        if (!StringUtil.isBlank(am.group(2))) {
                            ara.add(18);
                        }
                        ara.add(from);
                    }
                    result.put("looking_age", ara);
                } else {
                    for (String iv : val.split(",")) {
                        addProp(lookings, arr, propsArr, iv);
                    }
                }
            } else if (props == answers) {
                if (enumOfQ == null) {
                    enumOfQ = val;
                } else {
                    int qenum = addProp(props, arr, null, enumOfQ);
                    answerMap.put(String.valueOf(qenum), val);
                    enumOfQ = null;
                }
            }
        }
        if (!answerMap.isEmpty()) result.put("answers", answerMap);
        if (!arr.isEmpty()) result.put("looking", arr);
    }

    @NotNull
    private String parseOutMeasures(ObjectNode result, String txt) {
        ObjectNode measures = Serde.OM.createObjectNode();
        Matcher all = MEASUERS.matcher(txt);
        StringBuilder sb = new StringBuilder();
        while (all.find()) {
            all.appendReplacement(sb, "");
            String val = Utils.normalize(all.group(1));
            if (val == null) continue;
            String mode = all.group(2);
            mode = switch (mode) {
                case "éves" -> "age";
                case "kg" -> "weight";
                case "mell" -> "breast";
                case "derék" -> "waist";
                case "csípő" -> "hips";
                case "cm" -> "height";
                default -> null;
            };
            if (mode != null) {
                try {
                    Integer mi = Integer.parseInt(val);
                    measures.put(mode, mi);
                } catch (NumberFormatException nfe) {
                    measures.put(mode, val);
                }
            }

        }
        result.put("measures", measures);
        all.appendTail(sb);
        return sb.toString();
    }

    @Override
    public ObjectNode apply(String html) {
        Document doc = Jsoup.parse(html);
        ObjectNode result = Serde.OM.createObjectNode();
        ArrayNode propsArr = Serde.OM.createArrayNode();
        ArrayNode massageArr = Serde.OM.createArrayNode();

        Element leftContainer = doc.selectFirst("div#girlMainLeftContainer");
        Element err = doc.selectFirst(".mainError");
        if (err != null || leftContainer == null) {
            System.err.println("Failed to get" +
                    (err == null ? "" : ", error is: " + err.text()) + "!");
            return null;
        }

        String name = text(doc.selectFirst(".mainDataRow a.datasheetColorLink,div#memberReportingMain p.title"));
        result.put("name", name.replace(" - Jelentés", ""));
        name = text(
                doc.selectFirst("div#externalContainer a[href~=(member|adatlap)], form#tag_felhaszn_comment"),
                "href", "action");
        Matcher m = IDP.matcher(name);
        m.find();
        name = m.group(2);
        result.put("id", Integer.parseInt(name));

        String intro = null;
        for (Element s : doc.select("div:containsOwn(Jelige: ),div#content h1")) {
            intro = text(s);
            if (intro != null && intro.contains("Jelige")) {
                break;
            }
        }
        intro = Utils.normalize(intro);
        if (intro != null && (intro.startsWith(name) || intro.endsWith(".hu"))) intro = null;
        result.put("pass", intro);

        ArrayNode phoneArr = Serde.OM.createArrayNode();
        Elements phoneLinks = doc.select("a.phone-number");
        for (Element link : phoneLinks) {
            String txt = link.text().replaceAll("[^\\d+]", "");
            if (!txt.isEmpty()) phoneArr.add(txt);
            for (Element img : link.select("img")) {
                String alt = img.attr("src").replaceAll(".+/([a-z]+)_icon.+", "$1");
                addProp(props, phoneArr, null, alt);
            }
        }
        result.set("phone", phoneArr);

        Elements dataCols = leftContainer.select("div.dataSheetColumnData");
        ArrayNode location = firstData(dataCols.get(0), propsArr, result);
        if (location != null) {
            String glink = text(doc.selectFirst("div#mapsInnerContainer iframe"), "src");
            if (glink != null) {
                glink = HttpUrl.get(glink).queryParameter("q");
                if (glink.matches("[0-9.,]+")) { // geo coordinate
                    location.add(glink);
                }
            }
        }
        secondData(dataCols.get(1), propsArr, massageArr, result);

        var inact = doc.selectFirst("div#phoneMiddle div:containsOwn(Most inaktív)");
        if (inact == null) {
            quality(doc.selectFirst("div#rightVerifiedStat ul"), propsArr, result);
            Element lf = html(doc.selectFirst("div#dataLowerRight"), "⚠️");
            lf.select("h2").append("💕⚠️");
            lf.select(".dlBAnswer").append("⚠️");
            parseOutMyInfo(result, propsArr, lf.text());
            result.put("active", true);
        } else {
            inact = doc.selectFirst("div#statusContainer span:containsOwn(Legközelebb)");
            name = null;
            if (inact != null) {
                m = DATEF.matcher(inact.text());
                while (m.find()) {
                    name = m.group(0);
                }
            }
            if (name == null) {
                result.put("active", false);
            } else {
                result.put("active", name);
            }
        }

        Element logContainer = leftContainer.selectFirst("div#logContainer");
        if (logContainer != null) {
            ObjectNode activity = Serde.OM.createObjectNode();
            for (Element font : logContainer.select("font.leftLikes2")) {
                String txt = font.text().trim();
                String[] parts = txt.split(" ", 2);
                if (parts.length == 2 && parts[0].matches("^[0-9-]+")) activity.put(parts[0], parts[1]);
            }
            result.set("activity", activity);
        }

        Element imagesDiv = leftContainer.selectFirst("div#imagesDiv");
        if (imagesDiv != null) {
            ArrayNode imgsArr = Serde.OM.createArrayNode();
            for (Element imgCont : imagesDiv.select("div.imageEmelemntContainer")) {
                Element img = imgCont.selectFirst("img");
                if (img != null) {
                    String src = img.attr("src");
                    String title = img.attr("title");
                    m = Pattern.compile("Feltöltve: (" + DATEP + ")").matcher(title);
                    String date = m.find() ? m.group(1) : "";
                    ArrayNode imgData = Serde.OM.createArrayNode();
                    imgData.add(Utils.normalize(date));
                    imgData.add(src);
                    imgsArr.add(imgData);
                }
            }
            result.set("imgs", imgsArr);
        }

        Element aboutDiv = leftContainer.selectFirst("div#bemutatkozasContainer");
        if (aboutDiv != null) {
            aboutDiv.select("div,span").remove();
            String introHtml = aboutDiv.html().replaceAll("\\s+", " ").replaceAll(">\\s+<", "><").trim();
            result.put("about", introHtml);
        }

        Element likesDiv = leftContainer.selectFirst("div#dsLeftLikeContainer");
        if (likesDiv != null) {
            ObjectNode possibilitiesObj = Serde.OM.createObjectNode();
            ArrayNode yesArr = Serde.OM.createArrayNode();
            ArrayNode askArr = Serde.OM.createArrayNode();
            ArrayNode noArr = Serde.OM.createArrayNode();
            for (Element font : likesDiv.select("font")) {
                String txt = font.text().replaceAll("\\s*\\(ha megkérsz\\)", "");
                if (txt.contains(":")) {
                    int cln = txt.indexOf(':');
                    String key = Utils.toEnumLike(txt.substring(0, cln));
                    result.put(key, Utils.normalize(txt.substring(cln + 1)));
                } else if (!addMassage(massageArr, txt)) {
                    ArrayNode arr = noArr;
                    if (font.hasClass("leftLikes1")) arr = yesArr;
                    else if (font.hasClass("leftLikes2")) arr = askArr;
                    addProp(likes, arr, null, txt);
                }
            }
            possibilitiesObj.set("no", noArr);
            possibilitiesObj.set("yes", yesArr);
            possibilitiesObj.set("ask", askArr);
            result.set("likes", possibilitiesObj);
        }
        result.put("massages", massageArr);
        return result;
    }

    @Override
    public void flush() throws IOException {
        if (changedProps == 0) return;
        try (FileOutputStream fos = new FileOutputStream(ENUMS)) {
            props.getValue().store(fos, "Properties of advertisers");
            likes.getValue().store(fos, "Likes and preferences of services of advertisers");
            lookings.getValue().store(fos, "What the advertiser can accept");
            answers.getValue().store(fos, "Answers given to common questions");
            massages.getValue().store(fos, "Advertiser supported massages");
        }
        try {
            persister.saveProps(ENUMS);
        } catch (SQLException ex) {
            throw new IOException(ex);
        }
        changedProps = 0;
    }
}