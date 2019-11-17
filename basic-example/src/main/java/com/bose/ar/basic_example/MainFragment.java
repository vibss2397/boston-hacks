package com.bose.ar.basic_example;

//
//  MainFragment.java
//  BoseWearable
//
//  Created by Tambet Ingo on 12/10/2018.
//  Copyright © 2018 Bose Corporation. All rights reserved.
//
//, View.OnClickListener
import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProviders;

import com.bose.blecore.DeviceException;
import com.bose.wearable.sensordata.Quaternion;
import com.bose.wearable.sensordata.SensorValue;
import com.bose.wearable.sensordata.Vector;
import com.google.android.material.snackbar.Snackbar;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.io.IOException;
import java.util.Locale;

import android.widget.ImageView;

import static android.content.Context.SENSOR_SERVICE;

public class MainFragment extends Fragment implements SensorEventListener, StepListener{
    public static final String ARG_DEVICE_ADDRESS = "device-address";
    public static final String ARG_USE_SIMULATED_DEVICE = "use-simulated-device";
    private static final String TAG = MainFragment.class.getSimpleName();
    private static final Quaternion TRANSLATION_Q = new Quaternion(1, 0, 0, 0);

    private String mDeviceAddress;
    private boolean mUseSimulatedDevice;
    @SuppressWarnings("PMD.SingularField") // Need to keep a reference to it so it does not get GC'd
    private MainViewModel mViewModel;
    private View mParentView;
    @Nullable
    private ProgressBar mProgressBar;


    private SimpleStepDetector simpleStepDetector;
    private SensorManager sensorManager;
    private Sensor accel;
    private static final String TEXT_NUM_STEPS = "Number of Steps: ";
    private int numSteps;
    private boolean flag = false;
    private float baseline_myaw = -1000;
    public MediaPlayer mediaPlayer;
    private int counter = 0;
    public final float stride = 0.5f;
    public float quaternion_temp;
    public float[] coords = {0, 0};

    private ImageView pic1, pic2, pic3, pic4;

    public locations[] locarr = new locations[4];
    public float DistThreshold = 10 ;
    public float maxvol = 35;
    public boolean songPlaying = false;
    public TextView description;
    @Nullable
    private Snackbar mSnackBar;

    public class locations {
        public float x;
        public float y;
        public String song;
    }

    @Override
    public void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle args = getArguments();
        if (args != null) {
            mDeviceAddress = args.getString(ARG_DEVICE_ADDRESS);
            mUseSimulatedDevice = args.getBoolean(ARG_USE_SIMULATED_DEVICE, false);
        }

        if (mDeviceAddress == null && !mUseSimulatedDevice) {
            throw new IllegalArgumentException();
        }
        sensorManager = (SensorManager) getActivity().getSystemService(SENSOR_SERVICE);
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        simpleStepDetector = new SimpleStepDetector();
        simpleStepDetector.registerListener(this);

        locations screen1 = new locations( );
        screen1.x=0; screen1.y = 0; screen1.song = "a.mp3";

        locations screen2 = new locations( );
        screen2.x=30; screen2.y = 0; screen2.song = "b.mp3";

        locations booth = new locations( );
        booth.x=30; booth.y = 25; booth.song = "c.mp3";

        locations pillar = new locations( );
        pillar.x=0; pillar.y = 25 ; pillar.song = "d.mp3";

        locarr[0] = screen1;
        locarr[1] = screen2;
        locarr[2] = booth;
        locarr[3] = pillar;
        mediaPlayer = new MediaPlayer();

        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
//        mediaPlayer.start();


    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
//        img.setOnClickListener(new OnClickListener() {
//            //something
//        });
        return inflater.inflate(R.layout.fragment_main, container, false);
    }
//    @Override
//    public void onClick(View v) {
//        Log.d("HELLO", "WORLD");
//        if (v.getId() == R.id.pic4) {
//            // Launching new Activity on hitting the image
//            Intent j = new Intent(getActivity().getApplicationContext(), MainFragment.class);
//            startActivity(j);
//        }
//    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable final Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mParentView = view.findViewById(R.id.container);

        pic1 = view.findViewById(R.id.pic1);
        pic2 = view.findViewById(R.id.pic2);
        pic3 = view.findViewById(R.id.pic3);
        pic4 = view.findViewById(R.id.pic4);
        description = view.findViewById(R.id.description);
    }

    @Override
    public void onActivityCreated(@Nullable final Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final Activity activity = requireActivity();
        mProgressBar = activity.findViewById(R.id.progressbar);

        mViewModel = ViewModelProviders.of(this)
            .get(MainViewModel.class);

        mViewModel.busy()
            .observe(this, this::onBusy);

        mViewModel.errors()
            .observe(this, this::onError);

        mViewModel.sensorsSuspended()
            .observe(this, this::onSensorsSuspended);

        mViewModel.accelerometerData()
            .observe(this, this::onAccelerometerData);

        mViewModel.rotationData()
            .observe(this, this::onRotationData);

        if (mDeviceAddress != null) {
            mViewModel.selectDevice(mDeviceAddress);
        } else if (mUseSimulatedDevice) {
            mViewModel.selectSimulatedDevice();
        }
    }

    @Override
    public void onDestroy() {
        onBusy(false);

        final Snackbar snackbar = mSnackBar;
        mSnackBar = null;
        if (snackbar != null) {
            snackbar.dismiss();
        }

        super.onDestroy();
    }
    @Override
    public void onResume() {
        super.onResume();
        numSteps = 0;

        sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_FASTEST);
    }

    @Override
    public void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            try {
                simpleStepDetector.updateAccel(
                        event.timestamp, event.values[0], event.values[1], event.values[2]);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void step(long timeNs){
        numSteps++;
        Log.d("asd", "yaaha"+numSteps);
        coords = distanceCalculator(quaternion_temp, 1, coords[0], coords[1]);
        getPos(coords[0], coords[1], baseline_myaw);
    }

    private void onBusy(final boolean isBusy) {
        if (mProgressBar != null) {
            mProgressBar.setVisibility(isBusy ? View.VISIBLE : View.INVISIBLE);
        }

        final Activity activity = getActivity();
        final Window window = activity != null ? activity.getWindow() : null;
        if (window != null) {
            if (isBusy) {
                window.setFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            }
        }
    }

    private void onError(@NonNull final Event<DeviceException> event) {
        final DeviceException deviceException = event.get();
        if (deviceException != null) {
            showError(deviceException.getMessage());
            getFragmentManager().popBackStack();
        }
    }

    private void onSensorsSuspended(final boolean isSuspended) {
        final Snackbar snackbar;
        if (isSuspended) {
            snackbar = Snackbar.make(mParentView, R.string.sensors_suspended,
                Snackbar.LENGTH_INDEFINITE);
        } else if (mSnackBar != null) {
            snackbar = Snackbar.make(mParentView, R.string.sensors_resumed,
                Snackbar.LENGTH_SHORT);
        } else {
            snackbar = null;
        }

        if (snackbar != null) {
            snackbar.show();
        }

        mSnackBar = snackbar;
    }

    @SuppressWarnings("PMD.ReplaceVectorWithList") // PMD confuses SDK Vector with java.util.Vector
    private void onAccelerometerData(@NonNull final SensorValue sensorValue) {
        final Vector vector = sensorValue.vector();
//        mX.setText(formatValue(vector.x()));
//        mY.setText(formatValue(vector.y()));
//        mZ.setText(formatValue(vector.z()));
    }

    private void onRotationData(@NonNull final SensorValue sensorValue) {
        final Quaternion quaternion = Quaternion.multiply(sensorValue.quaternion(), TRANSLATION_Q);

        if(baseline_myaw ==-1000) {
            Log.d("As", "Pehli baar");
            baseline_myaw = formatAngle2(-quaternion.zRotation());
//            mediaPlayer.pause();
        }

        float diff = formatAngle2(-quaternion.zRotation())-baseline_myaw;

        quaternion_temp = diff;
//        if(diff>25){
//
//            if(flag==false) {
//                mPitch.setText("Song Shouldnt Play" + counter);
//                counter++;
//                flag = true;
//                mediaPlayer.start();
//            }
//        }
//        else{
//
//            if(flag==true){
//
//                mediaPlayer.pause();
//            }
//            flag = false;
//            mediaPlayer.pause();

//            float log1 = (float)(Math.log(maxvol-(2*diff-1))/Math.log(maxvol));
//            mRoll.setText("Song Should play"+);
//            mediaPlayer.setVolume(30, 30);

//        }
    }

    private void showError(final String message) {
        final Context context = getContext();
        if (context != null) {
            final Toast toast = Toast.makeText(context, message, Toast.LENGTH_LONG);
            toast.setGravity(Gravity.CENTER, 0, 0);
            toast.show();
        } else {
            Log.e(TAG, "Device error: " + message);
        }
    }

    private static String formatValue(final double value) {
        return String.format(Locale.US, "%.3f", value);
    }

    private static String formatAngle(final double radians) {
        final double degrees = radians * 180 / Math.PI;
        return String.format(Locale.US, "%.2f°", degrees);
    }

    private static float formatAngle2(final double radians) {
        final double degrees = radians * 180 / Math.PI;
        return (float) degrees;
    }

     public float[] distanceCalculator(float yaw, int steps, float x1, float y1) {
        float[] rtn = new float[2];

        float distance = stride * steps;
        if(-30<yaw && yaw<30){
            yaw = 0;
        }
        else if(30<yaw && yaw<60){
            yaw = 45;
        }
        else if(60<yaw && yaw<120){
            yaw = 90;
        }
        else if(120<yaw && yaw<150){
            yaw = 135;
        }
        else if(150<yaw || yaw<-150){
            yaw = 180;
        }
        else if(-150<yaw && yaw<-120){
            yaw = -135;
        }
        else if(-120<yaw && yaw<-60){
            yaw = -90;
        }
        else if(-60<yaw && yaw<30){
            yaw = -45;
        }
        float x = (float) (distance * Math.cos(yaw*Math.PI/180));
        float y = (float) (distance * Math.sin(yaw*Math.PI/180));
        rtn[0] = x + x1;
        rtn[1] = y + y1;


        return rtn;
    }

    void getPos(float x,float y,float yaw) {
        int count = 0;
        for (int i=0; i<4;i++){
            float dist = (float)Math.sqrt((x-locarr[i].x)*(x-locarr[i].x)+(y-locarr[i].y)* (y-locarr[i].y));
            if(dist<DistThreshold){
                float volume = (float)(Math.pow(((DistThreshold-dist)/DistThreshold), 2)* maxvol);
                String song = locarr[i].song;
                playSong(song,volume);
            }
            else{
                count++;
            }
        }
        if(count==4){
            stopSong();
        }
    }

    void playSong(String song,float volume) {
        Uri filename = Uri.parse("android.res://" + this.getContext().getPackageName()+"/raw/"+song);

        if(songPlaying==false){
            songPlaying = true;
            mediaPlayer.reset();
            if(song=="a.mp3"){
                pic1.setVisibility(View.VISIBLE);
                pic1.setMaxHeight(250);
                description.setText("The Mona Lisa (/ˌmoʊnə ˈliːsə/; Italian: Monna Lisa [ˈmɔnna ˈliːza] or La Gioconda [la dʒoˈkonda], French: La Joconde [la ʒɔkɔ̃d]) is a half-length portrait painting by the Italian Renaissance artist Leonardo da Vinci that has been described as \"the best known, the most visited, the most written about, the most sung about, the most parodied work of art in the world.\"[1] The Mona Lisa is also one of the most valuable paintings in the world. It holds the Guinness World Record for the highest known insurance valuation in history at US$100 million in 1962[2] (equivalent to $650 million in 2018).");
                pic2.getLayoutParams().height = pic1.getHeight() == 0 ? 0 : 100;
                pic3.getLayoutParams().height = pic1.getHeight() == 0 ? 0 : 100;
                pic4.getLayoutParams().height = pic1.getHeight() == 0 ? 0 : 100;

                mediaPlayer = MediaPlayer.create(getContext(), R.raw.a);
            }
            else if(song=="b.mp3"){
                pic2.setVisibility(View.VISIBLE);
                pic2.setMaxHeight(250);
                description.setText("B");

                pic1.getLayoutParams().height = pic1.getHeight() == 0 ? 0 : 100;
                pic3.getLayoutParams().height = pic1.getHeight() == 0 ? 0 : 100;
                pic4.getLayoutParams().height = pic1.getHeight() == 0 ? 0 : 100;

                mediaPlayer = MediaPlayer.create(getContext(), R.raw.b);
            }
            else if(song=="c.mp3"){
                pic3.setVisibility(View.VISIBLE);
                pic3.setMaxHeight(250);
                description.setText("C");
                pic1.getLayoutParams().height = pic1.getHeight() == 0 ? 0 : 100;
                pic2.getLayoutParams().height = pic1.getHeight() == 0 ? 0 : 100;
                pic4.getLayoutParams().height = pic1.getHeight() == 0 ? 0 : 100;

                mediaPlayer = MediaPlayer.create(getContext(), R.raw.c);
            }
            else if(song=="d.mp3"){
                pic4.setVisibility(View.VISIBLE);
                pic4.setMaxHeight(250);
                description.setText("D");
                pic1.getLayoutParams().height = pic1.getHeight() == 0 ? 0 : 100;
                pic2.getLayoutParams().height = pic1.getHeight() == 0 ? 0 : 100;
                pic3.getLayoutParams().height = pic1.getHeight() == 0 ? 0 : 100;

                mediaPlayer = MediaPlayer.create(getContext(), R.raw.d);
            }

            mediaPlayer.start();
            mediaPlayer.setVolume(volume, volume);

        }else {
            mediaPlayer.setVolume(volume,volume);

        }

        pic1.requestLayout();
        pic2.requestLayout();
        pic3.requestLayout();
        pic4.requestLayout();
        description.setVisibility(View.VISIBLE);
    }

    void stopSong(){
        mediaPlayer.pause();
        songPlaying = false;
    }

}
