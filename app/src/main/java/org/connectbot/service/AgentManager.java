/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright (C) 2017 Christian Hagau <ach@hagau.se>
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

import java.util.ArrayDeque;
import java.util.Deque;

import org.connectbot.AgentActivity;
import org.connectbot.util.AgentRequest;
import org.openintents.ssh.authentication.ISshAuthenticationService;
import org.openintents.ssh.authentication.SshAuthenticationApi;
import org.openintents.ssh.authentication.SshAuthenticationConnection;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;

public class AgentManager extends Service {

	public static int AGENT_REQUEST_CODE = 1729;

	public static final int RESULT_CODE_CANCELED = -1;

	public static String AGENT_REQUEST_RESULT = "result";

	public static final String AGENT_PENDING_INTENT = "pendingIntent";

	private Deque<AgentRequest> mPendingIntentsStack = new ArrayDeque<>();

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

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		/*
		 * The lifecycle of this service is bound to the lifecycle  of TerminalManager, since
		 * authentication might need to occur in the background if connectivity is temporarily lost,
		 * so this service needs to run as long as there are TerminalBridges active in TerminalManager
		 */
		return START_STICKY;
	}

	public void execute(final AgentRequest agentRequest) {
		final SshAuthenticationConnection agentConnection = new SshAuthenticationConnection(getApplicationContext(), agentRequest.getTargetPackage());

		agentConnection.connect(new SshAuthenticationConnection.OnBound() {
			@Override
			public void onBound(ISshAuthenticationService sshAgent) {
				executeInternal(sshAgent, agentRequest);
				agentConnection.disconnect();
			}

			@Override
			public void onError() {
			}
		});
	}

	private void executeInternal(ISshAuthenticationService sshAgent, final AgentRequest agentRequest) {
		SshAuthenticationApi agentApi = new SshAuthenticationApi(getApplicationContext(), sshAgent);

		agentApi.executeApiAsync(agentRequest.getRequest(), new SshAuthenticationApi.ISshAgentCallback() {
			@Override
			public void onReturn(Intent intent) {
				processResponse(intent, agentRequest);
			}
		});

	}

	private void processResponse(Intent response, AgentRequest agentRequest) {
		int statusCode = response.getIntExtra(SshAuthenticationApi.EXTRA_RESULT_CODE, SshAuthenticationApi.RESULT_CODE_ERROR);
		switch (statusCode) {
		case SshAuthenticationApi.RESULT_CODE_SUCCESS:
		case SshAuthenticationApi.RESULT_CODE_ERROR:
			sendResult(response, agentRequest);
			return;
		case SshAuthenticationApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
			executePendingIntent(response, agentRequest);
		}
	}

	private void sendResult(Intent result, AgentRequest agentRequest) {
		if (result != null) { // return result to origin
			Handler resultHandler = agentRequest.getAgentResultHandler();

			Bundle bundle = new Bundle();
			bundle.putParcelable(AGENT_REQUEST_RESULT, result);

			Message message = resultHandler.obtainMessage();
			message.setData(bundle);

			message.sendToTarget();
		}
	}

	private void executePendingIntent(Intent response, AgentRequest agentRequest) {
		PendingIntent pendingIntent = response.getParcelableExtra(SshAuthenticationApi.EXTRA_PENDING_INTENT);

		// push request onto a stack so we know which request to drop when cancelled
		mPendingIntentsStack.push(agentRequest);

		Intent intent = new Intent(this, AgentActivity.class);
		intent.putExtra(AGENT_PENDING_INTENT, pendingIntent);
		startActivity(intent);

	}

	public void cancelPendingIntent() {
		mPendingIntentsStack.pop();
	}

	public void processPendingIntentResult(int resultCode, Intent result) {
		// get the request belonging to this result
		AgentRequest agentRequest = mPendingIntentsStack.pop();
		if (resultCode == Activity.RESULT_CANCELED) {
			agentRequest.getAgentResultHandler().sendEmptyMessage(RESULT_CODE_CANCELED);
			return;
		}

		if (result != null) {
			agentRequest.setRequest(result);

			// execute received Intent again for result
			execute(agentRequest);
		}
	}

}

