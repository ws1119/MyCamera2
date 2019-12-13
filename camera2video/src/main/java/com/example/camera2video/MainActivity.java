package com.example.camera2video;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;
import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private TextureView mTextureView;
    private Button record,stop,pause,picture;
    private TextView textView;
    private HandlerThread mHandlerThread;
    private Handler childHandler;
    private CameraManager mCameraManager;
    private Size mPreviewSize;
    private String mCameraId;
    private CameraDevice mCameraDevice;
    private SurfaceTexture mSurfaceTexture;
    private CaptureRequest.Builder builder;
    private Surface mSurface;
    private CameraCaptureSession mCameraCaptureSession;
    private MediaRecorder mMediaRecord;
    private boolean isRecording = false;
    private boolean isPause = false;
    private ImageView imageView;
    private Bitmap videoThumbnail;
    private File videoFile;
    private static final SparseIntArray ORIENTATION = new SparseIntArray();
    private Size cameraSize;
    private ImageReader mImageReader;
    private File path;
    private File file;
    static
    {//为了照片竖直显示
        ORIENTATION.append(Surface.ROTATION_0,90);
        ORIENTATION.append(Surface.ROTATION_90,0);
        ORIENTATION.append(Surface.ROTATION_180,270);
        ORIENTATION.append(Surface.ROTATION_270,180);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextureView = findViewById(R.id.texture);
        record = findViewById(R.id.record);
        stop = findViewById(R.id.stop);
        pause = findViewById(R.id.pause);
        textView=findViewById(R.id.text);
        imageView = findViewById(R.id.image);
        picture = findViewById(R.id.picture);

        record.setOnClickListener(this);
        stop.setOnClickListener(this);
        pause.setOnClickListener(this);
        picture.setOnClickListener(this);
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
        initThread();
        initImageReader();
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
    //打开相机
    private void openCamera()
    {
        mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        if (ActivityCompat.checkSelfPermission(MainActivity.this,Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
         || ActivityCompat.checkSelfPermission(MainActivity.this,Manifest.permission.RECORD_AUDIO) !=PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(MainActivity.this,Manifest.permission.READ_EXTERNAL_STORAGE) !=PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.CAMERA,Manifest.permission.RECORD_AUDIO,Manifest.permission.READ_EXTERNAL_STORAGE},21);
        }
        else
        {
            try {
                mCameraManager.openCamera(mCameraId,new CameraDevice.StateCallback() {
                    @Override
                    public void onOpened(CameraDevice camera) {
                        mCameraDevice = camera;
                        startPreview();
                    }

                    @Override
                    public void onDisconnected(CameraDevice camera) {

                    }

                    @Override
                    public void onError(CameraDevice camera, int error) {

                    }
                },childHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            mMediaRecord = new MediaRecorder();
        }
    }
    private void startPreview()
    {
        SurfaceTexture mSurfaceTexture = mTextureView.getSurfaceTexture();
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(),mPreviewSize.getHeight());
        Surface mSurface = new Surface(mSurfaceTexture);
        try {
            //创建预览请求
            builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            //builder.set();
            builder.addTarget(mSurface);
            Log.v("ppppppppppppp1", String.valueOf(mSurface));
            //创建相机捕获会话
            mCameraDevice.createCaptureSession(Arrays.asList(mSurface,mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    mCameraCaptureSession = session;
                    try {
                        mCameraCaptureSession.setRepeatingRequest(builder.build(), new CameraCaptureSession.CaptureCallback() {
                            @Override
                            public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                                super.onCaptureStarted(session, request, timestamp, frameNumber);
                            }

                            @Override
                            public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
                                super.onCaptureProgressed(session, request, partialResult);
                            }

                            @Override
                            public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                                super.onCaptureCompleted(session, request, result);
                            }

                            @Override
                            public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
                                super.onCaptureFailed(session, request, failure);
                            }

                            @Override
                            public void onCaptureSequenceCompleted(CameraCaptureSession session, int sequenceId, long frameNumber) {
                                super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
                            }

                            @Override
                            public void onCaptureSequenceAborted(CameraCaptureSession session, int sequenceId) {
                                super.onCaptureSequenceAborted(session, sequenceId);
                            }

                            @Override
                            public void onCaptureBufferLost(CameraCaptureSession session, CaptureRequest request, Surface target, long frameNumber) {
                                super.onCaptureBufferLost(session, request, target, frameNumber);
                            }
                        },childHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                }
            }, childHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    
    private void setupMediaRecord()
    {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy年MM月dd日 hh:mm:ss");
        String currDate = dateFormat.format(new Date());
        videoFile = new File(MainActivity.this.getExternalCacheDir()+currDate+".mp4");
        mMediaRecord.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mMediaRecord.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecord.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mMediaRecord.setOutputFile(videoFile.getAbsolutePath());
        mMediaRecord.setVideoEncodingBitRate(10000000);
        mMediaRecord.setVideoFrameRate(30);
        Size size = getMatchingSize();
        mMediaRecord.setVideoSize(size.getWidth(),size.getHeight());
        mMediaRecord.setVideoEncoder(MediaRecorder.VideoEncoder.HEVC);
        mMediaRecord.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecord.setOrientationHint(90);
        try {
            mMediaRecord.prepare();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private void configRecord(){

        try {
            setupMediaRecord();
            cameraSize = getMatchingSize();
            SurfaceTexture mTexture = mTextureView.getSurfaceTexture();
            mTexture.setDefaultBufferSize(cameraSize.getWidth(),cameraSize.getHeight());
            builder=mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            Surface previewSurface = new Surface(mTexture);
            builder.addTarget(previewSurface);
            Surface recordSurface = mMediaRecord.getSurface();
            builder.addTarget(recordSurface);
            Log.v("ppppppppppppp", String.valueOf(previewSurface));
            mCameraDevice.createCaptureSession(Arrays.asList(previewSurface,recordSurface,mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    mCameraCaptureSession = session;
                    try {
                        mCameraCaptureSession.setRepeatingRequest(builder.build(),null,childHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                }
            },childHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }
    private void initImageReader(){
        mImageReader = ImageReader.newInstance(1920,1080, ImageFormat.JPEG,2);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("YYYY年MM月dd日 HH:mm:ss");
                String curDate = simpleDateFormat.format(new Date());
                Image image = reader.acquireNextImage();
                path = new File(MainActivity.this.getExternalCacheDir().getPath());
                file = new File(path, curDate+".jpg");

                if (!path.exists())
                {
                    path.mkdir();
                }
                else
                {
                    Log.v("==========", String.valueOf(path));
                }
                try {
                    FileOutputStream fileOutputStream = new FileOutputStream(file);
                    //这里的image.getPlanes()[0]其实是图层的意思,因为我的图片格式是JPEG只有一层所以是geiPlanes()[0],如果你是其他格式(例如png)的图片会有多个图层,就可以获取指定图层的图像数据
                    ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[byteBuffer.remaining()];
                    byteBuffer.get(bytes);
                    fileOutputStream.write(bytes);
                    fileOutputStream.flush();
                    fileOutputStream.close();
                    image.close();
                }catch (IOException e)
                {
                    e.printStackTrace();
                }

            }
        },childHandler);
    }

    private void takePicture(){
        try {
            CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            Surface captureSurface = mImageReader.getSurface();
            captureBuilder.addTarget(captureSurface);
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION,90);
            mCameraCaptureSession.capture(captureBuilder.build(),null,childHandler);
            Toast.makeText( MainActivity.this, "拍照成功", Toast.LENGTH_SHORT ).show();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void stopRecord()
    {
        mMediaRecord.stop();
        mMediaRecord.reset();
        seePicture();
        startPreview();
    }

    private Bitmap getVideoThumbnail(String videoPath,int width,int height,int kind)
    {
        Bitmap bitmap = null;
        //获取视频的缩略图
        bitmap = ThumbnailUtils.createVideoThumbnail(videoPath,kind);
        bitmap = ThumbnailUtils.extractThumbnail(bitmap,width,height,ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
        return  bitmap;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode)
        {
            case 21:
                if (permissions.length>0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    openCamera();
                }
                else
                {
                    finish();
                }
        }
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
        return selectSize;
    }
    private void initThread(){
        mHandlerThread = new HandlerThread("CameraVideo");
        mHandlerThread.start();
        childHandler = new Handler(mHandlerThread.getLooper());
    }
    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onClick(View v) {
        switch (v.getId())
        {
            case R.id.record:
                if (!isRecording)
                {
                    configRecord();
                    mMediaRecord.start();
                    isRecording=true;
                    textView.setText("正在录制中");
                }
                else
                {
                    Toast.makeText( MainActivity.this, "正在录像中", Toast.LENGTH_SHORT ).show();
                }
                break;
            case R.id.stop:
                if (isRecording)
                {
                    stopRecord();
                    Toast.makeText( MainActivity.this, "录像完成，文件保存在"+MainActivity.this.getExternalCacheDir(), Toast.LENGTH_SHORT ).show();
                    isRecording=false;
                    isPause= false;
                    textView.setText("未开始录制");
                }
                else
                {
                    Toast.makeText( MainActivity.this, "还未开始录像，请先开始录像", Toast.LENGTH_SHORT ).show();
                }
                break;
            case R.id.pause:
                if (!isPause && isRecording)
                {
                    mMediaRecord.pause();
                    isPause=true;
                    textView.setText("已暂停");
                }
                else if (isPause)
                {
                    mMediaRecord.resume();
                    isPause=false;
                    textView.setText("正在录制中");
                }
                else 
                {
                    Toast.makeText( MainActivity.this, "还未开始录像，请先开始录像", Toast.LENGTH_SHORT ).show();
                }
                break;
            case R.id.picture:
                takePicture();
                break;
        }
    }
    @Override
    protected void onPause() {
        super.onPause();
    }
    @Override
    protected void onStop() {
        super.onStop();
        if (isRecording)
        {
            mMediaRecord.stop();
            mMediaRecord=null;
            release();
            Toast.makeText( MainActivity.this, "已停止录制视频，文件保存在"+MainActivity.this.getExternalCacheDir(), Toast.LENGTH_SHORT ).show();
            textView.setText("还未开始录制视频");
            Log.v("dedededede2", String.valueOf(mCameraDevice));
        }
    }
    @Override
    protected void onRestart() {
        super.onRestart();
        isRecording=false;
        initThread();
        setupCamera();
        openCamera();
        seePicture();
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        release();
    }
    private void release()
    {
        if (builder != null) {
            builder.removeTarget(mSurface);
            builder = null;
        }
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
        if (mSurfaceTexture != null){
            mSurfaceTexture.release();
            mSurfaceTexture = null;
        }
        if (mCameraCaptureSession != null) {
            try {
                mCameraCaptureSession.stopRepeating();
                mCameraCaptureSession.abortCaptures();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            mCameraCaptureSession = null;
        }

        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (childHandler != null) {
            childHandler.removeCallbacksAndMessages(null);
            childHandler = null;
        }
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            mHandlerThread = null;
        }
        mCameraManager = null;
    }
    private void seePicture(){
        if (videoFile != null)
        {
            videoThumbnail = getVideoThumbnail(videoFile.getAbsolutePath(),cameraSize.getHeight(),cameraSize.getHeight(), MediaStore.Images.Thumbnails.MICRO_KIND);
            imageView.setImageBitmap(videoThumbnail);
            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    Uri mUri = FileProvider.getUriForFile(MainActivity.this,"com.example.camera2video.FileProvider",videoFile);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);//授权临时权限
                    intent.setDataAndType(mUri,"video/*");
                    startActivity(intent);
                }
            });
        }
    }
}
