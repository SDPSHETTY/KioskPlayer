# KioskPlayer

Android kiosk video player app controlled by Managed Config (App Restrictions). This app is designed for MDM-managed devices where video files are pushed by the MDM and playback is controlled by policy.

## What It Supports

- Single file or playlist playback
- Loop modes: `loop_one`, `loop_all`, `once`
- Fullscreen and optional visible controls
- Portrait, landscape, or auto orientation
- Mute and volume controls
- Absolute file paths and relative paths
- Autostart on boot
- Runtime config refresh (policy updates apply without reinstall)

## Build Outputs

```bash
cd /Users/sudeepshetty/Documents/KioskPlayer
./gradlew assembleDebug
./gradlew assembleRelease
```

- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
- Release unsigned APK: `app/build/outputs/apk/release/app-release-unsigned.apk`

## Generic MDM Workflow

### 1) Upload and install APK

- Upload the APK in your MDM app catalog
- Install it on target devices

### 2) Push video files with MDM file management

Use your MDM file transfer feature to push files to a known directory, for example:

- `/storage/emulated/0/Download/kiosk`

Example target files:

- `/storage/emulated/0/Download/kiosk/intro.mp4`
- `/storage/emulated/0/Download/kiosk/promo.mov`

### 3) Apply managed config policy

Send JSON managed configuration to the app.

Example (playlist loop):

```json
{
  "video_dir": "/storage/emulated/0/Download/kiosk",
  "play_mode": "playlist",
  "playlist_files": "intro.mp4,promo.mov",
  "loop_mode": "loop_all",
  "fullscreen": true,
  "hide_controls": true,
  "orientation": "landscape",
  "mute": false,
  "volume_percent": 80,
  "autostart_on_boot": true,
  "show_debug_overlay": false,
  "skip_missing_files": true
}
```

### 4) Validate on device

- Open app once (or relaunch from MDM)
- Confirm playback starts
- Update one field (for example `loop_mode`) and confirm behavior changes

## Managed Config Reference

- `video_dir` (string): Base video folder path
- `play_mode` (string): `single` or `playlist`
- `single_file` (string): File for single mode
- `playlist_files` (string): Comma-separated files for playlist mode
- `loop_mode` (string): `loop_one`, `loop_all`, `once`
- `fullscreen` (boolean): `true` or `false`
- `hide_controls` (boolean): `true` or `false`
- `orientation` (string): `landscape`, `portrait`, `auto`
- `mute` (boolean): `true` or `false`
- `volume_percent` (int): `0` to `100`
- `autostart_on_boot` (boolean): `true` or `false`
- `skip_missing_files` (boolean): `true` or `false`
- `show_debug_overlay` (boolean): `true` or `false`

## Value Patterns You Can Reuse

### Single file, loop forever

```json
{
  "video_dir": "/storage/emulated/0/Download/kiosk",
  "play_mode": "single",
  "single_file": "intro.mp4",
  "loop_mode": "loop_one",
  "fullscreen": true,
  "hide_controls": true,
  "orientation": "landscape",
  "mute": false,
  "volume_percent": 80,
  "autostart_on_boot": true,
  "show_debug_overlay": false,
  "skip_missing_files": true
}
```

### Playlist, loop all

```json
{
  "video_dir": "/storage/emulated/0/Download/kiosk",
  "play_mode": "playlist",
  "playlist_files": "intro.mp4,promo.mov",
  "loop_mode": "loop_all",
  "fullscreen": true,
  "hide_controls": true,
  "orientation": "landscape",
  "mute": false,
  "volume_percent": 80,
  "autostart_on_boot": true,
  "show_debug_overlay": false,
  "skip_missing_files": true
}
```

### Playlist, play once and stop

```json
{
  "video_dir": "/storage/emulated/0/Download/kiosk",
  "play_mode": "playlist",
  "playlist_files": "intro.mp4,promo.mov",
  "loop_mode": "once",
  "fullscreen": true,
  "hide_controls": true,
  "orientation": "landscape",
  "mute": false,
  "volume_percent": 80,
  "autostart_on_boot": true,
  "show_debug_overlay": false,
  "skip_missing_files": true
}
```

## Path Rules

- Absolute `video_dir` is used directly, for example `/storage/emulated/0/Download/kiosk`
- Relative `video_dir` is resolved in this order:
  1. `getExternalFilesDir(null)/<video_dir>`
  2. `filesDir/<video_dir>`
  3. `/sdcard/<video_dir>`
- `playlist_files` and `single_file` can be:
  - Relative names (resolved under `video_dir`)
  - Absolute file paths

## Common Mistakes

- Using invalid JSON (missing quotes around keys/strings)
- Putting playlist files as separate JSON values instead of one comma-separated string
- Wrong directory path (files not actually in `video_dir`)
- Wrong filename case/extension mismatch

## Quick Local Validation (ADB)

For local pre-MDM testing only:

```bash
adb shell am broadcast -n com.esper.kioskplayer/.DebugConfigReceiver -a com.esper.kioskplayer.DEBUG_APPLY_CONFIG \
  --es video_dir "/storage/emulated/0/Download/kiosk" \
  --es play_mode "playlist" \
  --es playlist_files "intro.mp4,promo.mov" \
  --es loop_mode "loop_all" \
  --ez fullscreen true \
  --ez hide_controls true \
  --es orientation "landscape" \
  --ez mute false \
  --ei volume_percent 80 \
  --ez show_debug_overlay true
```
