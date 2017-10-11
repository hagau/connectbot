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
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.CountDownLatch;

import org.connectbot.bean.AgentBean;
import org.connectbot.service.AgentManager;
import org.openintents.ssh.SshAgentApi;
import org.openintents.ssh.SigningRequest;
import org.openintents.ssh.SshAgentApi;
import org.openintents.ssh.SshAgentApiError;

import com.trilead.ssh2.auth.SignatureProxy;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;


public class SshAgentSignatureProxy extends SignatureProxy implements AgentRequest.OnAgentResultCallback {

	private Context mAppContext;

    private AgentBean mAgentBean;

	private CountDownLatch mExecutionLatch;

	private Intent mResult;
	private AgentRequest mAgentRequest;

	protected AgentManager agentManager = null;
	private ServiceConnection agentConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			agentManager = ((AgentManager.AgentBinder) service).getService();
			agentManager.execute(mAgentRequest);
		}

		public void onServiceDisconnected(ComponentName className) {
			agentManager = null;
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
    }

    @Override
    public byte[] sign(final byte[] challenge, final String hashAlgorithm) throws IOException {
		Log.d(getClass().toString(), "====>>>> executing sign in tid: "+ android.os.Process.myTid());

        Intent request = new SigningRequest(challenge, mAgentBean.getKeyIdentifier(), translateHashAlgorithm(hashAlgorithm)).toIntent();

		mAgentRequest = new AgentRequest(request, mAgentBean.getPackageName());
		mAgentRequest.setAgentResultCallback(this);

		mExecutionLatch = new CountDownLatch(1);
		mAppContext.bindService(new Intent(mAppContext, AgentManager.class), agentConnection, Context.BIND_AUTO_CREATE);

		// this is always run from a connection thread,
		// never the main thread, so locking is acceptable
		mExecutionLatch = new CountDownLatch(1);
		try { // wait for result
			mExecutionLatch.await();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		if (mResult == null) {
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

	public void onAgentResult(Intent data) {
		mResult = data;
		mExecutionLatch.countDown();
	}

}

