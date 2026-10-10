require('dotenv').config();
const mongoose = require('mongoose');
const User = require('../models/User');

async function run() {
  const email = process.env.ADMIN_SEED_EMAIL || 'admin@campus.edu';
  const password = process.env.ADMIN_SEED_PASSWORD || 'Admin@123';
  await mongoose.connect(process.env.MONGO_URI);
  let admin = await User.findOne({ email });
  if (admin) {
    admin.role = 'admin'; admin.isActive = true; admin.password = password;
    await admin.save();
    console.log(`Admin updated: ${email}`);
  } else {
    await User.create({ name: 'Campus Admin', email, password, role: 'admin' });
    console.log(`Admin created: ${email}`);
  }
  await mongoose.disconnect();
}
run().catch(e => { console.error(e); process.exit(1); });
