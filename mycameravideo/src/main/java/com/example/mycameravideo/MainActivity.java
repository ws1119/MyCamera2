package com.example.mycameravideo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private TextureView mTextureView;
    private Button record,stop;
    private HandlerThread mHandlerThread;
    private Handler childHandler;
    private CameraManager mCameraManager;
    private Size mPreviewSize;
    private String mCameraId;
    private CameraDevice mCameraDevice;
    private CaptureRequest.Builder builder;
    private CameraCaptureSession mCameraCaptureSession;
    private MediaRecorder mMediaRecord;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextureView = findViewById(R.id.texture);
        record = findViewById(R.id.record);
        stop = findViewById(R.id.stop);

        record.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ActivityCompat.checkSelfPermission(MainActivity.this,Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                {
                    ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.RECORD_AUDIO},1);

                }
                else
                {
                    startRecordingVideo();
                }


            }
        });
        stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecord();
            }
        });
        initThread();
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                setupCamera();
                openCamera();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }

    //配置相机
    private void setupCamera(){
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            //遍历所有摄像头
            for (String cameraId:mCameraManager.getCameraIdList())
            {
                CameraCharacteristics characteristics = mCameraManager.getCameraCharacteristics(cameraId);
                //默认打开后置摄像头
                if (characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK)
                {
                    mCameraId = cameraId;
                }
            }
            //根据TextureView的尺寸设置预览尺寸
            mPreviewSize = getMatchingSize();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void openCamera()
    {
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.CAMERA},1);
        }
        else
        {
            try {
                mCameraManager.openCamera(mCameraId,mStateCallback,childHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        mMediaRecord = new MediaRecorder();
    }

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
            startPreview();

        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {

        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {

        }

    };


    private void  startPreview() {
        if (null == mCameraDevice || !mTextureView.isAvailable() || null == mPreviewSize) {
            return;
        }
        try {
            closePreviewSession();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            Surface previewSurface = new Surface(texture);
            builder.addTarget(previewSurface);

            mCameraDevice.createCaptureSession(Collections.singletonList(previewSurface),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mCameraCaptureSession = session;
                            updatePreview();
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                        }
                    }, childHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private void    startRecordingVideo() {

        try {
            closePreviewSession();
            setUpMediaRecorder();
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            List<Surface> surfaces = new ArrayList<>();


            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            builder.addTarget(previewSurface);


            Surface recorderSurface = mMediaRecord.getSurface();
            surfaces.add(recorderSurface);
            builder.addTarget(recorderSurface);


            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {

                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Log.v("sssssssss","11111111");
                    mCameraCaptureSession = cameraCaptureSession;
                    updatePreview();
                    MainActivity.this.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Start recording
                            mMediaRecord.start();
                        }
                    });
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

                }
            }, childHandler);
        } catch (CameraAccessException | IOException e) {
            e.printStackTrace();
        }

    }

    private void setUpMediaRecorder() throws IOException {

        File videoFile = new File(MainActivity.this.getExternalCacheDir()+"demo.mp4");
        mMediaRecord.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mMediaRecord.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecord.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        mMediaRecord.setOutputFile(videoFile.getAbsolutePath());
        mMediaRecord.setVideoEncodingBitRate(10000000);
        mMediaRecord.setVideoFrameRate(30);
        Size mVideoSize = getMatchingSize();
        mMediaRecord.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecord.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecord.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecord.prepare();
    }

    private void closePreviewSession()
    {
        if (mCameraCaptureSession != null)
        {
            mCameraCaptureSession.close();
            mCameraCaptureSession=null;
        }
    }
    private void    updatePreview() {
        if (null == mCameraDevice) {
            return;
        }
        try {
            //setUpCaptureRequestBuilder(builder);
            //HandlerThread thread = new HandlerThread("CameraPreview");
            //thread.start();
            mCameraCaptureSession.setRepeatingRequest(builder.build(), null, childHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }
    private void initThread(){
        mHandlerThread = new HandlerThread("CameraVideo");
        mHandlerThread.start();
        childHandler = new Handler(mHandlerThread.getLooper());
    }

    private void stopRecord(){
        mMediaRecord.stop();
        mMediaRecord.reset();
        startPreview();
    }



    /*
   获取匹配的大小
    */
    private Size getMatchingSize(){
        Size selectSize = null;
        float selectProportion = 0;
        try {
            mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            float viewProportion = (float)mTextureView.getWidth()/(float)mTextureView.getHeight();

            CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(mCameraId);
            StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
            for (int i =0; i < sizes.length;i++)
            {
                Size itemSize = sizes[i];
                float itemProportion = (float)itemSize.getHeight()/(float)itemSize.getWidth();
                float differentProporrion = Math.abs(viewProportion-itemProportion);
                if (i == 0)
                {
                    selectSize = itemSize;
                    selectProportion = differentProporrion;
                    continue;
                }
                if (differentProporrion <= selectProportion)
                {
                    if (differentProporrion == selectProportion)
                    {
                        if (selectSize.getWidth() + selectSize.getHeight() < itemSize.getWidth()+itemSize.getHeight())
                        {
                            selectSize = itemSize;
                            selectProportion = differentProporrion;
                        }
                    }
                    else
                    {
                        selectSize = itemSize;
                        selectProportion = differentProporrion;
                    }
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        Log.e("1111111111111", "getMatchingSize: 选择的比例是=" + selectProportion);
        Log.e("2222222222222", "getMatchingSize: 选择的尺寸是 宽度=" + selectSize.getWidth() + "高度=" + selectSize.getHeight());
        return selectSize;
    }
}
