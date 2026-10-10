const mongoose = require('mongoose');

module.exports = mongoose.model('ResponseTeam', new mongoose.Schema({
  name: { type: String, required: true, unique: true },
  members: [{ type: mongoose.Schema.Types.ObjectId, ref: 'User' }],
  contact: { type: String, default: '' },
  isActive: { type: Boolean, default: true }
}, { timestamps: true }));
