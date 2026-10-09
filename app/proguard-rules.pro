# Release builds run R8 in full mode with resource shrinking.

# WorkManager creates input mergers and workers by reflection. Full mode strips constructors that
# nothing calls directly, and Glance runs every widget session as a worker: without these rules the
# widget never gets past its loading layout.
-keep class * extends androidx.work.InputMerger { <init>(); }
-keep class * extends androidx.work.ListenableWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }

# Navigation 3 saves the back stack by each key's serializer, found by class name on restore. Keep the
# keys whole (names may still be shortened) so full mode never merges two keys of the same shape, such
# as HabitDetail and HabitSettings, into one class.
-keep,allowobfuscation class * implements androidx.navigation3.runtime.NavKey { *; }
