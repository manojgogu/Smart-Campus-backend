const mongoose = require('mongoose');

const historySchema = new mongoose.Schema({
  status: { type: String, required: true },
  note: { type: String, default: '' },
  by: { type: mongoose.Schema.Types.ObjectId, ref: 'User' },
  at: { type: Date, default: Date.now }
}, { _id: false });

const incidentSchema = new mongoose.Schema({
  incidentId: { type: String, required: true, unique: true, index: true },
  reporter: { type: mongoose.Schema.Types.ObjectId, ref: 'User', required: true },
  category: { type: String, enum: ['Medical', 'Fire', 'Accident', 'Security Threat', 'Suspicious Activity', 'Other'], required: true },
  priority: { type: String, enum: ['Critical', 'High', 'Medium', 'Low'], default: 'Medium' },
  description: { type: String, required: true, minlength: 10 },
  locationName: { type: String, required: true },
  coordinates: { lat: Number, lng: Number },
  imageUrl: { type: String, default: '' },
  contactNumber: { type: String, default: '' },
  assignedTo: { type: String, default: '' },
  assignedTeam: { type: mongoose.Schema.Types.ObjectId, ref: 'ResponseTeam' },
  status: { type: String, enum: ['Reported', 'Acknowledged', 'Assigned', 'In Progress', 'Resolved'], default: 'Reported' },
  history: [historySchema],
  acknowledgedAt: Date,
  assignedAt: Date,
  resolvedAt: Date
}, { timestamps: true });

incidentSchema.index({ status: 1, priority: 1, createdAt: -1 });

module.exports = mongoose.model('Incident', incidentSchema);
