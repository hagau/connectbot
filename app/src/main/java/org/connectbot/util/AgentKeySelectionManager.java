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
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

import org.connectbot.bean.AgentBean;
import org.connectbot.service.AgentManager;
import org.openintents.ssh.authentication.SshAuthenticationApi;
import org.openintents.ssh.authentication.request.PublicKeyRequest;
import org.openintents.ssh.authentication.response.PublicKeyResponse;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;


public class AgentKeySelectionManager {
	public static final String AGENT_BEAN = "agent_bean";
	public static final int RESULT_CODE_ERROR = SshAuthenticationApi.RESULT_CODE_ERROR;
	public static final int RESULT_CODE_SUCCESS = SshAuthenticationApi.RESULT_CODE_SUCCESS;
	public static final int RESULT_CODE_CANCELED = AgentManager.RESULT_CODE_CANCELED;

	private AgentManager mAgentManager = null;

	private ServiceConnection mAgentConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mAgentManager = ((AgentManager.AgentBinder) service).getService();
			getKey(mAgentName);
		}

		public void onServiceDisconnected(ComponentName className) {
			mAgentManager = null;
		}
	};

	private Context mAppContext;
	private String mAgentName;
	private Handler mUpdateHandler;

	public AgentKeySelectionManager(Context mAppContext, String mAgentName, Handler mUpdateHandler) {
		this.mAppContext = mAppContext;
		this.mAgentName = mAgentName;
		this.mUpdateHandler = mUpdateHandler;
	}

    /**
	 * Select a key from an external ssh-agent
	 */
	public void selectKeyFromAgent() {
		Log.d(getClass().toString(), "====>>>> selectKeyFromAgent tid: "+ android.os.Process.myTid());

		mAppContext.bindService(new Intent(mAppContext, AgentManager.class), mAgentConnection, Context.BIND_AUTO_CREATE);

	}

	public void updateFragment(PublicKeyResponse response) {
		int resultCode;
		if (response == null) {
			resultCode = RESULT_CODE_CANCELED;
		} else {
			resultCode = response.getResultCode();
		}

		Message message = mUpdateHandler.obtainMessage(resultCode);

		if (resultCode == PublicKeyResponse.RESULT_CODE_SUCCESS) {
			Bundle bundle = new Bundle();

			byte[] encodedPublicKey = response.getEncodedPublicKey();
			int algorithm = response.getKeyAlgorithm();

			// try decoding the encoded key to make sure it can be used for authentication later
			PublicKey publicKey = getPublicKey(encodedPublicKey, algorithm);
			if (publicKey == null) {
				message.what = PublicKeyResponse.RESULT_CODE_ERROR;
				message.sendToTarget();
				return;
			}

			AgentBean agentBean = new AgentBean();
			agentBean.setKeyIdentifier(response.getKeyID());
			try {
				agentBean.setKeyType(translateAlgorithm(algorithm));
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
				message.what = PublicKeyResponse.RESULT_CODE_ERROR;
				message.sendToTarget();
				return;
			}
			agentBean.setPackageName(mAgentName);
			agentBean.setDescription(response.getKeyDescription());
			agentBean.setPublicKey(publicKey.getEncoded());

			bundle.putParcelable(AGENT_BEAN, agentBean);
			message.setData(bundle);
		}

		message.sendToTarget();
	}

	private PublicKey getPublicKey(byte[] encodedPublicKey, int algorithmFlag) {
		PublicKey publicKey = null;
		try {
			publicKey = PubkeyUtils.decodePublic(encodedPublicKey, translateAlgorithm(algorithmFlag));
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
			return null;
		} catch (InvalidKeySpecException e) {
			e.printStackTrace();
			return null;
		}
		return publicKey;
	}

	private String translateAlgorithm(int algorithm) throws NoSuchAlgorithmException {
		switch (algorithm) {
		case SshAuthenticationApi.RSA:
			return "RSA";
		case SshAuthenticationApi.DSA:
			return "DSA";
		case SshAuthenticationApi.ECDSA:
			return "EC";
		case SshAuthenticationApi.EDDSA:
			return "Ed25519";
		default:
			throw new NoSuchAlgorithmException("Algorithm not supported: "+ algorithm);
		}
	}

	private void getKey(String targetPackage) {

		Intent request = new PublicKeyRequest().toIntent();

		AgentRequest agentRequest = new AgentRequest(request, targetPackage);
		agentRequest.setAgentResultHandler(mResultHandler);

		mAgentManager.execute(agentRequest);

    }

	private ResultHandler mResultHandler = new ResultHandler(new WeakReference<>(this));

	private static class ResultHandler extends Handler {
		private WeakReference<AgentKeySelectionManager> sshAgentSignatureProxyWeakReference;

		public ResultHandler(WeakReference<AgentKeySelectionManager> sshAgentSignatureProxyWeakReference) {
			this.sshAgentSignatureProxyWeakReference = sshAgentSignatureProxyWeakReference;
		}

		@Override
		public void handleMessage(Message msg) {
			AgentKeySelectionManager agentKeySelectionManager = sshAgentSignatureProxyWeakReference.get();
			if (agentKeySelectionManager == null) {
				return;
			}

			if (msg.what == AgentManager.RESULT_CODE_CANCELED) {
				agentKeySelectionManager.updateFragment(null);
			} else {
				Intent result = msg.getData().getParcelable(AgentRequest.AGENT_REQUEST_RESULT);
				agentKeySelectionManager.updateFragment(new PublicKeyResponse(result));
			}

			agentKeySelectionManager.mAppContext.unbindService(agentKeySelectionManager.mAgentConnection);
		}
	}
}

