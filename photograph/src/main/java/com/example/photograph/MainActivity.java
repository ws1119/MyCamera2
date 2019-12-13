package com.example.photograph;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.zip.Inflater;

public class MainActivity extends AppCompatActivity {

    private Button take;
    private TextureView mTextureView;
    private CameraManager mCameraManager;//相机管理
    private String mCameraId;
    private HandlerThread mHandlerThread;
    private Handler mChildHandler;
    private CameraDevice mCameraDevice;//相机设备类
    private CaptureRequest.Builder builder;//数据请求配置类
    private CameraCaptureSession mCameraSession;//数据会话类
    private ImageReader mImageReader;//图片读取器
    private  Surface mSurface;
    private SurfaceTexture mSurfaceTexture;
    private static final SparseIntArray ORIENTATION = new SparseIntArray();
    private Size matchingSize;
    private  ImageView imageView;
    private Bitmap ThumbnailImage;
    private File file;
    private File path;
    private static  final  int IMAGE=1;
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
        take = findViewById(R.id.take);
        mTextureView = findViewById(R.id.textureview);
        imageView = findViewById(R.id.image);

        initImageReader();
        take.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    mCameraSession.stopRepeating();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
                takePicture();


            }
        });

        Button sure = findViewById(R.id.determine);
        sure.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    mCameraSession.setRepeatingRequest(builder.build(),mCaptureSessionCaptureCallback,mChildHandler);
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        });
        initThread();
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                //获取可用摄像头
                try {
                    for (String cameraId : mCameraManager.getCameraIdList()) {
                        //获取摄像头的特征，包含前后摄像头等
                        CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraId);
                        Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                        if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                            mCameraId = cameraId;
                        }
                    }

                    if (ActivityCompat.checkSelfPermission(MainActivity.this,Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED)
                    {
                        ActivityCompat.requestPermissions(MainActivity.this,new String[]{Manifest.permission.CAMERA},1);
                    }else
                    {
                        mCameraManager.openCamera(mCameraId, mStateCallback , mChildHandler);
                    }

                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
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

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            try {
                matchingSize= getMatchingSize();
                mSurfaceTexture = mTextureView.getSurfaceTexture();
                mSurfaceTexture.setDefaultBufferSize(matchingSize.getWidth(),matchingSize.getHeight());
                mSurface = new Surface(mSurfaceTexture);
                //创建预览
                builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                //设置自动对焦模式
                builder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                //设置自动曝光模式
                builder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                builder.addTarget(mSurface);
                //创建会话
                mCameraDevice.createCaptureSession(Arrays.asList(mSurface,mImageReader.getSurface()),mCaptureSessionStateCallback,mChildHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
        @Override
        public void onDisconnected(CameraDevice camera) {

        }

        @Override
        public void onError(CameraDevice camera, int error) {

        }
    };

    private CameraCaptureSession.StateCallback mCaptureSessionStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession session) {
            mCameraSession = session;
            try {
                mCameraSession.setRepeatingRequest(builder.build(),mCaptureSessionCaptureCallback,mChildHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession session) {

        }
    };

    private CameraCaptureSession.CaptureCallback mCaptureSessionCaptureCallback = new CameraCaptureSession.CaptureCallback() {
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
    };// 利用ThumbnailUtils来创建缩略图，这里要指定要缩放哪个Bitmap对象

    private void initThread(){
        mHandlerThread = new HandlerThread("Camera2");
        mHandlerThread.start();
        mChildHandler= new Handler(mHandlerThread.getLooper());
    }

    private void initImageReader(){
        mImageReader = ImageReader.newInstance(1920,1080, ImageFormat.JPEG,20);
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
                    ThumbnailImage=getImageThumbnail(file.toString(),1920,1920);
                    imageView.setImageBitmap(ThumbnailImage);
                    imageView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            /*Intent intent = new Intent(MainActivity.this,ShowImageActivity.class);
                            Bundle bundle = new Bundle();
                            bundle.putSerializable("image",file.toString());
                            intent.putExtras(bundle);
                            startActivity(intent);*/
                            //Intent intent = new Intent(Intent.ACTION_PICK, null);
                            //intent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            Uri mUri =  FileProvider.getUriForFile(getApplicationContext(),"com.example.photograph.FileProvider",file);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);//授予临时权限
                            intent.setDataAndType(mUri,"image/*");
                            startActivity(intent);

                        }
                    });
                    fileOutputStream.flush();
                    fileOutputStream.close();
                    image.close();
                }catch (IOException e)
                {
                    e.printStackTrace();
                }

            }
        },mChildHandler);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

    }



    /*
        获取匹配的大小
         */
    private Size getMatchingSize(){
        Size selectSize = null;
        float selectProportion = 0;
        try {
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
    private void takePicture()
    {
        CaptureRequest.Builder captureBuilder = null;
        try {
            captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            Surface surface = mImageReader.getSurface();

            //设置自动对焦模式
            /*captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            //设置自动曝光模式
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);*/
            captureBuilder.addTarget(surface);
            Log.v("ppppppppppppp", String.valueOf(surface));

            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);
            CaptureRequest request = captureBuilder.build();
            long date = System.currentTimeMillis();
            mCameraSession.capture(request, new CameraCaptureSession.CaptureCallback() {
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
                    long data1 = System.currentTimeMillis();
                    Log.v("d1d1d1d1", String.valueOf(data1));
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
            }, mChildHandler);
            Toast.makeText( MainActivity.this, "拍照成功", Toast.LENGTH_SHORT ).show();


        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    /**
     * 根据指定的图像路径和大小来获取缩略图
     * 此方法有两点好处：
     *     1. 使用较小的内存空间，第一次获取的bitmap实际上为null，只是为了读取宽度和高度，
     *        第二次读取的bitmap是根据比例压缩过的图像，第三次读取的bitmap是所要的缩略图。
     *     2. 缩略图对于原图像来讲没有拉伸，这里使用了2.2版本的新工具ThumbnailUtils，使
     *        用这个工具生成的图像不会被拉伸。
     * @param imagePath 图像的路径
     * @param width 指定输出图像的宽度
     * @param height 指定输出图像的高度
     * @return 生成的缩略图
     */

    private Bitmap getImageThumbnail(String imagePath, int width, int height) {
        Bitmap bitmap = null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        // 获取这个图片的宽和高，注意此处的bitmap为null
        bitmap = BitmapFactory.decodeFile(imagePath, options);
        options.inJustDecodeBounds = false; // 设为 false
        // 计算缩放比
        int h = options.outHeight;
        int w = options.outWidth;
        int beWidth = w / width;
        int beHeight = h / height;
        int be = 1;
        if (beWidth < beHeight) {
            be = beWidth;
        } else {
            be = beHeight;
        }
        if (be <= 0) {
            be = 1;
        }
        options.inSampleSize = be;
        // 重新读入图片，读取缩放后的bitmap，注意这次要把options.inJustDecodeBounds 设为 false
        bitmap = BitmapFactory.decodeFile(imagePath, options);
        // 利用ThumbnailUtils来创建缩略图，这里要指定要缩放哪个Bitmap对象
        bitmap = ThumbnailUtils.extractThumbnail(bitmap, width, height,
                ThumbnailUtils.OPTIONS_RECYCLE_INPUT);
        return bitmap;
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
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
        if (mCameraSession != null) {
            try {
                mCameraSession.stopRepeating();
                mCameraSession.abortCaptures();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            mCameraSession = null;
        }

        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (mChildHandler != null) {
            mChildHandler.removeCallbacksAndMessages(null);
            mChildHandler = null;
        }
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            mHandlerThread = null;
        }

        mCameraManager = null;
        mCaptureSessionStateCallback = null;
        mCaptureSessionCaptureCallback = null;
        mStateCallback = null;
    }



}
