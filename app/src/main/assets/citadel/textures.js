(function () {
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
    tex.anisotropy = 4;
    tex.needsUpdate = true;
    return tex;
  }

  // Deterministic pseudo-random helper for crisp repeatable textures
  function mulberry(seed) {
    var a = seed >>> 0;
    return function () {
      a |= 0;
      a = (a + 0x6d2b79f5) | 0;
      var t = Math.imul(a ^ (a >>> 15), 1 | a);
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }

  function createStoneMasonry(seed, baseLuma, rows, cols) {
    var colC = makeCanvas(256, 256);
    var bmpC = makeCanvas(256, 256);
    var ctx = colC.getContext("2d");
    var bctx = bmpC.getContext("2d");
    var rnd = mulberry(seed);

    ctx.fillStyle = "#3f4348";
    ctx.fillRect(0, 0, 256, 256);
    bctx.fillStyle = "#1d1d1d";
    bctx.fillRect(0, 0, 256, 256);

    var rh = 256 / rows;
    var cw = 256 / cols;
    for (var r = 0; r < rows; r++) {
      var offset = (r % 2) * (cw * 0.5);
      for (var c = -1; c <= cols; c++) {
        var x = c * cw + offset + 2;
        var y = r * rh + 2;
        var w = cw - 4;
        var h = rh - 4;
        var v = Math.floor(baseLuma + (rnd() - 0.5) * 44);
        var warm = Math.floor((rnd() - 0.45) * 14);
        ctx.fillStyle = "rgb(" + (v + warm) + "," + (v + Math.floor(warm * 0.6)) + "," + (v - 2) + ")";
        ctx.fillRect(x, y, w, h);

        // Subtle weathered speckle & highlight top edge
        ctx.fillStyle = "rgba(255,255,255,0.12)";
        ctx.fillRect(x, y, w, 2);
        ctx.fillStyle = "rgba(0,0,0,0.16)";
        ctx.fillRect(x, y + h - 2, w, 2);

        var bv = Math.floor(170 + rnd() * 75);
        bctx.fillStyle = "rgb(" + bv + "," + bv + "," + bv + ")";
        bctx.fillRect(x, y, w, h);
      }
    }
    return { color: colC, bump: bmpC };
  }

  function createShingleRoof(seed, rgbBase, rows, cols) {
    var colC = makeCanvas(256, 256);
    var bmpC = makeCanvas(256, 256);
    var ctx = colC.getContext("2d");
    var bctx = bmpC.getContext("2d");
    var rnd = mulberry(seed);

    ctx.fillStyle = "rgb(" + Math.floor(rgbBase[0] * 0.55) + "," + Math.floor(rgbBase[1] * 0.55) + "," + Math.floor(rgbBase[2] * 0.55) + ")";
    ctx.fillRect(0, 0, 256, 256);
    bctx.fillStyle = "#262626";
    bctx.fillRect(0, 0, 256, 256);

    var rh = 256 / rows;
    var cw = 256 / cols;
    for (var r = 0; r < rows; r++) {
      var off = (r % 2) * (cw * 0.5);
      for (var c = -1; c <= cols; c++) {
        var x = c * cw + off + 1;
        var y = r * rh + 1;
        var delta = (rnd() - 0.5) * 34;
        var rr = Math.max(12, Math.min(245, Math.floor(rgbBase[0] + delta)));
        var gg = Math.max(12, Math.min(245, Math.floor(rgbBase[1] + delta)));
        var bb = Math.max(12, Math.min(245, Math.floor(rgbBase[2] + delta)));
        ctx.fillStyle = "rgb(" + rr + "," + gg + "," + bb + ")";
        ctx.fillRect(x, y, cw - 2, rh - 2);
        ctx.fillStyle = "rgba(0,0,0,0.22)";
        ctx.fillRect(x, y + rh - 4, cw - 2, 2);
        var bv = Math.floor(155 + rnd() * 85);
        bctx.fillStyle = "rgb(" + bv + "," + bv + "," + bv + ")";
        bctx.fillRect(x, y, cw - 2, rh - 3);
      }
    }
    return { color: colC, bump: bmpC };
  }

  function createThatchRoof(seed) {
    var colC = makeCanvas(256, 256);
    var bmpC = makeCanvas(256, 256);
    var ctx = colC.getContext("2d");
    var bctx = bmpC.getContext("2d");
    var rnd = mulberry(seed);

    ctx.fillStyle = "#8a7042";
    ctx.fillRect(0, 0, 256, 256);
    bctx.fillStyle = "#555555";
    bctx.fillRect(0, 0, 256, 256);

    for (var i = 0; i < 420; i++) {
      var x = rnd() * 256;
      var y = rnd() * 256;
      var len = 18 + rnd() * 34;
      var v = Math.floor(145 + rnd() * 68);
      ctx.strokeStyle = "rgb(" + v + "," + Math.floor(v * 0.84) + "," + Math.floor(v * 0.52) + ")";
      ctx.lineWidth = 1.5 + rnd() * 1.5;
      ctx.beginPath();
      ctx.moveTo(x, y);
      ctx.lineTo(x + (rnd() - 0.5) * 4, y + len);
      ctx.stroke();

      var bv = Math.floor(110 + rnd() * 130);
      bctx.strokeStyle = "rgb(" + bv + "," + bv + "," + bv + ")";
      bctx.lineWidth = 2;
      bctx.beginPath();
      bctx.moveTo(x, y);
      bctx.lineTo(x, y + len);
      bctx.stroke();
    }
    // Horizontal reed lashings
    ctx.fillStyle = "rgba(72,52,28,0.38)";
    for (var row = 32; row < 256; row += 52) {
      ctx.fillRect(0, row, 256, 4);
    }
    return { color: colC, bump: bmpC };
  }

  function createTudorPlaster(seed) {
    var colC = makeCanvas(256, 256);
    var bmpC = makeCanvas(256, 256);
    var ctx = colC.getContext("2d");
    var bctx = bmpC.getContext("2d");
    var rnd = mulberry(seed);

    // Warm medieval cream plaster base
    ctx.fillStyle = "#ded5c3";
    ctx.fillRect(0, 0, 256, 256);
    bctx.fillStyle = "#b0b0b0";
    bctx.fillRect(0, 0, 256, 256);

    for (var i = 0; i < 650; i++) {
      var a = rnd() * 0.08;
      ctx.fillStyle = rnd() > 0.5 ? "rgba(110,95,70," + a + ")" : "rgba(255,250,240," + a + ")";
      ctx.fillRect(rnd() * 256, rnd() * 256, 6 + rnd() * 10, 6 + rnd() * 10);
    }

    // Dark walnut half-timber beams & diagonal braces
    var timber = "#3f2b1c";
    ctx.fillStyle = timber;
    bctx.fillStyle = "#ffffff";
    // Horizontal rails
    [0, 120, 240].forEach(function (y) {
      ctx.fillRect(0, y, 256, 16);
      bctx.fillRect(0, y, 256, 16);
    });
    // Vertical posts
    [0, 82, 166, 242].forEach(function (x) {
      ctx.fillRect(x, 0, 14, 256);
      bctx.fillRect(x, 0, 14, 256);
    });
    // Diagonal cross-braces
    ctx.strokeStyle = timber;
    ctx.lineWidth = 11;
    bctx.strokeStyle = "#ffffff";
    bctx.lineWidth = 11;
    [[10, 124, 84, 14], [174, 14, 246, 124], [88, 134, 168, 242]].forEach(function (seg) {
      ctx.beginPath(); ctx.moveTo(seg[0], seg[1]); ctx.lineTo(seg[2], seg[3]); ctx.stroke();
      bctx.beginPath(); bctx.moveTo(seg[0], seg[1]); bctx.lineTo(seg[2], seg[3]); bctx.stroke();
    });

    // Warm glowing mullioned cottage windows
    var wins = [[28, 42, 38, 48], [112, 42, 38, 48], [194, 160, 36, 48]];
    wins.forEach(function (w) {
      ctx.fillStyle = "#2b1d12";
      ctx.fillRect(w[0] - 4, w[1] - 4, w[2] + 8, w[3] + 8);
      ctx.fillStyle = "#3c4b5c";
      ctx.fillRect(w[0], w[1], w[2], w[3]);
      ctx.fillStyle = "rgba(255, 212, 130, 0.42)";
      ctx.fillRect(w[0] + 3, w[1] + 3, w[2] - 6, w[3] - 6);
      ctx.fillStyle = "#2b1d12";
      ctx.fillRect(w[0] + w[2] * 0.5 - 1.5, w[1], 3, w[3]);
      ctx.fillRect(w[0], w[1] + w[3] * 0.5 - 1.5, w[2], 3);
    });

    return { color: colC, bump: bmpC };
  }

  function createAlpineGrass(seed) {
    var colC = makeCanvas(512, 512);
    var ctx = colC.getContext("2d");
    var rnd = mulberry(seed);

    ctx.fillStyle = "#5a7239";
    ctx.fillRect(0, 0, 512, 512);

    // Organic mottled olive/emerald/sunlit ochre grass tufts matching alpine meadow photo
    for (var i = 0; i < 1600; i++) {
      var x = rnd() * 512;
      var y = rnd() * 512;
      var rad = 6 + rnd() * 24;
      var pick = rnd();
      if (pick < 0.35) ctx.fillStyle = "rgba(116, 134, 62, 0.24)";
      else if (pick < 0.72) ctx.fillStyle = "rgba(62, 88, 40, 0.24)";
      else ctx.fillStyle = "rgba(136, 114, 66, 0.17)";
      ctx.beginPath();
      ctx.arc(x, y, rad, 0, Math.PI * 2);
      ctx.fill();
    }
    return colC;
  }

  function createCourtyardDirt(seed) {
    var colC = makeCanvas(256, 256);
    var ctx = colC.getContext("2d");
    var rnd = mulberry(seed);
    ctx.fillStyle = "#8a754c";
    ctx.fillRect(0, 0, 256, 256);
    for (var i = 0; i < 800; i++) {
      var x = rnd() * 256;
      var y = rnd() * 256;
      var r = 4 + rnd() * 14;
      var p = rnd();
      if (p < 0.4) ctx.fillStyle = "rgba(164, 136, 86, 0.25)";
      else if (p < 0.72) ctx.fillStyle = "rgba(100, 116, 58, 0.24)";
      else ctx.fillStyle = "rgba(92, 76, 52, 0.22)";
      ctx.beginPath();
      ctx.arc(x, y, r, 0, Math.PI * 2);
      ctx.fill();
    }
    return colC;
  }

  function createRiverWater(seed) {
    var colC = makeCanvas(256, 256);
    var ctx = colC.getContext("2d");
    var rnd = mulberry(seed);
    var grad = ctx.createLinearGradient(0, 0, 256, 0);
    grad.addColorStop(0, "#1d9be2");
    grad.addColorStop(0.5, "#3ac1ff");
    grad.addColorStop(1, "#1a8fd4");
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, 256, 256);

    // Bright frothy alpine rapids & ripple streaks
    for (var i = 0; i < 95; i++) {
      var x = 18 + rnd() * 220;
      var y = rnd() * 256;
      var len = 18 + rnd() * 44;
      ctx.strokeStyle = rnd() > 0.3 ? "rgba(235, 250, 255, 0.55)" : "rgba(16, 115, 184, 0.45)";
      ctx.lineWidth = 2 + rnd() * 2.5;
      ctx.beginPath();
      ctx.moveTo(x, y);
      ctx.bezierCurveTo(x + 6, y + len * 0.3, x - 6, y + len * 0.7, x, y + len);
      ctx.stroke();
    }
    return colC;
  }

  window.createDioramaMaterials = function () {
    var wallStone = createStoneMasonry(101, 138, 10, 6);
    var flagStone = createStoneMasonry(202, 162, 6, 6);
    var darkStone = createStoneMasonry(303, 114, 12, 7);
    var slateRoof = createShingleRoof(404, [66, 118, 144], 12, 10);
    var turretSlate = createShingleRoof(455, [86, 98, 130], 14, 12);
    var terraRoof = createShingleRoof(505, [158, 92, 60], 12, 10);
    var thatch = createThatchRoof(606);
    var tudor = createTudorPlaster(707);
    var grassC = createAlpineGrass(808);
    var dirtC = createCourtyardDirt(909);
    var riverC = createRiverWater(1010);

    var riverTex = toTex(riverC, 1, 8, true);

    return {
      riverTexture: riverTex,
      stoneWall: new THREE.MeshStandardMaterial({
        map: toTex(wallStone.color, 2, 1.5, true),
        bumpMap: toTex(wallStone.bump, 2, 1.5, false),
        bumpScale: 0.16,
        roughness: 0.88,
        metalness: 0.04
      }),
      stoneKeep: new THREE.MeshStandardMaterial({
        map: toTex(darkStone.color, 3, 2.5, true),
        bumpMap: toTex(darkStone.bump, 3, 2.5, false),
        bumpScale: 0.2,
        roughness: 0.9,
        metalness: 0.05
      }),
      flagstoneTop: new THREE.MeshStandardMaterial({
        map: toTex(flagStone.color, 2, 2, true),
        bumpMap: toTex(flagStone.bump, 2, 2, false),
        bumpScale: 0.12,
        roughness: 0.85,
        metalness: 0.04
      }),
      slateRoof: new THREE.MeshStandardMaterial({
        map: toTex(slateRoof.color, 2, 2, true),
        bumpMap: toTex(slateRoof.bump, 2, 2, false),
        bumpScale: 0.12,
        roughness: 0.65,
        metalness: 0.12
      }),
      turretRoof: new THREE.MeshStandardMaterial({
        map: toTex(turretSlate.color, 2, 1.5, true),
        bumpMap: toTex(turretSlate.bump, 2, 1.5, false),
        bumpScale: 0.14,
        roughness: 0.72,
        metalness: 0.1
      }),
      terracottaRoof: new THREE.MeshStandardMaterial({
        map: toTex(terraRoof.color, 2, 2, true),
        bumpMap: toTex(terraRoof.bump, 2, 2, false),
        bumpScale: 0.13,
        roughness: 0.76,
        metalness: 0.05
      }),
      thatchRoof: new THREE.MeshStandardMaterial({
        map: toTex(thatch.color, 2, 2, true),
        bumpMap: toTex(thatch.bump, 2, 2, false),
        bumpScale: 0.2,
        roughness: 0.95,
        metalness: 0.0
      }),
      tudorWall: new THREE.MeshStandardMaterial({
        map: toTex(tudor.color, 1.5, 1, true),
        bumpMap: toTex(tudor.bump, 1.5, 1, false),
        bumpScale: 0.1,
        roughness: 0.84,
        metalness: 0.02
      }),
      timber: new THREE.MeshStandardMaterial({
        color: 0x5c3c24,
        roughness: 0.88,
        metalness: 0.03
      }),
      timberLight: new THREE.MeshStandardMaterial({
        color: 0x875f3b,
        roughness: 0.84,
        metalness: 0.02
      }),
      valleyGrass: new THREE.MeshStandardMaterial({
        map: toTex(grassC, 9, 9, true),
        roughness: 0.94,
        metalness: 0.0
      }),
      courtyardDirt: new THREE.MeshStandardMaterial({
        map: toTex(dirtC, 5, 5, true),
        roughness: 0.95,
        metalness: 0.0
      }),
      riverWater: new THREE.MeshStandardMaterial({
        map: riverTex,
        roughness: 0.18,
        metalness: 0.12,
        emissive: 0x0c4266,
        emissiveIntensity: 0.28
      }),
      fountainWater: new THREE.MeshStandardMaterial({
        color: 0x3ec8ff,
        roughness: 0.15,
        metalness: 0.1,
        emissive: 0x1680b8,
        emissiveIntensity: 0.35
      }),
      awningRed: new THREE.MeshStandardMaterial({ color: 0xc82b2b, roughness: 0.82 }),
      awningYellow: new THREE.MeshStandardMaterial({ color: 0xe6be32, roughness: 0.8 }),
      awningCanvas: new THREE.MeshStandardMaterial({ color: 0xbcb6a6, roughness: 0.88 }),
      wheatGold: new THREE.MeshStandardMaterial({ color: 0xd9ad3c, roughness: 0.88 }),
      goldIngot: new THREE.MeshStandardMaterial({ color: 0xf4c842, roughness: 0.28, metalness: 0.82, emissive: 0x6b4b08, emissiveIntensity: 0.25 }),
      forgeEmber: new THREE.MeshStandardMaterial({ color: 0xff6b24, emissive: 0xff4500, emissiveIntensity: 1.2, roughness: 0.5 }),
      pineDark: new THREE.MeshStandardMaterial({ color: 0x26422c, roughness: 0.9 }),
      pineMid: new THREE.MeshStandardMaterial({ color: 0x365c39, roughness: 0.88 }),
      rockGrey: new THREE.MeshStandardMaterial({ color: 0x697078, roughness: 0.92 }),
      mountainFar: new THREE.MeshStandardMaterial({ color: 0x76899a, roughness: 0.95 }),
      mountainSnow: new THREE.MeshStandardMaterial({ color: 0xdbe6ef, roughness: 0.85 })
    };
  };
})();
