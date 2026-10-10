const mongoose = require('mongoose');

module.exports = mongoose.model('Notification', new mongoose.Schema({
  user: { type: mongoose.Schema.Types.ObjectId, ref: 'User' }, // null = broadcast to role
  role: { type: String, enum: ['student', 'faculty', 'security', 'admin', 'all'], default: 'all' },
  title: { type: String, required: true },
  message: { type: String, default: '' },
  incident: { type: mongoose.Schema.Types.ObjectId, ref: 'Incident' },
  read: { type: Boolean, default: false }
}, { timestamps: true }));
