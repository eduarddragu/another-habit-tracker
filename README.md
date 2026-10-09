<picture>
  <source media="(prefers-color-scheme: dark)" srcset="docs/assets/banner-dark.svg">
  <img alt="(Another) Habit Tracker" src="docs/assets/banner-light.svg" width="100%">
</picture>

<p>
  <picture><source media="(prefers-color-scheme: dark)" srcset="docs/assets/badges-dark.svg"><img alt="Kotlin 2.4 · UI: Jetpack Compose · Android 14+ · Internet: not even asked · License: GPL v3" src="docs/assets/badges-light.svg"></picture>
  <a href="https://eduarddragu.dev"><picture><source media="(prefers-color-scheme: dark)" srcset="docs/assets/badge-by-dark.svg"><img alt="By eduarddragu.dev" src="docs/assets/badge-by-light.svg"></picture></a>
</p>

**(Another) habit tracker, built for me: it picks what I study and nags me until I've done it. It also keeps my reading list and blocks my doomscrolling.**

Every habit tracker I tried was happy to count my days, but none told me what to study, and none got annoying enough late at night to make me do it. So I built one that does both. The studying still happens in a paper notebook: the app picks the topic, keeps asking until the session is logged, and keeps the score.

> Built for one Pixel, installed as a sideloaded APK. The code is public, the data is not. Sibling of [(Another) Reminder App](https://github.com/eduarddragu/another-reminder-app), same palette, same type.

## Screens

Light and dark, both native. The light ones are here.

<p align="center">
  <img src="docs/assets/screenshots/home.png" width="150" alt="Home: greeting, planet and a card per habit with its streak">
  <img src="docs/assets/screenshots/study.png" width="150" alt="The study habit: today's topic with its guiding questions">
  <img src="docs/assets/screenshots/log.png" width="150" alt="Logging a session: score, minutes, notes">
  <img src="docs/assets/screenshots/curriculum.png" width="150" alt="The curriculum, area by area">
  <img src="docs/assets/screenshots/guard.png" width="150" alt="The scroll guard over Instagram">
</p>
<p align="center"><sub>Home · Today's topic · How did it go? · Curriculum · Scroll guard</sub></p>

## What it does

**Picks today's topic.** I don't choose, so I can't dodge. About 120 topics (Linux, networking, Kubernetes, cloud, distributed systems, databases, security and more) sit in a prerequisite graph in `app/src/main/assets/curriculum.json`. Each day one is drawn, with three or four questions to work through. After the session I give it a score, and the score decides when it comes back:

| Score | Meaning | Next |
|---|---|---|
| 1 | Didn't get it | back in 2 days |
| 2 | Partially | back in 4 days |
| 3 | Could explain the gist | opens the topics that depend on it, review in 14 days |
| 4 | Could explain it properly | review in 30 days |
| 5 | Could teach it | review in 90 days |

Topics I already know can be marked as known, one at a time or a whole area at once. Reviews that are due show up first in the curriculum, weakest first. Meditation gets one small practice a day too, in three short steps.

**Keeps a reading list.** Twenty minutes of a paper book a day. Each session is logged with the book I'm on, "Finished it" closes it, and the habit's page keeps the shelf: what I'm reading and everything I've finished.

**Gets pushier.** Each habit has its own reminder times, and each reminder is a bit less polite than the last. The last call puts the streak on the line, with its own sound. Logging the habit makes the rest of the day quiet. Weekdays and weekends can have different times.

| Reminder | Sounds like |
|---|---|
| First of the day | "Today: …" with the first question and the streak so far |
| Middle | "Friendly reminder (for now): Meditation." |
| Later | "The day is getting away. Meditation first." |
| Last call | "Throwing away 12 days for a night on the couch? Meditation." |

From the notification I can log, open a linked app (a meditation app, say), or say "On it" to silence the reminders for two hours. Not the last call, though.

**Guards the streak.** The streak is the biggest number on the screen, with this week under it. One freeze a week covers a missed day. A session finished after midnight still lands on the right day. After eight weeks a GitHub-style heatmap shows up, and on Sunday evening a short recap closes the week.

**Opens on a planet.** Home greets me by the time of day ("Still up, Eduard?") with a line for the day, a small planet in the middle and the habit cards below. One tap on the planet starts a session: it turns into a timer, with the topic and its questions still on screen. When time's up, the log opens with the minutes filled in.

**Takes time off with me.** Days off don't break the streak and nothing nags: no reminders, no guard, no recap.

**Guards the feed.** An optional scroll guard stops Instagram (or any app I pick) while a habit is still open: what's left, the days at stake, and a button straight to the habit. It's a soft block: "Ten minutes, then I'm out" lets me through, then it asks again. It's an accessibility service that only learns which app is in front, never what's on screen.

**Sits on the home screen.** A "Today" widget shows what's done, what's left and the streaks.

**Backs itself up.** All the data is one JSON file, rewritten to a place I picked (Google Drive works) a couple of minutes after every change. Importing a file replaces everything, after asking, and saves the old data first.

## Under the hood

- Kotlin, Jetpack Compose, Material 3 and Navigation 3, in the palette of [eduarddragu.dev](https://eduarddragu.dev).
- Room for storage, with every schema version exported and migrated.
- Reminders are exact alarms set on the phone (`AlarmManager`, `USE_EXACT_ALARM`), rescheduled after a reboot, an app update, or a time or timezone change. No server involved.
- Glance for the widget, refreshed on every change and just after midnight.
- Type from [eduarddragu.dev](https://eduarddragu.dev): Cormorant Garamond for titles, DM Sans for text and numbers, Geist Mono for small labels. Bundled under the SIL Open Font License and cut down to Latin characters (licenses in `app/src/main/assets/licenses/`). Habit icons are hand-drawn vectors.
- The logic that matters (streaks, time off, reminder timing and tone, heatmap, stats, topic picking and due reviews, curriculum validation, the scroll guard, the session timer, the weekly recap, the reading list) is plain Kotlin with unit tests.

```
app/src/main/java/dev/eduarddragu/anotherhabittracker/
├── domain/      pure logic: streaks, reminders, heatmap, stats, curriculum, topic picker
├── data/        Room entities and DAOs, HabitRepository
├── reminders/   alarm scheduling, receivers, notifications
├── guard/       scroll guard: accessibility service and its screen
├── backup/      JSON backup, nightly job
├── ui/          Compose screens and components
├── widget/      Glance widget
└── theme/       palette and type
```

## Build and install

You need JDK 21 (the build targets Java 17), the Android SDK (the Android CLI or Android Studio fetches the platform packages on the first build), and a phone with USB or wireless debugging on.

```bash
scripts/install.sh            # debug build, installed in place and launched
scripts/install.sh release    # release build
./gradlew testDebugUnitTest   # unit tests, on the JVM
```

### Signing

An update keeps the app's data only if it is signed with the same key as the version already installed. So the key is personal, and it stays out of the repository.

1. Create a keystore somewhere private:
   ```bash
   keytool -genkeypair -keystore ~/.android-keys/another-habit-tracker.jks -storetype PKCS12 \
     -alias another-habit-tracker -keyalg RSA -keysize 4096 -validity 36500
   ```
2. Point Gradle at it in `~/.gradle/gradle.properties` (your user-level file, not the project's):
   ```properties
   aht.storeFile=/absolute/path/to/another-habit-tracker.jks
   aht.storePassword=...
   aht.keyAlias=another-habit-tracker
   aht.keyPassword=...
   ```
3. Back up the keystore and its password. Lose them and the next version only installs after an uninstall, which deletes the data.

Without these properties the build falls back to the debug key. That's fine for trying the project, but the install script won't use it: the phone would reject the result as an update anyway.

Never run `connectedAndroidTest` against a phone you use. Instrumented tests uninstall the app when they finish, and the data goes with it.

## Privacy

The data lives on the phone. Like most Android apps, it's included in Android's own backup to the Google account, and the only other copy is the backup file I pick. No accounts, no analytics, no network calls: the app doesn't even ask for internet access. The scroll guard only learns which app is in front, and keeps that on the phone. `.gitignore` keeps keystores, credentials and exports out of this repository.

## License

[GPL v3](LICENSE). Take it, change it, ship your own version, as long as yours stays open too. The bundled fonts keep their own SIL Open Font License (`app/src/main/assets/licenses/`).
