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
import java.util.concurrent.ConcurrentLinkedQueue;

import org.connectbot.util.AgentRequest;

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

	private ConcurrentLinkedQueue<Integer> mPendingIntentsIdQueue = new ConcurrentLinkedQueue<>();

	public void dropRequest(int requestId) {
		mAgentRequests.remove(requestId);
	}

	public void setActivity(Activity activity) {
		mActivityWeakReference = new WeakReference<>(activity);
		mAgentManagerTaskResultHandler = new AgentResultHandler(mActivityWeakReference, new WeakReference<>(this));
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
		executeInternal(agentRequest);
	}

	public void executeInternal(final AgentRequest agentRequest) {
		AgentManagerTask agentManagerTask = new AgentManagerTask(getApplicationContext(), mAgentManagerTaskResultHandler);
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

	public void processPendingIntentResult(int requestCode, int resultCode, Intent result) {
		int requestId = mPendingIntentsIdQueue.poll();
		if (resultCode == Activity.RESULT_CANCELED) {
			mAgentRequests.get(requestId).getAgentResultHandler().sendEmptyMessage(RESULT_CODE_CANCELED);
			dropRequest(requestId);
			return;
		}
		AgentRequest agentRequest = mAgentRequests.get(requestId);

		if (result != null) {
			agentRequest.setRequest(result);

			// execute received Intent again for result
			executeInternal(agentRequest);
		}
	}

	private AgentResultHandler mAgentManagerTaskResultHandler = new AgentResultHandler(mActivityWeakReference, new WeakReference<>(this));

	public class AgentResultHandler extends Handler {
		private WeakReference<Activity> activityWeakReference;
		private WeakReference<AgentManager> agentManagerWeakReference;

		public AgentResultHandler(WeakReference<Activity> activityWeakReference, WeakReference<AgentManager> agentManagerWeakReference) {
			this.activityWeakReference = activityWeakReference;
			this.agentManagerWeakReference = agentManagerWeakReference;
		}

		@Override
		public void handleMessage(Message msg) {
			Activity activity = activityWeakReference.get();
			AgentManager agentManager = agentManagerWeakReference.get();
			if (activity == null || agentManager == null) {
				return;
			}

			Intent result = msg.getData().getParcelable(AgentRequest.AGENT_REQUEST_RESULT);
			int requestId = AgentRequest.REQUEST_ID_NONE;
			if (result != null) { // return result to origin
				requestId = msg.getData().getInt(AgentRequest.REQUEST_ID, AgentRequest.REQUEST_ID_NONE);
				Handler resultHandler = agentManager.mAgentRequests.get(requestId).getAgentResultHandler();
				Bundle bundle = new Bundle();
				bundle.putParcelable(AgentRequest.AGENT_REQUEST_RESULT, result);

				Message message = resultHandler.obtainMessage();
				message.setData(bundle);

				message.sendToTarget();
				return;
			}

			// execute PendingIntent
			requestId = msg.getData().getInt(AgentRequest.REQUEST_ID);
			PendingIntent pendingIntent = msg.getData().getParcelable(AgentRequest.AGENT_REQUEST_PENDINGINTENT);
			try {
				Log.d(getClass().toString(), "====>>>> tid: " + android.os.Process.myTid());

				// push request id on to a queue so we know which req to remove when cancelled
				// TODO: this does not work as intended, investigate
				mPendingIntentsIdQueue.add(requestId);

				activity.startIntentSenderForResult(pendingIntent.getIntentSender(), AgentRequest.AGENT_REQUEST_CODE, null, 0, 0, 0);
			} catch (IntentSender.SendIntentException e) {
				e.printStackTrace();
			}
		}
}
}

