package com.vpr.vprlock.service;

import android.app.ActivityManager;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import com.vpr.vprlock.activity.VerifyActivity;
import com.vpr.vprlock.bean.AppInfo;
import com.vpr.vprlock.utils.UITools;

import net.tsz.afinal.FinalDb;

import org.simple.eventbus.EventBus;
import org.simple.eventbus.Subscriber;
import org.simple.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public class LockService extends Service {

    private final String TAG = "LockService";

    private Handler mHandler = null;
    private final static int LOOPHANDLER = 0;
    private HandlerThread handlerThread = null;

    private final List<String> lockName = new ArrayList<>();


    //每隔100ms检查一次
    private static long cycleTime = 100;
    //记录当前开启的有锁应用
    private String currentPackageName;
    //记录已解锁的应用
    private String unlockPackageName;
    ArrayList<String> hadUnlockList = new ArrayList<>();

    @Override
    public void onCreate() {
        super.onCreate();
        EventBus.getDefault().register(this);
        handlerThread = new HandlerThread("count_thread");
        handlerThread.start();

        List<AppInfo> selectedAppList = FinalDb.create(this).findAllByWhere(AppInfo.class, "selected=1");
        for (AppInfo item : selectedAppList) {
            lockName.add(item.getPackageName());
        }

        //开始循环检查
        mHandler = new Handler(handlerThread.getLooper()) {
            public void dispatchMessage(android.os.Message msg) {
                switch (msg.what) {
                    case LOOPHANDLER:
                        Log.i(TAG, "do something..." + (System.currentTimeMillis() / 1000));

                        if (isLockName()) {
                            Log.i(TAG, "locking...");
                            Intent intent = new Intent(LockService.this, VerifyActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.putExtra("ShowBackBtn", false);
                            intent.putExtra("packageName", currentPackageName);
                            startActivity(intent);
                        }
                        break;
                }
                mHandler.sendEmptyMessageDelayed(LOOPHANDLER, cycleTime);
            }
        };
        mHandler.sendEmptyMessage(LOOPHANDLER);
    }

    /**
     * 判断当前的Activity是不是我们开启解锁界面的app
     *
     * @return
     */
    private boolean isLockName() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            UsageStatsManager mUsageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            long time = System.currentTimeMillis();
            List<UsageStats> stats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 10, time);
            if (stats != null) {
                SortedMap<Long, UsageStats> mySortedMap = new TreeMap<Long, UsageStats>();
                for (UsageStats usageStats : stats) {
                    mySortedMap.put(usageStats.getLastTimeUsed(), usageStats);
                }
                if (mySortedMap != null && !mySortedMap.isEmpty()) {
                    String topPackageName = mySortedMap.get(mySortedMap.lastKey()).getPackageName();
                    Log.e("TopPackage Name", topPackageName);
                    System.out.println("=========="+topPackageName);
                    if (!hadUnlockList.contains(topPackageName) && lockName.contains(topPackageName)) {
                        currentPackageName = topPackageName;
                        return true;
                    }

                }
            }
        } else {
            ActivityManager mActivityManager;
            mActivityManager = (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
            ComponentName topActivity = mActivityManager.getRunningTasks(1).get(0).topActivity;
            String packageName = topActivity.getPackageName();
            System.out.println("========="+packageName);
            if (!hadUnlockList.contains(packageName) && lockName.contains(packageName)) {
                currentPackageName = packageName;
                return true;
            }

        }

        return false;
    }

    public String getLauncherPackageName(Context context) {
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        final ResolveInfo res = context.getPackageManager().resolveActivity(intent, 0);
        if (res.activityInfo == null) {
            // should not happen. A home is always installed, isn't it?
            return null;
        }
        if (res.activityInfo.packageName.equals("android")) {
            // 有多个桌面程序存在，且未指定默认项时；
            return null;
        } else {
            return res.activityInfo.packageName;
        }
    }

    /**
     * 返回所有桌面app的包名
     *
     * @return
     */
    private List<String> getHomes() {
        List<String> names = new ArrayList<>();
        PackageManager packageManager = this.getPackageManager();
        //属性
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> resolveInfo = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        for (ResolveInfo ri : resolveInfo) {
            names.add(ri.activityInfo.packageName);
            System.out.println(ri.activityInfo.packageName);
        }
        return names;
    }

    @Subscriber(mode = ThreadMode.MAIN, tag = "datebase_update")
    public void databaseUpdate(FinalDb finalDb) {
        if (lockName != null) {
            lockName.clear();
        }
        List<AppInfo> selectedAppList = FinalDb.create(this).findAllByWhere(AppInfo.class, "selected=1");
        for (AppInfo item : selectedAppList) {
            lockName.add(item.getPackageName());
        }
    }

    @Subscriber(mode = ThreadMode.MAIN, tag = "unlock")
    public void setUnlock(String packageName) {
        hadUnlockList.add(packageName);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }
}
