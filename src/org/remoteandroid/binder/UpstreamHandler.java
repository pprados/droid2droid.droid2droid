package org.remoteandroid.binder;

import java.security.PublicKey;

import org.remoteandroid.internal.Messages.Msg;
import org.remoteandroid.internal.socket.Channel;


public interface UpstreamHandler 
{
    public void messageReceived(PublicKey clientPublicKey,int id,final Msg msg,Channel channel) throws Exception; 
    public void channelOpen(int id); 
    public void channelClosed(int id); 
    public void exceptionCaught(int id,Throwable e);
}
