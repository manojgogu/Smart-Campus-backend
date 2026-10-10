const mongoose = require('mongoose');

module.exports = mongoose.model('CampusLocation', new mongoose.Schema({
  name: { type: String, required: true, unique: true },
  description: { type: String, default: '' },
  coordinates: { lat: Number, lng: Number },
  isActive: { type: Boolean, default: true }
}, { timestamps: true }));
