# Afli

Will they bite? A native Android app for shore fishing on the Reykjanes peninsula that scores the water you're standing at, from the tide, light, wind, pressure and sea temperature, and says separately whether it's safe to go.

Liquid glass over an animated sea (Kyant's Backdrop 2.0 lens and frost on Android 13+), with springy press effects, fades between screens, a blur behind sheets (drag them down to close), and haptics.

- **Now**: a bite score from 0 to 100 (Great, OK or Slow), the best fish to try for, and a separate **Safe / Careful / Stay home** pill. "Why" chips underneath say what's helping and hurting. Also the next best window in the next two days, a tide curve that draws itself in (times along the bottom, and the time of each high and low), a wind compass and the current conditions.
- **Forecast**: seven days of hour bars coloured by bite score, with Stay home hours tinted red. Tap a bar for that hour.
- **Log**: Start fishing where you stand, tap a fish each time you catch one, End trip. Empty trips count. Each trip saves the conditions it started in.
- **Spots by GPS**: no location chooser. Afli shows the saved spot you're standing at (within 300 m), otherwise where you are. Starting a trip somewhere new saves that place as a spot. Keflavík harbour, Njarðvík harbour, Kleifarvatn and Seltjörn are built in.
- **Fix spot**: point the phone at the water to set which way it faces (so onshore and offshore wind count right), and mark it as harbour, open coast or lake.
- **Help built in**: a three-card intro, a short coach-mark tour the first time you open Now and Log, and long-press on almost anything for a one-line explanation. A Guide tab explains every feature and every fish.
- **Fish**: saithe, cod, mackerel, shorthorn sculpin, haddock, plaice and Atlantic wolffish at sea; Arctic char and brown trout in lakes.

No server, no account. Trips and spots stay on the phone.

## How the score works

Each hour gets plain multipliers for:

- **Tide**: how fast the sea level is changing compared with the fastest flow that day (moving water is feeding water), a little more around spring tides (new and full moon).
- **Light**: the sun's height. Dawn and dusk (−6° to 8°) score best; bright sun on clear water a bit lower; darkness lower except for cod, which come shallower at night.
- **Pressure**: the change over the last 3 hours. A slow fall is best; a fast fall or a rise is worse.
- **Wind**: some wind helps, over 8 m/s is hard work, over 12 m/s is poor. With the spot's water direction set, onshore wind gets a small boost.
- **Water clarity**: lots of rain in the last two days, or big waves at an open coast, lower it.

Each fish then gets a presence score from the sea temperature (its comfortable range, from Wikipedia), how often it's within reach of the shore, and, for lake fish, the May to September season. The bite score is the best fish's presence times the multipliers. Lake spots score zero at night (no night fishing under the Veiðikortið rules).

For the next 12 hours, the forecast's wind, gusts and pressure are nudged toward the nearest Veðurstofa station's live reading, fading out over the 12 hours.

Safety is worked out on its own and never mixed into the score: gusts of 22 m/s or more, or waves of 3.5 m or more at a sea spot, mean Stay home; gusts from 15 m/s, waves from 2 m, or cold and windy mean Careful. Harbours count about a third of the offshore wave height.

The score is a guide, not a promise. Tides come from a sea model and are approximate near the shore.

## Data

| Source | Used for |
| --- | --- |
| Open-Meteo forecast API (DMI HARMONIE 2 km in Iceland, best available model elsewhere) | Wind, gusts, pressure, cloud, rain and air temperature by hour |
| Open-Meteo Marine API (DWD and Météo-France models) | Wave height, sea surface temperature and sea level including tides |
| Veðurstofa Íslands, `api.vedur.is/weather` (CC BY 4.0) | Latest 10-minute wind, gusts and pressure from the nearest station, in Iceland only |
| Wikipedia and Hafrannsóknastofnun | The fish facts and temperature ranges, built into the app |
| On-device sun and moon maths | Light and spring/neap tides |
| The phone's GPS and compass | Where you are and which way the water faces |

Every build runs a live check against these feeds for Keflavík harbour and puts the result in the release notes.

## Install

Every push to `main` builds a signed APK and publishes it as a GitHub Release. Open the latest release on your phone and tap `afli.apk`. Builds are signed with one key kept in the repo's Actions secrets (`KEYSTORE_B64`, `KEYSTORE_PASSWORD`, and optionally `KEY_ALIAS`), never in the code, so new versions install over the old one. Other branches build as debug-signed checks.

The app keeps itself current: it checks GitHub for a newer release when opened (at most hourly) and in the background every ~6 hours, shows a banner across the top of the app and one notification per version, and one tap on the banner (or Settings → Updates) downloads and installs it in place.

## Layout

```
app/src/main/java/app/afli/
  data/     GPS, Open-Meteo and Veðurstofa feeds, spots and trips on disk, app state
  model/    sun and moon maths, the fish, the bite score and safety
  ui/       glass, animated sea, screens, help (intro, coach marks, explanations), haptics
  update/   GitHub release check, download, install, background watcher
```

Built with Kotlin 2.4, Compose 1.12, Android Gradle Plugin 9.4 and Kyant's Backdrop 2.0.1.
