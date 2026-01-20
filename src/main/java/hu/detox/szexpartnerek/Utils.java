package hu.detox.szexpartnerek;

import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.Element;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Map;

public class Utils {
    public static final String PAGER = "pager";

    public static RequestBody bodyOf(String form) {
        return RequestBody.create(
                form,
                MediaType.parse("application/x-www-form-urlencoded; charset=UTF-8")
        );
    }

    public static Map<String, String> map(String file) throws IOException {
        LinkedHashMap<String, String> res = new LinkedHashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String ln;
            while ((ln = br.readLine()) != null) {
                String[] lna = ln.split("=");
                res.put(lna[0], lna.length > 1 ? lna[1] : null);
            }
        }
        return res;
    }

    public static String text(Element el, String... attrs) {
        if (el == null) return null;
        String data = null;
        for (String att : attrs) {
            data = Utils.normalize(el.attr(att));
            if (data != null) break;
        }
        if (data == null) data = Utils.normalize(el.text());
        return data;
    }

    public static Object getField(JsonNode node, String key) {
        JsonNode value = node.get(key);
        if (value == null || value.isNull()) return null;
        if (value.isInt()) return value.intValue();
        if (value.isLong()) return value.longValue();
        if (value.isDouble()) return value.doubleValue();
        if (value.isTextual()) return value.textValue();
        if (value.isBoolean()) return value.booleanValue();
        return value.toString();
    }

    public static String normalize(String data) {
        if (data == null) return data;
        //data = data.replaceAll(".*\"([^\"]+)\".*", "$1");
        data = data.trim();
        if (StringUtil.isBlank(data) || data.equals("-") || data.equals("null")) data = null;
        return data;
    }

    public static String toEnumLike(String input) {
        if (input == null) return null;
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String underscored = normalize(normalized.replaceAll("[^A-Za-z_ ]", ""));
        if (underscored == null) return null;
        underscored = underscored.replaceAll("\\s+", "_");
        return underscored.toUpperCase();
    }

}
