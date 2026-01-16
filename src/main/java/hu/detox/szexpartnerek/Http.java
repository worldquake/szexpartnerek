package hu.detox.szexpartnerek;

import okhttp3.*;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.util.concurrent.TimeUnit;

public class Http {
    private final OkHttpClient client;
    private final String base;
    private String prev;

    Http(String b) {
        prev = base = b;
        CookieManager cookieManager = new CookieManager();
        cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);

        client = new OkHttpClient.Builder()
                .cookieJar(new JavaNetCookieJar(cookieManager))
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addInterceptor(new RedirectLimitInterceptor(2))
                .addInterceptor(new GzipInterceptor())
                .build();
    }

    private Request.Builder base(String rest) {
        var nu = base + rest;
        Request.Builder result = new Request.Builder()
                .url(nu)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:147.0) Gecko/20100101 Firefox/147.0")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "hu,en;q=0.9")
                .header("Connection", "keep-alive")
                .header("Referer", prev);
        prev = nu;
        return result;
    }

    public Response get(String url) throws IOException {
        Request.Builder requestBuilder = base(url).get();
        return client.newCall(requestBuilder.build()).execute();
    }

    static class RedirectLimitInterceptor implements Interceptor {
        private final int maxRedirects;

        RedirectLimitInterceptor(int maxRedirects) {
            this.maxRedirects = maxRedirects;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            Response response = chain.proceed(request);
            int redirectCount = 0;
            while (response.isRedirect() && redirectCount < maxRedirects) {
                String location = response.header("Location");
                if (location == null) break;
                Request newRequest = request.newBuilder().url(location).build();
                response.close();
                response = chain.proceed(newRequest);
                redirectCount++;
            }
            if (response.isRedirect()) {
                throw new IOException("Too many redirects: " + (redirectCount + 1));
            }
            return response;
        }
    }

}
