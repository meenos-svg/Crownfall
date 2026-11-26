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
    mats.terracottaRoof = new THREE.MeshStandardMaterial({ color: 0x9a4a2a, roughness: 0.85 });
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
      for (var i = 0; i < updaters.length; i++) updaters[i](dt, t);
      requestAnimationFrame(tick);
    }
    requestAnimationFrame(tick);
  }

  // ------------------------------------------------------------------ world additions

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
      var hw = width * (0.85 + 0.3 * Math.sin(k * 0.7)) * 0.5;
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
    var cone = new THREE.Mesh(new THREE.ConeGeometry(r, h, 14, 4, true), mats.volcanoRock);
    cone.position.set(x, h / 2 - 0.3, z);
    group.add(shadowed(cone));
    var crater = new THREE.Mesh(new THREE.CylinderGeometry(r * 0.22, r * 0.22, 0.3, 14), mats.lavaPool);
    crater.position.set(x, h * 0.86, z);
    group.add(crater);
    // glowing lava flow spilling down the front flank
    var flow = new THREE.Mesh(new THREE.ConeGeometry(r, h, 14, 1, true, -0.25, 0.5), mats.lava);
    flow.position.set(x, h / 2 - 0.3, z);
    flow.scale.set(1.03, 1.0, 1.03);
    group.add(flow);
    var glow = new THREE.PointLight(0xff6a20, 1.4, r * 5);
    glow.position.set(x, h + 1.5, z);
    group.add(glow);
    smokeSpots.push(new THREE.Vector3(x, h * 0.9, z));
  }

  function addHut(group, mats, x, y, z, s) {
    var g = new THREE.Group();
    g.position.set(x, y, z);
    var wall = new THREE.Mesh(new THREE.CylinderGeometry(0.9 * s, 1.0 * s, 1.1 * s, 10), mats.tudorWall);
    wall.position.y = 0.55 * s;
    g.add(shadowed(wall));
    var roof = new THREE.Mesh(new THREE.ConeGeometry(1.35 * s, 1.3 * s, 10), mats.thatchRoof);
    roof.position.y = 1.1 * s + 0.62 * s;
    g.add(shadowed(roof));
    var door = new THREE.Mesh(new THREE.BoxGeometry(0.4 * s, 0.6 * s, 0.1), new THREE.MeshBasicMaterial({ color: 0x1a120a }));
    door.position.set(0, 0.3 * s, 0.97 * s);
    g.add(door);
    group.add(g);
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
    cloud.position.set(9, 12, -34);
    var r = rng(77);
    for (var i = 0; i < 26; i++) {
      var puff = new THREE.Mesh(new THREE.SphereGeometry(1.6 + r() * 2.4, 12, 10), mats.cloud);
      var a = r() * Math.PI * 2, d = r() * 7;
      puff.position.set(Math.cos(a) * d, (r() - 0.6) * 1.8, Math.sin(a) * d * 0.7);
      puff.scale.y = 0.65;
      cloud.add(puff);
    }
    // marble sky-temple on top
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
    var dome = new THREE.Mesh(new THREE.SphereGeometry(1.5, 16, 10, 0, Math.PI * 2, 0, Math.PI / 2), mats.templeGold);
    dome.position.y = 1.9;
    cloud.add(dome);
    group.add(cloud);
    updaters.push(function (dt, t) {
      cloud.position.y = 12 + Math.sin(t * 0.5) * 0.6;
      cloud.rotation.y = Math.sin(t * 0.12) * 0.15;
    });
  }

  var origWorld = window.buildAlpineValleyWorld;
  window.buildAlpineValleyWorld = function (scene, mats) {
    var result = origWorld(scene, mats);
    var g = new THREE.Group();
    scene.add(g);
    var th = result.terrainHeight;
    var smokeSpots = [];

    // Warmer, hazier sky to match the painted-map look
    scene.background = new THREE.Color(0xb7a98c);
    scene.fog = new THREE.Fog(0xb8a888, 80, 300);

    // Obsidian fields and a lava river west of the walls
    var westPath = [[-26, -40], [-27.5, -26], [-25.4, -14], [-27.2, -2], [-25.2, 9], [-27.6, 20], [-26.2, 34]];
    var basaltBand = ribbon(westPath, 10, 0.05, mats.basalt);
    g.add(basaltBand);
    g.add(ribbon(westPath, 3.6, 0.1, mats.lava));
    var rr = rng(91);
    for (var i = 0; i < 26; i++) {
      var pz = -30 + i * 2.3;
      var side = i % 2 === 0 ? -1 : 1;
      var px = -26.4 + side * (2.6 + rr() * 2.2);
      var rock = new THREE.Mesh(new THREE.DodecahedronGeometry(0.4 + rr() * 0.8, 0), mats.obsidianRock);
      rock.position.set(px, 0.25, pz);
      rock.scale.set(1.2, 0.7 + rr() * 0.6, 1);
      rock.rotation.y = rr() * 6;
      g.add(shadowed(rock));
    }
    addVolcano(g, mats, -44, -30, 11, 16, smokeSpots);
    addVolcano(g, mats, -38, 2, 6, 8, smokeSpots);
    addVolcano(g, mats, -46, 22, 8, 11, smokeSpots);

    // Embers drifting up from the lava river
    var emberMat = new THREE.MeshBasicMaterial({ color: 0xffa040, transparent: true, opacity: 0.9 });
    var embers = [];
    for (var e = 0; e < 40; e++) {
      var em = new THREE.Mesh(new THREE.SphereGeometry(0.07, 5, 4), emberMat);
      g.add(em);
      embers.push({ m: em, x: -26.4 + (rr() - 0.5) * 3, z: -34 + rr() * 64, p: rr() });
    }
    // Ash plumes from the craters
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
    });

    // Thatched villages outside the east gate and along the southern road
    var huts = [
      [27.5, -6], [30.5, -3.5], [28.5, 0.5], [32, 2.5], [29.5, 5.5], [33, 7.5], [27.5, 9],
      [30.8, 11.5], [26.5, 19.5], [29.8, 21.5], [6, 24.5], [9.5, 26], [2.5, 26.5]
    ];
    huts.forEach(function (h, idx) {
      addHut(g, mats, h[0], th(h[0], h[1]), h[1], 0.8 + (idx % 3) * 0.12);
    });

    // Lake to the south-east
    var lake = new THREE.Mesh(new THREE.CircleGeometry(1, 40), mats.lakeWater);
    lake.rotation.x = -Math.PI / 2;
    lake.scale.set(8, 5.5, 1);
    lake.position.set(19, 0.12, 30);
    g.add(lake);
    var shore = new THREE.Mesh(new THREE.RingGeometry(0.98, 1.12, 40), mats.courtyardDirt);
    shore.rotation.x = -Math.PI / 2;
    shore.scale.set(8, 5.5, 1);
    shore.position.set(19, 0.11, 30);
    g.add(shore);

    // Forest to the south-west
    var fr = rng(55);
    for (var f = 0; f < 46; f++) {
      var fx = -14 + fr() * 14;
      var fz = 24 + fr() * 12;
      addBroadleaf(g, mats, fx, th(fx, fz), fz, 0.9 + fr() * 0.6);
    }

    // A ring of brown, snow-capped mountains to the north and east
    [[-6, -60, 22, 30], [14, -62, 20, 28], [30, -56, 18, 26], [48, -44, 20, 30],
     [58, -20, 16, 24], [60, 6, 16, 22], [-24, -64, 18, 24]].forEach(function (m) {
      var peak = new THREE.Mesh(new THREE.ConeGeometry(m[2], m[3], 8), mats.mountainFar);
      peak.position.set(m[0], m[3] * 0.44, m[1]);
      peak.rotation.y = m[0] * 0.13;
      g.add(peak);
      var cap = new THREE.Mesh(new THREE.ConeGeometry(m[2] * 0.33, m[3] * 0.3, 8), mats.mountainSnow);
      cap.position.set(m[0], m[3] * 0.79, m[1]);
      cap.rotation.y = m[0] * 0.13;
      g.add(cap);
    });

    addCloudCitadel(g, mats);

    // Terraced gardens inside the walls
    function gardenBed(x, z, w, d, plantMat) {
      box(g, w, 0.45, d, x, 0.22, z, mats.stoneWall);
      box(g, w - 0.3, 0.1, d - 0.3, x, 0.48, z, mats.gardenSoil);
      var pr = rng(Math.floor(x * 100 + z * 7));
      var n = Math.floor(w * d * 1.4);
      for (var p = 0; p < n; p++) {
        var px = x + (pr() - 0.5) * (w - 0.6);
        var pz = z + (pr() - 0.5) * (d - 0.6);
        var pick = pr();
        var mat = pick < 0.2 ? mats.flowerRed : pick < 0.32 ? mats.flowerGold : plantMat;
        var plant = new THREE.Mesh(new THREE.DodecahedronGeometry(0.2 + pr() * 0.15, 0), mat);
        plant.position.set(px, 0.65, pz);
        g.add(shadowed(plant));
      }
    }
    gardenBed(5.0, -11.8, 2.6, 3.4, mats.leafBright);
    gardenBed(4.6, -1.0, 2.4, 2.2, mats.leafBright);
    gardenBed(-0.8, 13.4, 3.0, 2.0, mats.agave);
    gardenBed(5.2, 13.2, 2.2, 2.2, mats.leafBright);

    if (!window.__ancientLoop) {
      window.__ancientLoop = true;
      startLoop();
    }
    return result;
  };

  // ------------------------------------------------------------------ ziggurat keep

  function buildZiggurat(group, mats, tiers, flames) {
    var B = 4.8;      // base half-width
    var STEP = 0.62;  // inset per tier
    var H = 2.3;      // tier height
    for (var i = 0; i < tiers; i++) {
      var hw = B - i * STEP;
      box(group, hw * 2, H, hw * 2, 0, i * H + H / 2, 0, mats.stoneKeep);
      box(group, hw * 2 + 0.2, 0.18, hw * 2 + 0.2, 0, (i + 1) * H, 0, mats.flagstoneTop);
      // gold trim along each terrace edge
      var ty = (i + 1) * H + 0.12, e = hw + 0.11;
      box(group, e * 2, 0.07, 0.07, 0, ty, e, mats.templeGold);
      box(group, e * 2, 0.07, 0.07, 0, ty, -e, mats.templeGold);
      box(group, 0.07, 0.07, e * 2, e, ty, 0, mats.templeGold);
      box(group, 0.07, 0.07, e * 2, -e, ty, 0, mats.templeGold);
      if (i < tiers - 1) {
        // corner towers on each terrace
        [[-1, -1], [1, -1], [-1, 1], [1, 1]].forEach(function (c) {
          var tx = c[0] * (hw - 0.35), tz = c[1] * (hw - 0.35);
          box(group, 0.8, H + 0.9, 0.8, tx, i * H + (H + 0.9) / 2, tz, mats.stoneWall);
          box(group, 0.95, 0.16, 0.95, tx, i * H + H + 0.98, tz, mats.flagstoneTop);
        });
      }
    }
    var top = tiers * H;
    var topHw = B - (tiers - 1) * STEP;

    // grand stairway up the south face
    var steps = tiers * 5;
    var zStart = B + 0.3;
    var zEnd = topHw - 0.3;
    var stepH = top / steps;
    for (var s = 0; s < steps; s++) {
      var z = zStart - (s + 0.5) * ((zStart - zEnd) / steps);
      box(group, 1.9, (s + 1) * stepH, (zStart - zEnd) / steps + 0.02, 0, (s + 1) * stepH / 2, z, mats.flagstoneTop);
    }
    // side balustrades
    [-1.15, 1.15].forEach(function (sx) {
      for (var s2 = 0; s2 < steps; s2 += 2) {
        var z2 = zStart - (s2 + 0.5) * ((zStart - zEnd) / steps);
        box(group, 0.35, (s2 + 1) * stepH + 0.35, ((zStart - zEnd) / steps) * 2, sx, ((s2 + 1) * stepH + 0.35) / 2, z2, mats.stoneWall);
      }
    });

    // summit shrine
    box(group, 2.6, 2.1, 2.6, 0, top + 1.05, 0, mats.stoneWall);
    box(group, 3.0, 0.3, 3.0, 0, top + 2.25, 0, mats.flagstoneTop);
    var door = new THREE.Mesh(new THREE.BoxGeometry(0.8, 1.3, 0.1), new THREE.MeshBasicMaterial({ color: 0x120c08 }));
    door.position.set(0, top + 0.65, 1.31);
    group.add(door);
    var cap = new THREE.Mesh(new THREE.ConeGeometry(1.25, 1.4, 4), mats.templeGold);
    cap.position.set(0, top + 3.1, 0);
    cap.rotation.y = Math.PI / 4;
    group.add(shadowed(cap));

    // braziers on the summit corners
    [[-1, 1], [1, 1], [-1, -1], [1, -1]].forEach(function (c) {
      var bx = c[0] * (topHw - 0.3), bz = c[1] * (topHw - 0.3);
      var bowl = new THREE.Mesh(new THREE.CylinderGeometry(0.32, 0.18, 0.4, 8), mats.templeGold);
      bowl.position.set(bx, top + 0.4, bz);
      group.add(bowl);
      var flame = new THREE.Mesh(new THREE.ConeGeometry(0.24, 0.7, 7), mats.brazierFlame);
      flame.position.set(bx, top + 0.95, bz);
      group.add(flame);
      flames.push(flame);
    });
    var light = new THREE.PointLight(0xffa040, 1.1, 14);
    light.position.set(0, top + 2, 1.5);
    group.add(light);

    // carved pylons flanking the stair base
    [-1.9, 1.9].forEach(function (px) {
      box(group, 0.9, 3.4, 0.9, px, 1.7, zStart - 0.2, mats.stoneKeep);
      var tip = new THREE.Mesh(new THREE.ConeGeometry(0.5, 0.7, 4), mats.templeGold);
      tip.position.set(px, 3.75, zStart - 0.2);
      tip.rotation.y = Math.PI / 4;
      group.add(tip);
    });
    return { top: top, front: zStart };
  }

  function tiersForLevel(level) {
    var l = Math.max(1, Number(level) || 1);
    if (l >= 15) return 7;
    if (l >= 10) return 6;
    if (l >= 5) return 5;
    return 4;
  }

  var keepState = null;

  var origBuildings = window.buildCitadelBuildings;
  window.buildCitadelBuildings = function (scene, mats) {
    var result = origBuildings(scene, mats);
    var keep = result.buildings.KEEP;
    if (!keep) return result;

    // Move the keep slightly forward so the larger ziggurat clears the north wall.
    keep.group.position.set(0.4, 0, -13.2);

    // Remove the old round keep (keep the selection ring and ground shadow).
    var toRemove = [];
    keep.group.children.forEach(function (child) {
      if (!child.userData || !child.userData.nonPickable) toRemove.push(child);
    });
    toRemove.forEach(function (child) { keep.group.remove(child); });
    for (var i = result.pickables.length - 1; i >= 0; i--) {
      if (result.pickables[i].userData.buildingKey === "KEEP") result.pickables.splice(i, 1);
    }

    var content = new THREE.Group();
    keep.group.add(content);
    keepState = { keep: keep, content: content, mats: mats, pickables: result.pickables, tiers: 0, flames: [] };
    rebuildKeep(4);

    updaters.push(function (dt, t) {
      for (var f = 0; f < keepState.flames.length; f++) {
        var fl = keepState.flames[f];
        var s = 0.85 + Math.sin(t * 9 + f * 1.7) * 0.15 + Math.sin(t * 23 + f) * 0.06;
        fl.scale.set(1, s, 1);
      }
    });
    return result;
  };

  function rebuildKeep(tiers) {
    if (!keepState || keepState.tiers === tiers) return;
    var st = keepState;
    // drop old meshes from the tap list
    for (var i = st.pickables.length - 1; i >= 0; i--) {
      if (st.pickables[i].userData.buildingKey === "KEEP") st.pickables.splice(i, 1);
    }
    while (st.content.children.length) st.content.remove(st.content.children[0]);
    st.flames = [];
    var dims = buildZiggurat(st.content, st.mats, tiers, st.flames);
    st.content.traverse(function (obj) {
      if (obj.isMesh) {
        obj.userData.buildingKey = "KEEP";
        st.pickables.push(obj);
      }
    });
    var gp = st.keep.group.position;
    st.keep.baseAnchor.set(gp.x, 0.2, gp.z + dims.front + 0.4);
    st.keep.peakAnchor.set(gp.x, dims.top + 3.5, gp.z);
    st.tiers = tiers;
  }

  // Hook Kotlin state updates so the ziggurat grows with Keep level.
  var origInit = window.initCitadelDiorama;
  window.initCitadelDiorama = function () {
    origInit();
    var api = window.CrownfallDiorama;
    if (!api || !api.updateState) return;
    var origUpdate = api.updateState;
    api.updateState = function (state) {
      origUpdate(state);
      try {
        var k = state && state.buildings && state.buildings.KEEP;
        if (k) rebuildKeep(tiersForLevel(k.level));
      } catch (e) { /* never break the UI over decoration */ }
    };
  };
})();
