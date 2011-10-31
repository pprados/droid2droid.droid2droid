package org.remoteandroid.discovery;

import org.remoteandroid.service.RemoteAndroidManagerStub;

public interface DiscoverAndroids
{
	public boolean startDiscovery(final long timeToIdentify,int flags,RemoteAndroidManagerStub discover);
	public void cancelDiscovery(RemoteAndroidManagerStub discover);
}
