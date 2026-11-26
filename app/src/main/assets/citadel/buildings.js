(function () {
  function enableShadow(mesh, cast, receive) {
    mesh.castShadow = cast !== false;
    mesh.receiveShadow = receive !== false;
    return mesh;
  }

  function addMesh(parent, geo, mat, x, y, z, rx, ry, rz) {
    var m = new THREE.Mesh(geo, mat);
    m.position.set(x || 0, y || 0, z || 0);
    if (rx) m.rotation.x = rx;
    if (ry) m.rotation.y = ry;
    if (rz) m.rotation.z = rz;
    parent.add(enableShadow(m));
    return m;
  }

  // Extruded triangular pitched roof prism aligned along the Z axis (length = depth, width = span)
  function createPitchedRoof(width, height, depth, mat) {
    var shape = new THREE.Shape();
    var hw = width * 0.5;
    shape.moveTo(-hw, 0);
    shape.lineTo(0, height);
    shape.lineTo(hw, 0);
    shape.closePath();
    var geo = new THREE.ExtrudeGeometry(shape, {
      depth: depth,
      bevelEnabled: false
    });
    geo.translate(0, 0, -depth * 0.5);
    var m = new THREE.Mesh(geo, mat);
    return enableShadow(m);
  }

  // Medieval timber-and-plaster house helper with stone plinth + pitched roof + aged oak ridge beam
  function addMedievalHouse(parent, w, h, d, roofH, wallMat, roofMat, timberMat, x, y, z, ry) {
    var g = new THREE.Group();
    g.position.set(x || 0, y || 0, z || 0);
    if (ry) g.rotation.y = ry;

    addMesh(g, new THREE.BoxGeometry(w + 0.16, 0.55, d + 0.16), wallMat.plinth || wallMat, 0, 0.275, 0);
    addMesh(g, new THREE.BoxGeometry(w, h, d), wallMat.wall || wallMat, 0, 0.55 + h * 0.5, 0);
    if (roofMat && roofMat.userData && roofMat.userData.pitched) {
      // Thatched roofs keep their pitched shape
      var roof = createPitchedRoof(w + 0.58, roofH, d + 0.52, roofMat);
      roof.position.set(0, 0.55 + h - 0.04, 0);
      g.add(roof);
      addMesh(g, new THREE.BoxGeometry(0.18, 0.2, d + 0.62), timberMat, 0, 0.55 + h + roofH - 0.04, 0);
    } else {
      // Flat sun-baked roof with a low parapet and a small rooftop room
      var topY = 0.55 + h;
      addMesh(g, new THREE.BoxGeometry(w + 0.24, 0.18, d + 0.24), roofMat, 0, topY + 0.09, 0);
      addMesh(g, new THREE.BoxGeometry(w + 0.24, 0.32, 0.16), wallMat.wall || wallMat, 0, topY + 0.34, (d + 0.24) / 2);
      addMesh(g, new THREE.BoxGeometry(w + 0.24, 0.32, 0.16), wallMat.wall || wallMat, 0, topY + 0.34, -(d + 0.24) / 2);
      addMesh(g, new THREE.BoxGeometry(0.16, 0.32, d + 0.24), wallMat.wall || wallMat, (w + 0.24) / 2, topY + 0.34, 0);
      addMesh(g, new THREE.BoxGeometry(0.16, 0.32, d + 0.24), wallMat.wall || wallMat, -(w + 0.24) / 2, topY + 0.34, 0);
      addMesh(g, new THREE.BoxGeometry(w * 0.42, roofH * 0.55, d * 0.4), wallMat.wall || wallMat, -w * 0.18, topY + 0.18 + roofH * 0.275, -d * 0.15);
      addMesh(g, new THREE.BoxGeometry(w * 0.46, 0.12, d * 0.44), timberMat, -w * 0.18, topY + 0.18 + roofH * 0.55 + 0.06, -d * 0.15);
    }

    parent.add(g);
    return g;
  }

  function addBanner(parent, x, y, z, colorHex, bannersOut) {
    var pole = new THREE.Mesh(
      new THREE.CylinderGeometry(0.04, 0.05, 2.1, 6),
      new THREE.MeshStandardMaterial({ color: 0x322216, roughness: 0.85 })
    );
    pole.position.set(x, y + 1.05, z);
    parent.add(enableShadow(pole));

    var clothGeo = new THREE.PlaneGeometry(0.68, 1.05, 3, 2);
    clothGeo.translate(0.34, 0, 0);
    var clothMat = new THREE.MeshStandardMaterial({
      color: colorHex,
      roughness: 0.72,
      side: THREE.DoubleSide
    });
    var cloth = new THREE.Mesh(clothGeo, clothMat);
    cloth.position.set(x, y + 1.55, z);
    cloth.userData.phase = (x * 0.7 + z * 0.4);
    parent.add(cloth);
    bannersOut.push(cloth);
    return cloth;
  }

  function addSelectionRing(parent, radius) {
    var ringGeo = new THREE.RingGeometry(radius * 0.88, radius, 36);
    ringGeo.rotateX(-Math.PI / 2);
    var ringMat = new THREE.MeshBasicMaterial({
      color: 0xf4d16c,
      transparent: true,
      opacity: 0.0,
      side: THREE.DoubleSide,
      depthWrite: false
    });
    var ring = new THREE.Mesh(ringGeo, ringMat);
    ring.position.y = 0.14;
    ring.userData.nonPickable = true;
    parent.add(ring);
    return ring;
  }

  // Subtle dark ambient-occlusion contact shadow decal grounding every structure cleanly onto the terrain
  function addContactShadow(parent, w, d, mat, ox, oz) {
    var ao = new THREE.Mesh(new THREE.PlaneGeometry(w, d), mat);
    ao.rotation.x = -Math.PI / 2;
    ao.position.set(ox || 0, 0.088, oz || 0);
    ao.renderOrder = 2;
    ao.userData.nonPickable = true;
    parent.add(ao);
    return ao;
  }

  function buildGuardMesh() {
    var g = new THREE.Group();
    var armor = new THREE.MeshStandardMaterial({ color: 0x5c6775, roughness: 0.42, metalness: 0.62 });
    var cloak = new THREE.MeshStandardMaterial({ color: 0x921f1f, roughness: 0.82 });
    var helm = new THREE.MeshStandardMaterial({ color: 0xd4b668, roughness: 0.36, metalness: 0.66 });
    var body = new THREE.Mesh(new THREE.CylinderGeometry(0.14, 0.19, 0.54, 7), armor);
    body.position.y = 0.34;
    g.add(enableShadow(body));
    var cape = new THREE.Mesh(new THREE.BoxGeometry(0.24, 0.42, 0.12), cloak);
    cape.position.set(0, 0.34, -0.14);
    g.add(cape);
    var head = new THREE.Mesh(new THREE.SphereGeometry(0.13, 8, 8), helm);
    head.position.y = 0.72;
    g.add(enableShadow(head));
    var spear = new THREE.Mesh(new THREE.CylinderGeometry(0.025, 0.025, 1.05, 5), armor);
    spear.position.set(0.19, 0.55, 0.05);
    g.add(spear);
    return g;
  }

  window.buildCitadelBuildings = function (scene, mats) {
    var buildings = {};
    var pickables = [];
    var banners = [];
    var smokeOrigins = [];
    var watermillWheel = null;
    var fountainJet = null;
    var guards = [];

    var houseMats = { plinth: mats.stoneWall, wall: mats.tudorWall };

    function registerBuilding(key, label, resourceKey, x, z, baseOffsetZ, peakY, ringRadius, aoW, aoD, buildFn) {
      var group = new THREE.Group();
      group.position.set(x, 0, z);
      var ring = addSelectionRing(group, ringRadius);
      addContactShadow(group, aoW, aoD, mats.contactAoRound, 0, 0);
      buildFn(group);

      // Tag every clickable mesh (excluding selection ring & contact AO decals) for Three.js Raycaster
      group.traverse(function (obj) {
        if (obj.isMesh && !obj.userData.nonPickable) {
          obj.userData.buildingKey = key;
          pickables.push(obj);
        }
      });

      scene.add(group);
      buildings[key] = {
        key: key,
        label: label,
        resourceKey: resourceKey || null,
        group: group,
        highlightRing: ring,
        baseAnchor: new THREE.Vector3(x, 0.2, z + baseOffsetZ),
        peakAnchor: new THREE.Vector3(x, peakY, z)
      };
    }

    // 1. OBSIDIAN KEEP
    registerBuilding("KEEP", "Obsidian Keep", null, 0.0, -15.5, 4.4, 11.4, 4.6, 11.4, 10.6, function (g) {
      addMesh(g, new THREE.CylinderGeometry(4.1, 4.5, 0.7, 22), mats.flagstoneTop, 0, 0.35, 0);
      addMesh(g, new THREE.CylinderGeometry(2.95, 3.25, 9.2, 24), mats.stoneKeep, 0, 5.1, 0);
      addMesh(g, new THREE.CylinderGeometry(3.32, 3.08, 0.85, 24), mats.stoneWall, 0, 9.7, 0);
      addMesh(g, new THREE.CylinderGeometry(3.12, 3.12, 0.18, 24), mats.flagstoneTop, 0, 10.15, 0);
      for (var i = 0; i < 14; i++) {
        var ang = (i / 14) * Math.PI * 2;
        var mx = Math.cos(ang) * 3.05;
        var mz = Math.sin(ang) * 3.05;
        addMesh(g, new THREE.BoxGeometry(0.62, 0.72, 0.44), mats.stoneWall, mx, 10.52, mz, 0, -ang + Math.PI / 2, 0);
      }
      addMesh(g, new THREE.BoxGeometry(0.8, 0.35, 1.3), mats.timber, -0.4, 10.42, 0.2, 0, 0.35, 0);
      addMesh(g, new THREE.BoxGeometry(0.18, 0.9, 0.18), mats.timberLight, -0.4, 10.85, 0.1, 0.4, 0.35, 0);

      addMesh(g, new THREE.BoxGeometry(1.5, 0.45, 0.9), mats.stoneWall, 0, 6.2, 3.15);
      addMesh(g, new THREE.BoxGeometry(0.9, 1.35, 0.25), mats.timber, 0, 7.0, 3.05);
      addMesh(g, new THREE.BoxGeometry(0.55, 0.85, 0.18), mats.forgeEmber, 0, 7.1, 3.14);

      addMesh(g, new THREE.BoxGeometry(1.9, 2.4, 0.65), mats.stoneWall, 0, 1.85, 3.12);
      addMesh(g, new THREE.BoxGeometry(1.25, 1.9, 0.72), mats.timber, 0, 1.6, 3.16);

      addMedievalHouse(g, 3.1, 2.3, 4.2, 1.85, houseMats, mats.slateRoof, mats.timber, -3.7, 0.2, 0.8, 0.12);

      addBanner(g, 1.8, 10.1, 1.2, 0xb52424, banners);
      addBanner(g, -1.9, 10.1, 1.1, 0xd4a932, banners);
    });

    // 2. ALLIANCE HALL
    registerBuilding("ALLIANCE_HALL", "Alliance Hall", null, -7.0, -10.5, 4.2, 6.8, 4.2, 7.6, 10.2, function (g) {
      addMesh(g, new THREE.BoxGeometry(5.6, 0.55, 8.2), mats.flagstoneTop, 0, 0.27, 0);
      addMedievalHouse(g, 3.1, 2.4, 5.4, 1.9, houseMats, mats.slateRoof, mats.timber, -0.7, 0.45, 0.6, 0.08);
      addMedievalHouse(g, 3.4, 2.6, 2.9, 2.0, houseMats, mats.slateRoof, mats.timber, 0.65, 0.45, -2.1, Math.PI / 2);
      addMesh(g, new THREE.CylinderGeometry(0.55, 0.6, 1.2, 8), mats.tudorWall, 0.9, 3.8, -2.1);
      addMesh(g, new THREE.ConeGeometry(0.76, 1.45, 8), mats.turretRoof, 0.9, 5.05, -2.1);
      addBanner(g, 2.05, 0.55, 2.6, 0x26658c, banners);
      addBanner(g, 2.05, 0.55, -0.2, 0xd4a932, banners);
    });

    // 3. WAR BARRACKS
    registerBuilding("BARRACKS", "War Barracks", null, -5.6, -0.8, 3.6, 6.0, 3.8, 8.4, 7.8, function (g) {
      addMedievalHouse(g, 3.8, 2.4, 4.4, 1.9, houseMats, mats.terracottaRoof, mats.timber, -0.6, 0, 0.1, 0.08);
      addMedievalHouse(g, 2.3, 1.9, 2.4, 1.4, houseMats, mats.terracottaRoof, mats.timber, 1.65, 0, -0.8, Math.PI / 2);
      var yellowAwning = new THREE.Mesh(new THREE.BoxGeometry(1.4, 0.12, 2.6), mats.awningYellow);
      yellowAwning.position.set(1.75, 1.45, 1.0);
      yellowAwning.rotation.z = -0.35;
      g.add(enableShadow(yellowAwning));
      for (var d = 0; d < 2; d++) {
        var dx = 2.45;
        var dz = 2.1 + d * 0.95;
        addMesh(g, new THREE.CylinderGeometry(0.08, 0.08, 1.25, 6), mats.timber, dx, 0.62, dz);
        addMesh(g, new THREE.BoxGeometry(0.82, 0.14, 0.14), mats.timberLight, dx, 0.95, dz);
        addMesh(g, new THREE.CylinderGeometry(0.2, 0.2, 0.45, 7), mats.thatchRoof, dx, 0.85, dz);
      }
      addBanner(g, -2.3, 0.2, 2.0, 0xb52424, banners);
    });

    // 4. ASHBOW RANGE
    registerBuilding("ARCHERY", "Ashbow Range", null, 2.0, -4.6, 3.4, 6.2, 3.5, 7.6, 7.4, function (g) {
      addMedievalHouse(g, 3.0, 2.6, 3.6, 2.0, houseMats, mats.slateRoof, mats.timber, -0.5, 0, -0.7, -0.08);
      addMesh(g, new THREE.BoxGeometry(2.6, 0.16, 2.0), mats.terracottaRoof, 1.6, 1.85, 0.4, 0, 0, -0.22);
      [[0.6, 1.2], [2.5, 1.2], [0.6, -0.4], [2.5, -0.4]].forEach(function (p) {
        addMesh(g, new THREE.CylinderGeometry(0.08, 0.08, 1.85, 6), mats.timber, p[0], 0.92, p[1]);
      });
      var redMat = mats.awningRed;
      var whiteMat = new THREE.MeshStandardMaterial({ color: 0xeae2d2, roughness: 0.85 });
      for (var t = 0; t < 3; t++) {
        var tx = -1.1 + t * 1.1;
        var tz = 2.05;
        addMesh(g, new THREE.CylinderGeometry(0.36, 0.36, 0.18, 12), mats.thatchRoof, tx, 0.78, tz, Math.PI / 2, 0, 0);
        addMesh(g, new THREE.CylinderGeometry(0.24, 0.24, 0.2, 12), whiteMat, tx, 0.78, tz, Math.PI / 2, 0, 0);
        addMesh(g, new THREE.CylinderGeometry(0.11, 0.11, 0.22, 10), redMat, tx, 0.78, tz, Math.PI / 2, 0, 0);
        addMesh(g, new THREE.BoxGeometry(0.09, 0.85, 0.09), mats.timber, tx, 0.42, tz - 0.12, -0.18, 0, 0);
      }
    });

    // 5. SIEGE FOUNDRY
    registerBuilding("SIEGE", "Siege Foundry", null, 3.8, 7.6, 3.4, 6.4, 3.6, 7.8, 7.6, function (g) {
      addMedievalHouse(g, 3.5, 2.3, 3.6, 1.75, houseMats, mats.thatchRoof, mats.timber, 0.2, 0, -0.4, -0.15);
      addMesh(g, new THREE.BoxGeometry(0.95, 5.1, 0.95), mats.stoneKeep, 1.55, 2.55, -1.65);
      addMesh(g, new THREE.BoxGeometry(1.18, 0.32, 1.18), mats.stoneWall, 1.55, 5.15, -1.65);
      smokeOrigins.push(new THREE.Vector3(3.8 + 1.55, 5.4, 7.6 - 1.65));

      addMesh(g, new THREE.BoxGeometry(1.5, 0.95, 1.35), mats.stoneWall, -1.65, 0.48, 0.65);
      addMesh(g, new THREE.BoxGeometry(0.92, 0.25, 0.82), mats.forgeEmber, -1.65, 0.96, 0.65);
      addMesh(g, new THREE.BoxGeometry(1.55, 0.22, 2.0), mats.timber, -0.4, 0.32, 2.15, 0, 0.3, 0);
      addMesh(g, new THREE.BoxGeometry(0.18, 1.25, 0.18), mats.timberLight, -0.4, 0.88, 2.15, -0.48, 0.3, 0);
      [[-1.1, 1.7], [0.3, 1.9], [-0.9, 2.6], [0.5, 2.8]].forEach(function (w) {
        addMesh(g, new THREE.CylinderGeometry(0.32, 0.32, 0.14, 10), mats.timber, w[0], 0.32, w[1], 0, 0, Math.PI / 2);
      });
    });

    // 6. DREAD STABLES
    registerBuilding("STABLES", "Dread Stables", null, -3.8, 11.8, 3.6, 5.5, 3.8, 8.6, 7.6, function (g) {
      addMedievalHouse(g, 3.4, 2.0, 4.4, 1.9, houseMats, mats.thatchRoof, mats.timber, -0.4, 0, 0, 0.05);
      addMedievalHouse(g, 2.5, 1.85, 2.8, 1.6, houseMats, mats.thatchRoof, mats.timber, 0.85, 0, 0.3, Math.PI / 2);

      var fencePosts = [[-3.1, -1.8], [-3.1, 1.8], [-1.9, 1.8], [-1.9, -1.8]];
      fencePosts.forEach(function (fp) {
        addMesh(g, new THREE.BoxGeometry(0.12, 0.78, 0.12), mats.timber, fp[0], 0.39, fp[1]);
      });
      addMesh(g, new THREE.BoxGeometry(0.08, 0.1, 3.6), mats.timberLight, -3.1, 0.56, 0);
      addMesh(g, new THREE.BoxGeometry(1.2, 0.1, 0.08), mats.timberLight, -2.5, 0.56, 1.8);
      addMesh(g, new THREE.BoxGeometry(1.2, 0.1, 0.08), mats.timberLight, -2.5, 0.56, -1.8);

      var horseMat = new THREE.MeshStandardMaterial({ color: 0x4f2c16, roughness: 0.82 });
      [[-2.5, -0.5, 0.3], [-2.55, 0.75, -0.5]].forEach(function (h) {
        addMesh(g, new THREE.BoxGeometry(0.36, 0.36, 0.72), horseMat, h[0], 0.54, h[1], 0, h[2], 0);
        addMesh(g, new THREE.BoxGeometry(0.18, 0.34, 0.22), horseMat, h[0], 0.78, h[1] + 0.3, 0.3, h[2], 0);
      });
      addMesh(g, new THREE.CylinderGeometry(0.32, 0.32, 0.48, 8), mats.wheatGold, 2.2, 0.24, 1.4, 0, 0, Math.PI / 2);
      addMesh(g, new THREE.CylinderGeometry(0.32, 0.32, 0.48, 8), mats.wheatGold, 2.4, 0.24, 2.0, 0, 0.4, Math.PI / 2);
    });

    // COURTYARD SIGNATURE PROPS (Circular Sky-Blue Fountain + Red/Canvas Market Stalls + Well)
    (function addCourtyardLandmarks() {
      var ft = new THREE.Group();
      if (window.CROWNFALL_DOMINION) ft.position.set(-2.0, 0, -1.2); else ft.position.set(-3.2, 0, -5.8);
      addContactShadow(ft, 4.4, 4.4, mats.contactAoRound, 0, 0);
      addMesh(ft, new THREE.CylinderGeometry(1.45, 1.58, 0.48, 20), mats.stoneWall, 0, 0.24, 0);
      addMesh(ft, new THREE.CylinderGeometry(1.22, 1.22, 0.38, 20), mats.fountainWater, 0, 0.32, 0);
      addMesh(ft, new THREE.CylinderGeometry(0.28, 0.4, 1.25, 12), mats.flagstoneTop, 0, 0.65, 0);
      addMesh(ft, new THREE.CylinderGeometry(0.68, 0.24, 0.28, 14), mats.flagstoneTop, 0, 1.25, 0);
      fountainJet = new THREE.Mesh(
        new THREE.ConeGeometry(0.24, 0.95, 10),
        new THREE.MeshStandardMaterial({
          color: 0xbcf2ff,
          emissive: 0x3ec8ff,
          emissiveIntensity: 0.45,
          transparent: true,
          opacity: 0.78
        })
      );
      fountainJet.position.y = 1.75;
      ft.add(fountainJet);
      scene.add(ft);

      function addStall(x, z, w, d, awningMat, ry) {
        var s = new THREE.Group();
        s.position.set(x, 0, z);
        if (ry) s.rotation.y = ry;
        addContactShadow(s, w + 1.5, d + 1.5, mats.contactAoRound, 0, 0);
        addMesh(s, new THREE.BoxGeometry(w, 0.85, d), mats.timber, 0, 0.42, 0);
        var tent = createPitchedRoof(w + 0.35, 1.05, d + 0.3, awningMat);
        tent.position.set(0, 1.55, 0);
        s.add(tent);
        [[-w * 0.45, -d * 0.45], [w * 0.45, -d * 0.45], [-w * 0.45, d * 0.45], [w * 0.45, d * 0.45]].forEach(function (c) {
          addMesh(s, new THREE.CylinderGeometry(0.06, 0.06, 1.6, 6), mats.timber, c[0], 0.8, c[1]);
        });
        scene.add(s);
      }
      if (window.CROWNFALL_DOMINION) return; // the Dominion layout has its own market outside the walls
      addStall(-0.4, 2.0, 1.9, 1.7, mats.awningCanvas, 0.06);
      addStall(1.5, 4.9, 2.2, 2.0, mats.awningRed, -0.08);
      addStall(-0.9, 6.7, 2.0, 1.8, mats.awningCanvas, 0.04);

      var well = new THREE.Group();
      well.position.set(-1.5, 0, 4.3);
      addContactShadow(well, 2.1, 2.1, mats.contactAoRound, 0, 0);
      addMesh(well, new THREE.CylinderGeometry(0.55, 0.62, 0.75, 12), mats.stoneWall, 0, 0.38, 0);
      addMesh(well, new THREE.CylinderGeometry(0.42, 0.42, 0.78, 12), mats.fountainWater, 0, 0.38, 0);
      addMesh(well, new THREE.BoxGeometry(0.1, 1.3, 0.1), mats.timber, -0.48, 0.9, 0);
      addMesh(well, new THREE.BoxGeometry(0.1, 1.3, 0.1), mats.timber, 0.48, 0.9, 0);
      var wellRoof = createPitchedRoof(1.3, 0.55, 1.1, mats.terracottaRoof);
      wellRoof.position.set(0, 1.5, 0);
      well.add(wellRoof);
      scene.add(well);
    })();

    // 7. GOLD MINT
    registerBuilding("MINT", "Gold Mint", "GOLD", 20.5, -3.6, 4.0, 6.6, 4.2, 9.2, 8.8, function (g) {
      addMesh(g, new THREE.BoxGeometry(6.4, 1.35, 6.0), mats.stoneWall, 0, 0.68, 0);
      addMesh(g, new THREE.BoxGeometry(6.6, 0.25, 6.2), mats.flagstoneTop, 0, 1.42, 0);
      addMesh(g, new THREE.BoxGeometry(4.0, 3.6, 3.8), mats.stoneKeep, -0.2, 3.2, -0.8);
      addMesh(g, new THREE.BoxGeometry(4.3, 0.32, 4.1), mats.flagstoneTop, -0.2, 5.1, -0.8);
      [[-1.8, -2.5], [1.4, -2.5], [-1.8, 0.9], [1.4, 0.9], [-0.2, -2.5], [-0.2, 0.9]].forEach(function (c) {
        addMesh(g, new THREE.BoxGeometry(0.65, 0.72, 0.65), mats.stoneWall, c[0], 5.52, c[1]);
      });
      var porchRoof = new THREE.Mesh(new THREE.BoxGeometry(3.4, 0.2, 2.3), mats.terracottaRoof);
      porchRoof.position.set(-0.2, 3.35, 1.75);
      porchRoof.rotation.x = 0.28;
      g.add(enableShadow(porchRoof));
      for (var row = 0; row < 3; row++) {
        for (var col = 0; col < 3 - row; col++) {
          addMesh(g, new THREE.BoxGeometry(0.62, 0.28, 0.38), mats.goldIngot, 1.55 + col * 0.45 + row * 0.22, 1.68 + row * 0.28, 1.85);
        }
      }
      addBanner(g, -2.1, 5.1, 0.9, 0xe8bc38, banners);
    });

    // 8. FARMSTEAD
    registerBuilding("FARM", "Farmstead", "FOOD", 21.0, 13.8, 4.2, 5.6, 4.4, 11.4, 9.2, function (g) {
      addMesh(g, new THREE.BoxGeometry(5.8, 0.22, 4.6), mats.wheatGold, 1.6, 0.12, 1.4);
      for (var r = -1.6; r <= 1.6; r += 0.65) {
        addMesh(g, new THREE.BoxGeometry(5.2, 0.36, 0.26), mats.wheatGold, 1.6, 0.32, 1.4 + r);
      }
      addMedievalHouse(g, 3.3, 2.0, 3.6, 1.85, houseMats, mats.thatchRoof, mats.timber, -0.8, 0, -1.3, 0.15);
      addMesh(g, new THREE.BoxGeometry(0.62, 3.9, 0.62), mats.stoneWall, -1.7, 1.95, -2.3);
      smokeOrigins.push(new THREE.Vector3(21.0 - 1.7, 4.1, 13.8 - 2.3));

      addMedievalHouse(g, 2.0, 1.8, 2.4, 1.2, houseMats, mats.terracottaRoof, mats.timber, -3.2, 0, 0.4, 0.2);
      var wheelGroup = new THREE.Group();
      wheelGroup.position.set(-4.35, 1.15, 0.4);
      var rim = new THREE.Mesh(new THREE.TorusGeometry(1.05, 0.12, 8, 18), mats.timber);
      rim.rotation.y = Math.PI / 2;
      wheelGroup.add(enableShadow(rim));
      for (var sp = 0; sp < 8; sp++) {
        var ang = (sp / 8) * Math.PI * 2;
        var paddle = new THREE.Mesh(new THREE.BoxGeometry(0.36, 0.1, 2.25), mats.timberLight);
        paddle.rotation.x = ang;
        wheelGroup.add(enableShadow(paddle));
      }
      g.add(wheelGroup);
      watermillWheel = wheelGroup;
    });

    // 9. TIMBER MILL
    registerBuilding("LUMBER", "Timber Mill", "TIMBER", -19.2, -10.2, 3.8, 5.4, 4.0, 8.8, 8.4, function (g) {
      addMedievalHouse(g, 3.4, 2.0, 3.8, 1.7, houseMats, mats.terracottaRoof, mats.timber, 0, 0, -0.5, -0.18);
      var logOffsets = [
        [2.3, 0.28, 0.8], [2.3, 0.28, 1.35], [2.3, 0.28, 1.9],
        [2.3, 0.72, 1.08], [2.3, 0.72, 1.62], [2.3, 1.16, 1.35]
      ];
      logOffsets.forEach(function (lo) {
        addMesh(g, new THREE.CylinderGeometry(0.24, 0.24, 2.3, 8), mats.timber, lo[0], lo[1], lo[2], 0, 0, Math.PI / 2);
      });
      addMesh(g, new THREE.CylinderGeometry(0.45, 0.52, 0.55, 8), mats.timberLight, -1.9, 0.28, 1.8);
      addMesh(g, new THREE.BoxGeometry(1.5, 0.35, 1.0), mats.timberLight, -0.4, 0.45, 2.3, 0, 0.25, 0);
    });

    // 10. QUARRY
    registerBuilding("QUARRY", "Quarry", "STONE", 18.2, -17.8, 4.0, 6.2, 4.2, 9.2, 8.4, function (g) {
      addMesh(g, new THREE.DodecahedronGeometry(2.6, 0), mats.rockGrey, 0.8, 1.6, -1.4, 0, 0.4, 0);
      addMesh(g, new THREE.DodecahedronGeometry(2.0, 0), mats.stoneKeep, -1.3, 1.2, -1.6, 0, 1.1, 0);
      addMesh(g, new THREE.BoxGeometry(4.8, 1.1, 3.2), mats.stoneWall, 0, 0.55, -0.9);
      addMesh(g, new THREE.BoxGeometry(1.6, 2.0, 0.55), mats.timber, 0.1, 1.0, 0.7);
      addMesh(g, new THREE.BoxGeometry(1.15, 1.55, 0.62), new THREE.MeshBasicMaterial({ color: 0x0f1115 }), 0.1, 0.85, 0.72);
      addMesh(g, new THREE.BoxGeometry(0.24, 3.4, 0.24), mats.timber, -2.0, 1.7, 0.8);
      addMesh(g, new THREE.BoxGeometry(2.2, 0.2, 0.2), mats.timberLight, -1.2, 3.1, 0.8, 0, 0, -0.22);
      addMesh(g, new THREE.BoxGeometry(0.95, 0.75, 0.95), mats.flagstoneTop, 1.9, 0.38, 1.4);
      addMesh(g, new THREE.BoxGeometry(0.85, 0.65, 0.85), mats.flagstoneTop, 2.5, 0.33, 2.1);
      addMesh(g, new THREE.BoxGeometry(1.25, 0.52, 0.9), mats.timber, -0.6, 0.45, 2.25, 0, -0.2, 0);
      addMesh(g, new THREE.DodecahedronGeometry(0.38, 0), mats.rockGrey, -0.6, 0.85, 2.25);
    });

    // MINIATURE ANIMATED PATROLLING ARMORED GUARDS
    var patrolRoutes = [
      { a: new THREE.Vector3(6.9, 3.45, 0.9), b: new THREE.Vector3(9.4, 3.45, 11.2), speed: 0.12, offset: 0.0 },
      { a: new THREE.Vector3(-8.4, 3.45, 16.5), b: new THREE.Vector3(-10.5, 3.45, 8.2), speed: 0.11, offset: 0.35 },
      { a: new THREE.Vector3(10.8, 1.72, -5.6), b: new THREE.Vector3(17.1, 1.72, -4.3), speed: 0.14, offset: 0.65 },
      { a: new THREE.Vector3(-2.6, 0.08, -2.5), b: new THREE.Vector3(0.4, 0.08, 9.2), speed: 0.09, offset: 0.2 }
    ];
    patrolRoutes.forEach(function (route) {
      var mesh = buildGuardMesh();
      scene.add(mesh);
      guards.push({
        mesh: mesh,
        a: route.a,
        b: route.b,
        speed: route.speed,
        offset: route.offset
      });
    });

    return {
      buildings: buildings,
      pickables: pickables,
      animRefs: {
        watermillWheel: watermillWheel,
        fountainJet: fountainJet,
        smokeOrigins: smokeOrigins,
        banners: banners,
        guards: guards
      }
    };
  };
})();
