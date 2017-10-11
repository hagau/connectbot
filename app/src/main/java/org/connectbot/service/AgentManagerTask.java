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
import org.openintents.ssh.ISSHAgentService;
import org.openintents.ssh.SshAgentApi;
import org.openintents.ssh.SshAgentConnection;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class AgentManagerTask extends AsyncTask<AgentRequest, Void, Void> {

	private Handler mActivityHandler;
	private Context mAppContext;

	private AgentRequest mAgentRequest;

	public AgentManagerTask(Context appContext, Handler activityHandler) {
		mAppContext = appContext;
		mActivityHandler = activityHandler;
	}

	public void execute(final AgentRequest agentRequest) {
		mAgentRequest = agentRequest;
		final SshAgentConnection agentConnection = new SshAgentConnection(mAppContext, agentRequest.getTargetPackage());

		agentConnection.connect(new SshAgentConnection.OnBound() {
			@Override
			public void onBound(ISSHAgentService sshAgent) {
				executeInternal(sshAgent);
				agentConnection.disconnect();
			}

			@Override
			public void onError() {
			}
		});
	}

	private void executeInternal(ISSHAgentService sshAgent) {
		Log.d(getClass().toString(), "====>>>> executing request in tid: "+ android.os.Process.myTid());

		SshAgentApi agentApi = new SshAgentApi(sshAgent);

		Intent response = agentApi.executeApi(mAgentRequest.getRequest());
		int statusCode = response.getIntExtra(SshAgentApi.EXTRA_RESULT_CODE, SshAgentApi.RESULT_CODE_FAILURE);

		switch (statusCode) {
		case SshAgentApi.RESULT_CODE_SUCCESS:
		case SshAgentApi.RESULT_CODE_FAILURE:
			sendResult(response);
			return;
		case SshAgentApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
			sendPendingIntent(response);
		}
	}

	private void sendResult(Intent response) {
		Handler resultHandler = mAgentRequest.getAgentResultHandler();

		Bundle bundle = new Bundle();
		bundle.putParcelable(AgentRequest.AGENT_REQUEST_RESULT, response);

		Message message = resultHandler.obtainMessage();
		message.setData(bundle);

		message.sendToTarget();
	}

	private void sendPendingIntent(Intent data) {
		// send back via handler to activity to execute
		PendingIntent pendingIntent = data.getParcelableExtra(SshAgentApi.EXTRA_PENDING_INTENT);

		Bundle bundle = new Bundle();
		bundle.putParcelable(AgentRequest.AGENT_REQUEST_PENDINGINTENT, pendingIntent);

		Message message = mActivityHandler.obtainMessage();
		message.setData(bundle);

		message.sendToTarget();
	}

	@Override
	protected Void doInBackground(AgentRequest... params) {
		execute(params[0]);
		return null;
	}
}

