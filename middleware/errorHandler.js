// Centralized error handler (must be last middleware)
module.exports = function errorHandler(err, req, res, _next) {
  console.error(err);
  if (res.headersSent) return;
  const status = err.status || 500;
  res.status(status).json({ message: err.message || 'Server error' });
};
