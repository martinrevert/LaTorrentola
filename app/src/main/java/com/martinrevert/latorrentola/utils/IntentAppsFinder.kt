package com.martinrevert.latorrentola.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.util.Log;

import java.util.List;

/**
 * Created by martin on 11/12/17.
 */

public class IntentAppsFinder {

    public boolean IntentAppsFinder(Context context, String magnet) {

        PackageManager manager = context.getPackageManager();
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_VIEW);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        // NOTE: Provide some data to help the Intent resolver
        intent.setData(Uri.parse(magnet));
        // Query for all activities that match my filter and request that the filter used
        //  to match is returned in the ResolveInfo
        List<ResolveInfo> infos = manager.queryIntentActivities(intent,
                PackageManager.GET_RESOLVED_FILTER);
        if (infos.isEmpty()) {
            return false;
        } else {

            for (ResolveInfo info : infos) {
                ActivityInfo activityInfo = info.activityInfo;
                IntentFilter filter = info.filter;
                if (filter != null && filter.hasAction(Intent.ACTION_VIEW) &&
                        filter.hasCategory(Intent.CATEGORY_BROWSABLE)) {
                    // This activity resolves my Intent with the filter I'm looking for
                    String activityPackageName = activityInfo.packageName;
                    String activityName = activityInfo.name;
                    Log.v("IntentFinder", "Activity " + activityPackageName + "/" + activityName);
                }
            }
        return true;
        }
    }
}
