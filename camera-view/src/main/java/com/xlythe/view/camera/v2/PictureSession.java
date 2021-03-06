package com.xlythe.view.camera.v2;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.location.Location;
import android.media.Image;
import android.media.ImageReader;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.UiThread;
import android.support.annotation.WorkerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import com.xlythe.view.camera.CameraView;
import com.xlythe.view.camera.Exif;
import com.xlythe.view.camera.ICameraModule;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static com.xlythe.view.camera.ICameraModule.TAG;

@TargetApi(21)
class PictureSession extends PreviewSession {
    /**
     * Supports {@link ImageFormat#JPEG} and {@link ImageFormat#YUV_420_888}. You can support
     * larger sizes with YUV_420_888, at the cost of speed.
     */
    private static final int IMAGE_FORMAT_DEFAULT = ImageFormat.JPEG;
    private static final int IMAGE_FORMAT_MAX = ImageFormat.YUV_420_888;

    private final PictureSurface mPictureSurface;

    PictureSession(Camera2Module camera2Module) {
        super(camera2Module);
        mPictureSurface = new PictureSurface(camera2Module, getPreviewSurface());
    }

    @Override
    public void initialize(@NonNull StreamConfigurationMap map) throws CameraAccessException {
        super.initialize(map);
        mPictureSurface.initialize(map);
    }

    @NonNull
    @Override
    public List<Surface> getSurfaces() {
        List<Surface> surfaces = super.getSurfaces();
        surfaces.add(mPictureSurface.getSurface());
        return surfaces;
    }

    void takePicture(@NonNull File file, @NonNull CameraDevice device, @NonNull CameraCaptureSession session) {
        mPictureSurface.initializePicture(file);
        try {
            CaptureRequest.Builder builder = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            if (hasFlash()) {
                switch (getFlash()) {
                    case AUTO:
                        // TODO: This doesn't work :(
                        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                        break;
                    case ON:
                        builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_SINGLE);
                        break;
                    case OFF:
                        builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                        break;
                }
            }
            builder.addTarget(mPictureSurface.getSurface());
            if (mMeteringRectangle != null) {
                builder.set(CaptureRequest.CONTROL_AE_REGIONS, new MeteringRectangle[]{mMeteringRectangle});
                builder.set(CaptureRequest.CONTROL_AF_REGIONS, new MeteringRectangle[]{mMeteringRectangle});
            }
            if (mCropRegion != null) {
                builder.set(CaptureRequest.SCALER_CROP_REGION, mCropRegion);
            }
            session.capture(builder.build(), null /* callback */, getBackgroundHandler());
        } catch (CameraAccessException | IllegalStateException | NullPointerException e) {
            // Crashes if the Camera is interacted with while still loading
            Log.e(TAG, "Failed to create capture request", e);

            CameraView.OnImageCapturedListener l = getOnImageCapturedListener();
            if (l != null) {
                l.onFailure();
            }
        }
    }

    private static class ImageSaver extends AsyncTask<Void, Void, Void> {
        private final Context mContext;

        // The image that was captured
        private final Image mImage;

        // The orientation of the image
        private final int mOrientation;

        // If true, the picture taken is reversed and needs to be flipped.
        // Typical with front facing cameras.
        private final boolean mIsReversed;

        // The file to save the image to
        private final File mFile;

        ImageSaver(Context context, Image image, int orientation, boolean reversed, File file) {
            mContext = context.getApplicationContext();
            mImage = image;
            mOrientation = orientation;
            mIsReversed = reversed;
            mFile = file;
        }

        @WorkerThread
        @Override
        protected Void doInBackground(Void... params) {
            // Finally, we save the file to disk
            byte[] bytes = getBytes();
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);

                Exif exif = new Exif(mFile);
                exif.attachTimestamp();
                exif.rotate(mOrientation);
                if (mIsReversed) {
                    exif.flipHorizontally();
                }
                Location location = SessionImpl.CameraSurface.getLocation(mContext);
                if (location != null) {
                    exif.attachLocation(location);
                }
                exif.save();
            } catch (IOException e) {
                Log.e(TAG, "Failed to write the file", e);
            } finally {
                mImage.close();
                if (output != null) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to close the output stream", e);
                    }
                }
            }
            return null;
        }

        private byte[] getBytes() {
            return toByteArray(mImage);
        }

        private static byte[] toByteArray(Image image) {
            byte[] data = null;
            if (image.getFormat() == ImageFormat.JPEG) {
                Image.Plane[] planes = image.getPlanes();
                ByteBuffer buffer = planes[0].getBuffer();
                data = new byte[buffer.capacity()];
                buffer.get(data);
                return data;
            } else if (image.getFormat() == ImageFormat.YUV_420_888) {
                data = NV21toJPEG(
                        YUV_420_888toNV21(image),
                        image.getWidth(), image.getHeight());
            } else {
                Log.w(TAG, "Unrecognized image format: " + image.getFormat());
            }
            return data;
        }

        private static byte[] YUV_420_888toNV21(Image image) {
            byte[] nv21;
            ByteBuffer yBuffer = image.getPlanes()[0].getBuffer();
            ByteBuffer uBuffer = image.getPlanes()[1].getBuffer();
            ByteBuffer vBuffer = image.getPlanes()[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            nv21 = new byte[ySize + uSize + vSize];

            // U and V are swapped
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            return nv21;
        }

        private static byte[] NV21toJPEG(byte[] nv21, int width, int height) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            YuvImage yuv = new YuvImage(nv21, ImageFormat.NV21, width, height, null);
            yuv.compressToJpeg(new Rect(0, 0, width, height), 100, out);
            return out.toByteArray();
        }
    }

    private static final class PictureSurface extends CameraSurface {
        private List<Size> getSizes(StreamConfigurationMap map) {
            // Special case for high resolution images (assuming, of course, quality was set to high)
            if (getQuality() == CameraView.Quality.HIGH && Build.VERSION.SDK_INT >= 23) {
                Size[] sizes = map.getHighResolutionOutputSizes(getImageFormat(getQuality()));
                if (sizes != null) {
                    List<Size> availableSizes = isRaw(getImageFormat(getQuality()))
                            ? Arrays.asList(sizes) : filter(sizes);
                    if (availableSizes.size() > 0) {
                        return availableSizes;
                    }
                }
            }

            // Otherwise, just return the default sizes
            Size[] sizes = map.getOutputSizes(getImageFormat(getQuality()));
            return isRaw(getImageFormat(getQuality())) ? Arrays.asList(sizes) : filter(sizes);
        }

        private final ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(final ImageReader reader) {
                mCameraView.getBackgroundHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if (mFile != null) {
                                final File file = mFile;
                                new ImageSaver(
                                        mCameraView.getContext(),
                                        reader.acquireLatestImage(),
                                        mCameraView.getRelativeCameraOrientation(),
                                        isUsingFrontFacingCamera(),
                                        mFile) {
                                    @UiThread
                                    @Override
                                    protected void onPostExecute(Void aVoid) {
                                        showImageConfirmation(file);
                                    }
                                }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                mFile = null;
                            } else {
                                Log.w(ICameraModule.TAG, "OnImageAvailable called but no file to write to");
                            }
                        } catch (IllegalStateException e) {
                            Log.e(TAG, "Failed to save image", e);
                        }
                    }
                });
            }
        };

        private final CameraSurface mPreviewSurface;
        private ImageReader mImageReader;
        private File mFile;

        PictureSurface(Camera2Module camera2Module, CameraSurface previewSurface) {
            super(camera2Module);
            mPreviewSurface = previewSurface;
        }

        @Override
        void initialize(StreamConfigurationMap map) {
            super.initialize(chooseSize(getSizes(map), mPreviewSurface.mSize));
            mImageReader = ImageReader.newInstance(getWidth(), getHeight(), getImageFormat(getQuality()), 1 /* maxImages */);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mCameraView.getBackgroundHandler());
        }

        void initializePicture(File file) {
            mFile = file;
            if (mFile.exists()) {
                Log.w(TAG, "File already exists. Deleting.");
                mFile.delete();
            }
        }

        @Override
        Surface getSurface() {
            return mImageReader.getSurface();
        }

        @Override
        void close() {
            if (mImageReader != null) {
                mImageReader.close();
                mImageReader = null;
            }
        }
    }

    private static int getImageFormat(CameraView.Quality quality) {
        switch (quality) {
            case MAX:
                return IMAGE_FORMAT_MAX;
            default:
                return IMAGE_FORMAT_DEFAULT;
        }
    }

    private static boolean isRaw(int imageFormat) {
        switch (imageFormat) {
            case ImageFormat.YUV_420_888:
                return true;
            default:
                return false;
        }
    }
}
