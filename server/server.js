'use strict';

require('dotenv').config();
const express = require('express');
const http = require('http');
const { Server } = require('socket.io');
const path = require('path');

const app = express();
const server = http.createServer(app);

const LOGIN = process.env.LOGIN;
const PASSWORD = process.env.PASSWORD;
const PORT = Number(process.env.PORT) || 3000;

if (!LOGIN || !PASSWORD) {
    console.error('Set LOGIN and PASSWORD in .env');
    process.exit(1);
}

app.use(express.static(path.join(__dirname, 'public')));

app.get('/health', (req, res) => {
    res.type('text/plain').send('ok');
});

const io = new Server(server, {
    pingInterval: 30000,
    pingTimeout: 60000,
    cors: { origin: '*' }
});

io.use((socket, next) => {
    const login = socket.handshake.query.login;
    const pass = socket.handshake.query.password || socket.handshake.query.pass;
    if (login === LOGIN && pass === PASSWORD) {
        return next();
    }
    next(new Error('Auth failed'));
});

io.on('connection', (socket) => {
    const userRoom = String(socket.handshake.query.login);
    socket.join(userRoom);

    const relay = (eventName, ...args) => {
        socket.to(userRoom).emit(eventName, ...args);
    };

    socket.on('request_gps', () => relay('request_gps'));

    socket.on('gps_data', (...args) => relay('gps_data', ...args));

    socket.on('disconnect', () => {});
});

server.listen(PORT, () => {
    console.log('Server listening on port', PORT);
});
