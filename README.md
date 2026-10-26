# Pocket Skip

Change tracks in your favorite music app with the volume buttons, without taking your gloves off or your phone out of your pocket.

- **Short press**: changes the volume, like normal.
- **Hold** volume up or down: skips to the next or previous track.
- **Hold while nothing is playing**: starts your chosen music app.

It works with the screen off too, and you feel a short vibration when a track is skipped.

## Features

- Pick which apps it skips tracks in, or leave it on for every app
- Pick an app to start when you hold a button while nothing is playing
- Adjustable hold time (300–1500 ms)
- Swap which button skips forward and which goes back
- A setup wizard the first time you open the app
- Light and dark theme

## Requirements

- Android 17 (API 37) or newer

## Permissions

| Permission | Why |
| --- | --- |
| Notification access | Android puts music controls under this permission. Pocket Skip uses it to see what's playing and to skip tracks. |
| Accessibility (screen-on mode) | The only way for an app to catch volume button presses while the screen is on. Pocket Skip uses it for the volume buttons only. Optional, but recommended. |
| Vibration | For the short buzz when a track is skipped. |
| Installed music apps | To list the apps you can pick in the app. |

## Privacy

Nothing is collected. The app has no internet permission, so it can't send anything anywhere: no tracking, no ads, no analytics. Your settings are stored on your phone, and in your own Android backup if you have that turned on.

## How it works

With the screen on, an accessibility service catches the volume buttons and decides whether a press was short or long.

With the screen off, Android doesn't send volume buttons to apps at all. It sends them to whichever media session is playing. So while music is playing, Pocket Skip holds its own media session with remote volume handling, which routes the button presses to the app. Short presses are passed on as normal volume changes.

Playback is controlled through Android's media session APIs, which is what notification access is needed for.

## Building

1. Open the project in Android Studio.
2. Install the Android 37 SDK platform if it's missing (**Settings → Languages & Frameworks → Android SDK**).
3. Connect your phone with USB debugging turned on and press **Run**.

Or from the command line:

```sh
./gradlew installDebug
```

After installing, open the app and follow the setup wizard to grant notification access and turn on screen-on mode.

## Credits

Created with the help of [Claude Code](https://claude.com/claude-code).
