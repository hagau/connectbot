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
import java.util.HashMap;

import org.connectbot.util.AgentRequest;
import org.openintents.ssh.ISSHAgentService;
import org.openintents.ssh.SshAgentConnection;
import org.openintents.ssh.SshAgentApi;

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
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

public class AgentManager extends Service {

	private Activity mActivity;

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

	public void setActivity(Activity activity) {
		mActivity = activity;
	}

	public void execute(final AgentRequest agentRequest) {
		register(agentRequest);

		final SshAgentConnection agentConnector = new SshAgentConnection(getApplicationContext(), agentRequest.getTargetPackage());

		agentConnector.connect(new SshAgentConnection.OnBound() {
			@Override
			public void onBound(ISSHAgentService sshAgent) {
				executeInternal(agentConnector, agentRequest.getRequestId());
				agentConnector.disconnect();
			}

			@Override
			public void onError() {
			}
		});
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

	public void executeInternal(SshAgentConnection agentConnector, int requestId) {
		Log.d(getClass().toString(), "====>>>> executing request in tid: "+ android.os.Process.myTid());
		try {
			AgentRequest agentRequest = mAgentRequests.get(requestId);

			Intent response = agentConnector.execute(agentRequest.getRequest());
            int statusCode = response.getIntExtra(SshAgentApi.EXTRA_RESULT_CODE, SshAgentApi.RESULT_CODE_FAILURE);

            switch (statusCode) {
			case SshAgentApi.RESULT_CODE_SUCCESS:
			case SshAgentApi.RESULT_CODE_FAILURE:
				agentRequest.getAgentResultCallback().onAgentResult(response);
				mAgentRequests.remove(requestId);
                return;
            case SshAgentApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
                PendingIntent pendingIntent = response.getParcelableExtra(SshAgentApi.EXTRA_PENDING_INTENT);

				try {
					Log.d(getClass().toString(), "====>>>> tid: " + android.os.Process.myTid());
					mActivity.startIntentSenderForResult(pendingIntent.getIntentSender(), AgentRequest.AGENT_REQUEST_CODE, null, 0, 0, 0);
				} catch (IntentSender.SendIntentException e) {
					e.printStackTrace();
				}
            }
        } catch (RemoteException e) {
            Log.d(getClass().toString(), "Error while signing key from agent:");
            Log.d(getClass().toString(), e.getMessage());
            e.printStackTrace();
        }
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
			Log.d(getClass().toString(), "====>>>> agentRequest: "+ agentRequest);
			agentRequest.getAgentResultCallback().onAgentResult(null);
			mAgentRequests.remove(requestId);
		}
	}

//	public Handler getPendingIntentResultHandler() {
//		return pendingIntentResultHandler;
//	}
//
//	private Handler pendingIntentResultHandler = new PendingIntentResultHandler(new WeakReference<>(this));
//
//	private static class PendingIntentResultHandler extends Handler {
//		private WeakReference<AgentManager> agentManagerWeakReference;
//
//		public PendingIntentResultHandler(WeakReference<AgentManager> agentManagerWeakReference) {
//			this.agentManagerWeakReference = agentManagerWeakReference;
//		}
//
//		@Override
//		public void handleMessage(Message msg) {
//			AgentManager agentManager = agentManagerWeakReference.get();
//			if (agentManager == null) {
//				return;
//			}
//
//			Intent result = msg.getData().getParcelable(AgentRequest.AGENT_REQUEST_PENDINGINTENT_RESULT);
//			agentManager.processPendingIntentResult(result);
//		}
//	}
}

