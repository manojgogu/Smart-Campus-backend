const rateLimit = require('express-rate-limit');

const authLimiter = rateLimit({ windowMs: 15 * 60 * 1000, max: 60, message: { message: 'Too many attempts, try again later.' } });
const reportLimiter = rateLimit({ windowMs: 5 * 60 * 1000, max: 20, message: { message: 'Too many reports in a short time.' } });

module.exports = { authLimiter, reportLimiter };
