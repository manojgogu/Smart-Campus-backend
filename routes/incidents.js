const router = require('express').Router();
const { auth, authorize } = require('../middleware/auth');
const upload = require('../middleware/upload');
const { reportLimiter } = require('../middleware/rateLimit');
const c = require('../controllers/incidentController');

router.post('/', auth, reportLimiter, upload.single('image'), c.createValidators, c.createIncident);
router.get('/my', auth, c.myIncidents);
router.get('/my/stats', auth, c.myStats);
router.get('/track/:id', auth, c.track);
router.get('/', auth, authorize('security', 'admin'), c.listIncidents);
router.get('/:id', auth, c.getOne);
router.patch('/:id/status', auth, authorize('security', 'admin'), c.updateStatus);

module.exports = router;
