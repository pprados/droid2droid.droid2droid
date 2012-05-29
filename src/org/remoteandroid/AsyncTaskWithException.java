package org.remoteandroid;

import java.util.concurrent.Executor;

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
			catch (Throwable e) // $codepro.audit.disable
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
		protected void onProgressUpdate(Progress... values)
		{
			AsyncTaskWithException.this.onProgressUpdate(values);
		}
		void publish(Progress... values)
		{
			publishProgress(values);
		}
	};
	private Wrapper mAsync=new Wrapper();
	
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
