const router = require('express').Router();
const { auth, authorize } = require('../middleware/auth');
const c = require('../controllers/adminController');

router.get('/', auth, c.teams);
router.post('/', auth, authorize('admin'), c.createTeam);

module.exports = router;
