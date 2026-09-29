# Release builds run R8 in full mode with resource shrinking.

# WorkManager creates input mergers and workers by reflection. Full mode strips constructors that
# nothing calls directly, and Glance runs every widget session as a worker: without these rules the
# widget never gets past its loading layout.
-keep class * extends androidx.work.InputMerger { <init>(); }
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }
