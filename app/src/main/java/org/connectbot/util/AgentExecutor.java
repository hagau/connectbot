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

import java.util.concurrent.CountDownLatch;

import org.connectbot.service.AgentManager;

import android.content.Intent;


public class AgentExecutor implements AgentRequest.OnAgentResultCallback {

	private Intent mResult;

	private CountDownLatch mResultReadyLatch;

	public Intent execute(AgentRequest agentRequest) {
		agentRequest.setAgentResultCallback(this);

		AgentManager.get().execute(agentRequest);

		mResultReadyLatch = new CountDownLatch(1);
		try {
			mResultReadyLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		return mResult;
	}


    public void onAgentResult(Intent data) {
		mResult = data;
		mResultReadyLatch.countDown();
	}

}

