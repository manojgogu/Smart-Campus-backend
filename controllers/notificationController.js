const Notification = require('../models/Notification');

async function list(req, res, next) {
  try {
    const { limit = 20 } = req.query;
    const notifs = await Notification.find({
      $or: [{ user: req.user._id }, { user: null, role: { $in: [req.user.role, 'all'] } }]
    }).sort({ createdAt: -1 }).limit(Math.min(+limit, 50));
    res.json({ notifications: notifs });
  } catch (e) { next(e); }
}

async function markRead(req, res, next) {
  try {
    await Notification.updateMany({ user: req.user._id, read: false }, { read: true });
    res.json({ ok: true });
  } catch (e) { next(e); }
}

module.exports = { list, markRead };
