## Jellywing

Jellywing is a native music player for Android devices that connects to Jellyfin media servers. It is based on Gelli, which itself was based on a relatively recent version of Phonograph. Jellywing keeps the same GPLv3 license as Gelli; see [LICENSE](LICENSE) for the full terms.

The app is now branded as Jellywing throughout the project and uses the `club.thatpetbff.gramophone` Android package/application ID. The current build also resets the active ExoPlayer media source whenever a song is selected, so choosing a new song after Wi-Fi or mobile data loss opens a fresh playback stream instead of reusing a stale one.

This project was made for personal use, but contributions are welcome. Please open an issue to discuss larger changes before submitting a pull request. I am open to an improved icon if any graphic designers have a good suggestion.

[<img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="90">](https://f-droid.org/packages/club.thatpetbff.gramophone)

## Features

* Basic library navigation
* Cache songs individually or through batch actions
* Gapless playback
* Fresh playback stream reset when selecting a song
* Sort albums and songs by different fields
* Search media for partial matches
* Media service integration with notification
* Favorites and playlists
* Playback history reporting
* Filter content by library

## Recent Changes

* Moved the Android package and application ID to `club.thatpetbff.gramophone`
* Renamed user-facing app branding from Gelli to Jellywing
* Updated source packages, resources, tests, ProGuard rules, and XML view references for the new namespace
* Added a playback reset before loading a selected song to avoid stale streams after network loss
* Updated the GitHub Actions release workflow to use the Actions-provided token for release creation

## Issues

Since this was a small project intended mainly for myself, there are some things I haven't resolved yet. I would appreciate pull requests to fix any of these issues!

* Artist sorting isn't available through the API
* Playlists and favorites will not update automatically when changed

## Future Plans

If I ever find the time, these are some of the items I would potentially include.

* Interface overhaul
* Offline downloads
* SyncPlay
* Smart playlists
* Session controls
* QuickConnect

These are features I wouldn't include myself, but I would accept pull requests with good code.

* Support for other media types
* Chromecast
* Android Auto

## Screenshots

<img src='https://raw.githubusercontent.com/birdrock00/jellywing/master/metadata/en-US/screenshots.png'>
