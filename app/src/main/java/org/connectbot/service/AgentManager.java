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

package org.connectbot.service;

import java.util.HashMap;

import org.connectbot.util.AgentRequest;
import org.openintents.ssh.SshAgentApi;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;

public class AgentManager extends Service {

	private Handler mActivityHandler;

	private HashMap<Integer, AgentRequest> mAgentRequests = new HashMap<>();

	public class AgentBinder extends Binder {
		public AgentManager getService() {
			return AgentManager.this;
		}
	}
	private final IBinder mAgentBinder = new AgentBinder();

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return mAgentBinder;
	}

	public void setActivityHandler(Handler activityHandler) {
		mActivityHandler = activityHandler;
	}

	public void execute(final AgentRequest agentRequest) {
		register(agentRequest);

		AgentManagerTask agentManagerTask = new AgentManagerTask(getApplicationContext(), mActivityHandler);
		agentManagerTask.execute(agentRequest);

	}

	private void register(AgentRequest agentRequest) {
		int requestId = agentRequest.getRequestId();
		if (requestId == AgentRequest.REQUEST_ID_NONE) {
			// new AgentRequest, assign id
			// TODO: something else as key maybe?
			requestId = agentRequest.hashCode();
			agentRequest.setRequestId(requestId);
		}
		mAgentRequests.put(requestId, agentRequest);
	}

	public void processPendingIntentResult(Intent result) {
		int requestId = 0;
		if (result != null) {
			requestId = result.getIntExtra(AgentRequest.REQUEST_ID, AgentRequest.REQUEST_ID_NONE);
		}
		AgentRequest agentRequest = mAgentRequests.get(requestId);

		if (result != null) {
			agentRequest.setRequest(result);

			// execute received Intent again for result
			execute(agentRequest);
		} else {
			agentRequest.getAgentResultHandler().sendEmptyMessage(SshAgentApi.RESULT_CODE_ERROR);
			mAgentRequests.remove(requestId);
		}
	}
}

