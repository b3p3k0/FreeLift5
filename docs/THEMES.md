# FreeLift5 Themes

FreeLift5 includes three light themes and two dark themes. Theme preferences
stay on the device and are not included in data exports.

## Gallery

The labeled comparison is intended for documentation:

![Five FreeLift5 themes](images/themes/theme-comparison-labeled.png)

Individual labeled captures:

- [Solarized Light](images/themes/labeled/solarized-light.png)
- [Field Manual](images/themes/labeled/field-manual.png)
- [Pool Tile](images/themes/labeled/pool-tile.png)
- [Solarized Dark](images/themes/labeled/solarized-dark.png)
- [Foundry](images/themes/labeled/foundry.png)

Promotional cards:

- [Solarized Light](images/themes/promo/solarized-light.png)
- [Field Manual](images/themes/promo/field-manual.png)
- [Pool Tile](images/themes/promo/pool-tile.png)
- [Solarized Dark](images/themes/promo/solarized-dark.png)
- [Foundry](images/themes/promo/foundry.png)
- [Five-card comparison](images/themes/theme-comparison-promo.png)

Native emulator captures and their comparison are under
[`images/themes/raw/`](images/themes/raw/) and
[`theme-comparison-raw.png`](images/themes/theme-comparison-raw.png).

## Rebuild The Gallery

Capture the same populated Workout home screen for each theme on the API 36
Pixel 9 emulator, using the filenames above. Then run:

```bash
scripts/build-theme-gallery.sh docs/images/themes/raw
```

The script validates equal source dimensions, strips incidental metadata,
creates labeled 540 px panels and 1080 x 1350 promotional cards, and stitches
each image set in the theme-picker order.
