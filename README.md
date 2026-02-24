# KioskPlayer

Android kiosk video player controlled by Managed Config (App Restrictions).

## Simple Managed Config Keys

- `path` (string): Base directory for videos
- `mode` (string): `single` or `playlist`
- `files` (string): Comma-separated file names/paths
- `loop` (string): `one`, `all`, `off`
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

## Path and Files Notes

- `path` accepts absolute or relative directory.
- `files` accepts relative names or absolute file paths.
- Relative file names are resolved under `path`.

## Legacy Key Support

The app still accepts older keys (`video_dir`, `play_mode`, `playlist_files`, `loop_mode`, `hide_controls`, `volume_percent`, `autostart_on_boot`) for compatibility.
