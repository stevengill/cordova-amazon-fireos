/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
*/

package org.apache.cordova;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.cordova.api.CordovaInterface;
import org.apache.cordova.api.LOG;
import org.apache.cordova.api.PluginManager;
import org.xmlpull.v1.XmlPullParserException;

import android.content.Context;
import android.content.Intent;
import android.content.res.XmlResourceParser;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebSettings.LayoutAlgorithm;

public class CordovaWebView extends WebView {

    public static final String TAG = "CordovaWebView";

    /** The whitelist **/
    private ArrayList<Pattern> whiteList = new ArrayList<Pattern>();
    private HashMap<String, Boolean> whiteListCache = new HashMap<String, Boolean>();
    public PluginManager pluginManager;
    public CallbackServer callbackServer;

    /** Actvities and other important classes **/
    private CordovaInterface mCtx;
    CordovaWebViewClient viewClient;
    @SuppressWarnings("unused")
    private CordovaChromeClient chromeClient;

    //This is for the polyfil history 
    private String url;
    String baseUrl;
    private Stack<String> urls = new Stack<String>();

    boolean useBrowserHistory = false;

    // Flag to track that a loadUrl timeout occurred
    int loadUrlTimeout = 0;

    /**
     * Constructor.
     * 
     * @param context
     */
    public CordovaWebView(CordovaInterface context) {
        super(context.getActivity());
        this.mCtx = context;
        this.loadConfiguration();
        this.setup();
    }

    /**
     * Constructor.
     * 
     * @param context
     * @param attrs
     */
    public CordovaWebView(CordovaInterface context, AttributeSet attrs) {
        super(context.getActivity(), attrs);
        this.mCtx = context;
        this.loadConfiguration();
        this.setup();
    }

    /**
     * Constructor.
     * 
     * @param context
     * @param attrs
     * @param defStyle
     */
    public CordovaWebView(CordovaInterface context, AttributeSet attrs, int defStyle) {
        super(context.getActivity(), attrs, defStyle);
        this.mCtx = context;
        this.loadConfiguration();
        this.setup();
    }

    /**
     * Constructor.
     * 
     * @param context
     * @param attrs
     * @param defStyle
     * @param privateBrowsing
     */
    public CordovaWebView(CordovaInterface context, AttributeSet attrs, int defStyle, boolean privateBrowsing) {
        super(context.getActivity(), attrs, defStyle, privateBrowsing);
        this.mCtx = context;
        this.loadConfiguration();
        this.setup();
    }

    /**
     * Initialize webview.
     */
    @SuppressWarnings("deprecation")
    private void setup() {

        this.setInitialScale(0);
        this.setVerticalScrollBarEnabled(false);
        this.requestFocusFromTouch();

        // Enable JavaScript
        WebSettings settings = this.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setLayoutAlgorithm(LayoutAlgorithm.NORMAL);

        //Set the nav dump for HTC
        settings.setNavDump(true);

        // Enable database
        settings.setDatabaseEnabled(true);
        String databasePath = this.mCtx.getActivity().getApplicationContext().getDir("database", Context.MODE_PRIVATE).getPath();
        settings.setDatabasePath(databasePath);

        // Enable DOM storage
        settings.setDomStorageEnabled(true);

        // Enable built-in geolocation
        settings.setGeolocationEnabled(true);

        //Start up the plugin manager
        this.pluginManager = new PluginManager(this, this.mCtx);
    }

    /**
     * Set the WebViewClient.
     * 
     * @param client
     */
    public void setWebViewClient(CordovaWebViewClient client) {
        this.viewClient = client;
        super.setWebViewClient(client);
    }

    /**
     * Set the WebChromeClient.
     * 
     * @param client
     */
    public void setWebChromeClient(CordovaChromeClient client) {
        this.chromeClient = client;
        super.setWebChromeClient(client);
    }

    /**
     * Add entry to approved list of URLs (whitelist)
     * 
     * @param origin        URL regular expression to allow
     * @param subdomains    T=include all subdomains under origin
     */
    public void addWhiteListEntry(String origin, boolean subdomains) {
        try {
            // Unlimited access to network resources
            if (origin.compareTo("*") == 0) {
                LOG.d(TAG, "Unlimited access to network resources");
                this.whiteList.add(Pattern.compile(".*"));
            } else { // specific access
                // check if subdomains should be included
                // TODO: we should not add more domains if * has already been added
                if (subdomains) {
                    // XXX making it stupid friendly for people who forget to include protocol/SSL
                    if (origin.startsWith("http")) {
                        this.whiteList.add(Pattern.compile(origin.replaceFirst("https?://", "^https?://(.*\\.)?")));
                    } else {
                        this.whiteList.add(Pattern.compile("^https?://(.*\\.)?" + origin));
                    }
                    LOG.d(TAG, "Origin to allow with subdomains: %s", origin);
                } else {
                    // XXX making it stupid friendly for people who forget to include protocol/SSL
                    if (origin.startsWith("http")) {
                        this.whiteList.add(Pattern.compile(origin.replaceFirst("https?://", "^https?://")));
                    } else {
                        this.whiteList.add(Pattern.compile("^https?://" + origin));
                    }
                    LOG.d(TAG, "Origin to allow: %s", origin);
                }
            }
        } catch (Exception e) {
            LOG.d(TAG, "Failed to add origin %s", origin);
        }
    }

    /**
     * Determine if URL is in approved list of URLs to load.
     * 
     * @param url
     * @return
     */
    public boolean isUrlWhiteListed(String url) {

        // Check to see if we have matched url previously
        if (this.whiteListCache.get(url) != null) {
            return true;
        }

        // Look for match in white list
        Iterator<Pattern> pit = this.whiteList.iterator();
        while (pit.hasNext()) {
            Pattern p = pit.next();
            Matcher m = p.matcher(url);

            // If match found, then cache it to speed up subsequent comparisons
            if (m.find()) {
                this.whiteListCache.put(url, true);
                return true;
            }
        }
        return false;
    }

    /**
     * Load the url into the webview.
     * 
     * @param url
     */
    @Override
    public void loadUrl(String url) {
        String initUrl = this.getProperty("url", null);

        // If first page of app, then set URL to load to be the one passed in
        if (initUrl == null || (this.urls.size() > 0)) {
            this.loadUrlIntoView(url);
        }
        // Otherwise use the URL specified in the activity's extras bundle
        else {
            this.loadUrlIntoView(initUrl);
        }
    }

    /**
     * Load the url into the webview after waiting for period of time.
     * This is used to display the splashscreen for certain amount of time.
     * 
     * @param url
     * @param time              The number of ms to wait before loading webview
     */
    public void loadUrl(final String url, int time) {
        String initUrl = this.getProperty("url", null);

        // If first page of app, then set URL to load to be the one passed in
        if (initUrl == null || (this.urls.size() > 0)) {
            this.loadUrlIntoView(url, time);
        }
        // Otherwise use the URL specified in the activity's extras bundle
        else {
            this.loadUrlIntoView(initUrl);
        }
    }

    /**
     * Load the url into the webview.
     * 
     * @param url
     */
    public void loadUrlIntoView(final String url) {
        if (!url.startsWith("javascript:")) {
            LOG.d(TAG, ">>> loadUrl(" + url + ")");

            this.url = url;
            if (this.baseUrl == null) {
                int i = url.lastIndexOf('/');
                if (i > 0) {
                    this.baseUrl = url.substring(0, i + 1);
                }
                else {
                    this.baseUrl = this.url + "/";
                }

                this.pluginManager.init();

                if (!this.useBrowserHistory) {
                    this.urls.push(url);
                }
            }

            // Create a timeout timer for loadUrl
            final CordovaWebView me = this;
            final int currentLoadUrlTimeout = me.loadUrlTimeout;
            final int loadUrlTimeoutValue = Integer.parseInt(this.getProperty("loadUrlTimeoutValue", "20000"));

            // Timeout error method
            final Runnable loadError = new Runnable() {
                public void run() {
                    me.stopLoading();
                    LOG.e(TAG, "CordovaWebView: TIMEOUT ERROR!");
                    if (viewClient != null) {
                        viewClient.onReceivedError(me, -6, "The connection to the server was unsuccessful.", url);
                    }
                }
            };

            // Timeout timer method
            final Runnable timeoutCheck = new Runnable() {
                public void run() {
                    try {
                        synchronized (this) {
                            wait(loadUrlTimeoutValue);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    // If timeout, then stop loading and handle error
                    if (me.loadUrlTimeout == currentLoadUrlTimeout) {
                        me.mCtx.getActivity().runOnUiThread(loadError);
                    }
                }
            };

            // Load url
            this.mCtx.getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    Thread thread = new Thread(timeoutCheck);
                    thread.start();
                    me.loadUrlNow(url);
                }
            });
        }

        // If Javascript, then just load it now
        else {
            super.loadUrl(url);
        }
    }

    /**
     * Load URL in webview.
     * 
     * @param url
     */
    private void loadUrlNow(String url) {
        LOG.d(TAG, ">>> loadUrlNow()");
        super.loadUrl(url);
    }

    /**
     * Load the url into the webview after waiting for period of time.
     * This is used to display the splashscreen for certain amount of time.
     * 
     * @param url
     * @param time              The number of ms to wait before loading webview
     */
    public void loadUrlIntoView(final String url, final int time) {

        // If not first page of app, then load immediately
        // Add support for browser history if we use it.
        if ((url.startsWith("javascript:")) || this.urls.size() > 0 || this.canGoBack()) {
        }

        // If first page, then show splashscreen
        else {

            LOG.d(TAG, "DroidGap.loadUrl(%s, %d)", url, time);

            // Send message to show splashscreen now if desired
            this.postMessage("splashscreen", "show");
        }

        // Load url
        this.loadUrlIntoView(url);
    }

    /**
     * Send JavaScript statement back to JavaScript.
     * (This is a convenience method)
     * 
     * @param message
     */
    public void sendJavascript(String statement) {
        if (this.callbackServer != null) {
            this.callbackServer.sendJavascript(statement);
        }
    }

    /**
     * Send a message to all plugins. 
     * 
     * @param id            The message id
     * @param data          The message data
     */
    public void postMessage(String id, Object data) {
        if (this.pluginManager != null) {
            this.pluginManager.postMessage(id, data);
        }
    }

    /** 
     * Returns the top url on the stack without removing it from 
     * the stack.
     */
    public String peekAtUrlStack() {
        if (this.urls.size() > 0) {
            return this.urls.peek();
        }
        return "";
    }

    /**
     * Add a url to the stack
     * 
     * @param url
     */
    public void pushUrl(String url) {
        this.urls.push(url);
    }

    /**
     * Go to previous page in history.  (We manage our own history)
     * 
     * @return true if we went back, false if we are already at top
     */
    public boolean backHistory() {

        // Check webview first to see if there is a history
        // This is needed to support curPage#diffLink, since they are added to appView's history, but not our history url array (JQMobile behavior)
        if (super.canGoBack()) {
            super.goBack();
            return true;
        }

        // If our managed history has prev url
        if (this.urls.size() > 1) {
            this.urls.pop();                // Pop current url
            String url = this.urls.pop();   // Pop prev url that we want to load, since it will be added back by loadUrl()
            this.loadUrl(url);
            return true;
        }

        return false;
    }

    /**
     * Return true if there is a history item.
     * 
     * @return
     */
    public boolean canGoBack() {
        if (super.canGoBack()) {
            return true;
        }
        if (this.urls.size() > 1) {
            return true;
        }
        return false;
    }

    /**
     * Load the specified URL in the Cordova webview or a new browser instance.
     * 
     * NOTE: If openExternal is false, only URLs listed in whitelist can be loaded.
     *
     * @param url           The url to load.
     * @param openExternal  Load url in browser instead of Cordova webview.
     * @param clearHistory  Clear the history stack, so new page becomes top of history
     * @param params        DroidGap parameters for new app
     */
    public void showWebPage(String url, boolean openExternal, boolean clearHistory, HashMap<String, Object> params) {
        LOG.d(TAG, "showWebPage(%s, %b, %b, HashMap", url, openExternal, clearHistory);

        // If clearing history
        if (clearHistory) {
            this.clearHistory();
        }

        // If loading into our webview
        if (!openExternal) {

            // Make sure url is in whitelist
            if (url.startsWith("file://") || url.indexOf(this.baseUrl) == 0 || isUrlWhiteListed(url)) {
                // TODO: What about params?

                // Clear out current url from history, since it will be replacing it
                if (clearHistory) {
                    this.urls.clear();
                }

                // Load new URL
                this.loadUrl(url);
            }
            // Load in default viewer if not
            else {
                LOG.w(TAG, "showWebPage: Cannot load URL into webview since it is not in white list.  Loading into browser instead. (URL=" + url + ")");
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setData(Uri.parse(url));
                    mCtx.getActivity().startActivity(intent);
                } catch (android.content.ActivityNotFoundException e) {
                    LOG.e(TAG, "Error loading url " + url, e);
                }
            }
        }

        // Load in default view intent
        else {
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(url));
                mCtx.getActivity().startActivity(intent);
            } catch (android.content.ActivityNotFoundException e) {
                LOG.e(TAG, "Error loading url " + url, e);
            }
        }
    }

    /**
     * Load Cordova configuration from res/xml/cordova.xml.
     * Approved list of URLs that can be loaded into DroidGap
     *      <access origin="http://server regexp" subdomains="true" />
     * Log level: ERROR, WARN, INFO, DEBUG, VERBOSE (default=ERROR)
     *      <log level="DEBUG" />
     */
    private void loadConfiguration() {
        int id = getResources().getIdentifier("cordova", "xml", this.mCtx.getActivity().getPackageName());
        if (id == 0) {
            LOG.i("CordovaLog", "cordova.xml missing. Ignoring...");
            return;
        }
        XmlResourceParser xml = getResources().getXml(id);
        int eventType = -1;
        while (eventType != XmlResourceParser.END_DOCUMENT) {
            if (eventType == XmlResourceParser.START_TAG) {
                String strNode = xml.getName();
                if (strNode.equals("access")) {
                    String origin = xml.getAttributeValue(null, "origin");
                    String subdomains = xml.getAttributeValue(null, "subdomains");
                    if (origin != null) {
                        this.addWhiteListEntry(origin, (subdomains != null) && (subdomains.compareToIgnoreCase("true") == 0));
                    }
                }
                else if (strNode.equals("log")) {
                    String level = xml.getAttributeValue(null, "level");
                    LOG.i("CordovaLog", "Found log level %s", level);
                    if (level != null) {
                        LOG.setLogLevel(level);
                    }
                }
                else if (strNode.equals("preference")) {
                    String name = xml.getAttributeValue(null, "name");
                    String value = xml.getAttributeValue(null, "value");

                    LOG.i("CordovaLog", "Found preference for %s=%s", name, value);

                    // Save preferences in Intent
                    this.mCtx.getActivity().getIntent().putExtra(name, value);
                }
            }
            try {
                eventType = xml.next();
            } catch (XmlPullParserException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Init preferences
        if ("true".equals(this.getProperty("useBrowserHistory", "true"))) {
            this.useBrowserHistory = true;
        }
        else {
            this.useBrowserHistory = false;
        }
    }

    /**
     * Get string property for activity.
     * 
     * @param name
     * @param defaultValue
     * @return
     */
    public String getProperty(String name, String defaultValue) {
        Bundle bundle = this.mCtx.getActivity().getIntent().getExtras();
        if (bundle == null) {
            return defaultValue;
        }
        Object p = bundle.get(name);
        if (p == null) {
            return defaultValue;
        }
        return p.toString();
    }
}
