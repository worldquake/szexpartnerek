package hu.detox.szexpartnerek;

import org.jsoup.internal.StringUtil;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Map;

public class Utils {

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
