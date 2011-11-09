package org.remoteandroid.ui.connect;

import static org.remoteandroid.Constants.TAG_CONNECT;
import static org.remoteandroid.internal.Constants.I;
import static org.remoteandroid.internal.Constants.W;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.remoteandroid.R;
import org.remoteandroid.internal.Compatibility;
import org.remoteandroid.ui.connect.qrcode.BeepManager;
import org.remoteandroid.ui.connect.qrcode.CameraManager;
import org.remoteandroid.ui.connect.qrcode.CaptureHandler;
import org.remoteandroid.ui.connect.qrcode.FinishListener;
import org.remoteandroid.ui.connect.qrcode.FlashlightManager;
import org.remoteandroid.ui.connect.qrcode.InactivityTimer;
import org.remoteandroid.ui.connect.qrcode.ViewfinderView;
import org.remoteandroid.ui.connect.qrcode.Wrapper;

import android.app.AlertDialog;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.view.Menu;
import android.support.v4.view.MenuItem;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.ResultPoint;

public class QRCodeFragment extends AbstractBodyFragment implements SurfaceHolder.Callback, KeyEvent.Callback, Wrapper
{
	private View mViewer;

	private static final boolean NO_CAMERA=false;
	
	private static final Set<ResultMetadataType>	DISPLAYABLE_METADATA_TYPES;
	static
	{
		DISPLAYABLE_METADATA_TYPES = new HashSet<ResultMetadataType>(5);
		DISPLAYABLE_METADATA_TYPES.add(ResultMetadataType.ISSUE_NUMBER);
		DISPLAYABLE_METADATA_TYPES.add(ResultMetadataType.SUGGESTED_PRICE);
		DISPLAYABLE_METADATA_TYPES.add(ResultMetadataType.ERROR_CORRECTION_LEVEL);
		DISPLAYABLE_METADATA_TYPES.add(ResultMetadataType.POSSIBLE_COUNTRY);
	}
	static class Cache
	{
		private CaptureHandler			mHandler;
		private boolean 				mFlashState;
		private Result					mLastResult;
		private InactivityTimer			mInactivityTimer;
		private BeepManager				mBeepManager;
	}
	Cache mCache;

	
	private ViewfinderView			mViewfinderView;

	private TextView				mStatusView;

	private boolean					mHasSurface;

	@Override
	public ViewfinderView getViewfinderView()
	{
		return mViewfinderView;
	}

	public Handler getHandler()
	{
		return mCache.mHandler;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		mViewer =inflater.inflate(R.layout.connect_qrcode, container, false);

		if (I) Log.i(TAG_CONNECT,"onCreateView...");

		Window window = getActivity().getWindow();
		
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		mViewfinderView = (ViewfinderView) mViewer.findViewById(R.id.viewfinder_view);
		mStatusView = (TextView) mViewer.findViewById(R.id.status_view);
//		mCache=(Cache)getLastNonConfigurationInstance();
		mCache=null;
		mHasSurface = false;
		
		if (mCache==null)
		{
			mCache=new Cache();
			mCache.mHandler = null;
			mCache.mLastResult = null;
			mCache.mInactivityTimer = new InactivityTimer(getActivity());
			mCache.mBeepManager = new BeepManager(getActivity());
			if (!NO_CAMERA)
			{
				CameraManager.init(getActivity().getApplication());
			}
		}
		else
		{
			mCache.mInactivityTimer.setActivity(getActivity());
			mCache.mBeepManager.setActivity(getActivity());
			if (mCache.mHandler!=null)
				mCache.mHandler.setWrapper(this);
		}
		return mViewer;
	}
	
//	@Override
//	public Object onRetainNonConfigurationInstance()
//	{
//		return mCache;
//	}
	
	@Override
	public void onResume()
	{
		super.onResume();
		if (I) Log.i(TAG_CONNECT,"onResume...");
		resetStatusView();

		SurfaceView surfaceView = (SurfaceView) mViewer.findViewById(R.id.preview_view);
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		if (mHasSurface)
		{
			// The activity was paused but not stopped, so the surface still exists. Therefore
			// surfaceCreated() won't be called, so init the camera here.
			
			initCamera(surfaceHolder,getRotation());
			
		}
		else
		{
			// Install the callback and wait for surfaceCreated() to init the camera.
			surfaceHolder.addCallback(this);
			surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}

		mCache.mBeepManager.updatePrefs();
		mCache.mInactivityTimer.onResume();
	}
	private int getRotation()
	{
		if (Compatibility.VERSION_SDK_INT>=Compatibility.VERSION_FROYO)
			return getActivity().getWindowManager().getDefaultDisplay().getRotation();
		else
			return getActivity().getWindowManager().getDefaultDisplay().getOrientation();
	}
	
	@Override
	public void onPause()
	{
		super.onPause();
		if (I) Log.i(TAG_CONNECT,"onPause...");
		if (mCache.mHandler != null)
		{
			mCache.mHandler.quitSynchronously();
			mCache.mHandler = null;
		}
		mCache.mInactivityTimer.onPause();
		if (!NO_CAMERA)
			CameraManager.get().closeDriver();
	}

	@Override
	public void onDestroy()
	{
		if (I) Log.i(TAG_CONNECT,"onDestroy...");
		mCache.mInactivityTimer.shutdown();
		super.onDestroy();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);
		CameraManager.get().closeDriver();
		CameraManager.get().init(getActivity());
		SurfaceView surfaceView = (SurfaceView) mViewer.findViewById(R.id.preview_view);
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		CameraManager.get().closeDriver();
		initCamera(surfaceHolder,getRotation());
	}
	
//	@Override
//	public boolean onKeyDown(int keyCode, KeyEvent event)
//	{
//		// FIXME:
//		if (keyCode == KeyEvent.KEYCODE_FOCUS || keyCode == KeyEvent.KEYCODE_CAMERA)
//		{
//			// Handle these events so they don't launch the Camera app
//			return true;
//		}
//		return getActivity().onKeyDown(keyCode, event);
//	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
	{
        inflater.inflate(R.menu.context_qrcode, menu);    
		super.onCreateOptionsMenu(menu,inflater);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.context_qrcode_flash:
				mCache.mFlashState=!mCache.mFlashState;
				FlashlightManager.setFlashlight(mCache.mFlashState);
				return true;
				
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	public void surfaceCreated(SurfaceHolder holder)
	{
		if (!mHasSurface)
		{
			mHasSurface = true;
			if (!NO_CAMERA)
			{
				int[] location=new int[4];;
				mViewfinderView.getLocationOnScreen(location);
				Rect locatInScreen=new Rect(location[0],location[1],location[2],location[3]);
				locatInScreen.right=locatInScreen.left+mViewfinderView.getWidth();
				locatInScreen.bottom=locatInScreen.top+mViewfinderView.getHeight();
				initCamera(holder,getRotation());
				CameraManager cameraManager=CameraManager.get();
				Point size=cameraManager.getSize();
				Rect rect=new Rect(
						(locatInScreen.width()-size.x)/2,
						(locatInScreen.height()-size.y)/2,
						(locatInScreen.width()+size.x)/2,
						(locatInScreen.height()+size.y)/2);
//				Rect rect=locatInScreen;
rect=new Rect(10,10,200,200);				
				CameraManager.get().setManualFramingPos(rect);
			}
		}
	}

	public void surfaceDestroyed(SurfaceHolder holder)
	{
		mHasSurface = false;
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
	{

	}

	/**
	 * A valid qrcode has been found, so give an indication of success and show the results.
	 * 
	 * @param rawResult
	 *            The contents of the barcode.
	 * @param barcode
	 *            A greyscale bitmap of the camera data which was decoded.
	 */
	@Override
	public void handleDecode(Result rawResult, Bitmap barcode)
	{
		if (I) Log.i(TAG_CONNECT,"handle valide decode "+rawResult);
		mCache.mInactivityTimer.onActivity();
		mCache.mLastResult = rawResult;
		mViewfinderView.drawResultBitmap(barcode);
		mCache.mBeepManager.playBeepSoundAndVibrate();
		drawResultPoints(barcode, rawResult);
	}
	@Override
	public void handlePrevious(Result rawResult, Bitmap barcode)
	{
		if (I) Log.i(TAG_CONNECT,"handle valide decode "+rawResult);
		mCache.mLastResult = rawResult;
		mViewfinderView.drawPreviousBitmap(barcode);
	}

	/**
	 * Superimpose a line for 1D or dots for 2D to highlight the key features of the barcode.
	 * 
	 * @param barcode
	 *            A bitmap of the captured image.
	 * @param rawResult
	 *            The decoded results which contains the points to draw.
	 */
	private void drawResultPoints(Bitmap barcode, Result rawResult)
	{
		ResultPoint[] points = rawResult.getResultPoints();
		if (points != null && points.length > 0)
		{
			Canvas canvas = new Canvas(barcode);
			Paint paint = new Paint();
			paint.setColor(getResources().getColor(R.color.qrcode_result_image_border));
			paint.setStrokeWidth(3.0f);
			paint.setStyle(Paint.Style.STROKE);
			Rect border = new Rect(2, 2, barcode.getWidth() - 2, barcode.getHeight() - 2);
			canvas.drawRect(border, paint);

			paint.setColor(getResources().getColor(R.color.qrcode_result_points));
			if (points.length == 2)
			{
				paint.setStrokeWidth(4.0f);
				drawLine(canvas, paint, points[0], points[1]);
			}
			else if (points.length == 4
					&& (rawResult.getBarcodeFormat().equals(BarcodeFormat.UPC_A) || rawResult
							.getBarcodeFormat().equals(BarcodeFormat.EAN_13)))
			{
				// Hacky special case -- draw two lines, for the barcode and metadata
				drawLine(canvas, paint, points[0], points[1]);
				drawLine(canvas, paint, points[2], points[3]);
			}
			else
			{
				paint.setStrokeWidth(10.0f);
				for (ResultPoint point : points)
				{
					canvas.drawPoint(point.getX(), point.getY(), paint);
				}
			}
		}
	}

	private static void drawLine(Canvas canvas, Paint paint, ResultPoint a, ResultPoint b)
	{
		canvas.drawLine(a.getX(), a.getY(), b.getX(), b.getY(), paint);
	}

	private void initCamera(SurfaceHolder surfaceHolder,int rotation)
	{
		try
		{
			CameraManager.get().openDriver(surfaceHolder,rotation);
			// Creating the handler starts the preview, which can also throw a RuntimeException.
			if (mCache.mHandler == null)
			{
				mCache.mHandler = new CaptureHandler(this);
			}
		}
		catch (IOException ioe)
		{
			if (W) Log.w(TAG_CONNECT, ioe);
			displayFrameworkBugMessageAndExit();
		}
		catch (RuntimeException e)
		{
			// Barcode Scanner has seen crashes in the wild of this variety:
			// java.?lang.?RuntimeException: Fail to connect to camera service
			if (W) Log.w(TAG_CONNECT, "Unexpected error initializating camera", e);
			displayFrameworkBugMessageAndExit();
		}
	}

	private void displayFrameworkBugMessageAndExit()
	{
		// TODO: Fragment dialog
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(getString(R.string.app_name));
		builder.setMessage(getString(R.string.qrcode_msg_camera_framework_bug));
		builder.setPositiveButton(R.string.button_ok, new FinishListener(getActivity()));
		builder.setOnCancelListener(new FinishListener(getActivity()));
		builder.show();
	}

	private void resetStatusView()
	{
		mStatusView.setText(R.string.msg_default_status);
		mStatusView.setVisibility(View.VISIBLE);
		mViewfinderView.setVisibility(View.VISIBLE);
		mCache.mLastResult = null;
	}

	public void drawViewfinder()
	{
		mViewfinderView.drawViewfinder();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean onKeyLongPress(int keyCode, KeyEvent event)
	{
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean onKeyMultiple(int keyCode, int count, KeyEvent event)
	{
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event)
	{
		// TODO Auto-generated method stub
		return false;
	}

}
