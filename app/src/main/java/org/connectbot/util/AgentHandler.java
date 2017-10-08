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

import java.lang.ref.WeakReference;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.IntentSender;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class AgentHandler extends Handler {
	private WeakReference<Activity> activityWeakReference;

	public AgentHandler(WeakReference<Activity> activityWeakReference) {
		this.activityWeakReference = activityWeakReference;
	}

	@Override
	public void handleMessage(Message msg) {
		Activity activity = activityWeakReference.get();
		if (activity == null) {
			return;
		}

		PendingIntent pendingIntent = msg.getData().getParcelable(AgentRequest.AGENT_REQUEST_PENDINGINTENT);
		try {
			Log.d(getClass().toString(), "====>>>> tid: " + android.os.Process.myTid());
			activity.startIntentSenderForResult(pendingIntent.getIntentSender(), AgentRequest.AGENT_REQUEST_CODE, null, 0, 0, 0);
		} catch (IntentSender.SendIntentException e) {
			e.printStackTrace();
		}
	}
}
