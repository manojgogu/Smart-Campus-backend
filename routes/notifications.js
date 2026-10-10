const router = require('express').Router();
const { auth } = require('../middleware/auth');
const c = require('../controllers/notificationController');

router.get('/', auth, c.list);
router.patch('/read', auth, c.markRead);

module.exports = router;
