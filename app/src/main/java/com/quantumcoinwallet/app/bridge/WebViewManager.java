package com.quantumcoinwallet.app.bridge;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.webkit.WebViewAssetLoader;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import timber.log.Timber;

public class WebViewManager
{
    private static final String TAG = "WebViewManager";
    private static final String BRIDGE_URL = "https://appassets.androidplatform.net/assets/bridge.html";
    private static final String JS_INTERFACE_NAME = "AndroidBridge";

    private static volatile WebViewManager instance;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ConcurrentHashMap<String, BridgeCallback> pendingCallbacks = new ConcurrentHashMap<>();
    private final AtomicLong requestIdCounter = new AtomicLong(0);
    private final AtomicBoolean ready = new AtomicBoolean(false);
    private final CountDownLatch readyLatch = new CountDownLatch(1);

    private WebView webView;
    private Context appContext;

    private WebViewManager()
    {
    }

    public static WebViewManager getInstance(@NonNull Context context)
    {
        if (instance == null)
        {
            synchronized (WebViewManager.class)
            {
                if (instance == null)
                {
                    instance = new WebViewManager();
                    instance.initialize(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initialize(@NonNull Context context)
    {
        this.appContext = context;

        if (Looper.myLooper() == Looper.getMainLooper())
        {
            createWebView();
        }
        else
        {
            mainHandler.post(this::createWebView);
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void createWebView()
    {
        webView = new WebView(appContext);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccessFromFileURLs(true);

        final WebViewAssetLoader assetLoader = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(appContext))
                .build();

        webView.setWebViewClient(new WebViewClient()
        {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request)
            {
                return assetLoader.shouldInterceptRequest(request.getUrl());
            }

            @Override
            public void onPageFinished(WebView view, String url)
            {
                super.onPageFinished(view, url);
                if (BRIDGE_URL.equals(url))
                {
                    ready.set(true);
                    readyLatch.countDown();
                    Timber.tag(TAG).d("Bridge WebView ready");
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient()
        {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm)
            {
                String level;
                switch (cm.messageLevel())
                {
                    case ERROR:
                        level = "ERROR";
                        Log.e(TAG, "JS " + level + ": " + cm.message()
                                + " [" + cm.sourceId() + ":" + cm.lineNumber() + "]");
                        break;
                    case WARNING:
                        level = "WARN";
                        Log.w(TAG, "JS " + level + ": " + cm.message()
                                + " [" + cm.sourceId() + ":" + cm.lineNumber() + "]");
                        break;
                    default:
                        level = "LOG";
                        Log.d(TAG, "JS " + level + ": " + cm.message()
                                + " [" + cm.sourceId() + ":" + cm.lineNumber() + "]");
                        break;
                }
                return true;
            }
        });

        webView.addJavascriptInterface(this, JS_INTERFACE_NAME);
        webView.loadUrl(BRIDGE_URL);
    }

    public boolean isReady()
    {
        return ready.get();
    }

    public boolean waitUntilReady(long timeoutSeconds)
    {
        if (ready.get()) return true;
        try
        {
            return readyLatch.await(timeoutSeconds, TimeUnit.SECONDS);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public void evaluateJavascript(@NonNull String script, @Nullable ValueCallback<String> callback)
    {
        if (Looper.myLooper() == Looper.getMainLooper())
        {
            if (webView != null)
            {
                webView.evaluateJavascript(script, callback);
            }
        }
        else
        {
            mainHandler.post(() ->
            {
                if (webView != null)
                {
                    webView.evaluateJavascript(script, callback);
                }
            });
        }
    }

    public String callBridge(@NonNull String functionCall)
    {
        String requestId = generateRequestId();
        String script = "bridge." + functionCall.replace("(", "('" + requestId + "',");
        evaluateJavascript(script, null);
        return requestId;
    }

    public void registerCallback(@NonNull String requestId, @NonNull BridgeCallback callback)
    {
        pendingCallbacks.put(requestId, callback);
    }

    @JavascriptInterface
    public void onResult(String requestId, String jsonResult)
    {
        Timber.tag(TAG).d("onResult requestId=%s", requestId);
        BridgeCallback callback = pendingCallbacks.remove(requestId);
        if (callback != null)
        {
            try
            {
                org.json.JSONObject json = new org.json.JSONObject(jsonResult);
                if (json.optBoolean("success", false))
                {
                    callback.onResult(jsonResult);
                }
                else
                {
                    String error = json.optString("error", "Unknown bridge error");
                    callback.onError(error);
                }
            }
            catch (org.json.JSONException e)
            {
                callback.onResult(jsonResult);
            }
        }
    }

    @JavascriptInterface
    public void onError(String requestId, String error)
    {
        Timber.tag(TAG).e("onError requestId=%s error=%s", requestId, error);
        BridgeCallback callback = pendingCallbacks.remove(requestId);
        if (callback != null)
        {
            callback.onError(error);
        }
    }

    private String generateRequestId()
    {
        return "req_" + requestIdCounter.incrementAndGet();
    }

    public void destroy()
    {
        ready.set(false);
        pendingCallbacks.clear();

        mainHandler.post(() ->
        {
            if (webView != null)
            {
                webView.removeJavascriptInterface(JS_INTERFACE_NAME);
                webView.stopLoading();
                webView.destroy();
                webView = null;
            }
        });

        synchronized (WebViewManager.class)
        {
            instance = null;
        }
    }
}
