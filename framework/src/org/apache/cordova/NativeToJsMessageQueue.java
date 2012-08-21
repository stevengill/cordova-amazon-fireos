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

import java.util.LinkedList;

import android.util.Log;

/**
 * Holds the list of messages to be sent to the WebView.
 */
public class NativeToJsMessageQueue {
    private static final String LOG_TAG = "JsMessageQueue";

    // This must match the default value in incubator-cordova-js/lib/android/exec.js
    private static final int DEFAULT_BRIDGE_MODE = 1;
    
    /**
     * The list of JavaScript statements to be sent to JavaScript.
     */
    private LinkedList<String> queue = new LinkedList<String>();

    /**
     * The index into registeredListeners to treat as active. 
     */
    private int activeListenerIndex;
    
    /**
     * The array of listeners that can be used to send messages to JS.
     */
    private BridgeMode[] registeredListeners;    
        
    public NativeToJsMessageQueue(CordovaWebView webView) {
    	registeredListeners = new BridgeMode[3];
    	registeredListeners[0] = null;
    	registeredListeners[1] = new CallbackBridgeMode(webView);
    	registeredListeners[2] = new LoadUrlBridgeMode(webView);
    	reset();
//        POLLING: 0,
//        HANGING_GET: 1,
//        LOAD_URL: 2,
//        ONLINE_EVENT: 3,
//        PRIVATE_API: 4
    }
    
    /**
     * Changes the bridge mode.
     */
    public void setBridgeMode(int value) {
    	if (value < 0 || value >= registeredListeners.length) {
    		Log.d(LOG_TAG, "Invalid NativeToJsBridgeMode: " + value);
    	} else {
    		if (value != activeListenerIndex) {
    			Log.d(LOG_TAG, "Set native->JS mode to " + value);
    			synchronized (this) {
    			    activeListenerIndex = value;
    			    BridgeMode activeListener = registeredListeners[value];
    			    if (!queue.isEmpty() && activeListener != null) {
    			    	activeListener.onNativeToJsMessageAvailable(this);
    			    }
    			}
    		}
    	}
    }
    
	/**
	 * Clears all messages and resets to the default bridge mode.
     */
    public void reset() {
    	synchronized (this) {
    		queue.clear();
    		setBridgeMode(DEFAULT_BRIDGE_MODE);
    	}
    }

    /**
     * Removes and returns the last statement in the queue.
     * Returns null if the queue is empty.
     */
    public String pop() {
        synchronized (this) {
            if (queue.isEmpty()) {
                return null;
            }
            return queue.remove(0);
        }
    }

    /**
     * Combines and returns all statements. Clears the queue.
     * Returns null if the queue is empty.
     */
    public String popAll() {
        synchronized (this) {
            int length = queue.size();
            if (length == 0) {
                return null;
            }
            StringBuffer sb = new StringBuffer();
            // Wrap each statement in a try/finally so that if one throws it does 
            // not affect the next.
            int i = 0;
			for (String message : queue) {
            	if (++i == length) {
            		sb.append(message);
            	} else {
	            	sb.append("try{")
	            	  .append(message)
	            	  .append("}finally{");
            	}
            }
            for ( i = 1; i < length; ++i) {
            	sb.append('}');
            }
            queue.clear();
            return sb.toString();
        }
    }    

    /**
     * Add a JavaScript statement to the list.
     */
    public void add(String statement) {
        synchronized (this) {
            queue.add(statement);
            if (registeredListeners[activeListenerIndex] != null) {
            	registeredListeners[activeListenerIndex].onNativeToJsMessageAvailable(this);
            }
        }
    }

    private interface BridgeMode {
		void onNativeToJsMessageAvailable(NativeToJsMessageQueue queue);
	}
	
    /** Uses a local server to send messages to JS via an XHR */
    private static class CallbackBridgeMode implements BridgeMode {
    	private CordovaWebView webView;
		public CallbackBridgeMode(CordovaWebView webView) {
    		this.webView = webView;
    	}
    	public void onNativeToJsMessageAvailable(NativeToJsMessageQueue queue) {
    		if (webView.callbackServer != null) {
    			webView.callbackServer.onNativeToJsMessageAvailable(queue);
    		}
        }
    }
    
    /** Uses webView.loadUrl("javascript:") to execute messages. */
    public static class LoadUrlBridgeMode implements BridgeMode {
		private CordovaWebView webView;
		public LoadUrlBridgeMode(CordovaWebView webView) {
    		this.webView = webView;
    	}
    	public void onNativeToJsMessageAvailable(NativeToJsMessageQueue queue) {
    		webView.loadUrlNow("javascript:" + queue.popAll());
        }
    }

}