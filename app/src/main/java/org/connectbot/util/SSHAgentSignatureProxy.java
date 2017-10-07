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

import android.content.Intent;
import android.util.Log;

import com.trilead.ssh2.auth.SignatureProxy;

import org.connectbot.bean.AgentBean;
import org.openintents.ssh.SSHAgentApi;
import org.openintents.ssh.SigningRequest;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;


public class SSHAgentSignatureProxy extends SignatureProxy {

    private AgentBean agentBean;

    /**
     * Instantiates a new SignatureProxy which needs a public key for the
     * later authentication process.
     */
    private SSHAgentSignatureProxy(AgentBean agentBean, PublicKey publicKey) throws InvalidKeySpecException, NoSuchAlgorithmException {
        super(publicKey);
        this.agentBean = agentBean;
    }

    @Override
    public byte[] sign(final byte[] challenge, final String hashAlgorithm) throws IOException {
		Log.d(getClass().toString(), "====>>>> executing sign in tid: "+ android.os.Process.myTid());

        Intent request = new SigningRequest(challenge, agentBean.getKeyIdentifier(), translateHashAlgorithm(hashAlgorithm)).toIntent();

		AgentRequest agentRequest = new AgentRequest(request, agentBean.getPackageName());

		AgentExecutor agentExecutor = new AgentExecutor();
		Intent result = agentExecutor.execute(agentRequest);
		if (result == null) {
			return null;
		}

		byte[] signature = result.getByteArrayExtra(SSHAgentApi.EXTRA_SIGNATURE);

		return signature;
    }

	private int translateHashAlgorithm(String hashAlgorithm) {
		switch (hashAlgorithm) {
		case SignatureProxy.SHA1:
			return SSHAgentApi.SHA1;
		case SignatureProxy.SHA512:
			return SSHAgentApi.SHA512;
		default:
			return SSHAgentApi.INVALID_HASH_ALGORITHM;
		}
	}

	public static class Builder {
        private AgentBean agentBean;

        public Builder(AgentBean agentBean) {
            this.agentBean = agentBean;
        }

        public SSHAgentSignatureProxy build() throws InvalidKeySpecException, NoSuchAlgorithmException {
            PublicKey publicKey = PubkeyUtils.decodePublic(agentBean.getPublicKey(), agentBean.getKeyType());
            return new SSHAgentSignatureProxy(agentBean, publicKey);
        }
    }
}

