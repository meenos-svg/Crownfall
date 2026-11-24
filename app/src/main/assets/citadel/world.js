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

  // Sculpt rolling alpine valley hills outside the fortress while keeping courtyard & estates flat
  function terrainHeight(x, z) {
    // Keep inner fortress & main valley estates smoothly leveled around y=0
    var inCore = Math.abs(x) < 22 && Math.abs(z) < 24;
    if (inCore) return 0;
    var dx = Math.max(0, Math.abs(x) - 21);
    var dz = Math.max(0, Math.abs(z) - 23);
    var dist = Math.sqrt(dx * dx + dz * dz);
    var blend = Math.min(1, dist / 24);
    var hill = (Math.sin(x * 0.08) * Math.cos(z * 0.07) * 3.4 + Math.sin((x + z) * 0.05) * 2.6 + dist * 0.16) * blend;
    // Slight dip along eastern river channel
    var riverX = 14.5 + Math.sin(z * 0.14) * 2.2;
    var rDist = Math.abs(x - riverX);
    if (rDist < 4.0) hill *= rDist / 4.0;
    return Math.max(-0.2, hill);
  }

  window.buildAlpineValleyWorld = function (scene, mats) {
    var worldGroup = new THREE.Group();
    scene.add(worldGroup);

    // 1. Sculpted Green Alpine Valley Terrain
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

    // 2. Warm Ochre-Dirt & Grass Inner Courtyard Floor (matches reference photo!)
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
    yardMesh.position.y = 0.05;
    yardMesh.receiveShadow = true;
    worldGroup.add(yardMesh);

    // 3. Winding Ochre Dirt Footpath hugging western outer wall + paths to outer estates
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

    // Western winding dirt footpath (matching reference left valley path)
    buildFlatRibbon([
      [-15.5, 25], [-16.0, 14], [-15.2, 4], [-16.8, -6], [-19.5, -14], [-15.5, -22]
    ], 2.2, 0.06, mats.courtyardDirt);
    // Eastern estate footpath beyond bridges
    buildFlatRibbon([
      [19.5, -4.5], [22.0, 3.0], [21.5, 13.5], [16.0, 18.0]
    ], 1.9, 0.06, mats.courtyardDirt);

    // 4. Bright Cerulean Branching Eastern River / Moat (iconic feature of reference photo!)
    buildFlatRibbon([
      [15.5, -24], [14.8, -14], [12.8, -7.5], [11.2, -1.5], [13.2, 4.5], [15.0, 12.0], [18.5, 22.0], [24.0, 34.0]
    ], 2.7, 0.09, mats.riverWater);
    // River island loop fork around the arched stone bridge
    buildFlatRibbon([
      [14.2, -10.5], [16.4, -5.0], [14.4, 0.2], [12.5, 3.5]
    ], 1.7, 0.085, mats.riverWater);

    // Riverbank rocky stones along edges
    var bankStones = [[11.5, -5.2], [15.4, -1.2], [10.8, 2.2], [14.6, 8.6], [17.2, 16.5], [-15.2, 17.5], [-18.2, 10.2]];
    bankStones.forEach(function (bs, idx) {
      var rock = new THREE.Mesh(new THREE.DodecahedronGeometry(0.65 + (idx % 3) * 0.28, 0), mats.rockGrey);
      rock.position.set(bs[0], 0.35, bs[1]);
      rock.scale.set(1.3, 0.65, 1.1);
      rock.rotation.y = idx * 1.1;
      worldGroup.add(enableShadow(rock));
    });

    // 5. Two Iconic Eastern Bridges Spanning the Cerulean River
    // Bridge A: Heavy Arched Grey Stone Bridge from East Barbican Gate -> Gold Mint Outpost
    var stoneBridge = new THREE.Group();
    stoneBridge.position.set(14.0, 0, -5.0);
    stoneBridge.rotation.y = -0.22;
    addBox(stoneBridge, 6.8, 0.45, 2.5, 0, 1.45, 0, mats.flagstoneTop);
    addBox(stoneBridge, 6.8, 0.65, 0.35, 0, 1.95, 1.1, mats.stoneWall);
    addBox(stoneBridge, 6.8, 0.65, 0.35, 0, 1.95, -1.1, mats.stoneWall);
    // Stone bridge piers
    [-2.5, 0, 2.5].forEach(function (px) {
      addBox(stoneBridge, 1.0, 1.5, 2.4, px, 0.72, 0, mats.stoneWall);
    });
    // Timber entrance ramp deck connecting gate to bridge
    addBox(stoneBridge, 2.2, 0.22, 2.1, -4.1, 1.38, 0, mats.timber);
    worldGroup.add(stoneBridge);

    // Bridge B: Rustic Timber-and-Stone Footbridge downstream toward Farmstead
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

    // 6. Distant Misty Alpine Mountain Peaks framing Left & Right Horizon (Reference Backdrop!)
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

    // 7. Jagged, Multi-Tiered Stone Curtain Walls, Corner Watchtowers & Ballistas
    var wallGroup = new THREE.Group();
    worldGroup.add(wallGroup);

    function buildWallSegment(x1, z1, x2, z2, height, thick) {
      var dx = x2 - x1, dz = z2 - z1;
      var len = Math.sqrt(dx * dx + dz * dz);
      var angle = Math.atan2(dx, dz);
      var seg = new THREE.Group();
      seg.position.set((x1 + x2) * 0.5, 0, (z1 + z2) * 0.5);
      seg.rotation.y = angle;

      // Main masonry curtain wall
      addBox(seg, thick, height, len, 0, height * 0.5, 0, mats.stoneWall);
      // Flagstone parapet walkway on top
      addBox(seg, thick + 0.16, 0.24, len + 0.1, 0, height + 0.08, 0, mats.flagstoneTop);
      // Crenellated merlons along outer edge
      var merlonCount = Math.max(2, Math.floor(len / 1.45));
      for (var m = 0; m < merlonCount; m++) {
        var mz = -len * 0.5 + (m + 0.5) * (len / merlonCount);
        addBox(seg, 0.36, 0.62, 0.78, thick * 0.5 - 0.12, height + 0.48, mz, mats.stoneWall);
      }
      wallGroup.add(seg);
    }

    // Elongated, stepped fortress perimeter matching the reference silhouette!
    var wallHeight = 3.2;
    var wallPts = [
      [-11.8, -18.5], [10.6, -18.5],     // North wall behind Keep
      [10.6, -18.5], [10.6, -10.0],      // NE flank
      [10.6, -10.0], [9.2, -5.0],        // East Barbican gate wall
      [9.2, -5.0], [6.8, 0.5],           // Stepped inward mid-east wall
      [6.8, 0.5], [9.6, 11.8],           // SE curtain wall along river
      [9.6, 11.8], [3.5, 16.5],          // South-east angled rampart
      [3.5, 16.5], [-1.2, 19.6],         // Southernmost apex
      [-1.2, 19.6], [-8.8, 17.0],        // South-west foreground bastion
      [-8.8, 17.0], [-10.8, 7.5],        // Lower-west stepped curtain
      [-10.8, 7.5], [-12.2, -2.5],       // Mid-west curtain
      [-12.2, -2.5], [-11.8, -18.5]      // Upper-west curtain
    ];
    for (var w = 0; w < wallPts.length; w += 2) {
      buildWallSegment(wallPts[w][0], wallPts[w][1], wallPts[w + 1][0], wallPts[w + 1][1], wallHeight, 1.85);
    }

    // Exterior stone staircases climbing up inside the eastern & southern walls (visible in reference!)
    function addStoneStairs(x, z, ry) {
      var st = new THREE.Group();
      st.position.set(x, 0, z);
      st.rotation.y = ry;
      for (var s = 0; s < 7; s++) {
        addBox(st, 1.3, 0.45 * (s + 1), 0.52, 0, 0.225 * (s + 1), s * 0.48, mats.flagstoneTop);
      }
      wallGroup.add(st);
    }
    addStoneStairs(6.2, 2.2, 0.2);
    addStoneStairs(-9.4, -7.0, 0.0);

    // Conical Slate-Roofed Round Corner Watchtowers & Square Crenellated Bastion Towers
    function addRoundTower(x, z, r, h, hasConicalRoof) {
      var t = new THREE.Group();
      t.position.set(x, 0, z);
      var body = new THREE.Mesh(new THREE.CylinderGeometry(r, r * 1.07, h, 18), mats.stoneKeep);
      body.position.y = h * 0.5;
      t.add(enableShadow(body));
      var rim = new THREE.Mesh(new THREE.CylinderGeometry(r * 1.14, r * 1.08, 0.65, 18), mats.stoneWall);
      rim.position.y = h + 0.2;
      t.add(enableShadow(rim));
      if (hasConicalRoof) {
        var cone = new THREE.Mesh(new THREE.ConeGeometry(r * 1.32, h * 0.42, 18), mats.turretRoof);
        cone.position.y = h + 0.5 + h * 0.21;
        t.add(enableShadow(cone));
      }
      wallGroup.add(t);
    }

    function addSquareBastion(x, z, size, h, hasBallista) {
      var b = new THREE.Group();
      b.position.set(x, 0, z);
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

    // Iconic foreground west round turret with blue conical roof (front-left of reference!)
    addRoundTower(-10.8, 7.6, 2.05, 6.2, true);
    // Iconic north-west tall conical slate-roofed lookout tower behind Alliance Hall
    addRoundTower(-5.5, -18.5, 1.65, 8.6, true);
    // Small southern conical watch turret on foreground apex
    addRoundTower(-1.0, 19.6, 1.15, 4.4, true);
    // Tall square crenellated NE watchtower right of Obsidian Keep
    addSquareBastion(7.4, -17.8, 2.5, 8.2, false);
    // Corner & gatehouse crenellated square bastions (with mounted corner ballistas!)
    addSquareBastion(-11.8, -18.5, 3.0, 4.4, false);
    addSquareBastion(-12.2, -2.5, 2.8, 4.4, false);
    addSquareBastion(-8.8, 17.0, 3.2, 4.5, true);
    addSquareBastion(9.6, 11.8, 3.1, 4.8, true);
    addSquareBastion(10.0, -7.8, 3.3, 5.4, false); // East Barbican Gatehouse tower
    addSquareBastion(10.6, -12.4, 2.8, 5.0, false);

    // 8. Clustered 3D Alpine Pine/Fir Trees outside West & North valley slopes
    function addPine(x, z, sc) {
      var p = new THREE.Group();
      p.position.set(x, terrainHeight(x, z), z);
      var trunk = new THREE.Mesh(new THREE.CylinderGeometry(0.18 * sc, 0.24 * sc, 1.1 * sc, 6), mats.timber);
      trunk.position.y = 0.55 * sc;
      p.add(enableShadow(trunk));
      [
        [1.5 * sc, 1.45 * sc, 1.8 * sc, mats.pineDark],
        [2.4 * sc, 1.15 * sc, 1.6 * sc, mats.pineMid],
        [3.2 * sc, 0.78 * sc, 1.35 * sc, mats.pineDark]
      ].forEach(function (tier) {
        var cone = new THREE.Mesh(new THREE.ConeGeometry(tier[1], tier[2], 7), tier[3]);
        cone.position.y = tier[0];
        p.add(enableShadow(cone));
      });
      worldGroup.add(p);
    }

    var pines = [
      [-23.5, -14.5, 1.2], [-25.2, -10.8, 1.35], [-22.0, -7.8, 1.1],
      [-26.5, -6.0, 1.25], [-21.5, -17.5, 1.05], [-19.5, -22.0, 1.15],
      [-23.0, 4.5, 1.1], [-24.5, 9.5, 1.2], [-20.5, 16.5, 1.0],
      [25.5, -15.5, 1.15], [27.0, -10.0, 1.05], [26.5, 2.5, 1.1],
      [-12.0, -25.0, 1.15], [14.0, -25.5, 1.2]
    ];
    pines.forEach(function (pt) { addPine(pt[0], pt[1], pt[2]); });

    return {
      worldGroup: worldGroup,
      terrainHeight: terrainHeight
    };
  };
})();
