package hu.detox.szexpartnerek;

import com.fasterxml.jackson.databind.JsonNode;
import hu.detox.szexpartnerek.rl.Advertiser;
import hu.detox.szexpartnerek.rl.AdvertiserPersister;
import hu.detox.szexpartnerek.rl.Lista;
import hu.detox.szexpartnerek.rl.ListaPersister;
import okhttp3.Response;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.URL;
import java.util.Arrays;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.function.Function;

public class Main implements Callable<Integer> {
    private static final Main APP = new Main();
    private Http rl = new Http("https://rosszlanyok.hu/");
    private Db db = new Db(new File("target/data/db.sqlite3"));

    public static void main(String[] args) throws Exception {
        Integer result = APP.call();
        System.out.println(result == null ? 0 : result);
    }

    private void test(Function<String, ?> trafo) throws IOException {
        URL u = this.getClass().getResource("/list.html");
        var res = trafo.apply(IOUtils.toString(u.openStream()));
        System.out.println(Serde.OM.valueToTree(res));
    }

    public void trafoAll(PrintStream ps, TrafoEngine trafo, String... urls) throws IOException {
        String typ = null;
        int page = trafo.page();
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
                    if (!serde.serialize(resp, trafo) || page == 0) {
                        break;
                    }
                    cpage++;
                }
            }
        } finally {
            serde.flush();
            if (trafo instanceof Flushable fl) {
                fl.flush();
            }
        }
    }

    @Override
    public Integer call() throws Exception {
        var lista = new Lista();
        Advertiser adv = new Advertiser();
        //lista.combineAll();
        //rlDataDl(adv);
        adv.close();
        var r = new FileReader("target/listak.txt.json");
        try (var conn = db.getConnection();
             ListaPersister ap = new ListaPersister(conn)) {
            JsonNode node = Serde.OM.readTree(r);
            ap.save(node);
        }
        r = new FileReader("target/partnerek.txt");
        try (var conn = db.getConnection();
             AdvertiserPersister ap = new AdvertiserPersister(conn);
             Serde serde = new Serde(null, new BufferedReader(r))) {
            Response resp;
            Properties props = new Properties();
            props.load(new BufferedReader(new FileReader("src/main/resources/enums.properties")));
            ap.enumLoad(props);
            while ((resp = serde.next()) != null) {
                JsonNode n = Serde.OM.readTree(resp.body().string());
                ap.save(n);
            }
        }
        return 0;
    }

    private void refreshAll() throws IOException {
        rlDataDl(new Advertiser());
        Lista lista = new Lista();
        rlDataDl(lista);
        lista.combineAll();
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

    public void rlDataDl(TrafoEngine any) throws IOException {
        boolean cont = true;
        String[] arr = new String[10];
        String ln;
        PrintStream ps = new PrintStream(new FileOutputStream(any.out()));
        try (BufferedReader br = new BufferedReader(new FileReader(any.in()))) {
            while (cont) {
                Arrays.fill(arr, null);
                for (int i = 0; i < 10; i++) {
                    ln = br.readLine();
                    if (ln == null) {
                        cont = false;
                        break;
                    }
                    arr[i] = any.url().apply(ln);
                }
                trafoAll(ps, any, arr);
            }
        } finally {
            ps.close();
        }
    }
}
