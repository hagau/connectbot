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

import java.lang.ref.WeakReference;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;

import org.connectbot.util.AgentRequest;
import org.openintents.ssh.authentication.ISshAuthenticationService;
import org.openintents.ssh.authentication.SshAuthenticationApi;
import org.openintents.ssh.authentication.SshAuthenticationConnection;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.IntentSender;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.annotation.Nullable;
import android.util.Log;

public class AgentManager extends Service {

	public static final int RESULT_CODE_CANCELED = -1;

	private WeakReference<Activity> mActivityWeakReference;

	private HashMap<Integer, AgentRequest> mAgentRequests = new HashMap<>();

	private Deque<Integer> mPendingIntentsIdStack = new ArrayDeque<>();

	public void dropRequest(int requestId) {
		mAgentRequests.remove(requestId);
	}

	public void setActivity(Activity activity) {
		mActivityWeakReference = new WeakReference<>(activity);
	}

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

	public void execute(final AgentRequest agentRequest) {
		register(agentRequest);
		connectExecute(agentRequest);
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

	public void connectExecute(final AgentRequest agentRequest) {
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
		Log.d(getClass().toString(), "====>>>> executing request in tid: " + android.os.Process.myTid());

		SshAuthenticationApi agentApi = new SshAuthenticationApi(getApplicationContext(), sshAgent);

		agentApi.executeApiAsync(agentRequest.getRequest(), new SshAuthenticationApi.ISshAgentCallback() {
			@Override
			public void onReturn(Intent intent) {
				checkResponse(intent, agentRequest);
			}
		});

	}

	private void checkResponse(Intent response, AgentRequest agentRequest) {
		int statusCode = response.getIntExtra(SshAuthenticationApi.EXTRA_RESULT_CODE, SshAuthenticationApi.RESULT_CODE_ERROR);
		switch (statusCode) {
		case SshAuthenticationApi.RESULT_CODE_SUCCESS:
		case SshAuthenticationApi.RESULT_CODE_ERROR:
			processResult(response, agentRequest);
			return;
		case SshAuthenticationApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
			processPendingIntent(response, agentRequest);
		}
	}

	private void processPendingIntent(Intent response, AgentRequest agentRequest) {
		// execute PendingIntent
		int requestId = agentRequest.getRequestId();
		PendingIntent pendingIntent = response.getParcelableExtra(SshAuthenticationApi.EXTRA_PENDING_INTENT);
		try {
			Log.d(getClass().toString(), "====>>>> tid: " + android.os.Process.myTid());

			// push request id on to a queue so we know which req to remove when cancelled
			// TODO: this does not work as intended, investigate
			mPendingIntentsIdStack.push(requestId);

			mActivityWeakReference.get().startIntentSenderForResult(pendingIntent.getIntentSender(), AgentRequest.AGENT_REQUEST_CODE, null, 0, 0, 0);
		} catch (IntentSender.SendIntentException e) {
			e.printStackTrace();
		}
	}

	private void processResult(Intent result, AgentRequest agentRequest) {
		int requestId = AgentRequest.REQUEST_ID_NONE;
		if (result != null) { // return result to origin
			requestId = agentRequest.getRequestId();
			Handler resultHandler = mAgentRequests.get(requestId).getAgentResultHandler();
			Bundle bundle = new Bundle();
			bundle.putParcelable(AgentRequest.AGENT_REQUEST_RESULT, result);

			Message message = resultHandler.obtainMessage();
			message.setData(bundle);

			message.sendToTarget();
		}
	}


	public void processPendingIntentResult(int requestCode, int resultCode, Intent result) {
		int requestId = mPendingIntentsIdStack.pop();
		if (resultCode == Activity.RESULT_CANCELED) {
			mAgentRequests.get(requestId).getAgentResultHandler().sendEmptyMessage(RESULT_CODE_CANCELED);
			dropRequest(requestId);
			return;
		}
		AgentRequest agentRequest = mAgentRequests.get(requestId);

		if (result != null) {
			agentRequest.setRequest(result);

			// execute received Intent again for result
			connectExecute(agentRequest);
		}
	}

}

