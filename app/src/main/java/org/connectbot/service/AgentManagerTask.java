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
import org.openintents.ssh.SSHAgentApi;
import org.openintents.ssh.SSHAgentConnection;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;

public class AgentManagerTask extends AsyncTask<AgentRequest, Void, Void> {

	private Handler mActivityHandler;
	private Context mAppContext;

	// there is only one of these in flight at any time
	private AgentRequest mAgentRequest;

	public void setActivityHandler(Handler handler) {
		mActivityHandler = handler;
	}
	public void setApplicationContext(Context appContext) {
		mAppContext = appContext;
	}

	public void execute(final AgentRequest agentRequest) {
		mAgentRequest = agentRequest;
		final SSHAgentConnection agentConnector = new SSHAgentConnection(mAppContext, agentRequest.getTargetPackage());

		agentConnector.connect(new SSHAgentConnection.OnBound() {
			@Override
			public void onBound(ISSHAgentService sshAgent) {
				executeInternal(agentConnector);
				agentConnector.disconnect();
			}

			@Override
			public void onError() {
			}
		});
	}

	public void executeInternal(SSHAgentConnection agentConnector) {
		Log.d(getClass().toString(), "====>>>> executing request in tid: "+ android.os.Process.myTid());
		try {

			Intent response = agentConnector.execute(mAgentRequest.getRequest());
            int statusCode = response.getIntExtra(SSHAgentApi.EXTRA_STATUS_CODE, SSHAgentApi.STATUS_CODE_FAILURE);

            switch (statusCode) {
			case SSHAgentApi.STATUS_CODE_SUCCESS:
			case SSHAgentApi.STATUS_CODE_FAILURE:
				mAgentRequest.getAgentResultCallback().onAgentResult(response);
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

			if (result != null) {
				// execute received Intent again for result
				mAgentRequest.setRequest(result);
				execute(mAgentRequest);
			} else {
				mAgentRequest.getAgentResultCallback().onAgentResult(null);
			}
		}
	};

	@Override
	protected Void doInBackground(AgentRequest... params) {
		execute(params[0]);
		return null;
	}
}

