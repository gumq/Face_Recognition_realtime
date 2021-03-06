package com.tranlequyen.facerecognition;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;


import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import android.os.ParcelFileDescriptor;
import android.speech.tts.TextToSpeech;
import android.text.InputType;
import android.util.Pair;
import android.util.Size;
import android.view.View;

import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    FaceDetector detector;
    TextToSpeech toSpeech;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    PreviewView previewView;
    ImageView face_preview;
    Interpreter tfLite;
    TextView reco_name,preview_info;
    Button recognize,camera_switch, actions;
    ImageButton add_face;
    CameraSelector cameraSelector;
    boolean start=true,flipX=false;
    Context context=MainActivity.this;
    int cam_face=CameraSelector.LENS_FACING_BACK; //M??y ???nh m???t sau m???c ?????nh

    int[] intValues;
    int inputSize=112;  //K??ch th?????c ?????u v??o cho m?? h??nh
    boolean isModelQuantized=false;
    float[][] embeedings;
    float IMAGE_MEAN = 128.0f;
    float IMAGE_STD = 128.0f;
    int OUTPUT_SIZE=192; //K??ch th?????c ?????u ra c???a m?? h??nh
    private static int SELECT_PICTURE = 1;
    ProcessCameraProvider cameraProvider;
    private static final int MY_CAMERA_REQUEST_CODE = 100;

    String modelFile="mobile_face_net.tflite"; //t??n m?? h??nh

    private HashMap<String, SimilarityClassifier.Recognition> registered = new HashMap<>(); //khu??n m???t ???? l??u
    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        registered=readFromSP(); //T???i c??c khu??n m???t ???? l??u t??? b??? nh??? khi ???ng d???ng kh???i ?????ng
        setContentView(R.layout.activity_main);
        face_preview =findViewById(R.id.imageView);
        reco_name =findViewById(R.id.textView);
        preview_info =findViewById(R.id.textView2);
        add_face=findViewById(R.id.imageButton);
        add_face.setVisibility(View.INVISIBLE);

        face_preview.setVisibility(View.INVISIBLE);
        recognize=findViewById(R.id.button3);
        camera_switch=findViewById(R.id.button5);
        actions=findViewById(R.id.button2);
        preview_info.setText("\n        Nh???n di???n khu??n m???t:");
        //Camera Permission
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, MY_CAMERA_REQUEST_CODE);
        }
        //N??t h??nh ?????ng tr??n m??n h??nh
        actions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle("Ch???n thao t??c:");

                    // th??m m???t danh s??ch checkbox
                String[] names= {"Xem danh s??ch","X??a Khu??n M???t Trong Danh S??ch","X??a to??n b??? danh s??ch nh???n di???n","Nh???p ???nh c?? s???n"};

                builder.setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        switch (which)
                        {
                            case 0:
                                displaynameListview();
                                break;
                            case 1:
                                updatenameListview();
                                break;
                            case 2:
                                clearnameList();
                                break;
                            case 3:
                                loadphoto();
                                break;
                        }

                    }
                });


                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
                builder.setNegativeButton("Cancel", null);

                    // create and show the alert dialog
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });

        //On-screen switch to toggle between Cameras.
        camera_switch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (cam_face==CameraSelector.LENS_FACING_BACK) {
                    cam_face = CameraSelector.LENS_FACING_FRONT;
                    flipX=true;
                }
                else {
                    cam_face = CameraSelector.LENS_FACING_BACK;
                    flipX=false;
                }
                cameraProvider.unbindAll();
                cameraBind();
            }
        });

        add_face.setOnClickListener((new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                addFace();
            }
        }));


        recognize.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(recognize.getText().toString().equals("Ch???c n??ng nh???n di???n"))
                {
                 start=true;
                recognize.setText("Th??m g????ng m???t v??o danh s??ch");
                add_face.setVisibility(View.INVISIBLE);
                reco_name.setVisibility(View.VISIBLE);
                face_preview.setVisibility(View.INVISIBLE);
                preview_info.setText("\n G????ng m???t ???????c nh???n di???n:");

               // preview_info.setVisibility(View.INVISIBLE);
                }
                else
                {
                    recognize.setText("Ch???c n??ng nh???n di???n");
                    add_face.setVisibility(View.VISIBLE);
                    reco_name.setVisibility(View.INVISIBLE);
                    face_preview.setVisibility(View.VISIBLE);
                    preview_info.setText("1.?????t khu??n m???t ngay camera.\n\n2.B???n xem tr?????c khu??n m???t c???a b???n s??? xu???t hi???n ??? ????y.\n\n3.Nh???p v??o n??t Th??m ????? l??u khu??n m???t.");


                }

            }
        });

        //Load model
        try {
            tfLite=new Interpreter(loadModelFile(MainActivity.this,modelFile));
        } catch (IOException e) {
            e.printStackTrace();
        }
        //Initialize Face Detector
        FaceDetectorOptions highAccuracyOpts =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)//??u ti??n t???c ????? ho???c ????? ch??nh x??c khi ph??t hi???n khu??n m???t.
                        .build();
        //Cho bi???t ??u ti??n v??? ????? ch??nh x??c trong c??c t??y ch???n c?? th??? t???o ra s??? c??n b???ng gi???a ????? ch??nh x??c v?? t???c ?????. ??i???u n??y s??? c?? xu h?????ng ph??t hi???n nhi???u khu??n m???t h??n v?? c?? th??? ch??nh x??c h??n trong vi???c x??c ?????nh c??c gi?? tr??? nh?? v??? tr??, v???i chi ph?? l?? t???c ?????.
        detector = FaceDetection.getClient(highAccuracyOpts);

        cameraBind();



    }
    private void addFace()
    {
        {

            start=false;
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle("Nh???p t??n ?????i t?????ng");

                // Thi???t l???p ?????u v??o
            final EditText input = new EditText(context);

            input.setInputType(InputType.TYPE_CLASS_TEXT );
            builder.setView(input);

                // Set up the buttons
            builder.setPositiveButton("TH??M", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    //Toast.makeText(context, input.getText().toString(), Toast.LENGTH_SHORT).show();

                    //T???o v?? kh???i t???o ?????i t?????ng m???i v???i t??nh n??ng Nh??ng khu??n m???t v?? ?????t t??n.
                    SimilarityClassifier.Recognition result = new SimilarityClassifier.Recognition(
                            "0", "", -1f);
                    result.setExtra(embeedings);

                    registered.put( input.getText().toString(),result);
                    start=true;

                }
            });
            builder.setNegativeButton("H???Y", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    start=true;
                    dialog.cancel();
                }
            });

            builder.show();
        }
    }
    private  void clearnameList()
    {
        AlertDialog.Builder builder =new AlertDialog.Builder(context);
        builder.setTitle("B???n c?? mu???n x??a t???t c??? b???n nh???n di???n kh??ng?");
        builder.setPositiveButton("X??a t???t c???!", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                registered.clear();
                Toast.makeText(context, "Khu??n m???t ???? ???????c x??a", Toast.LENGTH_SHORT).show();
            }
        });
        insertToSP(registered,true);
        builder.setNegativeButton("H???y",null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    private void updatenameListview()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        if(registered.isEmpty()) {
            builder.setTitle("Kh??ng c?? khu??n m???t n??o ???????c th??m v??o !!");
            builder.setPositiveButton("OK",null);
        }
        else{
            builder.setTitle("Ch???n b???n nh???n di???n mu???n x??a:");

        // add a checkbox list
        String[] names= new String[registered.size()];
        boolean[] checkedItems = new boolean[registered.size()];
         int i=0;
                for (Map.Entry<String, SimilarityClassifier.Recognition> entry : registered.entrySet())
                {
                    //System.out.println("NAME"+entry.getKey());
                    names[i]=entry.getKey();
                    checkedItems[i]=false;
                    i=i+1;

                }

                builder.setMultiChoiceItems(names, checkedItems, new DialogInterface.OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        // user checked or unchecked a box
                        //Toast.makeText(MainActivity.this, names[which], Toast.LENGTH_SHORT).show();
                       checkedItems[which]=isChecked;

                    }
                });


        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                       // System.out.println("status:"+ Arrays.toString(checkedItems));
                        for(int i=0;i<checkedItems.length;i++)
                        {
                            //System.out.println("status:"+checkedItems[i]);
                            if(checkedItems[i])
                            {
//                                Toast.makeText(MainActivity.this, names[i], Toast.LENGTH_SHORT).show();
                                registered.remove(names[i]);
                            }

                        }
                Toast.makeText(context, "C???p nh???n khu??n m???t c???n nh???n di???n", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("H???y", null);

        // create and show the alert dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    }
    private void displaynameListview()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
       // System.out.println("Registered"+registered);
        if(registered.isEmpty())
            builder.setTitle("Kh??ng c?? khu??n m???t n??o ???????c th??m v??o !!");
        else
            builder.setTitle("Recognitions:");

        // add a checkbox list
        String[] names= new String[registered.size()];
        boolean[] checkedItems = new boolean[registered.size()];
        int i=0;
        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : registered.entrySet())
        {
            //System.out.println("NAME"+entry.getKey());
            names[i]=entry.getKey();
            checkedItems[i]=false;
            i=i+1;

        }
        builder.setItems(names,null);



        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

            }
        });

            // create and show the alert dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Quy???n m??y ???nh ???? ???????c c???p", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Quy???n m??y ???nh b??? t??? ch???i", Toast.LENGTH_LONG).show();
            }
        }
    }

    private MappedByteBuffer loadModelFile(Activity activity, String MODEL_FILE) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    //R??ng bu???c m??y ???nh v?? ch??? ????? xem tr?????c
    private void cameraBind()
    {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        previewView=findViewById(R.id.previewView);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                // Kh??ng c?? l???i n??o c???n ???????c x??? l?? cho vi???c n??y trong T????ng lai.
                // ??i???u n??y kh??ng bao gi??? n??n ?????t ???????c.
            }
        }, ContextCompat.getMainExecutor(this));
    }
    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .build();

        cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(cam_face)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        ImageAnalysis imageAnalysis =
                new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) //Khung h??nh m???i nh???t ???????c hi???n th???
                        .build();

        Executor executor = Executors.newSingleThreadExecutor();
        imageAnalysis.setAnalyzer(executor, new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy imageProxy) {

                InputImage image = null;


                @SuppressLint("UnsafeExperimentalUsageError")
                // Camera Feed-->Analyzer-->ImageProxy-->mediaImage-->InputImage(c???n thi???t ????? ph??t hi???n khu??n m???t kit ML)

                Image mediaImage = imageProxy.getImage();

                if (mediaImage != null) {
                    image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
                //    System.out.println("Rotation "+imageProxy.getImageInfo().getRotationDegrees());
                }

                System.out.println("PH??N T??CH");

                //X??? l?? h??nh ???nh thu ???????c ????? ph??t hi???n khu??n m???t
                Task<List<Face>> result =
                        detector.process(image)
                                .addOnSuccessListener(
                                        new OnSuccessListener<List<Face>>() {
                                            @Override
                                            public void onSuccess(List<Face> faces) {

                                                if(faces.size()!=0) {
                                                    Face face = faces.get(0); //Nh???n khu??n m???t ?????u ti??n t??? nh???ng khu??n m???t ???????c ph??t hi???n
                                                    System.out.println(face);

                                                    //mediaImage to Bitmap
                                                    Bitmap frame_bmp = toBitmap(mediaImage);

                                                    int rot = imageProxy.getImageInfo().getRotationDegrees();

                                                    //??i???u ch???nh h?????ng c???a khu??n m???t
                                                    Bitmap frame_bmp1 = rotateBitmap(frame_bmp, rot, false, false);



                                                    //Nh???n h???p gi???i h???n c???a khu??n m???t
                                                    RectF boundingBox = new RectF(face.getBoundingBox());

                                                    //Crop out bounding box from whole Bitmap(image)
                                                    Bitmap cropped_face = getCropBitmapByCPU(frame_bmp1, boundingBox);

                                                    if(flipX)
                                                        cropped_face = rotateBitmap(cropped_face, 0, flipX, false);
                                                    //Scale the acquired Face to 112*112 which is required input for model
                                                    Bitmap scaled = getResizedBitmap(cropped_face, 112, 112);

                                                    if(start)
                                                        recognizeImage(scaled); //G???i bitmap ???????c chia t??? l??? ????? t???o nh??ng khu??n m???t.
                                                    System.out.println(boundingBox);
                                                    try {
                                                        Thread.sleep(10);  //Xem tr?????c m??y ???nh ???????c l??m m???i sau m???i 10 mili gi??y (??i???u ch???nh theo y??u c???u)
                                                    } catch (InterruptedException e) {
                                                        e.printStackTrace();
                                                    }
                                                }
                                                else
                                                {
                                                    if(registered.isEmpty())
                                                        reco_name.setText("Th??m khu??n m???t v??o danh s??ch");
                                                    else
                                                        reco_name.setText("H??? Th???ng kh??ng ph??t hi???n khu??n m???t n??o, Vui l??ng th??? l???i!!");

                                                }

                                            }
                                        })
                                .addOnFailureListener(
                                        new OnFailureListener() {
                                            @Override
                                            public void onFailure(@NonNull Exception e) {
                                                // T??c v??? kh??ng th??nh c??ng v???i m???t ngo???i l???
                                                // ...
                                            }
                                        })
                                .addOnCompleteListener(new OnCompleteListener<List<Face>>() {
                            @Override
                            public void onComplete(@NonNull Task<List<Face>> task) {

                                imageProxy.close(); //v. quan tr???ng ????? c?? ???????c khung ti???p theo ????? ph??n t??ch
                            }
                        });


            }
        });


        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, imageAnalysis, preview);


    }

    public void recognizeImage(final Bitmap bitmap) {

        // ?????t Khu??n m???t th??nh Xem tr?????c
        face_preview.setImageBitmap(bitmap);

        //T???o ByteBuffer ????? l??u tr??? h??nh ???nh chu???n h??a

        ByteBuffer imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4);

        imgData.order(ByteOrder.nativeOrder());

        intValues = new int[inputSize * inputSize];

        //l???y gi?? tr??? pixel t??? Bitmap ????? chu???n h??a
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        imgData.rewind();

        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                if (isModelQuantized) {
                    // M?? h??nh l?????ng t??? h??a
                    imgData.put((byte) ((pixelValue >> 16) & 0xFF));
                    imgData.put((byte) ((pixelValue >> 8) & 0xFF));
                    imgData.put((byte) (pixelValue & 0xFF));
                } else { // Float model
                    imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);

                }
            }
        }
        //imgData is input to our model
        Object[] inputArray = {imgData};

        Map<Integer, Object> outputMap = new HashMap<>();


        embeedings = new float[1][OUTPUT_SIZE]; //?????u ra c???a m?? h??nh s??? ???????c l??u tr??? trong bi???n n??y

        outputMap.put(0, embeedings);

        tfLite.runForMultipleInputsOutputs(inputArray, outputMap); //Run model



        float distance = Float.MAX_VALUE;
        String id = "0";
        String label = "?";

        //So s??nh khu??n m???t m???i v???i c??c khu??n m???t ???? l??u.
        if (registered.size() > 0) {

            final Pair<String, Float> nearest = findNearest(embeedings[0]);//T??m khu??n m???t ph?? h???p nh???t

            if (nearest != null) {

                final String name = nearest.first;
                label = name;
                distance = nearest.second;
                if(distance<1.000f) //N???u kho???ng c??ch gi???a khu??n m???t ???????c t??m th???y g???n nh???t l?? h??n 1.000, th?? xu???t ra khu??n m???t UNKNOWN.
                {    reco_name.setText(name);

                    toSpeech = new TextToSpeech ( MainActivity.this, new TextToSpeech.OnInitListener () {
                        @Override
                        public void onInit(int status) {
                            if(status!=TextToSpeech.ERROR) {
                                toSpeech.setLanguage ( Locale.ENGLISH );
                                toSpeech.speak ("faces in the list:   ,"+name,TextToSpeech.QUEUE_FLUSH,null,name );
                                Objects.requireNonNull ( toSpeech );

                            }
                        }
                    } );


                }



                else{

                        reco_name.setText("Kh??ng r??");
                        System.out.println("G???n nh???t: " + name + " - kho???ng c??ch: " + distance);
                        toSpeech = new TextToSpeech ( MainActivity.this, new TextToSpeech.OnInitListener () {
                            @Override
                            public void onInit(int status) {
                                if(status!=TextToSpeech.ERROR){
                                    toSpeech.setLanguage ( Locale.ENGLISH);
                                    toSpeech.speak ( "Unknown face",TextToSpeech.QUEUE_FLUSH,null,"unknown" );

                                }
                            }
                        } );
                    }





                }
            }


//            final int numDetectionsOutput = 1;
//            final ArrayList<SimilarityClassifier.Recognition> recognitions = new ArrayList<>(numDetectionsOutput);
//            SimilarityClassifier.Recognition rec = new SimilarityClassifier.Recognition(
//                    id,
//                    label,
//                    distance);
//
//            recognitions.add( rec );

    }
    public void onPause(){
        if(toSpeech !=null){
            toSpeech.stop();
           toSpeech.shutdown();
        }
        super.onPause();
    }
//    public void register(String name, SimilarityClassifier.Recognition rec) {
//        registered.put(name, rec);
//    }

    //So s??nh c??c khu??n m???t theo kho???ng c??ch gi???a c??c l???n nh??ng khu??n m???t
    private Pair<String, Float> findNearest(float[] emb) {

        Pair<String, Float> ret = null;
        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : registered.entrySet()) {

            final String name = entry.getKey();
           final float[] knownEmb = ((float[][]) entry.getValue().getExtra())[0];

            float distance = 0;
            for (int i = 0; i < emb.length; i++) {
                float diff = emb[i] - knownEmb[i];
                distance += diff*diff;
            }
            distance = (float) Math.sqrt(distance);
            if (ret == null || distance < ret.second) {
                ret = new Pair<>(name, distance);
            }
        }

        return ret;

    }
    public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        bm.recycle();
        return resizedBitmap;
    }
    private static Bitmap getCropBitmapByCPU(Bitmap source, RectF cropRectF) {
        Bitmap resultBitmap = Bitmap.createBitmap((int) cropRectF.width(),
                (int) cropRectF.height(), Bitmap.Config.ARGB_8888);
        Canvas cavas = new Canvas(resultBitmap);

        // draw background
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setColor(Color.WHITE);
        cavas.drawRect(//from  w w  w. ja v  a  2s. c  om
                new RectF(0, 0, cropRectF.width(), cropRectF.height()),
                paint);

        Matrix matrix = new Matrix();
        matrix.postTranslate(-cropRectF.left, -cropRectF.top);

        cavas.drawBitmap(source, matrix, paint);

        if (source != null && !source.isRecycled()) {
            source.recycle();
        }

        return resultBitmap;
    }

    private static Bitmap rotateBitmap(
            Bitmap bitmap, int rotationDegrees, boolean flipX, boolean flipY) {
        Matrix matrix = new Matrix();

        // Xoay h??nh ???nh tr??? l???i th???ng.
        matrix.postRotate(rotationDegrees);

        // Ph???n chi???u h??nh ???nh d???c theo tr???c X ho???c Y.
        matrix.postScale(flipX ? -1.0f : 1.0f, flipY ? -1.0f : 1.0f);
        Bitmap rotatedBitmap =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        // Recycle the old bitmap if it has changed.
        if (rotatedBitmap != bitmap) {
            bitmap.recycle();
        }
        return rotatedBitmap;
    }

    //IMPORTANT. N???u chuy???n ?????i kh??ng ???????c th???c hi???n, chuy???n ?????i toBitmap kh??ng ho???t ?????ng tr??n m???t s??? thi???t b???.
    private static byte[] YUV_420_888toNV21(Image image) {

        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = width*height;
        int uvSize = width*height/4;

        byte[] nv21 = new byte[ySize + uvSize*2];

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer(); // Y
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer(); // U
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer(); // V

        int rowStride = image.getPlanes()[0].getRowStride();
        assert(image.getPlanes()[0].getPixelStride() == 1);

        int pos = 0;

        if (rowStride == width) { // likely
            yBuffer.get(nv21, 0, ySize);
            pos += ySize;
        }
        else {
            long yBufferPos = -rowStride; // not an actual position
            for (; pos<ySize; pos+=width) {
                yBufferPos += rowStride;
                yBuffer.position((int) yBufferPos);
                yBuffer.get(nv21, pos, width);
            }
        }

        rowStride = image.getPlanes()[2].getRowStride();
        int pixelStride = image.getPlanes()[2].getPixelStride();

        assert(rowStride == image.getPlanes()[1].getRowStride());
        assert(pixelStride == image.getPlanes()[1].getPixelStride());

        if (pixelStride == 2 && rowStride == width && uBuffer.get(0) == vBuffer.get(1)) {
            // maybe V an U planes overlap as per NV21, which means vBuffer[1] is alias of uBuffer[0]
            byte savePixel = vBuffer.get(1);
            try {
                vBuffer.put(1, (byte)~savePixel);
                if (uBuffer.get(0) == (byte)~savePixel) {
                    vBuffer.put(1, savePixel);
                    vBuffer.position(0);
                    uBuffer.position(0);
                    vBuffer.get(nv21, ySize, 1);
                    uBuffer.get(nv21, ySize + 1, uBuffer.remaining());

                    return nv21; // shortcut
                }
            }
            catch (ReadOnlyBufferException ex) {
                // unfortunately, we cannot check if vBuffer and uBuffer overlap
            }

            // unfortunately, the check failed. We must save U and V pixel by pixel
            vBuffer.put(1, savePixel);
        }

        // other optimizations could check if (pixelStride == 1) or (pixelStride == 2),
        // nh??ng hi???u su???t ?????t ???????c s??? ??t ????ng k??? h??n

        for (int row=0; row<height/2; row++) {
            for (int col=0; col<width/2; col++) {
                int vuPos = col*pixelStride + row*rowStride;
                nv21[pos++] = vBuffer.get(vuPos);
                nv21[pos++] = uBuffer.get(vuPos);
            }
        }

        return nv21;
    }

    private Bitmap toBitmap(Image image) {

        byte[] nv21=YUV_420_888toNV21(image);


        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

        byte[] imageBytes = out.toByteArray();
        //System.out.println("bytes"+ Arrays.toString(imageBytes));

        //System.out.println("FORMAT"+image.getFormat());

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    //L??u Khu??n m???t v??o T??y ch???n Chia s???. Chuy???n ?????i c??c ?????i t?????ng Nh???n d???ng th??nh chu???i json
    private void insertToSP(HashMap<String, SimilarityClassifier.Recognition> jsonMap,boolean clear) {
        if(clear)
            jsonMap.clear();
        else
            jsonMap.putAll(readFromSP());
        String jsonString = new Gson().toJson(jsonMap);
//        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : jsonMap.entrySet())
//        {
//            System.out.println("Entry Input "+entry.getKey()+" "+  entry.getValue().getExtra());
//        }
        SharedPreferences sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("map", jsonString);
        //System.out.println("Input josn"+jsonString.toString());
        editor.apply();
        Toast.makeText(context, "B???n nh???n di???n ???? ???????c l??u", Toast.LENGTH_SHORT).show();
    }

    //T???i c??c khu??n m???t t??? c??c t??y ch???n ???????c chia s???. Chu???i json v??o ?????i t?????ng nh???n d???ng
    private HashMap<String, SimilarityClassifier.Recognition> readFromSP(){
        SharedPreferences sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE);
        String defValue = new Gson().toJson(new HashMap<String, SimilarityClassifier.Recognition>());
        String json=sharedPreferences.getString("map",defValue);
       // System.out.println("Output json"+json.toString());
        TypeToken<HashMap<String,SimilarityClassifier.Recognition>> token = new TypeToken<HashMap<String,SimilarityClassifier.Recognition>>() {};
        HashMap<String,SimilarityClassifier.Recognition> retrievedMap=new Gson().fromJson(json,token.getType());
       // System.out.println("Output map"+retrievedMap.toString());

        //During type conversion and save/load procedure,format changes(eg float converted to double).
        //So embeddings need to be extracted from it in required format(eg.double to float).
        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : retrievedMap.entrySet())
        {
            float[][] output=new float[1][OUTPUT_SIZE];
            ArrayList arrayList= (ArrayList) entry.getValue().getExtra();
            arrayList = (ArrayList) arrayList.get(0);
            for (int counter = 0; counter < arrayList.size(); counter++) {
                output[0][counter]= ((Double) arrayList.get(counter)).floatValue();
            }
            entry.getValue().setExtra(output);

            //System.out.println("Entry output "+entry.getKey()+" "+entry.getValue().getExtra() );

        }
//        System.out.println("OUTPUT"+ Arrays.deepToString(outut));
        Toast.makeText(context, "B???n nh???n di???n ???? ???????c t???i", Toast.LENGTH_SHORT).show();
        return retrievedMap;
    }

    //T???i ???nh t??? b??? nh??? ??i???n tho???i
    private void loadphoto()
    {
        start=false;
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), SELECT_PICTURE);
    }

    //Quy tr??nh ph??n t??ch t????ng t???
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == SELECT_PICTURE) {
                Uri selectedImageUri = data.getData();
                try {
                    InputImage impphoto=InputImage.fromBitmap(getBitmapFromUri(selectedImageUri),0);
                    detector.process(impphoto).addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                        @Override
                        public void onSuccess(List<Face> faces) {

                            if(faces.size()!=0) {
                                recognize.setText("Nh???n di???n g????ng m???t");
                                add_face.setVisibility(View.VISIBLE);
                                reco_name.setVisibility(View.INVISIBLE);
                                face_preview.setVisibility(View.VISIBLE);
                                preview_info.setText("1.?????t g????ng m???t tr?????c camera.\n\n2.B???n xem tr?????c khu??n m???t c???a b???n s??? xu???t hi???n ??? ????y.\n\n3.Nh???p v??o n??t Th??m ????? l??u.");
                                Face face = faces.get(0);
                                System.out.println(face);

                                //write code to recreate bitmap from source
                                //Write code to show bitmap to canvas

                                Bitmap frame_bmp= null;
                                try {
                                    frame_bmp = getBitmapFromUri(selectedImageUri);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                Bitmap frame_bmp1 = rotateBitmap(frame_bmp, 0, flipX, false);

                                //face_preview.setImageBitmap(frame_bmp1);


                                RectF boundingBox = new RectF(face.getBoundingBox());


                                Bitmap cropped_face = getCropBitmapByCPU(frame_bmp1, boundingBox);

                                Bitmap scaled = getResizedBitmap(cropped_face, 112, 112);
                                // face_preview.setImageBitmap(scaled);

                                    recognizeImage(scaled);
                                    addFace();
                                System.out.println(boundingBox);
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            start=true;
                            Toast.makeText(context, "Kh??ng th??m ???????c! Vui l??ng th??? l???i!", Toast.LENGTH_SHORT).show();
                        }
                    });
                    face_preview.setImageBitmap(getBitmapFromUri(selectedImageUri));
                } catch (IOException e) {
                    e.printStackTrace();
                }


            }
        }
    }

    private Bitmap getBitmapFromUri(Uri uri) throws IOException {
        ParcelFileDescriptor parcelFileDescriptor =
                getContentResolver().openFileDescriptor(uri, "r");
        FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
        parcelFileDescriptor.close();
        return image;
    }

}

