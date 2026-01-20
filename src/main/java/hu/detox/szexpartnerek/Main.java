package hu.detox.szexpartnerek;

import com.fasterxml.jackson.databind.JsonNode;
import hu.detox.szexpartnerek.rl.User;
import okhttp3.RequestBody;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.Flushable;
import java.io.IOException;
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

    public void transformAll(boolean recurse, Serde serde, TrafoEngine engine, String... urls) throws IOException, SQLException {
        Iterator<String> pager = engine.pager();
        Persister p = engine.persister();
        try {
            boolean first = true, cont;
            for (String url : urls) {
                if (url == null) continue;
                cont = true;
                while (cont) {
                    JsonNode bodyNode = null;
                    if (serde.inMode() == null || serde.inMode().equals(Serde.Mode.TXT)) {
                        String curl = url;
                        if (pager != null) {
                            if (!pager.hasNext()) break;
                            curl += "&" + pager.next();
                        }
                        RequestBody body = null;
                        if (pager instanceof Pager pg) {
                            body = pg.req();
                        }
                        var resp = engine.post() ? rl.post(curl, body) : rl.get(curl);
                        System.err.println("Current is " + curl);
                        bodyNode = serde.serialize(resp, curl, engine);
                        if (bodyNode != null && pager instanceof Pager pg) {
                            if (first) {
                                pg.first(bodyNode);
                                first = false;
                            }
                            cont = pg.current(bodyNode) >= 0;
                        }
                    } else if (serde.inMode().equals(Serde.Mode.JSONL)) {
                        bodyNode = Serde.OM.readTree(url);
                    }
                    if (bodyNode == null) {
                        break;
                    }
                    save(recurse, engine, bodyNode);
                    if (pager == null) break;
                }
            }
        } finally {
            serde.flush();
            if (p instanceof Flushable flp) flp.flush();
            if (engine instanceof Flushable fl) {
                fl.flush();
            }
        }
    }

    private void save(boolean recurse, TrafoEngine engine, JsonNode bodyNode) throws IOException, SQLException {
        TrafoEngine[] tes;
        if (recurse) {
            tes = engine.preTrafos();
            if (tes != null) for (TrafoEngine ste : tes) {
                rlDataDl(false, ste, bodyNode);
            }
        }
        Persister p = engine.persister();
        if (p != null) p.save(bodyNode);
        if (recurse) {
            tes = engine.subTrafos();
            if (tes != null) for (TrafoEngine ste : tes) {
                rlDataDl(recurse, ste, bodyNode);
            }
        }
    }

    @Override
    public Integer call() throws Exception {
        //rlDataDl(true, Feedbacks.INSTANCE, null);
        rlDataDl(true, User.INSTANCE, null);
        //rlDataDl(true, New.INSTANCE, null);
        //rlDataDl(true, Lista.INSTANCE, null);
        return 0;
    }

    public void rlDataDl(boolean recurse, TrafoEngine engine, JsonNode parent) throws IOException, SQLException {
        boolean cont = true;
        String[] arr = new String[10];
        String ln;
        File out = engine.out();
        Serde serde = null;
        try {
            File in = null;
            if (parent == null) for (String fp : engine.in()) {
                File f = new File(fp);
                if (f.isFile()) {
                    in = f;
                    break;
                }
            }
            Iterator<?> ini = parent == null ? null : engine.input(parent);
            serde = new Serde(out, in);
            while (cont) {
                Arrays.fill(arr, null);
                if (in == null && ini == null) {
                    arr[0] = engine.url().apply(null);
                    cont = false;
                } else {
                    for (int i = 0; i < 10; i++) {
                        ln = null;
                        if (ini != null) {
                            if (ini.hasNext()) {
                                Object o = ini.next();
                                ln = o instanceof JsonNode jn ? jn.asText() : o.toString();
                            }
                        } else {
                            ln = serde.nextStr();
                        }
                        if (ln == null) {
                            cont = false;
                            break;
                        }
                        arr[i] = engine.url().apply(ln);
                    }
                }
                transformAll(recurse, serde, engine, arr);
            }
        } finally {
            if (serde != null) serde.close();
            if (engine instanceof Flushable fle) fle.flush();
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
