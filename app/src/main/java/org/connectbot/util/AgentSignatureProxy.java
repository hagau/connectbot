/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2017 Jonas Dippel, Marc Totzke
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

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import org.connectbot.bean.AgentBean;
import org.connectbot.service.AgentManager;
import org.openintents.ssh.authentication.SshAuthenticationApi;
import org.openintents.ssh.authentication.SshAuthenticationApiError;
import org.openintents.ssh.authentication.request.SigningRequest;

import com.trilead.ssh2.auth.SignatureProxy;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;


public class AgentSignatureProxy extends SignatureProxy {

	private Context mAppContext;

	private AgentBean mAgentBean;

	private AgentRequest mAgentRequest;

	private Intent mResult;

	private Handler mResultHandler;

	private AgentManager mAgentManager = null;

	private ServiceConnection mAgentConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mAgentManager = ((AgentManager.AgentBinder) service).getService();
			mAgentManager.execute(mAgentRequest);
		}

		public void onServiceDisconnected(ComponentName className) {
			mAgentManager = null;
		}
	};

	/**
	 * Instantiates a new SignatureProxy which needs a public key for the
	 * later authentication process.
	 */
	public AgentSignatureProxy(Context mAppContext, AgentBean mAgentBean) throws InvalidKeySpecException, NoSuchAlgorithmException {
		super(PubkeyUtils.decodePublic(mAgentBean.getPublicKey(), mAgentBean.getKeyType()));
		this.mAppContext = mAppContext;
		this.mAgentBean = mAgentBean;

		// this is always run from a connection thread, which is a bare Thread
		Looper.prepare();
		mResultHandler = new ResultHandler(this);
	}

	@Override
	public byte[] sign(final byte[] challenge, final String hashAlgorithm) throws IOException {
		Intent request = new SigningRequest(challenge, mAgentBean.getKeyIdentifier(), translateHashAlgorithm(hashAlgorithm)).toIntent();

		mAgentRequest = new AgentRequest(request, mAgentBean.getPackageName());
		mAgentRequest.setAgentResultHandler(mResultHandler);

		mAppContext.bindService(new Intent(mAppContext, AgentManager.class), mAgentConnection, Context.BIND_AUTO_CREATE);

		// this is always run from a connection thread, never the main thread
		// wait for message on Handler
		Looper.loop();

		if (mResult == null) { // canceled
			return null;
		}

		byte[] signature = mResult.getByteArrayExtra(SshAuthenticationApi.EXTRA_SIGNATURE);

		if (signature == null) {
			throw new IOException("No signature in agent response");
		}

		return signature;
	}

	private int translateHashAlgorithm(String hashAlgorithm) {
		switch (hashAlgorithm) {
		case SignatureProxy.SHA1:
			return SshAuthenticationApi.SHA1;
		case SignatureProxy.SHA256:
			return SshAuthenticationApi.SHA256;
		case SignatureProxy.SHA384:
			return SshAuthenticationApi.SHA384;
		case SignatureProxy.SHA512:
			return SshAuthenticationApi.SHA512;
		default:
			return SshAuthenticationApiError.INVALID_HASH_ALGORITHM;
		}
	}

	private static class ResultHandler extends Handler {
		private WeakReference<AgentSignatureProxy> mAgentSignatureProxyWeakReference;

		public ResultHandler(AgentSignatureProxy agentSignatureProxy) {
			mAgentSignatureProxyWeakReference = new WeakReference<>(agentSignatureProxy);
		}

		@Override
		public void handleMessage(Message msg) {
			AgentSignatureProxy agentSignatureProxy = mAgentSignatureProxyWeakReference.get();
			if (agentSignatureProxy == null) {
				Looper.myLooper().quit();
				return;
			}

			agentSignatureProxy.mResult = msg.getData().getParcelable(AgentManager.AGENT_REQUEST_RESULT);
			agentSignatureProxy.mAppContext.unbindService(agentSignatureProxy.mAgentConnection);
			Looper.myLooper().quit();
		}
	}

}

