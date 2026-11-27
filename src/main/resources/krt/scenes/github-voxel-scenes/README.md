# GitHub Voxel Scenes

Three self-contained low-poly/voxel scenes designed as README hero visuals. Every scene is made from simple reviewable geometry, uses flat MTL fallback colors, and has no external textures or assets.

## Contents

| Folder | Concept | Main files |
|---|---|---|
| `01-notification-avalanche/` | Office worker caught by a wave of notifications | OBJ, MTL, `scene-layout.json`, `render-showcase.json` |
| `02-laptop-construction/` | Tiny workers repair a giant laptop | OBJ, MTL, `scene-layout.json`, `render-showcase.json` |
| `03-deploy-button/` | Developer reaches toward the red DEPLOY button | OBJ, MTL, `scene-layout.json`, `render-showcase.json` |

The OBJ files are complete scenes, not just loose props. Objects retain descriptive names such as `worker_head`, `notification_07_card`, `giant_screen_display`, and `deploy_button`, so parts remain selectable after import. `scene-layout.json` is the machine-readable source for positions, rotations, dimensions, fallback materials, suggested camera, and lights. Smooth spheres and cylinders include `vn` vertex normals.

`render-showcase.json` is the target look-development contract. It binds object name patterns to physical materials, volumes, and sampled lights. MTL remains a deliberately simple fallback for ordinary OBJ viewers; it cannot express spectral dispersion, volumes, thin-film interference, SSS, sheen, procedural bump, or Kelvin emission.

## Effect coverage

| Renderer capability | Scene 1: notifications | Scene 2: laptop crew | Scene 3: DEPLOY |
|---|:---:|:---:|:---:|
| Spectral dispersion | Prism + glass cards | — | Safety glass |
| Rough glass (GGX) | Notification cards | — | Button cover |
| Anisotropic metal (GGX) | Laptop shell | Giant laptop | Server racks |
| Fog / god rays | Window blinds + haze | — | Neon and practical lights in haze |
| Subsurface scattering | Skin | Skin + jade | Skin + red resin button |
| Thin film | — | Two soap bubbles + oil puddle | — |
| Fabric sheen | Worker sweater | Crew workwear | Developer and observers |
| Procedural texture / bump | Wood desk | Rubber keys | Marble runway |
| Textured / Kelvin emission | Screen, badges, warm lamp | Grid screen, work lamps | Pixel neon, LEDs, warning bulbs |
| Smooth normals | Lamp diffuser, cup | Bubbles, puddle, jade | Round button and bulbs |
| Explicit light sampling | Small badges and lamps | Work lights and screen | LEDs, runway lights, neon and bulbs |

## Blender import

1. Choose **File → Import → Wavefront (.obj)** and select the OBJ inside a scene folder.
2. Keep **Forward: -Z Forward** and **Up: Y Up** if your Blender importer exposes those legacy options; otherwise use the defaults and rotate the imported collection only if needed. The source data itself is right-handed and Z-up.
3. The matching MTL is loaded automatically when it stays beside the OBJ.
4. Add an Area light and camera using the suggestions in `scene-layout.json`, or frame the scene freely.
5. For the intended graphic look, use Cycles for spectral/volume look development or Eevee for a quick composition preview. Preserve imported custom normals, use a 35–55 mm camera, and select AgX medium-high contrast.

If Blender imports each named box as a separate object, select the collection and use **Object → Convert → Mesh** only when you need a single editable mesh. Keeping objects separate makes recoloring and animation easier.

## Three.js import

Load the MTL first, then pass its materials to `OBJLoader`:

```js
import * as THREE from 'three';
import { MTLLoader } from 'three/addons/loaders/MTLLoader.js';
import { OBJLoader } from 'three/addons/loaders/OBJLoader.js';

const base = '/scenes/03-deploy-button/';
new MTLLoader().setPath(base).load('03-deploy-button.mtl', (materials) => {
  materials.preload();
  new OBJLoader()
    .setMaterials(materials)
    .setPath(base)
    .load('03-deploy-button.obj', (sceneRoot) => {
      sceneRoot.rotation.x = -Math.PI / 2; // convert source Z-up to Three.js Y-up
      scene.add(sceneRoot);
    });
});
```

Add a `HemisphereLight` plus one or two `DirectionalLight`s for an inexpensive README/demo render. Preserve imported vertex normals on curved parts; use flat shading only for intentionally voxel-shaped boxes.

## Layout schema

Each `scene-layout.json` contains:

- `coordinate_system` and `unit` conventions;
- suggested camera and two-light setup;
- a flat `objects` list with stable names;
- each object's `role`, primitive, position, rotation, scale, and material.

Transforms are already baked into each OBJ. The JSON is intended for rebuilding, procedural animation, hit targets, or swapping simple parts in another engine; do not apply its transforms a second time to the imported OBJ.

## KRT Showcase integration

`render-showcase.json` deliberately mirrors KRT concepts where they already have stable names: `Dielectric`, `Conductor`, `Fog`, `SoapBubble`, `Iridescent`, and `Emissive`. Upcoming concepts use descriptive target names such as `Subsurface` and `Sheen`. Treat the file as a data contract rather than something the current OBJ importer consumes automatically.

Recommended integration order:

1. Load the OBJ while preserving object/group names, MTL assignments, and `vn` normals.
2. Resolve each `bindings[].objects` glob against those names; later rules may override earlier ones if you add project-specific bindings.
3. Construct physical materials from `materials`, then add `volumes` independently of surface geometry.
4. Register every emitter matched by `light_sampling.include` with next-event estimation. Wildcards are intentional because pixel letters, LEDs, and notification badges are separate small meshes.
5. Keep the MTL material only when no physical binding matches.

The three OBJ files use many materials at once, while KRT's current simple OBJ loading path may assign one material to a whole imported model. Full Showcase use therefore requires group-aware material binding; loading the same OBJ multiple times with one material is not equivalent because it duplicates all geometry.

## GitHub-friendly rendering

OBJ cannot render directly in a GitHub README. Render a 16:9 PNG/WebP or a short looping GIF in Blender/Three.js, commit that preview, then link the image to an interactive demo if desired. The source files stay reviewable text and use no binary textures.

## License

The generated geometry and scene layouts are provided as project assets. Adapt, recolor, animate, and redistribute them with the project.
