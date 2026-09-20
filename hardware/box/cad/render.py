"""
Draw kindred_badge.stl as a technical sheet, in the Klick palette.

No CAD tool and no 3D suite required — the STL is parsed directly and
rendered with matplotlib, so anyone who can run Python can regenerate the
sheet after changing the enclosure.

    python hardware/box/cad/render.py

Writes docs/badge.png.

Why line art rather than a shaded render: matplotlib is not a renderer. Its
3D artist sorts whole polygons by mean depth, which tears interlocking
geometry like this into z-fighting artifacts. Feature edges sidestep depth
sorting entirely — and a patent-style drawing reads as deliberate, where a
muddy shaded render reads as a failed photo.
"""
import struct
from collections import defaultdict
from pathlib import Path

import numpy as np
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.collections import LineCollection

ROOT = Path(__file__).resolve().parents[3]
STL = ROOT / "hardware" / "box" / "cad" / "kindred_badge.stl"
OUT = ROOT / "docs" / "badge.png"

# Same tokens as ui/theme/Color.kt.
PAGE = "#EEECE9"
INK = "#1C1B1A"
MUTED = "#A3A09B"
ACCENT = "#C97B45"

# Dihedral angle above which an edge counts as a real feature rather than
# tessellation of a smooth surface.
FEATURE_DEG = 22.0


def load_binary_stl(path):
    with open(path, "rb") as f:
        f.read(80)
        count = struct.unpack("<I", f.read(4))[0]
        raw = np.frombuffer(f.read(count * 50), dtype=np.uint8).reshape(count, 50)
    return raw[:, 12:48].copy().view("<f4").reshape(count, 3, 3).astype(np.float64)


def face_normals(tris):
    n = np.cross(tris[:, 1] - tris[:, 0], tris[:, 2] - tris[:, 0])
    return n / np.clip(np.linalg.norm(n, axis=1, keepdims=True), 1e-12, None)


def feature_edges(tris, thresh_deg=FEATURE_DEG):
    """Edges that bound the shape or sit on a hard crease."""
    normals = face_normals(tris)
    # Quantise so shared vertices from a mesher's float output actually match.
    keys = np.round(tris.reshape(-1, 3), 4)
    edges = defaultdict(list)
    for fi in range(len(tris)):
        v = keys[fi * 3:fi * 3 + 3]
        for a, b in ((0, 1), (1, 2), (2, 0)):
            ka, kb = tuple(v[a]), tuple(v[b])
            edges[(ka, kb) if ka <= kb else (kb, ka)].append(fi)

    cos_t = np.cos(np.radians(thresh_deg))
    out = []
    for (ka, kb), faces in edges.items():
        keep = len(faces) != 2
        if not keep:
            keep = float(np.dot(normals[faces[0]], normals[faces[1]])) < cos_t
        if keep:
            out.append((ka, kb))
    return np.array(out, dtype=np.float64)  # (E, 2, 3)


def project(pts, view):
    """Orthographic projection. pts is (..., 3)."""
    x, y, z = pts[..., 0], pts[..., 1], pts[..., 2]
    if view == "top":    return np.stack([x, y], -1)
    if view == "front":  return np.stack([x, z], -1)
    if view == "side":   return np.stack([y, z], -1)
    # Isometric: 30 degrees about two axes, the classic technical view.
    a = np.radians(30.0)
    ex = (x - y) * np.cos(a)
    ey = (x + y) * np.sin(a) + z
    return np.stack([ex, ey], -1)


def panel(ax, segs, view, title, accent=False):
    p = project(segs, view)
    ax.add_collection(LineCollection(
        p,
        colors=ACCENT if accent else INK,
        linewidths=0.55 if accent else 0.45,
        alpha=0.95 if accent else 0.85,
    ))
    flat = p.reshape(-1, 2)
    lo, hi = flat.min(0), flat.max(0)
    pad = (hi - lo).max() * 0.14
    cx, cy = (lo + hi) / 2
    half = (hi - lo).max() / 2 + pad
    ax.set_xlim(cx - half, cx + half)
    ax.set_ylim(cy - half, cy + half)
    ax.set_aspect("equal")
    ax.set_axis_off()
    # matplotlib's Text has no letter-spacing property, so the tracked-out
    # look the rest of the design system uses is done by hand.
    ax.text(
        0.0, 1.0, " ".join(title), transform=ax.transAxes,
        fontsize=8.5, color=MUTED, fontweight="bold",
        family="sans-serif", va="top", ha="left",
    )


def main():
    tris = load_binary_stl(STL)
    segs = feature_edges(tris)
    verts = tris.reshape(-1, 3)
    size = verts.max(0) - verts.min(0)

    fig, axes = plt.subplots(2, 2, figsize=(9, 9), dpi=200)
    fig.patch.set_facecolor(PAGE)
    for ax in axes.flat:
        ax.set_facecolor(PAGE)

    panel(axes[0][0], segs, "iso", "ISOMETRIC", accent=True)
    panel(axes[0][1], segs, "top", "TOP")
    panel(axes[1][0], segs, "front", "FRONT")
    panel(axes[1][1], segs, "side", "SIDE")

    fig.suptitle("Klick badge — enclosure", x=0.5, y=0.965,
                 fontsize=15, fontweight="bold", color=INK, family="sans-serif")
    fig.text(0.5, 0.932,
             f"{size[0]:.0f} × {size[1]:.0f} × {size[2]:.1f} mm   ·   "
             f"{len(tris):,} triangles   ·   {len(segs):,} feature edges",
             ha="center", fontsize=9, color=MUTED, family="sans-serif")

    fig.subplots_adjust(left=0.04, right=0.96, top=0.90, bottom=0.04,
                        wspace=0.02, hspace=0.04)
    OUT.parent.mkdir(parents=True, exist_ok=True)
    fig.savefig(OUT, facecolor=PAGE)
    plt.close(fig)

    print(f"{len(tris)} triangles -> {len(segs)} feature edges")
    print(f"{size[0]:.0f} x {size[1]:.0f} x {size[2]:.1f} mm")
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
