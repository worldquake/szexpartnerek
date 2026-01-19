package hu.detox.szexpartnerek;

import com.fasterxml.jackson.databind.JsonNode;
import hu.detox.szexpartnerek.rl.Advertiser;
import hu.detox.szexpartnerek.rl.Lista;
import hu.detox.szexpartnerek.rl.New;
import okhttp3.Response;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.URL;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;

public class Main implements Callable<Integer>, AutoCloseable {
    public static final Main APP = new Main();
    private Http rl = new Http("https://rosszlanyok.hu/");
    private static Db DB = new Db(new File("target/data/db.sqlite3"));
    private transient Set<AutoCloseable> closeables = new HashSet<>();
    private transient Connection connection;

    public static void main(String[] args) throws Exception {
        try {
            APP.connection = DB.getConnection();
            Integer result = APP.call();
            System.out.println(result == null ? 0 : result);
        } finally {
            APP.close();
        }
    }

    public Connection getConn() {
        return connection;
    }

    private void test(Function<String, ?> trafo) throws IOException {
        URL u = this.getClass().getResource("/list.html");
        var res = trafo.apply(IOUtils.toString(u.openStream()));
        System.out.println(Serde.OM.valueToTree(res));
    }

    public void trafoAll(PrintStream ps, TrafoEngine engine, String... urls) throws IOException, SQLException {
        String typ = null;
        int page = engine.page();
        int cpage;
        if (page > 0) {
            if (page == 1) typ = "page=";
            else typ = "offset=";
        }
        Serde serde = new Serde(ps, null);
        try {
            for (String url : urls) {
                if (url == null) continue;
                cpage = 0;
                while (true) {
                    String curl = url + (typ == null ? "" : "&" + typ + cpage);
                    var resp = rl.get(curl);
                    System.err.println("Current is " + curl);
                    JsonNode bodyNode = serde.serialize(resp, engine);
                    if (bodyNode == null) {
                        break;
                    }
                    Persister p = engine.persister();
                    if (p != null) p.save(bodyNode);
                    TrafoEngine[] tes = engine.subTrafos();
                    if (tes != null) for (TrafoEngine ste : tes) {
                        rlDataDl(ste, bodyNode);
                    }
                    if (page == 0) break;
                    cpage++;
                }
            }
        } finally {
            serde.flush();
            if (engine instanceof Flushable fl) {
                fl.flush();
            }
        }
    }

    @Override
    public Integer call() throws Exception {
        rlDataDl(New.INSTANCE, null);
        return 0;
    }

    private void refreshFromFile() throws SQLException, IOException {
        rlDataDl(Advertiser.INSTANCE, null);
        rlDataDl(Lista.INSTANCE, null);
    }

    public void load() throws Exception {
        var r = new FileReader("target/results.txt");
        Serde serde = new Serde(null, new BufferedReader(r));
        Response resp;
        while ((resp = serde.next()) != null) {
            System.err.println(resp.headers());
        }
        serde.close();
    }

    public void rlDataDl(TrafoEngine engine, JsonNode parent) throws IOException, SQLException {
        boolean cont = true;
        String[] arr = new String[10];
        String ln;
        PrintStream ps = new PrintStream(new FileOutputStream(engine.out(), true));
        BufferedReader br = null;
        try {
            File in = engine.in();
            Iterator<?> ini = parent == null ? null : engine.input(parent);
            br = parent != null || in == null ? null : new BufferedReader(new FileReader(engine.in()));
            while (cont) {
                Arrays.fill(arr, null);
                if (br == null && ini == null) {
                    arr[0] = "";
                    cont = false;
                } else {
                    for (int i = 0; i < 10; i++) {
                        ln = null;
                        if (br != null) ln = br.readLine();
                        else if (ini.hasNext()) {
                            Object o = ini.next();
                            ln = o instanceof JsonNode jn ? jn.asText() : o.toString();
                        }
                        if (ln == null) {
                            cont = false;
                            break;
                        }
                        arr[i] = engine.url().apply(ln);
                    }
                }
                trafoAll(ps, engine, arr);
            }
        } finally {
            if (br != null) br.close();
            ps.close();
            closeables.add(engine);
        }
    }

    @Override
    public void close() throws Exception {
        for (AutoCloseable c : closeables) {
            c.close();
        }
        if (connection != null) {
            connection.close();
        }
    }
}
