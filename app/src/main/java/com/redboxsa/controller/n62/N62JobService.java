package com.redboxsa.controller.n62;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Runs {@link N62Updater} every 60 minutes on an N62 (the interval of the old N62 controller). Survives reboots. */
public class N62JobService extends JobService {

    // Not 1/2: those are the locker flow's UpdateJobService ids.
    private static final int PERIODIC_JOB_ID = 62;
    private static final int RUN_NOW_JOB_ID = 63;
    private static final long INTERVAL_MS = 60 * 60 * 1000L;

    /** Idempotent: keeps an existing periodic job. Also drops the old N62 controller's WorkManager jobs, whose service is gone. */
    public static void schedule(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        boolean scheduled = false;
        for (JobInfo job : scheduler.getAllPendingJobs()) {
            if (!job.getService().getClassName().equals(N62JobService.class.getName())) {
                scheduler.cancel(job.getId());
            } else if (job.getId() == PERIODIC_JOB_ID) {
                scheduled = true;
            }
        }
        if (scheduled) return;
        scheduler.schedule(new JobInfo.Builder(PERIODIC_JOB_ID, new ComponentName(context, N62JobService.class))
                .setPeriodic(INTERVAL_MS)
                .setPersisted(true)
                .build());
    }

    public static void runNow(Context context) {
        context.getSystemService(JobScheduler.class).schedule(
                new JobInfo.Builder(RUN_NOW_JOB_ID, new ComponentName(context, N62JobService.class))
                        .setOverrideDeadline(0)
                        .build());
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        N62Updater.runAsync(this, () -> jobFinished(params, false));
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
