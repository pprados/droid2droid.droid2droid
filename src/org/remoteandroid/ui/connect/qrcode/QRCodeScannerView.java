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

package org.remoteandroid.ui.connect.qrcode;

import static android.hardware.Camera.Parameters.ANTIBANDING_60HZ;
import static android.hardware.Camera.Parameters.ANTIBANDING_AUTO;
import static android.hardware.Camera.Parameters.ANTIBANDING_OFF;
import static android.hardware.Camera.Parameters.EFFECT_MONO;
import static android.hardware.Camera.Parameters.EFFECT_NONE;
import static android.hardware.Camera.Parameters.FLASH_MODE_AUTO;
import static android.hardware.Camera.Parameters.FLASH_MODE_OFF;
import static android.hardware.Camera.Parameters.FOCUS_MODE_AUTO;
import static android.hardware.Camera.Parameters.FOCUS_MODE_MACRO;
import static android.hardware.Camera.Parameters.SCENE_MODE_ACTION;
import static android.hardware.Camera.Parameters.SCENE_MODE_AUTO;
import static android.hardware.Camera.Parameters.SCENE_MODE_BARCODE;
import static android.hardware.Camera.Parameters.WHITE_BALANCE_AUTO;
import static android.hardware.Camera.Parameters.WHITE_BALANCE_FLUORESCENT;
import static org.remoteandroid.Constants.QRCODE_ALPHA;
import static org.remoteandroid.Constants.QRCODE_ANIMATION_DELAY;
import static org.remoteandroid.Constants.QRCODE_MINIMAL_CAMERA_RESOLUTION;
import static org.remoteandroid.Constants.QRCODE_PERCENT_WIDTH_LANDSCAPE;
import static org.remoteandroid.Constants.QRCODE_PERCENT_WIDTH_PORTRAIT;
import static org.remoteandroid.Constants.QRCODE_SHOW_CURRENT_DECODE;
import static org.remoteandroid.Constants.QRCODE_VIBRATE_DURATION;
import static org.remoteandroid.Constants.TAG_QRCODE;
import static org.remoteandroid.internal.Constants.D;
import static org.remoteandroid.internal.Constants.E;
import static org.remoteandroid.internal.Constants.I;
import static org.remoteandroid.internal.Constants.V;
import static org.remoteandroid.internal.Constants.W;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.remoteandroid.R;
import org.remoteandroid.internal.Compatibility;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.SystemClock;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder
 * rectangle and partial transparency outside it, as well as the laser scanner
 * animation and result points.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Yohann Melo
 */
public final class QRCodeScannerView extends ViewGroup
implements SurfaceHolder.Callback
{
	/**
	 * 
	 * Call-back
	 *
	 */
	public interface QRCodeResult
	{
		void onQRCode(Result rawResult);
	}

	public static final int msg_auto_focus=1;
	public static final int msg_request_frame=2;
	public static final int msg_show_frame=3;
	public static final int msg_decode=4;
	public static final int msg_decode_succeeded=5;
	public static final int msg_decode_failed=6;
	
	// View with surface for camera previous
	private SurfaceView mSurfaceView;
	// The surface holder
	private SurfaceHolder mHolder;
	// View overlow of camera previous
	private AnimView mAnimView;

	// The selected camera size
	private Size mCameraSize;
	/*private*/ Point mPreviousSize;
	
	/*private*/ Camera mCamera;
	private int mCameraId;
	/*private*/ int mRotation;
	private boolean mOptimizeSize;

	private Rect mCameraRect;
	private Rect mRect = new Rect(); // Working rect
	/*package*/Rect mFramingRectInPreview;
	
	private QRCodeResult mCallBack;
	
	private static final int CURRENT_POINT_OPACITY = 0xFF;
	private static final int MAX_RESULT_POINTS = 20; // TODO:QRCode result points

	private final Paint mPaint=new Paint();

	/*private*/ CaptureHandler mCaptureHandler;
	
	private Bitmap mResultBitmap;

	private Bitmap mPreviousBitmap;

	private final int mMaskColor;

	private final int mResultColor;

	private final int mFrameColor;

	private final int mLaserColor;

	private final int mResultPointColor;

	private int mAnimPos;

	private List<ResultPoint> mPossibleResultPoints=new ArrayList<ResultPoint>(5);

	private List<ResultPoint> mLastPossibleResultPoints;

	private Long mStartTime = 0L;

	private BeepManager mBeepManager;
	
	private Runnable mUpdateTimeTask = new Runnable()
	{
		public void run()
		{
			++mAnimPos;
			mStartTime = SystemClock.uptimeMillis();
			mCaptureHandler.postAtTime(this, mStartTime + QRCODE_ANIMATION_DELAY);
		}
	};

	private static final int[] sSurfaceRotationToCameraDegreeForLandscapeDefault=
		{
			0, 		// Surface.ROTATION_0
			270,	// Surface.ROTATION_90
			180,	// Surface.ROTATION_180
			90,		// Surface.ROTATION_270
			
		};
	private static final int[] sSurfaceRotationToCameraDegreeForPortraitDefaut=
		{
			90,		// Surface.ROTATION_0
			0, 		// Surface.ROTATION_90
			270,	// Surface.ROTATION_180
			180,	// Surface.ROTATION_270
		};
	
	private class AnimView extends View
	{
		AnimView(Context context)
		{
			super(context);
		}
		@Override
		protected void onDraw(Canvas canvas) 
		{
			Rect frame = getFramingRectInPreview();
			if (frame == null)
			{
				return;
			}

			final int width = canvas.getWidth();
			final int height = canvas.getHeight();
			
			// Draw the exterior (i.e. outside the framing rect) darkened
			mPaint.setStyle(Style.FILL);
			mPaint.setStrokeWidth(1);
			mPaint.setColor(mResultBitmap != null ? mResultColor : mMaskColor);
			canvas.drawRect(0, 0, width, frame.top, mPaint);
			canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, mPaint);
			canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, mPaint);
			canvas.drawRect(0, frame.bottom + 1, width, height, mPaint);
			 
			 
			if (mResultBitmap != null)
			{
				// Draw the opaque result bitmap over the scanning rectangle
				mPaint.setAlpha(CURRENT_POINT_OPACITY);
				canvas.drawBitmap(mResultBitmap, null, frame, mPaint);
			}
			else
			{
				if (QRCODE_SHOW_CURRENT_DECODE)
				{
					if (mPreviousBitmap != null)
					{
						// Draw the opaque result bitmap over the scanning rectangle
						mPaint.setAlpha(CURRENT_POINT_OPACITY);
						canvas.drawBitmap(mPreviousBitmap, null, frame, mPaint);
					}
				}
				// Draw a red "laser scanner" line through the middle to show
				// decoding is active
				mPaint.setColor(mLaserColor);
				mPaint.setAlpha(QRCODE_ALPHA);
				mPaint.setStyle(Style.STROKE);
				mPaint.setStrokeWidth(5);

				int animPos = mAnimPos % 8;
				int w = frame.width();
				int h = frame.height();
				int ww = w / 20 * (animPos);
				int hh = h / 20 * (animPos);
				mRect.set(frame.left + ww, frame.top + hh, frame.right - ww, frame.bottom- hh);
				canvas.drawRect(mRect, mPaint);

				int boxLeft = mRect.width() / 8;
				int boxTop = mRect.height() / 8;
				int widthBox = boxLeft + (mRect.width() / 8);
				int heightBox = boxTop + (mRect.height() / 8);
				int animPos2 = (mAnimPos / 8) % 4;
				if (animPos2 != 0)
					canvas.drawRect(mRect.left + boxLeft, mRect.top + boxTop, mRect.left + widthBox, mRect.top + heightBox, mPaint);
				if (animPos2 != 1)
					canvas.drawRect(mRect.left + boxLeft, mRect.bottom - boxTop, mRect.left	+ widthBox, mRect.bottom - heightBox, mPaint);
				if (animPos2 != 2)
					canvas.drawRect(mRect.right - boxLeft, mRect.bottom - boxTop, mRect.right - widthBox, mRect.bottom - heightBox, mPaint);
				if (animPos2 != 3)
					canvas.drawRect(mRect.right - boxLeft, mRect.top + boxTop, mRect.right - widthBox, mRect.top + heightBox, mPaint);

				// Draw a two pixel solid black border inside the framing rect
				mPaint.setColor(mFrameColor);
				if (!QRCODE_SHOW_CURRENT_DECODE)
				{
					canvas.drawRect(
						frame.left, frame.top, frame.right + 1, frame.top + 2,
						mPaint);
					canvas.drawRect(
						frame.left, frame.top + 2, frame.left + 2,
						frame.bottom - 1, mPaint);
					canvas.drawRect(
						frame.right - 1, frame.top, frame.right + 1,
						frame.bottom - 1, mPaint);
					canvas.drawRect(
						frame.left, frame.bottom - 1, frame.right + 1,
						frame.bottom + 1, mPaint);
				}

				Rect previewFrame = frame;
				float scaleX = frame.width() / (float) previewFrame.width();
				float scaleY = frame.height() / (float) previewFrame.height();

				List<ResultPoint> currentPossible = mPossibleResultPoints;
				List<ResultPoint> currentLast = mLastPossibleResultPoints;
				if (currentPossible.isEmpty())
				{
					mLastPossibleResultPoints = null;
				}
				else
				{
					mPossibleResultPoints = new ArrayList<ResultPoint>(5);
					mLastPossibleResultPoints = currentPossible;
					mPaint.setAlpha(CURRENT_POINT_OPACITY);
					mPaint.setColor(mResultPointColor);
					synchronized (currentPossible)
					{
						for (ResultPoint point : currentPossible)
						{			mAnimView.invalidate();

							Point p = new Point();
							p.x = (int) point.getX();
							p.y = (int) point.getY();
							p = scaledRotatePoint(p, mRotation , previewFrame.width(), previewFrame.height());
							canvas.drawCircle(
								frame.left + (int) (p.x * scaleX),
								frame.top + (int) (p.y * scaleY), 6.0f,
								mPaint);
						}
					}
				}
				if (currentLast != null)
				{
					mPaint.setAlpha(CURRENT_POINT_OPACITY / 2);
					mPaint.setColor(mResultPointColor);
					synchronized (currentLast)
					{
						for (ResultPoint point : currentLast)
						{
							canvas.drawCircle(
								frame.left + (int) (point.getX() * scaleX),
								frame.top + (int) (point.getY() * scaleY), 3.0f,
								mPaint);
						}
					}
				}

				// Request another update at the animation interval, but only repaint the laser line,
				// not the entire viewfinder mask.
				if (!QRCODE_SHOW_CURRENT_DECODE)
					postInvalidateDelayed(
						QRCODE_ANIMATION_DELAY, frame.left, frame.top, frame.right,
						frame.bottom);
			}
			
		}
	}
	
	// This constructor is used when the class is built from an XML resource.
	@SuppressWarnings("deprecation")
	public QRCodeScannerView(Context context, AttributeSet attrs)
	{
		super(context, attrs);

		// Init camera previous
		mSurfaceView = new SurfaceView(context);
		addView(mSurfaceView);
		mAnimView=new AnimView(context);
		addView(mAnimView);
		mCaptureHandler = new CaptureHandler(getContext(),this);

		// Install a SurfaceHolder.Callback so we get notified when the
		// underlying surface is created and destroyed.
		mHolder = mSurfaceView.getHolder();
		mHolder.addCallback(this);
		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		// Initialize these once for performance rather than calling them every
		// time in onDraw().
		Resources resources = getResources();
		mMaskColor = resources.getColor(R.color.qrcode_mask);
		mResultColor = resources.getColor(R.color.qrcode_result_color);
		mFrameColor = resources.getColor(R.color.qrcode_frame_color);
		mLaserColor = resources.getColor(R.color.qrcode_laser);
		mResultPointColor = resources.getColor(R.color.qrcode_possible_result_points);
		mLastPossibleResultPoints = null;
		if (mStartTime == 0L)
		{
			mStartTime = System.currentTimeMillis();
			mCaptureHandler.removeCallbacks(mUpdateTimeTask);
			mCaptureHandler.postDelayed(
				mUpdateTimeTask, 100);
		}
		mBeepManager=new BeepManager(getContext());
	}

	/**
	 * Set callback on qrcode is decoded.
	 * 
	 * @param callback The call back.
	 */
	public final void setOnResult(QRCodeResult callback)
	{
		mCallBack=callback;
	}
	
	/**
	 * Optimize the previous size for camera. Else, use the optimized resolution.
	 * 
	 * @param optimizeSize true or false
	 */
	public final void setOptimizeSize(boolean optimizeSize)
	{
		mOptimizeSize=optimizeSize;
	}
	
	public final boolean isOptimizeSize()
	{
		return mOptimizeSize;
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	public void invalidate()
	{
		super.invalidate();
		if (mAnimView!=null) mAnimView.invalidate();
	}
	
	/**
	 * {@inheritDoc}
	 */
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
	{
		// We purposely disregard child measurements because act as a
		// wrapper to a SurfaceView that centers the camera preview instead
		// of stretching it.
		int width = resolveSize(getSuggestedMinimumWidth(), widthMeasureSpec);
		int height = resolveSize(getSuggestedMinimumHeight(), heightMeasureSpec);
		setMeasuredDimension(width, height);
		if (mCamera!=null && mCameraSize==null)
		{
			mCameraSize = getOptimalCameraPreviewSize(mCamera.getParameters().getSupportedPreviewSizes(), width, height);
			if (V) Log.v(TAG_QRCODE,"camera size="+mCameraSize.width+","+mCameraSize.height);
		}
	}

	private final int getWindowRotation()
	{
		if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.FROYO)
			return ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
		else
			return ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getOrientation();
	}
	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b)
	{
		// The onConfigurationChanged is not allways invoked.
		setRotation(getWindowRotation());
		if (changed && getChildCount() > 0)
		{
			calcCameraRect(l, t, r, b);
			mSurfaceView.layout(mCameraRect.left,mCameraRect.top,mCameraRect.width(),mCameraRect.height());
			if (mAnimView!=null)
				mAnimView.layout(mCameraRect.left,mCameraRect.top,mCameraRect.width(),mCameraRect.height());
		}
	}

	private void calcCameraRect(int l, int t, int r, int b)
	{
		final int width = r - l;
		final int height = b - t;

		int previewWidth = width;
		int previewHeight = height;
		if (mOptimizeSize && mCameraSize != null)
		{
			previewWidth = mCameraSize.width;
			previewHeight = mCameraSize.height;
		}
		// Center the child SurfaceView within the parent.
		if (width * previewHeight > height * previewWidth)
		{
			final int scaledChildWidth = previewWidth * height / previewHeight;
			mCameraRect=new Rect((width - scaledChildWidth) / 2, 0, (width + scaledChildWidth) / 2, height);
		}
		else
		{
			final int scaledChildHeight = previewHeight * width / previewWidth;
			mCameraRect=new Rect(0, (height - scaledChildHeight) / 2, width, (height + scaledChildHeight) / 2);
		}
	}
	
	/*package*/ void drawViewfinder()
	{
		if (!QRCODE_SHOW_CURRENT_DECODE)
		{
			mResultBitmap = null;
			invalidate();
		}
	}

	/**
	 * Draw a bitmap with the result points highlighted instead of the live
	 * scanning display.
	 * 
	 * @param barcode An image of the decoded barcode.
	 */
	private void drawResultBitmap(Bitmap barcode)
	{
		if (D) Log.d(TAG_QRCODE,"Result w:" + barcode.getWidth() + " h:" + barcode.getHeight());
		mResultBitmap = barcode;
		invalidate();
	}

	private void drawPreviousBitmap(Bitmap barcode)
	{
		mPreviousBitmap = barcode;
		invalidate();
	}

	// TODO: Show result points ?
	/*package*/ void addPossibleResultPoint(ResultPoint point)
	{
		List<ResultPoint> points = mPossibleResultPoints;
		synchronized (point)
		{
			points.add(point);
			int size = points.size();
			if (size > MAX_RESULT_POINTS)
			{
				// trim it
				points.subList(
					0, size - MAX_RESULT_POINTS / 2).clear();
			}
		}
	}
	
	
	private Point scaledRotatePoint(Point p, int rotation, int canvasW, int canvasH)
	{
		Point tmp = new Point();
		if (D) Log.d(TAG_QRCODE, "rotating result points to match the device orientation (rotation: " + rotation + "°)");
		switch(rotation){
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
		return tmp;
	}

	/*package*/ void handlePrevious(Result rawResult, Bitmap barcode)
	{
		drawPreviousBitmap(barcode);
	}
	
	/**
	 * A valid qrcode has been found, so give an indication of success and show
	 * the results.
	 * 
	 * @param rawResult The contents of the barcode.
	 * @param barcode A greyscale bitmap of the camera data which was decoded.
	 */
	/*package*/ void handleDecode(Result rawResult, Bitmap barcode)
	{
		drawResultBitmap(barcode);
		drawResultPoints(barcode, rawResult);
		mBeepManager.playBeepSoundAndVibrate();
		if (mCallBack!=null)
		{
			mCallBack.onQRCode(rawResult);
		}
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
			else if (points.length == 4 && (rawResult.getBarcodeFormat().equals(
				BarcodeFormat.UPC_A) || rawResult.getBarcodeFormat().equals(
				BarcodeFormat.EAN_13)))
			{
				// Hacky special case -- draw two lines, for the barcode and metadata
				drawLine(canvas, paint, points[0], points[1]);
				drawLine(canvas, paint, points[2], points[3]);
			}
			else
			{
				
				for (ResultPoint point : points)
				{
					paint.setStrokeWidth(10.0f);
					paint.setColor(Color.RED);
					Point p = new Point();
					p.x = (int) point.getX();
					p.y = (int) point.getY();
					p = scaledRotatePoint(p, mRotation , canvas.getWidth(), canvas.getHeight());
					canvas.drawPoint(p.x, p.y, paint);
				}
			}
		}
	}

	private static void drawLine(Canvas canvas, Paint paint, ResultPoint a, ResultPoint b)
	{
		canvas.drawLine(a.getX(), a.getY(), b.getX(), b.getY(), paint);
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int w, int h)
	{
		if (mCamera!=null)
			mCamera.setParameters(getCameraParameters());
		
		mCaptureHandler.startScan();
		requestLayout();
	}

	private Camera.Parameters getCameraParameters()
	{
		Camera.Parameters parameters = mCamera.getParameters();
		final String focusMode = findSettableValue(parameters.getSupportedFocusModes(),
			FOCUS_MODE_AUTO,FOCUS_MODE_MACRO);
		if (focusMode != null)
			parameters.setFocusMode(focusMode);
		final String whiteBalance = findSettableValue(parameters.getSupportedWhiteBalance(),
			WHITE_BALANCE_AUTO,WHITE_BALANCE_FLUORESCENT);
		if (whiteBalance!=null)
			parameters.setWhiteBalance(whiteBalance);
		final String sceneMode = findSettableValue(parameters.getSupportedSceneModes(),
			SCENE_MODE_BARCODE,SCENE_MODE_ACTION,SCENE_MODE_AUTO);
		if (sceneMode!=null)
			parameters.setSceneMode(sceneMode);
		final String colorEffect = findSettableValue(parameters.getSupportedColorEffects(),
			EFFECT_NONE);
		if (colorEffect!=null)
			parameters.setColorEffect(colorEffect);
		final String flashMode = findSettableValue(parameters.getSupportedFlashModes(),
			FLASH_MODE_AUTO,FLASH_MODE_OFF);
		if (flashMode!=null)
			parameters.setFlashMode(flashMode);
		final String antiBandingMode = findSettableValue(parameters.getSupportedAntibanding(),
			ANTIBANDING_AUTO,ANTIBANDING_60HZ,ANTIBANDING_OFF);
		if (antiBandingMode!=null)
			parameters.setAntibanding(antiBandingMode);
		
		parameters.setAntibanding(ANTIBANDING_AUTO);
		if ((Build.VERSION.SDK_INT>=Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) && parameters.isVideoStabilizationSupported())
			parameters.setVideoStabilization(true);
		if (parameters.isZoomSupported())
			parameters.setZoom(0);
		if (D)
		{
			Log.d(TAG_QRCODE,"--------------");
			Log.d(TAG_QRCODE,"White balance="+parameters.getWhiteBalance());
			Log.d(TAG_QRCODE,"Scene        ="+parameters.getSceneMode());
			Log.d(TAG_QRCODE,"Color effect ="+parameters.getColorEffect());
			Log.d(TAG_QRCODE,"Flash mode   ="+parameters.getFlashMode());
			Log.d(TAG_QRCODE,"Anti-banding ="+parameters.getAntibanding());
			Log.d(TAG_QRCODE,"Zoom         ="+parameters.getZoom());
			Log.d(TAG_QRCODE,"--------------");
		}
		return parameters;
	}

	/**
	 * 
	 * @param cameraId Camera.CameraInfo.CAMERA_* or -1
	 * @throws IOException
	 */
	public final void setCamera(int cameraId) throws IOException
	{
		if (mCamera!=null)
		{
			mCamera.stopPreview();
			mCamera.cancelAutoFocus();
			mCamera.release();
			mCamera=null;
		}
		if (cameraId!=-1)
		{
			if (Compatibility.VERSION_SDK_INT >= Compatibility.VERSION_GINGERBREAD)
			{
				mCamera = Camera.open(cameraId);
			}
			else
				mCamera = Camera.open();		
			mCameraId=cameraId;
			
			if (mCamera != null)
			{
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO)
					mCamera.setDisplayOrientation(mRotation);
				mCamera.setPreviewDisplay(mHolder);
			}
		}
		requestLayout();
	}
	
	public final Camera getCamera()
	{
		return mCamera;
	}
	private int getDeviceDefaultOrientation()
	{

		Configuration cfg = getResources().getConfiguration();
		int lRotation = getWindowRotation();

		if (
				(((lRotation == Surface.ROTATION_0) ||(lRotation == Surface.ROTATION_180)) &&   
				(cfg.orientation == Configuration.ORIENTATION_LANDSCAPE)) ||
				(((lRotation == Surface.ROTATION_90) ||(lRotation == Surface.ROTATION_270)) &&    
						(cfg.orientation == Configuration.ORIENTATION_PORTRAIT)))
		{
			return Configuration.ORIENTATION_LANDSCAPE;
		}     
		return Configuration.ORIENTATION_PORTRAIT;
	}	
	private void setRotation(int displayRotation)
	{
		if (D)
		{
			int detectOrientation=getWindowRotation();
			if  (detectOrientation!=displayRotation)
			{
				if (E) Log.e(TAG_QRCODE,"detectOrientation="+detectOrientation+", displayRotation="+displayRotation);
			}
			assert (detectOrientation==displayRotation);
		}
		int rotation;
		int defaultOrientation=getDeviceDefaultOrientation();
		if (defaultOrientation==Configuration.ORIENTATION_LANDSCAPE)
			rotation=sSurfaceRotationToCameraDegreeForLandscapeDefault[displayRotation];
		else
			rotation=sSurfaceRotationToCameraDegreeForPortraitDefaut[displayRotation];
		if (mCamera!=null && rotation!=mRotation)
		{
			try
			{
				Camera.Parameters parameters=getCameraParameters();
				parameters.setRotation(mRotation=rotation);
setCamera(mCameraId);
				mCamera.setParameters(parameters);
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO)
				{
					mCamera.setDisplayOrientation(mRotation);
				}
			}
			catch (Exception e)
			{
				if (E) Log.e(TAG_QRCODE,"Error when reconnect the camera",e);
			}
			mCamera.startPreview();
			requestLayout();
		}
		mFramingRectInPreview=null;
		mCaptureHandler.startScan();
	}
	
	@Override
	public void surfaceCreated(SurfaceHolder holder)
	{
		// The Surface has been created, acquire the camera and tell it where
		// to draw.
		try
		{
			if (mCamera != null)
			{
				mCamera.setPreviewDisplay(holder);
				Size s=mCamera.getParameters().getPreviewSize();
				mPreviousSize=new Point(s.width,s.height);
//				mCaptureHandler.startScan();
			}
		}
		catch (IOException exception)
		{
			if (E) Log.e(TAG_QRCODE, "IOException caused by setPreviewDisplay()", exception);
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder)
	{
		// Surface will be destroyed when we return, so stop the preview.
		if (mCamera != null)
		{
			mCamera.stopPreview();
		}
	}
	
	private Size getOptimalCameraPreviewSize(List<Size> sizes, int w, int h)
	{
		// Select resolution with the minimum number of pixels
		final int minimalSize=QRCODE_MINIMAL_CAMERA_RESOLUTION; 
		int posMin=-1;
		int pixels=Integer.MAX_VALUE;
		Size size;
		for (int i=0;i<sizes.size();++i)
		{
			size=sizes.get(i);
			if (V) Log.v(TAG_QRCODE,i+" Check size "+size.width+","+size.height);
			final int p=size.height*size.width;
			if (p<pixels && p>=minimalSize)
			{
				pixels=p;
				posMin=i;
			}
		}
		if (posMin==-1)
			posMin=0;
		size=sizes.get(posMin);
		if (I) Log.i(TAG_QRCODE,"Select resolution "+size.width+","+size.height+" for the camera");
		return size;
	}

	/*package*/ Rect getFramingRectInPreview()
	{
		final int width=getWidth();
		final int height=getHeight();
		if (mFramingRectInPreview == null)
		{
			final int coef=(mRotation==90) ? QRCODE_PERCENT_WIDTH_PORTRAIT : QRCODE_PERCENT_WIDTH_LANDSCAPE;
			final int sizemax=Math.min(width,height)*coef/100;
			mFramingRectInPreview=new Rect();
			mFramingRectInPreview.left=(width-sizemax)/2;
			mFramingRectInPreview.top=(height-sizemax)/2;
			mFramingRectInPreview.right=mFramingRectInPreview.left+sizemax;
			mFramingRectInPreview.bottom=mFramingRectInPreview.top+sizemax;
		}
		return mFramingRectInPreview;
	}
	/*package*/ Rect getCameraRect()
	{
		return mCameraRect;
	}
	private static String findSettableValue(Collection<String> supportedValues, String... desiredValues)
	{
		String result = null;
		if (supportedValues != null)
		{
			for (String desiredValue : desiredValues)
			{
				if (supportedValues.contains(desiredValue))
				{
					result = desiredValue;
					break;
				}
			}
		}
		return result;
	}

	private static final class BeepManager
	{

		private static final float BEEP_VOLUME = 0.10f;

		private Context mContext;

		private MediaPlayer mMediaPlayer;

		private boolean mPlayBeep;

		public BeepManager(Context context)
		{
			mContext = context;
			
			mMediaPlayer = null;
			updatePrefs();
		}

		public void updatePrefs()
		{
			SharedPreferences prefs = PreferenceManager
					.getDefaultSharedPreferences(mContext);
			mPlayBeep = shouldBeep(
				prefs, mContext);
			// vibrate = prefs.getBoolean(PreferencesActivity.KEY_VIBRATE, false);
			if (mPlayBeep && mMediaPlayer == null)
			{
				// The volume on STREAM_SYSTEM is not adjustable, and users found it too loud,
				// so we now play on the music stream.
				// FIXME: Volume
//				WindowManager wm=(WindowManager)mContext.getSystemService(Context.WINDOW_SERVICE);
//				wm.getDefaultDisplay().setVolumeControlStream(AudioManager.STREAM_MUSIC);
				mMediaPlayer = buildMediaPlayer(mContext);
			}
		}

		public void playBeepSoundAndVibrate()
		{
			if (mPlayBeep && mMediaPlayer != null)
			{
				mMediaPlayer.start();
			}
			if (QRCODE_VIBRATE_DURATION!=0)
			{
				final Vibrator vibrator = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);
				vibrator.vibrate(QRCODE_VIBRATE_DURATION);
			}
		}

		private static boolean shouldBeep(SharedPreferences prefs, Context activity)
		{
			boolean shouldPlayBeep = true;
			// TODO prefs.getBoolean(PreferencesActivity.KEY_PLAY_BEEP, true);
			if (shouldPlayBeep)
			{
				// See if sound settings overrides this
				AudioManager audioService = (AudioManager) activity
						.getSystemService(Context.AUDIO_SERVICE);
				if (audioService.getRingerMode() != AudioManager.RINGER_MODE_NORMAL)
				{
					shouldPlayBeep = false;
				}
			}
			return shouldPlayBeep;
		}

		private static MediaPlayer buildMediaPlayer(Context activity)
		{
			MediaPlayer mediaPlayer = new MediaPlayer();
			mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			// When the beep has finished playing, rewind to queue up another one.
			mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener()
			{
				public void onCompletion(MediaPlayer player)
				{
					player.seekTo(0);
				}
			});

			AssetFileDescriptor file = activity.getResources().openRawResourceFd(R.raw.beep);
			try
			{
				mediaPlayer.setDataSource(
					file.getFileDescriptor(), file.getStartOffset(),
					file.getLength());
				file.close();
				mediaPlayer.setVolume(BEEP_VOLUME, BEEP_VOLUME);
				mediaPlayer.prepare();
			}
			catch (IOException ioe)
			{
				if (W) Log.w(TAG_QRCODE, ioe);
				mediaPlayer = null;
			}
			return mediaPlayer;
		}

	}

}
