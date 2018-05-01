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

import net.tsz.afinal.FinalDb;

import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public class LockService  extends Service{

    private final String TAG = "LockService";

    private Handler mHandler = null;
    private final static int LOOPHANDLER = 0;
    private HandlerThread handlerThread = null;

    private final List<String> lockName = new ArrayList<>();

    private boolean isUnLockActivity = false;

    //每隔100ms检查一次
    private static long cycleTime = 100;

    @Override
    public void onCreate() {
        super.onCreate();
        handlerThread = new HandlerThread("count_thread");
        handlerThread.start();

        List<AppInfo> selectedAppList = FinalDb.create(this).findAllByWhere(AppInfo.class, "selected=1");
        for(AppInfo item : selectedAppList) {
            lockName.add(item.getPackageName());
        }

        //开始循环检查
        mHandler = new Handler(handlerThread.getLooper()) {
            public void dispatchMessage(android.os.Message msg) {
                switch (msg.what) {
                    case LOOPHANDLER:
                        Log.i(TAG,"do something..."+(System.currentTimeMillis()/1000));
                        /**
                         * 这里需要注意的是：isLockName是用来判断当前的topActivity是不是我们需要加锁的应用
                         * 同时还是需要做一个判断，就是是否已经对这个app加过锁了，不然会出现一个问题
                         * 当我们打开app时，启动我们的加锁界面，解锁之后，回到了app,但是这时候又发现栈顶app是
                         * 需要加锁的app,那么这时候又启动了我们加锁界面，这样就出现死循环了。
                         * 可以自行的实验一下
                         * 所以这里用isUnLockActivity变量来做判断的
                         */
                        if(isLockName() && !isUnLockActivity){
                            Log.i(TAG, "locking...");
                            Intent intent = new Intent(LockService.this, VerifyActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            intent.putExtra("ShowBackBtn", false);
                            startActivity(intent);
                            //调用了解锁界面之后，需要设置一下isUnLockActivity的值
                            isUnLockActivity = true;
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
     * @return
     */
    private boolean isLockName(){
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            UsageStatsManager mUsageStatsManager = (UsageStatsManager)getSystemService(Context.USAGE_STATS_SERVICE);
            long time = System.currentTimeMillis();
            List<UsageStats> stats = mUsageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000*10, time);
            if(stats != null) {
                SortedMap<Long,UsageStats> mySortedMap = new TreeMap<Long,UsageStats>();
                for (UsageStats usageStats : stats) {
                    mySortedMap.put(usageStats.getLastTimeUsed(),usageStats);
                }
                if(mySortedMap != null && !mySortedMap.isEmpty()) {
                    String topPackageName =  mySortedMap.get(mySortedMap.lastKey()).getPackageName();
                    Log.e("TopPackage Name",topPackageName);
                    //如果当前的Activity是桌面app,那么就需要将isUnLockActivity清空值
                    if(getHomes().contains(topPackageName)){
                        isUnLockActivity = false;
                    }
                    if(lockName.contains(topPackageName)) {
                        return true;
                    }
                }
            }
        } else {
            ActivityManager mActivityManager;
            mActivityManager = (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
            ComponentName topActivity = mActivityManager.getRunningTasks(1).get(0).topActivity;
            String packageName = topActivity.getPackageName();

            //如果当前的Activity是桌面app,那么就需要将isUnLockActivity清空值
            if(getHomes().contains(packageName)){
                isUnLockActivity = false;
            }
            if(lockName.contains(packageName)){
                return true;
            }
        }

        return false;
    }

    /**
     * 返回所有桌面app的包名
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
        for(ResolveInfo ri : resolveInfo){
            names.add(ri.activityInfo.packageName);
            System.out.println(ri.activityInfo.packageName);
        }
        return names;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return Service.START_STICKY;
    }
}
