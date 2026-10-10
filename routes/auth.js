const router = require('express').Router();
const { registerValidators, register, login, me, updateProfile } = require('../controllers/authController');
const { auth } = require('../middleware/auth');
const { authLimiter } = require('../middleware/rateLimit');

router.post('/register', authLimiter, registerValidators, register);
router.post('/login', authLimiter, login);
router.get('/me', auth, me);
router.patch('/profile', auth, updateProfile);

module.exports = router;
