/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2017 Kenny Root, Jeffrey Sharkey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot.util;

import android.content.Intent;

public class AgentRequest {

	public static String AGENT_REQUEST_PENDINGINTENT_RESULT = "org.connectbot.util.AgentRequest.PENDINGINTENT_RESULT";
	public static String AGENT_REQUEST_PENDINGINTENT= "org.connectbot.util.AgentRequest.PENDINGINTENT";
	public static int AGENT_REQUEST_CODE = 0;

	public static final String REQUEST_ID = "request_id";
	public static final int REQUEST_ID_NONE = -1;

	public interface OnAgentResultCallback {
		void onAgentResult(Intent data);
	}

	private OnAgentResultCallback mAgentResultCallback;

	private String mTargetPackage;

	private Intent mRequest;

	private int mRequestId = REQUEST_ID_NONE;

	public AgentRequest(Intent request, String targetPackage) {
		mRequest = request;
		mTargetPackage = targetPackage;
	}

	public String getTargetPackage() {
		return mTargetPackage;
	}

	public void setTargetPackage(String targetPackage) {
		mTargetPackage = targetPackage;
	}

	public OnAgentResultCallback getAgentResultCallback() {
		return mAgentResultCallback;
	}

	public void setAgentResultCallback(OnAgentResultCallback agentResultCallback) {
		mAgentResultCallback = agentResultCallback;
	}

	public Intent getRequest() {
		return mRequest;
	}

	public void setRequest(Intent request) {
		this.mRequest = request;
	}

	public int getRequestId() {
		return mRequestId;
	}

	public void setRequestId(int requestId) {
		mRequestId = requestId;
		mRequest.putExtra(REQUEST_ID, requestId);
	}
}
