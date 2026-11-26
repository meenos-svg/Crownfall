/*
 * Crownfall "Ancient Dominion" restyle.
 *
 * Loaded last (after app.js), before initCitadelDiorama() runs. It wraps
 * the original builders instead of replacing them, so the camera, badges, taps and
 * Kotlin bridge in app.js keep working unchanged:
 *   - carved dark stone, sandstone and lava materials
 *   - the Obsidian Keep becomes a stepped ziggurat that grows with Keep level
 *   - a lava river, volcanoes and obsidian fields to the west
 *   - thatched villages, a lake, a forest, a mountain ring and a floating cloud citadel
 *   - terraced gardens in the courtyard
 */
(function () {
  "use strict";

  // ------------------------------------------------------------------ helpers

  function makeCanvas(w, h) {
    var c = document.createElement("canvas");
    c.width = w;
    c.height = h;
    return c;
  }

  function toTex(canvas, rx, ry, isColor) {
    var tex = new THREE.CanvasTexture(canvas);
    tex.wrapS = THREE.RepeatWrapping;
    tex.wrapT = THREE.RepeatWrapping;
    tex.repeat.set(rx || 1, ry || 1);
    if (isColor) tex.encoding = THREE.sRGBEncoding;
    tex.anisotropy = 8;
    tex.needsUpdate = true;
    return tex;
  }

  function rng(seed) {
    var a = seed >>> 0;
    return function () {
      a = (a + 0x6d2b79f5) | 0;
      var t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  function shadowed(mesh) {
    mesh.castShadow = true;
    mesh.receiveShadow = true;
    return mesh;
  }

  function box(parent, w, h, d, x, y, z, mat, ry) {
    var m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat);
    m.position.set(x, y, z);
    if (ry) m.rotation.y = ry;
    parent.add(shadowed(m));
    return m;
  }

  // ------------------------------------------------------------------ textures

  /** Dark umber ashlar with some blocks carved with glyph panels. */
  function carvedStone(seed, base, rows, cols, glyphChance) {
    var col = makeCanvas(512, 512);
    var bmp = makeCanvas(512, 512);
    var c = col.getContext("2d");
    var b = bmp.getContext("2d");
    var r = rng(seed);
    c.fillStyle = "#14100d";
    c.fillRect(0, 0, 512, 512);
    b.fillStyle = "#101010";
    b.fillRect(0, 0, 512, 512);
    var rh = 512 / rows;
    var cw = 512 / cols;
    for (var row = 0; row < rows; row++) {
      var off = (row % 2) * cw * 0.5;
      for (var k = -1; k <= cols; k++) {
        var x = k * cw + off + 2;
        var y = row * rh + 2;
        var w = cw - 4;
        var h = rh - 4;
        var v = base + (r() - 0.5) * 34;
        var rr = Math.round(v * 1.06), gg = Math.round(v * 0.96), bb = Math.round(v * 0.86);
        c.fillStyle = "rgb(" + rr + "," + gg + "," + bb + ")";
        c.fillRect(x, y, w, h);
        for (var s = 0; s < 18; s++) {
          c.fillStyle = r() > 0.5 ? "rgba(0,0,0,0.18)" : "rgba(255,230,190,0.07)";
          c.fillRect(x + r() * w, y + r() * h, 2 + r() * 8, 2 + r() * 4);
        }
        c.fillStyle = "rgba(255,225,180,0.12)";
        c.fillRect(x, y, w, 2);
        c.fillStyle = "rgba(0,0,0,0.38)";
        c.fillRect(x, y + h - 3, w, 3);
        var bv = Math.round(160 + r() * 80);
        b.fillStyle = "rgb(" + bv + "," + bv + "," + bv + ")";
        b.fillRect(x, y, w, h);

        if (r() < glyphChance) {
          // carved glyph: framed panel with a stepped spiral / eye motif
          var px = x + w * 0.16, py = y + h * 0.18, pw = w * 0.68, ph = h * 0.64;
          c.strokeStyle = "rgba(0,0,0,0.55)";
          c.lineWidth = 3;
          c.strokeRect(px, py, pw, ph);
          b.strokeStyle = "#3a3a3a";
          b.lineWidth = 3;
          b.strokeRect(px, py, pw, ph);
          var motif = Math.floor(r() * 3);
          c.beginPath();
          b.beginPath();
          var cx = px + pw / 2, cy = py + ph / 2, rad = Math.min(pw, ph) * 0.32;
          if (motif === 0) {
            for (var t = 0; t < 26; t++) {
              var a = t * 0.5, rr2 = rad * (t / 26);
              var sx = cx + Math.cos(a) * rr2, sy = cy + Math.sin(a) * rr2;
              if (t === 0) { c.moveTo(sx, sy); b.moveTo(sx, sy); } else { c.lineTo(sx, sy); b.lineTo(sx, sy); }
            }
          } else if (motif === 1) {
            c.moveTo(cx - rad, cy); c.quadraticCurveTo(cx, cy - rad, cx + rad, cy); c.quadraticCurveTo(cx, cy + rad, cx - rad, cy);
            b.moveTo(cx - rad, cy); b.quadraticCurveTo(cx, cy - rad, cx + rad, cy); b.quadraticCurveTo(cx, cy + rad, cx - rad, cy);
            c.moveTo(cx + rad * 0.3, cy); c.arc(cx, cy, rad * 0.3, 0, Math.PI * 2);
          } else {
            var st = rad * 0.5;
            c.moveTo(cx - rad, cy + rad); c.lineTo(cx - rad, cy + st); c.lineTo(cx - st, cy + st); c.lineTo(cx - st, cy - st);
            c.lineTo(cx + st, cy - st); c.lineTo(cx + st, cy + st); c.lineTo(cx + rad, cy + st); c.lineTo(cx + rad, cy + rad);
            b.moveTo(cx - rad, cy + rad); b.lineTo(cx - rad, cy + st); b.lineTo(cx - st, cy + st); b.lineTo(cx - st, cy - st);
            b.lineTo(cx + st, cy - st); b.lineTo(cx + st, cy + st); b.lineTo(cx + rad, cy + st); b.lineTo(cx + rad, cy + rad);
          }
          c.strokeStyle = "rgba(0,0,0,0.6)";
          c.lineWidth = 3;
          c.stroke();
          c.strokeStyle = "rgba(255,220,170,0.12)";
          c.lineWidth = 1;
          c.stroke();
          b.stroke();
        }
      }
    }
    return { color: col, bump: bmp };
  }

  /** Warm sun-baked adobe plaster with a darker base band and small dark windows. */
  function adobe(seed) {
    var col = makeCanvas(256, 256);
    var c = col.getContext("2d");
    var r = rng(seed);
    c.fillStyle = "#9a7a54";
    c.fillRect(0, 0, 256, 256);
    for (var i = 0; i < 520; i++) {
      c.fillStyle = r() > 0.5 ? "rgba(70,48,28,0.10)" : "rgba(255,236,200,0.10)";
      var s = 4 + r() * 22;
      c.fillRect(r() * 256, r() * 256, s, s * (0.4 + r()));
    }
    c.fillStyle = "rgba(60,40,24,0.35)";
    c.fillRect(0, 222, 256, 34);
    c.fillStyle = "#2a1c12";
    [[40, 70], [150, 70], [40, 150], [150, 150]].forEach(function (p) {
      c.fillRect(p[0], p[1], 26, 34);
      c.fillStyle = "rgba(255,220,170,0.25)";
      c.fillRect(p[0] - 3, p[1] + 34, 32, 4);
      c.fillStyle = "#2a1c12";
    });
    return col;
  }

  /** Cracked basalt crust with glowing magma veins (used as colour + emissive). */
  function lava(seed) {
    var col = makeCanvas(256, 256);
    var emi = makeCanvas(256, 256);
    var c = col.getContext("2d");
    var e = emi.getContext("2d");
    var r = rng(seed);
    c.fillStyle = "#6a1806";
    c.fillRect(0, 0, 256, 256);
    e.fillStyle = "#8a2400";
    e.fillRect(0, 0, 256, 256);
    // hot bright cores
    for (var i = 0; i < 60; i++) {
      var g = c.createRadialGradient(r() * 256, r() * 256, 0, 0, 0, 0);
      var x = r() * 256, y = r() * 256, rad = 8 + r() * 26;
      var grd = c.createRadialGradient(x, y, 0, x, y, rad);
      grd.addColorStop(0, "rgba(255,190,70,0.95)");
      grd.addColorStop(1, "rgba(220,70,10,0)");
      c.fillStyle = grd;
      c.fillRect(x - rad, y - rad, rad * 2, rad * 2);
      var grd2 = e.createRadialGradient(x, y, 0, x, y, rad);
      grd2.addColorStop(0, "rgba(255,200,80,1)");
      grd2.addColorStop(1, "rgba(230,80,10,0)");
      e.fillStyle = grd2;
      e.fillRect(x - rad, y - rad, rad * 2, rad * 2);
    }
    // dark cooling crust plates
    for (var p = 0; p < 22; p++) {
      var px = r() * 256, py = r() * 256, pr = 8 + r() * 16;
      c.beginPath();
      e.beginPath();
      for (var k = 0; k < 7; k++) {
        var a = (k / 7) * Math.PI * 2;
        var rr = pr * (0.6 + r() * 0.5);
        var vx = px + Math.cos(a) * rr, vy = py + Math.sin(a) * rr * 0.7;
        if (k === 0) { c.moveTo(vx, vy); e.moveTo(vx, vy); } else { c.lineTo(vx, vy); e.lineTo(vx, vy); }
      }
      c.closePath();
      e.closePath();
      c.fillStyle = "rgba(40,16,8,0.92)";
      c.fill();
      e.fillStyle = "rgba(0,0,0,0.95)";
      e.fill();
    }
    return { color: col, emissive: emi };
  }

  function basalt(seed) {
    var col = makeCanvas(256, 256);
    var c = col.getContext("2d");
    var r = rng(seed);
    c.fillStyle = "#1c1816";
    c.fillRect(0, 0, 256, 256);
    for (var i = 0; i < 900; i++) {
      var v = Math.round(20 + r() * 40);
      c.fillStyle = "rgba(" + (v + 8) + "," + v + "," + (v - 4) + ",0.6)";
      c.fillRect(r() * 256, r() * 256, 2 + r() * 6, 2 + r() * 4);
    }
    for (var j = 0; j < 40; j++) {
      c.strokeStyle = "rgba(255,90,20,0.18)";
      c.lineWidth = 1;
      c.beginPath();
      var x = r() * 256, y = r() * 256;
      c.moveTo(x, y);
      c.lineTo(x + (r() - 0.5) * 40, y + (r() - 0.5) * 40);
      c.stroke();
    }
    return col;
  }

  /** Golden sandstone relief panel: a border frame around a serpent / sun glyph. */
  function reliefPanel(seed) {
    var col = makeCanvas(256, 256);
    var bmp = makeCanvas(256, 256);
    var c = col.getContext("2d");
    var b = bmp.getContext("2d");
    var r = rng(seed);
    c.fillStyle = "#8a6a3c";
    c.fillRect(0, 0, 256, 256);
    b.fillStyle = "#808080";
    b.fillRect(0, 0, 256, 256);
    for (var i = 0; i < 300; i++) {
      c.fillStyle = r() > 0.5 ? "rgba(40,26,10,0.12)" : "rgba(255,225,160,0.10)";
      c.fillRect(r() * 256, r() * 256, 2 + r() * 10, 2 + r() * 6);
    }
    function both(fn) { fn(c, "rgba(30,18,6,0.75)"); fn(b, "#202020"); }
    both(function (ctx, col2) {
      ctx.strokeStyle = col2;
      ctx.lineWidth = 8;
      ctx.strokeRect(14, 14, 228, 228);
      ctx.lineWidth = 5;
      // serpent spiral
      ctx.beginPath();
      for (var t = 0; t < 60; t++) {
        var a = t * 0.32;
        var rr = 8 + t * 1.55;
        var x = 128 + Math.cos(a) * rr;
        var y = 128 + Math.sin(a) * rr * 0.85;
        if (t === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
      }
      ctx.stroke();
      // stepped fret along the bottom
      ctx.beginPath();
      for (var k = 0; k < 6; k++) {
        var bx = 26 + k * 36;
        ctx.moveTo(bx, 226); ctx.lineTo(bx, 206); ctx.lineTo(bx + 18, 206); ctx.lineTo(bx + 18, 216); ctx.lineTo(bx + 30, 216);
      }
      ctx.stroke();
    });
    c.strokeStyle = "rgba(255,230,170,0.35)";
    c.lineWidth = 2;
    c.strokeRect(20, 20, 216, 216);
    return { color: col, bump: bmp };
  }

  // ------------------------------------------------------------------ materials

  var origMaterials = window.createDioramaMaterials;
  window.createDioramaMaterials = function () {
    var mats = origMaterials();

    var wall = carvedStone(1101, 62, 6, 3, 0.2);
    var keep = carvedStone(1202, 56, 5, 3, 0.4);
    var flag = carvedStone(1303, 84, 5, 5, 0.0);
    var lv = lava(1404);

    mats.stoneWall = new THREE.MeshStandardMaterial({
      map: toTex(wall.color, 2, 1.5, true), bumpMap: toTex(wall.bump, 2, 1.5, false),
      bumpScale: 0.32, roughness: 0.9, metalness: 0.04
    });
    mats.stoneKeep = new THREE.MeshStandardMaterial({
      map: toTex(keep.color, 2, 2, true), bumpMap: toTex(keep.bump, 2, 2, false),
      bumpScale: 0.38, roughness: 0.88, metalness: 0.05
    });
    mats.flagstoneTop = new THREE.MeshStandardMaterial({
      map: toTex(flag.color, 2, 2, true), bumpMap: toTex(flag.bump, 2, 2, false),
      bumpScale: 0.2, roughness: 0.86, metalness: 0.04
    });
    mats.tudorWall = new THREE.MeshStandardMaterial({
      map: toTex(adobe(1505), 1, 1, true), roughness: 0.92, metalness: 0.0
    });
    mats.thatchRoof.userData.pitched = true;
    mats.adobeRoof = new THREE.MeshStandardMaterial({ color: 0x8c6c48, roughness: 0.95 });
    mats.slateRoof = mats.adobeRoof;
    mats.turretRoof = new THREE.MeshStandardMaterial({ color: 0x6b4e2a, roughness: 0.6, metalness: 0.35 });

    var lavaColor = toTex(lv.color, 1, 1, true);
    var lavaEmit = toTex(lv.emissive, 1, 1, true);
    mats.lava = new THREE.MeshStandardMaterial({
      map: lavaColor, emissiveMap: lavaEmit, emissive: 0xffffff, emissiveIntensity: 1.0, roughness: 0.7
    });
    mats.lavaTextures = [lavaColor, lavaEmit];
    var poolColor = toTex(lv.color, 2, 2, true);
    var poolEmit = toTex(lv.emissive, 2, 2, true);
    mats.lavaPool = new THREE.MeshStandardMaterial({
      map: poolColor, emissiveMap: poolEmit, emissive: 0xffffff, emissiveIntensity: 1.0, roughness: 0.7
    });
    mats.lavaTextures.push(poolColor, poolEmit);
    mats.basalt = new THREE.MeshStandardMaterial({ map: toTex(basalt(1606), 3, 3, true), color: 0x9a8f88, roughness: 0.95 });
    mats.volcanoRock = new THREE.MeshStandardMaterial({ color: 0x1e1814, roughness: 0.95, flatShading: true });
    mats.obsidianRock = new THREE.MeshStandardMaterial({ color: 0x1a1513, roughness: 0.55, metalness: 0.2 });
    mats.templeGold = new THREE.MeshStandardMaterial({
      color: 0xe0b23c, roughness: 0.28, metalness: 0.9, emissive: 0x5a3c06, emissiveIntensity: 0.35
    });
    mats.brazierFlame = new THREE.MeshBasicMaterial({ color: 0xffb340, transparent: true, opacity: 0.92 });
    mats.cloud = new THREE.MeshStandardMaterial({ color: 0xf2eef6, roughness: 1.0, emissive: 0x6a6070, emissiveIntensity: 0.25 });
    mats.marble = new THREE.MeshStandardMaterial({ color: 0xe8e2d6, roughness: 0.5 });
    mats.lakeWater = new THREE.MeshStandardMaterial({
      color: 0x2a5c78, roughness: 0.12, metalness: 0.25, emissive: 0x0a2840, emissiveIntensity: 0.3
    });
    mats.gardenSoil = new THREE.MeshStandardMaterial({ color: 0x3b2a1c, roughness: 1.0 });
    mats.leafBright = new THREE.MeshStandardMaterial({ color: 0x4f8a3a, roughness: 0.85 });
    mats.agave = new THREE.MeshStandardMaterial({ color: 0x5e8a6e, roughness: 0.8 });
    mats.flowerRed = new THREE.MeshStandardMaterial({ color: 0xc23a3a, roughness: 0.8 });
    mats.flowerGold = new THREE.MeshStandardMaterial({ color: 0xe0b33a, roughness: 0.8 });
    mats.broadleaf = new THREE.MeshStandardMaterial({ color: 0x142a12, roughness: 0.9 });
    mats.mountainFar = new THREE.MeshStandardMaterial({ color: 0x1c1712, roughness: 0.96, flatShading: true });
    var relief = reliefPanel(1707);
    mats.relief = new THREE.MeshStandardMaterial({
      map: toTex(relief.color, 1, 1, true), bumpMap: toTex(relief.bump, 1, 1, false),
      bumpScale: 0.6, roughness: 0.75, metalness: 0.15
    });
    mats.teal = new THREE.MeshStandardMaterial({ color: 0x1f6b62, roughness: 0.6 });
    mats.hedge = new THREE.MeshStandardMaterial({ color: 0x1d4a1a, roughness: 0.9 });
    mats.corn = new THREE.MeshStandardMaterial({ color: 0xd9b23a, roughness: 0.8 });
    mats.cropGreen = new THREE.MeshStandardMaterial({ color: 0x2f6a24, roughness: 0.85 });
    mats.darkOpening = new THREE.MeshBasicMaterial({ color: 0x0e0a07 });
    mats.horse = new THREE.MeshStandardMaterial({ color: 0x3a2312, roughness: 0.8 });
    mats.cloth = [0x8a2a22, 0x2a4a7a, 0xc9a23a, 0x3d6b3a, 0x7a4a8a, 0xd8d0c0].map(function (c) {
      return new THREE.MeshStandardMaterial({ color: c, roughness: 0.85 });
    });
    mats.skin = new THREE.MeshStandardMaterial({ color: 0x6b4a32, roughness: 0.8 });
    return mats;
  };

  // ------------------------------------------------------------------ animation hooks

  var updaters = [];
  function startLoop() {
    var last = performance.now();
    var t0 = last;
    function tick(now) {
      var dt = Math.min(0.05, (now - last) / 1000);
      last = now;
      var t = (now - t0) / 1000;
      for (var i = 0; i < updaters.length; i++) {
        try { updaters[i](dt, t); } catch (e) { /* keep animating */ }
      }
      requestAnimationFrame(tick);
    }
    requestAnimationFrame(tick);
  }

  // ------------------------------------------------------------------ small builders

  function cyl(parent, rt, rb, h, seg, x, y, z, mat) {
    var m = new THREE.Mesh(new THREE.CylinderGeometry(rt, rb, h, seg), mat);
    m.position.set(x, y, z);
    parent.add(shadowed(m));
    return m;
  }

  function cone(parent, r, h, seg, x, y, z, mat, ry) {
    var m = new THREE.Mesh(new THREE.ConeGeometry(r, h, seg), mat);
    m.position.set(x, y, z);
    if (ry) m.rotation.y = ry;
    parent.add(shadowed(m));
    return m;
  }

  function pitchedRoof(parent, w, h, d, x, y, z, mat, ry) {
    var shape = new THREE.Shape();
    shape.moveTo(-w / 2, 0);
    shape.lineTo(0, h);
    shape.lineTo(w / 2, 0);
    shape.closePath();
    var geo = new THREE.ExtrudeGeometry(shape, { depth: d, bevelEnabled: false });
    geo.translate(0, 0, -d / 2);
    var m = new THREE.Mesh(geo, mat);
    m.position.set(x, y, z);
    if (ry) m.rotation.y = ry;
    parent.add(shadowed(m));
    return m;
  }

  /** Merlons around the top edge of a w x d rectangle centred at (x, z). */
  function crenellate(parent, w, d, x, y, z, mat, size) {
    var s = size || 0.5;
    var nx = Math.max(2, Math.round(w / (s * 2)));
    var nz = Math.max(2, Math.round(d / (s * 2)));
    for (var i = 0; i < nx; i++) {
      var px = x - w / 2 + (i + 0.5) * (w / nx);
      box(parent, s, s * 1.1, s * 0.8, px, y + s * 0.55, z - d / 2 + s * 0.4, mat);
      box(parent, s, s * 1.1, s * 0.8, px, y + s * 0.55, z + d / 2 - s * 0.4, mat);
    }
    for (var j = 1; j < nz - 1; j++) {
      var pz = z - d / 2 + (j + 0.5) * (d / nz);
      box(parent, s * 0.8, s * 1.1, s, x - w / 2 + s * 0.4, y + s * 0.55, pz, mat);
      box(parent, s * 0.8, s * 1.1, s, x + w / 2 - s * 0.4, y + s * 0.55, pz, mat);
    }
  }

  /** Vertical pilaster ribs on the front (+z) face, like the carved fortress in the art. */
  function pilasters(parent, w, h, x, y, z, mat, count) {
    var n = count || Math.max(2, Math.round(w / 1.4));
    for (var i = 0; i < n; i++) {
      var px = x - w / 2 + (i + 0.5) * (w / n);
      box(parent, 0.32, h * 0.86, 0.22, px, y, z + 0.1, mat);
    }
  }

  function adobeHouse(parent, mats, w, h, d, x, z, ry, tiled) {
    var g = new THREE.Group();
    g.position.set(x, 0, z);
    if (ry) g.rotation.y = ry;
    box(g, w + 0.2, 0.35, d + 0.2, 0, 0.17, 0, mats.flagstoneTop);
    box(g, w, h, d, 0, 0.35 + h / 2, 0, mats.tudorWall);
    box(g, 0.7, 1.1, 0.06, 0, 0.9, d / 2 + 0.02, mats.darkOpening);
    if (tiled) {
      pitchedRoof(g, w + 0.5, h * 0.45, d + 0.4, 0, 0.35 + h, 0, mats.terracottaRoof);
    } else {
      box(g, w + 0.2, 0.16, d + 0.2, 0, 0.35 + h + 0.08, 0, mats.adobeRoof);
      crenellate(g, w + 0.2, d + 0.2, 0, 0.35 + h + 0.16, 0, mats.tudorWall, 0.3);
    }
    parent.add(g);
    return g;
  }

  function thatchHut(parent, mats, x, y, z, s) {
    var g = new THREE.Group();
    g.position.set(x, y, z);
    cyl(g, 0.9 * s, 1.0 * s, 1.1 * s, 10, 0, 0.55 * s, 0, mats.tudorWall);
    cone(g, 1.4 * s, 1.35 * s, 10, 0, 1.1 * s + 0.65 * s, 0, mats.thatchRoof);
    box(g, 0.4 * s, 0.6 * s, 0.1, 0, 0.3 * s, 0.97 * s, mats.darkOpening);
    parent.add(g);
    return g;
  }

  function thatchHall(parent, mats, w, d, x, z, ry) {
    var g = new THREE.Group();
    g.position.set(x, 0, z);
    if (ry) g.rotation.y = ry;
    box(g, w, 1.3, d, 0, 0.65, 0, mats.tudorWall);
    pitchedRoof(g, w + 0.8, 1.5, d + 0.6, 0, 1.3, 0, mats.thatchRoof);
    box(g, 0.8, 1.0, 0.06, 0, 0.5, d / 2 + 0.02, mats.darkOpening);
    parent.add(g);
    return g;
  }

  function villager(parent, mats, x, y, z, seed) {
    var g = new THREE.Group();
    g.position.set(x, y, z);
    var cloth = mats.cloth[seed % mats.cloth.length];
    cyl(g, 0.12, 0.2, 0.55, 6, 0, 0.28, 0, cloth);
    var head = new THREE.Mesh(new THREE.SphereGeometry(0.1, 6, 5), mats.skin);
    head.position.y = 0.66;
    g.add(head);
    parent.add(g);
    return g;
  }

  function brazier(parent, mats, x, y, z, flames) {
    cyl(parent, 0.3, 0.16, 0.4, 8, x, y + 0.2, z, mats.templeGold);
    var f = new THREE.Mesh(new THREE.ConeGeometry(0.22, 0.65, 7), mats.brazierFlame);
    f.position.set(x, y + 0.72, z);
    parent.add(f);
    if (flames) flames.push(f);
  }

  // ------------------------------------------------------------------ world

  var WALL = { x: 12, z: 18, h: 4.2, t: 2.2, gate: 2.4 };

  function terrainHeight(x, z) {
    var ax = Math.abs(x), az = Math.abs(z);
    if (ax < 15.5 && az < 21.5) return 0;
    if (x < -12 && x > -23 && z > -45 && z < 30) return 0; // lava flats
    var dx = Math.max(0, ax - 15.5), dz = Math.max(0, az - 21.5);
    var dist = Math.sqrt(dx * dx + dz * dz);
    var blend = Math.min(1, dist / 18);
    var hill = (Math.sin(x * 0.07) * Math.cos(z * 0.06) * 2.6 + Math.sin((x + z) * 0.045) * 2.0 + dist * 0.12) * blend;
    // lake basin south-east
    var ldx = (x - 21) / 9, ldz = (z - 29) / 6.5;
    var ld = Math.sqrt(ldx * ldx + ldz * ldz);
    if (ld < 1.6) hill *= Math.max(0, (ld - 1.0) / 0.6);
    return Math.max(0, hill);
  }

  function buildDominionWorld(scene, mats) {
    var g = new THREE.Group();
    scene.add(g);
    var smokeSpots = [];
    var flames = [];

    scene.background = new THREE.Color(0xb3a789);
    scene.fog = new THREE.Fog(0xb3a789, 85, 300);

    // Terrain
    var gGeo = new THREE.PlaneGeometry(260, 260, 104, 104);
    gGeo.rotateX(-Math.PI / 2);
    var pos = gGeo.attributes.position;
    for (var i = 0; i < pos.count; i++) pos.setY(i, terrainHeight(pos.getX(i), pos.getZ(i)));
    gGeo.computeVertexNormals();
    var terrain = new THREE.Mesh(gGeo, mats.valleyGrass);
    terrain.receiveShadow = true;
    g.add(terrain);

    // Paved inner courtyard
    var yard = new THREE.Mesh(new THREE.PlaneGeometry(WALL.x * 2, WALL.z * 2), mats.courtyardDirt);
    yard.rotation.x = -Math.PI / 2;
    yard.position.y = 0.03;
    yard.receiveShadow = true;
    g.add(yard);
    // flagstone avenues: south gate -> fountain -> ziggurat, west gate -> fountain
    g.add(ribbon([[0, 19.5], [0, 10], [-1.5, 2], [-2, -1], [1.5, -5], [5, -6.5]], 2.6, 0.06, mats.flagstoneTop));
    g.add(ribbon([[-14, 0], [-9, -0.5], [-4.5, -1]], 2.4, 0.06, mats.flagstoneTop));
    g.add(ribbon([[-2, -1], [-6, -6], [-6, -8]], 1.8, 0.06, mats.flagstoneTop));

    // Walls with carved relief panels on the outside
    function wallSeg(x1, z1, x2, z2) {
      var dx = x2 - x1, dz = z2 - z1;
      var len = Math.sqrt(dx * dx + dz * dz);
      var seg = new THREE.Group();
      seg.position.set((x1 + x2) / 2, 0, (z1 + z2) / 2);
      seg.rotation.y = Math.atan2(dx, dz);
      box(seg, WALL.t, WALL.h, len, 0, WALL.h / 2, 0, mats.stoneWall);
      box(seg, WALL.t + 0.2, 0.24, len + 0.1, 0, WALL.h + 0.1, 0, mats.flagstoneTop);
      var n = Math.max(2, Math.floor(len / 1.3));
      for (var m = 0; m < n; m++) {
        var mz = -len / 2 + (m + 0.5) * (len / n);
        box(seg, 0.5, 0.75, 0.75, WALL.t / 2 - 0.25, WALL.h + 0.55, mz, mats.stoneWall);
        box(seg, 0.4, 0.5, 0.75, -WALL.t / 2 + 0.2, WALL.h + 0.45, mz, mats.stoneWall);
      }
      var panels = Math.floor(len / 4);
      for (var p = 0; p < panels; p++) {
        var pz = -len / 2 + (p + 0.5) * (len / panels);
        var panel = new THREE.Mesh(new THREE.PlaneGeometry(2.4, 2.4), mats.relief);
        panel.position.set(WALL.t / 2 + 0.03, WALL.h * 0.52, pz);
        panel.rotation.y = Math.PI / 2;
        seg.add(panel);
        var panel2 = panel.clone();
        panel2.position.x = -WALL.t / 2 - 0.03;
        panel2.rotation.y = -Math.PI / 2;
        seg.add(panel2);
      }
      g.add(seg);
    }
    var X = WALL.x, Z = WALL.z, G = WALL.gate;
    wallSeg(-X, -Z, X, -Z);           // north
    wallSeg(X, -Z, X, Z);             // east
    wallSeg(X, Z, G + 1.2, Z);        // south (east half)
    wallSeg(-G - 1.2, Z, -X, Z);      // south (west half)
    wallSeg(-X, Z, -X, G + 1.2);      // west (south half)
    wallSeg(-X, -G - 1.2, -X, -Z);    // west (north half)

    // Massive tapered towers with relief faces
    function tower(x, z, s, h, withFire) {
      var t = new THREE.Group();
      t.position.set(x, 0, z);
      var body = new THREE.Mesh(new THREE.CylinderGeometry(s * 0.62, s * 0.78, h, 4), mats.stoneKeep);
      body.rotation.y = Math.PI / 4;
      body.position.y = h / 2;
      t.add(shadowed(body));
      for (var f = 0; f < 4; f++) {
        var a = f * Math.PI / 2;
        var panel = new THREE.Mesh(new THREE.PlaneGeometry(s * 0.78, h * 0.45), mats.relief);
        var off = s * 0.72 + 0.02;
        panel.position.set(Math.sin(a) * off, h * 0.42, Math.cos(a) * off);
        panel.rotation.y = a;
        panel.rotation.x = 0;
        t.add(panel);
      }
      box(t, s * 1.0, 0.4, s * 1.0, 0, h + 0.2, 0, mats.flagstoneTop);
      crenellate(t, s * 1.0, s * 1.0, 0, h + 0.4, 0, mats.stoneWall, 0.45);
      if (withFire) brazier(t, mats, 0, h + 0.4, 0, flames);
      g.add(t);
    }
    tower(-X, -Z, 4.0, 7.6, true);
    tower(X, -Z, 4.0, 7.6, true);
    tower(-X, Z, 4.0, 7.2, true);
    tower(X, Z, 4.0, 7.2, true);
    tower(X, 0, 3.4, 6.4, false);
    tower(0, -Z, 3.4, 6.4, false);
    // gate pylons
    tower(-G - 1.6, Z, 2.9, 8.4, true);
    tower(G + 1.6, Z, 2.9, 8.4, true);
    tower(-X, -G - 1.6, 2.9, 8.8, true);
    tower(-X, G + 1.6, 2.9, 8.8, true);
    // lintels over the gates
    box(g, 2 * G + 1.4, 0.8, WALL.t + 0.4, 0, 6.6, Z, mats.stoneKeep);
    box(g, WALL.t + 0.4, 0.8, 2 * G + 1.4, -X, 6.8, 0, mats.stoneKeep);

    // Lava river right outside the west wall, crossed by a stone bridge from the west gate
    var lavaPath = [[-17, -46], [-16.6, -30], [-17.4, -16], [-16.5, -2], [-17.2, 12], [-18.2, 24], [-24, 33], [-36, 41]];
    g.add(ribbon(lavaPath, 8.5, 0.05, mats.basalt));
    g.add(ribbon(lavaPath, 4.0, 0.1, mats.lava));
    var bridge = new THREE.Group();
    bridge.position.set(-16.8, 0, 0);
    box(bridge, 7.2, 0.5, 3.0, 0, 1.05, 0, mats.flagstoneTop);
    box(bridge, 7.2, 0.6, 0.3, 0, 1.55, 1.35, mats.stoneWall);
    box(bridge, 7.2, 0.6, 0.3, 0, 1.55, -1.35, mats.stoneWall);
    [-2.6, 2.6].forEach(function (px) { box(bridge, 1.2, 1.1, 3.0, px, 0.5, 0, mats.stoneWall); });
    g.add(bridge);

    var rr = rng(91);
    for (var sp = 0; sp < 30; sp++) {
      var pz = -42 + sp * 2.4;
      var side = sp % 2 === 0 ? -1 : 1;
      var lx = -17.2 + side * (2.6 + rr() * 1.4);
      if (Math.abs(pz) < 3) continue;
      var spire = new THREE.Mesh(new THREE.DodecahedronGeometry(0.5 + rr() * 0.6, 0), mats.obsidianRock);
      spire.position.set(lx, 0.6, pz);
      spire.scale.set(0.8, 1.4 + rr() * 1.6, 0.8);
      spire.rotation.y = rr() * 6;
      g.add(shadowed(spire));
    }
    addVolcano(g, mats, -42, -28, 11, 16, smokeSpots);
    addVolcano(g, mats, -36, 4, 6, 8, smokeSpots);
    addVolcano(g, mats, -46, 22, 8, 11, smokeSpots);

    var emberMat = new THREE.MeshBasicMaterial({ color: 0xffa040, transparent: true, opacity: 0.9 });
    var embers = [];
    for (var e = 0; e < 44; e++) {
      var em = new THREE.Mesh(new THREE.SphereGeometry(0.07, 5, 4), emberMat);
      g.add(em);
      embers.push({ m: em, x: -17 + (rr() - 0.5) * 3, z: -40 + rr() * 66, p: rr() });
    }
    var ashMat = new THREE.MeshBasicMaterial({ color: 0x5a524c, transparent: true, opacity: 0.35, depthWrite: false });
    var plumes = [];
    smokeSpots.forEach(function (spot, si) {
      for (var p = 0; p < 6; p++) {
        var puff = new THREE.Mesh(new THREE.SphereGeometry(1.2, 8, 6), ashMat.clone());
        g.add(puff);
        plumes.push({ m: puff, o: spot, p: p / 6 + si * 0.21 });
      }
    });
    var lavaTex = mats.lavaTextures || [];
    updaters.push(function (dt, t) {
      for (var k = 0; k < lavaTex.length; k++) lavaTex[k].offset.y -= dt * 0.05;
      mats.lava.emissiveIntensity = 0.95 + Math.sin(t * 2.3) * 0.12;
      for (var j = 0; j < embers.length; j++) {
        var it = embers[j];
        var c = (t * 0.35 + it.p) % 1;
        it.m.position.set(it.x + Math.sin(t + it.p * 9) * 0.4, 0.2 + c * 4.5, it.z);
        it.m.material.opacity = 1 - c;
      }
      for (var q = 0; q < plumes.length; q++) {
        var pl = plumes[q];
        var cy = (t * 0.12 + pl.p) % 1;
        pl.m.position.set(pl.o.x + cy * 3, pl.o.y + cy * 9, pl.o.z + cy * 1.5);
        var s = 1 + cy * 3;
        pl.m.scale.set(s, s * 0.8, s);
        pl.m.material.opacity = (1 - cy) * 0.4;
      }
      for (var f = 0; f < flames.length; f++) {
        var fl = flames[f];
        fl.scale.set(1, 0.85 + Math.sin(t * 9 + f * 1.7) * 0.15 + Math.sin(t * 23 + f) * 0.06, 1);
      }
    });

    // Outside the south gate: market stalls, crop plots, huts and villagers
    var market = [[6.5, 23.5], [9.5, 25.5], [3.5, 26.5], [12.5, 23]];
    market.forEach(function (m, idx) {
      var st = new THREE.Group();
      st.position.set(m[0], terrainHeight(m[0], m[1]), m[1]);
      box(st, 2.0, 0.8, 1.6, 0, 0.4, 0, mats.timber);
      [[-0.9, -0.7], [0.9, -0.7], [-0.9, 0.7], [0.9, 0.7]].forEach(function (c) {
        cyl(st, 0.06, 0.06, 1.7, 5, c[0], 0.85, c[1], mats.timber);
      });
      pitchedRoof(st, 2.5, 0.8, 2.0, 0, 1.7, 0, mats.thatchRoof);
      [0, 1, 2].forEach(function (k) {
        var basket = new THREE.Mesh(new THREE.CylinderGeometry(0.22, 0.18, 0.2, 8), k === 1 ? mats.flowerRed : mats.corn);
        basket.position.set(-0.6 + k * 0.6, 0.9, 0.3);
        st.add(basket);
      });
      g.add(st);
    });
    function cropPlot(x, z, w, d, mat) {
      box(g, w, 0.2, d, x, 0.1, z, mats.gardenSoil);
      for (var r2 = 0; r2 < Math.floor(d / 0.6); r2++) {
        for (var c2 = 0; c2 < Math.floor(w / 0.6); c2++) {
          var pl = new THREE.Mesh(new THREE.DodecahedronGeometry(0.2, 0), mat);
          pl.position.set(x - w / 2 + 0.3 + c2 * 0.6, 0.32, z - d / 2 + 0.3 + r2 * 0.6);
          g.add(pl);
        }
      }
    }
    cropPlot(-1.5, 30.5, 3.2, 2.4, mats.cropGreen);
    var huts = [[16, 20], [18.5, 23], [15, 26], [-14, 31.5], [-11, 33], [24, 14], [26.5, 17], [23, 20.5], [27, -4], [29.5, -1], [26, 2]];
    huts.forEach(function (h, idx) {
      thatchHut(g, mats, h[0], terrainHeight(h[0], h[1]), h[1], 0.8 + (idx % 3) * 0.12);
    });
    var walkers = [];
    var vr = rng(303);
    for (var v = 0; v < 22; v++) {
      var vx = 0 + vr() * 15, vz = 21 + vr() * 7;
      var person = villager(g, mats, vx, terrainHeight(vx, vz), vz, v);
      walkers.push({ m: person, x: vx, z: vz, a: vr() * 6, r: 0.6 + vr() * 1.6, s: 0.2 + vr() * 0.3 });
    }
    updaters.push(function (dt, t) {
      for (var w = 0; w < walkers.length; w++) {
        var wk = walkers[w];
        var ang = wk.a + t * wk.s;
        wk.m.position.x = wk.x + Math.cos(ang) * wk.r;
        wk.m.position.z = wk.z + Math.sin(ang) * wk.r * 0.6;
        wk.m.rotation.y = -ang;
      }
    });

    // Lake to the south-east
    var lake = new THREE.Mesh(new THREE.CircleGeometry(1, 40), mats.lakeWater);
    lake.rotation.x = -Math.PI / 2;
    lake.scale.set(8, 5.5, 1);
    lake.position.set(21, 0.12, 29);
    g.add(lake);

    // Forest to the south-west, beyond the lava
    var fr = rng(55);
    for (var f2 = 0; f2 < 50; f2++) {
      var fx = -34 + fr() * 16;
      var fz = 26 + fr() * 14;
      if (fx > -25 && fz < 34) continue;
      addBroadleaf(g, mats, fx, terrainHeight(fx, fz), fz, 0.9 + fr() * 0.6);
    }
    for (var f3 = 0; f3 < 24; f3++) {
      var ex = 18 + fr() * 14, ez = -38 + fr() * 14;
      addBroadleaf(g, mats, ex, terrainHeight(ex, ez), ez, 0.9 + fr() * 0.5);
    }

    // Mountain ring
    [[-6, -66, 24, 34], [16, -68, 22, 30], [34, -60, 20, 28], [52, -44, 22, 32],
     [62, -18, 18, 26], [64, 8, 16, 22], [-26, -68, 20, 26], [-62, -40, 18, 24]].forEach(function (m) {
      var peak = new THREE.Mesh(new THREE.ConeGeometry(m[2], m[3], 8), mats.mountainFar);
      peak.position.set(m[0], m[3] * 0.44, m[1]);
      peak.rotation.y = m[0] * 0.13;
      g.add(peak);
      var cap = new THREE.Mesh(new THREE.ConeGeometry(m[2] * 0.33, m[3] * 0.3, 8), mats.mountainSnow);
      cap.position.set(m[0], m[3] * 0.79, m[1]);
      cap.rotation.y = m[0] * 0.13;
      g.add(cap);
    });

    // Hedged garden beds around the fountain
    function agave(x, z, s) {
      for (var k = 0; k < 7; k++) {
        var leaf = new THREE.Mesh(new THREE.ConeGeometry(0.08 * s, 0.9 * s, 4), mats.agave);
        var a = (k / 7) * Math.PI * 2;
        leaf.position.set(x + Math.cos(a) * 0.18 * s, 0.75 + 0.35 * s, z + Math.sin(a) * 0.18 * s);
        leaf.rotation.z = Math.cos(a) * 0.6;
        leaf.rotation.x = -Math.sin(a) * 0.6;
        g.add(leaf);
      }
    }
    function garden(x, z, w, d, kind) {
      box(g, w, 0.55, 0.3, x, 0.28, z - d / 2, mats.hedge);
      box(g, w, 0.55, 0.3, x, 0.28, z + d / 2, mats.hedge);
      box(g, 0.3, 0.55, d, x - w / 2, 0.28, z, mats.hedge);
      box(g, 0.3, 0.55, d, x + w / 2, 0.28, z, mats.hedge);
      box(g, w - 0.3, 0.3, d - 0.3, x, 0.15, z, mats.gardenSoil);
      var pr = rng(Math.floor(x * 97 + z * 13 + 500));
      var cols = Math.floor((w - 0.6) / 0.55), rows = Math.floor((d - 0.6) / 0.55);
      for (var r = 0; r < rows; r++) {
        for (var c = 0; c < cols; c++) {
          var px = x - w / 2 + 0.45 + c * 0.55, pz = z - d / 2 + 0.45 + r * 0.55;
          if (kind === "corn") {
            var stalk = new THREE.Mesh(new THREE.ConeGeometry(0.1, 0.85, 5), pr() < 0.5 ? mats.corn : mats.cropGreen);
            stalk.position.set(px, 0.72, pz);
            g.add(stalk);
          } else if (kind === "agave") {
            if ((r + c) % 2 === 0) agave(px, pz, 0.8 + pr() * 0.4);
          } else {
            var pick = pr();
            var mat = pick < 0.25 ? mats.flowerRed : pick < 0.4 ? mats.flowerGold : mats.leafBright;
            var plant = new THREE.Mesh(new THREE.DodecahedronGeometry(0.18 + pr() * 0.1, 0), mat);
            plant.position.set(px, 0.45, pz);
            g.add(plant);
          }
        }
      }
    }
    garden(-6.2, -3.6, 3.4, 2.8, "corn");
    garden(-6.2, 2.6, 3.4, 2.6, "flowers");
    garden(2.0, 1.8, 3.0, 2.6, "agave");
    garden(2.4, -2.6, 2.6, 2.2, "flowers");
    agave(-9.6, -7.5, 1.3);
    agave(-2.6, -8.2, 1.2);
    agave(9.2, -4.6, 1.3);

    addCloudCitadel(g, mats);

    if (!window.__ancientLoop) {
      window.__ancientLoop = true;
      startLoop();
    }
    return { worldGroup: g, terrainHeight: terrainHeight };
  }

  function ribbon(points, width, y, mat) {
    var curve = new THREE.CatmullRomCurve3(points.map(function (p) { return new THREE.Vector3(p[0], y, p[1]); }));
    var pts = curve.getPoints(60);
    var verts = [], uvs = [], idx = [];
    var dist = 0;
    for (var k = 0; k < pts.length; k++) {
      if (k > 0) dist += pts[k].distanceTo(pts[k - 1]);
      var cur = pts[k];
      var nxt = pts[Math.min(k + 1, pts.length - 1)];
      var prv = pts[Math.max(k - 1, 0)];
      var tan = new THREE.Vector3().subVectors(nxt, prv).normalize();
      var perp = new THREE.Vector3(-tan.z, 0, tan.x);
      var hw = width * (0.9 + 0.2 * Math.sin(k * 0.7)) * 0.5;
      verts.push(cur.x + perp.x * hw, y, cur.z + perp.z * hw);
      verts.push(cur.x - perp.x * hw, y, cur.z - perp.z * hw);
      uvs.push(0, dist / width, 1, dist / width);
      if (k < pts.length - 1) {
        var a = k * 2;
        idx.push(a, a + 2, a + 1, a + 1, a + 2, a + 3);
      }
    }
    var geo = new THREE.BufferGeometry();
    geo.setAttribute("position", new THREE.Float32BufferAttribute(verts, 3));
    geo.setAttribute("uv", new THREE.Float32BufferAttribute(uvs, 2));
    geo.setIndex(idx);
    geo.computeVertexNormals();
    var m = new THREE.Mesh(geo, mat);
    m.receiveShadow = true;
    return m;
  }

  function addVolcano(group, mats, x, z, r, h, smokeSpots) {
    var body = new THREE.Mesh(new THREE.ConeGeometry(r, h, 14, 4, true), mats.volcanoRock);
    body.position.set(x, h / 2 - 0.3, z);
    group.add(shadowed(body));
    var crater = new THREE.Mesh(new THREE.CylinderGeometry(r * 0.22, r * 0.22, 0.3, 14), mats.lavaPool);
    crater.position.set(x, h * 0.86, z);
    group.add(crater);
    var flow = new THREE.Mesh(new THREE.ConeGeometry(r, h, 14, 1, true, -0.25, 0.5), mats.lava);
    flow.position.set(x, h / 2 - 0.3, z);
    flow.scale.set(1.03, 1.0, 1.03);
    group.add(flow);
    var glow = new THREE.PointLight(0xff6a20, 1.4, r * 5);
    glow.position.set(x, h + 1.5, z);
    group.add(glow);
    smokeSpots.push(new THREE.Vector3(x, h * 0.9, z));
  }

  function addBroadleaf(group, mats, x, y, z, s) {
    var trunk = new THREE.Mesh(new THREE.CylinderGeometry(0.12 * s, 0.18 * s, 1.0 * s, 6), mats.timber);
    trunk.position.set(x, y + 0.5 * s, z);
    group.add(shadowed(trunk));
    var crown = new THREE.Mesh(new THREE.DodecahedronGeometry(0.85 * s, 0), mats.broadleaf);
    crown.position.set(x, y + 1.45 * s, z);
    crown.scale.set(1, 0.85, 1);
    group.add(shadowed(crown));
  }

  function addCloudCitadel(group, mats) {
    var cloud = new THREE.Group();
    cloud.position.set(-8, 13, -32);
    var r = rng(77);
    for (var i = 0; i < 28; i++) {
      var puff = new THREE.Mesh(new THREE.SphereGeometry(1.6 + r() * 2.4, 12, 10), mats.cloud);
      var a = r() * Math.PI * 2, d = r() * 7;
      puff.position.set(Math.cos(a) * d, (r() - 0.6) * 1.8, Math.sin(a) * d * 0.7);
      puff.scale.y = 0.65;
      cloud.add(puff);
    }
    // a trail of cloud steps drifting down toward the citadel
    for (var s = 0; s < 8; s++) {
      var stepPuff = new THREE.Mesh(new THREE.SphereGeometry(1.3 - s * 0.06, 10, 8), mats.cloud);
      stepPuff.position.set(4 + s * 1.6, -1.2 - s * 1.3, 4 + s * 1.4);
      stepPuff.scale.y = 0.5;
      cloud.add(stepPuff);
    }
    var deck = new THREE.Mesh(new THREE.CylinderGeometry(4.2, 4.6, 0.6, 24), mats.marble);
    deck.position.y = 1.6;
    cloud.add(shadowed(deck));
    for (var c = 0; c < 10; c++) {
      var ang = (c / 10) * Math.PI * 2;
      var col = new THREE.Mesh(new THREE.CylinderGeometry(0.18, 0.2, 2.4, 8), mats.marble);
      col.position.set(Math.cos(ang) * 3.4, 3.1, Math.sin(ang) * 3.4);
      cloud.add(col);
    }
    var ring = new THREE.Mesh(new THREE.TorusGeometry(3.4, 0.22, 6, 28), mats.templeGold);
    ring.rotation.x = Math.PI / 2;
    ring.position.y = 4.35;
    cloud.add(ring);
    var throne = new THREE.Mesh(new THREE.BoxGeometry(1.2, 2.2, 0.8), mats.templeGold);
    throne.position.set(0, 2.9, -1.6);
    cloud.add(throne);
    var pool = new THREE.Mesh(new THREE.CylinderGeometry(0.9, 0.9, 0.1, 18), mats.fountainWater);
    pool.position.set(0, 1.95, 1.2);
    cloud.add(pool);
    group.add(cloud);
    updaters.push(function (dt, t) {
      cloud.position.y = 13 + Math.sin(t * 0.5) * 0.6;
    });
  }

  window.buildAlpineValleyWorld = function (scene, mats) {
    return buildDominionWorld(scene, mats);
  };

  // ------------------------------------------------------------------ building models
  //
  // Every building has 4 looks: Lv 1-4, 5-9, 10-14 and 15+. Each model builds into an
  // empty group at local (0,0,0) and returns { top, badge: [dx, dz] } for its labels.

  function stageFor(level) {
    var l = Math.max(1, Number(level) || 1);
    if (l >= 15) return 4;
    if (l >= 10) return 3;
    if (l >= 5) return 2;
    return 1;
  }

  var MODELS = {};

  // Obsidian Keep: a carved ziggurat fortress
  MODELS.KEEP = function (g, mats, st, fx) {
    var tiers = 2 + st;          // 3..6
    var B = 4.2, STEP = 0.6, H = 2.0;
    for (var i = 0; i < tiers; i++) {
      var hw = B - i * STEP;
      box(g, hw * 2, H, hw * 2, 0, i * H + H / 2, 0, mats.stoneKeep);
      pilasters(g, hw * 2 - 0.6, H, 0, i * H + H / 2, hw, mats.stoneWall);
      box(g, hw * 2 + 0.2, 0.18, hw * 2 + 0.2, 0, (i + 1) * H, 0, mats.flagstoneTop);
      var e = hw + 0.11, ty = (i + 1) * H + 0.12;
      box(g, e * 2, 0.07, 0.07, 0, ty, e, mats.templeGold);
      box(g, 0.07, 0.07, e * 2, e, ty, 0, mats.templeGold);
      box(g, 0.07, 0.07, e * 2, -e, ty, 0, mats.templeGold);
      if (i < tiers - 1) {
        [[-1, -1], [1, -1], [-1, 1], [1, 1]].forEach(function (c) {
          var tx = c[0] * (hw - 0.4), tz = c[1] * (hw - 0.4);
          box(g, 0.9, H + 1.0, 0.9, tx, i * H + (H + 1.0) / 2, tz, mats.stoneWall);
          crenellate(g, 0.9, 0.9, tx, i * H + H + 1.0, tz, mats.stoneWall, 0.28);
        });
      }
    }
    var top = tiers * H;
    var topHw = B - (tiers - 1) * STEP;
    // grand stairway
    var steps = tiers * 5, zStart = B + 0.3, zEnd = topHw - 0.2, stepH = top / steps;
    var stepD = (zStart - zEnd) / steps;
    for (var s = 0; s < steps; s++) {
      box(g, 1.8, (s + 1) * stepH, stepD + 0.02, 0, (s + 1) * stepH / 2, zStart - (s + 0.5) * stepD, mats.flagstoneTop);
    }
    // summit gatehouse
    box(g, 2.6, 2.0, 2.6, 0, top + 1.0, 0, mats.stoneWall);
    pilasters(g, 2.2, 2.0, 0, top + 1.0, 1.3, mats.stoneKeep, 3);
    box(g, 0.8, 1.2, 0.08, 0, top + 0.6, 1.36, mats.darkOpening);
    crenellate(g, 2.6, 2.6, 0, top + 2.0, 0, mats.stoneWall, 0.35);
    // stair pylons
    [-1.5, 1.5].forEach(function (px) {
      box(g, 0.8, 3.0 + st * 0.4, 0.8, px, (3.0 + st * 0.4) / 2, zStart - 0.1, mats.stoneKeep);
      brazier(g, mats, px, 3.0 + st * 0.4, zStart - 0.1, fx.flames);
    });
    if (st >= 2) {
      // fortified wings
      [-1, 1].forEach(function (side) {
        var wx = side * (B + 1.3);
        var wh = 3.4 + st * 0.5;
        box(g, 2.4, wh, 5.2, wx, wh / 2, -0.2, mats.stoneKeep);
        pilasters(g, 2.0, wh, wx, wh / 2, 2.4, mats.stoneWall, 2);
        crenellate(g, 2.4, 5.2, wx, wh, -0.2, mats.stoneWall, 0.35);
        box(g, 1.2, wh + 1.6, 1.2, wx, (wh + 1.6) / 2, 2.0, mats.stoneWall);
        crenellate(g, 1.2, 1.2, wx, wh + 1.6, 2.0, mats.stoneWall, 0.3);
      });
    }
    if (st >= 3) {
      // rear towers
      [-1, 1].forEach(function (side) {
        var rx = side * (B - 0.6), th = top * 0.8;
        box(g, 1.6, th, 1.6, rx, th / 2, -B - 0.4, mats.stoneWall);
        crenellate(g, 1.6, 1.6, rx, th, -B - 0.4, mats.stoneWall, 0.32);
      });
    }
    if (st >= 4) {
      cone(g, 1.0, 1.4, 4, 0, top + 2.9, 0, mats.templeGold, Math.PI / 4);
      [[-1, 1], [1, 1], [-1, -1], [1, -1]].forEach(function (c) {
        brazier(g, mats, c[0] * (topHw - 0.25), top, c[1] * (topHw - 0.25), fx.flames);
      });
    }
    return { top: top + 3, badge: [0, zStart + 0.6] };
  };

  // Alliance Hall: a stepped temple with a teal frieze shrine
  MODELS.ALLIANCE_HALL = function (g, mats, st, fx) {
    var tiers = 1 + st;          // 2..5
    var B = 2.9, STEP = 0.42, H = 1.15;
    for (var i = 0; i < tiers; i++) {
      var hw = B - i * STEP;
      box(g, hw * 2, H, hw * 2, 0, i * H + H / 2, 0, mats.stoneWall);
      box(g, hw * 2 + 0.12, 0.12, hw * 2 + 0.12, 0, (i + 1) * H, 0, mats.flagstoneTop);
    }
    var top = tiers * H, topHw = B - (tiers - 1) * STEP;
    var steps = tiers * 4, zStart = B + 0.4, zEnd = topHw - 0.1, stepH = top / steps, stepD = (zStart - zEnd) / steps;
    for (var s = 0; s < steps; s++) {
      box(g, 1.4, (s + 1) * stepH, stepD + 0.02, 0, (s + 1) * stepH / 2, zStart - (s + 0.5) * stepD, mats.flagstoneTop);
    }
    box(g, 1.9, 1.5, 1.7, 0, top + 0.75, -0.1, mats.tudorWall);
    box(g, 2.1, 0.35, 1.9, 0, top + 1.6, -0.1, mats.teal);
    box(g, 2.2, 0.2, 2.0, 0, top + 1.88, -0.1, mats.stoneWall);
    box(g, 0.6, 0.9, 0.06, 0, top + 0.5, 0.78, mats.darkOpening);
    if (st >= 2) box(g, 1.3, 0.5, 1.1, 0, top + 2.2, -0.1, mats.stoneWall);
    if (st >= 3) {
      [-1.6, 1.6].forEach(function (px) {
        box(g, 0.5, 1.6, 0.5, px, 0.8, zStart + 0.3, mats.stoneKeep);
        cone(g, 0.32, 0.5, 4, px, 1.85, zStart + 0.3, mats.templeGold, Math.PI / 4);
      });
    }
    if (st >= 4) {
      box(g, 0.35, 1.4, 0.35, 0, top + 2.6, -0.1, mats.templeGold);
      brazier(g, mats, -0.8, top + 1.98, 0.5, fx.flames);
      brazier(g, mats, 0.8, top + 1.98, 0.5, fx.flames);
    }
    return { top: top + 2.4, badge: [0, zStart + 0.8] };
  };

  // War Barracks: a long colonnaded hall facing the courtyard, with ranks of troops
  MODELS.BARRACKS = function (g, mats, st, fx) {
    var L = 5 + st * 1.25, D = 3.0, H = 2.4 + st * 0.3;
    box(g, L, H, D, 0, H / 2, 0, mats.stoneKeep);
    pilasters(g, L - 0.4, H, 0, H / 2, D / 2, mats.stoneWall);
    box(g, L + 0.3, 0.2, D + 0.3, 0, H + 0.1, 0, mats.flagstoneTop);
    for (var d = 0; d < Math.floor(L / 2.2); d++) {
      box(g, 0.7, 1.2, 0.06, -L / 2 + 1.1 + d * 2.2, 0.6, D / 2 + 0.24, mats.darkOpening);
    }
    if (st >= 2) {
      [-1, 1].forEach(function (side) {
        box(g, 1.5, H + 1.4, 1.5, side * (L / 2 + 0.2), (H + 1.4) / 2, 0, mats.stoneWall);
        crenellate(g, 1.5, 1.5, side * (L / 2 + 0.2), H + 1.4, 0, mats.stoneWall, 0.3);
      });
      // ranks of soldiers drilling in front
      for (var r = 0; r < Math.min(st, 3); r++) {
        for (var c = 0; c < Math.floor(L / 0.7); c++) {
          var sx = -L / 2 + 0.5 + c * 0.7, sz = D / 2 + 1.1 + r * 0.6;
          cyl(g, 0.11, 0.15, 0.45, 5, sx, 0.23, sz, mats.cloth[0]);
          var helm = new THREE.Mesh(new THREE.SphereGeometry(0.09, 5, 4), mats.templeGold);
          helm.position.set(sx, 0.53, sz);
          g.add(helm);
        }
      }
    }
    if (st >= 3) {
      crenellate(g, L + 0.3, D + 0.3, 0, H + 0.2, 0, mats.stoneWall, 0.35);
      [-L / 4, L / 4].forEach(function (bx) {
        cyl(g, 0.04, 0.05, 1.8, 5, bx, H + 1.1, D / 2, mats.timber);
        box(g, 0.6, 0.9, 0.03, bx + 0.3, H + 1.5, D / 2, mats.cloth[0]);
      });
    }
    if (st >= 4) {
      box(g, L * 0.5, 1.4, D * 0.7, 0, H + 0.9, -0.2, mats.stoneWall);
      crenellate(g, L * 0.5, D * 0.7, 0, H + 1.6, -0.2, mats.stoneWall, 0.3);
    }
    return { top: H + 2, badge: [0, D / 2 + 1.2 + st * 0.5] };
  };

  // Ashbow Range: adobe lodge, straw targets and later a watchtower
  MODELS.ARCHERY = function (g, mats, st, fx) {
    adobeHouse(g, mats, 3.0, 2.0, 2.6, -1.2, -1.0, 0, true);
    var targets = 1 + st;
    for (var t = 0; t < targets; t++) {
      var tx = -1.8 + t * 1.0, tz = 2.2;
      var disc = new THREE.Mesh(new THREE.CylinderGeometry(0.38, 0.38, 0.14, 12), mats.thatchRoof);
      disc.rotation.x = Math.PI / 2;
      disc.position.set(tx, 0.75, tz);
      g.add(shadowed(disc));
      var bull = new THREE.Mesh(new THREE.CylinderGeometry(0.13, 0.13, 0.16, 10), mats.flowerRed);
      bull.rotation.x = Math.PI / 2;
      bull.position.set(tx, 0.75, tz + 0.02);
      g.add(bull);
      cyl(g, 0.04, 0.04, 0.8, 4, tx, 0.4, tz - 0.12, mats.timber);
    }
    if (st >= 2) adobeHouse(g, mats, 2.2, 1.7, 2.0, 1.8, -1.4, 0, false);
    if (st >= 3) {
      box(g, 1.3, 4.6, 1.3, 2.4, 2.3, 1.0, mats.stoneWall);
      box(g, 1.8, 0.2, 1.8, 2.4, 4.7, 1.0, mats.timber);
      crenellate(g, 1.8, 1.8, 2.4, 4.8, 1.0, mats.stoneWall, 0.28);
    }
    if (st >= 4) {
      cyl(g, 0.05, 0.05, 2.0, 5, -2.8, 1.0, 1.2, mats.timber);
      box(g, 0.7, 1.0, 0.03, -2.45, 1.5, 1.2, mats.cloth[1]);
    }
    return { top: 3.5 + (st >= 3 ? 1.6 : 0), badge: [0, 3.0] };
  };

  // Siege Foundry: stone forge with chimneys and siege engines
  MODELS.SIEGE = function (g, mats, st, fx) {
    box(g, 3.4, 2.2, 2.6, -0.4, 1.1, -0.6, mats.stoneKeep);
    pilasters(g, 3.0, 2.2, -0.4, 1.1, 0.7, mats.stoneWall, 3);
    box(g, 3.6, 0.2, 2.8, -0.4, 2.3, -0.6, mats.flagstoneTop);
    box(g, 0.9, 4.6, 0.9, 0.9, 2.3, -1.3, mats.stoneWall);
    fx.smoke.push(new THREE.Vector3(0.9, 4.8, -1.3));
    box(g, 1.2, 0.6, 1.0, -1.4, 0.3, 1.4, mats.stoneWall);
    box(g, 0.8, 0.2, 0.6, -1.4, 0.68, 1.4, mats.forgeEmber);
    if (st >= 2) {
      // trebuchet frame
      var tb = new THREE.Group();
      tb.position.set(1.6, 0, 1.6);
      box(tb, 1.6, 0.2, 1.0, 0, 0.1, 0, mats.timber);
      box(tb, 0.15, 1.8, 0.15, 0, 1.0, -0.4, mats.timber);
      box(tb, 0.15, 1.8, 0.15, 0, 1.0, 0.4, mats.timber);
      var arm = box(tb, 0.12, 0.12, 2.6, 0, 1.9, 0, mats.timberLight);
      arm.rotation.x = 0.5;
      box(tb, 0.5, 0.5, 0.5, 0, 1.3, -0.9, mats.stoneWall);
      g.add(tb);
    }
    if (st >= 3) {
      box(g, 0.8, 4.0, 0.8, -1.9, 2.0, -1.3, mats.stoneWall);
      fx.smoke.push(new THREE.Vector3(-1.9, 4.2, -1.3));
      crenellate(g, 3.6, 2.8, -0.4, 2.4, -0.6, mats.stoneWall, 0.3);
    }
    if (st >= 4) {
      var tb2 = new THREE.Group();
      tb2.position.set(-2.6, 0, 1.8);
      tb2.rotation.y = 0.5;
      box(tb2, 1.6, 0.2, 1.0, 0, 0.1, 0, mats.timber);
      box(tb2, 0.15, 1.8, 0.15, 0, 1.0, -0.4, mats.timber);
      box(tb2, 0.15, 1.8, 0.15, 0, 1.0, 0.4, mats.timber);
      var arm2 = box(tb2, 0.12, 0.12, 2.6, 0, 1.9, 0, mats.timberLight);
      arm2.rotation.x = -0.4;
      g.add(tb2);
    }
    return { top: 5.0, badge: [0, 2.8] };
  };

  // Dread Stables: thatched halls, paddock and horses
  MODELS.STABLES = function (g, mats, st, fx) {
    thatchHall(g, mats, 3.6, 2.2, -0.6, -0.9, 0);
    // paddock fence
    var px = 0.6, pz = 1.6, pw = 3.6, pd = 1.8;
    [[-pw / 2, -pd / 2], [pw / 2, -pd / 2], [-pw / 2, pd / 2], [pw / 2, pd / 2], [0, pd / 2], [0, -pd / 2]].forEach(function (c) {
      box(g, 0.1, 0.7, 0.1, px + c[0], 0.35, pz + c[1], mats.timber);
    });
    box(g, pw, 0.08, 0.06, px, 0.55, pz + pd / 2, mats.timberLight);
    box(g, pw, 0.08, 0.06, px, 0.55, pz - pd / 2, mats.timberLight);
    box(g, 0.06, 0.08, pd, px - pw / 2, 0.55, pz, mats.timberLight);
    box(g, 0.06, 0.08, pd, px + pw / 2, 0.55, pz, mats.timberLight);
    for (var h = 0; h < st + 1; h++) {
      var hx = px - 1.2 + h * 0.8, hz = pz + (h % 2 ? 0.3 : -0.3);
      box(g, 0.28, 0.3, 0.65, hx, 0.45, hz, mats.horse, h * 0.6);
      box(g, 0.14, 0.3, 0.2, hx, 0.68, hz + 0.28, mats.horse, h * 0.6);
    }
    if (st >= 3) thatchHall(g, mats, 2.4, 2.0, 2.4, -1.0, 0);
    if (st >= 4) {
      cyl(g, 0.05, 0.05, 2.0, 5, -2.6, 1.0, 0.4, mats.timber);
      box(g, 0.7, 1.0, 0.03, -2.25, 1.5, 0.4, mats.cloth[2]);
    }
    return { top: 3.2, badge: [0, 2.9] };
  };

  // Farmstead: crop fields that multiply, a granary and later a windmill
  MODELS.FARM = function (g, mats, st, fx) {
    var plots = [[-1.6, 1.6], [1.6, 1.6], [-1.6, 4.2], [1.6, 4.2]];
    for (var p = 0; p < st; p++) {
      var cx = plots[p][0], cz = plots[p][1];
      box(g, 2.8, 0.2, 2.2, cx, 0.1, cz, mats.gardenSoil);
      for (var r = 0; r < 3; r++) {
        for (var c = 0; c < 4; c++) {
          var stalk = new THREE.Mesh(new THREE.ConeGeometry(0.14, 0.6, 5), p % 2 ? mats.cropGreen : mats.corn);
          stalk.position.set(cx - 1.05 + c * 0.7, 0.5, cz - 0.7 + r * 0.7);
          g.add(stalk);
        }
      }
    }
    thatchHut(g, mats, -1.6, 0, -1.8, 1.0);
    if (st >= 2) thatchHut(g, mats, 1.4, 0, -2.0, 0.85);
    if (st >= 3) {
      var mill = new THREE.Group();
      mill.position.set(3.8, 0, -0.6);
      cyl(mill, 0.6, 0.85, 3.2, 8, 0, 1.6, 0, mats.tudorWall);
      cone(mill, 0.9, 1.0, 8, 0, 3.7, 0, mats.thatchRoof);
      var sails = new THREE.Group();
      sails.position.set(0, 3.0, 0.9);
      for (var k = 0; k < 4; k++) {
        var blade = new THREE.Mesh(new THREE.BoxGeometry(0.22, 1.8, 0.05), mats.awningCanvas);
        blade.position.y = 0.9;
        var holder = new THREE.Group();
        holder.rotation.z = k * Math.PI / 2;
        holder.add(blade);
        sails.add(holder);
      }
      mill.add(sails);
      g.add(mill);
      fx.spinners.push(sails);
    }
    if (st >= 4) {
      cyl(g, 0.8, 0.8, 1.6, 10, -3.6, 0.8, -0.4, mats.stoneWall);
      cone(g, 1.0, 0.8, 10, -3.6, 2.0, -0.4, mats.terracottaRoof);
    }
    return { top: 3.4, badge: [0, 2.8] };
  };

  // Timber Mill: huts and growing log piles
  MODELS.LUMBER = function (g, mats, st, fx) {
    thatchHall(g, mats, 3.0, 2.2, -0.8, -0.8, 0);
    var piles = 1 + st;
    for (var p = 0; p < piles; p++) {
      var bx = 1.6, bz = -1.6 + p * 1.0;
      for (var l = 0; l < 3; l++) {
        var log = new THREE.Mesh(new THREE.CylinderGeometry(0.2, 0.2, 1.8, 8), mats.timber);
        log.rotation.z = Math.PI / 2;
        log.position.set(bx, 0.2 + (l === 2 ? 0.34 : 0), bz + (l === 2 ? 0 : (l - 0.5) * 0.4));
        g.add(shadowed(log));
      }
    }
    if (st >= 2) {
      cyl(g, 0.45, 0.5, 0.5, 8, -1.8, 0.25, 1.6, mats.timberLight);
      box(g, 1.4, 0.3, 0.8, -0.4, 0.5, 1.8, mats.timberLight);
    }
    if (st >= 3) thatchHut(g, mats, -2.6, 0, 1.2, 0.8);
    if (st >= 4) {
      box(g, 0.2, 2.6, 0.2, 2.8, 1.3, 1.2, mats.timber);
      box(g, 1.8, 0.16, 0.16, 2.2, 2.5, 1.2, mats.timberLight);
    }
    return { top: 3.0, badge: [0, 2.8] };
  };

  // Quarry: a cliff that gets cut deeper, with a crane at higher levels
  MODELS.QUARRY = function (g, mats, st, fx) {
    var rock = new THREE.Mesh(new THREE.DodecahedronGeometry(2.6, 0), mats.rockGrey);
    rock.position.set(0.6, 1.5, -1.5);
    rock.scale.set(1.3, 1, 1);
    g.add(shadowed(rock));
    for (var t = 0; t < st; t++) {
      box(g, 3.6 - t * 0.6, 0.5, 1.0, 0.4, 0.25 + t * 0.5, -0.2 - t * 0.6, mats.flagstoneTop);
    }
    for (var b = 0; b < st + 1; b++) {
      box(g, 0.7, 0.5, 0.7, -1.8 + b * 0.8, 0.25, 1.8, mats.flagstoneTop);
    }
    if (st >= 2) {
      box(g, 1.2, 0.5, 0.9, 1.9, 0.4, 1.6, mats.timber);
      cyl(g, 0.3, 0.3, 0.12, 10, 1.4, 0.3, 2.1, mats.timber).rotation.x = Math.PI / 2;
    }
    if (st >= 3) {
      box(g, 0.2, 3.6, 0.2, -2.4, 1.8, 0.2, mats.timber);
      var jib = box(g, 2.6, 0.16, 0.16, -1.3, 3.5, 0.2, mats.timberLight);
      jib.rotation.z = -0.15;
    }
    if (st >= 4) adobeHouse(g, mats, 2.0, 1.6, 1.8, -2.6, -2.0, 0.2, true);
    return { top: 4.0, badge: [0, 2.8] };
  };

  // Gold Mint: a pillared treasury that gains a golden dome
  MODELS.MINT = function (g, mats, st, fx) {
    box(g, 4.2, 0.5, 3.6, 0, 0.25, 0, mats.flagstoneTop);
    box(g, 3.2, 2.6, 2.4, 0, 1.8, -0.3, mats.stoneKeep);
    pilasters(g, 2.8, 2.6, 0, 1.8, 0.9, mats.stoneWall, 3);
    box(g, 3.6, 0.3, 2.8, 0, 3.25, -0.3, mats.flagstoneTop);
    for (var i = 0; i < st; i++) {
      box(g, 0.5, 0.22, 0.3, -1.2 + i * 0.5, 0.62, 1.3, mats.goldIngot);
      box(g, 0.5, 0.22, 0.3, -1.0 + i * 0.5, 0.84, 1.3, mats.goldIngot);
    }
    if (st >= 2) {
      for (var c = 0; c < 4; c++) cyl(g, 0.16, 0.18, 2.6, 8, -1.2 + c * 0.8, 1.8, 1.25, mats.marble);
      box(g, 3.4, 0.3, 0.5, 0, 3.15, 1.25, mats.marble);
    }
    if (st >= 3) {
      var dome = new THREE.Mesh(new THREE.SphereGeometry(1.1, 16, 10, 0, Math.PI * 2, 0, Math.PI / 2), mats.templeGold);
      dome.position.set(0, 3.4, -0.3);
      g.add(dome);
    }
    if (st >= 4) {
      [-2.2, 2.2].forEach(function (ox) {
        box(g, 0.45, 3.2, 0.45, ox, 1.6, 1.4, mats.stoneKeep);
        cone(g, 0.3, 0.5, 4, ox, 3.45, 1.4, mats.templeGold, Math.PI / 4);
      });
    }
    return { top: 4.8, badge: [0, 2.6] };
  };

  // Where each building sits in the new layout, and which way its front faces
  var LAYOUT = {
    KEEP: { x: 3.8, z: -10.6, ry: 0 },
    ALLIANCE_HALL: { x: -6.4, z: -11.4, ry: 0 },
    BARRACKS: { x: 8.7, z: 1.4, ry: -Math.PI / 2 },
    ARCHERY: { x: -7.0, z: 9.8, ry: 0 },
    SIEGE: { x: 4.0, z: 7.6, ry: 0 },
    STABLES: { x: 7.4, z: 13.6, ry: 0 },
    FARM: { x: 18.0, z: 8.0, ry: -0.2 },
    LUMBER: { x: -7.5, z: 26.0, ry: 0.15 },
    QUARRY: { x: 20.0, z: -13.0, ry: -0.3 },
    MINT: { x: 19.0, z: -1.5, ry: -Math.PI / 2 }
  };

  var bState = null;

  function rebuildBuilding(key, stage) {
    var entry = bState && bState.entries[key];
    if (!entry || entry.stage === stage) return;
    var pick = bState.pickables;
    for (var i = pick.length - 1; i >= 0; i--) {
      if (pick[i].userData.buildingKey === key) pick.splice(i, 1);
    }
    while (entry.content.children.length) entry.content.remove(entry.content.children[0]);
    var fx = { flames: [], smoke: [], spinners: [] };
    var dims = MODELS[key](entry.content, bState.mats, stage, fx);
    entry.content.traverse(function (obj) {
      if (obj.isMesh) {
        obj.userData.buildingKey = key;
        pick.push(obj);
      }
    });
    entry.flames = fx.flames;
    entry.spinners = fx.spinners;
    var gp = entry.info.group.position;
    var ry = entry.info.group.rotation.y;
    var bx = dims.badge[0], bz = dims.badge[1];
    var wx = bx * Math.cos(ry) + bz * Math.sin(ry);
    var wz = -bx * Math.sin(ry) + bz * Math.cos(ry);
    entry.info.baseAnchor.set(gp.x + wx, 0.2, gp.z + wz);
    entry.info.peakAnchor.set(gp.x, dims.top, gp.z);
    entry.smoke = fx.smoke.map(function (v) {
      return v.clone().applyAxisAngle(new THREE.Vector3(0, 1, 0), ry).add(gp);
    });
    entry.stage = stage;
    refreshSmoke();
  }

  function refreshSmoke() {
    if (!bState || !bState.smokeOrigins) return;
    var spots = [];
    Object.keys(bState.entries).forEach(function (k) {
      (bState.entries[k].smoke || []).forEach(function (v) { spots.push(v); });
    });
    bState.smokeOrigins.forEach(function (origin, i) {
      if (spots[i]) origin.copy(spots[i]);
      else origin.set(0, -60, 0); // park unused smoke underground
    });
  }

  window.CROWNFALL_DOMINION = true;

  var origBuildings = window.buildCitadelBuildings;
  window.buildCitadelBuildings = function (scene, mats) {
    var result = origBuildings(scene, mats);
    bState = { mats: mats, pickables: result.pickables, entries: {}, smokeOrigins: result.animRefs.smokeOrigins };

    Object.keys(LAYOUT).forEach(function (key) {
      var info = result.buildings[key];
      if (!info || !MODELS[key]) return;
      var spot = LAYOUT[key];
      info.group.position.set(spot.x, terrainHeight(spot.x, spot.z), spot.z);
      info.group.rotation.y = spot.ry;
      var old = [];
      info.group.children.forEach(function (child) {
        if (!child.userData || !child.userData.nonPickable) old.push(child);
      });
      old.forEach(function (child) { info.group.remove(child); });
      var content = new THREE.Group();
      info.group.add(content);
      bState.entries[key] = { info: info, content: content, stage: 0, flames: [], spinners: [], smoke: [] };
      rebuildBuilding(key, 1);
    });

    // Guards now patrol the new wall tops
    var y = WALL.h + 0.22;
    var routes = [
      [[-9, -18], [9, -18]], [[12, -15], [12, 15]], [[-12, -15], [-12, -5]], [[5, 18], [10, 18]]
    ];
    (result.animRefs.guards || []).forEach(function (gd, i) {
      var r = routes[i % routes.length];
      gd.a.set(r[0][0], y, r[0][1]);
      gd.b.set(r[1][0], y, r[1][1]);
    });

    updaters.push(function (dt, t) {
      Object.keys(bState.entries).forEach(function (k) {
        var en = bState.entries[k];
        for (var f = 0; f < en.flames.length; f++) {
          en.flames[f].scale.set(1, 0.85 + Math.sin(t * 9 + f * 1.7) * 0.15, 1);
        }
        for (var s = 0; s < en.spinners.length; s++) en.spinners[s].rotation.z -= dt * 1.2;
      });
    });
    return result;
  };

  // Grow buildings when Kotlin sends new levels. app.js defines initCitadelDiorama,
  // and this file loads after it, so wrap it here.
  var origInit = window.initCitadelDiorama;
  window.initCitadelDiorama = function () {
    origInit();
    var api = window.CrownfallDiorama;
    if (!api || !api.updateState) return;
    var origUpdate = api.updateState;
    api.updateState = function (state) {
      origUpdate(state);
      try {
        var bmap = (state && state.buildings) || {};
        Object.keys(bmap).forEach(function (key) {
          rebuildBuilding(key, stageFor(bmap[key].level));
        });
      } catch (e) { /* never break the UI over decoration */ }
    };
  };
})();
