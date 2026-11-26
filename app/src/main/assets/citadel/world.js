(function () {
  function enableShadow(mesh, cast, receive) {
    mesh.castShadow = cast !== false;
    mesh.receiveShadow = receive !== false;
    return mesh;
  }

  function addBox(parent, w, h, d, x, y, z, mat, ry) {
    var m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat);
    m.position.set(x, y, z);
    if (ry) m.rotation.y = ry;
    parent.add(enableShadow(m));
    return m;
  }

  function addContactDecal(parent, w, d, x, y, z, mat, ry) {
    var m = new THREE.Mesh(new THREE.PlaneGeometry(w, d), mat);
    m.rotation.x = -Math.PI / 2;
    if (ry) m.rotation.z = -ry;
    m.position.set(x, y, z);
    m.renderOrder = 2;
    parent.add(m);
    return m;
  }

  // Sculpt rolling alpine valley hills outside the fortress while keeping courtyard & estates flat
  function terrainHeight(x, z) {
    var inCore = Math.abs(x) < 22 && Math.abs(z) < 24;
    if (inCore) return 0;
    var dx = Math.max(0, Math.abs(x) - 21);
    var dz = Math.max(0, Math.abs(z) - 23);
    var dist = Math.sqrt(dx * dx + dz * dz);
    var blend = Math.min(1, dist / 24);
    var hill = (Math.sin(x * 0.08) * Math.cos(z * 0.07) * 3.4 + Math.sin((x + z) * 0.05) * 2.6 + dist * 0.16) * blend;
    var riverX = 14.5 + Math.sin(z * 0.14) * 2.2;
    var rDist = Math.abs(x - riverX);
    if (rDist < 4.0) hill *= rDist / 4.0;
    return Math.max(-0.2, hill);
  }

  window.buildAlpineValleyWorld = function (scene, mats) {
    var worldGroup = new THREE.Group();
    scene.add(worldGroup);

    // 1. Sculpted Rich Alpine Valley Terrain
    var gGeo = new THREE.PlaneGeometry(180, 180, 72, 72);
    gGeo.rotateX(-Math.PI / 2);
    var pos = gGeo.attributes.position;
    for (var i = 0; i < pos.count; i++) {
      var vx = pos.getX(i);
      var vz = pos.getZ(i);
      pos.setY(i, terrainHeight(vx, vz));
    }
    gGeo.computeVertexNormals();
    var terrain = new THREE.Mesh(gGeo, mats.valleyGrass);
    terrain.receiveShadow = true;
    worldGroup.add(terrain);

    // 2. Warm Packed-Earth Inner Courtyard Floor
    var yardShape = new THREE.Shape();
    yardShape.moveTo(-11.5, -18.5);
    yardShape.lineTo(10.5, -18.5);
    yardShape.lineTo(10.5, -3.5);
    yardShape.lineTo(7.5, 2.5);
    yardShape.lineTo(9.5, 13.5);
    yardShape.lineTo(-1.0, 19.5);
    yardShape.lineTo(-9.5, 16.5);
    yardShape.lineTo(-11.5, 4.0);
    yardShape.closePath();
    var yardGeo = new THREE.ShapeGeometry(yardShape);
    yardGeo.rotateX(-Math.PI / 2);
    var yardMesh = new THREE.Mesh(yardGeo, mats.courtyardDirt);
    yardMesh.position.y = 0.04;
    yardMesh.receiveShadow = true;
    worldGroup.add(yardMesh);

    // 2B. Weathered Cobblestone Plazas around Obsidian Keep, Fountain & Central Courtyard Square
    function addCobbleDisc(x, z, rx, rz, yElev) {
      var disc = new THREE.Mesh(new THREE.CircleGeometry(1, 28), mats.cobblePlaza);
      disc.rotation.x = -Math.PI / 2;
      disc.scale.set(rx, rz, 1);
      disc.position.set(x, yElev || 0.065, z);
      disc.receiveShadow = true;
      worldGroup.add(disc);
    }
    addCobbleDisc(0.0, -14.8, 6.2, 5.4, 0.065);   // Grand Obsidian Keep forecourt plaza
    addCobbleDisc(-3.2, -5.8, 3.6, 3.4, 0.068);   // Circular Fountain cobblestone square
    addCobbleDisc(-0.2, 3.6, 4.6, 5.2, 0.062);    // Central Market & Armory cobbled yard

    // 3. Winding Dirt Pathways (Inner Courtyard Network + Outer Valley Roads)
    function buildFlatRibbon(points, width, yElev, mat) {
      var curve = new THREE.CatmullRomCurve3(points.map(function (p) { return new THREE.Vector3(p[0], yElev, p[1]); }));
      var samples = 44;
      var pts = curve.getPoints(samples);
      var verts = [];
      var uvs = [];
      var indices = [];
      var halfW = width * 0.5;
      for (var k = 0; k < pts.length; k++) {
        var cur = pts[k];
        var next = pts[Math.min(k + 1, pts.length - 1)];
        var prev = pts[Math.max(k - 1, 0)];
        var tan = new THREE.Vector3().subVectors(next, prev).normalize();
        var perp = new THREE.Vector3(-tan.z, 0, tan.x);
        verts.push(cur.x + perp.x * halfW, cur.y, cur.z + perp.z * halfW);
        verts.push(cur.x - perp.x * halfW, cur.y, cur.z - perp.z * halfW);
        uvs.push(0, k / 5);
        uvs.push(1, k / 5);
        if (k < pts.length - 1) {
          var a = k * 2, b = a + 1, c = a + 2, d = a + 3;
          indices.push(a, c, b, b, c, d);
        }
      }
      var geo = new THREE.BufferGeometry();
      geo.setAttribute("position", new THREE.Float32BufferAttribute(verts, 3));
      geo.setAttribute("uv", new THREE.Float32BufferAttribute(uvs, 2));
      geo.setIndex(indices);
      geo.computeVertexNormals();
      var m = new THREE.Mesh(geo, mat);
      m.receiveShadow = true;
      worldGroup.add(m);
      return m;
    }

    // Inner courtyard cobblestone/worn paths linking Keep -> Fountain -> Barracks -> Stables -> Siege -> East Gate
    buildFlatRibbon([[0.0, -11.2], [-1.8, -7.2], [-2.0, -1.2], [-0.4, 4.0], [-2.8, 10.2]], 1.85, 0.072, mats.cobblePlaza);
    buildFlatRibbon([[-4.8, -9.2], [-2.5, -5.6], [1.4, -5.8], [8.5, -5.4]], 1.65, 0.072, mats.cobblePlaza);
    buildFlatRibbon([[-0.4, 4.0], [3.2, 7.2]], 1.45, 0.07, mats.cobblePlaza);

    // Western winding dirt footpath (matching reference left valley path)
    buildFlatRibbon([
      [-15.5, 25], [-16.0, 14], [-15.2, 4], [-16.8, -6], [-19.5, -14], [-15.5, -22]
    ], 2.3, 0.06, mats.courtyardDirt);
    // Eastern estate footpath connecting Gold Mint, Quarry & Farmstead
    buildFlatRibbon([
      [18.2, -16.0], [20.2, -9.5], [19.8, -3.5], [22.0, 3.8], [21.2, 13.5], [15.5, 17.5]
    ], 2.0, 0.06, mats.courtyardDirt);

    // 4. Flowing Cerulean Branching Eastern River / Moat
    buildFlatRibbon([
      [15.5, -24], [14.8, -14], [12.8, -7.5], [11.2, -1.5], [13.2, 4.5], [15.0, 12.0], [18.5, 22.0], [24.0, 34.0]
    ], 2.7, 0.09, mats.riverWater);
    buildFlatRibbon([
      [14.2, -10.5], [16.4, -5.0], [14.4, 0.2], [12.5, 3.5]
    ], 1.7, 0.085, mats.riverWater);

    // Scattered mossy rocks & boulders along riverbanks and valley perimeter
    var bankStones = [
      [11.5, -5.2], [15.4, -1.2], [10.8, 2.2], [14.6, 8.6], [17.2, 16.5],
      [-15.2, 17.5], [-18.2, 10.2], [-14.2, -14.8], [-17.6, -2.4], [13.0, -19.2],
      [23.5, -11.0], [24.8, 7.4], [-9.2, 21.8], [5.4, 19.6], [-13.4, 12.2]
    ];
    bankStones.forEach(function (bs, idx) {
      var rx = bs[0];
      var rz = bs[1];
      var rSize = 0.52 + (idx % 3) * 0.26;
      var rock = new THREE.Mesh(new THREE.DodecahedronGeometry(rSize, 0), mats.rockGrey);
      rock.position.set(rx, terrainHeight(rx, rz) + rSize * 0.42, rz);
      rock.scale.set(1.3, 0.62, 1.08);
      rock.rotation.y = idx * 1.1;
      worldGroup.add(enableShadow(rock));
      addContactDecal(worldGroup, rSize * 2.9, rSize * 2.5, rx, terrainHeight(rx, rz) + 0.055, rz, mats.contactAoRound);
    });

    // 5. Two Iconic Eastern Bridges Spanning the Cerulean River
    var stoneBridge = new THREE.Group();
    stoneBridge.position.set(14.0, 0, -5.0);
    stoneBridge.rotation.y = -0.22;
    addBox(stoneBridge, 6.8, 0.45, 2.5, 0, 1.45, 0, mats.flagstoneTop);
    addBox(stoneBridge, 6.8, 0.65, 0.35, 0, 1.95, 1.1, mats.stoneWall);
    addBox(stoneBridge, 6.8, 0.65, 0.35, 0, 1.95, -1.1, mats.stoneWall);
    [-2.5, 0, 2.5].forEach(function (px) {
      addBox(stoneBridge, 1.0, 1.5, 2.4, px, 0.72, 0, mats.stoneWall);
    });
    addBox(stoneBridge, 2.2, 0.22, 2.1, -4.1, 1.38, 0, mats.timber);
    worldGroup.add(stoneBridge);

    var footBridge = new THREE.Group();
    footBridge.position.set(15.6, 0, 14.2);
    footBridge.rotation.y = 0.38;
    addBox(footBridge, 5.2, 0.28, 1.7, 0, 0.75, 0, mats.timber);
    addBox(footBridge, 1.1, 0.75, 1.8, -2.3, 0.35, 0, mats.stoneWall);
    addBox(footBridge, 1.1, 0.75, 1.8, 2.3, 0.35, 0, mats.stoneWall);
    [-0.75, 0.75].forEach(function (rz) {
      addBox(footBridge, 4.8, 0.14, 0.14, 0, 1.25, rz, mats.timberLight);
    });
    worldGroup.add(footBridge);

    // 6. Distant Misty Alpine Mountain Peaks framing Left & Right Horizon
    function addAlpinePeak(x, z, r, h) {
      var g = new THREE.Group();
      var peak = new THREE.Mesh(new THREE.ConeGeometry(r, h, 7), mats.mountainFar);
      peak.position.set(x, h * 0.44, z);
      peak.rotation.y = (x + z) * 0.2;
      g.add(peak);
      var cap = new THREE.Mesh(new THREE.ConeGeometry(r * 0.32, h * 0.3, 7), mats.mountainSnow);
      cap.position.set(x, h * 0.79, z);
      cap.rotation.y = (x + z) * 0.2;
      g.add(cap);
      worldGroup.add(g);
    }
    addAlpinePeak(-42, -52, 24, 34);
    addAlpinePeak(-18, -58, 20, 26);
    addAlpinePeak(38, -54, 26, 36);
    addAlpinePeak(54, -36, 22, 28);
    addAlpinePeak(-55, -26, 20, 22);

    // 7. Jagged, Multi-Tiered Dark Granite Curtain Walls, Bastions & Contact AO Shadows
    var wallGroup = new THREE.Group();
    worldGroup.add(wallGroup);

    function buildWallSegment(x1, z1, x2, z2, height, thick) {
      var dx = x2 - x1, dz = z2 - z1;
      var len = Math.sqrt(dx * dx + dz * dz);
      var angle = Math.atan2(dx, dz);
      var seg = new THREE.Group();
      seg.position.set((x1 + x2) * 0.5, 0, (z1 + z2) * 0.5);
      seg.rotation.y = angle;

      // Grounded dark ambient-occlusion contact shadow strip under curtain wall base
      addContactDecal(seg, thick + 1.5, len + 0.8, 0, 0.08, 0, mats.contactAoRect, 0);

      // Main weathered dark granite curtain wall
      addBox(seg, thick, height, len, 0, height * 0.5, 0, mats.stoneWall);
      // Flagstone parapet walkway on top
      addBox(seg, thick + 0.18, 0.24, len + 0.1, 0, height + 0.08, 0, mats.flagstoneTop);
      // Crenellated merlons casting crisp crenellated shadows across the courtyard
      var merlonCount = Math.max(2, Math.floor(len / 1.45));
      for (var m = 0; m < merlonCount; m++) {
        var mz = -len * 0.5 + (m + 0.5) * (len / merlonCount);
        addBox(seg, 0.38, 0.66, 0.8, thick * 0.5 - 0.12, height + 0.5, mz, mats.stoneWall);
      }
      wallGroup.add(seg);
    }

    var wallHeight = 3.2;
    var wallPts = [
      [-11.8, -18.5], [10.6, -18.5],
      [10.6, -18.5], [10.6, -10.0],
      [10.6, -10.0], [9.2, -5.0],
      [9.2, -5.0], [6.8, 0.5],
      [6.8, 0.5], [9.6, 11.8],
      [9.6, 11.8], [3.5, 16.5],
      [3.5, 16.5], [-1.2, 19.6],
      [-1.2, 19.6], [-8.8, 17.0],
      [-8.8, 17.0], [-10.8, 7.5],
      [-10.8, 7.5], [-12.2, -2.5],
      [-12.2, -2.5], [-11.8, -18.5]
    ];
    for (var w = 0; w < wallPts.length; w += 2) {
      buildWallSegment(wallPts[w][0], wallPts[w][1], wallPts[w + 1][0], wallPts[w + 1][1], wallHeight, 1.85);
    }

    function addStoneStairs(x, z, ry) {
      var st = new THREE.Group();
      st.position.set(x, 0, z);
      st.rotation.y = ry;
      addContactDecal(st, 2.2, 4.2, 0, 0.08, 1.5, mats.contactAoRect, 0);
      for (var s = 0; s < 7; s++) {
        addBox(st, 1.3, 0.45 * (s + 1), 0.52, 0, 0.225 * (s + 1), s * 0.48, mats.flagstoneTop);
      }
      wallGroup.add(st);
    }
    addStoneStairs(6.2, 2.2, 0.2);
    addStoneStairs(-9.4, -7.0, 0.0);

    function addRoundTower(x, z, r, h, hasConicalRoof) {
      var t = new THREE.Group();
      t.position.set(x, 0, z);
      addContactDecal(t, r * 3.1, r * 3.1, 0, 0.082, 0, mats.contactAoRound);
      // Tapered carved pylon tower (square, slightly battered walls)
      var body = new THREE.Mesh(new THREE.CylinderGeometry(r * 0.92, r * 1.25, h, 4), mats.stoneKeep);
      body.rotation.y = Math.PI / 4;
      body.position.y = h * 0.5;
      t.add(enableShadow(body));
      var rim = new THREE.Mesh(new THREE.CylinderGeometry(r * 1.08, r * 0.96, 0.55, 4), mats.flagstoneTop);
      rim.rotation.y = Math.PI / 4;
      rim.position.y = h + 0.2;
      t.add(enableShadow(rim));
      if (hasConicalRoof) {
        var bowl = new THREE.Mesh(new THREE.CylinderGeometry(r * 0.35, r * 0.22, 0.45, 8), mats.goldIngot);
        bowl.position.y = h + 0.7;
        t.add(bowl);
        var fire = new THREE.Mesh(new THREE.ConeGeometry(r * 0.26, r * 0.7, 7), mats.forgeEmber);
        fire.position.y = h + 1.15 + r * 0.2;
        t.add(fire);
      }
      wallGroup.add(t);
    }

    function addSquareBastion(x, z, size, h, hasBallista) {
      var b = new THREE.Group();
      b.position.set(x, 0, z);
      addContactDecal(b, size * 1.65, size * 1.65, 0, 0.082, 0, mats.contactAoRound);
      addBox(b, size, h, size, 0, h * 0.5, 0, mats.stoneWall);
      addBox(b, size + 0.3, 0.35, size + 0.3, 0, h + 0.1, 0, mats.flagstoneTop);
      var half = size * 0.5;
      [[-half, -half], [half, -half], [-half, half], [half, half], [0, -half], [0, half], [-half, 0], [half, 0]].forEach(function (c) {
        addBox(b, 0.55, 0.72, 0.55, c[0] * 0.92, h + 0.52, c[1] * 0.92, mats.stoneWall);
      });
      if (hasBallista) {
        addBox(b, 0.3, 0.3, 1.35, 0, h + 0.45, 0, mats.timber);
        addBox(b, 1.5, 0.16, 0.18, 0, h + 0.55, -0.35, mats.timberLight);
      }
      wallGroup.add(b);
    }

    addRoundTower(-10.8, 7.6, 2.05, 6.2, true);
    addRoundTower(-5.5, -18.5, 1.65, 8.6, true);
    addRoundTower(-1.0, 19.6, 1.15, 4.4, true);
    addSquareBastion(7.4, -17.8, 2.5, 8.2, false);
    addSquareBastion(-11.8, -18.5, 3.0, 4.4, false);
    addSquareBastion(-12.2, -2.5, 2.8, 4.4, false);
    addSquareBastion(-8.8, 17.0, 3.2, 4.5, true);
    addSquareBastion(9.6, 11.8, 3.1, 4.8, true);
    addSquareBastion(10.0, -7.8, 3.3, 5.4, false);
    addSquareBastion(10.6, -12.4, 2.8, 5.0, false);

    // 8. Clustered Alpine Pine Trees & Low Courtyard/Perimeter Bushes
    function addPine(x, z, sc) {
      var p = new THREE.Group();
      var ty = terrainHeight(x, z);
      p.position.set(x, ty, z);
      addContactDecal(p, 2.6 * sc, 2.6 * sc, 0, 0.055, 0, mats.contactAoRound);
      var trunk = new THREE.Mesh(new THREE.CylinderGeometry(0.18 * sc, 0.24 * sc, 1.1 * sc, 6), mats.timber);
      trunk.position.y = 0.55 * sc;
      p.add(enableShadow(trunk));
      [
        [1.5 * sc, 1.45 * sc, 1.85 * sc, mats.pineDark],
        [2.4 * sc, 1.15 * sc, 1.62 * sc, mats.pineMid],
        [3.2 * sc, 0.78 * sc, 1.38 * sc, mats.pineDark]
      ].forEach(function (tier) {
        var cone = new THREE.Mesh(new THREE.ConeGeometry(tier[1], tier[2], 7), tier[3]);
        cone.position.y = tier[0];
        p.add(enableShadow(cone));
      });
      worldGroup.add(p);
    }

    function addBush(x, z, sc) {
      var b = new THREE.Group();
      var ty = terrainHeight(x, z);
      b.position.set(x, ty, z);
      addContactDecal(b, 1.6 * sc, 1.6 * sc, 0, 0.06, 0, mats.contactAoRound);
      var m1 = new THREE.Mesh(new THREE.DodecahedronGeometry(0.52 * sc, 0), mats.bushLeaf);
      m1.position.set(0, 0.36 * sc, 0);
      m1.scale.set(1.15, 0.82, 1.05);
      b.add(enableShadow(m1));
      var m2 = new THREE.Mesh(new THREE.DodecahedronGeometry(0.36 * sc, 0), mats.pineMid);
      m2.position.set(0.34 * sc, 0.28 * sc, 0.2 * sc);
      b.add(enableShadow(m2));
      worldGroup.add(b);
    }

    var pines = [
      [-23.5, -14.5, 1.25], [-25.2, -10.8, 1.4], [-22.0, -7.8, 1.15],
      [-26.5, -6.0, 1.3], [-21.5, -17.5, 1.1], [-19.5, -22.0, 1.2],
      [-23.0, 4.5, 1.15], [-24.5, 9.5, 1.25], [-20.5, 16.5, 1.05],
      [-17.2, 20.4, 1.1], [-13.5, 22.8, 1.0], [-21.8, -1.5, 1.2],
      [25.5, -15.5, 1.2], [27.0, -10.0, 1.1], [26.5, 2.5, 1.15],
      [25.8, 8.2, 1.2], [11.5, 20.5, 1.05], [7.8, 22.4, 1.15],
      [-12.0, -25.0, 1.2], [-6.5, -24.5, 1.1], [14.0, -25.5, 1.25], [22.8, -22.5, 1.15]
    ];
    pines.forEach(function (pt) {
      if (pt[0] < -21.5 && Math.abs(pt[1]) < 30) return; // lava fields now
      addPine(pt[0], pt[1], pt[2]);
    });

    var bushes = [
      // Courtyard accents tucked near walls & fountain
      [-4.6, -7.0, 0.85], [-2.1, -9.8, 0.78], [5.8, -12.6, 0.9], [4.8, 1.8, 0.82],
      [-8.5, 5.2, 0.85], [-6.8, 14.8, 0.85], [1.4, 14.2, 0.82],
      // Outer perimeter hedgerows & valley clusters
      [-14.0, 11.2, 1.1], [-13.8, 1.5, 1.05], [-14.2, -9.0, 1.15], [-14.6, -18.0, 1.1],
      [13.4, -13.2, 1.0], [17.8, 6.2, 1.1], [13.0, 16.4, 1.05], [-5.2, 21.2, 1.15]
    ];
    bushes.forEach(function (bu) { addBush(bu[0], bu[1], bu[2]); });

    return {
      worldGroup: worldGroup,
      terrainHeight: terrainHeight
    };
  };
})();
