-- ============================================================
-- 会员等级阈值字典数据初始化
-- 执行本脚本后，会员等级阈值可通过字典表配置，无需修改代码
--
-- dic_code = 'huiyuandengji_threshold'
-- code_index = 等级编号（1, 2, 3）
-- beizhu = 该等级所需的最低累计积分
--
-- 注意：如果这些记录不存在，系统会回退到代码中的默认值
--       （等级1: 0, 等级2: 10000, 等级3: 100000）
-- ============================================================

-- 先清理可能存在的旧数据
DELETE FROM dictionary WHERE dic_code = 'huiyuandengji_threshold';

-- 等级 1：普通会员（积分 >= 0）
INSERT INTO dictionary (dic_code, dic_name, code_index, index_name, super_id, beizhu, create_time)
VALUES ('huiyuandengji_threshold', '会员等级积分阈值', 1, '普通会员', 0, '0', NOW());

-- 等级 2：银卡会员（积分 >= 10000）
INSERT INTO dictionary (dic_code, dic_name, code_index, index_name, super_id, beizhu, create_time)
VALUES ('huiyuandengji_threshold', '会员等级积分阈值', 2, '银卡会员', 0, '10000', NOW());

-- 等级 3：金卡会员（积分 >= 100000）
INSERT INTO dictionary (dic_code, dic_name, code_index, index_name, super_id, beizhu, create_time)
VALUES ('huiyuandengji_threshold', '会员等级积分阈值', 3, '金卡会员', 0, '100000', NOW());

-- ============================================================
-- 如需新增等级 4（钻石会员，积分 >= 1000000），只需执行：
-- INSERT INTO dictionary (dic_code, dic_name, code_index, index_name, super_id, beizhu, create_time)
-- VALUES ('huiyuandengji_threshold', '会员等级积分阈值', 4, '钻石会员', 0, '1000000', NOW());
--
-- 调整阈值只需 UPDATE beizhu 字段即可，无需重启应用
-- ============================================================
