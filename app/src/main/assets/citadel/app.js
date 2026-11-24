(function () {
  var RESOURCE_DOT_COLORS = {
    FOOD: "#7bd959",
    TIMBER: "#b87a44",
    STONE: "#b4bec8",
    GOLD: "#ffd95c"
  };

  window.initCitadelDiorama = function () {
    var root = document.getElementById("gl-root");
    var overlayLayer = document.getElementById("overlay-layer");
    var loader = document.getElementById("loader");
    if (!root || !window.THREE) return;

    // 1. WebGL Renderer (soft shadows + filimic tone mapping for crisp miniature daylight look)
    var renderer = new THREE.WebGLRenderer({
      antialias: true,
      alpha: false,
      powerPreference: "high-performance"
    });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    renderer.setSize(window.innerWidth, window.innerHeight);
    renderer.shadowMap.enabled = true;
    renderer.shadowMap.type = THREE.PCFSoftShadowMap;
    renderer.outputEncoding = THREE.sRGBEncoding;
    renderer.toneMapping = THREE.ACESFilmicToneMapping;
    renderer.toneMappingExposure = 1.08;
    root.appendChild(renderer.domElement);

    // 2. Scene, Bright Overcast Alpine Sky & Misty Valley Fog
    var scene = new THREE.Scene();
    scene.background = new THREE.Color(0x9fc2de);
    scene.fog = new THREE.FogExp2(0xb8d0e2, 0.0085);

    // 3. Elevated Three-Quarter Tabletop Aerial Perspective Camera (Fixed Viewing Angle!)
    var camera = new THREE.PerspectiveCamera(
      38,
      window.innerWidth / Math.max(1, window.innerHeight),
      0.5,
      240
    );

    // Camera state: fixed tabletop three-quarter elevation angle, smooth drag-to-pan & pinch-to-zoom
    var DEFAULT_TARGET = new THREE.Vector3(1.2, 0.0, 1.0);
    var DEFAULT_ZOOM = 1.0;
    var MIN_ZOOM = 0.56;
    var MAX_ZOOM = 1.62;
    var BASE_OFFSET = new THREE.Vector3(-4.0, 38.0, 41.0);

    var target = DEFAULT_TARGET.clone();
    var desiredTarget = DEFAULT_TARGET.clone();
    var zoomScale = DEFAULT_ZOOM;
    var desiredZoom = DEFAULT_ZOOM;

    function clampTarget(v) {
      v.x = Math.max(-18.5, Math.min(20.5, v.x));
      v.z = Math.max(-18.5, Math.min(19.5, v.z));
      v.y = 0;
    }

    function applyCamera() {
      camera.position.set(
        target.x + BASE_OFFSET.x * zoomScale,
        BASE_OFFSET.y * zoomScale,
        target.z + BASE_OFFSET.z * zoomScale
      );
      camera.lookAt(target);
    }
    applyCamera();

    // 4. Bright Overcast Alpine Daylight Lighting (soft directional shadows + sky/grass hemisphere)
    var hemi = new THREE.HemisphereLight(0xe4f1fc, 0x5c7344, 0.96);
    hemi.position.set(0, 50, 0);
    scene.add(hemi);

    var sun = new THREE.DirectionalLight(0xfff9ee, 1.05);
    sun.position.set(-22, 46, 26);
    sun.castShadow = true;
    sun.shadow.mapSize.width = 1024;
    sun.shadow.mapSize.height = 1024;
    sun.shadow.camera.near = 10;
    sun.shadow.camera.far = 125;
    sun.shadow.camera.left = -36;
    sun.shadow.camera.right = 36;
    sun.shadow.camera.top = 36;
    sun.shadow.camera.bottom = -36;
    sun.shadow.bias = -0.0005;
    scene.add(sun);

    var rimFill = new THREE.DirectionalLight(0xbad6f0, 0.36);
    rimFill.position.set(28, 22, -24);
    scene.add(rimFill);

    // 5. Build Procedural Materials, Alpine Valley World & All 10 Medieval Buildings
    var mats = window.createDioramaMaterials();
    window.buildAlpineValleyWorld(scene, mats);
    var diorama = window.buildCitadelBuildings(scene, mats);
    var buildings = diorama.buildings;
    var pickables = diorama.pickables;
    var animRefs = diorama.animRefs;

    // 6. Rising Translucent Chimney Smoke Particle Pool
    var smokeParticles = [];
    var smokeGeo = new THREE.SphereGeometry(0.32, 7, 7);
    var smokeBaseMat = new THREE.MeshBasicMaterial({
      color: 0xe6e8ec,
      transparent: true,
      opacity: 0.48,
      depthWrite: false
    });
    animRefs.smokeOrigins.forEach(function (origin, oIdx) {
      for (var p = 0; p < 7; p++) {
        var sm = new THREE.Mesh(smokeGeo, smokeBaseMat.clone());
        scene.add(sm);
        smokeParticles.push({
          mesh: sm,
          origin: origin,
          phase: (p / 7) + oIdx * 0.37
        });
      }
    });

    // 7. Projected 2D DOM Badges (strictly textContent + classList — zero innerHTML!)
    var badgeNodes = {};
    var buildingKeys = Object.keys(buildings);

    buildingKeys.forEach(function (key) {
      var info = buildings[key];

      // Dark compact pill strictly BELOW building base
      var pill = document.createElement("div");
      pill.className = "building-pill";
      pill.dataset.buildingKey = key;

      var nameSpan = document.createElement("span");
      nameSpan.className = "pill-name";
      nameSpan.textContent = info.label;

      var levelSpan = document.createElement("span");
      levelSpan.className = "pill-level";
      levelSpan.textContent = "Lv.1";

      var upSpan = document.createElement("span");
      upSpan.className = "pill-up-arrow";
      upSpan.textContent = "\u2191";
      upSpan.style.display = "none";

      var buildSpan = document.createElement("span");
      buildSpan.className = "pill-build-icon";
      buildSpan.textContent = "\u2692";
      buildSpan.style.display = "none";

      pill.appendChild(nameSpan);
      pill.appendChild(levelSpan);
      pill.appendChild(upSpan);
      pill.appendChild(buildSpan);

      pill.addEventListener("click", function (ev) {
        ev.stopPropagation();
        notifyBuildingTapped(key);
      });
      overlayLayer.appendChild(pill);

      // Floating gold harvest bubble strictly ABOVE resource building roof peak
      var bubble = null;
      var bubbleText = null;
      if (info.resourceKey) {
        bubble = document.createElement("div");
        bubble.className = "harvest-bubble";
        bubble.style.display = "none";

        var dot = document.createElement("span");
        dot.className = "harvest-dot";
        dot.style.backgroundColor = RESOURCE_DOT_COLORS[info.resourceKey] || "#ffd95c";

        bubbleText = document.createElement("span");
        bubbleText.textContent = "+0";

        bubble.appendChild(dot);
        bubble.appendChild(bubbleText);
        bubble.addEventListener("click", function (ev) {
          ev.stopPropagation();
          notifyHarvestTapped(info.resourceKey);
        });
        overlayLayer.appendChild(bubble);
      }

      badgeNodes[key] = {
        pill: pill,
        nameSpan: nameSpan,
        levelSpan: levelSpan,
        upSpan: upSpan,
        buildSpan: buildSpan,
        bubble: bubble,
        bubbleText: bubbleText,
        hasBubble: false
      };
    });

    function notifyBuildingTapped(key) {
      if (window.CrownfallBridge && typeof window.CrownfallBridge.onBuildingTapped === "function") {
        window.CrownfallBridge.onBuildingTapped(String(key));
      }
    }

    function notifyHarvestTapped(resourceKey) {
      if (window.CrownfallBridge && typeof window.CrownfallBridge.onHarvestTapped === "function") {
        window.CrownfallBridge.onHarvestTapped(String(resourceKey));
      }
    }

    // 8. Touch & Pointer Gestures: 1-Finger Drag-to-Pan, 2-Finger Pinch-to-Zoom, Tap Raycasting
    var raycaster = new THREE.Raycaster();
    var pointerNdc = new THREE.Vector2();
    var activeTouches = new Map();
    var isDragging = false;
    var wasMultiTouch = false;
    var downX = 0;
    var downY = 0;
    var lastX = 0;
    var lastY = 0;
    var movedPx = 0;
    var lastPinchDist = 0;
    var velX = 0;
    var velZ = 0;

    function getTouchList() {
      var list = [];
      activeTouches.forEach(function (pt) { list.push(pt); });
      return list;
    }

    function pinchDistance(pts) {
      var dx = pts[0].x - pts[1].x;
      var dy = pts[0].y - pts[1].y;
      return Math.sqrt(dx * dx + dy * dy);
    }

    var domEl = renderer.domElement;

    domEl.addEventListener("pointerdown", function (e) {
      domEl.setPointerCapture(e.pointerId);
      activeTouches.set(e.pointerId, { x: e.clientX, y: e.clientY });
      velX = 0;
      velZ = 0;

      if (activeTouches.size === 1) {
        isDragging = true;
        wasMultiTouch = false;
        downX = lastX = e.clientX;
        downY = lastY = e.clientY;
        movedPx = 0;
      } else if (activeTouches.size === 2) {
        wasMultiTouch = true;
        lastPinchDist = pinchDistance(getTouchList());
      }
    });

    domEl.addEventListener("pointermove", function (e) {
      if (!activeTouches.has(e.pointerId)) return;
      activeTouches.set(e.pointerId, { x: e.clientX, y: e.clientY });

      if (activeTouches.size === 2) {
        var pts = getTouchList();
        var dist = pinchDistance(pts);
        if (lastPinchDist > 8 && dist > 8) {
          var ratio = lastPinchDist / dist;
          desiredZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, desiredZoom * ratio));
        }
        lastPinchDist = dist;
        return;
      }

      if (isDragging && activeTouches.size === 1 && !wasMultiTouch) {
        var dx = e.clientX - lastX;
        var dy = e.clientY - lastY;
        movedPx += Math.hypot(dx, dy);
        lastX = e.clientX;
        lastY = e.clientY;

        // Convert screen drag deltas into world XZ ground pan proportional to zoomScale
        var panFactor = 0.056 * zoomScale;
        var wx = -dx * panFactor;
        var wz = -dy * panFactor * 1.18;
        desiredTarget.x += wx;
        desiredTarget.z += wz;
        clampTarget(desiredTarget);
        velX = wx * 0.55;
        velZ = wz * 0.55;
      }
    });

    function onPointerEnd(e) {
      if (!activeTouches.has(e.pointerId)) return;
      activeTouches.delete(e.pointerId);

      if (activeTouches.size === 0) {
        if (!wasMultiTouch && movedPx < 14) {
          // Single clean tap -> Raycast against all 10 3D buildings
          var rect = domEl.getBoundingClientRect();
          pointerNdc.x = ((e.clientX - rect.left) / rect.width) * 2 - 1;
          pointerNdc.y = -((e.clientY - rect.top) / rect.height) * 2 + 1;
          raycaster.setFromCamera(pointerNdc, camera);
          var hits = raycaster.intersectObjects(pickables, false);
          if (hits.length > 0) {
            var bKey = hits[0].object.userData.buildingKey;
            if (bKey) notifyBuildingTapped(bKey);
          }
        }
        isDragging = false;
        lastPinchDist = 0;
      } else if (activeTouches.size === 1) {
        var rem = getTouchList()[0];
        lastX = rem.x;
        lastY = rem.y;
        lastPinchDist = 0;
      }
    }

    domEl.addEventListener("pointerup", onPointerEnd);
    domEl.addEventListener("pointercancel", onPointerEnd);

    // Optional wheel zoom for desktop/emulator mouse testing
    domEl.addEventListener("wheel", function (e) {
      e.preventDefault();
      var factor = e.deltaY > 0 ? 1.09 : 0.91;
      desiredZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, desiredZoom * factor));
    }, { passive: false });

    // 9. Public State & Control API Called From Kotlin (CitadelScreen.kt)
    var currentSelectedKey = "";
    var projVec = new THREE.Vector3();

    function projectToScreen(worldVec, w, h) {
      projVec.copy(worldVec).project(camera);
      return {
        x: (projVec.x * 0.5 + 0.5) * w,
        y: (-projVec.y * 0.5 + 0.5) * h,
        visible: projVec.z >= -1 && projVec.z <= 1
      };
    }

    window.CrownfallDiorama = {
      updateState: function (state) {
        if (!state || typeof state !== "object") return;
        currentSelectedKey = typeof state.selectedBuilding === "string" ? state.selectedBuilding : "";
        var bMap = state.buildings || {};

        buildingKeys.forEach(function (key) {
          var node = badgeNodes[key];
          var bInfo = buildings[key];
          var data = bMap[key];
          if (!node || !bInfo || !data) return;

          var isSelected = currentSelectedKey === key;
          var isUpgrading = Boolean(data.isUpgrading);
          var canUpgrade = Boolean(data.canUpgrade);

          node.pill.classList.toggle("selected", isSelected);
          node.pill.classList.toggle("upgrading", isUpgrading);
          node.pill.classList.toggle("upgradeable", !isUpgrading && canUpgrade);

          if (isUpgrading && typeof data.countdown === "string" && data.countdown.length > 0) {
            node.levelSpan.textContent = data.countdown;
            node.buildSpan.style.display = "inline";
            node.upSpan.style.display = "none";
          } else {
            node.levelSpan.textContent = "Lv." + (Number(data.level) || 1);
            node.buildSpan.style.display = "none";
            node.upSpan.style.display = canUpgrade ? "inline-flex" : "none";
          }

          if (bInfo.highlightRing && bInfo.highlightRing.material) {
            bInfo.highlightRing.material.opacity = isSelected ? 0.78 : (isUpgrading ? 0.42 : 0.0);
          }

          if (node.bubble && node.bubbleText) {
            var pending = Number(data.pendingAmount) || 0;
            if (pending >= 10 && typeof data.pendingFormatted === "string") {
              node.bubbleText.textContent = "+" + data.pendingFormatted;
              node.hasBubble = true;
            } else {
              node.hasBubble = false;
              node.bubble.style.display = "none";
            }
          }
        });
      },

      zoomIn: function () {
        desiredZoom = Math.max(MIN_ZOOM, desiredZoom * 0.82);
      },

      zoomOut: function () {
        desiredZoom = Math.min(MAX_ZOOM, desiredZoom * 1.22);
      },

      recenter: function () {
        desiredTarget.copy(DEFAULT_TARGET);
        desiredZoom = DEFAULT_ZOOM;
        velX = 0;
        velZ = 0;
      },

      showHarvestPopup: function (resourceKey, formattedText) {
        var matchedKey = null;
        for (var i = 0; i < buildingKeys.length; i++) {
          if (buildings[buildingKeys[i]].resourceKey === resourceKey) {
            matchedKey = buildingKeys[i];
            break;
          }
        }
        if (!matchedKey) return;
        var pos = projectToScreen(buildings[matchedKey].peakAnchor, window.innerWidth, window.innerHeight);
        if (!pos.visible) return;

        var pop = document.createElement("div");
        pop.className = "harvest-popup";
        pop.textContent = String(formattedText || "+0");
        pop.style.left = pos.x.toFixed(1) + "px";
        pop.style.top = (pos.y - 16).toFixed(1) + "px";
        overlayLayer.appendChild(pop);
        setTimeout(function () {
          if (pop.parentNode) pop.parentNode.removeChild(pop);
        }, 1280);
      }
    };

    // 10. Handle Resize cleanly across foldables/tablets/orientation changes
    window.addEventListener("resize", function () {
      var w = window.innerWidth;
      var h = Math.max(1, window.innerHeight);
      camera.aspect = w / h;
      camera.updateProjectionMatrix();
      renderer.setSize(w, h);
    });

    // 11. Main 60fps Animation Loop (River Flow, Watermill, Chimney Smoke, Banners, Patrolling Guards, 2D Badges)
    var clock = new THREE.Clock();
    var firstFrameDone = false;

    function animate() {
      requestAnimationFrame(animate);
      var dt = Math.min(clock.getDelta(), 0.05);
      var elapsed = clock.elapsedTime;

      // Inertial glide after drag release
      if (!isDragging && (Math.abs(velX) > 0.001 || Math.abs(velZ) > 0.001)) {
        desiredTarget.x += velX;
        desiredTarget.z += velZ;
        clampTarget(desiredTarget);
        velX *= 0.90;
        velZ *= 0.90;
      }

      // Smooth damped camera tracking
      target.lerp(desiredTarget, Math.min(1, dt * 11));
      zoomScale += (desiredZoom - zoomScale) * Math.min(1, dt * 10);
      applyCamera();

      // Animate cerulean river water flowing downstream
      if (mats.riverTexture) {
        mats.riverTexture.offset.y -= dt * 0.42;
      }

      // Spin Farmstead wooden watermill wheel continuously
      if (animRefs.watermillWheel) {
        animRefs.watermillWheel.rotation.x -= dt * 1.45;
      }

      // Shimmer circular courtyard fountain water jet
      if (animRefs.fountainJet) {
        var pulse = 0.88 + Math.sin(elapsed * 6.5) * 0.14;
        animRefs.fountainJet.scale.set(1.0, pulse, 1.0);
        animRefs.fountainJet.rotation.y += dt * 1.8;
      }

      // Rising translucent chimney smoke puffs
      for (var s = 0; s < smokeParticles.length; s++) {
        var sp = smokeParticles[s];
        var cycle = (elapsed * 0.28 + sp.phase) % 1.0;
        sp.mesh.position.set(
          sp.origin.x + Math.sin(elapsed * 1.1 + sp.phase * 6.2) * (0.25 + cycle * 0.7),
          sp.origin.y + cycle * 4.4,
          sp.origin.z + cycle * 0.65
        );
        var sc = 0.55 + cycle * 2.1;
        sp.mesh.scale.set(sc, sc * 0.85, sc);
        sp.mesh.material.opacity = (1.0 - cycle) * 0.44;
      }

      // Gentle breeze banner sway
      for (var b = 0; b < animRefs.banners.length; b++) {
        var bn = animRefs.banners[b];
        bn.rotation.y = Math.sin(elapsed * 2.6 + (bn.userData.phase || 0)) * 0.24;
      }

      // Miniature armored guards patrolling wall battlements & eastern stone bridge
      for (var g = 0; g < animRefs.guards.length; g++) {
        var gd = animRefs.guards[g];
        var rawT = (elapsed * gd.speed + gd.offset) % 2.0;
        var forward = rawT <= 1.0;
        var t = forward ? rawT : (2.0 - rawT);
        gd.mesh.position.lerpVectors(gd.a, gd.b, t);
        gd.mesh.position.y += Math.abs(Math.sin(elapsed * 8.0 + g)) * 0.04;
        var targetPt = forward ? gd.b : gd.a;
        gd.mesh.lookAt(targetPt.x, gd.mesh.position.y, targetPt.z);
      }

      // Render 3D diorama
      renderer.render(scene, camera);

      // Synchronize non-overlapping 2D projected pills (below base) & gold collect bubbles (above peak)
      var w = window.innerWidth;
      var h = window.innerHeight;
      for (var k = 0; k < buildingKeys.length; k++) {
        var key = buildingKeys[k];
        var info = buildings[key];
        var node = badgeNodes[key];

        var baseScreen = projectToScreen(info.baseAnchor, w, h);
        var baseInBounds = baseScreen.visible &&
          baseScreen.x > -90 && baseScreen.x < w + 90 &&
          baseScreen.y > -40 && baseScreen.y < h + 40;

        if (baseInBounds) {
          node.pill.style.display = "inline-flex";
          node.pill.style.left = baseScreen.x.toFixed(1) + "px";
          node.pill.style.top = (baseScreen.y + 4).toFixed(1) + "px";
        } else {
          node.pill.style.display = "none";
        }

        if (node.bubble && node.hasBubble) {
          var peakScreen = projectToScreen(info.peakAnchor, w, h);
          var peakInBounds = peakScreen.visible &&
            peakScreen.x > -70 && peakScreen.x < w + 70 &&
            peakScreen.y > -40 && peakScreen.y < h + 40;
          if (peakInBounds) {
            node.bubble.style.display = "inline-flex";
            node.bubble.style.left = peakScreen.x.toFixed(1) + "px";
            node.bubble.style.top = (peakScreen.y - 6).toFixed(1) + "px";
          } else {
            node.bubble.style.display = "none";
          }
        }
      }

      if (!firstFrameDone) {
        firstFrameDone = true;
        if (loader) loader.classList.add("hidden");
        if (window.CrownfallBridge && typeof window.CrownfallBridge.onDioramaReady === "function") {
          window.CrownfallBridge.onDioramaReady();
        }
      }
    }

    animate();
  };
})();
