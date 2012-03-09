package org.remoteandroid.discovery;

import org.remoteandroid.service.RemoteAndroidManagerStub;

public interface DiscoverAndroids
{
	public boolean startDiscovery(final long timeToIdentify,int flags);
	public void cancelDiscovery();
}
