require('dotenv').config();
const mongoose = require('mongoose');
const User = require('../models/User');
const Incident = require('../models/Incident');
const ResponseTeam = require('../models/ResponseTeam');
const CampusLocation = require('../models/CampusLocation');
const { generateIncidentId } = require('../utils/helpers');

async function run() {
  await mongoose.connect(process.env.MONGO_URI || 'mongodb://127.0.0.1:27017/campussafe');
  console.log('Seeding CampusSafe (demo data — clearly labeled)...');
  await Promise.all([User.deleteMany({}), Incident.deleteMany({}), ResponseTeam.deleteMany({}), CampusLocation.deleteMany({})]);

  const users = await User.create([
    { name: 'Demo Student', email: 'student@campus.edu', password: 'Password@123', role: 'student', phone: '+91-9000000001' },
    { name: 'Demo Faculty', email: 'faculty@campus.edu', password: 'Password@123', role: 'faculty', phone: '+91-9000000002' },
    { name: 'Demo Security', email: 'security@campus.edu', password: 'Password@123', role: 'security', phone: '+91-9000000003' },
    { name: 'Campus Admin', email: process.env.ADMIN_SEED_EMAIL || 'admin@campus.edu', password: process.env.ADMIN_SEED_PASSWORD || 'Admin@123', role: 'admin' }
  ]);
  const [student, faculty, security] = users;

  await CampusLocation.create([
    { name: 'Main Academic Block', coordinates: { lat: 28.6139, lng: 77.209 } },
    { name: 'Library', coordinates: { lat: 28.6145, lng: 77.2095 } },
    { name: 'Boys Hostel A', coordinates: { lat: 28.615, lng: 77.21 } },
    { name: 'Cafeteria', coordinates: { lat: 28.6135, lng: 77.2085 } },
    { name: 'Sports Complex', coordinates: { lat: 28.616, lng: 77.211 } }
  ]);

  await ResponseTeam.create([
    { name: 'Alpha Response', members: [security._id], contact: '+91-9000000003' },
    { name: 'Night Patrol', members: [security._id], contact: '+91-9000000004' }
  ]);

  const samples = [
    { reporter: student._id, category: 'Medical', priority: 'Critical', description: 'Demo: student fainted near library entrance, needs first aid.', locationName: 'Library', status: 'In Progress', coordinates: { lat: 28.6145, lng: 77.2095 } },
    { reporter: faculty._id, category: 'Fire', priority: 'High', description: 'Demo: smoke detected in cafeteria kitchen exhaust.', locationName: 'Cafeteria', status: 'Acknowledged', coordinates: { lat: 28.6135, lng: 77.2085 } },
    { reporter: student._id, category: 'Suspicious Activity', priority: 'Medium', description: 'Demo: unknown individual loitering near parking lot at night.', locationName: 'Parking Lot', status: 'Reported' },
    { reporter: student._id, category: 'Accident', priority: 'Low', description: 'Demo: minor bicycle skid near sports complex, no injury.', locationName: 'Sports Complex', status: 'Resolved', coordinates: { lat: 28.616, lng: 77.211 } }
  ];
  for (const s of samples) {
    const now = new Date();
    await Incident.create({
      ...s,
      incidentId: generateIncidentId(),
      contactNumber: '+91-9000000001',
      history: [{ status: 'Reported', note: 'Demo seeded report', at: now }],
      acknowledgedAt: s.status !== 'Reported' ? now : undefined,
      resolvedAt: s.status === 'Resolved' ? now : undefined,
      createdAt: now, updatedAt: now
    });
  }
  console.log('Seed complete. Demo logins: student/faculty/security@campus.edu / Password@123');
  await mongoose.disconnect();
}

run().catch(e => { console.error(e); process.exit(1); });
