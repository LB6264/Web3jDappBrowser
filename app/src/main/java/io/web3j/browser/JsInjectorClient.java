package io.web3j.browser;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.annotation.RawRes;
import android.text.TextUtils;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Js injector client
 *
 * @author kuang on 2018/01/08.
 */
final class JsInjectorClient {

    private static final String DEFAULT_CHARSET = "utf-8";
    private static final String DEFAULT_MIME_TYPE = "text/html";
    /* script tag */
    private final static String JS_TAG_TEMPLATE = "<script type=\"text/javascript\">%1$s%2$s</script>";

    private final Context context;
    private final OkHttpClient httpClient;

    private String jsLibrary;

    private int chainId = 1;
    private Address walletAddress;
    private String rpcUrl = "https://mainnet.infura.io/llyrtzQ3YhkdESt2Fzrk";

    JsInjectorClient(Context context) {
        this.context = context;
        this.httpClient = createHttpClient();
    }

    private OkHttpClient createHttpClient() {
        return new OkHttpClient.Builder()
            .cookieJar(new WebViewCookieJar())
            .build();
    }

    public Address getWalletAddress() {
        return walletAddress;
    }

    public void setWalletAddress(Address address) {
        this.walletAddress = address;
    }

    public int getChainId() {
        return chainId;
    }

    public void setChainId(int chainId) {
        this.chainId = chainId;
    }

    public String getRpcUrl() {
        return rpcUrl;
    }

    public void setRpcUrl(String rpcUrl) {
        this.rpcUrl = rpcUrl;
    }

    @Nullable
    private Request buildRequest(String url, Map<String, String> headers) {
        HttpUrl httpUrl = HttpUrl.parse(url);
        if (httpUrl == null) {
            return null;
        }
        Request.Builder requestBuilder = new Request.Builder()
            .get()
            .url(httpUrl);
        Set<String> keys = headers.keySet();
        for (String key : keys) {
            requestBuilder.addHeader(key, headers.get(key));
        }
        return requestBuilder.build();
    }

    @Nullable
    private JsInjectorResponse buildResponse(Response response) {
        String body = null;
        int code = response.code();
        try {
            if (response.isSuccessful()) {
                body = response.body().string();
            }
        } catch (IOException ex) {
            Log.e("READ_BODY_ERROR", "Ex", ex);
        }
        Request request = response.request();
        Response prior = response.priorResponse();
        boolean isRedirect = prior != null && prior.isRedirect();
        String result = injectJS(body);
        String contentType = getContentTypeHeader(response);
        String charset = getCharset(contentType);
        String mime = getMimeType(contentType);
        String finalUrl = request.url().toString();
        return new JsInjectorResponse(result, code, finalUrl, mime, charset, isRedirect);
    }

    String injectJS(String html) {
        if (TextUtils.isEmpty(html)) {
            return html;
        }
        int position = getInjectionPosition(html);
        if (position > 0) {
            String beforeTag = html.substring(0, position);
            String afterTab = html.substring(position);
            String js = assembleJs(context, JS_TAG_TEMPLATE);
            return beforeTag + js + afterTab;
        }
        return html;
    }

    String assembleJs(Context context, String template) {
        if (TextUtils.isEmpty(jsLibrary)) {
            jsLibrary = loadFile(context, R.raw.js_library);
        }
        String initJs = loadInitJs(context);
        return String.format(template, jsLibrary, initJs);
    }

    private int getInjectionPosition(String body) {
        body = body.toLowerCase();
        int ieDetectTagIndex = body.indexOf("<!--[if");
        int scriptTagIndex = body.indexOf("<script");

        int index;
        if (ieDetectTagIndex < 0) {
            index = scriptTagIndex;
        } else {
            index = Math.min(scriptTagIndex, ieDetectTagIndex);
        }
        if (index < 0) {
            index = body.indexOf("</head");
        }
        return index;
    }

    private String loadInitJs(Context context) {
        String initSrc = loadFile(context, R.raw.init);
        String address = walletAddress == null ? Address.EMPTY.toString() : walletAddress.toString();
        return String.format(initSrc, address, rpcUrl, chainId);
    }

    private String loadFile(Context context, @RawRes int rawRes) {
        byte[] buffer = new byte[0];
        try {
            InputStream in = context.getResources().openRawResource(rawRes);
            buffer = new byte[in.available()];
            int len = in.read(buffer);
            if (len < 1) {
                throw new IOException("Nothing is read.");
            }
        } catch (Exception ex) {
            Log.e("READ_JS_TAG", "Ex", ex);
        }
        return new String(buffer);
    }

    private String getMimeType(String contentType) {
        Matcher regexResult = Pattern.compile("^.*(?=;)").matcher(contentType);
        if (regexResult.find()) {
            return regexResult.group();
        }
        return DEFAULT_MIME_TYPE;
    }

    private String getCharset(String contentType) {
        Matcher regexResult = Pattern.compile("charset=([a-zA-Z0-9-]+)").matcher(contentType);
        if (regexResult.find()) {
            if (regexResult.groupCount() >= 2) {
                return regexResult.group(1);
            }
        }
        return DEFAULT_CHARSET;
    }

    @Nullable
    private String getContentTypeHeader(Response response) {
        Headers headers = response.headers();
        String contentType;
        if (TextUtils.isEmpty(headers.get("Content-Type"))) {
            if (TextUtils.isEmpty(headers.get("content-Type"))) {
                contentType = "text/data; charset=utf-8";
            } else {
                contentType = headers.get("content-Type");
            }
        } else {
            contentType = headers.get("Content-Type");
        }
        if (contentType != null) {
            contentType = contentType.trim();
        }
        return contentType;
    }

    JsInjectorResponse loadUrl(final String url, String userAgent) {
        Map<String, String> headers = Collections.emptyMap();
        headers.put("User-Agent", userAgent);
        return loadUrl(url, headers);
    }

    @Nullable
    JsInjectorResponse loadUrl(final String url, final Map<String, String> headers) {
        Request request = buildRequest(url, headers);
        JsInjectorResponse result = null;
        try {
            Response response = httpClient.newCall(request).execute();
            result = buildResponse(response);
        } catch (Exception ex) {
            Log.e("REQUEST_ERROR", "", ex);
        }
        return result;
    }
}