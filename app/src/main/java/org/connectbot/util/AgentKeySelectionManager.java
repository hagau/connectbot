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

package org.connectbot.util;

import java.lang.ref.WeakReference;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

import org.connectbot.bean.AgentBean;
import org.connectbot.service.AgentManager;
import org.openintents.ssh.authentication.SshAuthenticationApi;
import org.openintents.ssh.authentication.request.KeySelectionRequest;
import org.openintents.ssh.authentication.request.PublicKeyRequest;
import org.openintents.ssh.authentication.response.KeySelectionResponse;
import org.openintents.ssh.authentication.response.PublicKeyResponse;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;


public class AgentKeySelectionManager {
	public static final int RESULT_CODE_ERROR = SshAuthenticationApi.RESULT_CODE_ERROR;
	public static final int RESULT_CODE_SUCCESS = SshAuthenticationApi.RESULT_CODE_SUCCESS;
	public static final int RESULT_CODE_CANCELED = AgentManager.RESULT_CODE_CANCELED;

	public interface AgentKeySelectionCallback {
		void onKeySelectionResult(int resultCode, AgentBean agentBean);
	}

	private AgentManager mAgentManager = null;

	private ServiceConnection mAgentConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mAgentManager = ((AgentManager.AgentBinder) service).getService();
			getKeyId();
		}

		public void onServiceDisconnected(ComponentName className) {
			mAgentManager = null;
		}
	};

	private Context mAppContext;
	private String mAgentName;

	private AgentKeySelectionCallback mResultCallback;

	private AgentBean mAgentBean;

	public AgentKeySelectionManager(Context appContext, String agentName, AgentKeySelectionCallback resultCallback) {
		mAppContext = appContext;
		mAgentName = agentName;
		mResultCallback = resultCallback;

		mAgentBean = new AgentBean();
		mAgentBean.setPackageName(mAgentName);
	}

	/**
	 * Select a key from an external ssh-agent
	 */
	public void selectKeyFromAgent() {
		mAppContext.bindService(new Intent(mAppContext, AgentManager.class), mAgentConnection, Context.BIND_AUTO_CREATE);

	}

	protected void onResult(Intent response) {
		int resultCode = response.getIntExtra(SshAuthenticationApi.EXTRA_RESULT_CODE,
				SshAuthenticationApi.RESULT_CODE_ERROR);
		if (resultCode == SshAuthenticationApi.RESULT_CODE_ERROR) {
			finishError();
		}

		String keyId = response.getStringExtra(SshAuthenticationApi.EXTRA_KEY_ID);
		if (keyId != null) {
			onKeySelected(new KeySelectionResponse(response));
		} else {
			onPublicKey(new PublicKeyResponse(response));
		}
	}

	private void finish(int resultCode, AgentBean agentBean) {
		mResultCallback.onKeySelectionResult(resultCode, agentBean);

		mAppContext.unbindService(mAgentConnection);
	}

	protected void finishCancel() {
		finish(RESULT_CODE_CANCELED, null);
	}

	protected void finishError() {
		finish(PublicKeyResponse.RESULT_CODE_ERROR, null);
	}

	protected void finishSuccess() {
		finish(PublicKeyResponse.RESULT_CODE_SUCCESS, mAgentBean);
	}

	private PublicKey getPublicKey(byte[] encodedPublicKey, int algorithmFlag) {
		PublicKey publicKey;
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
			throw new NoSuchAlgorithmException("Algorithm not supported: " + algorithm);
		}
	}

	private void getKeyId() {
		Intent request = new KeySelectionRequest().toIntent();

		AgentRequest agentRequest = new AgentRequest(request, mAgentName);
		agentRequest.setAgentResultHandler(mResultHandler);

		mAgentManager.execute(agentRequest);
	}

	private void onKeySelected(KeySelectionResponse response) {
		int resultCode = response.getResultCode();

		if (resultCode == PublicKeyResponse.RESULT_CODE_SUCCESS) {
			mAgentBean.setKeyIdentifier(response.getKeyId());
			mAgentBean.setDescription(response.getKeyDescription());
			getPublicKey();
		} else {
			finishError();
		}
	}

	private void getPublicKey() {
		Intent request = new PublicKeyRequest(mAgentBean.getKeyIdentifier()).toIntent();

		AgentRequest agentRequest = new AgentRequest(request, mAgentName);
		agentRequest.setAgentResultHandler(mResultHandler);

		mAgentManager.execute(agentRequest);
	}

	private void onPublicKey(PublicKeyResponse response) {
		int resultCode = response.getResultCode();

		if (resultCode == PublicKeyResponse.RESULT_CODE_SUCCESS) {

			byte[] encodedPublicKey = response.getEncodedPublicKey();
			int algorithm = response.getKeyAlgorithm();

			// try decoding the encoded key to make sure it can be used for authentication later
			PublicKey publicKey = getPublicKey(encodedPublicKey, algorithm);
			if (publicKey == null) {
				finishError();
				return;
			}

			try {
				mAgentBean.setKeyType(translateAlgorithm(algorithm));
			} catch (NoSuchAlgorithmException e) {
				e.printStackTrace();
				finishError();
				return;
			}
			mAgentBean.setPublicKey(publicKey.getEncoded());

			finishSuccess();
		} else {
			finishError();
		}
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
				agentKeySelectionManager.finishCancel();
			} else {
				Intent result = msg.getData().getParcelable(AgentManager.AGENT_REQUEST_RESULT);
				if (result != null) {
					agentKeySelectionManager.onResult(result);
				} else {
					agentKeySelectionManager.finishError();
				}
			}
		}
	}
}

