# SolidCraft CAD v0.21.0 — Samsung Galaxy S26 Ultra Android Edition (`r2`)

**Android Port & S26 Ultra Build Creator:** Sanidhya Verma (@Zero70400)  
**Original Upstream Project:** [SolidCraft](https://github.com/tangency-labs/solidcraft) by **Tangency Labs**, **B. Herbruck (`bherbruck`)**, **Karl Von Richter**, and **SolidCraft Contributors**  
**License:** MIT License (with dual Apache-2.0 option upstream — see [`LICENSE-MIT`](LICENSE-MIT))  
**Package:** `com.tangencylabs.solidcraft`  
**Version:** `0.21.0-s26ultra-r2` (`versionCode 2102`)  
**APK File:** `solidcraft-s26ultra-v0.21.0-r2.apk` (~93 MB, 100% Offline)  
**Target Device:** Samsung Galaxy S26 Ultra (`arm64-v8a`, Android 15/16, 19.5:9 QHD+/FHD+, Split-Screen & Samsung DeX)

---

## 1. What Is New in This Android Build (`s26ultra-r2`)

1. **Automatic Screen-Size & Orientation Optimization (`AutoScreenAdapter`)**
   - Automatically detects your screen dimensions, pixel density, and orientation on startup and on every rotation, split-screen resize, or Samsung DeX window resize:
     - **Landscape (19.5:9):** Scales the UI (`~0.70×`) to provide a full `~1211 × 560 pt` logical CAD workspace where the top Ribbon, Model Browser tree, 3D Viewport, and bottom Parametric Timeline all fit cleanly without clipping.
     - **Portrait (9:19.5):** Scales the UI (`~0.56–0.65×`), switches the top ribbon into compact mode, and automatically collapses the left Browser tree so the 3D viewport stays wide and unobstructed.
   - **Manual + Auto Scale Button:** Go to **File / Menu → Preferences → Interface → Scale** and tap **`Auto`** at any time to re-fit the interface to your current screen, or drag the slider (`0.50× – 2.00×`) manually.
   - **HiDPI Fix:** Fixes the mobile `ResizeObserverEntry.devicePixelContentBoxSize` double-scaling bug so icons, text, and gizmos render crisp at native S26 Ultra QHD+ resolution.

2. **100% Offline Zero-Copy Engine (`net::ERR_CACHE_MISS` Fixed)**
   - **Triple-Redundant Asset Streaming:** Streams the 92 MB compiled Rust/WASM CAD engine (`solidcraft-web.wasm`) directly from uncompressed APK entries in 64 KB chunks via an embedded `127.0.0.1` loopback HTTP server + `WebViewClient.shouldInterceptRequest` + `ServiceWorkerClient` fallback.
   - **No Internet Required:** Works completely offline in Airplane mode while still supporting `Cross-Origin-Opener-Policy: same-origin` and `Cross-Origin-Embedder-Policy: require-corp` for full WebGPU / WebGL2 hardware acceleration.

3. **Crash-Proof Android 15 / 16 (One UI 7 / 8) Lifecycle**
   - Safe `DecorView` fullscreen insets initialization after `setContentView(...)`, display-cutout (`shortEdges`) support, automatic WebView renderer recovery (`onRenderProcessGone`), and an on-screen diagnostic view if any unexpected error occurs.
   - **Android Back Gesture / Button:** Pressing Back sends `Escape` to SolidCraft (closing the active dialog, sketch tool, or selection) instead of accidentally closing your CAD session.
   - **Native File Open & Downloads:** Opening a CAD/mesh file launches the Android system file picker (`ACTION_OPEN_DOCUMENT`), and exported files (`.step`, `.stl`, `.3mf`, `.obj`, `.dxf`, `.solidcraft`) save directly to your phone's **Downloads** folder with a confirmation toast.

---

## 2. Touch, S-Pen & Navigation Controls

| Action | Touch / S-Pen Gesture |
|---|---|
| **Orbit 3D Model** | **1-finger drag** on empty viewport space |
| **Select Face / Edge / Vertex / Sketch Entity** | **1-finger tap** on the geometry |
| **Zoom In / Out** | **2-finger pinch** in or out (zooms toward pinch center) |
| **Pan 3D Camera** | **2-finger drag** across the viewport |
| **Snap Camera to Standard Views (Top, Front, Right, Iso)** | Tap faces, edges, or corners of the **ViewCube** (top-right of viewport) |
| **Fit Model to Screen** | Tap **Fit** in the bottom navigation bar |
| **Context Menu / Secondary Actions** | **Long-press** (or S-Pen button click) on a body, face, feature in the timeline, or browser node |
| **Cancel Active Tool / Close Dialog** | Android **Back** gesture or tap **Cancel** |

*(You can also change navigation presets in **Preferences → Interface → Navigation** between OpenSCAD, Fusion, SolidWorks, Onshape, Blender, and FreeCAD styles.)*

---

## 3. Complete Feature & Function Reference

SolidCraft is a full parametric B-rep 3D CAD system built in Rust (`truck` geometry kernel + custom constraint solver + GPU compute tessellator). Every feature below is included in this APK:

### A. 2D & 3D Sketching (`SKETCH` Tab)
- **Create Geometry:**
  - **Lines:** Line (`sketch.line`), Midpoint Line, Centerline, Construction Line (`sketch.construction`), 3D Line (`sketch.line3d`), Infinite Line.
  - **Rectangles:** 2-Point Rectangle, 3-Point Rectangle, Center Rectangle, Rounded Rectangle.
  - **Circles & Ellipses:** Center-Diameter Circle, 2-Point Circle, 3-Point Circle, 2-Tangent Circle, 3-Tangent Circle, Ellipse.
  - **Arcs:** Center-Point Arc, 3-Point Arc, Tangent Arc.
  - **Polygons:** Circumscribed Polygon, Inscribed Polygon, Edge Polygon.
  - **Slots:** Center-to-Center Slot, Overall Slot, Center-Point Slot, 3-Point Arc Slot, Center-Point Arc Slot.
  - **Splines & Curves:** Fit-Point Spline (periodic C2 closed or open), Control-Point Spline (Degree 3 & Degree 5), 3D Spline, Conic Curve, Blend Curve, Intersection Curve, Isoparametric Curve, Spun Profile, Fit Curves to Mesh Section.
  - **Text & Points:** Parametric TrueType Font Text (`sketch.text` for engraving/embossing), 2D Point, 3D Point.
- **Modify Sketch:**
  - **Fillet & Chamfer:** Sketch Fillet, Equal Distance Chamfer, Two-Distance Chamfer, Distance-and-Angle Chamfer.
  - **Trim, Extend & Break:** Trim (`sketch.trim`), Trim Along Path, Extend, Break curve at intersections, Tie Loose Ends.
  - **Offset & Transform:** Associative Offset (`sketch.offset`), Break Offset Association, Move/Copy, Scale, Rectangular Pattern, Circular Pattern, Mirror.
  - **Project & Insert:** Project 3D edges (`sketch.project`), Auto-Project, Project to Surface, Intersect, Include 3D Geometry, Insert **DXF** (`sketch.insert_dxf`), Insert **SVG** (`sketch.insert_svg`), Export Sketch as **DXF** (`sketch.export_dxf`).
- **Geometric Constraints & Dimensions (`SKETCH › CONSTRAINTS`):**
  - **Constraints:** Coincident, Collinear, Concentric, Parallel, Perpendicular, Horizontal/Vertical, Tangent, Curvature (G2), Equal, Midpoint, Symmetry, Polygon, Fix/Unfix, AutoConstrain, AutoConstrain from Datum.
  - **Dimensions:** Linear, Aligned, Horizontal, Vertical, Point-to-Line, Line-to-Circle, Radius, Diameter, Angle, Arc Sweep, Arc Length, Slot Length, Driven/Driving toggle, and Parameter expressions (e.g. `width / 2 + 3 mm`).

---

### B. 3D Solid Modelling (`SOLID` Tab)
- **Create Solids:**
  - **Extrude** (`solid.extrude`): New body, join, cut, intersect; one-sided, two-sided, or symmetric; to distance, to face/object, or through all; with draft angle.
  - **Revolve** (`solid.revolve`): Full `360°`, one-sided, two-sided, or symmetric around any line/axis/edge.
  - **Sweep** (`solid.sweep`): Sweep profile along 2D/3D path with orientation & twist controls.
  - **Loft** (`solid.loft`): Smooth transitional solid between multiple profiles/rails.
  - **Primitives:** Box, Cylinder, Sphere, Torus, Coil (helical springs/threads), Pipe (tubular path), Rib, Web, Emboss, Boundary Fill, Bounding Solid.
  - **Hole & Thread Wizard:** Counterbore, countersink, simple, tapped, and clearance holes (`solid.hole`); cosmetic and modeled ISO/UTS threads (`solid.thread`).
  - **Patterns & Mirror:** Rectangular Pattern, Circular Pattern, Pattern on Path, Mirror bodies/features/faces.
  - **Structural Frames:** Frame Member generator (`frame.member`) with built-in structural profile library.
  - **Standard Parts & Fasteners:** Built-in Fastener & Standard Parts library (`parts.fastener`, `parts.library`, `parts.resize`).
- **Modify Solids:**
  - **Fillet & Chamfer:** Constant/variable-radius Edge/Face Fillet (`solid.fillet`), Equal/Two-Distance/Distance-Angle Chamfer (`solid.chamfer`).
  - **Shell & Draft:** Hollow out parts with constant/multi-thickness walls (`solid.shell`); mold release Draft angles (`solid.draft`).
  - **Direct Editing:** Press/Pull (`solid.press_pull`), Offset Face, Replace Face, Delete Face (with automatic healing), Split Body, Split Face, Combine (Boolean Join / Cut / Intersect), Move/Copy, Align, Scale, Remove.

---

### C. Surface Modelling (`SURFACE` Tab)
- **Create Surfaces:** Surface Extrude, Surface Revolve, Surface Sweep, Surface Loft, Patch (n-sided boundary fill), Boundary Surface, Blend Surface, Offset Surface, Ruled Surface, Thicken (convert surface to solid).
- **Modify Surfaces:** Trim Surface, Untrim, Extend Surface, Stitch surfaces into a solid B-rep (`surface.stitch`), Unstitch, Reverse Normal, Fair Surface, Match Surface.

---

### D. Sheet Metal & Plastic Enclosure Design (`SHEET METAL` & `PLASTIC` Tabs)
- **Sheet Metal:**
  - Convert Solid to Sheet Metal (`sheet.convert`), Flange (`sheet.flange`), Hem (`sheet.hem`), Fold, Unfold, Refold, Perforate.
  - **Flat Pattern & Laser/CNC Export:** Generate Flat Pattern (`sheet.flat_pattern`), Export Flat Pattern as exact line/arc **DXF** (`sheet.export_dxf`), Export Bend Table (`sheet.export_bend_table`), Manage Sheet Metal Rules (K-factor, thickness, bend radius).
- **Plastic Injection / 3D-Printed Enclosures:**
  - Mounting **Boss** (`plastic.boss`), interlocking **Lip / Groove** (`plastic.lip`), **Snap Fit** hooks (`plastic.snap_fit`), **Rest** features (`plastic.rest`), and configurable Plastic Rules (wall thickness, draft, knife-edge checks).

---

### E. Construction Geometry (`CONSTRUCT` Menu)
- **Planes:** Offset Plane, Midplane, Plane at Angle, Tangent Plane, Perpendicular Plane, Plane Along Path, Plane Through 3 Points, Plane Through 2 Edges.
- **Axes:** Axis Through Cylinder/Cone/Torus, Axis Perpendicular to Face, Axis Through Edge, Axis Through 2 Points, Axis Through 2 Planes.
- **Points:** Point at Vertex, Point at Center of Circle/Sphere/Torus, Point at Edge & Plane, Point Through 2 Edges, Point Through 3 Planes, Point Along Path.

---

### F. Assemblies, Joints, Motion Studies & BOM (`ASSEMBLE` Menu)
- **Components & Occurrences:** Multi-component assemblies, nested sub-assemblies, Insert External Design, Ground/Unground, Capture/Revert Position, Find Identical Copies, Paste New vs. Instance.
- **Mechanical Joints:** Rigid, Revolute, Slider, Cylindrical, Pin-Slot, Planar, Ball, **As-Built Joint**, **Gear** coupling, **Rack and Pinion**, **Screw** joint, **Cam** follower, **Motion Link**, Joint Limits, and interactive **Drive Joints**.
- **Motion Studies & Contact:** Keyframe Motion Study (`motion.study`), Play/Plot Motion, Motion Trace curves, Motion Interference check, Contact Sets (`contact.create`), and **Exploded Views** (`explode.create`).
- **Bill of Materials (BOM):** Interactive BOM table (`assembly.bom`) with part numbers, quantities, materials, and mass rollups.

---

### G. Parametric Timeline, Equations & Configurations
- **Editable History Timeline:** Roll back/forward the history marker, edit any earlier sketch or feature, reorder features, group into folders, suppress/unsuppress, or export the entire history as a JSON command script.
- **Parameters Dialog (`parameters.change`):** Define named variables with unit-aware formulas (`mm`, `cm`, `m`, `in`, `ft`, `deg`, `rad`, `pi`, trig functions), view the Parameter Dependency Graph, and import/export parameters.
- **Configuration Tables (`config.table`):** Build variant families of a part (different dimensions, suppression states, materials, and appearances per row) and switch configurations on the fly.

---

### H. Inspection, Measurement & Engineering Analysis (`INSPECT` Menu)
- **Measure (`inspect.measure`):** Exact point-to-point, edge length, face area, angle, minimum distance, radius/diameter, and bounding box.
- **Physical & Mass Properties:** Volume, surface area, mass (based on assigned physical material density), Center of Mass (`inspect.center_of_mass`), and moments of inertia.
- **CAD Quality & Mold Analysis:**
  - **Section Analysis** (`inspect.section`): Live cut-plane cross-section view.
  - **Interference & Clearance** (`inspect.interference`, `inspect.clearance`): Detect overlapping bodies in assemblies.
  - **Zebra Stripes, Curvature Map, Curvature Comb, Isocurves, Continuity & Environment Map:** Surface smoothness and G0/G1/G2 continuity checks.
  - **Draft, Minimum Radius & Accessibility Analysis:** DFM checks for 3D printing, CNC milling, and injection molding.
  - **Compare Bodies** (`inspect.compare`): Geometric diff between two versions of a part.

---

### I. Materials, Appearances & Studio Rendering (`RENDER` Tab)
- **Physical Materials (`material.assign`):** Assign metals, plastics (PLA, PETG, ABS, Nylon, Resin), woods, glass, or custom materials with density and mechanical properties.
- **Visual Appearances (`appearance.assign`):** PBR metallic/roughness/IOR materials, colors, and surface finishes.
- **Integrated Product Renderer (`render.start`):** Built-in studio lighting presets, HDR environments, backdrop/ground stage controls, camera framing, and PNG render export.
- **Reference Canvas & Decals:** Place reference images (`canvas.insert`), calibrate scale between two points (`canvas.calibrate`), and project decals onto faces (`canvas.decal`).

---

### J. Supported File Formats (Import & Export)

| Format | Extension(s) | Import | Export | Notes |
|---|---|---|---|---|
| **SolidCraft Native** | `.solidcraft`, `.json` | Yes | Yes | Full parametric feature timeline, sketches, parameters & configurations |
| **STEP (ISO 10303)** | `.step`, `.stp` | Yes (AP203/214/242) | Yes (AP242) | Exact B-rep solids, assemblies, names & colors |
| **IGES** | `.iges`, `.igs` | Yes | Yes (5.3) | Manifold B-rep solids & sewn trimmed surfaces |
| **3MF (3D Printing)** | `.3mf` | Yes | Yes | Multi-body packages ready for OrcaSlicer / Bambu Studio / PrusaSlicer |
| **STL (3D Printing)** | `.stl` | Yes | Yes (Binary/ASCII) | Watertight tessellated meshes with configurable chord/angle tolerance |
| **Wavefront OBJ** | `.obj` | Yes | Yes | Mesh geometry for rendering & 3D tools |
| **glTF 2.0 / GLB** | `.glb`, `.gltf` | Yes | — | Imports node hierarchy as components & materials as appearances |
| **FreeCAD & Rhino** | `.FCStd`, `.3dm` | Yes | — | Direct open-source CAD import via built-in `cadmpeg` |
| **2D DXF** | `.dxf` | Yes (Sketch) | Yes (Sketch, Sheet Metal Flat Pattern, Current 3D View) | Ready for Creality Falcon laser cutting & CNC routing |
| **2D SVG** | `.svg` | Yes (Sketch) | — | Import vector logos/outlines directly into sketches |
| **Robot URDF** | `.urdf` | — | Yes | Exports assemblies and joints for ROS / robotics simulation |

*(Tip: Imported closed constant-section meshes can also be reconstructed into editable B-rep solids using **Convert Mesh** `mesh.convert`.)*

---

## 4. Building the Android APK from Source

```bash
# Re-pack the APK from the compiled WASM bundle:
./packaging/android/build-apk.sh --skip-wasm

# Full rebuild (compiles Rust -> WASM + packages & signs APK):
./packaging/android/build-apk.sh
```

---

## 5. Credits & Original MIT License

### Credits
- **Original SolidCraft CAD Engine & UI:** Created by **[Tangency Labs](https://github.com/tangency-labs/solidcraft)**, **B. Herbruck ([`bherbruck`](https://github.com/bherbruck/solidcraft))**, **Karl Von Richter**, and the **SolidCraft Contributors**.
- **Android Port & Samsung Galaxy S26 Ultra Optimization (`v0.21.0-s26ultra-r2`):** Built and maintained by **Sanidhya Verma (@Zero70400)**.

### MIT License

```text
MIT License

Copyright (c) 2026 SolidCraft contributors
Copyright (c) 2026 Sanidhya Verma (Android / Samsung Galaxy S26 Ultra Port)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
