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
  var W = 2048, H = 2048;

  // World coordinates are background pixels. `x, y` is the ground point under the
  // building (bottom-centre of its picture); `w` is the picture width per stage.
  var BUILDINGS = {
    KEEP: { label: "Obsidian Keep", x: 1030, y: 790, sprites: ["keep_1.webp", "keep_2.webp", "keep_3.webp", "keep_4.webp"], w: [360, 400, 420, 440], flames: [[0.33, 0.25], [0.62, 0.25], [0.45, 0.33]] },
    ALLIANCE_HALL: { label: "Alliance Hall", x: 640, y: 1010, sprites: ["hall_1.webp", "hall_2.webp", "hall_3.webp", "hall_4.webp"], w: [190, 220, 235, 250] },
    BARRACKS: { label: "War Barracks", x: 1395, y: 1085, sprites: ["barracks_1.webp", "barracks_2.webp", "barracks_3.webp", "barracks_4.webp"], w: [210, 245, 265, 285] },
    ARCHERY: { label: "Ashbow Range", x: 730, y: 1255, sprites: ["archery_1.webp", "archery_2.webp", "archery_3.webp", "archery_4.webp"], w: [180, 230, 240, 250] },
    SIEGE: { label: "Siege Foundry", x: 880, y: 1440, sprites: ["siege_1.webp", "siege_2.webp", "siege_3.webp", "siege_4.webp"], w: [165, 195, 220, 220],
      smoke: [[[0.48, 0.03]], [[0.52, 0.02]], [[0.59, 0.03], [0.61, 0.15], [0.74, 0.15]], [[0.59, 0.03], [0.61, 0.15], [0.74, 0.15]]] },
    STABLES: { label: "Dread Stables", x: 1150, y: 1330, sprites: ["stables_1.webp", "stables_2.webp", "stables_3.webp", "stables_4.webp"], w: [250, 250, 265, 275] },
    FARM: { label: "Farmstead", x: 1800, y: 1390, sprites: ["farm_1.webp", "farm_2.webp", "farm_3.webp", "farm_4.webp"], w: [300, 300, 300, 300], resource: "FOOD" },
    LUMBER: { label: "Timber Mill", x: 840, y: 1800, sprites: ["lumber_1.webp", "lumber_2.webp", "lumber_3.webp", "lumber_4.webp"], w: [200, 240, 260, 280], resource: "TIMBER" },
    QUARRY: { label: "Quarry", x: 175, y: 1270, sprites: ["quarry_1.webp", "quarry_2.webp", "quarry_3.webp", "quarry_4.webp"], w: [220, 230, 240, 250], resource: "STONE" },
    MINT: { label: "Gold Mint", x: 1520, y: 1470, sprites: ["mint_1.webp", "mint_2.webp", "mint_3.webp", "mint_4.webp"], w: [190, 200, 210, 230], resource: "GOLD" }
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
      (n.smokeEls || []).forEach(function (el) { if (el.parentNode) el.parentNode.removeChild(el); });
      n.smokeEls = [];
      var sm = b.smoke && b.smoke[idx];
      if (sm) sm.forEach(function (f, si) {
        for (var k = 0; k < 3; k++) {
          n.smokeEls.push(fx("smoke chimney", b.x - w / 2 + f[0] * w - 12, b.y - h + f[1] * h - 14,
            { "animation-delay": (-(k * 2.3 + si * 0.8)) + "s" }));
        }
      });
      if (n.bubbleAnchor) n.bubbleAnchor.style.top = (b.y - h + 10) + "px";
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

  // lava glow: west lava field, the north-east river and the volcano craters
  [[120, 940, 220], [260, 800, 200], [420, 720, 180], [80, 1060, 160], [500, 640, 150], [180, 680, 160],
   [1120, 400, 170], [1300, 300, 170], [1480, 200, 160], [1640, 90, 150], [980, 470, 150],
   [260, 330, 120], [430, 190, 110], [1010, 110, 110], [1150, 120, 90]].forEach(function (p, i) {
    fx("glow", p[0] - p[2] / 2, p[1] - p[2] / 2, {
      width: p[2] + "px", height: p[2] + "px", "animation-delay": (i * 0.37) + "s"
    });
  });
  // embers drifting up from the lava
  for (var e = 0; e < 40; e++) {
    var lavaPts = [[120, 940], [260, 800], [420, 720], [500, 640], [1120, 400], [1300, 300], [1480, 200]];
    var lp = lavaPts[e % lavaPts.length];
    fx("ember", lp[0] + Math.sin(e * 3.1) * 70, lp[1] + Math.cos(e * 2.3) * 40, {
      "--dx": (Math.sin(e) * 24).toFixed(0) + "px",
      "animation-duration": (2.2 + (e % 5) * 0.5) + "s",
      "animation-delay": (-(e * 0.41) % 3) + "s"
    });
  }
  // volcano smoke
  [[260, 320], [430, 180], [1010, 100], [1150, 110], [130, 600]].forEach(function (v, vi) {
    for (var s2 = 0; s2 < 4; s2++) {
      fx("smoke", v[0] - 17, v[1] - 13, { "animation-delay": (-(s2 * 1.75 + vi * 0.9)) + "s" });
    }
  });
  // a few village hearths
  [[330, 1380], [1180, 1600], [1700, 1050], [560, 1640]].forEach(function (v, vi) {
    for (var k = 0; k < 3; k++) fx("smoke chimney", v[0] - 12, v[1] - 14, { "animation-delay": (-(k * 2.3 + vi)) + "s" });
  });
  // drifting cloud wisps around the sky palace
  [[620, 300, 260, 80], [860, 380, 220, 70], [700, 160, 200, 60]].forEach(function (c, i) {
    fx("wisp", c[0], c[1], { width: c[2] + "px", height: c[3] + "px", "animation-delay": (-i * 7) + "s" });
  });
  // sparkles on the fountain, rivers and lakes
  [[1020, 930], [1000, 950], [1760, 500], [1900, 470], [1930, 1120], [160, 1700], [300, 1760], [1700, 1780], [1820, 1860], [1100, 1990]].forEach(function (p, i) {
    fx("sparkle", p[0], p[1], { "animation-delay": (i * 0.53) + "s" });
  });
  // a flock of birds crossing now and then
  for (var b2 = 0; b2 < 5; b2++) {
    fx("bird", -60 - b2 * 22, 900 + b2 * 12, { "animation-duration": "30s", "animation-delay": (-(b2 * 0.6)) + "s" });
  }
  // flickering fire on the keep's summit braziers (shown at its final stage)
  var keepFlames = [];
  function updateKeepFlames(stage) {
    keepFlames.forEach(function (f) { if (f.parentNode) f.parentNode.removeChild(f); });
    keepFlames = [];
    if (stage < 4) return;
    var b = BUILDINGS.KEEP, n = nodes.KEEP;
    var w = b.w[3];
    var ratio = n.img.naturalWidth ? n.img.naturalHeight / n.img.naturalWidth : 0.99;
    var h = w * ratio;
    b.flames.forEach(function (f, i) {
      keepFlames.push(fx("flame-glow", b.x - w / 2 + f[0] * w - 8, b.y - h + f[1] * h - 8, { "animation-delay": (i * 0.07) + "s" }));
    });
  }

  // ------------------------------------------------------------------ camera (pan / pinch / zoom)

  var cam = { z: 1, tx: 0, ty: 0 };
  var minZ = 1, maxZ = 3;
  var target = null; // smooth zoom target
  var DEFAULT_FOCUS = { x: 1000, y: 920 };

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
    maxZ = minZ * 4;
  }

  function recenter() {
    resetLimits();
    focusOn(DEFAULT_FOCUS.x, DEFAULT_FOCUS.y, minZ * 1.25);
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
            if (key === "KEEP") { if (n.img.complete) updateKeepFlames(st); else n.img.addEventListener("load", function () { updateKeepFlames(st); }, { once: true }); }
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
