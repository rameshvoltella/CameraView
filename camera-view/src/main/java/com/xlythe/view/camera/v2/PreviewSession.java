package com.xlythe.view.camera.v2;

import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.xlythe.view.camera.ICameraModule.DEBUG;
import static com.xlythe.view.camera.ICameraModule.TAG;

@TargetApi(21)
class PreviewSession extends SessionImpl {
    private final PreviewSurface mPreviewSurface;

    PreviewSession(Camera2Module camera2Module) {
        super(camera2Module);
        mPreviewSurface = new PreviewSurface(camera2Module);
    }

    CameraSurface getPreviewSurface() {
        return mPreviewSurface;
    }

    @Override
    public void initialize(StreamConfigurationMap map) throws CameraAccessException {
        mPreviewSurface.initialize(map);
        transformPreview(mPreviewSurface.getWidth(), mPreviewSurface.getHeight());
    }

    private CaptureRequest createCaptureRequest(CameraDevice device) throws CameraAccessException {
        CaptureRequest.Builder builder = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        if (mMeteringRectangle != null) {
            builder.set(CaptureRequest.CONTROL_AE_REGIONS, new MeteringRectangle[]{mMeteringRectangle});
            builder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{mMeteringRectangle});
        }
        if (mCropRegion != null) {
            builder.set(CaptureRequest.SCALER_CROP_REGION, mCropRegion);
        }
        builder.addTarget(mPreviewSurface.getSurface());
        return builder.build();
    }

    @Override
    public void onAvailable(CameraDevice device, CameraCaptureSession session) throws CameraAccessException {
        session.setRepeatingRequest(createCaptureRequest(device), null /* callback */, getBackgroundHandler());
    }

    @Override
    public void onInvalidate(CameraDevice device, CameraCaptureSession session) throws CameraAccessException {
        session.setRepeatingRequest(createCaptureRequest(device), null /* callback */, getBackgroundHandler());
    }

    @Override
    public List<Surface> getSurfaces() {
        List<Surface> surfaces = super.getSurfaces();
        surfaces.add(mPreviewSurface.getSurface());
        return surfaces;
    }

    private static final class PreviewSurface extends CameraSurface {
        private static Size chooseOptimalSize(Size[] choices, int width, int height) {
            List<Size> availableSizes = new ArrayList<>(choices.length);
            for (Size size : choices) {
                if (size.getWidth() >= width && size.getHeight() >= height) {
                    availableSizes.add(size);
                }
            }

            if (availableSizes.isEmpty()) {
                Log.e(TAG, "Couldn't find a suitable preview size");
                availableSizes.add(choices[0]);
            }

            if (DEBUG) {
                Log.d(TAG, "Found available preview sizes: " + availableSizes);
            }

            return Collections.min(availableSizes, new CompareSizesByArea());
        }

        private Surface mSurface;

        PreviewSurface(Camera2Module cameraView) {
            super(cameraView);
        }

        void initialize(StreamConfigurationMap map) {
            super.initialize(chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), mCameraView.getWidth(), mCameraView.getHeight()));

            SurfaceTexture texture = mCameraView.getSurfaceTexture();
            texture.setDefaultBufferSize(getWidth(), getHeight());

            // This is the output Surface we need to start the preview.
            mSurface = new Surface(texture);
        }

        @Override
        Surface getSurface() {
            return mSurface;
        }

        @Override
        void close() {}
    }
}
