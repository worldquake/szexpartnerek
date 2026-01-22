package hu.detox.szexpartnerek;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import hu.detox.szexpartnerek.rl.Partner;

import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;

public abstract class Mapper implements TrafoEngine, Flushable {
    public static final String ENUMS = "src/main/resources/enums.properties";
    private final PreparedStatement enumCount;
    private final PreparedStatement enumDel;
    private final PreparedStatement enumAdd;
    private transient Properties addedProps = new Properties();
    protected final Map.Entry<String, Properties> props = new AbstractMap.SimpleEntry<>("properties", new Properties());
    protected final Map.Entry<String, Properties> massages = new AbstractMap.SimpleEntry<>("massage", new Properties());
    protected final Map.Entry<String, Properties> likes = new AbstractMap.SimpleEntry<>("likes", new Properties());
    protected final Map.Entry<String, Properties> lookings = new AbstractMap.SimpleEntry<>("looking", new Properties());
    protected final Map.Entry<String, Properties> answers = new AbstractMap.SimpleEntry<>("answers", new Properties());
    protected final Map.Entry<String, Properties> fbgbtype = new AbstractMap.SimpleEntry<>("fbgbtype", new Properties());
    protected final Map.Entry<String, Properties> fbrtype = new AbstractMap.SimpleEntry<>("fbrtype", new Properties());
    protected final Map.Entry<String, Properties> fbtype = new AbstractMap.SimpleEntry<>("fbtype", new Properties());
    protected final Map.Entry<String, Properties> fbdtype = new AbstractMap.SimpleEntry<>("fbdtype", new Properties());
    private Map<String, String> map;

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
            else if (enumType.equals(fbtype.getKey()))
                fbtype.getValue().put(keyVal.getKey(), value);
            else if (enumType.equals(fbgbtype.getKey()))
                fbgbtype.getValue().put(keyVal.getKey(), value);
            else if (enumType.equals(fbrtype.getKey()))
                fbrtype.getValue().put(keyVal.getKey(), value);
            else if (enumType.equals(fbdtype.getKey()))
                fbdtype.getValue().put(keyVal.getKey(), value);
        }
    }

    protected Mapper(String file) {
        try {
            Connection conn = Main.APP.getConn();
            this.enumDel = conn.prepareStatement("DELETE FROM int_enum");
            this.enumAdd = conn.prepareStatement("INSERT INTO int_enum (id, type, name) VALUES (?, ?, ?)");
            this.enumCount = conn.prepareStatement("SELECT count(*) FROM int_enum");
            try (ResultSet rs = enumCount.executeQuery()) {
                int enums = -1;
                if (rs.next()) enums = rs.getInt(1);
                if (enums == 0) resetProps();
            }
            map = Utils.map(file);
            loadEnums();
        } catch (IOException | SQLException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public void resetProps() throws IOException, SQLException {
        Properties props = new Properties();
        props.load(new BufferedReader(new FileReader(Partner.ENUMS)));
        enumDel.executeUpdate();
        addProps(props);
    }

    private void addProp(String key, String value) throws IOException, SQLException {
        addedProps.put(key, value);
        int eq = key.indexOf('.');
        if (eq < 0) return; // skip comments or invalid lines
        String type = key.substring(0, eq);
        String name = key.substring(eq + 1);
        int id;
        try {
            id = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return;
        }
        enumAdd.setInt(1, id);
        enumAdd.setString(2, type);
        enumAdd.setString(3, name);
        enumAdd.execute();

    }

    private void addProps(Properties props) throws IOException, SQLException {
        for (Map.Entry<Object, Object> kv : props.entrySet()) {
            addProp((String) kv.getKey(), (String) kv.getValue());
        }
    }

    protected Integer doAddProp(Map.Entry<String, Properties> map, ArrayNode arr, String prp) {
        Integer res = null;
        if (prp != null) {
            res = Integer.parseInt((String) map.getValue().computeIfAbsent(map.getKey() + "." + prp,
                    k -> {
                        String val = "" + map.getValue().size();
                        System.err.println("** Property " + k + " added");
                        try {
                            addProp((String) k, val);
                        } catch (IOException | SQLException ex) {
                            throw new IllegalArgumentException(ex);
                        }
                        return val;
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

    protected abstract Integer onEnum(Map.Entry<String, Properties> map, ArrayNode arr, ArrayNode propsArr, AtomicReference<String> en);

    protected final Integer addProp(Map.Entry<String, Properties> map, ArrayNode arr, ArrayNode propsArr, String prp) {
        String enm = Utils.toEnumLike(prp);
        enm = this.map.getOrDefault(enm, enm);
        if (enm == null) return null;
        var ar = new AtomicReference<String>(enm);
        Integer res = onEnum(map, arr, propsArr, ar);
        if (res == null) res = doAddProp(map, arr, enm);
        return res;
    }

    @Override
    public void flush() throws IOException {
        if (addedProps.isEmpty()) return;
        try (FileOutputStream fos = new FileOutputStream(ENUMS)) {
            props.getValue().store(fos, "Properties of advertisers");
            likes.getValue().store(fos, "Likes and preferences of services of advertisers");
            lookings.getValue().store(fos, "What the advertiser can accept");
            answers.getValue().store(fos, "Answers given to common questions");
            massages.getValue().store(fos, "Advertiser supported massages");
            fbgbtype.getValue().store(fos, "Feedback good/bad text");
            fbrtype.getValue().store(fos, "Feedback rating text");
            fbtype.getValue().store(fos, "Feedback text");
            fbdtype.getValue().store(fos, "Feedback details key text");
        }
        addedProps.clear();
    }

    @Override
    public void close() throws Exception {
        flush();
        if (enumCount != null) enumCount.close();
        if (enumDel != null) enumDel.close();
        if (enumAdd != null) enumAdd.close();
        TrafoEngine.super.close();
    }
}
