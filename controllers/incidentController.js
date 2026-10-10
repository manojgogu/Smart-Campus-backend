const { body, validationResult } = require('express-validator');
const Incident = require('../models/Incident');
const Notification = require('../models/Notification');
const User = require('../models/User');
const { generateIncidentId, audit } = require('../utils/helpers');
const { emitToRoles, emitToUser } = require('../sockets/io');

const createValidators = [
  body('category').isIn(['Medical', 'Fire', 'Accident', 'Security Threat', 'Suspicious Activity', 'Other']),
  body('priority').optional().isIn(['Critical', 'High', 'Medium', 'Low']),
  body('description').trim().isLength({ min: 10 }).withMessage('Description min 10 chars'),
  body('locationName').trim().notEmpty().withMessage('Location required')
];

async function notifyRoles(roles, title, message, incident) {
  const docs = roles.map(role => ({ role, title, message, incident: incident?._id }));
  await Notification.insertMany(docs);
  emitToRoles(roles, 'notification', { title, message, incidentId: incident?.incidentId });
}

async function createIncident(req, res, next) {
  try {
    const errors = validationResult(req);
    if (!errors.isEmpty()) return res.status(400).json({ message: errors.array()[0].msg });
    const { category, priority = 'Medium', description, locationName, contactNumber = '', lat, lng } = req.body;
    const incident = await Incident.create({
      incidentId: generateIncidentId(),
      reporter: req.user._id,
      category, priority, description, locationName, contactNumber,
      coordinates: (lat && lng) ? { lat: parseFloat(lat), lng: parseFloat(lng) } : undefined,
      imageUrl: req.file ? `/uploads/${req.file.filename}` : '',
      history: [{ status: 'Reported', note: 'Incident reported', by: req.user._id }]
    });
    await incident.populate('reporter', 'name email phone');
    audit('INCIDENT_CREATE', req.user, incident.incidentId);
    // Real-time: alert security + admin only
    emitToRoles(['security', 'admin'], 'incident:new', incident);
    await notifyRoles(['security', 'admin'], `New ${priority} incident`, `${incident.incidentId} · ${category} at ${locationName}`, incident);
    res.status(201).json({ incident });
  } catch (e) { next(e); }
}

async function listIncidents(req, res, next) {
  try {
    const { priority, status, category, q, active, limit = 50, page = 1 } = req.query;
    const filter = {};
    if (priority) filter.priority = priority;
    if (status) filter.status = status;
    if (category) filter.category = category;
    if (active === 'true') filter.status = { $ne: 'Resolved' };
    if (q) filter.$or = [{ incidentId: new RegExp(q, 'i') }, { locationName: new RegExp(q, 'i') }, { description: new RegExp(q, 'i') }];
    // Students/faculty normally use /my; this route is security+admin
    const incidents = await Incident.find(filter).populate('reporter', 'name email phone')
      .sort({ createdAt: -1 }).limit(Math.min(+limit, 200)).skip((+page - 1) * +limit);
    const total = await Incident.countDocuments(filter);
    res.json({ incidents, total });
  } catch (e) { next(e); }
}

async function myIncidents(req, res, next) {
  try {
    const { limit = 20 } = req.query;
    const incidents = await Incident.find({ reporter: req.user._id }).sort({ createdAt: -1 }).limit(Math.min(+limit, 100));
    res.json({ incidents });
  } catch (e) { next(e); }
}

async function myStats(req, res, next) {
  try {
    const total = await Incident.countDocuments({ reporter: req.user._id });
    const active = await Incident.countDocuments({ reporter: req.user._id, status: { $ne: 'Resolved' } });
    res.json({ stats: { total, active, resolved: total - active } });
  } catch (e) { next(e); }
}

async function getOne(req, res, next) {
  try {
    const inc = await Incident.findOne({ incidentId: req.params.id }).populate('reporter', 'name email phone').populate('history.by', 'name');
    if (!inc) return res.status(404).json({ message: 'Incident not found' });
    const isOwner = inc.reporter._id.toString() === req.user._id.toString();
    if (!isOwner && !['security', 'admin'].includes(req.user.role)) return res.status(403).json({ message: 'Forbidden' });
    // Hide reporter contact from non-authorized viewers: only owner/security/admin already checked
    res.json({ incident: inc });
  } catch (e) { next(e); }
}

async function track(req, res, next) {
  try {
    const inc = await Incident.findOne({ incidentId: req.params.id }).populate('history.by', 'name');
    if (!inc) return res.status(404).json({ message: 'Not found' });
    const isOwner = inc.reporter.toString() === req.user._id.toString();
    if (!isOwner && !['security', 'admin'].includes(req.user.role)) return res.status(403).json({ message: 'Forbidden' });
    res.json({ incident: inc });
  } catch (e) { next(e); }
}

const VALID = ['Reported', 'Acknowledged', 'Assigned', 'In Progress', 'Resolved'];

async function updateStatus(req, res, next) {
  try {
    const { status, note = '', assignedTo } = req.body;
    if (!VALID.includes(status)) return res.status(400).json({ message: 'Invalid status' });
    const inc = await Incident.findOne({ incidentId: req.params.id }).populate('reporter', 'name email');
    if (!inc) return res.status(404).json({ message: 'Not found' });
    inc.status = status;
    if (assignedTo !== undefined) { inc.assignedTo = assignedTo; if (status === 'Reported') inc.status = 'Assigned'; }
    if (status === 'Acknowledged' && !inc.acknowledgedAt) inc.acknowledgedAt = new Date();
    if (status === 'Assigned' && !inc.assignedAt) inc.assignedAt = new Date();
    if (status === 'Resolved') inc.resolvedAt = new Date();
    inc.history.push({ status: inc.status, note, by: req.user._id });
    await inc.save();
    await inc.populate('history.by', 'name');
    audit('INCIDENT_STATUS', req.user, `${inc.incidentId} -> ${inc.status}`);
    emitToRoles(['security', 'admin'], 'incident:updated', inc);
    emitToUser(inc.reporter._id?.toString() || inc.reporter.toString(), 'incident:updated', inc);
    await Notification.create({ user: inc.reporter._id || inc.reporter, title: `Incident ${inc.incidentId}: ${inc.status}`, message: note || `Status changed to ${inc.status}`, incident: inc._id });
    emitToUser((inc.reporter._id || inc.reporter).toString(), 'notification', { title: `Incident ${inc.status}`, message: `${inc.incidentId} is now ${inc.status}` });
    res.json({ incident: inc });
  } catch (e) { next(e); }
}

module.exports = { createValidators, createIncident, listIncidents, myIncidents, myStats, getOne, track, updateStatus };
