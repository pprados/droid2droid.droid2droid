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

import java.util.concurrent.Executor;

import android.annotation.TargetApi;
import android.os.AsyncTask;

public abstract class AsyncTaskWithException<Params,Progress,Result>
{
	class Wrapper extends AsyncTask<Params,Progress,Object>
	{

		@Override
		protected Object doInBackground(Params... params)
		{
			try
			{
				return AsyncTaskWithException.this.doInBackground(params);
			}
			catch (Throwable e)
			{
				return e;
			}
		}
		@Override
		protected void onPreExecute()
		{
			AsyncTaskWithException.this.onPreExecute();
		}
		@SuppressWarnings("unchecked")
		@Override
		protected final void onPostExecute(Object result)
		{
			if (result instanceof Throwable)
			{
				AsyncTaskWithException.this.onException((Throwable)result);
			}
			else
				AsyncTaskWithException.this.onPostExecute((Result)result);
		}
		@SuppressWarnings("unchecked")
		@Override
		protected void onCancelled(Object result)
		{
			AsyncTaskWithException.this.onCancelled((Result)result);
		}
		@Override
		protected void onCancelled()
		{
			AsyncTaskWithException.this.onCancelled();
		}
		@Override
		protected void onProgressUpdate(Progress... values)
		{
			AsyncTaskWithException.this.onProgressUpdate(values);
		}
		void publish(Progress... values)
		{
			publishProgress(values);
		}
	};
	private final Wrapper mAsync=new Wrapper();
	
	protected abstract Result doInBackground(Params... params) throws Exception;
	protected abstract void onException(Throwable e);
	protected void onCancelled(Result result)
	{
		
	}
	protected void onCancelled()
	{
	}
	protected void onPreExecute()
	{
		
	}
	protected void onProgressUpdate(Progress... values)
	{
		
	}
	protected void onPostExecute(Result result)
	{
		
	}
	protected final void publishProgress(Progress... values)
	{
		mAsync.publish(values);
	}
	public void execute(Params...params)
	{
		mAsync.execute(params);
	}
	@TargetApi(11)
	public final AsyncTaskWithException<Params, Progress, Result>	 executeOnExecutor(Executor exec, Params... params)
	{
		mAsync.executeOnExecutor(exec, params);
		return this;
	}
	public final boolean	 cancel(boolean mayInterruptIfRunning)
	{
		return mAsync.cancel(mayInterruptIfRunning);
	}
	public final AsyncTask.Status	 getStatus()
	{
		return mAsync.getStatus();
	}
//	public final Result	 get() throws InterruptedException, ExecutionException
//	{
//		Object rc=mAsync.get();
//		if (rc instanceof Throwable)
//		{
//			return null;
//		}
//		return (Result)rc;
//	}
//	public final Result	 get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException
//	{
//		Object rc=mAsync.get(timeout,unit);
//		if (rc instanceof Throwable)
//		{
//			return null;
//		}
//		return (Result)rc;
//	}
	public final boolean	 isCancelled()
	{
		return mAsync.isCancelled();
	}
}
