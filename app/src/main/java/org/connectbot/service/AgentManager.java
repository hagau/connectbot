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
import java.util.HashSet;

import org.connectbot.bean.AgentBean;
import org.connectbot.util.AgentRequest;
import org.openintents.ssh.ISSHAgentService;
import org.openintents.ssh.SSHAgentApi;
import org.openintents.ssh.SSHAgentConnection;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

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

	public void setActivityHandler(Handler handler) {
		mActivityHandler = handler;
	}


	public void execute(final AgentRequest agentRequest) {
		register(agentRequest);

		final SSHAgentConnection agentConnector = new SSHAgentConnection(getApplicationContext(), agentRequest.getTargetPackage());

		agentConnector.connect(new SSHAgentConnection.OnBound() {
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

	public void executeInternal(SSHAgentConnection agentConnector, int requestId) {
		Log.d(getClass().toString(), "====>>>> executing request in tid: "+ android.os.Process.myTid());
		try {
			AgentRequest agentRequest = mAgentRequests.get(requestId);

			Intent response = agentConnector.execute(agentRequest.getRequest());
            int statusCode = response.getIntExtra(SSHAgentApi.EXTRA_STATUS_CODE, SSHAgentApi.STATUS_CODE_FAILURE);

            switch (statusCode) {
			case SSHAgentApi.STATUS_CODE_SUCCESS:
			case SSHAgentApi.STATUS_CODE_FAILURE:
				agentRequest.getAgentResultCallback().onAgentResult(response);
				mAgentRequests.remove(requestId);
                return;
            case SSHAgentApi.STATUS_CODE_USER_INTERACTION_REQUIRED:
            	// send back via handler to activity to execute
                PendingIntent pendingIntent = response.getParcelableExtra(SSHAgentApi.EXTRA_PENDING_INTENT);

				Bundle bundle = new Bundle();
				bundle.putParcelable(AgentRequest.AGENT_REQUEST_PENDINGINTENT, pendingIntent);

				Message message = mActivityHandler.obtainMessage();
				message.setData(bundle);

				mActivityHandler.sendMessage(message);
            }
        } catch (RemoteException e) {
            Log.d(getClass().toString(), "Error while signing key from agent:");
            Log.d(getClass().toString(), e.getMessage());
            e.printStackTrace();
        }
	}


	public Handler getPendingIntentResultHandler() {
		return pendingIntentResultHandler;
	}

	private Handler pendingIntentResultHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Log.d(getClass().toString(), "====>>>> pendingIntentResultHandler tid: "+ android.os.Process.myTid());
			Intent result = msg.getData().getParcelable(AgentRequest.AGENT_REQUEST_PENDINGINTENT_RESULT);

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
				agentRequest.getAgentResultCallback().onAgentResult(null);
				mAgentRequests.remove(requestId);
			}
		}
	};

}

