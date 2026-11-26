-- Crownfall part 10: line world sites up with the painted realm maps (keeps them out of lakes and lava)
UPDATE world_nodes SET map_x = 46, map_y = 54 WHERE id = 'bastion_5';
UPDATE world_nodes SET map_x = 80, map_y = 82 WHERE id = 'bastion_7';
UPDATE world_nodes SET map_x = 62, map_y = 31 WHERE id = 'res_timber_1';
UPDATE world_nodes SET map_x = 40, map_y = 47 WHERE id = 'res_gold_1';
UPDATE landmarks SET map_x = 90, map_y = 46 WHERE id = 'realm-3:cold';
UPDATE landmarks SET map_x = 74, map_y = 92 WHERE id = 'realm-3:lumber';
SELECT id, map_x, map_y FROM world_nodes WHERE id IN ('bastion_5','bastion_7','res_timber_1','res_gold_1')
UNION ALL
SELECT id, map_x, map_y FROM landmarks WHERE id IN ('realm-3:cold','realm-3:lumber');
