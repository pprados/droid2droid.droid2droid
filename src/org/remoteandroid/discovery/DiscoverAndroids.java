package org.remoteandroid.discovery;


public interface DiscoverAndroids
{
	public boolean startDiscovery(final long timeToIdentify,int flags);
	public void cancelDiscovery();
}
