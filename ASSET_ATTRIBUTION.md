# Texture attribution

`app/src/main/res/drawable-nodpi/earth_night.jpg`,
`app/src/main/res/drawable-nodpi/earth_clouds.jpg` and
`app/src/main/res/drawable-nodpi/earth_day.jpg` are derived, resized mobile textures from
Solar System Scope's Earth texture collection, obtained via Wikimedia Commons:

- `Solarsystemscope_texture_8k_earth_nightmap.jpg`
- `Solarsystemscope_texture_2k_earth_clouds.jpg`
- `Solarsystemscope_texture_2k_earth_daymap.jpg`

They are licensed under **Creative Commons Attribution 4.0 International (CC BY 4.0)**. The
in-app **About & data sources** panel retains this credit. Keep an equivalent attribution in the
Play Store listing or in-app credits when publishing a derivative.

The imagery is based on NASA Earth/Black Marble source imagery. NASA source imagery is generally
public domain, but the packaged Solar System Scope derivatives remain subject to their stated
CC BY 4.0 attribution requirement.

Live weather readings are requested from [Open-Meteo](https://open-meteo.com/). Review its terms
and attribution guidance before changing request volume or publishing at scale.

City photographs are loaded at runtime from [Wikipedia](https://www.wikipedia.org/) via its
key-free REST API (`en.wikipedia.org/api/rest_v1/page/summary/...`); images carry their original
Creative Commons / public-domain licenses from Wikimedia Commons. Each image is shown with its
source city in the app's UI and is not redistributed from this repository.

The optional live-broadcast feature streams 24/7 television channels (for example WeatherNation,
Sky News, France 24, DW News) inside a WebView and links out to YouTube; it is a convenience link
to public broadcast channels, not an embedding of their content in this repository, and it can be
disabled from the planet tools menu.
