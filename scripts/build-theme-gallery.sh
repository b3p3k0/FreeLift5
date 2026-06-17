#!/usr/bin/env bash
set -euo pipefail

RAW_DIR="${1:-docs/images/themes/raw}"
THEME_ROOT="$(dirname "$RAW_DIR")"

command -v magick >/dev/null 2>&1 || {
    printf 'ImageMagick 7 (magick) is required.\n' >&2
    exit 1
}

themes=(
    solarized-light
    field-manual
    pool-tile
    solarized-dark
    foundry
)
labels=(
    "Solarized Light"
    "Field Manual"
    "Pool Tile"
    "Solarized Dark"
    "Foundry"
)
modes=(
    "Light"
    "Light"
    "Light"
    "Dark"
    "Dark"
)
backgrounds=(
    "#FDF6E3"
    "#F3E9CF"
    "#F2F7F4"
    "#002B36"
    "#171A1B"
)
foregrounds=(
    "#002B36"
    "#28251C"
    "#10201E"
    "#FDF6E3"
    "#F3EEE6"
)
swatches=(
    "#FDF6E3 #006A94 #376E68 #765F00"
    "#F3E9CF #46512F #6B5844 #A44323"
    "#F2F7F4 #006B73 #405D78 #A63D4C"
    "#002B36 #2AA198 #B58900 #9C9FE8"
    "#171A1B #E47A42 #8EB9C4 #D0AD58"
)

for theme in "${themes[@]}"; do
    path="$RAW_DIR/$theme.png"
    [[ -f "$path" ]] || {
        printf 'Missing raw capture: %s\n' "$path" >&2
        exit 1
    }
done

expected_dimensions="$(magick identify -format '%wx%h' "$RAW_DIR/${themes[0]}.png")"
for theme in "${themes[@]}"; do
    path="$RAW_DIR/$theme.png"
    dimensions="$(magick identify -format '%wx%h' "$path")"
    [[ "$dimensions" == "$expected_dimensions" ]] || {
        printf '%s is %s; expected %s.\n' "$path" "$dimensions" "$expected_dimensions" >&2
        exit 1
    }
done

tmp="$(mktemp -d -t freelift5-theme-gallery.XXXXXX)"
trap 'rm -rf "$tmp"' EXIT
mkdir -p "$tmp/raw" "$tmp/labeled" "$tmp/promo"

for index in "${!themes[@]}"; do
    theme="${themes[$index]}"
    label="${labels[$index]}"
    mode="${modes[$index]}"
    background="${backgrounds[$index]}"
    foreground="${foregrounds[$index]}"

    magick "$RAW_DIR/$theme.png" \
        -auto-orient \
        -colorspace sRGB \
        -depth 8 \
        -strip \
        "$tmp/raw/$theme.png"

    magick "$tmp/raw/$theme.png" -resize 540x "$tmp/screen-$theme.png"
    magick \
        -size 540x72 "xc:$background" \
        -fill "$foreground" \
        -font DejaVu-Sans-Bold \
        -pointsize 24 \
        -gravity West \
        -annotate +22+0 "$label  |  $mode" \
        "$tmp/label-$theme.png"
    magick "$tmp/label-$theme.png" "$tmp/screen-$theme.png" \
        -append \
        -colorspace sRGB \
        -depth 8 \
        -strip \
        "$tmp/labeled/$theme.png"

    read -r swatch_one swatch_two swatch_three swatch_four <<<"${swatches[$index]}"
    magick \
        -size 42x42 "xc:$swatch_one" \
        -size 42x42 "xc:$swatch_two" \
        -size 42x42 "xc:$swatch_three" \
        -size 42x42 "xc:$swatch_four" \
        +append \
        "$tmp/swatches-$theme.png"
    magick "$tmp/raw/$theme.png" \
        -resize 470x \
        -bordercolor "$foreground" \
        -border 4 \
        "$tmp/phone-$theme.png"
    magick \
        -size 1080x1350 "xc:$background" \
        -fill "$foreground" \
        -font DejaVu-Sans-Bold \
        -pointsize 48 \
        -gravity NorthWest \
        -annotate +64+48 "$label" \
        -font DejaVu-Sans \
        -pointsize 26 \
        -annotate +66+112 "$mode theme" \
        "$tmp/promo-canvas-$theme.png"
    magick "$tmp/promo-canvas-$theme.png" \
        "$tmp/swatches-$theme.png" -gravity NorthWest -geometry +66+158 -composite \
        "$tmp/phone-$theme.png" -gravity South -geometry +0+32 -composite \
        -colorspace sRGB \
        -depth 8 \
        -strip \
        "$tmp/promo/$theme.png"
done

raw_files=()
labeled_files=()
promo_files=()
for theme in "${themes[@]}"; do
    raw_files+=("$tmp/raw/$theme.png")
    labeled_files+=("$tmp/labeled/$theme.png")
    promo_files+=("$tmp/promo/$theme.png")
done

magick montage "${raw_files[@]}" \
    -tile 5x1 \
    -geometry +12+0 \
    -background "#D7D7D7" \
    -colorspace sRGB \
    -depth 8 \
    -strip \
    "$tmp/theme-comparison-raw.png"
magick montage "${labeled_files[@]}" \
    -tile 5x1 \
    -geometry +12+0 \
    -background "#D7D7D7" \
    -colorspace sRGB \
    -depth 8 \
    -strip \
    "$tmp/theme-comparison-labeled.png"
magick montage "${promo_files[@]}" \
    -tile 5x1 \
    -geometry +12+0 \
    -background "#D7D7D7" \
    -colorspace sRGB \
    -depth 8 \
    -strip \
    "$tmp/theme-comparison-promo.png"

generated_count="$(find "$tmp" -maxdepth 2 -type f -name '*.png' \
    ! -name 'screen-*' \
    ! -name 'label-*' \
    ! -name 'swatches-*' \
    ! -name 'phone-*' \
    ! -name 'promo-canvas-*' | wc -l)"
[[ "$generated_count" -eq 18 ]] || {
    printf 'Expected 18 gallery images, generated %s.\n' "$generated_count" >&2
    exit 1
}

mkdir -p "$RAW_DIR" "$THEME_ROOT/labeled" "$THEME_ROOT/promo"
cp "$tmp/raw/"*.png "$RAW_DIR/"
cp "$tmp/labeled/"*.png "$THEME_ROOT/labeled/"
cp "$tmp/promo/"*.png "$THEME_ROOT/promo/"
cp "$tmp"/theme-comparison-*.png "$THEME_ROOT/"

printf 'Generated 18 theme gallery images in %s\n' "$THEME_ROOT"
