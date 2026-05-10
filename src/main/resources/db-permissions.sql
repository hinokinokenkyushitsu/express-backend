-- ============================================================
-- 数据库权限规则
-- 负责人：同学 D（核心任务 3：配置数据库权限规则，确保数据安全）
-- ============================================================
-- 设计原则：最小权限原则
--   - 应用账号仅 SELECT/INSERT/UPDATE，禁止 DELETE/DROP/ALTER
--   - 备份账号独立，仅 SELECT
--   - 删除使用逻辑删除（deleted 字段），不允许物理删除
-- ============================================================

DROP USER IF EXISTS 'express_app'@'%';
CREATE USER 'express_app'@'%' IDENTIFIED BY 'CHANGE_THIS_PASSWORD';

GRANT SELECT, INSERT, UPDATE ON express_db.users  TO 'express_app'@'%';
GRANT SELECT, INSERT, UPDATE ON express_db.orders TO 'express_app'@'%';

DROP USER IF EXISTS 'express_readonly'@'%';
CREATE USER 'express_readonly'@'%' IDENTIFIED BY 'CHANGE_THIS_PASSWORD';
GRANT SELECT ON express_db.* TO 'express_readonly'@'%';

FLUSH PRIVILEGES;

-- 验证：用 express_app 登录后
--   DROP TABLE orders;          -- ❌ 应被拒绝
--   DELETE FROM orders;         -- ❌ 应被拒绝
--   UPDATE orders SET status=1 WHERE id=1 AND status=0;  -- ✅ 抢单 SQL
