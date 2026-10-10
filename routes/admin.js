const router = require('express').Router();
const { auth, authorize } = require('../middleware/auth');
const c = require('../controllers/adminController');

router.get('/stats', auth, authorize('admin'), c.stats);
router.get('/users', auth, authorize('admin'), c.listUsers);
router.patch('/users/:id/toggle', auth, authorize('admin'), c.toggleUser);
router.get('/teams', auth, authorize('security', 'admin'), (req, res, next) => c.teams(req, res, next));
router.post('/teams', auth, authorize('admin'), c.createTeam);
router.get('/locations', auth, c.locations);
router.post('/locations', auth, authorize('admin'), c.createLocation);

module.exports = router;
