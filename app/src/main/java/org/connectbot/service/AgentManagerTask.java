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

import org.connectbot.util.AgentRequest;
import org.openintents.ssh.authentication.ISshAuthenticationService;
import org.openintents.ssh.authentication.SshAuthenticationApi;
import org.openintents.ssh.authentication.SshAuthenticationConnection;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class AgentManagerTask extends AsyncTask<AgentRequest, Void, Void> {

	private Handler mAgentManagerHandler;
	private Context mAppContext;

	private AgentRequest mAgentRequest;

	public AgentManagerTask(Context appContext, Handler agentManagerHandler) {
		mAppContext = appContext;
		mAgentManagerHandler = agentManagerHandler;
	}

	public void execute(final AgentRequest agentRequest) {
		mAgentRequest = agentRequest;
		final SshAuthenticationConnection agentConnection = new SshAuthenticationConnection(mAppContext, agentRequest.getTargetPackage());

		agentConnection.connect(new SshAuthenticationConnection.OnBound() {
			@Override
			public void onBound(ISshAuthenticationService sshAgent) {
				executeInternal(sshAgent);
				agentConnection.disconnect();
			}

			@Override
			public void onError() {
			}
		});
	}

	private void executeInternal(ISshAuthenticationService sshAgent) {
		Log.d(getClass().toString(), "====>>>> executing request in tid: "+ android.os.Process.myTid());

		SshAuthenticationApi agentApi = new SshAuthenticationApi(mAppContext, sshAgent);

		Intent response = agentApi.executeApi(mAgentRequest.getRequest());
		int statusCode = response.getIntExtra(SshAuthenticationApi.EXTRA_RESULT_CODE, SshAuthenticationApi.RESULT_CODE_ERROR);

		switch (statusCode) {
		case SshAuthenticationApi.RESULT_CODE_SUCCESS:
		case SshAuthenticationApi.RESULT_CODE_ERROR:
			sendResult(response);
			return;
		case SshAuthenticationApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
			sendPendingIntent(response);
		}
	}

	private void sendResult(Intent response) {
		Bundle bundle = new Bundle();
		bundle.putParcelable(AgentRequest.AGENT_REQUEST_RESULT, response);
		sendBundle(bundle);
	}

	private void sendPendingIntent(Intent data) {
		// send back via handler to mAgentManager to execute
		PendingIntent pendingIntent = data.getParcelableExtra(SshAuthenticationApi.EXTRA_PENDING_INTENT);

		Bundle bundle = new Bundle();
		bundle.putParcelable(AgentRequest.AGENT_REQUEST_PENDINGINTENT, pendingIntent);
		sendBundle(bundle);
	}

	private void sendBundle(Bundle bundle) {
		bundle.putInt(AgentRequest.REQUEST_ID, mAgentRequest.getRequestId());

		Message message = mAgentManagerHandler.obtainMessage();
		message.setData(bundle);

		message.sendToTarget();
	}

	@Override
	protected Void doInBackground(AgentRequest... params) {
		execute(params[0]);
		return null;
	}
}

