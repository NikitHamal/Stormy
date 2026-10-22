---
name: three-d
description: Three.js from a CDN: PBR lighting, controls, models, post-processing, performance
---
# Three.js without a build
```html
<script type="importmap">{"imports":{"three":"https://cdn.jsdelivr.net/npm/three@0.170/build/three.module.js","three/addons/":"https://cdn.jsdelivr.net/npm/three@0.170/examples/jsm/"}}</script>
<script type="module">
import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { RoomEnvironment } from 'three/addons/environments/RoomEnvironment.js';
const renderer = new THREE.WebGLRenderer({ antialias:true, alpha:true });
renderer.setPixelRatio(Math.min(devicePixelRatio, 2));
renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.outputColorSpace = THREE.SRGBColorSpace;
renderer.shadowMap.enabled = true; renderer.shadowMap.type = THREE.PCFSoftShadowMap;
const scene = new THREE.Scene();
scene.environment = new THREE.PMREMGenerator(renderer).fromScene(new RoomEnvironment()).texture;
const camera = new THREE.PerspectiveCamera(45, 1, .1, 100); camera.position.set(3,2,5);
const controls = new OrbitControls(camera, renderer.domElement); controls.enableDamping = true;
</script>
```
- Lighting: environment map (PBR looks right instantly) + one shadow-casting key light + soft fill.
- Materials: `MeshStandardMaterial`/`MeshPhysicalMaterial` (clearcoat, transmission for glass).
- Resize with a ResizeObserver on the container; render loop via `renderer.setAnimationLoop`.
- Post-processing (bloom, SMAA) via `three/addons/postprocessing/*`, only if needed.
- Models: GLTFLoader + DRACOLoader; show a progress loader; center & scale to fit.
- Performance: cap DPR at 2, reuse geometries/materials, dispose on teardown, pause when hidden.
- Always provide a non-WebGL fallback message and keep text/UI in HTML overlays.
