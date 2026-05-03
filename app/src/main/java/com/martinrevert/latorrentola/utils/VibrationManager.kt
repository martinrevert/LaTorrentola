package com.martinrevert.latorrentola.utils;

import android.content.Context;
import android.os.Vibrator;

/**
 * Created by martin on 10/12/17.
 */

public class VibrationManager {

    private VibrationManager me;
    private Context context;

    private Vibrator v = null;

    private Vibrator getVibrator(){
        if(v == null){
            v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        }
        return v;
    }

    public VibrationManager getManager(Context context) {
        if(me == null){
            me = new VibrationManager();
        }
        me.setContext(context);
        return me;
    }

    private void setContext(Context context){
        this.context = context;
    }

    public void vibrate(long[] pattern){

    }
}