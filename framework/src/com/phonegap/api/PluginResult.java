/*
 * PhoneGap is available under *either* the terms of the modified BSD license *or* the
 * MIT License (2008). See http://opensource.org/licenses/alphabetical for full text.
 * 
 * Copyright (c) 2005-2010, Nitobi Software Inc.
 * Copyright (c) 2010, IBM Corporation
 */
package com.phonegap.api;

import org.json.JSONArray;
import org.json.JSONObject;

public class PluginResult {
	private final int status;
	private final String message;
	
	public PluginResult(Status status) {
		this.status = status.ordinal();
		this.message = "'" + PluginResult.StatusMessages[this.status] + "'";
	}
	
	public PluginResult(Status status, String message) {
		this.status = status.ordinal();
		this.message = "'" + message + "'";
	}

	public PluginResult(Status status, JSONArray message) {
		this.status = status.ordinal();
		this.message = message.toString();
	}

	public PluginResult(Status status, JSONObject message) {
		this.status = status.ordinal();
		this.message = message.toString();
	}

	public PluginResult(Status status, int i) {
		this.status = status.ordinal();
		this.message = ""+i;
	}

	public PluginResult(Status status, float f) {
		this.status = status.ordinal();
		this.message = ""+f;
	}

	public PluginResult(Status status, boolean b) {
		this.status = status.ordinal();
		this.message = ""+b;
	}
	
	public int getStatus() {
		return status;
	}

	public String getMessage() {
		return message;
	}
		
	public String getJSONString() {
		return "{ status: " + this.getStatus() + ", message: " + this.getMessage() + " }";
	}
	
	public String toSuccessCallbackString(String callbackId) {
		return "PhoneGap.callbackSuccess('"+callbackId+"', " + this.getJSONString() + " );";
	}
	
	public String toErrorCallbackString(String callbackId) {
		return "PhoneGap.callbackError('"+callbackId+"', " + this.getJSONString()+ ");";
	}
	
	public static String[] StatusMessages = new String[] {
		"OK",
		"Class not found",
		"Illegal access",
		"Instantiation error",
		"Malformed url",
		"IO error",
		"Invalid action",
		"JSON error",
		"Error",
		"Result to be sent later",
		"Next result",
		"No more results"
	};
	
	public enum Status {
		OK,
		CLASS_NOT_FOUND_EXCEPTION,
		ILLEGAL_ACCESS_EXCEPTION,
		INSTANTIATION_EXCEPTION,
		MALFORMED_URL_EXCEPTION,
		IO_EXCEPTION,
		INVALID_ACTION,
		JSON_EXCEPTION,
		ERROR,
		RESULT_TO_BE_SENT,
		NEXT_RESULT,
		NO_MORE_RESULTS
	}
}
