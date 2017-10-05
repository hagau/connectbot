/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2017 Jonas Dippel, Michael Perk, Marc Totzke
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

package org.connectbot.bean;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Parcel;
import android.os.Parcelable;

import org.connectbot.R;
import org.connectbot.util.AgentDatabase;
import org.openintents.ssh.SSHAgentApi;

public class AgentBean extends AbstractBean implements Parcelable {
    public static final String BEAN_NAME = "agent";

    private long id = -1;
    private String keyIdentifier;
    private String keyType;
    private byte[] publicKey;

	private String packageName;

    // TODO: deprecate
    private String serviceName;



    public AgentBean() {

    }

    public AgentBean(String keyIdentifier, String keyType , String packageName, byte[] publicKey) {
        this.keyIdentifier = keyIdentifier;
        this.keyType = keyType;
        this.serviceName = SSHAgentApi.SERVICE_INTENT;
        this.packageName = packageName;
        this.publicKey = publicKey;
    }

	protected AgentBean(Parcel in) {
		id = in.readLong();
		keyIdentifier = in.readString();
		keyType = in.readString();
		publicKey = in.createByteArray();
		packageName = in.readString();
		serviceName = in.readString();
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeLong(id);
		dest.writeString(keyIdentifier);
		dest.writeString(keyType);
		dest.writeByteArray(publicKey);
		dest.writeString(packageName);
		dest.writeString(serviceName);
	}

	public static final Creator<AgentBean> CREATOR = new Creator<AgentBean>() {
		@Override
		public AgentBean createFromParcel(Parcel in) {
			return new AgentBean(in);
		}

		@Override
		public AgentBean[] newArray(int size) {
			return new AgentBean[size];
		}
	};

	public String getKeyType() {
        return keyType;
    }

    public void setKeyType(String keyType) {
        this.keyType = keyType;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getKeyIdentifier() {
        return keyIdentifier;
    }

    public void setKeyIdentifier(String keyIdentifier) {
        this.keyIdentifier = keyIdentifier;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public byte[] getPublicKey() {
        if (publicKey != null) {
			return publicKey.clone();
		} else {
			return null;
		}
    }

    public void setPublicKey(byte[] encoded) {
        if (encoded == null)
            publicKey = null;
        else
            publicKey = encoded.clone();
    }

    public String getAgentAppName(Context context) {
        PackageManager packageManager = context.getPackageManager();
        ApplicationInfo applicationInfo = null;
        try {
            applicationInfo = packageManager.getApplicationInfo(getPackageName(), 0);
        } catch (final PackageManager.NameNotFoundException e) {
            return context.getString(R.string.Unknown);
        }
        return (String) (applicationInfo != null ? packageManager.getApplicationLabel(applicationInfo) : context.getString(R.string.Unknown));
    }

    @Override
    public ContentValues getValues() {

        ContentValues values = new ContentValues();

        values.put(AgentDatabase.FIELD_AGENT_KEY_IDENTIFIER, keyIdentifier);
        values.put(AgentDatabase.FIELD_AGENT_KEY_TYPE, keyType);
        values.put(AgentDatabase.FIELD_AGENT_PACKAGE_NAME, packageName);
        values.put(AgentDatabase.FIELD_AGENT_SERVICE_NAME, serviceName);
        values.put(AgentDatabase.FIELD_AGENT_PUBLIC_KEY, publicKey);

        return values;
    }

    @Override
    public String getBeanName() {
        return BEAN_NAME;
    }

    @Override
    public int describeContents() {
        return 0;
    }


}
