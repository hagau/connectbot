/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2017 Jonas Dippel, Marc Totzke
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
import org.openintents.ssh.SigningRequest;
import org.openintents.ssh.SshAgentApi;
import org.openintents.ssh.SshAgentApiError;

import com.trilead.ssh2.auth.SignatureProxy;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.util.Log;


public class SshAgentSignatureProxy extends SignatureProxy {

	private Context mAppContext;

    private AgentBean mAgentBean;

	private AgentRequest mAgentRequest;

	private Intent mResult;

	private Handler mResultHandler;

	private AgentManager mAgentManager = null;

	private ServiceConnection agentConnection = new ServiceConnection() {
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
    public SshAgentSignatureProxy(Context mAppContext, AgentBean mAgentBean) throws InvalidKeySpecException, NoSuchAlgorithmException {
        super(PubkeyUtils.decodePublic(mAgentBean.getPublicKey(), mAgentBean.getKeyType()));
		this.mAppContext = mAppContext;
        this.mAgentBean = mAgentBean;

		// this is always run from a connection thread, which is a bare Thread
		Looper.prepare();
		mResultHandler = new ResultHandler(new WeakReference<>(this));
    }

    @Override
    public byte[] sign(final byte[] challenge, final String hashAlgorithm) throws IOException {
		Log.d(getClass().toString(), "====>>>> executing sign in tid: "+ android.os.Process.myTid());

        Intent request = new SigningRequest(challenge, mAgentBean.getKeyIdentifier(), translateHashAlgorithm(hashAlgorithm)).toIntent();

		mAgentRequest = new AgentRequest(request, mAgentBean.getPackageName());
		mAgentRequest.setAgentResultHandler(mResultHandler);

		mAppContext.bindService(new Intent(mAppContext, AgentManager.class), agentConnection, Context.BIND_AUTO_CREATE);

		// this is always run from a connection thread, never the main thread
		// wait for message on Handler
		Looper.loop();

		if (mResult == null) { // canceled
			return null;
		}

		byte[] signature = mResult.getByteArrayExtra(SshAgentApi.EXTRA_SIGNATURE);

		// TODO: error handling
		if (signature == null) {
			throw new IOException("No signature in agent response");
		}

		return signature;
    }

	private int translateHashAlgorithm(String hashAlgorithm) {
		switch (hashAlgorithm) {
		case SignatureProxy.SHA1:
			return SshAgentApi.SHA1;
		case SignatureProxy.SHA256:
			return SshAgentApi.SHA256;
		case SignatureProxy.SHA384:
			return SshAgentApi.SHA384;
		case SignatureProxy.SHA512:
			return SshAgentApi.SHA512;
		default:
			return SshAgentApiError.INVALID_HASH_ALGORITHM;
		}
	}

	private static class ResultHandler extends Handler {
		private WeakReference<SshAgentSignatureProxy> sshAgentSignatureProxyWeakReference;

		public ResultHandler(WeakReference<SshAgentSignatureProxy> sshAgentSignatureProxyWeakReference) {
			this.sshAgentSignatureProxyWeakReference = sshAgentSignatureProxyWeakReference;
		}

		@Override
		public void handleMessage(Message msg) {
			SshAgentSignatureProxy sshAgentSignatureProxy = sshAgentSignatureProxyWeakReference.get();
			if (sshAgentSignatureProxy == null) {
				return;
			}

			Intent result = msg.getData().getParcelable(AgentRequest.AGENT_REQUEST_RESULT);
			sshAgentSignatureProxy.mResult = result;
			sshAgentSignatureProxy.mAppContext.unbindService(sshAgentSignatureProxy.agentConnection);
			Looper.myLooper().quit();
		}
	}

}

