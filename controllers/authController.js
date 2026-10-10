const jwt = require('jsonwebtoken');
const { body, validationResult } = require('express-validator');
const User = require('../models/User');
const { audit } = require('../utils/helpers');

const sign = (user) => jwt.sign({ id: user._id, role: user.role }, process.env.JWT_SECRET, { expiresIn: process.env.JWT_EXPIRES_IN || '7d' });

const registerValidators = [
  body('name').trim().isLength({ min: 2 }).withMessage('Name required'),
  body('email').isEmail().normalizeEmail().withMessage('Valid email required'),
  body('password').isLength({ min: 8 }).withMessage('Password min 8 chars'),
  body('role').optional().isIn(['student', 'faculty', 'security']).withMessage('Admin cannot self-register')
];

async function register(req, res, next) {
  try {
    const errors = validationResult(req);
    if (!errors.isEmpty()) return res.status(400).json({ message: errors.array()[0].msg });
    const { name, email, password, role = 'student', phone = '' } = req.body;
    if (role === 'admin') return res.status(403).json({ message: 'Admin accounts are created via seed script only.' });
    const exists = await User.findOne({ email: email.toLowerCase() });
    if (exists) return res.status(409).json({ message: 'Email already registered' });
    const user = await User.create({ name, email: email.toLowerCase(), password, role, phone });
    audit('REGISTER', user);
    res.status(201).json({ token: sign(user), user: user.toSafeJSON() });
  } catch (e) { next(e); }
}

async function login(req, res, next) {
  try {
    const { email, password } = req.body;
    if (!email || !password) return res.status(400).json({ message: 'Email and password required' });
    const user = await User.findOne({ email: email.toLowerCase() }).select('+password');
    if (!user || !(await user.comparePassword(password))) return res.status(401).json({ message: 'Invalid credentials' });
    if (!user.isActive) return res.status(403).json({ message: 'Account deactivated. Contact admin.' });
    audit('LOGIN', user);
    res.json({ token: sign(user), user: user.toSafeJSON() });
  } catch (e) { next(e); }
}

async function me(req, res) { res.json({ user: req.user.toSafeJSON() }); }

async function updateProfile(req, res, next) {
  try {
    const { name, phone } = req.body;
    if (name) req.user.name = name;
    if (phone !== undefined) req.user.phone = phone;
    await req.user.save();
    res.json({ user: req.user.toSafeJSON() });
  } catch (e) { next(e); }
}

module.exports = { registerValidators, register, login, me, updateProfile };
