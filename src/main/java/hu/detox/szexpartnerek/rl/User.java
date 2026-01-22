package hu.detox.szexpartnerek.rl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.detox.szexpartnerek.Persister;
import hu.detox.szexpartnerek.TrafoEngine;
import hu.detox.szexpartnerek.Utils;
import org.jsoup.Jsoup;
import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class User implements TrafoEngine, TrafoEngine.Filteres {
    public static final String IDR = "user_id";
    public static final User INSTANCE = new User();
    private static final TrafoEngine[] SUB = new TrafoEngine[]{UserReview.INSTANCE};
    private transient Collection<String> idList = List.of();
    private transient UserPersister persister;
    private Map<String, String> propMapping;

    private User() {
        try {
            propMapping = Utils.map("src/main/resources/prop-mapping.kv");
            persister = new UserPersister();
            idList = persister.getUntouchableIds();
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

    public ObjectNode getNodeFromLines(String... lines) {
        String nameLine = lines[0];
        String formLine = lines[1];
        String infoText = lines[2] + " " + lines[3];

        ObjectNode result = JsonNodeFactory.instance.objectNode();

        // Extract name and optional data
        Document soup = Jsoup.parse(nameLine);
        String nameFull = soup.text().trim();
        String name, opt;
        int idx = nameFull.indexOf(" (");
        if (idx != -1) {
            name = nameFull.substring(0, idx).trim();
            opt = nameFull.substring(idx + 2).replaceAll("\\)$", "").trim();
        } else {
            name = nameFull;
            opt = "";
        }
        if (!name.isEmpty()) result.put("name", name);

        // Gender
        String gender = null;
        String optLower = opt.toLowerCase();
        if (optLower.contains("férfi")) gender = "FIU";
        else if (optLower.contains("nő")) gender = "LANY";
        else if (optLower.contains("transz")) gender = "TRANSZSZEXUALIS";
        if (gender != null) result.put("gender", gender);

        Matcher ageMatch = Pattern.compile("(\\d+\\+?)\\s*éves").matcher(opt);
        if (ageMatch.find()) result.put("age", ageMatch.group(1));

        Matcher heightMatch = Pattern.compile("(\\d+)\\s*cm").matcher(opt);
        if (heightMatch.find()) result.put("height", Integer.parseInt(heightMatch.group(1)));

        Matcher weightMatch = Pattern.compile("(\\d+)\\s*kg").matcher(opt);
        if (weightMatch.find()) result.put("weight", Integer.parseInt(weightMatch.group(1)));

        Matcher sizeMatch = Pattern.compile("(\\d+)\\s*cm\\s*intim méret").matcher(infoText);
        if (sizeMatch.find()) result.put("size", Integer.parseInt(sizeMatch.group(1)));

        Matcher regMatch = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})-én regisztrált").matcher(infoText);
        if (regMatch.find()) result.put("regd", regMatch.group(1));

        Matcher lastMatch = Pattern.compile("(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2})-kor járt itt").matcher(infoText);
        if (lastMatch.find()) result.put("last", lastMatch.group(1));

        Matcher idMatch = Pattern.compile("id=(\\d+)").matcher(formLine);
        if (idMatch.find()) result.put("id", Integer.parseInt(idMatch.group(1)));

        if (infoText.contains("Amit szeret:")) {
            Matcher likesMatcher = Pattern.compile("Amit szeret:(.*?)(?:</div>|$)", Pattern.DOTALL).matcher(infoText);
            if (likesMatcher.find()) {
                String likesStr = likesMatcher.group(1).trim();
                // Apply replacements to avoid comma problems
                likesStr = likesStr.replace("Domina, ", "Domina ");
                likesStr = likesStr.replace(", Rab", " Rab");
                likesStr = likesStr.replace("s, b", "s b");
                likesStr = likesStr.replace(", tort", " tort");
                likesStr = likesStr.replace(", CBT", " CBT");
                // Split and add to array
                String[] likesArr = likesStr.split(", ");
                ArrayNode likesNode = JsonNodeFactory.instance.arrayNode();
                for (String like : likesArr) {
                    like = Utils.toEnumLike(like);
                    like = propMapping.getOrDefault(like, like);
                    if (like != null) {
                        likesNode.add(like);
                    }
                }
                if (!likesNode.isEmpty()) {
                    result.set("likes", likesNode);
                }
            }
        }
        return result;
    }

    @Override
    public Iterator<?> input(JsonNode parent) {
        ArrayNode an = (ArrayNode) parent.get(New.USERS);
        JsonNode uid = parent.get(User.IDR);
        if (an != null) {
            return an.iterator();
        } else if (uid != null) {
            return List.of(uid).iterator();
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
        return new File("target/gen-users.jsonl");
    }

    @Override
    public ObjectNode apply(String data) {
        var soup = Jsoup.parse(data);
        Element frst = soup.selectFirst("#about-me-user-list");
        String frstln = frst == null ? "" : Utils.text(frst);
        String name = Utils.text(soup.selectFirst("div#content h1"));
        return name == null ? null : getNodeFromLines(
                name, soup.selectFirst("td#felsoLanguage a").attr("href"),
                soup.selectFirst("table#dataUpperTable").text(),
                frstln
        );
    }

    @Override
    public boolean skips(String in) {
        return in == null || this.idList.contains(in);
    }
}
