package com.codex.stormy.data.repository.templates

/**
 * Phaser 3 game template files
 */
object PhaserTemplates {
    val HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Phaser Game</title>
    <link rel="stylesheet" href="style.css">
    <script src="https://cdn.jsdelivr.net/npm/phaser@3.70.0/dist/phaser.min.js"></script>
</head>
<body>
    <div id="game-container"></div>
    <script type="module" src="src/game.js"></script>
</body>
</html>
    """.trimIndent()

    val CSS = """
* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

body {
    display: flex;
    justify-content: center;
    align-items: center;
    min-height: 100vh;
    background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
    overflow: hidden;
}

#game-container {
    border-radius: 8px;
    overflow: hidden;
    box-shadow: 0 10px 40px rgba(0, 0, 0, 0.5);
}

canvas {
    display: block;
}
    """.trimIndent()

    val GAME_JS = """
import MainScene from './scenes/MainScene.js';

const config = {
    type: Phaser.AUTO,
    width: 800,
    height: 600,
    parent: 'game-container',
    backgroundColor: '#2d2d44',
    physics: {
        default: 'arcade',
        arcade: {
            gravity: { y: 300 },
            debug: false
        }
    },
    scene: [MainScene]
};

const game = new Phaser.Game(config);
    """.trimIndent()

    val MAIN_SCENE = """
export default class MainScene extends Phaser.Scene {
    constructor() {
        super('MainScene');
        this.score = 0;
        this.gameOver = false;
    }

    preload() {
        // Create simple colored rectangles as placeholders
        this.createPlaceholderGraphics();
    }

    createPlaceholderGraphics() {
        // Create a simple player graphic
        const playerGraphics = this.make.graphics({ x: 0, y: 0, add: false });
        playerGraphics.fillStyle(0x00ff88);
        playerGraphics.fillRoundedRect(0, 0, 48, 48, 8);
        playerGraphics.generateTexture('player', 48, 48);

        // Create platform graphic
        const platformGraphics = this.make.graphics({ x: 0, y: 0, add: false });
        platformGraphics.fillStyle(0x6366f1);
        platformGraphics.fillRoundedRect(0, 0, 400, 32, 4);
        platformGraphics.generateTexture('platform', 400, 32);

        // Create small platform
        const smallPlatformGraphics = this.make.graphics({ x: 0, y: 0, add: false });
        smallPlatformGraphics.fillStyle(0x6366f1);
        smallPlatformGraphics.fillRoundedRect(0, 0, 200, 32, 4);
        smallPlatformGraphics.generateTexture('platformSmall', 200, 32);

        // Create collectible star
        const starGraphics = this.make.graphics({ x: 0, y: 0, add: false });
        starGraphics.fillStyle(0xffd700);
        starGraphics.fillCircle(12, 12, 12);
        starGraphics.generateTexture('star', 24, 24);

        // Create enemy
        const enemyGraphics = this.make.graphics({ x: 0, y: 0, add: false });
        enemyGraphics.fillStyle(0xff4444);
        enemyGraphics.fillCircle(16, 16, 16);
        enemyGraphics.generateTexture('enemy', 32, 32);
    }

    create() {
        // Reset game state
        this.score = 0;
        this.gameOver = false;

        // Create platforms
        this.platforms = this.physics.add.staticGroup();
        this.platforms.create(400, 568, 'platform');
        this.platforms.create(600, 400, 'platformSmall');
        this.platforms.create(50, 250, 'platformSmall');
        this.platforms.create(750, 220, 'platformSmall');

        // Create player
        this.player = this.physics.add.sprite(100, 450, 'player');
        this.player.setBounce(0.2);
        this.player.setCollideWorldBounds(true);

        // Create collectibles
        this.stars = this.physics.add.group({
            key: 'star',
            repeat: 11,
            setXY: { x: 12, y: 0, stepX: 70 }
        });

        this.stars.children.iterate((child) => {
            child.setBounceY(Phaser.Math.FloatBetween(0.4, 0.8));
        });

        // Create enemies
        this.enemies = this.physics.add.group();

        // Add colliders
        this.physics.add.collider(this.player, this.platforms);
        this.physics.add.collider(this.stars, this.platforms);
        this.physics.add.collider(this.enemies, this.platforms);

        // Add overlaps
        this.physics.add.overlap(this.player, this.stars, this.collectStar, null, this);
        this.physics.add.collider(this.player, this.enemies, this.hitEnemy, null, this);

        // Create cursors
        this.cursors = this.input.keyboard.createCursorKeys();

        // Add touch/click controls for mobile
        this.input.on('pointerdown', (pointer) => {
            if (pointer.x < this.scale.width / 3) {
                this.moveLeft = true;
            } else if (pointer.x > (this.scale.width * 2) / 3) {
                this.moveRight = true;
            } else {
                this.jump = true;
            }
        });

        this.input.on('pointerup', () => {
            this.moveLeft = false;
            this.moveRight = false;
            this.jump = false;
        });

        // Create score text
        this.scoreText = this.add.text(16, 16, 'Score: 0', {
            fontSize: '32px',
            fill: '#fff',
            fontFamily: 'Arial'
        });

        // Instructions text
        this.add.text(16, 560, 'Arrow keys to move, Up to jump', {
            fontSize: '16px',
            fill: '#888',
            fontFamily: 'Arial'
        });
    }

    update() {
        if (this.gameOver) {
            return;
        }

        // Handle input
        if (this.cursors.left.isDown || this.moveLeft) {
            this.player.setVelocityX(-160);
        } else if (this.cursors.right.isDown || this.moveRight) {
            this.player.setVelocityX(160);
        } else {
            this.player.setVelocityX(0);
        }

        if ((this.cursors.up.isDown || this.jump) && this.player.body.touching.down) {
            this.player.setVelocityY(-330);
        }
    }

    collectStar(player, star) {
        star.disableBody(true, true);
        this.score += 10;
        this.scoreText.setText('Score: ' + this.score);

        // Check if all stars collected
        if (this.stars.countActive(true) === 0) {
            // Respawn stars
            this.stars.children.iterate((child) => {
                child.enableBody(true, child.x, 0, true, true);
            });

            // Spawn an enemy
            const x = (player.x < 400) ? Phaser.Math.Between(400, 800) : Phaser.Math.Between(0, 400);
            const enemy = this.enemies.create(x, 16, 'enemy');
            enemy.setBounce(1);
            enemy.setCollideWorldBounds(true);
            enemy.setVelocity(Phaser.Math.Between(-200, 200), 20);
        }
    }

    hitEnemy(player, enemy) {
        this.physics.pause();
        player.setTint(0xff0000);
        this.gameOver = true;

        // Show game over text
        this.add.text(400, 300, 'GAME OVER', {
            fontSize: '64px',
            fill: '#ff4444',
            fontFamily: 'Arial'
        }).setOrigin(0.5);

        this.add.text(400, 380, 'Click to restart', {
            fontSize: '24px',
            fill: '#fff',
            fontFamily: 'Arial'
        }).setOrigin(0.5);

        // Restart on click
        this.input.once('pointerdown', () => {
            this.scene.restart();
        });
    }
}
    """.trimIndent()
}

/**
 * Three.js 3D graphics template files
 */
object ThreeJsTemplates {
    val HTML = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Three.js Scene</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <div id="container"></div>
    <div id="info">
        <h1>Three.js Demo</h1>
        <p>Drag to rotate • Scroll to zoom</p>
    </div>
    <script type="importmap">
    {
        "imports": {
            "three": "https://unpkg.com/three@0.160.0/build/three.module.js",
            "three/addons/": "https://unpkg.com/three@0.160.0/examples/jsm/"
        }
    }
    </script>
    <script type="module" src="main.js"></script>
</body>
</html>
    """.trimIndent()

    val CSS = """
* {
    margin: 0;
    padding: 0;
    box-sizing: border-box;
}

body {
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
    overflow: hidden;
}

#container {
    width: 100vw;
    height: 100vh;
}

#info {
    position: absolute;
    top: 20px;
    left: 0;
    right: 0;
    text-align: center;
    color: white;
    pointer-events: none;
    z-index: 100;
}

#info h1 {
    font-size: 2rem;
    margin-bottom: 0.5rem;
    text-shadow: 0 2px 10px rgba(0, 0, 0, 0.5);
}

#info p {
    font-size: 1rem;
    opacity: 0.8;
    text-shadow: 0 2px 10px rgba(0, 0, 0, 0.5);
}

canvas {
    display: block;
}
    """.trimIndent()

    val MAIN_JS = """
import * as THREE from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';

// Scene setup
const scene = new THREE.Scene();
scene.background = new THREE.Color(0x1a1a2e);

// Camera
const camera = new THREE.PerspectiveCamera(
    75,
    window.innerWidth / window.innerHeight,
    0.1,
    1000
);
camera.position.z = 5;

// Renderer
const renderer = new THREE.WebGLRenderer({ antialias: true });
renderer.setSize(window.innerWidth, window.innerHeight);
renderer.setPixelRatio(window.devicePixelRatio);
renderer.shadowMap.enabled = true;
document.getElementById('container').appendChild(renderer.domElement);

// Controls
const controls = new OrbitControls(camera, renderer.domElement);
controls.enableDamping = true;
controls.dampingFactor = 0.05;

// Lighting
const ambientLight = new THREE.AmbientLight(0x404040, 0.5);
scene.add(ambientLight);

const directionalLight = new THREE.DirectionalLight(0xffffff, 1);
directionalLight.position.set(5, 5, 5);
directionalLight.castShadow = true;
scene.add(directionalLight);

const pointLight = new THREE.PointLight(0x6366f1, 1, 100);
pointLight.position.set(2, 3, 2);
scene.add(pointLight);

// Create main cube
const geometry = new THREE.BoxGeometry(1.5, 1.5, 1.5);
const material = new THREE.MeshStandardMaterial({
    color: 0x6366f1,
    metalness: 0.3,
    roughness: 0.4,
});
const cube = new THREE.Mesh(geometry, material);
cube.castShadow = true;
scene.add(cube);

// Create torus
const torusGeometry = new THREE.TorusGeometry(0.8, 0.3, 16, 100);
const torusMaterial = new THREE.MeshStandardMaterial({
    color: 0x22c55e,
    metalness: 0.5,
    roughness: 0.3,
});
const torus = new THREE.Mesh(torusGeometry, torusMaterial);
torus.position.x = 3;
torus.castShadow = true;
scene.add(torus);

// Create sphere
const sphereGeometry = new THREE.SphereGeometry(0.7, 32, 32);
const sphereMaterial = new THREE.MeshStandardMaterial({
    color: 0xf59e0b,
    metalness: 0.4,
    roughness: 0.2,
});
const sphere = new THREE.Mesh(sphereGeometry, sphereMaterial);
sphere.position.x = -3;
sphere.castShadow = true;
scene.add(sphere);

// Create floor
const floorGeometry = new THREE.PlaneGeometry(20, 20);
const floorMaterial = new THREE.MeshStandardMaterial({
    color: 0x2d2d44,
    side: THREE.DoubleSide,
});
const floor = new THREE.Mesh(floorGeometry, floorMaterial);
floor.rotation.x = -Math.PI / 2;
floor.position.y = -2;
floor.receiveShadow = true;
scene.add(floor);

// Add particle system
const particlesGeometry = new THREE.BufferGeometry();
const particleCount = 1000;
const posArray = new Float32Array(particleCount * 3);

for (let i = 0; i < particleCount * 3; i++) {
    posArray[i] = (Math.random() - 0.5) * 20;
}

particlesGeometry.setAttribute('position', new THREE.BufferAttribute(posArray, 3));

const particlesMaterial = new THREE.PointsMaterial({
    size: 0.02,
    color: 0x818cf8,
    transparent: true,
    opacity: 0.8,
});

const particles = new THREE.Points(particlesGeometry, particlesMaterial);
scene.add(particles);

// Animation loop
function animate() {
    requestAnimationFrame(animate);

    // Rotate objects
    cube.rotation.x += 0.005;
    cube.rotation.y += 0.01;

    torus.rotation.x += 0.01;
    torus.rotation.y += 0.005;

    sphere.rotation.y += 0.008;

    // Float animation
    cube.position.y = Math.sin(Date.now() * 0.001) * 0.3;
    sphere.position.y = Math.cos(Date.now() * 0.001) * 0.3;

    // Rotate particles slowly
    particles.rotation.y += 0.0005;

    controls.update();
    renderer.render(scene, camera);
}

// Handle window resize
window.addEventListener('resize', () => {
    camera.aspect = window.innerWidth / window.innerHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(window.innerWidth, window.innerHeight);
});

animate();
    """.trimIndent()
}
