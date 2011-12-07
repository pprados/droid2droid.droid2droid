/*
 * Copyright (C) 2008 ZXing authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.remoteandroid.test;

import static org.remoteandroid.Constants.*;
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.Constants.TAG_CONNECT;
import static org.remoteandroid.internal.Constants.I;
import static org.remoteandroid.internal.Constants.W;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;

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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.view.animation.RotateAnimation;
import android.widget.ImageButton;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.ResultMetadataType;
import com.google.zxing.ResultPoint;

/**
 * The barcode reader activity itself. This is loosely based on the
 * CameraPreview example included in the Android SDK.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Sean Owen
 * @author Yohann Melo
 */
public final class CaptureQRCodeActivity extends Activity implements
		SurfaceHolder.Callback, KeyEvent.Callback, Wrapper
{
	private static final boolean NO_CAMERA = false;

	private static final Set<ResultMetadataType> DISPLAYABLE_METADATA_TYPES;
	static
	{
		DISPLAYABLE_METADATA_TYPES = new HashSet<ResultMetadataType>(5);
		DISPLAYABLE_METADATA_TYPES.add(ResultMetadataType.ISSUE_NUMBER);
		DISPLAYABLE_METADATA_TYPES.add(ResultMetadataType.SUGGESTED_PRICE);
		DISPLAYABLE_METADATA_TYPES
				.add(ResultMetadataType.ERROR_CORRECTION_LEVEL);
		DISPLAYABLE_METADATA_TYPES.add(ResultMetadataType.POSSIBLE_COUNTRY);
	}

	static class Cache
	{
		private CaptureHandler mHandler;

		private boolean mFlashState;

		private InactivityTimer mInactivityTimer;

		private BeepManager mBeepManager;
	}

	Cache mCache;

	private ViewfinderView mViewfinderView;

	private TextView mStatusView;

	private boolean mHasSurface;

	@Override
	public ViewfinderView getViewfinderView()
	{
		return mViewfinderView;
	}

	public Handler getHandler()
	{
		return mCache.mHandler;
	}

	private void setParams()
	{
		//CameraManager.camera_orientation = getResources().getConfiguration().orientation;
		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(
			metrics);
		CameraManager.density = metrics.density;

	}

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);
		Window window = getWindow();
		window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.test_qrcode_capture);

		setParams();

		final ImageButton btn = (ImageButton) findViewById(R.id.connect_qrcode_btn_camera);
		//if the SDK version is FROYO or better, we can switch front/back camera
		if (Compatibility.VERSION_SDK_INT >= Compatibility.VERSION_FROYO)
		{
			new Runnable()
			{
				@Override
				public void run()
				{
					btn.setOnClickListener(new OnClickListener()
					{

						@Override
						public void onClick(View v)
						{
							//switch to next camera
							CameraManager.camera = (CameraManager.camera + 1)
									% Camera.getNumberOfCameras();
							onPause();
							setParams();
							onResume();
						}
					});
					
				}
			}.run();
					}
//		else
//			btn.setClickable(false);
		
		mViewfinderView = (ViewfinderView) findViewById(R.id.viewfinder_view);
		mStatusView = (TextView) findViewById(R.id.status_view);

//		  RotateAnimation ranim = (RotateAnimation)AnimationUtils.loadAnimation(this, R.anim.rotate_anim);
//		  ranim.setFillAfter(true); //For the textview to remain at the same place after the rotation
//		  mStatusView.setAnimation(ranim);
//		  
		mCache = (Cache) getLastNonConfigurationInstance();
		mHasSurface = false;
		View v = findViewById(R.id.main_layout); // FIXME: remove id
		//int[] location = new int[2];
		
		//mViewfinderView.getLocationOnScreen(location);
		Rect r = new Rect();
		v.getLocalVisibleRect(r);
		if (mCache == null)
		{
			mCache = new Cache();
			mCache.mHandler = null;
			mCache.mInactivityTimer = new InactivityTimer(this);
			mCache.mBeepManager = new BeepManager(this);
			if (!NO_CAMERA)
				CameraManager.init(getApplication());
		}
		else
		{
			mCache.mInactivityTimer.setActivity(this);
			mCache.mBeepManager.setActivity(this);
			if (mCache.mHandler != null)
				mCache.mHandler.setWrapper(this);
		}
		//setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
	}

	@Override
	public Object onRetainNonConfigurationInstance()
	{

		return mCache;
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus)
	{
		super.onWindowFocusChanged(hasFocus);
		View v = findViewById(R.id.main_layout); // FIXME: remove id
		int[] location = new int[2];
		mViewfinderView.getLocationOnScreen(location);
		Rect r = new Rect();
		v.getLocalVisibleRect(r);
		if (D) Log.d(
			TAG_QRCODE, "rect=" + r);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		resetStatusView();
		if (D) Log.d(
			TAG_QRCODE, "resume");
		SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		if (mHasSurface)
		{
			// The activity was paused but not stopped, so the surface still
			// exists. Therefore
			// surfaceCreated() won't be called, so init the camera here.

			initCamera(
				surfaceHolder, getRotation());
		}
		else
		{
			// Install the callback and wait for surfaceCreated() to init the
			// camera.
			surfaceHolder.addCallback(this);
			surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		}

		mCache.mBeepManager.updatePrefs();
		mCache.mInactivityTimer.onResume();
	}
	
	
	
	private int getRotation()
	{
		if (Compatibility.VERSION_SDK_INT >= Compatibility.VERSION_FROYO){
		
			Integer job = new Callable<Integer>()
			{
				public Integer call() 
				{
					return getWindowManager().getDefaultDisplay().getRotation();
				}
			
				
			}.call();
			return job;
		}
		else
			if(getWindowManager().getDefaultDisplay().getWidth() < getWindowManager().getDefaultDisplay().getHeight())
				return Surface.ROTATION_0;
			else
				return Surface.ROTATION_90;
			//return getResources().getConfiguration().orientation;
			//return getWindowManager().getDefaultDisplay().getOrientation();
		
	}

	@Override
	protected void onPause()
	{
		super.onPause();
		if (I)
			Log.i(
				TAG_QRCODE, "onPause...");
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
	protected void onDestroy()
	{
		
		if (I)
			Log.i(
				TAG_QRCODE, "onDestroy...");
		mCache.mInactivityTimer.shutdown();
		this.mViewfinderView.detachHandler();
		super.onDestroy();
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig)
	{
		super.onConfigurationChanged(newConfig);
		if (D) Log.d(
			TAG_QRCODE, "onconfigchanged");
		CameraManager.init(this); // FIXME: Attention fuite mémoire sur le this !
		CameraManager.get().closeDriver();
		SurfaceView surfaceView = (SurfaceView) findViewById(R.id.preview_view);
		SurfaceHolder surfaceHolder = surfaceView.getHolder();
		CameraManager.get().closeDriver();
		initCamera(
			surfaceHolder, getRotation());
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		if (keyCode == KeyEvent.KEYCODE_FOCUS
				|| keyCode == KeyEvent.KEYCODE_CAMERA)
		{
			// Handle these events so they don't launch the Camera app
			return true;
		}
		return super.onKeyDown(
			keyCode, event);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(
			R.menu.context_qrcode, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case R.id.context_qrcode_flash:
				mCache.mFlashState = !mCache.mFlashState;
				FlashlightManager.setFlashlight(mCache.mFlashState);
				return true;
			case R.id.context_qrcode_camera:
				if (Compatibility.VERSION_SDK_INT >= Compatibility.VERSION_FROYO){
					new Runnable(){
						@Override
						public void run() {
							CameraManager.camera = (CameraManager.camera + 1)
									% Camera.getNumberOfCameras();
						}
					}.run();
				}
					
					
				this.onPause();
				this.onResume();
				return true;
			case  R.id.context_qrcode_debugitem:
				
			default:
				return super.onOptionsItemSelected(item);
		}
	}
//
//	static final int CAMERA_PARAMETERS_REQUEST = 1110;
//	protected void onActivityResult(int requestCode, int resultCode,
//            Intent data) {
//        if (requestCode == CAMERA_PARAMETERS_REQUEST) {
//            if (resultCode == RESULT_OK) {
//            	Bundle bundle = data.getExtras();
//            	int zoom = bundle.getInt("zoom", 1);
//            	String flashMode = bundle.getString("flashmode");
//            	
//            	CameraManager.get().setParameters(zoom,flashMode);
//            }
//        }
//    }

	
	public void surfaceCreated(SurfaceHolder holder)
	{
		if (D) Log.d(
			TAG_QRCODE, "surface created");
		if (!mHasSurface)
		{	
			mHasSurface = true;
			if (!NO_CAMERA)
				initCamera(
					holder, getRotation());
			
		}
	}

	public void surfaceDestroyed(SurfaceHolder holder)
	{
		mHasSurface = false;
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height)
	{
		if (D) Log.d(
			TAG_QRCODE, "surface changed");
		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(
			metrics);
		CameraManager.density = metrics.density;
//		CameraManager.get().setScreenResolutionValues(
//			new Point(width, height));
	}

	/**
	 * A valid qrcode has been found, so give an indication of success and show
	 * the results.
	 * 
	 * @param rawResult The contents of the barcode.
	 * @param barcode A greyscale bitmap of the camera data which was decoded.
	 */
	@Override
	public void handleDecode(Result rawResult, Bitmap barcode)
	{
		if (I)
			Log.i(
				TAG_QRCODE, "handle valide decode " + rawResult);
		mCache.mInactivityTimer.onActivity();
		ViewfinderView.CURRENT_POINT_OPACITY = 0xFF;
		mViewfinderView.drawResultBitmap(barcode);
		mViewfinderView.found = true;
		mViewfinderView.invalidate();
		mCache.mBeepManager.playBeepSoundAndVibrate();
		drawResultPoints(
			barcode, rawResult);
	}

	@Override
	public void handlePrevious(Result rawResult, Bitmap barcode)
	{
		if (I)
			Log.i(
				TAG_QRCODE, "handle valide decode " + rawResult);
		mViewfinderView.drawPreviousBitmap(barcode);
	}

	/**
	 * Superimpose a line for 1D or dots for 2D to highlight the key features of
	 * the barcode.
	 * 
	 * @param barcode A bitmap of the captured image.
	 * @param rawResult The decoded results which contains the points to draw.
	 */
	private void drawResultPoints(Bitmap barcode, Result rawResult)
	{
		ResultPoint[] points = rawResult.getResultPoints();
		if (points != null && points.length > 0)
		{
			Canvas canvas = new Canvas(barcode);
			Paint paint = new Paint();
			paint.setColor(getResources().getColor(
				R.color.qrcode_result_image_border));
			paint.setStrokeWidth(3.0f);
			paint.setStyle(Paint.Style.STROKE);
			Rect border = new Rect(2, 2, barcode.getWidth() - 2,
					barcode.getHeight() - 2);
			canvas.drawRect(
				border, paint);

			paint.setColor(getResources().getColor(
				R.color.qrcode_result_points));
			if (points.length == 2)
			{
				paint.setStrokeWidth(4.0f);
				drawLine(
					canvas, paint, points[0], points[1]);
			}
			else if (points.length == 4
					&& (rawResult.getBarcodeFormat().equals(
						BarcodeFormat.UPC_A) || rawResult.getBarcodeFormat()
							.equals(
								BarcodeFormat.EAN_13)))
			{
				// Hacky special case -- draw two lines, for the barcode and
				// metadata
				drawLine(
					canvas, paint, points[0], points[1]);
				drawLine(
					canvas, paint, points[2], points[3]);
			}
			else
			{
				paint.setStrokeWidth(10.0f);
				for (ResultPoint point : points)
				{
					paint.setStrokeWidth(10.0f);
					paint.setColor(Color.RED);
					Point p = new Point();
					p.x = (int) point.getX();
					p.y = (int) point.getY();
					p = this.scaledRotatePoint(p, CameraManager.get().getRotation() , canvas.getWidth(), canvas.getHeight());
					canvas.drawPoint(
						p.x, p.y, paint);
				}
			}
		}
	}
	
	private static void drawLine(Canvas canvas, Paint paint, ResultPoint a,
			ResultPoint b)
	{
		canvas.drawLine(
			a.getX(), a.getY(), b.getX(), b.getY(), paint);
	}

	private void initCamera(SurfaceHolder surfaceHolder, int rotation)
	{
		try
		{
			CameraManager.get().openDriver(
				surfaceHolder, rotation);
			// Creating the handler starts the preview, which can also throw a
			// RuntimeException.
			if (mCache.mHandler == null)
			{
				mCache.mHandler = new CaptureHandler(this);
			}
			CameraManager.get().setScreenResolutionValues(
					new Point(mViewfinderView.getWidth(), mViewfinderView
							.getHeight()));
		}
		catch (IOException ioe)
		{
			if (W)
				Log.w(
					TAG_QRCODE, ioe);
			displayFrameworkBugMessageAndExit();
		}
		catch (RuntimeException e)
		{
			// Barcode Scanner has seen crashes in the wild of this variety:
			// java.?lang.?RuntimeException: Fail to connect to camera service
			if (W)
				Log.w(
					TAG_QRCODE, "Unexpected error initializating camera", e);
			displayFrameworkBugMessageAndExit();
		}
	}

	private void displayFrameworkBugMessageAndExit()
	{
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(getString(R.string.app_name));
		builder.setMessage(getString(R.string.qrcode_msg_camera_framework_bug));
		builder.setPositiveButton(
			R.string.button_ok, new FinishListener(this));
		builder.setOnCancelListener(new FinishListener(this));
		builder.show();
	}

	private void resetStatusView()
	{
		mStatusView.setText(R.string.msg_default_status);
		mStatusView.setVisibility(View.VISIBLE);
		mViewfinderView.setVisibility(View.VISIBLE);
	}

	@Override
	public void drawViewfinder()
	{
		mViewfinderView.drawViewfinder();
	}


	public Point scaledRotatePoint(Point p, int orientation, int canvasW, int canvasH)
	{
		Point tmp = new Point();
		if (D)
			Log.d(
				TAG_QRCODE, "rotating result points to match the device orientation");

//		if (orientation == CameraManager.sOrientation[0]){
//			tmp.x = canvasW - p.y;
//			tmp.y = p.x;
//		}
//		else
//			if (orientation == CameraManager.sOrientation[1]){
//				tmp.x = p.x;
//				tmp.y = p.y;
//			}
//			else
//				if (orientation == CameraManager.sOrientation[2]){
//					tmp.x = p.y;
//					tmp.y = canvasH - p.x;
//				}
//				else
//					if (orientation == CameraManager.sOrientation[3]){
//						tmp.x = canvasW - p.x;
//						tmp.y = canvasH - p.y; 
//					}
		if(CameraManager.get().camera == Camera.CameraInfo.CAMERA_FACING_BACK)
			switch(orientation){
				case 90:
					tmp.x = canvasW - p.y;
					tmp.y = p.x;
				break;
				case 0:
					tmp.x = p.x;
					tmp.y = p.y;
					break;
				case 270:
					tmp.x = p.y;
					tmp.y = canvasH - p.x;
				break;
				case 180:
					tmp.x = canvasW - p.x;
					tmp.y = canvasH - p.y; 
				break;
			}
		else
			switch(orientation){
			case 0:
				tmp.x = p.x;
				tmp.y = p.y;
			break;
			case 90:
				tmp.x = canvasW - p.y;
				tmp.y = canvasH - p.x;
				break;
			case 180:
				tmp.x = p.x;
				tmp.y = canvasH - p.y;
			break;
			case 270:
				tmp.x = p.x;
				tmp.y = p.y; 
			break;
		}
		return tmp;
	}

}