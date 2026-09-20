"""Kindred badge backplate for ESP32-S3-BOX-3 (dimension-tolerant, rubber-band mount).
Prints flat, no supports. Units: mm."""
import numpy as np, trimesh
from shapely.geometry import Polygon, Point, box
from shapely.ops import unary_union
from shapely import affinity

W, H, T = 74.0, 78.0, 2.6          # plate width, height, thickness
R = 6.0                             # corner radius
EAR_R, EAR_X, EAR_Y = 13.0, 23.0, H/2 + 5.0
TAB_W, TAB_H = 30.0, 15.0           # lanyard tab above plate top
SLOT_W, SLOT_H = 20.0, 5.0          # lanyard slot
NOTCH_R, NOTCH_PITCH = 2.2, 10.0    # rubber-band notches
LIP_W, LIP_D, LIP_Z = 15.0, 3.0, 11.0   # corner lips: width, depth(Y), height above plate
WIN_W, WIN_H = 40.0, 40.0           # weight/time-saving window

def rrect(w, h, r, cx=0, cy=0):
    return box(cx-w/2+r, cy-h/2+r, cx+w/2-r, cy+h/2-r).buffer(r, quad_segs=12)

plate = rrect(W, H, R)
ears = [Point(sx*EAR_X, EAR_Y).buffer(EAR_R, quad_segs=24) for sx in (-1, 1)]
tab = rrect(TAB_W, TAB_H + 8, 5, 0, H/2 + TAB_H/2 - 4)
outline = unary_union([plate, tab, *ears])

cuts = [rrect(SLOT_W, SLOT_H, 2.2, 0, H/2 + TAB_H - 6.5),          # lanyard slot
        rrect(WIN_W, WIN_H, 6, 0, -2)]                               # centre window
ys = np.arange(-H/2 + 14, H/2 - 8, NOTCH_PITCH)
for y in ys:                                                          # side notches
    for sx in (-1, 1):
        cuts.append(Point(sx*W/2, y).buffer(NOTCH_R, quad_segs=12))
for x in (-24, -12, 12, 24):                                          # bottom notches
    cuts.append(Point(x, -H/2).buffer(NOTCH_R, quad_segs=12))
profile = outline.difference(unary_union(cuts))

body = trimesh.creation.extrude_polygon(profile, T)
lips = []
for sx in (-1, 1):
    lip = trimesh.creation.box(extents=[LIP_W, LIP_D, LIP_Z + T])
    lip.apply_translation([sx*(W/2 - LIP_W/2 - 5), -H/2 + LIP_D/2 + 4.5, (LIP_Z + T)/2])
    lips.append(lip)
mesh = trimesh.boolean.union([body, *lips], engine="manifold")
mesh.export(__file__.replace(".py", ".stl"))
print("watertight:", mesh.is_watertight, "| volume cm3: %.1f" % (mesh.volume/1000),
      "| bbox mm:", np.round(mesh.extents, 1))
