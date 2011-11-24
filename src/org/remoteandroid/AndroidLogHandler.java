package org.remoteandroid;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import android.util.Log;
import static org.remoteandroid.Constants.*;
public class AndroidLogHandler extends Handler
{

	public static void initLoggerHandler()
	{
		final Logger globalLogger=Logger.getLogger("");
		globalLogger.addHandler(new AndroidLogHandler());
		if (LOGGER_SEVERE) globalLogger.setLevel(Level.SEVERE);
		if (LOGGER_WARNING) globalLogger.setLevel(Level.WARNING);
		if (LOGGER_INFO) globalLogger.setLevel(Level.INFO);
		if (LOGGER_FINE) globalLogger.setLevel(Level.FINE);
		if (LOGGER_FINER) globalLogger.setLevel(Level.FINER);
		if (LOGGER_FINEST) globalLogger.setLevel(Level.FINEST);
	}
	public AndroidLogHandler()
	{
	}
	@Override
	public void close()
	{
	}

	@Override
	public void flush()
	{
	}

	@Override
	public void publish(LogRecord paramLogRecord)
	{
		// TODO Auto-generated method stub
		String message=paramLogRecord.getMessage();
		Level level=paramLogRecord.getLevel();
		String name=paramLogRecord.getLoggerName();
		message=name.substring(name.lastIndexOf('.')+1)+": "+message;
		name="Logger";
		if (level==Level.OFF)
			return;
		if (LOGGER_SEVERE && level==Level.SEVERE)
		{
			Log.e(name,message);
		}
		else if (LOGGER_WARNING && level==Level.WARNING)
		{
			Log.w(name,message);
		}
		else if (LOGGER_INFO && level==Level.INFO)
		{
			Log.i(name,message);
		}
		else if (LOGGER_FINE && level==Level.FINE)
		{
			Log.i(name,message);
		}
		else if (LOGGER_FINER && level==Level.FINER)
		{
			Log.i(name,message);
		}
		else if (LOGGER_FINEST && level==Level.FINEST)
		{
			Log.i(name,message);
		}
	}
}
