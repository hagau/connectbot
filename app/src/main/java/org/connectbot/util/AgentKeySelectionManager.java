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
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import org.connectbot.bean.AgentBean;
import org.openintents.ssh.KeySelectionRequest;
import org.openintents.ssh.KeySelectionResponse;
import org.openintents.ssh.SSHAgentApi;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;


public class AgentKeySelectionManager {
	public static final String AGENT_BEAN = "agent_bean";

    /**
	 * Select a key from an external ssh-agent
	 */
	public void selectKeyFromAgent(String agentName, Handler updateHandler) {

		Log.d(getClass().toString(), "====>>>> selectKeyFromAgent tid: "+ android.os.Process.myTid());

		KeySelectionResponse response = getKey(agentName);

		assert response != null; // response is never null anyway, so silence the warning
		int resultCode = response.getResultCode();

		Message message = updateHandler.obtainMessage(resultCode);

		if (resultCode == KeySelectionResponse.RESULT_CODE_SUCCESS) {
			Bundle bundle = new Bundle();

			byte[] encodedPublicKey = response.getEncodedPublicKey();
			int algorithm = response.getKeyAlgorithm();
			int format = response.getKeyFormat();

			PublicKey publicKey = getPublicKey(encodedPublicKey, algorithm, format);
			if (publicKey == null) {
				message.what = KeySelectionResponse.RESULT_CODE_ERROR;
				message.sendToTarget();
				return;
			}

			AgentBean agentBean = new AgentBean();
			agentBean.setKeyIdentifier(response.getKeyID());
			agentBean.setKeyType(publicKey.getAlgorithm());
			agentBean.setPackageName(agentName);
			agentBean.setDescription(response.getKeyDescription());
			agentBean.setPublicKey(publicKey.getEncoded());

			bundle.putParcelable(AGENT_BEAN, agentBean);
			message.setData(bundle);
		}

		message.sendToTarget();
	}

	private PublicKey getPublicKey(byte[] encodedPublicKey, int algorithmFlag, int format) {
        PublicKey publicKey = null;
		if (format == SSHAgentApi.X509) {
			X509EncodedKeySpec spec = new X509EncodedKeySpec(encodedPublicKey);
			try {
				KeyFactory kf = KeyFactory.getInstance(translateAlgorithm(algorithmFlag));
				publicKey = kf.generatePublic(spec);
//			publicKey = PubkeyUtils.decodePublic(encodedPublicKey, algorithm);
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
				return null;
			} catch (InvalidKeySpecException e) {
				e.printStackTrace();
				return null;
			}
		}
		return publicKey;
	}

	private String translateAlgorithm(int algorithm) throws NoSuchAlgorithmException {
		switch (algorithm) {
		case SSHAgentApi.RSA:
			return "RSA";
		case SSHAgentApi.DSA:
			return "DSA";
		case SSHAgentApi.ECDSA:
			return "EC";
		case SSHAgentApi.EDDSA:
			return "Ed25519";
		default:
			throw new NoSuchAlgorithmException("Algorithm not supported: "+ algorithm);
		}
	}

	private KeySelectionResponse getKey(String targetPackage) {

		Intent request = new KeySelectionRequest().toIntent();

		AgentRequest agentRequest = new AgentRequest(request, targetPackage);

		AgentExecutor agentExecutor = new AgentExecutor();
		Intent result = agentExecutor.execute(agentRequest);

		return new KeySelectionResponse(result);
    }
}

