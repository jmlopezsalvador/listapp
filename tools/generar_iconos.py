#!/usr/bin/env python3
"""
Genera TODO lo derivado de los iconos y nombres de la app a partir de data/icons/. Reproducible: no edites a mano lo generado.

  - Iconos incrustados (modo «Icono» de la caja de la pantalla 3 y miniaturas de la configuración):
      shared/src/commonMain/kotlin/com/listapp/ui/FakeIconsData.kt
  - Android (icono y nombre del lanzador, un <activity-alias> por cada combinación icono × nombre):
      app/src/main/res/drawable-nodpi/ic_<id>_fg.png        primer plano 432 x 432 del icono adaptativo
      app/src/main/res/mipmap-anydpi-v26/ic_<id>.xml        icono adaptativo
      app/src/main/res/values/alias_generated.xml           colores de fondo y nombres
      app/src/main/AndroidManifest.xml                      (solo entre las marcas GENERADO)
      shared/src/androidMain/kotlin/com/listapp/ui/AppIconList.kt
  - iOS (iconos alternativos; el nombre no se puede cambiar):
      iosApp/iosApp/Assets.xcassets/<id>.appiconset/        icon-1024.png (RGB, sin alfa) + Contents.json
      iosApp/project.yml                                    (solo entre las marcas GENERADO)
      shared/src/iosMain/kotlin/com/listapp/ui/AppIconList.kt

Para añadir un icono:  PNG en data/icons/png/<id>.png + entrada (id, label) en data/icons/fuentes.json  (o una línea en EXTRA_ICONS)
                       y, si debe salir en Android / iOS, el id en las listas de abajo. Después:  python tools/generar_iconos.py
Para añadir un nombre: una línea en NAMES y volver a ejecutar. Si cambia el icono por defecto, hay que cambiar a mano el android:icon de <application> en el manifiesto.
Los ids son los mismos en Kotlin, en el alias de Android (.Icon_<icono>_<nombre>) y en el appiconset de iOS.
"""
import base64
import json
import os
import re
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ICONS_DIR = os.path.join(ROOT, "data", "icons")

# ============================================================ LISTAS EDITABLES
# Iconos que no vienen del catálogo: (id, etiqueta, ruta relativa a la raíz). Vacío: todos salen de data/icons/.
EXTRA_ICONS = []

# Android: ids de los iconos que van al lanzador (None = todos) y el de por defecto.
ANDROID_ICONS = None
ANDROID_DEFAULT_ICON = "apple_notes"
# Iconos de Android que ya tienen su recurso hecho a mano (icono adaptativo existente): id -> (icon, roundIcon). Ninguno.
ANDROID_NATIVE = {}
# Cómo se coloca un icono opaco y a cuadrado completo en el icono adaptativo:
#   "bleed" = a sangre (432 px; el sistema recorta el tercio exterior), "inset" = reducido (290 px) sobre el color del borde (se ve entero).
ANDROID_FIT_DEFAULT = "inset"
ANDROID_FIT = {}  # id -> "bleed" | "inset"

# Nombres que puede mostrar el lanzador de Android: (id, texto visible). El primero es el de por defecto.
NAMES = [
    ("listapp", "ListApp"),
    ("listas", "Listas"),
    ("notas", "Notas"),
    ("recordatorios", "Recordatorios"),
    ("lists", "Lists"),
]

# iOS: icono principal (se escribe en AppIcon.appiconset) e iconos alternativos.
IOS_MAIN = "apple_notes"
IOS_ALTERNATES = ["apple_reminders"]

SIZE = 256          # lado de los iconos incrustados
CHUNK = 20000       # caracteres por constante (el límite de una constante de la JVM es 65535 bytes)
FG = 432            # primer plano del icono adaptativo (108 dp a 4x)
INSET = 290         # tamaño del icono dentro del primer plano en modo «inset»
# ============================================================

OUT_FAKE = os.path.join(ROOT, "shared", "src", "commonMain", "kotlin", "com", "listapp", "ui", "FakeIconsData.kt")
RES = os.path.join(ROOT, "app", "src", "main", "res")
MANIFEST = os.path.join(ROOT, "app", "src", "main", "AndroidManifest.xml")
XCASSETS = os.path.join(ROOT, "iosApp", "iosApp", "Assets.xcassets")
PROJECT_YML = os.path.join(ROOT, "iosApp", "project.yml")
KT_ANDROID = os.path.join(ROOT, "shared", "src", "androidMain", "kotlin", "com", "listapp", "ui", "AppIconList.kt")
KT_IOS = os.path.join(ROOT, "shared", "src", "iosMain", "kotlin", "com", "listapp", "ui", "AppIconList.kt")
GEN = "GENERADO por tools/generar_iconos.py: no editar a mano."


def warn(msg):
    print("AVISO: " + msg, file=sys.stderr)


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def load_icons():
    icons = []  # (id, label, ruta)
    for icon_id, label, rel in EXTRA_ICONS:
        icons.append((icon_id, label, os.path.join(ROOT, rel)))
    with open(os.path.join(ICONS_DIR, "fuentes.json"), encoding="utf-8-sig") as f:
        for e in json.load(f):
            icons.append((e["id"], e["label"], os.path.join(ICONS_DIR, e["png"])))
    return icons


def kt_str(s):
    return s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")


def replace_between(path, begin, end, new_block):
    """Sustituye lo que hay entre dos marcas (las marcas se conservan)."""
    with open(path, encoding="utf-8") as f:
        text = f.read()
    pattern = re.compile(r"(?P<b>[ \t]*" + re.escape(begin) + r"[^\n]*\n).*?(?P<e>[ \t]*" + re.escape(end) + r")", re.S)
    if not pattern.search(text):
        raise SystemExit("Faltan las marcas %s / %s en %s" % (begin, end, path))
    text = pattern.sub(lambda m: m.group("b") + new_block + m.group("e"), text, count=1)
    write(path, text)


# ------------------------------------------------------------ iconos incrustados
def gen_fake(icons):
    lines = [
        "// " + GEN + " Para cambiarlo, edita el script o data/icons y vuelve a ejecutarlo.",
        "package com.listapp.ui",
        "",
        "/** Iconos de 256 px en base64 (PNG), troceados para no pasar el límite de una constante de la JVM. */",
        "internal object FakeIconsData {",
        "    val ids: List<String> = listOf(" + ", ".join('"%s"' % kt_str(i[0]) for i in icons) + ")",
        "",
        "    val labels: Map<String, String> = mapOf(",
    ]
    for icon_id, label, _ in icons:
        lines.append('        "%s" to "%s",' % (kt_str(icon_id), kt_str(label)))
    lines += ["    )", "", "    val parts: Map<String, List<String>> = mapOf("]
    for icon_id, label, path in icons:
        im = Image.open(path).convert("RGBA").resize((SIZE, SIZE), Image.LANCZOS)
        tmp = path + ".tmp.png"
        im.save(tmp, "PNG", optimize=True)
        with open(tmp, "rb") as f:
            b64 = base64.b64encode(f.read()).decode("ascii")
        os.remove(tmp)
        chunks = [b64[i:i + CHUNK] for i in range(0, len(b64), CHUNK)]
        assert all(len(c) < 60000 for c in chunks)
        lines.append('        "%s" to listOf(' % kt_str(icon_id))
        lines += ['            "%s",' % c for c in chunks]
        lines.append("        ),")
    lines += ["    )", "}", ""]
    write(OUT_FAKE, "\n".join(lines))


# ------------------------------------------------------------ Android
def edge_color(im):
    """Media de los píxeles opacos del borde del icono."""
    w, h = im.size
    px = im.load()
    acc, n = [0, 0, 0], 0
    for x in range(w):
        for y in (0, h - 1):
            r, g, b, a = px[x, y]
            if a > 200:
                acc[0] += r; acc[1] += g; acc[2] += b; n += 1
    for y in range(1, h - 1):
        for x in (0, w - 1):
            r, g, b, a = px[x, y]
            if a > 200:
                acc[0] += r; acc[1] += g; acc[2] += b; n += 1
    if n == 0:
        return "#FFFFFF"
    return "#%02X%02X%02X" % tuple(c // n for c in acc)


def gen_android(icons):
    chosen = [i for i in icons if ANDROID_ICONS is None or i[0] in ANDROID_ICONS]
    chosen.sort(key=lambda i: i[0] != ANDROID_DEFAULT_ICON)  # el de por defecto, el primero
    ids = [i[0] for i in chosen]
    assert ANDROID_DEFAULT_ICON in ids, "el icono por defecto de Android no está en la lista"
    colors, aliases = [], []
    for icon_id, label, path in chosen:
        if icon_id in ANDROID_NATIVE:
            icon, round_icon = ANDROID_NATIVE[icon_id]
        else:
            im = Image.open(path).convert("RGBA")
            if im.size[0] < 400:
                warn("%s mide %d px: se amplía/reduce a %d (el Android moderno lo pide a 432; si es menor sale borroso)" % (icon_id, im.size[0], FG))
            fit = ANDROID_FIT.get(icon_id, ANDROID_FIT_DEFAULT)
            opaque_square = im.getchannel("A").getextrema()[0] == 255
            if opaque_square and fit == "bleed":
                fg = im.resize((FG, FG), Image.LANCZOS)
            elif opaque_square:
                fg = Image.new("RGBA", (FG, FG), (0, 0, 0, 0))
                fg.paste(im.resize((INSET, INSET), Image.LANCZOS), ((FG - INSET) // 2,) * 2)
            else:
                box = im.getbbox() or (0, 0) + im.size
                crop = im.crop(box)
                k = 310 / max(crop.size)
                crop = crop.resize((max(1, round(crop.size[0] * k)), max(1, round(crop.size[1] * k))), Image.LANCZOS)
                fg = Image.new("RGBA", (FG, FG), (0, 0, 0, 0))
                fg.paste(crop, ((FG - crop.size[0]) // 2, (FG - crop.size[1]) // 2), crop)
            os.makedirs(os.path.join(RES, "drawable-nodpi"), exist_ok=True)
            fg.save(os.path.join(RES, "drawable-nodpi", "ic_%s_fg.png" % icon_id), optimize=True)
            colors.append((icon_id, edge_color(im)))
            write(os.path.join(RES, "mipmap-anydpi-v26", "ic_%s.xml" % icon_id),
                  '<?xml version="1.0" encoding="utf-8"?>\n<!-- %s -->\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
                  '    <background android:drawable="@color/ic_bg_%s" />\n    <foreground android:drawable="@drawable/ic_%s_fg" />\n'
                  '</adaptive-icon>\n' % (GEN, icon_id, icon_id))
            icon = round_icon = "@mipmap/ic_%s" % icon_id
        for name_id, _ in NAMES:
            default = icon_id == ANDROID_DEFAULT_ICON and name_id == NAMES[0][0]
            aliases.append(
                '        <activity-alias\n'
                '            android:name=".Icon_%s_%s"\n'
                '            android:targetActivity=".MainActivity"\n'
                '            android:exported="true"\n'
                '            android:enabled="%s"\n'
                '            android:icon="%s"\n'
                '            android:roundIcon="%s"\n'
                '            android:label="@string/icon_name_%s">\n'
                '            <intent-filter>\n'
                '                <action android:name="android.intent.action.MAIN" />\n'
                '                <category android:name="android.intent.category.LAUNCHER" />\n'
                '            </intent-filter>\n'
                '        </activity-alias>\n' % (icon_id, name_id, "true" if default else "false", icon, round_icon, name_id))
    # colores y nombres
    xml = ['<?xml version="1.0" encoding="utf-8"?>', "<!-- " + GEN + " -->", "<resources>"]
    for icon_id, c in colors:
        xml.append('    <color name="ic_bg_%s">%s</color>' % (icon_id, c))
    for name_id, text in NAMES:
        xml.append('    <string name="icon_name_%s">%s</string>' % (name_id, text.replace("&", "&amp;").replace("<", "&lt;").replace("'", "\\'")))
    xml += ["</resources>", ""]
    write(os.path.join(RES, "values", "alias_generated.xml"), "\n".join(xml))
    replace_between(MANIFEST, "<!-- GENERADO:INICIO alias", "<!-- GENERADO:FIN alias", "".join(aliases))
    # lista en Kotlin
    kt = [
        "// " + GEN, "package com.listapp.ui", "",
        "/** Iconos y nombres que se pueden elegir en Android (cada combinación es un <activity-alias> del manifiesto). */",
        "object AppIconList {",
        "    val icons: List<AppIconOption> = listOf(",
    ] + ['        AppIconOption("%s", "%s"),' % (i[0], kt_str(i[1])) for i in chosen] + [
        "    )", "", "    val names: List<AppIconOption> = listOf(",
    ] + ['        AppIconOption("%s", "%s"),' % (n[0], kt_str(n[1])) for n in NAMES] + [
        "    )", "", '    const val DEFAULT_ICON = "%s"' % ANDROID_DEFAULT_ICON, '    const val DEFAULT_NAME = "%s"' % NAMES[0][0], "}", ""]
    write(KT_ANDROID, "\n".join(kt))
    print("Android: %d iconos x %d nombres = %d alias" % (len(chosen), len(NAMES), len(chosen) * len(NAMES)), file=sys.stderr)


# ------------------------------------------------------------ iOS
def gen_ios(icons):
    by_id = {i[0]: i for i in icons}
    # el icono principal es el AppIcon de siempre (1024 px, RGB, sin alfa)
    main = Image.open(by_id[IOS_MAIN][2]).convert("RGBA").resize((1024, 1024), Image.LANCZOS)
    main_bg = Image.new("RGBA", main.size, (255, 255, 255, 255))
    main_bg.alpha_composite(main)
    main_bg.convert("RGB").save(os.path.join(XCASSETS, "AppIcon.appiconset", "AppIcon-1024.png"), optimize=True)
    for icon_id in IOS_ALTERNATES:
        _, label, path = by_id[icon_id]
        im = Image.open(path).convert("RGBA")
        if im.size[0] < 1024:
            warn("iOS: %s mide %d px y se amplía a 1024 (saldrá algo borroso)" % (icon_id, im.size[0]))
        big = im.resize((1024, 1024), Image.LANCZOS)
        bg = Image.new("RGBA", big.size, (255, 255, 255, 255))  # sin canal alfa: se compone sobre blanco
        bg.alpha_composite(big)
        d = os.path.join(XCASSETS, "%s.appiconset" % icon_id)
        os.makedirs(d, exist_ok=True)
        bg.convert("RGB").save(os.path.join(d, "icon-1024.png"), optimize=True)
        write(os.path.join(d, "Contents.json"), json.dumps({
            "images": [{"filename": "icon-1024.png", "idiom": "universal", "platform": "ios", "size": "1024x1024"}],
            "info": {"author": "xcode", "version": 1},
        }, indent=2) + "\n")
    replace_between(PROJECT_YML, "# GENERADO:INICIO iconos alternativos", "# GENERADO:FIN iconos alternativos",
                    '        ASSETCATALOG_COMPILER_ALTERNATE_APPICON_NAMES: "%s"\n' % " ".join(IOS_ALTERNATES))
    listed = [IOS_MAIN] + IOS_ALTERNATES
    kt = [
        "// " + GEN, "package com.listapp.ui", "",
        "/** Iconos que se pueden elegir en iOS (el nombre no se puede cambiar). El principal es el AppIcon; los demás, iconos alternativos. */",
        "object AppIconList {",
        "    val icons: List<AppIconOption> = listOf(",
    ] + ['        AppIconOption("%s", "%s"),' % (i, kt_str(by_id[i][1])) for i in listed] + [
        "    )", "", "    val names: List<AppIconOption> = emptyList()", "", '    const val MAIN_ICON = "%s"' % IOS_MAIN, "}", ""]
    write(KT_IOS, "\n".join(kt))
    print("iOS: principal %s + alternativos %s" % (IOS_MAIN, ", ".join(IOS_ALTERNATES)), file=sys.stderr)


def main():
    icons = load_icons()
    gen_fake(icons)
    gen_android(icons)
    gen_ios(icons)
    print("Hecho.", file=sys.stderr)


if __name__ == "__main__":
    main()
