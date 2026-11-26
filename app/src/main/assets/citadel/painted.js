/*
 * Crownfall painted citadel.
 *
 * A hand-painted background (assets/painted/bg.jpg, 1024x1024) with building
 * pictures placed on top. Buildings that have their own pictures swap them as they
 * level up (4 stages); buildings without pictures yet use a tap zone on the painted
 * background. Speaks the same API as the old 3D diorama so the Kotlin side is unchanged:
 *   window.CrownfallDiorama.updateState / zoomIn / zoomOut / recenter / showHarvestPopup
 *   window.CrownfallBridge.onBuildingTapped / onHarvestTapped / onDioramaReady
 */
(function () {
  "use strict";

  var ASSET = "https://citadel.crownfall.local/assets/painted/";
  var W = 1024, H = 1024;

  // World coordinates are background pixels. `x, y` is the ground point under the
  // building (bottom-centre of its picture); `w` is the picture width per stage.
  var BUILDINGS = {
    KEEP: { label: "Obsidian Keep", x: 395, y: 505, sprites: ["keep_1.webp", "keep_2.webp", "keep_3.webp", "keep_4.webp"], w: [220, 236, 246, 252] },
    ALLIANCE_HALL: { label: "Alliance Hall", x: 700, y: 330, r: 110 },
    BARRACKS: { label: "War Barracks", x: 655, y: 545, r: 70 },
    ARCHERY: { label: "Ashbow Range", x: 178, y: 400, r: 60 },
    SIEGE: { label: "Siege Foundry", x: 455, y: 680, r: 45 },
    STABLES: { label: "Dread Stables", x: 795, y: 670, r: 50 },
    FARM: { label: "Farmstead", x: 730, y: 790, r: 55, resource: "FOOD" },
    LUMBER: { label: "Timber Mill", x: 470, y: 980, r: 60, resource: "TIMBER" },
    QUARRY: { label: "Quarry", x: 255, y: 860, r: 55, resource: "STONE" },
    MINT: { label: "Gold Mint", x: 945, y: 600, r: 45, resource: "GOLD" }
  };
  var KEYS = Object.keys(BUILDINGS);
  var DOT = { FOOD: "#e8b04a", TIMBER: "#6fae5c", STONE: "#b8b6ae", GOLD: "#ffd95c" };

  var viewport = document.getElementById("viewport");
  var world = document.getElementById("world");
  var bg = document.getElementById("bg");
  var spriteLayer = document.getElementById("sprites");
  var fxLayer = document.getElementById("fx");
  var labelLayer = document.getElementById("labels");
  var loader = document.getElementById("loader");

  world.style.width = W + "px";
  world.style.height = H + "px";
  bg.style.width = W + "px";
  bg.style.height = H + "px";

  // ------------------------------------------------------------------ bridge

  var dragged = false;
  function tapBuilding(key) {
    if (dragged) return;
    if (window.CrownfallBridge && typeof window.CrownfallBridge.onBuildingTapped === "function") {
      window.CrownfallBridge.onBuildingTapped(String(key));
    }
  }
  function tapHarvest(res) {
    if (dragged) return;
    if (window.CrownfallBridge && typeof window.CrownfallBridge.onHarvestTapped === "function") {
      window.CrownfallBridge.onHarvestTapped(String(res));
    }
  }

  // ------------------------------------------------------------------ buildings

  var nodes = {};
  var scaledEls = []; // labels that keep a constant on-screen size

  function anchorAt(x, y) {
    var a = document.createElement("div");
    a.className = "anchor";
    a.style.left = x + "px";
    a.style.top = y + "px";
    labelLayer.appendChild(a);
    return a;
  }

  KEYS.forEach(function (key) {
    var b = BUILDINGS[key];
    var node = { key: key, stage: 0, sprite: null, img: null, hit: null };

    if (b.sprites) {
      var sp = document.createElement("div");
      sp.className = "sprite";
      var img = document.createElement("img");
      img.alt = "";
      img.draggable = false;
      sp.appendChild(img);
      sp.addEventListener("click", function (ev) { ev.stopPropagation(); tapBuilding(key); });
      spriteLayer.appendChild(sp);
      node.sprite = sp;
      node.img = img;
    } else {
      var hit = document.createElement("div");
      hit.className = "hit";
      hit.style.left = (b.x - b.r) + "px";
      hit.style.top = (b.y - b.r * 1.3) + "px";
      hit.style.width = (b.r * 2) + "px";
      hit.style.height = (b.r * 1.6) + "px";
      hit.addEventListener("click", function (ev) { ev.stopPropagation(); tapBuilding(key); });
      spriteLayer.appendChild(hit);
      node.hit = hit;
    }

    // name pill under the building
    var pa = anchorAt(b.x, b.y + 4);
    var pill = document.createElement("div");
    pill.className = "building-pill";
    var nm = document.createElement("span"); nm.className = "pill-name"; nm.textContent = b.label;
    var lv = document.createElement("span"); lv.className = "pill-level"; lv.textContent = "Lv.1";
    var up = document.createElement("span"); up.className = "pill-up-arrow"; up.textContent = "↑"; up.style.display = "none";
    var bd = document.createElement("span"); bd.className = "pill-build-icon"; bd.textContent = "⚒"; bd.style.display = "none";
    pill.appendChild(nm); pill.appendChild(lv); pill.appendChild(up); pill.appendChild(bd);
    pill.addEventListener("click", function (ev) { ev.stopPropagation(); tapBuilding(key); });
    pa.appendChild(pill);
    scaledEls.push({ el: pill, base: "translate(-50%, 0)", origin: "50% 0" });
    node.pill = pill; node.levelSpan = lv; node.upSpan = up; node.buildSpan = bd;

    if (b.resource) {
      var ba = anchorAt(b.x, b.y - (b.r || 60) * 1.2);
      var bubble = document.createElement("div");
      bubble.className = "harvest-bubble";
      bubble.style.display = "none";
      var dot = document.createElement("span"); dot.className = "harvest-dot"; dot.style.backgroundColor = DOT[b.resource] || "#ffd95c";
      var bt = document.createElement("span"); bt.textContent = "+0";
      bubble.appendChild(dot); bubble.appendChild(bt);
      bubble.addEventListener("click", function (ev) { ev.stopPropagation(); tapHarvest(b.resource); });
      ba.appendChild(bubble);
      scaledEls.push({ el: bubble, base: "translate(-50%, -100%)", origin: "50% 100%" });
      node.bubble = bubble; node.bubbleText = bt; node.bubbleAnchor = ba;
    }
    nodes[key] = node;
  });

  function setStage(key, stage) {
    var b = BUILDINGS[key], n = nodes[key];
    if (!b.sprites || n.stage === stage) return;
    var idx = Math.max(0, Math.min(b.sprites.length - 1, stage - 1));
    var w = b.w[idx];
    n.img.onload = function () {
      var h = w * (n.img.naturalHeight / n.img.naturalWidth);
      n.sprite.style.left = (b.x - w / 2) + "px";
      n.sprite.style.top = (b.y - h) + "px";
      n.sprite.style.width = w + "px";
      n.peakY = b.y - h;
    };
    n.img.src = ASSET + b.sprites[idx];
    n.stage = stage;
  }

  function stageFor(level) {
    var l = Math.max(1, Number(level) || 1);
    if (l >= 15) return 4;
    if (l >= 10) return 3;
    if (l >= 5) return 2;
    return 1;
  }

  KEYS.forEach(function (k) { setStage(k, 1); });

  // ------------------------------------------------------------------ ambient life

  function fx(cls, x, y, style) {
    var d = document.createElement("div");
    d.className = cls;
    d.style.left = x + "px";
    d.style.top = y + "px";
    if (style) Object.keys(style).forEach(function (k) { d.style.setProperty(k, style[k]); });
    fxLayer.appendChild(d);
    return d;
  }

  // lava glow along the river and in the craters
  [[70, 420, 70], [110, 510, 80], [175, 610, 80], [235, 700, 70], [300, 800, 80], [350, 880, 90], [250, 930, 90],
   [150, 950, 70], [38, 190, 90], [62, 690, 80], [32, 240, 60]].forEach(function (p, i) {
    fx("glow", p[0] - p[2] / 2, p[1] - p[2] / 2, {
      width: p[2] + "px", height: p[2] + "px", "animation-delay": (i * 0.37) + "s"
    });
  });
  // embers drifting up from the lava
  for (var e = 0; e < 26; e++) {
    var t = e / 26;
    var ex = 60 + t * 300 + Math.sin(e * 3.1) * 25;
    var ey = 420 + t * 520 + Math.cos(e * 2.3) * 20;
    fx("ember", ex, ey, {
      "--dx": (Math.sin(e) * 18).toFixed(0) + "px",
      "animation-duration": (2.2 + (e % 5) * 0.5) + "s",
      "animation-delay": (-(e * 0.41) % 3) + "s"
    });
  }
  // volcano smoke
  [[30, 150], [55, 660]].forEach(function (v, vi) {
    for (var s = 0; s < 4; s++) {
      fx("smoke", v[0] - 17, v[1] - 13, { "animation-delay": (-(s * 1.75 + vi * 0.9)) + "s" });
    }
  });
  // drifting cloud wisps around the sky palace
  [[150, 160, 160, 50], [300, 210, 140, 40], [230, 110, 120, 36]].forEach(function (c, i) {
    fx("wisp", c[0], c[1], { width: c[2] + "px", height: c[3] + "px", "animation-delay": (-i * 7) + "s" });
  });
  // sparkles on water and the fountain
  [[800, 140], [830, 165], [860, 128], [490, 525], [480, 535], [870, 990], [910, 1005], [30, 990]].forEach(function (p, i) {
    fx("sparkle", p[0], p[1], { "animation-delay": (i * 0.53) + "s" });
  });
  // a flock of birds crossing now and then
  for (var b2 = 0; b2 < 4; b2++) {
    fx("bird", -60 - b2 * 18, 460 + b2 * 9, { "animation-duration": "26s", "animation-delay": (-(b2 * 0.6)) + "s" });
  }
  // flickering fire on the keep's summit braziers (shown at its final stage)
  var keepFlames = [];
  function updateKeepFlames(stage) {
    keepFlames.forEach(function (f) { if (f.parentNode) f.parentNode.removeChild(f); });
    keepFlames = [];
    if (stage < 4) return;
    var b = BUILDINGS.KEEP;
    var w = b.w[3];
    // flame positions as fractions of the stage-4 picture (x from left, y from top)
    [[0.36, 0.08], [0.61, 0.08], [0.29, 0.2], [0.68, 0.2], [0.21, 0.33], [0.76, 0.33], [0.5, 0.2]].forEach(function (f, i) {
      var h = w * (691 / 640);
      keepFlames.push(fx("flame-glow", b.x - w / 2 + f[0] * w - 8, b.y - h + f[1] * h - 8, { "animation-delay": (i * 0.07) + "s" }));
    });
  }

  // ------------------------------------------------------------------ camera (pan / pinch / zoom)

  var cam = { z: 1, tx: 0, ty: 0 };
  var minZ = 1, maxZ = 3;
  var target = null; // smooth zoom target
  var DEFAULT_FOCUS = { x: 470, y: 520 };

  function screenSize() { return { w: viewport.clientWidth || window.innerWidth, h: viewport.clientHeight || window.innerHeight }; }

  function clampCam() {
    var s = screenSize();
    var ww = W * cam.z, hh = H * cam.z;
    cam.tx = ww <= s.w ? (s.w - ww) / 2 : Math.min(0, Math.max(s.w - ww, cam.tx));
    cam.ty = hh <= s.h ? (s.h - hh) / 2 : Math.min(0, Math.max(s.h - hh, cam.ty));
  }

  function applyCam() {
    clampCam();
    world.style.transform = "translate(" + cam.tx.toFixed(2) + "px," + cam.ty.toFixed(2) + "px) scale(" + cam.z.toFixed(4) + ")";
    var inv = 1 / cam.z;
    for (var i = 0; i < scaledEls.length; i++) {
      var s = scaledEls[i];
      s.el.style.transformOrigin = s.origin;
      s.el.style.transform = s.base + " scale(" + inv.toFixed(4) + ")";
    }
  }

  function zoomAround(newZ, sx, sy) {
    newZ = Math.max(minZ, Math.min(maxZ, newZ));
    var wx = (sx - cam.tx) / cam.z, wy = (sy - cam.ty) / cam.z;
    cam.z = newZ;
    cam.tx = sx - wx * cam.z;
    cam.ty = sy - wy * cam.z;
    applyCam();
  }

  function focusOn(x, y, z) {
    var s = screenSize();
    cam.z = z;
    cam.tx = s.w / 2 - x * z;
    cam.ty = s.h / 2 - y * z;
    applyCam();
  }

  function resetLimits() {
    var s = screenSize();
    minZ = Math.max(s.w / W, s.h / H);
    maxZ = minZ * 3.2;
  }

  function recenter() {
    resetLimits();
    focusOn(DEFAULT_FOCUS.x, DEFAULT_FOCUS.y, minZ * 1.35);
  }

  var pointers = {};
  var lastPinch = null;
  var vel = { x: 0, y: 0 };
  var downAt = null;

  viewport.addEventListener("pointerdown", function (ev) {
    pointers[ev.pointerId] = { x: ev.clientX, y: ev.clientY };
    if (Object.keys(pointers).length === 1) {
      downAt = { x: ev.clientX, y: ev.clientY };
      dragged = false;
      vel.x = vel.y = 0;
    }
    target = null;
  });

  viewport.addEventListener("pointermove", function (ev) {
    var p = pointers[ev.pointerId];
    if (!p) return;
    var ids = Object.keys(pointers);
    if (ids.length === 1) {
      var dx = ev.clientX - p.x, dy = ev.clientY - p.y;
      if (downAt && Math.abs(ev.clientX - downAt.x) + Math.abs(ev.clientY - downAt.y) > 8) dragged = true;
      cam.tx += dx;
      cam.ty += dy;
      vel.x = dx; vel.y = dy;
      applyCam();
    } else if (ids.length >= 2) {
      dragged = true;
      p.x = ev.clientX; p.y = ev.clientY;
      var a = pointers[ids[0]], b = pointers[ids[1]];
      var dist = Math.hypot(a.x - b.x, a.y - b.y);
      var mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2;
      if (lastPinch) {
        cam.tx += mx - lastPinch.mx;
        cam.ty += my - lastPinch.my;
        zoomAround(cam.z * (dist / lastPinch.dist), mx, my);
      }
      lastPinch = { dist: dist, mx: mx, my: my };
      return;
    }
    p.x = ev.clientX; p.y = ev.clientY;
  });

  function endPointer(ev) {
    delete pointers[ev.pointerId];
    if (Object.keys(pointers).length < 2) lastPinch = null;
    // keep `dragged` set until the click that follows a drag has been ignored
    if (dragged) setTimeout(function () { dragged = false; }, 60);
  }
  viewport.addEventListener("pointerup", endPointer);
  viewport.addEventListener("pointercancel", endPointer);
  viewport.addEventListener("wheel", function (ev) {
    ev.preventDefault();
    zoomAround(cam.z * (ev.deltaY < 0 ? 1.12 : 0.89), ev.clientX, ev.clientY);
  }, { passive: false });

  function tick() {
    if (!Object.keys(pointers).length) {
      if (Math.abs(vel.x) + Math.abs(vel.y) > 0.2) {
        cam.tx += vel.x; cam.ty += vel.y;
        vel.x *= 0.9; vel.y *= 0.9;
        applyCam();
      }
      if (target) {
        var s = screenSize();
        var nz = cam.z + (target.z - cam.z) * 0.2;
        zoomAround(nz, s.w / 2, s.h / 2);
        if (Math.abs(target.z - cam.z) < 0.001) target = null;
      }
    }
    requestAnimationFrame(tick);
  }

  window.addEventListener("resize", function () { resetLimits(); applyCam(); });

  // ------------------------------------------------------------------ Kotlin API

  window.CrownfallDiorama = {
    updateState: function (state) {
      if (!state || typeof state !== "object") return;
      var selected = typeof state.selectedBuilding === "string" ? state.selectedBuilding : "";
      var bmap = state.buildings || {};
      KEYS.forEach(function (key) {
        var n = nodes[key], data = bmap[key];
        if (!n || !data) return;
        var isSel = selected === key;
        var upg = Boolean(data.isUpgrading);
        var can = Boolean(data.canUpgrade);
        n.pill.classList.toggle("selected", isSel);
        n.pill.classList.toggle("upgrading", upg);
        n.pill.classList.toggle("upgradeable", !upg && can);
        if (upg && typeof data.countdown === "string" && data.countdown.length) {
          n.levelSpan.textContent = data.countdown;
          n.buildSpan.style.display = "inline";
          n.upSpan.style.display = "none";
        } else {
          n.levelSpan.textContent = "Lv." + (Number(data.level) || 1);
          n.buildSpan.style.display = "none";
          n.upSpan.style.display = can ? "inline-flex" : "none";
        }
        if (n.sprite) {
          n.sprite.classList.toggle("selected", isSel);
          n.sprite.classList.toggle("upgrading", upg);
          var st = stageFor(data.level);
          if (st !== n.stage) {
            setStage(key, st);
            if (key === "KEEP") updateKeepFlames(st);
          }
        }
        if (n.hit) n.hit.classList.toggle("selected", isSel);
        if (n.bubble) {
          var pending = Number(data.pendingAmount) || 0;
          if (pending >= 10 && typeof data.pendingFormatted === "string") {
            n.bubbleText.textContent = "+" + data.pendingFormatted;
            n.bubble.style.display = "inline-flex";
          } else {
            n.bubble.style.display = "none";
          }
        }
      });
    },
    zoomIn: function () { target = { z: Math.min(maxZ, cam.z * 1.25) }; },
    zoomOut: function () { target = { z: Math.max(minZ, cam.z * 0.8) }; },
    recenter: function () { vel.x = vel.y = 0; target = null; recenter(); },
    showHarvestPopup: function (resourceKey, text) {
      var key = null;
      KEYS.forEach(function (k) { if (BUILDINGS[k].resource === resourceKey) key = k; });
      if (!key) return;
      var b = BUILDINGS[key];
      var a = anchorAt(b.x, b.y - (b.r || 60) * 1.2);
      var pop = document.createElement("div");
      pop.className = "harvest-popup";
      pop.textContent = String(text || "+0");
      pop.style.transformOrigin = "50% 100%";
      pop.style.transform = "translate(-50%, -100%) scale(" + (1 / cam.z).toFixed(4) + ")";
      a.appendChild(pop);
      setTimeout(function () { if (a.parentNode) a.parentNode.removeChild(a); }, 1300);
    }
  };

  function ready() {
    resetLimits();
    recenter();
    loader.style.opacity = "0";
    setTimeout(function () { loader.style.display = "none"; }, 450);
    requestAnimationFrame(tick);
    if (window.CrownfallBridge && typeof window.CrownfallBridge.onDioramaReady === "function") {
      window.CrownfallBridge.onDioramaReady();
    }
  }
  if (bg.complete && bg.naturalWidth) ready();
  else {
    bg.addEventListener("load", ready);
    bg.addEventListener("error", ready);
  }
})();
