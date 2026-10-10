const jwt = require('jsonwebtoken');
const User = require('../models/User');

let io = null;
const userSockets = new Map(); // userId -> Set(socketId)

function initSocket(server) {
  io = require('socket.io')(server, { cors: { origin: process.env.CLIENT_URL || 'http://localhost:5173', methods: ['GET', 'POST'] } });

  io.use(async (socket, next) => {
    try {
      const token = socket.handshake.auth?.token;
      if (!token) return next(new Error('No token'));
      const payload = jwt.verify(token, process.env.JWT_SECRET);
      const user = await User.findById(payload.id);
      if (!user || !user.isActive) return next(new Error('Inactive'));
      socket.user = { id: user._id.toString(), role: user.role };
      next();
    } catch { next(new Error('Auth failed')); }
  });

  io.on('connection', (socket) => {
    const { id, role } = socket.user;
    socket.join(`role:${role}`);
    socket.join(`user:${id}`);
    if (!userSockets.has(id)) userSockets.set(id, new Set());
    userSockets.get(id).add(socket.id);
    socket.on('disconnect', () => userSockets.get(id)?.delete(socket.id));
  });

  return io;
}

function getIO() { return io; }

function emitToRoles(roles, event, payload) {
  if (!io) return;
  roles.forEach(r => io.to(`role:${r}`).emit(event, payload));
}

function emitToUser(userId, event, payload) {
  if (!io) return;
  io.to(`user:${userId}`).emit(event, payload);
}

module.exports = { initSocket, getIO, emitToRoles, emitToUser };
