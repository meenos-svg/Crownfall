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
    tex.anisotropy = 8;
    tex.needsUpdate = true;
    return tex;
  }

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

  // Weathered dark granite & limestone ashlar masonry with recessed mortar grooves + roughness map
  function createStoneMasonry(seed, baseLuma, rows, cols, mossAmount) {
    var colC = makeCanvas(512, 512);
    var bmpC = makeCanvas(512, 512);
    var rghC = makeCanvas(512, 512);
    var ctx = colC.getContext("2d");
    var bctx = bmpC.getContext("2d");
    var rctx = rghC.getContext("2d");
    var rnd = mulberry(seed);

    // Deep recessed dark mortar joints
    ctx.fillStyle = "#1d2024";
    ctx.fillRect(0, 0, 512, 512);
    bctx.fillStyle = "#121212";
    bctx.fillRect(0, 0, 512, 512);
    rctx.fillStyle = "#f2f2f2";
    rctx.fillRect(0, 0, 512, 512);

    var rh = 512 / rows;
    var cw = 512 / cols;
    for (var r = 0; r < rows; r++) {
      var offset = (r % 2) * (cw * 0.5) + (rnd() - 0.5) * 6;
      for (var c = -1; c <= cols + 1; c++) {
        var jitterW = (rnd() - 0.5) * 8;
        var x = c * cw + offset + 3;
        var y = r * rh + 3;
        var w = Math.max(12, cw - 6 + jitterW);
        var h = rh - 6;

        // Rich weathered granite / warm limestone tonal variation
        var v = Math.floor(baseLuma + (rnd() - 0.5) * 46);
        var warm = Math.floor((rnd() - 0.42) * 18);
        var cool = Math.floor((rnd() - 0.5) * 10);
        var rr = Math.max(24, Math.min(200, v + warm));
        var gg = Math.max(24, Math.min(200, v + Math.floor(warm * 0.65)));
        var bb = Math.max(24, Math.min(205, v + cool));

        ctx.fillStyle = "rgb(" + rr + "," + gg + "," + bb + ")";
        ctx.fillRect(x, y, w, h);

        // Chiseled stone grain & weathered micro-pitting
        for (var s = 0; s < 14; s++) {
          var sx = x + rnd() * w;
          var sy = y + rnd() * h;
          ctx.fillStyle = rnd() > 0.5 ? "rgba(0,0,0,0.16)" : "rgba(255,248,236,0.09)";
          ctx.fillRect(sx, sy, 3 + rnd() * 9, 2 + rnd() * 5);
        }

        // Subtle moss / lichen weathering in damp joints
        if (mossAmount && rnd() < mossAmount) {
          ctx.fillStyle = "rgba(44, 62, 34, 0.34)";
          ctx.fillRect(x, y + h - 7, w * (0.4 + rnd() * 0.55), 7);
        }

        // Crisp bevel highlights & recessed bottom shadow edge
        ctx.fillStyle = "rgba(255,246,232,0.15)";
        ctx.fillRect(x, y, w, 3);
        ctx.fillStyle = "rgba(0,0,0,0.34)";
        ctx.fillRect(x, y + h - 4, w, 4);

        // Bump height & roughness contrast (worn granite faces slightly smoother than gritty mortar)
        var bv = Math.floor(165 + rnd() * 82);
        bctx.fillStyle = "rgb(" + bv + "," + bv + "," + bv + ")";
        bctx.fillRect(x, y, w, h);
        bctx.fillStyle = "rgba(255,255,255,0.22)";
        bctx.fillRect(x + 2, y + 2, w - 4, 3);

        var rv = Math.floor(175 + rnd() * 55);
        rctx.fillStyle = "rgb(" + rv + "," + rv + "," + rv + ")";
        rctx.fillRect(x, y, w, h);
      }
    }
    return { color: colC, bump: bmpC, rough: rghC };
  }

  // Deep weathered slate-blue & burnt terracotta shingle roofs with overlapping shadow courses
  function createShingleRoof(seed, rgbBase, rows, cols, sheenLow) {
    var colC = makeCanvas(512, 512);
    var bmpC = makeCanvas(512, 512);
    var rghC = makeCanvas(512, 512);
    var ctx = colC.getContext("2d");
    var bctx = bmpC.getContext("2d");
    var rctx = rghC.getContext("2d");
    var rnd = mulberry(seed);

    ctx.fillStyle = "rgb(" + Math.floor(rgbBase[0] * 0.35) + "," + Math.floor(rgbBase[1] * 0.35) + "," + Math.floor(rgbBase[2] * 0.35) + ")";
    ctx.fillRect(0, 0, 512, 512);
    bctx.fillStyle = "#181818";
    bctx.fillRect(0, 0, 512, 512);
    rctx.fillStyle = "#e0e0e0";
    rctx.fillRect(0, 0, 512, 512);

    var rh = 512 / rows;
    var cw = 512 / cols;
    for (var r = 0; r < rows; r++) {
      var off = (r % 2) * (cw * 0.5);
      for (var c = -1; c <= cols + 1; c++) {
        var x = c * cw + off + 1.5;
        var y = r * rh + 1.5;
        var w = cw - 3;
        var h = rh - 3;
        var delta = (rnd() - 0.5) * 34;
        var rr = Math.max(10, Math.min(220, Math.floor(rgbBase[0] + delta)));
        var gg = Math.max(10, Math.min(220, Math.floor(rgbBase[1] + delta)));
        var bb = Math.max(10, Math.min(225, Math.floor(rgbBase[2] + delta)));

        // Subtle vertical gradient per shingle for realistic overlap depth
        var grad = ctx.createLinearGradient(x, y, x, y + h);
        grad.addColorStop(0, "rgb(" + Math.max(8, rr - 14) + "," + Math.max(8, gg - 14) + "," + Math.max(8, bb - 14) + ")");
        grad.addColorStop(0.7, "rgb(" + rr + "," + gg + "," + bb + ")");
        grad.addColorStop(1, "rgb(" + Math.min(240, rr + 14) + "," + Math.min(240, gg + 12) + "," + Math.min(240, bb + 10) + ")");
        ctx.fillStyle = grad;
        ctx.fillRect(x, y, w, h);

        // Dark cast-shadow lip underneath every shingle course
        ctx.fillStyle = "rgba(0,0,0,0.42)";
        ctx.fillRect(x, y + h - 4, w, 4);

        var bv = Math.floor(150 + rnd() * 95);
        bctx.fillStyle = "rgb(" + bv + "," + bv + "," + bv + ")";
        bctx.fillRect(x, y, w, h - 3);

        var rv = Math.floor((sheenLow || 135) + rnd() * 75);
        rctx.fillStyle = "rgb(" + rv + "," + rv + "," + rv + ")";
        rctx.fillRect(x, y, w, h);
      }
    }
    return { color: colC, bump: bmpC, rough: rghC };
  }

  // Aged oak wood-grain plank texture for beams, doors, bridges, carts & stalls
  function createWoodGrain(seed, rgbBase) {
    var colC = makeCanvas(256, 256);
    var bmpC = makeCanvas(256, 256);
    var ctx = colC.getContext("2d");
    var bctx = bmpC.getContext("2d");
    var rnd = mulberry(seed);

    ctx.fillStyle = "rgb(" + rgbBase[0] + "," + rgbBase[1] + "," + rgbBase[2] + ")";
    ctx.fillRect(0, 0, 256, 256);
    bctx.fillStyle = "#888888";
    bctx.fillRect(0, 0, 256, 256);

    // Plank seams
    for (var py = 0; py < 256; py += 64) {
      ctx.fillStyle = "rgba(18, 10, 4, 0.55)";
      ctx.fillRect(0, py, 256, 3);
      bctx.fillStyle = "#222222";
      bctx.fillRect(0, py, 256, 3);
    }

    // Fine organic wood fibers
    for (var i = 0; i < 360; i++) {
      var y = rnd() * 256;
      var shade = (rnd() - 0.5) * 34;
      ctx.strokeStyle = "rgba(" +
        Math.max(10, Math.min(220, Math.floor(rgbBase[0] + shade))) + "," +
        Math.max(8, Math.min(200, Math.floor(rgbBase[1] + shade * 0.8))) + "," +
        Math.max(6, Math.min(180, Math.floor(rgbBase[2] + shade * 0.6))) + ", 0.45)";
      ctx.lineWidth = 1 + rnd() * 1.8;
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.bezierCurveTo(85, y + (rnd() - 0.5) * 5, 170, y + (rnd() - 0.5) * 5, 256, y);
      ctx.stroke();

      var bv = Math.floor(110 + rnd() * 115);
      bctx.strokeStyle = "rgb(" + bv + "," + bv + "," + bv + ")";
      bctx.lineWidth = 1.4;
      bctx.beginPath();
      bctx.moveTo(0, y);
      bctx.lineTo(256, y);
      bctx.stroke();
    }
    return { color: colC, bump: bmpC };
  }

  // Deep golden-brown layered reed thatch roof
  function createThatchRoof(seed) {
    var colC = makeCanvas(256, 256);
    var bmpC = makeCanvas(256, 256);
    var ctx = colC.getContext("2d");
    var bctx = bmpC.getContext("2d");
    var rnd = mulberry(seed);

    ctx.fillStyle = "#5c4724";
    ctx.fillRect(0, 0, 256, 256);
    bctx.fillStyle = "#404040";
    bctx.fillRect(0, 0, 256, 256);

    for (var i = 0; i < 580; i++) {
      var x = rnd() * 256;
      var y = rnd() * 256;
      var len = 18 + rnd() * 34;
      var v = Math.floor(112 + rnd() * 76);
      ctx.strokeStyle = "rgb(" + v + "," + Math.floor(v * 0.8) + "," + Math.floor(v * 0.44) + ")";
      ctx.lineWidth = 1.4 + rnd() * 1.6;
      ctx.beginPath();
      ctx.moveTo(x, y);
      ctx.lineTo(x + (rnd() - 0.5) * 4, y + len);
      ctx.stroke();

      var bv = Math.floor(90 + rnd() * 150);
      bctx.strokeStyle = "rgb(" + bv + "," + bv + "," + bv + ")";
      bctx.lineWidth = 2;
      bctx.beginPath();
      bctx.moveTo(x, y);
      bctx.lineTo(x, y + len);
      bctx.stroke();
    }
    ctx.fillStyle = "rgba(42,28,12,0.52)";
    for (var row = 28; row < 256; row += 48) {
      ctx.fillRect(0, row, 256, 5);
    }
    return { color: colC, bump: bmpC };
  }

  // Warm aged parchment Tudor plaster with rich wood-grain aged oak beams (no flat white grid!)
  function createTudorPlaster(seed) {
    var colC = makeCanvas(512, 512);
    var bmpC = makeCanvas(512, 512);
    var rghC = makeCanvas(512, 512);
    var ctx = colC.getContext("2d");
    var bctx = bmpC.getContext("2d");
    var rctx = rghC.getContext("2d");
    var rnd = mulberry(seed);

    // Warm weathered wattle-and-daub plaster
    ctx.fillStyle = "#c8ba9e";
    ctx.fillRect(0, 0, 512, 512);
    bctx.fillStyle = "#989898";
    bctx.fillRect(0, 0, 512, 512);
    rctx.fillStyle = "#e6e6e6";
    rctx.fillRect(0, 0, 512, 512);

    // Organic aged plaster stain & trowel mottling
    for (var i = 0; i < 1400; i++) {
      var a = 0.03 + rnd() * 0.09;
      ctx.fillStyle = rnd() > 0.45 ? "rgba(86,68,45," + a + ")" : "rgba(240,228,204," + a + ")";
      ctx.fillRect(rnd() * 512, rnd() * 512, 8 + rnd() * 20, 8 + rnd() * 18);
    }

    function drawAgedOakBeam(x, y, w, h, vertical) {
      // Soft contact shadow around timber framing
      ctx.fillStyle = "rgba(22, 14, 7, 0.36)";
      ctx.fillRect(x + 3, y + 4, w + 2, h + 2);

      // Base aged walnut/oak hue
      ctx.fillStyle = "#362213";
      ctx.fillRect(x, y, w, h);
      bctx.fillStyle = "#ffffff";
      bctx.fillRect(x, y, w, h);
      rctx.fillStyle = "#b0b0b0";
      rctx.fillRect(x, y, w, h);

      // Wood grain streaks along beam direction
      var lines = vertical ? 10 : 10;
      for (var k = 0; k < lines; k++) {
        ctx.fillStyle = k % 2 === 0 ? "rgba(96, 62, 34, 0.38)" : "rgba(20, 11, 5, 0.4)";
        if (vertical) {
          ctx.fillRect(x + (k / lines) * w, y, 2, h);
        } else {
          ctx.fillRect(x, y + (k / lines) * h, w, 2);
        }
      }
      // Subtle highlight edge
      ctx.fillStyle = "rgba(155, 112, 70, 0.28)";
      if (vertical) ctx.fillRect(x, y, 2, h);
      else ctx.fillRect(x, y, w, 2);
    }

    // Horizontal rails & vertical posts
    [0, 238, 480].forEach(function (y) { drawAgedOakBeam(0, y, 512, 32, false); });
    [0, 162, 330, 484].forEach(function (x) { drawAgedOakBeam(x, 0, 28, 512, true); });

    // Diagonal oak cross-braces with dark iron peg dots
    ctx.strokeStyle = "#362213";
    ctx.lineWidth = 22;
    bctx.strokeStyle = "#f4f4f4";
    bctx.lineWidth = 22;
    [[20, 244, 168, 28], [348, 28, 492, 244], [176, 266, 336, 484]].forEach(function (seg) {
      ctx.beginPath(); ctx.moveTo(seg[0], seg[1]); ctx.lineTo(seg[2], seg[3]); ctx.stroke();
      bctx.beginPath(); bctx.moveTo(seg[0], seg[1]); bctx.lineTo(seg[2], seg[3]); bctx.stroke();
    });

    // Warm recessed medieval leaded-glass windows with amber interior hearth glow
    var wins = [[56, 84, 76, 98], [224, 84, 76, 98], [386, 314, 74, 98]];
    wins.forEach(function (w) {
      ctx.fillStyle = "#22150b";
      ctx.fillRect(w[0] - 8, w[1] - 8, w[2] + 16, w[3] + 16);
      ctx.fillStyle = "#263342";
      ctx.fillRect(w[0], w[1], w[2], w[3]);
      var wg = ctx.createRadialGradient(
        w[0] + w[2] * 0.5, w[1] + w[3] * 0.55, 6,
        w[0] + w[2] * 0.5, w[1] + w[3] * 0.55, w[2] * 0.72
      );
      wg.addColorStop(0, "rgba(255, 198, 96, 0.78)");
      wg.addColorStop(1, "rgba(74, 58, 36, 0.25)");
      ctx.fillStyle = wg;
      ctx.fillRect(w[0] + 4, w[1] + 4, w[2] - 8, w[3] - 8);
      ctx.fillStyle = "#22150b";
      ctx.fillRect(w[0] + w[2] * 0.5 - 3, w[1], 6, w[3]);
      ctx.fillRect(w[0], w[1] + w[3] * 0.5 - 3, w[2], 6);
      // Window sill
      ctx.fillStyle = "#4d3520";
      ctx.fillRect(w[0] - 10, w[1] + w[3] + 4, w[2] + 20, 9);
      rctx.fillStyle = "#555555";
      rctx.fillRect(w[0], w[1], w[2], w[3]);
    });

    return { color: colC, bump: bmpC, rough: rghC };
  }

  // Deep organic alpine meadow terrain with dark grass tufts, mossy patches & pebbles
  function createAlpineGrass(seed) {
    var colC = makeCanvas(512, 512);
    var bmpC = makeCanvas(512, 512);
    var ctx = colC.getContext("2d");
    var bctx = bmpC.getContext("2d");
    var rnd = mulberry(seed);

    ctx.fillStyle = "#3f5628";
    ctx.fillRect(0, 0, 512, 512);
    bctx.fillStyle = "#7a7a7a";
    bctx.fillRect(0, 0, 512, 512);

    // Rich mottled soil, deep forest moss, and warm sunlit grass patches
    for (var i = 0; i < 2200; i++) {
      var x = rnd() * 512;
      var y = rnd() * 512;
      var rad = 4 + rnd() * 22;
      var pick = rnd();
      if (pick < 0.34) ctx.fillStyle = "rgba(38, 59, 24, 0.32)";
      else if (pick < 0.68) ctx.fillStyle = "rgba(86, 106, 44, 0.25)";
      else if (pick < 0.88) ctx.fillStyle = "rgba(104, 86, 48, 0.2)";
      else ctx.fillStyle = "rgba(24, 38, 18, 0.32)";
      ctx.beginPath();
      ctx.arc(x, y, rad, 0, Math.PI * 2);
      ctx.fill();
    }

    // Crisp individual dark grass tufts & micro blades for close-up detail
    for (var t = 0; t < 1800; t++) {
      var tx = rnd() * 512;
      var ty = rnd() * 512;
      ctx.strokeStyle = rnd() > 0.45 ? "rgba(28, 46, 18, 0.55)" : "rgba(116, 134, 62, 0.38)";
      ctx.lineWidth = 1.2;
      ctx.beginPath();
      ctx.moveTo(tx, ty);
      ctx.lineTo(tx + (rnd() - 0.5) * 6, ty - 4 - rnd() * 5);
      ctx.stroke();

      var bv = Math.floor(110 + rnd() * 110);
      bctx.fillStyle = "rgb(" + bv + "," + bv + "," + bv + ")";
      bctx.fillRect(tx, ty, 3, 3);
    }
    return { color: colC, bump: bmpC };
  }

  // Packed ochre-earth courtyard dirt with worn stone pebbles & grass fringes
  function createCourtyardDirt(seed) {
    var colC = makeCanvas(512, 512);
    var bmpC = makeCanvas(512, 512);
    var ctx = colC.getContext("2d");
    var bctx = bmpC.getContext("2d");
    var rnd = mulberry(seed);

    ctx.fillStyle = "#6c5939";
    ctx.fillRect(0, 0, 512, 512);
    bctx.fillStyle = "#787878";
    bctx.fillRect(0, 0, 512, 512);

    for (var i = 0; i < 1800; i++) {
      var x = rnd() * 512;
      var y = rnd() * 512;
      var r = 3 + rnd() * 16;
      var p = rnd();
      if (p < 0.38) ctx.fillStyle = "rgba(134, 108, 66, 0.25)";
      else if (p < 0.68) ctx.fillStyle = "rgba(64, 52, 34, 0.28)";
      else if (p < 0.86) ctx.fillStyle = "rgba(66, 82, 38, 0.24)";
      else ctx.fillStyle = "rgba(160, 136, 94, 0.18)";
      ctx.beginPath();
      ctx.arc(x, y, r, 0, Math.PI * 2);
      ctx.fill();
    }

    // Scattered worn pebbles & grit
    for (var k = 0; k < 650; k++) {
      var px = rnd() * 512;
      var py = rnd() * 512;
      var pr = 1.5 + rnd() * 3.2;
      ctx.fillStyle = rnd() > 0.5 ? "rgba(92,88,82,0.52)" : "rgba(42,36,28,0.45)";
      ctx.beginPath();
      ctx.arc(px, py, pr, 0, Math.PI * 2);
      ctx.fill();

      bctx.fillStyle = rnd() > 0.5 ? "#d8d8d8" : "#3a3a3a";
      bctx.fillRect(px, py, pr * 1.4, pr * 1.4);
    }
    return { color: colC, bump: bmpC };
  }

  // Worn medieval cobblestone plaza texture for Keep forecourt & Fountain plaza
  function createCobblePlaza(seed) {
    var colC = makeCanvas(512, 512);
    var bmpC = makeCanvas(512, 512);
    var rghC = makeCanvas(512, 512);
    var ctx = colC.getContext("2d");
    var bctx = bmpC.getContext("2d");
    var rctx = rghC.getContext("2d");
    var rnd = mulberry(seed);

    ctx.fillStyle = "#322e28";
    ctx.fillRect(0, 0, 512, 512);
    bctx.fillStyle = "#181818";
    bctx.fillRect(0, 0, 512, 512);
    rctx.fillStyle = "#efefef";
    rctx.fillRect(0, 0, 512, 512);

    var rows = 16;
    var cols = 14;
    var rh = 512 / rows;
    var cw = 512 / cols;
    for (var r = 0; r < rows; r++) {
      var off = (r % 2) * (cw * 0.5);
      for (var c = -1; c <= cols + 1; c++) {
        var cx = c * cw + off + cw * 0.5 + (rnd() - 0.5) * 4;
        var cy = r * rh + rh * 0.5 + (rnd() - 0.5) * 3;
        var rx = cw * 0.42;
        var ry = rh * 0.41;
        var v = Math.floor(86 + (rnd() - 0.45) * 42);
        var warm = Math.floor((rnd() - 0.4) * 14);
        ctx.fillStyle = "rgb(" + (v + warm) + "," + (v + Math.floor(warm * 0.6)) + "," + (v - 2) + ")";
        ctx.beginPath();
        ctx.ellipse(cx, cy, rx, ry, (rnd() - 0.5) * 0.2, 0, Math.PI * 2);
        ctx.fill();

        // Top-left worn highlight
        ctx.fillStyle = "rgba(255, 245, 224, 0.12)";
        ctx.beginPath();
        ctx.ellipse(cx - 2, cy - 2, rx * 0.6, ry * 0.45, 0, 0, Math.PI * 2);
        ctx.fill();

        var bv = Math.floor(175 + rnd() * 70);
        bctx.fillStyle = "rgb(" + bv + "," + bv + "," + bv + ")";
        bctx.beginPath();
        bctx.ellipse(cx, cy, rx * 0.9, ry * 0.9, 0, 0, Math.PI * 2);
        bctx.fill();

        var rv = Math.floor(145 + rnd() * 60);
        rctx.fillStyle = "rgb(" + rv + "," + rv + "," + rv + ")";
        rctx.beginPath();
        rctx.ellipse(cx, cy, rx * 0.9, ry * 0.9, 0, 0, Math.PI * 2);
        rctx.fill();
      }
    }
    return { color: colC, bump: bmpC, rough: rghC };
  }

  function createRiverWater(seed) {
    var colC = makeCanvas(256, 256);
    var ctx = colC.getContext("2d");
    var rnd = mulberry(seed);
    var grad = ctx.createLinearGradient(0, 0, 256, 0);
    grad.addColorStop(0, "#126fa8");
    grad.addColorStop(0.5, "#289ee0");
    grad.addColorStop(1, "#11669c");
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, 256, 256);

    for (var i = 0; i < 110; i++) {
      var x = 16 + rnd() * 224;
      var y = rnd() * 256;
      var len = 18 + rnd() * 44;
      ctx.strokeStyle = rnd() > 0.35 ? "rgba(225, 246, 255, 0.52)" : "rgba(10, 74, 126, 0.48)";
      ctx.lineWidth = 1.8 + rnd() * 2.2;
      ctx.beginPath();
      ctx.moveTo(x, y);
      ctx.bezierCurveTo(x + 6, y + len * 0.3, x - 6, y + len * 0.7, x, y + len);
      ctx.stroke();
    }
    return colC;
  }

  // Soft radial ambient-occlusion contact shadow texture for grounding buildings, walls & props
  function createContactAoTexture(isRect) {
    var c = makeCanvas(128, 128);
    var ctx = c.getContext("2d");
    ctx.clearRect(0, 0, 128, 128);
    if (isRect) {
      var gx = ctx.createLinearGradient(0, 0, 128, 0);
      gx.addColorStop(0.0, "rgba(8, 10, 14, 0.0)");
      gx.addColorStop(0.22, "rgba(8, 10, 14, 0.62)");
      gx.addColorStop(0.5, "rgba(8, 10, 14, 0.74)");
      gx.addColorStop(0.78, "rgba(8, 10, 14, 0.62)");
      gx.addColorStop(1.0, "rgba(8, 10, 14, 0.0)");
      ctx.fillStyle = gx;
      ctx.fillRect(0, 0, 128, 128);
      // Soft feather at top/bottom ends
      ctx.globalCompositeOperation = "destination-in";
      var gy = ctx.createLinearGradient(0, 0, 0, 128);
      gy.addColorStop(0.0, "rgba(0,0,0,0)");
      gy.addColorStop(0.14, "rgba(0,0,0,1)");
      gy.addColorStop(0.86, "rgba(0,0,0,1)");
      gy.addColorStop(1.0, "rgba(0,0,0,0)");
      ctx.fillStyle = gy;
      ctx.fillRect(0, 0, 128, 128);
    } else {
      var rad = ctx.createRadialGradient(64, 64, 14, 64, 64, 62);
      rad.addColorStop(0.0, "rgba(8, 10, 14, 0.74)");
      rad.addColorStop(0.55, "rgba(8, 10, 14, 0.44)");
      rad.addColorStop(0.82, "rgba(8, 10, 14, 0.16)");
      rad.addColorStop(1.0, "rgba(8, 10, 14, 0.0)");
      ctx.fillStyle = rad;
      ctx.fillRect(0, 0, 128, 128);
    }
    var tex = new THREE.CanvasTexture(c);
    tex.needsUpdate = true;
    return tex;
  }

  window.createDioramaMaterials = function () {
    // Rich weathered dark granite stone & aged oak palette
    var wallStone = createStoneMasonry(101, 98, 11, 7, 0.24);
    var flagStone = createStoneMasonry(202, 118, 7, 7, 0.12);
    var darkStone = createStoneMasonry(303, 76, 13, 8, 0.28);
    var slateRoof = createShingleRoof(404, [42, 82, 108], 13, 11, 118);
    var turretSlate = createShingleRoof(455, [52, 64, 96], 15, 12, 125);
    var terraRoof = createShingleRoof(505, [132, 64, 38], 13, 11, 155);
    var thatch = createThatchRoof(606);
    var tudor = createTudorPlaster(707);
    var oakDark = createWoodGrain(733, [70, 44, 24]);
    var oakLight = createWoodGrain(744, [110, 74, 42]);
    var grass = createAlpineGrass(808);
    var dirt = createCourtyardDirt(909);
    var cobble = createCobblePlaza(955);
    var riverC = createRiverWater(1010);

    var riverTex = toTex(riverC, 1, 8, true);

    return {
      riverTexture: riverTex,
      stoneWall: new THREE.MeshStandardMaterial({
        map: toTex(wallStone.color, 2, 1.5, true),
        bumpMap: toTex(wallStone.bump, 2, 1.5, false),
        roughnessMap: toTex(wallStone.rough, 2, 1.5, false),
        bumpScale: 0.28,
        roughness: 0.92,
        metalness: 0.06
      }),
      stoneKeep: new THREE.MeshStandardMaterial({
        map: toTex(darkStone.color, 3, 2.5, true),
        bumpMap: toTex(darkStone.bump, 3, 2.5, false),
        roughnessMap: toTex(darkStone.rough, 3, 2.5, false),
        bumpScale: 0.34,
        roughness: 0.9,
        metalness: 0.08
      }),
      flagstoneTop: new THREE.MeshStandardMaterial({
        map: toTex(flagStone.color, 2, 2, true),
        bumpMap: toTex(flagStone.bump, 2, 2, false),
        roughnessMap: toTex(flagStone.rough, 2, 2, false),
        bumpScale: 0.2,
        roughness: 0.88,
        metalness: 0.05
      }),
      cobblePlaza: new THREE.MeshStandardMaterial({
        map: toTex(cobble.color, 2.5, 2.5, true),
        bumpMap: toTex(cobble.bump, 2.5, 2.5, false),
        roughnessMap: toTex(cobble.rough, 2.5, 2.5, false),
        bumpScale: 0.22,
        roughness: 0.88,
        metalness: 0.06
      }),
      slateRoof: new THREE.MeshStandardMaterial({
        map: toTex(slateRoof.color, 2, 2, true),
        bumpMap: toTex(slateRoof.bump, 2, 2, false),
        roughnessMap: toTex(slateRoof.rough, 2, 2, false),
        bumpScale: 0.24,
        roughness: 0.7,
        metalness: 0.16
      }),
      turretRoof: new THREE.MeshStandardMaterial({
        map: toTex(turretSlate.color, 2, 1.5, true),
        bumpMap: toTex(turretSlate.bump, 2, 1.5, false),
        roughnessMap: toTex(turretSlate.rough, 2, 1.5, false),
        bumpScale: 0.24,
        roughness: 0.72,
        metalness: 0.14
      }),
      terracottaRoof: new THREE.MeshStandardMaterial({
        map: toTex(terraRoof.color, 2, 2, true),
        bumpMap: toTex(terraRoof.bump, 2, 2, false),
        roughnessMap: toTex(terraRoof.rough, 2, 2, false),
        bumpScale: 0.24,
        roughness: 0.8,
        metalness: 0.06
      }),
      thatchRoof: new THREE.MeshStandardMaterial({
        map: toTex(thatch.color, 2, 2, true),
        bumpMap: toTex(thatch.bump, 2, 2, false),
        bumpScale: 0.28,
        roughness: 0.96,
        metalness: 0.0
      }),
      tudorWall: new THREE.MeshStandardMaterial({
        map: toTex(tudor.color, 1.4, 1, true),
        bumpMap: toTex(tudor.bump, 1.4, 1, false),
        roughnessMap: toTex(tudor.rough, 1.4, 1, false),
        bumpScale: 0.22,
        roughness: 0.88,
        metalness: 0.03
      }),
      timber: new THREE.MeshStandardMaterial({
        map: toTex(oakDark.color, 1.5, 1.5, true),
        bumpMap: toTex(oakDark.bump, 1.5, 1.5, false),
        bumpScale: 0.18,
        roughness: 0.86,
        metalness: 0.04
      }),
      timberLight: new THREE.MeshStandardMaterial({
        map: toTex(oakLight.color, 1.5, 1.5, true),
        bumpMap: toTex(oakLight.bump, 1.5, 1.5, false),
        bumpScale: 0.16,
        roughness: 0.84,
        metalness: 0.03
      }),
      valleyGrass: new THREE.MeshStandardMaterial({
        map: toTex(grass.color, 12, 12, true),
        bumpMap: toTex(grass.bump, 12, 12, false),
        bumpScale: 0.12,
        roughness: 0.96,
        metalness: 0.0
      }),
      courtyardDirt: new THREE.MeshStandardMaterial({
        map: toTex(dirt.color, 6, 6, true),
        bumpMap: toTex(dirt.bump, 6, 6, false),
        bumpScale: 0.14,
        roughness: 0.96,
        metalness: 0.0
      }),
      riverWater: new THREE.MeshStandardMaterial({
        map: riverTex,
        roughness: 0.16,
        metalness: 0.18,
        emissive: 0x0a3452,
        emissiveIntensity: 0.24
      }),
      fountainWater: new THREE.MeshStandardMaterial({
        color: 0x2cb8f5,
        roughness: 0.14,
        metalness: 0.14,
        emissive: 0x106a9e,
        emissiveIntensity: 0.35
      }),
      contactAoRound: new THREE.MeshBasicMaterial({
        map: createContactAoTexture(false),
        transparent: true,
        depthWrite: false,
        polygonOffset: true,
        polygonOffsetFactor: -1
      }),
      contactAoRect: new THREE.MeshBasicMaterial({
        map: createContactAoTexture(true),
        transparent: true,
        depthWrite: false,
        polygonOffset: true,
        polygonOffsetFactor: -1
      }),
      awningRed: new THREE.MeshStandardMaterial({ color: 0xa81f20, roughness: 0.85 }),
      awningYellow: new THREE.MeshStandardMaterial({ color: 0xd4a424, roughness: 0.82 }),
      awningCanvas: new THREE.MeshStandardMaterial({ color: 0xa69e8c, roughness: 0.9 }),
      wheatGold: new THREE.MeshStandardMaterial({ color: 0xc99628, roughness: 0.9 }),
      goldIngot: new THREE.MeshStandardMaterial({ color: 0xf4c438, roughness: 0.24, metalness: 0.86, emissive: 0x6b4506, emissiveIntensity: 0.28 }),
      forgeEmber: new THREE.MeshStandardMaterial({ color: 0xff6b24, emissive: 0xff4500, emissiveIntensity: 1.45, roughness: 0.45 }),
      pineDark: new THREE.MeshStandardMaterial({ color: 0x1d3522, roughness: 0.92 }),
      pineMid: new THREE.MeshStandardMaterial({ color: 0x2c4d2f, roughness: 0.9 }),
      bushLeaf: new THREE.MeshStandardMaterial({ color: 0x2f4f26, roughness: 0.92 }),
      rockGrey: new THREE.MeshStandardMaterial({ color: 0x525961, roughness: 0.94 }),
      mountainFar: new THREE.MeshStandardMaterial({ color: 0x5b6c7d, roughness: 0.96 }),
      mountainSnow: new THREE.MeshStandardMaterial({ color: 0xd8d3c8, roughness: 0.88 })
    };
  };
})();
