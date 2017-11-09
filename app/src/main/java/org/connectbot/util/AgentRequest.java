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
import android.os.Handler;

public class AgentRequest {

	public static String AGENT_REQUEST_RESULT = "result";

	public static int AGENT_REQUEST_CODE = 1729;

	private Handler mAgentResultHandler;

	private String mTargetPackage;

	private Intent mRequest;

	public AgentRequest(Intent request, String targetPackage) {
		mRequest = request;
		mTargetPackage = targetPackage;
	}

	public String getTargetPackage() {
		return mTargetPackage;
	}

	public Handler getAgentResultHandler() {
		return mAgentResultHandler;
	}

	public void setAgentResultHandler(Handler agentResultHandler) {
		mAgentResultHandler = agentResultHandler;
	}

	public Intent getRequest() {
		return mRequest;
	}

	public void setRequest(Intent request) {
		this.mRequest = request;
	}
}
