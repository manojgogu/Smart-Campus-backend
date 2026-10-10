const User = require('../models/User');
const Incident = require('../models/Incident');
const ResponseTeam = require('../models/ResponseTeam');
const CampusLocation = require('../models/CampusLocation');
const { audit } = require('../utils/helpers');

async function stats(req, res, next) {
  try {
    const [total, active, users] = await Promise.all([
      Incident.countDocuments(),
      Incident.countDocuments({ status: { $ne: 'Resolved' } }),
      User.countDocuments()
    ]);
    const byCategory = await Incident.aggregate([{ $group: { _id: '$category', count: { $sum: 1 } } }]);
    const byStatus = await Incident.aggregate([{ $group: { _id: '$status', count: { $sum: 1 } } }]);
    const byLocation = await Incident.aggregate([{ $group: { _id: '$locationName', count: { $sum: 1 } } }, { $sort: { count: -1 } }]);
    const overTime = await Incident.aggregate([
      { $group: { _id: { $dateToString: { format: '%Y-%m-%d', date: '$createdAt' } }, count: { $sum: 1 } } },
      { $sort: { _id: 1 } }, { $limit: 30 }
    ]);
    // Avg response time from actual timestamps (ack or resolve minus created)
    const resolved = await Incident.find({ acknowledgedAt: { $exists: true } }).select('createdAt acknowledgedAt').lean();
    let avg = null;
    if (resolved.length) {
      const sum = resolved.reduce((a, i) => a + (new Date(i.acknowledgedAt) - new Date(i.createdAt)), 0);
      avg = Math.round(sum / resolved.length / 60000);
    }
    res.json({ counts: { total, active, resolved: total - active, users }, byCategory, byStatus, byLocation, overTime, avgResponseMinutes: avg });
  } catch (e) { next(e); }
}

async function listUsers(req, res, next) {
  try {
    const { limit = 50, page = 1 } = req.query;
    const users = await User.find().sort({ createdAt: -1 }).limit(Math.min(+limit, 200)).skip((+page - 1) * +limit);
    const total = await User.countDocuments();
    res.json({ users: users.map(u => u.toSafeJSON()), total });
  } catch (e) { next(e); }
}

async function toggleUser(req, res, next) {
  try {
    const u = await User.findById(req.params.id);
    if (!u) return res.status(404).json({ message: 'User not found' });
    if (u._id.toString() === req.user._id.toString()) return res.status(400).json({ message: 'Cannot deactivate yourself' });
    u.isActive = !u.isActive;
    await u.save();
    audit('USER_TOGGLE', req.user, `${u.email} -> ${u.isActive}`);
    res.json({ user: u.toSafeJSON() });
  } catch (e) { next(e); }
}

async function teams(req, res, next) {
  try { res.json({ teams: await ResponseTeam.find().populate('members', 'name email') }); }
  catch (e) { next(e); }
}

async function createTeam(req, res, next) {
  try {
    const t = await ResponseTeam.create(req.body);
    audit('TEAM_CREATE', req.user, t.name);
    res.status(201).json({ team: t });
  } catch (e) { next(e); }
}

async function locations(req, res, next) {
  try { res.json({ locations: await CampusLocation.find() }); }
  catch (e) { next(e); }
}

async function createLocation(req, res, next) {
  try {
    const l = await CampusLocation.create(req.body);
    audit('LOCATION_CREATE', req.user, l.name);
    res.status(201).json({ location: l });
  } catch (e) { next(e); }
}

module.exports = { stats, listUsers, toggleUser, teams, createTeam, locations, createLocation };
