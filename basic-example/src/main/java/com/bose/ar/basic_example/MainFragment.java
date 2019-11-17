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
import android.media.MediaPlayer;
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
    private TextView mPitch;
    private TextView mRoll;
    private TextView mYaw;
    private TextView mX;
    private TextView mY;
    private TextView mZ;

    private SimpleStepDetector simpleStepDetector;
    private SensorManager sensorManager;
    private Sensor accel;
    private static final String TEXT_NUM_STEPS = "Number of Steps: ";
    private int numSteps;
    private boolean flag = false;
    private float baseline_myaw = -1000;
    private MediaPlayer mediaPlayer;
    private int counter = 0;
    public final float stride = 0.5f;
    public float quaternion_temp;
    public float[] coords = {0, 0};
    private ImageView pic1, pic2, pic3, pic4;

    @Nullable
    private Snackbar mSnackBar;

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
        mediaPlayer = MediaPlayer.create(getContext(), R.raw.song);
        mediaPlayer.start();

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

        mPitch = view.findViewById(R.id.pitch);
        mRoll = view.findViewById(R.id.roll);
        mYaw = view.findViewById(R.id.yaw);
        pic1 = view.findViewById(R.id.pic1);
        pic2 = view.findViewById(R.id.pic2);
        pic3 = view.findViewById(R.id.pic3);
        pic4 = view.findViewById(R.id.pic4);
        mX = view.findViewById(R.id.x);
        mY = view.findViewById(R.id.y);
        mZ = view.findViewById(R.id.z);
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
        mZ.setText(TEXT_NUM_STEPS + numSteps);
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
            simpleStepDetector.updateAccel(
                    event.timestamp, event.values[0], event.values[1], event.values[2]);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void step(long timeNs) {
        numSteps++;
        Log.d("asd", "yaaha"+numSteps);
        coords = distanceCalculator(quaternion_temp, 1, coords[0], coords[1]);
        mX.setText(""+ coords[0]+" "+coords[1]);
        mZ.setText(TEXT_NUM_STEPS + numSteps);
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
        mY.setText(formatValue(vector.y()));
//        mZ.setText(formatValue(vector.z()));
    }

    private void onRotationData(@NonNull final SensorValue sensorValue) {
        final Quaternion quaternion = Quaternion.multiply(sensorValue.quaternion(), TRANSLATION_Q);

        mPitch.setText(formatAngle(quaternion.xRotation()));
        mRoll.setText(formatAngle(-quaternion.yRotation()));
        mYaw.setText(formatAngle(-quaternion.zRotation()));
        if(baseline_myaw ==-1000) {
            Log.d("As", "Pehli baar");
            baseline_myaw = formatAngle2(-quaternion.zRotation());
            mediaPlayer.pause();
        }
        else{
            quaternion_temp = baseline_myaw-formatAngle2(-quaternion.zRotation());
        }
        if(formatAngle2(-quaternion.zRotation())-baseline_myaw>25){

            if(flag==false) {
                mPitch.setText("Song Should Play"+ counter);
                counter++;
                flag = true;
                mediaPlayer.start();
            }
        }
        else{
            mRoll.setText("Song Shouldnt play"+counter);
            if(flag==true){

                mediaPlayer.pause();
            }
            flag = false;

        }
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
        float x = (float) (distance * Math.cos(yaw*Math.PI/180));
        float y = (float) (distance * Math.sin(yaw*Math.PI/180));
        rtn[0] = x + x1;
        rtn[1] = y + y1;


        return rtn;
    }



}
