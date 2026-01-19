package hu.detox.szexpartnerek;

import com.fasterxml.jackson.databind.JsonNode;
import hu.detox.szexpartnerek.rl.Lista;
import hu.detox.szexpartnerek.rl.New;
import hu.detox.szexpartnerek.rl.User;
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
    public static final String JSONL = ".jsonl";
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

    public void transformAll(Serde serde, TrafoEngine engine, boolean inJsonl, String... urls) throws IOException, SQLException {
        String typ = null;
        int page = inJsonl ? 0 : engine.page();
        int cpage;
        Persister p = engine.persister();
        if (page > 0) {
            if (page == 1) typ = "page=";
            else typ = "offset=";
        }
        try {
            for (String url : urls) {
                if (url == null) continue;
                cpage = 0;
                while (true) {
                    JsonNode bodyNode;
                    if (inJsonl) {
                        bodyNode = Serde.OM.readTree(url);
                    } else {
                        String curl = url + (typ == null ? "" : "&" + typ + cpage);
                        var resp = rl.get(curl);
                        System.err.println("Current is " + curl);
                        bodyNode = serde.serialize(resp, engine);
                    }
                    if (bodyNode == null) {
                        break;
                    }
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
        rlDataDl(User.INSTANCE, null);
        rlDataDl(New.INSTANCE, null);
        rlDataDl(Lista.INSTANCE, null);
        return 0;
    }

    public void rlDataDl(TrafoEngine engine, JsonNode parent) throws IOException, SQLException {
        boolean cont = true;
        String[] arr = new String[10];
        String ln;
        File outf = engine.out();
        boolean outJsonl = outf.getName().endsWith(Main.JSONL);
        PrintStream ps = new PrintStream(new FileOutputStream(outf));
        BufferedReader br = null;
        boolean inJsonl = false;
        try {
            File in = null;
            for (String fp : engine.in()) {
                File f = new File(fp);
                if (f.isFile()) {
                    in = f;
                    inJsonl = f.getName().endsWith(Main.JSONL);
                    break;
                }
            }
            Iterator<?> ini = parent == null ? null : engine.input(parent);
            br = parent != null || in == null ? null : new BufferedReader(new FileReader(in));
            Serde serde = new Serde(outJsonl, ps, null);
            while (cont) {
                Arrays.fill(arr, null);
                if (br == null && ini == null) {
                    arr[0] = engine.url().apply(null);
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
                transformAll(serde, engine, inJsonl, arr);
            }
        } finally {
            if (br != null) br.close();
            if (engine instanceof Flushable fle) fle.flush();
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
