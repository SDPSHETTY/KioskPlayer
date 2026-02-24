# KioskPlayer

Android kiosk video player controlled by Managed Config (App Restrictions).

## Simple Managed Config Keys

- `path` (string): Base directory for videos
- `mode` (string): `single` or `playlist`
- `files` (string): Comma-separated file names/paths
- `loop` (string): `one`, `all`, `off`
- `stream_url` (string): Optional HLS/MP4 stream URL
- `source_preference` (string): `local_first`, `stream_first`, `local_only`, `stream_only`
- `fallback_on_stream_error` (boolean)
- `image_duration_sec` (int): Duration for image slides
- `schedule_enabled` (boolean)
- `schedule_start` / `schedule_end` (string): `HH:mm`
- `schedule_days` (string): `mon,tue,wed,thu,fri,sat,sun` or `1..7`
- `heartbeat_sec` (int): Health heartbeat interval
- `fullscreen` (boolean)
- `controls` (string): `show` or `hide`
- `orientation` (string): `landscape`, `portrait`, `auto`
- `mute` (boolean)
- `volume` (int): `0` to `100`
- `autostart` (boolean)
- `skip_missing_files` (boolean)
- `show_debug_overlay` (boolean)

## Generic MDM Workflow

1) Install APK with your MDM app catalog.

2) Push videos using MDM file management, for example:

- `/storage/emulated/0/Download/kiosk/intro.mp4`
- `/storage/emulated/0/Download/kiosk/promo.mov`

3) Push managed config policy.

4) Relaunch app (or wait for refresh) and verify playback.

## Example Policies

### Playlist loop all

```json
{
  "path": "/storage/emulated/0/Download/kiosk",
  "mode": "playlist",
  "files": "intro.mp4,promo.mov",
  "loop": "all",
  "image_duration_sec": 10,
  "stream_url": "",
  "source_preference": "local_first",
  "fallback_on_stream_error": true,
  "schedule_enabled": false,
  "schedule_start": "00:00",
  "schedule_end": "23:59",
  "schedule_days": "mon,tue,wed,thu,fri,sat,sun",
  "heartbeat_sec": 60,
  "fullscreen": true,
  "controls": "hide",
  "orientation": "landscape",
  "mute": false,
  "volume": 80,
  "autostart": true,
  "skip_missing_files": true,
  "show_debug_overlay": false
}
```

### Single video, loop one

```json
{
  "path": "/storage/emulated/0/Download/kiosk",
  "mode": "single",
  "files": "intro.mp4",
  "loop": "one",
  "fullscreen": true,
  "controls": "hide",
  "orientation": "landscape",
  "mute": false,
  "volume": 80,
  "autostart": true,
  "skip_missing_files": true,
  "show_debug_overlay": false
}
```

### Playlist play once and stop

```json
{
  "path": "/storage/emulated/0/Download/kiosk",
  "mode": "playlist",
  "files": "intro.mp4,promo.mov",
  "loop": "off",
  "fullscreen": true,
  "controls": "hide",
  "orientation": "landscape",
  "mute": false,
  "volume": 80,
  "autostart": true,
  "skip_missing_files": true,
  "show_debug_overlay": false
}
```

### Mixed media + stream fallback

```json
{
  "path": "/storage/emulated/0/Download",
  "mode": "playlist",
  "files": "kiosk/intro.mp4,image.png",
  "loop": "all",
  "image_duration_sec": 5,
  "stream_url": "https://example.com/live.m3u8",
  "source_preference": "stream_first",
  "fallback_on_stream_error": true,
  "schedule_enabled": true,
  "schedule_start": "09:00",
  "schedule_end": "18:00",
  "schedule_days": "mon,tue,wed,thu,fri",
  "fullscreen": true,
  "controls": "hide",
  "orientation": "landscape",
  "mute": false,
  "volume": 80,
  "autostart": true,
  "skip_missing_files": true,
  "show_debug_overlay": false,
  "heartbeat_sec": 60
}
```

## Path and Files Notes

- `path` accepts absolute or relative directory.
- `files` accepts relative names or absolute file paths.
- Relative file names are resolved under `path`.

## Legacy Key Support

The app still accepts older keys (`video_dir`, `play_mode`, `playlist_files`, `loop_mode`, `hide_controls`, `volume_percent`, `autostart_on_boot`) for compatibility.

## Remote Control Actions

- Refresh playback now:
  - `adb shell am broadcast -n com.esper.kioskplayer/.ControlReceiver -a com.esper.kioskplayer.REFRESH_NOW`
- Dump health log:
  - `adb shell am broadcast -n com.esper.kioskplayer/.ControlReceiver -a com.esper.kioskplayer.HEALTH_DUMP`
