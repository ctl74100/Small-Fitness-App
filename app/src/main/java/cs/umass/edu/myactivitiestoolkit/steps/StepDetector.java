package cs.umass.edu.myactivitiestoolkit.steps;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.util.Log;

import java.util.ArrayList;

import cs.umass.edu.myactivitiestoolkit.constants.Constants;
import cs.umass.edu.myactivitiestoolkit.processing.Filter;

/**
 * This class is responsible for detecting steps from the accelerometer sensor.
 * All {@link OnStepListener step listeners} that have been registered will
 * be notified when a step is detected.
 */
public class StepDetector implements SensorEventListener {
    /** Used for debugging purposes. */
    @SuppressWarnings("unused")
    private static final String TAG = StepDetector.class.getName();

    /** Maintains the set of listeners registered to handle step events. **/
    private ArrayList<OnStepListener> mStepListeners;

    /**
     * The number of steps taken.
     */
    private int stepCount;



    //*************************OMGGGGGGG*************************///
    /**
     * CTL: Total Data count, used to filtered out the first few data that were counted when we put the phone into the pocket
     */
    private int dataCount = 0;
    /**
     * Exponential Smoothing filtering
     */
    private int ESFilterValue = 1;
    private Filter ESFiltering = new Filter(ESFilterValue);
    /**
     * Store data points to do the operation
     **/
    private ArrayList<float[]> dataBuffer = new ArrayList<float[]>();
    private ArrayList<Long> dataTimeBuffer = new ArrayList<Long>();
    /**
     * XYZ Max and Min
     **/
    private float x_max = -999,y_max= -999, z_max= -999;
    private float x_min = 999, y_min = 999, z_min = 999;
    /**
     * Threashold
     **/
    private float global_max = -1, global_min = -1,threshold = -1;
    private int axisToUse = -1,axisChangeRate = 5,axisChangeCD = 0;
    private float x_diff, y_diff,z_diff;
    private float lastData,currentData,slope;
    private int queuedSteps = 0;


    private void updateXYZMaxMin(float[] values) {
        if (values[0] >= x_max)
            x_max = values[0];
        else if (values[0] < x_min)
            x_min = values[0];
        if (values[1] >= y_max)
            y_max = values[1];
        else if (values[1] < y_min)
            y_min = values[1];
        if (values[2] >= z_max)
            z_max = values[2];
        else if (values[2] < z_min)
            z_min = values[2];
    }

    private void xyzMaxMinReset() {
        x_max = y_max = z_max = -999;
        x_min =  y_min = z_min = 999;
    }

    private void calculateThreshold() {
        if (axisToUse == 0) {
            global_max = x_max;
            global_min = x_min;
        } else if (axisToUse == 1) {
            global_max = y_max;
            global_min = y_min;
        } else if (axisToUse == 2) {
            global_max = z_max;
            global_min = z_min;
        }

        threshold = (global_max + global_min) / 2;
        //Log.d(TAG,"         DB Global Max: "+ global_max);
        //Log.d(TAG,"         DB Global Min: "+ global_min);
        //Log.d(TAG,"         DB Threshold: "+ threshold);
    }

    // Calculate the difference of the max and min in each axis, then decide which axis to use
    private void chooseAxis() {

        x_diff = Math.abs(x_max - x_min);
        y_diff = Math.abs(y_max - y_min);
        z_diff = Math.abs(z_max - z_min);


        if (x_diff > y_diff && x_diff > z_diff) {
            axisToUse = 0;
        } else if (y_diff > x_diff && y_diff > z_diff) {
            axisToUse = 1;
        } else if (z_diff > x_diff && z_diff > y_diff) {
            axisToUse = 2;
        }

        //Log.d(TAG, "            DB X- Diff: " + x_diff + " ; X-Max: " + x_max + " X-Min: " + x_min);
        //Log.d(TAG, "            DB Y- Diff: " + y_diff + " ; Y-Max: " + y_max + " Y-Min: " + y_min);
        //Log.d(TAG, "            DB Z - Diff: " + z_diff + " ; Z-Max: " + z_max + " Z-Min: " + z_min);

    }

    private void chooseAxisAdvance(){
        boolean firstCheck = !(x_max == -999 || y_max ==-999 || z_max == -999);
        boolean secondCheck = !(x_min == 999 || y_min ==999 || z_min == 999);
        if(axisChangeCD == 0 && firstCheck && secondCheck) {

            x_diff = Math.abs(x_max - x_min);
            y_diff = Math.abs(y_max - y_min);
            z_diff = Math.abs(z_max - z_min);

            if (x_diff > y_diff && x_diff > z_diff && x_diff >= 8) {

                Log.d(TAG, "            DB 4.5 X- Diff: " + x_diff + " ; X-Max: " + x_max + " X-Min: " + x_min);
                axisChangeCD = 20;
                if(axisToUse != 0)
                    onStepDetected(dataTimeBuffer.get(dataTimeBuffer.size()-1), dataBuffer.get(dataBuffer.size()-1));
                axisToUse = 0;
            } else if (y_diff > x_diff && y_diff > z_diff && y_diff >= 8) {
                Log.d(TAG, "            DB 4.5 Y- Diff: " + y_diff + " ; Y-Max: " + y_max + " Y-Min: " + y_min);
                axisChangeCD = 20;
                if(axisToUse != 1)
                    onStepDetected(dataTimeBuffer.get(dataTimeBuffer.size()-1), dataBuffer.get(dataBuffer.size()-1));
                axisToUse = 1;
            } else if (z_diff > x_diff && z_diff > y_diff && z_diff >= 8) {
                Log.d(TAG, "            DB 4.5 Z - Diff: " + z_diff + " ; Z-Max: " + z_max + " Z-Min: " + z_min);
                axisChangeCD = 20;
                if(axisToUse != 2)
                    onStepDetected(dataTimeBuffer.get(dataTimeBuffer.size()-1), dataBuffer.get(dataBuffer.size()-1));
                axisToUse = 2;
            }
        }
        else if(axisChangeCD>0){
            axisChangeCD--;
        }
    }

    private void stepDetecting() {

        if (axisToUse == -1)
            Log.d(TAG, "     DB Did not initialize axisToUse");
        if (threshold == -1)
            calculateThreshold();
        for (int i = 0; i < dataBuffer.size(); i++) {
            if (i == 0) {
                lastData = dataBuffer.get(i)[axisToUse];
            } else {
                currentData = dataBuffer.get(i)[axisToUse];
                slope = lastData - currentData;
                if (lastData > threshold && currentData <= threshold && slope > 1) {
                    Log.d(TAG, "DB Total Step: " + stepCount + "   ,   at Axis: " + axisToUse +"  Slope: "+ slope);
                    //Log.d(TAG, "                    DB Slope: " + (lastData - currentData) + "         ; last data: " + lastData + " , current data: " + currentData);
                    onStepDetected(dataTimeBuffer.get(i), dataBuffer.get(i));
                }
                lastData = dataBuffer.get(i)[axisToUse];
            }
        }
        dataBuffer.clear();
        dataTimeBuffer.clear();
    }


    //*************************OMGGGGGGG*************************///
    public StepDetector(){
        mStepListeners = new ArrayList<>();
        stepCount = 0;
    }

    /**
     * Registers a step listener for handling step events.
     * @param stepListener defines how step events are handled.
     */
    public void registerOnStepListener(final OnStepListener stepListener){
        mStepListeners.add(stepListener);
    }

    /**
     * Unregisters the specified step listener.
     * @param stepListener the listener to be unregistered. It must already be registered.
     */
    public void unregisterOnStepListener(final OnStepListener stepListener){
        mStepListeners.remove(stepListener);
    }

    /**
     * Unregisters all step listeners.
     */
    public void unregisterOnStepListeners(){
        mStepListeners.clear();
    }

    /**
     * Here is where you will receive accelerometer readings, buffer them if necessary
     * and run your step detection algorithm. When a step is detected, call
     * {@link #onStepDetected(long, float[])} to notify all listeners.
     *
     * Recall that human steps tend to take anywhere between 0.5 and 2 seconds.
     *
     * @param event sensor reading
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            //TODO: Detect steps! Call onStepDetected(...) when a step is detected.
            // convert the timestamp to milliseconds (note this is not in Unix time)
            long timestamp_in_milliseconds = (long) ((double) event.timestamp / Constants.TIMESTAMPS.NANOSECONDS_PER_MILLISECOND);


            // The total number of data.
            dataCount++;
            //Log.d(TAG, "DB      original values: "+event.values[0]+" , "+event.values[1]+" , "+event.values[2]);
            //Log.d(TAG, "DB: Total Data: "+ dataCount);

            // Clone the event.value array.
            float[] unfilteredData_clone = new float[3];
            unfilteredData_clone[0] = event.values[0];
            unfilteredData_clone[1] = event.values[1];
            unfilteredData_clone[2] = event.values[2];

            // Filter the data and dataBuffer them into another double array
            double[] temp = ESFiltering.getFilteredValues(event.values);
            float[] filtered_values = new float[temp.length];
            for (int i = 0; i < temp.length; i++) {
                // amplified the curve while storing the data to the clone
                filtered_values[i] = (float) temp[i];
            }


            //*************Algorithm starts*************//
            updateXYZMaxMin(filtered_values);
            //Wait for the user to put the phone into the pocket
            if (dataCount > 15) {
                // Start to add the data into the buffer
                dataBuffer.add(filtered_values);
                dataTimeBuffer.add(new Long(timestamp_in_milliseconds));
                // a little reminder that the phone should be in the pocket
                if (dataCount < 18) {
                    Log.d(TAG, "DB  You should've put the phone in the pocket now !!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                }
                // Keep changing the axis to use while the phone is still trying to get to  fixed position
                if (dataCount < 30 && dataCount % axisChangeRate == 0) {
                    chooseAxis();
                } else{
                    chooseAxisAdvance();
                }

                if (dataBuffer.size() > 3) {
                    if (axisToUse == -1)
                        chooseAxis();
                    calculateThreshold();
                    xyzMaxMinReset();
                    stepDetecting();
                }
            }

        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        // do nothing
    }

    /**
     * This method is called when a step is detected. It updates the current step count,
     * notifies all listeners that a step has occurred and also notifies all listeners
     * of the current step count.
     */
    private void onStepDetected(long timestamp, float[] values){
        stepCount++;
        for (OnStepListener stepListener : mStepListeners){
            stepListener.onStepDetected(timestamp, values);
            stepListener.onStepCountUpdated(stepCount);
        }
    }
}
