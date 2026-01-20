package hu.detox.szexpartnerek.rl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hu.detox.szexpartnerek.Pager;
import hu.detox.szexpartnerek.TrafoEngine;
import hu.detox.szexpartnerek.Utils;
import okhttp3.RequestBody;
import org.apache.commons.io.FileUtils;
import org.jsoup.internal.StringUtil;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Iterator;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;

public class Feedbacks extends UserReview {
    public static final Feedbacks INSTANCE = new Feedbacks();
    private static final TrafoEngine[] PRE = new TrafoEngine[]{Partner.INSTANCE, User.INSTANCE};
    private List<String> datas;
    private Timestamp last;

    public Feedbacks() {
        super();
        try {
            last = persister().maxTs();
            datas = FileUtils.readLines(new File("src/main/resources/search-reviews-data.txt"));
        } catch (IOException | SQLException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Override
    public Function<String, String> url() {
        return rest -> {
            String lst = "4layer/beszamolo_list.php?tagcategories=&folder_postfix=";
            if (StringUtil.isBlank(rest)) return lst;
            else if (rest.matches("[a-z]+")) lst += "&status=" + rest;
            else if (StringUtil.isNumeric(rest)) lst += "&offset=" + rest;
            return lst;
        };
    }

    @Override
    public Iterator<?> input(JsonNode parent) {
        return null;
    }

    public TrafoEngine[] preTrafos() {
        return PRE;
    }

    @Override
    public String[] in() {
        return new String[]{
                "src/main/resources/search-reviews.txt"
        };
    }

    protected String[] selectors() {
        return new String[]{
                "div.beszOuter", // In this view this is the list item
                ".hiddenNev" // This is the holder of the partner info
        };
    }

    @Override
    public File out() {
        return new File("target/gen-search-reviews.jsonl");
    }

    @Override
    protected int pageSize() {
        return 15;
    }

    @Override
    protected Element partnerId(ObjectNode map, Element curr) {
        Element partner = curr.selectFirst("#placeholder");
        if (partner != null) {
            String id = partner.attr("data-memberthumb");
            if (id != null) map.put(Partner.IDR, Integer.parseInt(id));
        }
        return null;
    }

    @Override
    protected Integer userId(Document soup, Element curr, String ts) {
        String uid = curr.selectFirst(".beszHeader a").attr("href");
        Matcher m = Partner.IDP.matcher(uid);
        Timestamp now = Timestamp.valueOf(ts + ":00");
        if (now.before(last)) return null;
        return m.find() ? Integer.parseInt(m.group(2)) : null;
    }

    @Override
    public Pager pager() {
        Pager p = super.pager();
        return new Pager.PagerWrap(p) {
            private int di;

            @Override
            public boolean hasNext() {
                boolean has = super.hasNext();
                if (!has) {
                    reset();
                    di++;
                }
                return di < datas.size();
            }

            @Override
            public int current(JsonNode node) {
                if (node.size() == 1) return -1; // We skipped items, only pager is there
                return super.current(node);
            }

            @Override
            public RequestBody req() {
                return Utils.bodyOf(datas.get(di));
            }
        };
    }
}
