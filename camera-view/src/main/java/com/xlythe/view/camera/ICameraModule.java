package com.xlythe.view.camera;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;

import java.io.File;

public abstract class ICameraModule {
    public static final String TAG = "CameraModule";
    public static final boolean DEBUG = false;

    private final CameraView mView;
    private CameraView.Quality mQuality = CameraView.Quality.HIGH;
    private long mMaxVideoDuration = CameraView.INDEFINITE_VIDEO_DURATION;
    private long mMaxVideoSize = CameraView.INDEFINITE_VIDEO_SIZE;
    private CameraView.Flash mFlash = CameraView.Flash.AUTO;
    private CameraView.OnImageCapturedListener mOnImageCapturedListener;
    private CameraView.OnVideoCapturedListener mOnVideoCapturedListener;

    public ICameraModule(CameraView view) {
        mView = view;
    }

    public Context getContext() {
        return mView.getContext();
    }

    public int getWidth() {
        return mView.getWidth();
    }

    public int getHeight() {
        return mView.getHeight();
    }

    public int getDisplayRotation() {
        int displayRotation = mView.getDisplayRotation();
        switch (displayRotation) {
            case Surface.ROTATION_0:
                displayRotation = 0;
                break;
            case Surface.ROTATION_90:
                displayRotation = 90;
                break;
            case Surface.ROTATION_180:
                displayRotation = 180;
                break;
            case Surface.ROTATION_270:
                displayRotation = 270;
                break;
        }
        return displayRotation;
    }

    public SurfaceTexture getSurfaceTexture() {
        return mView.getSurfaceTexture();
    }

    protected Matrix getTransform(Matrix matrix) {
        return mView.getTransform(matrix);
    }

    protected void setTransform(final Matrix matrix) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mView.post(new Runnable() {
                @Override
                public void run() {
                    setTransform(matrix);
                }
            });
        } else {
            mView.setTransform(matrix);
        }
    }

    /*
     * Opens the camera and starts displaying a preview. You are in charge of checking if the
     * phone has PackageManager.FEATURE_CAMERA_ANY and, if you are targeting Android M+, that
     * the phone has the following permissions:
     *       Manifest.permission.CAMERA
     *       Manifest.permission.RECORD_AUDIO
     *       Manifest.permission.WRITE_EXTERNAL_STORAGE
     */
    public abstract void open();

    /*
     * Closes the camera.
     */
    public abstract void close();

    /**
     * Takes a picture. Set a OnImageCapturedListener to be
     * notified of when the picture has finished saving.
     */
    public abstract void takePicture(File file);

    /**
     * Informs the CameraView to pause and show the taken photo
     */
    public void showImageConfirmation(File file) {
        mView.showImageConfirmation(file);
    }

    /**
     * Records a video. Set a OnVideoCapturedListener to be notified of when
     * the video has finished saving.
     */
    public abstract void startRecording(File file);

    /**
     * Stops recording the video. It's recommended that you set a timeout when recording to avoid
     * excessively large files.
     */
    public abstract void stopRecording();

    /**
     * Returns true if recording.
     */
    public abstract boolean isRecording();

    /**
     * Informs the CameraView to pause and show the taken video
     */
    public void showVideoConfirmation(File file) {
        mView.showVideoConfirmation(file);
    }

    public abstract void toggleCamera();

    public abstract boolean hasFrontFacingCamera();

    public abstract boolean isUsingFrontFacingCamera();

    public abstract void focus(Rect focus, Rect metering);

    public abstract void setZoomLevel(int zoomLevel);

    public abstract int getZoomLevel();

    public abstract int getMaxZoomLevel();

    public abstract boolean isZoomSupported();

    protected abstract int getRelativeCameraOrientation();

    public void setQuality(CameraView.Quality quality) {
        mQuality = quality;
    }

    public CameraView.Quality getQuality() {
        return mQuality;
    }

    public void setMaxVideoDuration(long duration) {
        mMaxVideoDuration = duration;
    }

    public long getMaxVideoDuration() {
        return mMaxVideoDuration;
    }

    public void setMaxVideoSize(long size) {
        mMaxVideoSize = size;
    }

    public long getMaxVideoSize() {
        return mMaxVideoSize;
    }

    public void setFlash(CameraView.Flash flash) {
        mFlash = flash;
    }

    public CameraView.Flash getFlash() {
        return mFlash;
    }

    public boolean hasFlash() {
        return false;
    }

    public void setOnImageCapturedListener(CameraView.OnImageCapturedListener l) {
        mOnImageCapturedListener = l;
    }

    public CameraView.OnImageCapturedListener getOnImageCapturedListener() {
        return mOnImageCapturedListener;
    }

    public void setOnVideoCapturedListener(CameraView.OnVideoCapturedListener l) {
        mOnVideoCapturedListener = l;
    }

    public CameraView.OnVideoCapturedListener getOnVideoCapturedListener() {
        return mOnVideoCapturedListener;
    }

    public static int getRelativeImageOrientation(int displayRotation, int sensorOrientation, boolean isFrontFacing, boolean compensateForMirroring) {
        if (DEBUG) {
            Log.d(TAG, String.format("getRelativeImageOrientation displayRotation=%s, sensorOrientation=%s, isFrontFacing=%s, compensateForMirroring=%s",
                    displayRotation, sensorOrientation, isFrontFacing, compensateForMirroring));
        }
        int result;
        if (isFrontFacing) {
            result = (sensorOrientation + displayRotation) % 360;
            if (compensateForMirroring) {
                result = (360 - result) % 360;
            }
        } else {
            result = (sensorOrientation - displayRotation + 360) % 360;
        }
        if (DEBUG) {
            Log.d(TAG, String.format("getRelativeImageOrientation result=%s", result));
        }
        return result;
    }
}
