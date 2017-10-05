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

import java.io.IOException;

import org.connectbot.bean.AgentBean;
import org.openintents.ssh.KeySelectionRequest;
import org.openintents.ssh.KeySelectionResponse;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ResultReceiver;
import android.support.annotation.Nullable;
import android.util.Log;


public class AgentKeySelectionManager {
	public static final String AGENT_BEAN = "agent_bean";

    /**
	 * Select a key from an external ssh-agent
	 */
	public void selectKeyFromAgent(String agentName, Handler updateHandler) {

		Log.d(getClass().toString(), "====>>>> selectKeyFromAgent tid: "+ android.os.Process.myTid());

		KeySelectionResponse response = null;
		try {
			response = getKey(agentName);
		} catch (IOException e) {
			e.printStackTrace();
		}

		assert response != null; // response is never null anyway, so silence the warning
		int resultCode = response.getResultCode();

		Message message = updateHandler.obtainMessage(resultCode);

		if (resultCode == KeySelectionResponse.RESULT_CODE_SUCCESS) {
			Bundle bundle = new Bundle();
			AgentBean agentBean = new AgentBean(response.getKeyID(),
					response.getPublicKey().getAlgorithm(),
					agentName,
					response.getPublicKey().getEncoded());

			bundle.putParcelable(AGENT_BEAN, agentBean);
			message.setData(bundle);
		}

		message.sendToTarget();
	}

	private KeySelectionResponse getKey(String targetPackage) throws IOException {

		Intent request = new KeySelectionRequest().toIntent();

		AgentRequest agentRequest = new AgentRequest(request, targetPackage);

		AgentExecutor agentExecutor = new AgentExecutor();
		Intent result = agentExecutor.execute(agentRequest);

		return new KeySelectionResponse(result);
    }
}

