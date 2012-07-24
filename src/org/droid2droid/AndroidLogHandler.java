/******************************************************************************
 *
 * droid2droid - Distributed Android Framework
 * ==========================================
 *
 * Copyright (C) 2012 by Atos (http://www.http://atos.net)
 * http://www.droid2droid.org
 *
 ******************************************************************************
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
******************************************************************************/
package org.droid2droid;

import static org.droid2droid.Constants.LOGGER_FINE;
import static org.droid2droid.Constants.LOGGER_FINER;
import static org.droid2droid.Constants.LOGGER_FINEST;
import static org.droid2droid.Constants.LOGGER_INFO;
import static org.droid2droid.Constants.LOGGER_SEVERE;
import static org.droid2droid.Constants.LOGGER_WARNING;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import android.util.Log;
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

	@SuppressWarnings("unused")
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
