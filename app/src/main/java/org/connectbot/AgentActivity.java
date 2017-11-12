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

package org.connectbot;

import org.connectbot.service.AgentManager;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;

public class AgentActivity extends AppCompatActivity {

	protected AgentManager mAgentManager = null;

	private ServiceConnection mAgentConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mAgentManager = ((AgentManager.AgentBinder) service).getService();
			startPendingIntent();
		}

		public void onServiceDisconnected(ComponentName className) {
			mAgentManager = null;
		}
	};

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		bindService(new Intent(this, AgentManager.class), mAgentConnection, Context.BIND_AUTO_CREATE);
	}

	private void startPendingIntent() {
		Intent intent = getIntent();
		PendingIntent pendingIntent = intent.getParcelableExtra(AgentManager.AGENT_PENDING_INTENT);
		try {
			startIntentSenderForResult(pendingIntent.getIntentSender(), AgentManager.AGENT_REQUEST_CODE,
					null, 0, 0, 0);
		} catch (IntentSender.SendIntentException e) {
			e.printStackTrace();
			mAgentManager.cancelPendingIntent();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);

		if (requestCode == AgentManager.AGENT_REQUEST_CODE) {
			mAgentManager.processPendingIntentResult(resultCode, data);
		}

		unbindService(mAgentConnection);

		setResult(Activity.RESULT_OK);
		finish();
	}
}
