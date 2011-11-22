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

import static org.remoteandroid.Constants.*;
import static org.remoteandroid.internal.Constants.*;
import static org.remoteandroid.Constants.QRCODE_SHOW_CURRENT_DECODE;
import static org.remoteandroid.Constants.TAG_CONNECT;
import static org.remoteandroid.internal.Constants.D;

import java.util.ArrayList;
import java.util.List;

import org.remoteandroid.R;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.os.Handler;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import com.google.zxing.ResultPoint;

/**
 * This view is overlaid on top of the camera preview. It adds the viewfinder
 * rectangle and partial transparency outside it, as well as the laser scanner
 * animation and result points.
 * 
 * @author dswitkin@google.com (Daniel Switkin)
 * @author Yohann Melo
 */
public final class ViewfinderView extends View
{

	private static final long ANIMATION_DELAY = 100L;

	private static final int CURRENT_POINT_OPACITY = 0x99;

	private static final int MAX_RESULT_POINTS = 20;

	private final Paint mPaint;

	private Bitmap mResultBitmap;

	private Bitmap mPreviousBitmap;

	private final int mMaskColor;

	private final int mResultColor;

	private final int mFrameColor;

	private final int mLaserColor;

	private final int mResultPointColor;

	private int mAnimPos;

	private List<ResultPoint> mPossibleResultPoints;

	private List<ResultPoint> mLastPossibleResultPoints;

	private Long mStartTime = 0L;

	private Handler mHandler = new Handler();

	private Runnable mUpdateTimeTask = new Runnable()
	{
		public void run()
		{
			++mAnimPos;
			mStartTime = SystemClock.uptimeMillis();
			mHandler.postAtTime(
				this, mStartTime + 300);
		}
	};

	// This constructor is used when the class is built from an XML resource.
	public ViewfinderView(Context context, AttributeSet attrs)
	{
		super(context, attrs);

		// Initialize these once for performance rather than calling them every
		// time in onDraw().
		mPaint = new Paint();
		Resources resources = getResources();
		mMaskColor = resources.getColor(R.color.qrcode_mask);
		mResultColor = resources.getColor(R.color.qrcode_result_color);
		mFrameColor = resources.getColor(R.color.qrcode_frame_color);
		mLaserColor = resources.getColor(R.color.qrcode_laser);
		mResultPointColor = resources
				.getColor(R.color.qrcode_possible_result_points);
		mPossibleResultPoints = new ArrayList<ResultPoint>(5);
		mLastPossibleResultPoints = null;
		if (mStartTime == 0L)
		{
			mStartTime = System.currentTimeMillis();
			mHandler.removeCallbacks(mUpdateTimeTask);
			mHandler.postDelayed(
				mUpdateTimeTask, 100);
		}
	}

	public void detachHandler()
	{
		if (mHandler != null)
			mHandler.removeCallbacks(mUpdateTimeTask);
	}

	private Rect mRect = new Rect();

	@Override
	public void onDraw(Canvas canvas)
	{

		Rect frame = CameraManager.get().getFramingRectInPreview();

		if (frame == null)
		{
			return;
		}

		int width = canvas.getWidth();
		int height = canvas.getHeight();

		// Draw the exterior (i.e. outside the framing rect) darkened
		mPaint.setStyle(Style.FILL);
		mPaint.setStrokeWidth(1);
		mPaint.setColor(mResultBitmap != null ? mResultColor : mMaskColor);
		canvas.drawRect(
			0, 0, width, frame.top, mPaint);
		canvas.drawRect(
			0, frame.top, frame.left, frame.bottom + 1, mPaint);
		canvas.drawRect(
			frame.right + 1, frame.top, width, frame.bottom + 1, mPaint);
		canvas.drawRect(
			0, frame.bottom + 1, width, height, mPaint);
		 
		 
		 mPaint.setColor(Color.RED);
		 canvas.drawCircle(70, 41, 10, mPaint);
		 
		 canvas.drawCircle(320-70, 240-41, 10, mPaint);
		 mPaint.setColor(Color.BLUE);
		 canvas.drawCircle(41, 70, 10, mPaint);
		if (mResultBitmap != null)
		{
			// Draw the opaque result bitmap over the scanning rectangle
			mPaint.setAlpha(CURRENT_POINT_OPACITY);
			canvas.drawBitmap(
				mResultBitmap, null, frame, mPaint);
		}
		else
		{
			if (QRCODE_SHOW_CURRENT_DECODE)
			{
				if (mPreviousBitmap != null)
				{
					// Draw the opaque result bitmap over the scanning rectangle
					// Matrix matrix=new Matrix();
					// matrix.setRotate(180);
					// mPaint.setAlpha(0xFF);
					// canvas.drawBitmap(mPreviousBitmap, matrix, /*new
					// RectF(frame),*/ mPaint);
					mPaint.setAlpha(CURRENT_POINT_OPACITY);

					canvas.drawBitmap(
						mPreviousBitmap, null, frame, mPaint);
				}
			}
			// Draw a red "laser scanner" line through the middle to show
			// decoding is active
			mPaint.setColor(mLaserColor);
			mPaint.setAlpha(128); // FIXME
			// mPaint.setAlpha(SCANNER_ALPHA[mScannerAlpha]);
			mPaint.setStyle(Style.STROKE);
			mPaint.setStrokeWidth(5);

			// TODO: Cadre plus petit que la réalité !
			int animPos = mAnimPos % 8;
			int w = frame.width();
			int h = frame.height();
			int ww = w / 20 * (animPos);
			int hh = h / 20 * (animPos);
			mRect.set(
				frame.left + ww, frame.top + hh, frame.right - ww, frame.bottom
						- hh);
			// mScannerAlpha = (mScannerAlpha + 1) % SCANNER_ALPHA.length;
			canvas.drawRect(
				mRect, mPaint);

			int boxLeft = mRect.width() / 8;
			int boxTop = mRect.height() / 8;
			int widthBox = boxLeft + (mRect.width() / 8);
			int heightBox = boxTop + (mRect.height() / 8);
			int animPos2 = (mAnimPos / 8) % 4;
			if (animPos2 != 0)
				canvas.drawRect(
					mRect.left + boxLeft, mRect.top + boxTop, mRect.left
							+ widthBox, mRect.top + heightBox, mPaint);
			if (animPos2 != 1)
				canvas.drawRect(
					mRect.left + boxLeft, mRect.bottom - boxTop, mRect.left
							+ widthBox, mRect.bottom - heightBox, mPaint);
			if (animPos2 != 2)
				canvas.drawRect(
					mRect.right - boxLeft, mRect.bottom - boxTop, mRect.right
							- widthBox, mRect.bottom - heightBox, mPaint);
			if (animPos2 != 3)
				canvas.drawRect(
					mRect.right - boxLeft, mRect.top + boxTop, mRect.right
							- widthBox, mRect.top + heightBox, mPaint);

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

			Rect previewFrame = CameraManager.get().getFramingRectInPreview();
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
					{
						canvas.drawCircle(
							frame.left + (int) (point.getX() * scaleX),
							frame.top + (int) (point.getY() * scaleY), 6.0f,
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

			// Request another update at the animation interval, but only
			// repaint the laser line,
			// not the entire viewfinder mask.
			if (!QRCODE_SHOW_CURRENT_DECODE)
				postInvalidateDelayed(
					ANIMATION_DELAY, frame.left, frame.top, frame.right,
					frame.bottom);
		}
	}

	public void drawViewfinder()
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
	public void drawResultBitmap(Bitmap barcode)
	{
		if (D)
			Log.d(
				TAG_QRCODE,
				"Result w:" + barcode.getWidth() + " h:" + barcode.getHeight());
		// FIXME if (CameraManager.get().isRotate())
//		if (CameraManager.camera_orientation == 2
//				|| CameraManager.camera_orientation == 0)// CameraManager.HACK_ROTATE)
//		{
//			Matrix matrix = new Matrix();
//			matrix.setRotate(
//				90, barcode.getWidth() / 2, barcode.getHeight() / 2);
//
//			barcode = Bitmap.createBitmap(
//				barcode, 0, 0, barcode.getWidth(), barcode.getHeight(), matrix,
//				true);
//			if (D)
//				Log.d(
//					TAG_QRCODE, "Rotate result w:" + barcode.getWidth()
//							+ " h:" + barcode.getHeight());
//		}
		mResultBitmap = barcode;
		invalidate();
	}

	public void drawPreviousBitmap(Bitmap barcode)
	{
//		if (D)
//			Log.d(
//				TAG_CONNECT,
//				"Result w:" + barcode.getWidth() + " h:" + barcode.getHeight());
//		// FIXME if (CameraManager.get().isRotate())
//
//		
//			Matrix matrix = new Matrix();
//			matrix.setRotate(
//				CameraManager.get().hackRotation, this.getWidth()/2, this.getHeight() / 2);
//
//			barcode = Bitmap.createBitmap(
//				barcode, 0, 0, barcode.getWidth(), barcode.getHeight(), matrix,
//				true);
//			if (D)
//				Log.d(
//					TAG_QRCODE, "Rotate result w:" + barcode.getWidth()
//							+ " h:" + barcode.getHeight());
//		
		mPreviousBitmap = barcode;
		invalidate();
	}

	public void addPossibleResultPoint(ResultPoint point)
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

}
