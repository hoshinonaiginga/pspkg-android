package io.github.hoshinonaiginga.pspkg;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;

import java.io.File;

/**
 * Foreground service that runs the extraction, so multi-GB PKGs keep going
 * with the screen off or the app in background.
 * Status is exposed through static fields that the UI polls.
 */
public class ExtractService extends Service {
    public static final String EXTRA_INPUTS = "inputs";
    public static final String EXTRA_OUT_BASE = "out_base"; // null = same folder as each PKG

    private static final String CHANNEL_ID = "extract";
    private static final int NOTIF_ID = 1;
    private static final int NOTIF_DONE_ID = 2;

    // ---- Status shared with the UI ----
    public static volatile boolean running = false;
    public static volatile String currentName = "";
    public static volatile String jobSource = ""; // what the user picked, shown again if the UI is recreated
    public static volatile String phase = "";
    public static volatile int pkgIndex = 0;
    public static volatile int pkgCount = 0;
    public static volatile int filesDone = 0;
    public static volatile int filesTotal = 0;
    private static final StringBuilder LOG = new StringBuilder();

    public static void log(String line) {
        synchronized (LOG) {
            LOG.append(line).append('\n');
            if (LOG.length() > 20000) LOG.delete(0, LOG.length() - 20000);
        }
    }

    public static String getLog() {
        synchronized (LOG) {
            return LOG.toString();
        }
    }

    public static void clearLog() {
        synchronized (LOG) {
            LOG.setLength(0);
        }
    }

    private static boolean jobActive = false; // guards against a second concurrent job
    private NotificationManager nm;
    private PowerManager.WakeLock wakeLock;
    private long lastNotify = 0;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createChannel();
        Notification n = buildProgress(getString(R.string.notif_preparing), 0, 0, true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIF_ID, n);
        }

        if (jobActive) {
            return START_NOT_STICKY;
        }
        if (intent == null) {
            running = false;
            stopSelfClean();
            return START_NOT_STICKY;
        }
        final String[] inputs = intent.getStringArrayExtra(EXTRA_INPUTS);
        final String outBase = intent.getStringExtra(EXTRA_OUT_BASE);
        if (inputs == null || inputs.length == 0) {
            running = false;
            stopSelfClean();
            return START_NOT_STICKY;
        }

        jobActive = true;
        running = true;
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pspkg:extract");
        wakeLock.acquire(6 * 60 * 60 * 1000L);

        new Thread(() -> runJob(inputs, outBase), "pkg-extract").start();
        return START_NOT_STICKY;
    }

    private void runJob(String[] inputs, String outBase) {
        int ok = 0, failed = 0;
        pkgCount = inputs.length;
        long start = SystemClock.elapsedRealtime();
        for (int i = 0; i < inputs.length; i++) {
            File pkg = new File(inputs[i]);
            pkgIndex = i + 1;
            currentName = pkg.getName();
            filesDone = 0;
            filesTotal = 0;
            phase = getString(R.string.phase_header);
            log("[" + (i + 1) + "/" + inputs.length + "] " + pkg.getName());

            NativeLib.Meta meta = NativeLib.meta(pkg.getAbsolutePath());
            if (!meta.ok()) {
                log(getString(R.string.log_open_failed, meta.error));
                failed++;
                continue;
            }
            File base = outBase != null ? new File(outBase) : pkg.getParentFile();
            File out = Util.outputDirFor(base, meta.titleId);
            log(getString(R.string.log_meta, meta.titleId, Util.humanSize(meta.size)));
            log(getString(R.string.log_dest, out.getAbsolutePath()));

            phase = getString(R.string.phase_decrypt);
            updateNotification(true);
            final String phaseExtract = getString(R.string.phase_extract);
            String err = NativeLib.extract(pkg.getAbsolutePath(), out.getAbsolutePath(), (done, total) -> {
                filesDone = done;
                filesTotal = total;
                phase = phaseExtract;
                long now = SystemClock.elapsedRealtime();
                if (now - lastNotify > 700 || done == total) {
                    lastNotify = now;
                    updateNotification(false);
                }
            });
            if (err != null) {
                log(getString(R.string.log_error, err));
                failed++;
            } else {
                log(getString(R.string.log_done, filesTotal));
                ok++;
            }
        }
        long secs = (SystemClock.elapsedRealtime() - start) / 1000;
        String summary = (inputs.length == 1)
                ? getString(ok == 1 ? R.string.summary_ok : R.string.summary_fail)
                : getString(R.string.summary_batch, ok, failed);
        log(getString(R.string.log_summary, summary, (int) (secs / 60), (int) (secs % 60)));
        phase = summary;
        running = false;
        jobActive = false;
        notifyDone(summary, failed == 0);
        stopSelfClean();
    }

    private void stopSelfClean() {
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelf();
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW);
        nm.createNotificationChannel(ch);
    }

    private PendingIntent openApp() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification buildProgress(String text, int done, int total, boolean indeterminate) {
        Notification.Builder b = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_extract)
                .setContentTitle(pkgCount > 1 ? ("PKG " + pkgIndex + "/" + pkgCount + " · " + currentName) : (currentName.isEmpty() ? getString(R.string.notif_title_default) : currentName))
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openApp())
                .setProgress(total, done, indeterminate);
        return b.build();
    }

    private void updateNotification(boolean indeterminate) {
        String text = indeterminate ? phase + "…" : getString(R.string.status_files, filesDone, filesTotal);
        nm.notify(NOTIF_ID, buildProgress(text, filesDone, filesTotal, indeterminate));
    }

    private void notifyDone(String summary, boolean success) {
        Notification n = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_extract)
                .setContentTitle(success ? "✓ " + summary : "✗ " + summary)
                .setContentText(currentName)
                .setAutoCancel(true)
                .setContentIntent(openApp())
                .build();
        nm.notify(NOTIF_DONE_ID, n);
    }
}
