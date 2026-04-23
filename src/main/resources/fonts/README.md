# Montserrat (Open Font License)

The launcher prefers **Montserrat** for its UI. Place the following TTFs here:

- `Montserrat-Regular.ttf`
- `Montserrat-Medium.ttf`
- `Montserrat-SemiBold.ttf`
- `Montserrat-Bold.ttf`

**Download** : https://fonts.google.com/specimen/Montserrat → *Get font* → *Download all* → unzip → copy the four `static/` files here.

If these files are absent at runtime, JavaFX falls back to the system font stack defined in `src/main/resources/css/style.css` (Segoe UI / Helvetica Neue / Arial) — the UI still looks correct, just not in Montserrat.
