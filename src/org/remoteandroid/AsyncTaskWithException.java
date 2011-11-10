package org.remoteandroid;

import android.os.AsyncTask;

public abstract class AsyncTaskWithException<Params,Progress,Result>
{
	private AsyncTask<Params,Progress,Object> mAsync=new AsyncTask<Params,Progress,Object>()
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
	};
	protected abstract Result doInBackground(Params... params) throws Exception;
	protected abstract void onException(Throwable e);
	protected void onPostExecute(Result result)
	{
		
	}
	public void execute(Params...params)
	{
		mAsync.execute(params);
	}
}
