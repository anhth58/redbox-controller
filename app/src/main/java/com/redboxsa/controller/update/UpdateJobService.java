package com.redboxsa.controller.update;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/**
 * Runs {@link Updater} every 30 minutes, 24/7, on Android 6 and Android 11.
 * JobScheduler (unlike the old AlarmManager + service chain) is allowed to start this app in the background on Android 8+,
 * and the job survives reboots (persisted).
 */
public class UpdateJobService extends JobService {

    private static final int PERIODIC_JOB_ID = 1;
    private static final int RUN_NOW_JOB_ID = 2;
    private static final long INTERVAL_MS = 30 * 60 * 1000L;

    /** Idempotent: leaves an existing periodic job alone so its 30-minute rhythm is not reset. */
    public static void schedule(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        for (JobInfo job : scheduler.getAllPendingJobs()) {
            if (job.getId() == PERIODIC_JOB_ID) return;
        }
        scheduler.schedule(new JobInfo.Builder(PERIODIC_JOB_ID, new ComponentName(context, UpdateJobService.class))
                .setPeriodic(INTERVAL_MS)
                .setPersisted(true)
                .build());
    }

    /** One check as soon as possible, run under the job so the system keeps the process alive while it downloads. */
    public static void runNow(Context context) {
        context.getSystemService(JobScheduler.class).schedule(
                new JobInfo.Builder(RUN_NOW_JOB_ID, new ComponentName(context, UpdateJobService.class))
                        .setOverrideDeadline(0)
                        .build());
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Updater.runAsync(this, () -> jobFinished(params, false));
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        // The worker thread keeps going; the periodic job comes back on its own.
        return false;
    }
}
