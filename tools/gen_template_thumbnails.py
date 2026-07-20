#!/usr/bin/env python3
"""Generates ASL's template thumbnails as VectorDrawables.

Original artwork drawn for ASL. It follows the familiar new-project-wizard idiom (a phone mockup
with a coloured app bar and a sketch of the template's layout) but every path here is ours -- the
reference app is GPLv3 and ASL is proprietary, so nothing is copied from it.
"""
import os

OUT = "app/src/main/res/drawable"

# --- palette -------------------------------------------------------------------------------------
GREEN = "#00A651"        # app bar
DARK_GREEN = "#0C7A3E"   # drawer header, tab pills
LIME = "#C6F000"         # selected/accent
LIGHT_GREEN = "#C8E6D2"  # drawer sheet
TEAL = "#009688"         # no-androidx bar (legacy = different family)
GREY = "#BDBDBD"         # inert content
DARK_GREY = "#6B6B6B"    # dashed outline
WHITE = "#FFFFFF"
BLUE = "#3B78E7"         # C++ mark
COMPOSE_TOP = "#3DDC84"
COMPOSE_RIGHT = "#4285F4"
COMPOSE_LEFT = "#1F3B54"

# --- geometry ------------------------------------------------------------------------------------
# The viewport hugs the phone (plus room for its shadow) so the drawable fills the picker tile
# rather than floating inside a square of empty margin.
VW, VH = 272.0, 452.0     # viewport
X0, X1 = 8.0, 260.0       # phone body
Y0, Y1 = 8.0, 430.0
R = 28.0                  # phone corner radius
BAR_Y = Y0 + 78.0         # app bar bottom
CX = (X0 + X1) / 2        # centre of the phone
CY = (Y0 + Y1) / 2


def rrect(x0, y0, x1, y1, r):
    return (f"M{x0 + r},{y0} H{x1 - r} A{r},{r} 0 0 1 {x1},{y0 + r} V{y1 - r} "
            f"A{r},{r} 0 0 1 {x1 - r},{y1} H{x0 + r} A{r},{r} 0 0 1 {x0},{y1 - r} "
            f"V{y0 + r} A{r},{r} 0 0 1 {x0 + r},{y0} Z")


def top_rrect(x0, y0, x1, y1, r):
    """Rounded on top, square on the bottom -- an app bar clipped by the phone body."""
    return (f"M{x0},{y1} V{y0 + r} A{r},{r} 0 0 1 {x0 + r},{y0} H{x1 - r} "
            f"A{r},{r} 0 0 1 {x1},{y0 + r} V{y1} Z")


def path(d, fill=None, stroke=None, width=0.0, alpha=None, cap="round"):
    out = f'    <path\n        android:pathData="{d}"'
    if fill:
        out += f'\n        android:fillColor="{fill}"'
    if alpha is not None:
        out += f'\n        android:fillAlpha="{alpha}"'
    if stroke:
        out += (f'\n        android:strokeColor="{stroke}"'
                f'\n        android:strokeWidth="{width}"'
                f'\n        android:strokeLineCap="{cap}"')
    return out + " />\n"


def doc(body):
    return ('<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{VW / 4:.0f}dp"\n'
            f'    android:height="{VH / 4:.0f}dp"\n'
            f'    android:viewportWidth="{VW:.0f}"\n'
            f'    android:viewportHeight="{VH:.0f}">\n'
            f'{body}</vector>\n')


def phone_shadow():
    return path(rrect(X0 + 4, Y0 + 8, X1 + 4, Y1 + 8, R), fill="#000000", alpha="0.08")


def phone_body(fill=WHITE):
    return phone_shadow() + path(rrect(X0, Y0, X1, Y1, R), fill=fill)


def app_bar(color=GREEN):
    """Bar plus the back arrow and overflow dots that mark it as an app bar."""
    out = path(top_rrect(X0, Y0, X1, BAR_Y, R), fill=color)
    cy = (Y0 + BAR_Y) / 2
    # back arrow
    out += path(f"M{X0 + 46},{cy} H{X0 + 20}", stroke=WHITE, width=6)
    out += path(f"M{X0 + 30},{cy - 10} L{X0 + 20},{cy} L{X0 + 30},{cy + 10}", stroke=WHITE, width=6)
    # overflow dots
    for i, dy in enumerate((-13, 0, 13)):
        out += path(f"M{X1 - 26},{cy + dy} h0.1", stroke=WHITE, width=6)
    return out


def clip_to_phone(inner):
    return ("    <group>\n"
            f'        <clip-path android:pathData="{rrect(X0, Y0, X1, Y1, R)}" />\n'
            f"{inner}"
            "    </group>\n")


def fab(cx, cy, r=26.0, fill=LIME, plus=GREEN):
    out = path(f"M{cx},{cy} m{-r},0 a{r},{r} 0 1 0 {2 * r},0 a{r},{r} 0 1 0 {-2 * r},0 Z", fill=fill)
    out += path(f"M{cx - 11},{cy} H{cx + 11}", stroke=plus, width=5)
    out += path(f"M{cx},{cy - 11} V{cy + 11}", stroke=plus, width=5)
    return out


def bar(x0, y0, x1, y1, fill, r=6.0):
    return path(rrect(x0, y0, x1, y1, r), fill=fill)


# --- the nine thumbnails -------------------------------------------------------------------------
def no_activity():
    """A dashed placeholder: the module exists, the UI is yours to add."""
    body = ""
    half = 78.0
    x0, x1 = CX - half, CX + half
    y0, y1 = CY - half, CY + half
    step, dash = 26.0, 15.0
    x = x0
    while x < x1:
        body += path(f"M{x},{y0} H{min(x + dash, x1)}", stroke=DARK_GREY, width=7, cap="butt")
        body += path(f"M{x},{y1} H{min(x + dash, x1)}", stroke=DARK_GREY, width=7, cap="butt")
        x += step
    y = y0
    while y < y1:
        body += path(f"M{x0},{y} V{min(y + dash, y1)}", stroke=DARK_GREY, width=7, cap="butt")
        body += path(f"M{x1},{y} V{min(y + dash, y1)}", stroke=DARK_GREY, width=7, cap="butt")
        y += step
    return doc(body)


def empty_activity():
    return doc(phone_body() + app_bar())


def basic_activity():
    return doc(phone_body() + app_bar() + fab(X1 - 52, Y1 - 58))


def cpp_activity():
    """No chrome, just the C++ mark -- this template is about the native layer, not the UI."""
    cx, cy = CX, CY
    body = phone_body()
    # open "C" arc
    body += path(f"M{cx + 30},{cy - 38} A48,48 0 1 0 {cx + 30},{cy + 38}", stroke=BLUE, width=11)
    for dx in (44.0, 82.0):
        body += path(f"M{cx + dx - 14},{cy} h28", stroke=BLUE, width=9)
        body += path(f"M{cx + dx},{cy - 14} v28", stroke=BLUE, width=9)
    return doc(body)


def nav_drawer():
    """The drawer open over dimmed content -- the template's whole point."""
    sheet_x = X0 + 168
    inner = path(f"M{X0},{Y0} H{X1} V{Y1} H{X0} Z", fill=GREY)
    inner += path(f"M{X0},{Y0} H{sheet_x} V{Y1} H{X0} Z", fill=LIGHT_GREEN)
    inner += path(f"M{X0},{Y0} H{sheet_x} V{Y0 + 148} H{X0} Z", fill=GREEN)
    # avatar + name line in the header
    inner += path(f"M{X0 + 84},{Y0 + 62} m-26,0 a26,26 0 1 0 52,0 a26,26 0 1 0 -52,0 Z",
                  fill=DARK_GREEN)
    inner += bar(X0 + 42, Y0 + 104, X0 + 126, Y0 + 118, DARK_GREEN)
    # destination rows
    for i in range(4):
        y = Y0 + 190 + i * 40
        inner += path(f"M{X0 + 40},{y} m-7,0 a7,7 0 1 0 14,0 a7,7 0 1 0 -14,0 Z", fill=GREEN)
        inner += bar(X0 + 58, y - 4, X0 + 58 + (86 if i % 2 == 0 else 60), y + 4, GREEN, r=4)
    body = phone_shadow() + clip_to_phone(inner)
    # overflow dots sit on the content side, above the drawer
    for dy in (-13, 0, 13):
        body += path(f"M{X1 - 26},{Y0 + 39 + dy} h0.1", stroke=WHITE, width=6)
    body += fab(X1 - 52, Y1 - 58, r=22)
    return doc(body)


def bottom_nav():
    inner = path(f"M{X0},{Y1 - 62} H{X1} V{Y1} H{X0} Z", fill=GREEN)
    third = (X1 - X0) / 3
    inner += path(f"M{X0 + third},{Y1 - 62} H{X0 + 2 * third} V{Y1} H{X0 + third} Z", fill=LIME)
    return doc(phone_body() + clip_to_phone(inner) + app_bar())


def tabbed():
    """Tabs live inside the app bar, with the selected one underlined."""
    bar_bottom = BAR_Y + 34
    body = phone_body()
    inner = path(top_rrect(X0, Y0, X1, bar_bottom, R), fill=GREEN)
    cy = (Y0 + BAR_Y) / 2
    inner += path(f"M{X0 + 46},{cy} H{X0 + 20}", stroke=WHITE, width=6)
    inner += path(f"M{X0 + 30},{cy - 10} L{X0 + 20},{cy} L{X0 + 30},{cy + 10}", stroke=WHITE, width=6)
    for dy in (-13, 0, 13):
        inner += path(f"M{X1 - 26},{cy + dy} h0.1", stroke=WHITE, width=6)
    tabs = ((X0 + 18, X0 + 90, LIGHT_GREEN), (X0 + 104, X0 + 176, DARK_GREEN),
            (X0 + 190, X0 + 262, DARK_GREEN))
    for x_start, x_end, fill in tabs:
        inner += bar(x_start, BAR_Y - 12, x_end, BAR_Y + 6, fill, r=9)
    inner += bar(X0 + 18, bar_bottom - 8, X0 + 90, bar_bottom, LIME, r=4)
    return doc(body + clip_to_phone(inner))


def no_androidx():
    """A struck-through support pill: the framework-only template."""
    body = phone_body() + app_bar(TEAL)
    cx, cy = CX, CY
    body += path(rrect(cx - 76, cy - 22, cx + 76, cy + 22, 22), fill="#E0E0E0")
    body += bar(cx - 56, cy - 6, cx + 36, cy + 6, GREY, r=6)
    body += path(f"M{cx - 84},{cy + 34} L{cx + 84},{cy - 34}", stroke=TEAL, width=10)
    return doc(body)


def compose():
    """The Compose cube, drawn as three faces."""
    body = phone_body() + app_bar()
    cx, cy, s = CX, CY + 16, 62.0
    top = f"M{cx},{cy - s} L{cx + s},{cy - s / 2} L{cx},{cy} L{cx - s},{cy - s / 2} Z"
    left = f"M{cx - s},{cy - s / 2} L{cx},{cy} L{cx},{cy + s} L{cx - s},{cy + s / 2} Z"
    right = f"M{cx + s},{cy - s / 2} L{cx + s},{cy + s / 2} L{cx},{cy + s} L{cx},{cy} Z"
    body += path(left, fill=COMPOSE_LEFT)
    body += path(right, fill=COMPOSE_RIGHT)
    body += path(top, fill=COMPOSE_TOP)
    # the little cube nested in the corner
    m = 24.0
    body += path(f"M{cx},{cy - m} L{cx + m},{cy - m / 2} L{cx},{cy} L{cx - m},{cy - m / 2} Z",
                 fill="#F5F5F5")
    body += path(f"M{cx - m},{cy - m / 2} L{cx},{cy} L{cx},{cy + m} L{cx - m},{cy + m / 2} Z",
                 fill="#D8D8D8")
    body += path(f"M{cx + m},{cy - m / 2} L{cx + m},{cy + m / 2} L{cx},{cy + m} L{cx},{cy} Z",
                 fill="#EDEDED")
    return doc(body)


THUMBS = {
    "template_no_activity": no_activity,
    "template_empty_activity": empty_activity,
    "template_basic_activity": basic_activity,
    "template_nav_drawer": nav_drawer,
    "template_bottom_nav": bottom_nav,
    "template_compose": compose,
}

os.makedirs(OUT, exist_ok=True)
for name, fn in THUMBS.items():
    with open(os.path.join(OUT, f"{name}.xml"), "w") as f:
        f.write(fn())
    print("wrote", name)
