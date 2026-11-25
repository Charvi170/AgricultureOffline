'use strict';

const http = require('http');
const https = require('https');
const { URL } = require('url');
const fs = require('fs');
const path = require('path');

loadEnv(path.join(__dirname, '.env'));

const PORT = Number(process.env.PORT || 8080);
const PROVIDER = (process.env.SMS_PROVIDER || 'msg91').toLowerCase();
const COOLDOWN_SECONDS = Number(process.env.COOLDOWN_SECONDS || 60);
const MAX_SENDS_PER_HOUR = Number(process.env.MAX_SENDS_PER_HOUR || 5);
const LOGIN_NOTIFY_SECRET = process.env.LOGIN_NOTIFY_SECRET || '';

// Map to track rate limiting and duplicate prevention: phone -> { lastSentAt, sends: [], lastEventKey }
const loginHistory = new Map();
// Map to track processed request IDs to prevent duplicate processing across network retries: requestId -> timestamp
const processedRequests = new Map();

function loadEnv(filePath) {
  if (!fs.existsSync(filePath)) return;
  const text = fs.readFileSync(filePath, 'utf8');
  for (const rawLine of text.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line || line.startsWith('#')) continue;
    const idx = line.indexOf('=');
    if (idx === -1) continue;
    const key = line.slice(0, idx).trim();
    const value = line.slice(idx + 1).trim();
    if (!(key in process.env)) process.env[key] = value;
  }
}

function json(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    'Content-Type': 'application/json',
    'Content-Length': Buffer.byteLength(payload)
  });
  res.end(payload);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on('data', (chunk) => chunks.push(chunk));
    req.on('end', () => {
      const raw = Buffer.concat(chunks).toString('utf8');
      if (!raw) return resolve({});
      try {
        resolve(JSON.parse(raw));
      } catch (error) {
        reject(error);
      }
    });
    req.on('error', reject);
  });
}

function normalizePhone(countryCode, phone) {
  const dial = String(countryCode || '').replace(/[^\d]/g, '');
  const national = String(phone || '').replace(/[^\d]/g, '');
  if (!dial || !national) return null;
  if (dial === '91' && national.length !== 10) return null;
  if (national.length < 8 || national.length > 15) return null;
  return dial + national;
}

function providerConfigured() {
  if (PROVIDER === 'fast2sms') {
    return Boolean(process.env.FAST2SMS_API_KEY);
  }
  if (PROVIDER === '2factor') {
    return Boolean(process.env.TWOFACTOR_API_KEY);
  }
  if (PROVIDER === 'msg91') {
    return Boolean(process.env.MSG91_AUTHKEY && (process.env.MSG91_LOGIN_TEMPLATE_ID || process.env.MSG91_TEMPLATE_ID));
  }
  return false;
}

function httpsRequest(urlString, options, body) {
  return new Promise((resolve, reject) => {
    const url = new URL(urlString);
    const req = https.request(
      {
        hostname: url.hostname,
        path: url.pathname + url.search,
        method: options.method || 'GET',
        headers: options.headers || {}
      },
      (res) => {
        const chunks = [];
        res.on('data', (chunk) => chunks.push(chunk));
        res.on('end', () => {
          const text = Buffer.concat(chunks).toString('utf8');
          let parsed = {};
          try {
            parsed = text ? JSON.parse(text) : {};
          } catch (_error) {
            parsed = { raw: text };
          }
          resolve({ status: res.statusCode || 500, body: parsed, text });
        });
      }
    );
    req.on('error', reject);
    if (body) req.write(body);
    req.end();
  });
}

function checkRateLimitAndDuplicate(phone, eventKey) {
  const record = loginHistory.get(phone);
  if (!record) return { allowed: true };

  // Duplicate login event check
  if (eventKey && record.lastEventKey === eventKey && Date.now() - record.lastSentAt < 120000) {
    return { allowed: false, reason: 'duplicate' };
  }

  // Cooldown check
  if (Date.now() - record.lastSentAt < COOLDOWN_SECONDS * 1000) {
    return { allowed: false, reason: 'cooldown' };
  }

  // Hourly limit check
  const hourAgo = Date.now() - 60 * 60 * 1000;
  record.sends = (record.sends || []).filter((ts) => ts > hourAgo);
  if (record.sends.length >= MAX_SENDS_PER_HOUR) {
    return { allowed: false, reason: 'hourly' };
  }

  return { allowed: true };
}

async function sendLoginSuccessFast2Sms(phone, dateStr, timeStr) {
  const apiKey = process.env.FAST2SMS_API_KEY;
  const nationalNumber = phone.startsWith('91') && phone.length === 12 ? phone.slice(2) : phone;
  const message = `AgriVision-Edge: Welcome to AgriVision! Successful login on ${dateStr} at ${timeStr}.`;

  const payload = JSON.stringify({
    route: 'q',
    message: message,
    language: 'english',
    flash: 0,
    numbers: nationalNumber
  });

  const result = await httpsRequest('https://www.fast2sms.com/dev/bulkV2', {
    method: 'POST',
    headers: {
      authorization: apiKey,
      'Content-Type': 'application/json',
      'Content-Length': Buffer.byteLength(payload)
    }
  }, payload);

  const accepted = result.status >= 200 && result.status < 300 &&
    (result.body.return === true || String(result.body.status_code || '') === '200');

  return {
    accepted,
    providerRef: (result.body.request_id || (result.body.message && result.body.message[0])) || null,
    status: result.status
  };
}

async function sendLoginSuccessMsg91(phone, dateStr, timeStr) {
  const templateId = process.env.MSG91_LOGIN_TEMPLATE_ID || process.env.MSG91_TEMPLATE_ID;

  // Standard MSG91 Flow API for DLT approved templates
  const flowPayload = JSON.stringify({
    template_id: templateId,
    short_url: '0',
    recipients: [
      {
        mobiles: phone,
        date: dateStr,
        time: timeStr
      }
    ]
  });

  const flowResult = await httpsRequest('https://control.msg91.com/api/v5/flow/', {
    method: 'POST',
    headers: {
      authkey: process.env.MSG91_AUTHKEY,
      'Content-Type': 'application/json',
      'Content-Length': Buffer.byteLength(flowPayload)
    }
  }, flowPayload);

  const accepted = flowResult.status >= 200 && flowResult.status < 300 &&
    String(flowResult.body.type || flowResult.body.message || '').toLowerCase() !== 'error';

  return {
    accepted,
    providerRef: flowResult.body.request_id || flowResult.body.message || null,
    status: flowResult.status
  };
}

async function sendLoginSuccess2Factor(phone, dateStr, timeStr) {
  const apiKey = process.env.TWOFACTOR_API_KEY;
  const senderId = encodeURIComponent(process.env.TWOFACTOR_SENDER_ID || 'AGRIVS');
  const msg = encodeURIComponent(`AgriVision-Edge: You have successfully logged in on ${dateStr} at ${timeStr}.`);
  const url = `https://2factor.in/API/R1/?module=TRANS_SMS&apikey=${apiKey}&to=${phone}&from=${senderId}&msg=${msg}`;

  const result = await httpsRequest(url, { method: 'GET' });
  const accepted = result.status >= 200 && result.status < 300 &&
    String(result.body.Status || '').toLowerCase() === 'success';

  return { accepted, providerRef: result.body.Details || null, status: result.status };
}

async function handleLoginSuccess(req, res) {
  // Validate optional secret if configured
  if (LOGIN_NOTIFY_SECRET) {
    const authHeader = req.headers['x-notify-secret'] || req.headers['authorization'];
    if (authHeader !== LOGIN_NOTIFY_SECRET && authHeader !== `Bearer ${LOGIN_NOTIFY_SECRET}`) {
      return json(res, 401, { ok: false, code: 'unauthorized', message: 'Unauthorized client request.' });
    }
  }

  const body = await readBody(req);
  const phone = normalizePhone(body.countryCode, body.phone);
  if (!phone) {
    return json(res, 400, { ok: false, code: 'invalid_phone', message: 'Enter a valid mobile number.' });
  }

  // Idempotency / duplicate request check by requestId
  if (body.requestId && processedRequests.has(body.requestId)) {
    return json(res, 200, {
      ok: true,
      code: 'duplicate_prevented',
      message: 'Request already processed.',
      requestId: body.requestId
    });
  }

  const now = new Date();
  const dateStr = String(body.date || now.toLocaleDateString('en-GB')).trim();
  const timeStr = String(body.time || now.toLocaleTimeString('en-US', { hour: '2-digit', minute: '2-digit' })).trim();
  const eventKey = `${phone}_${dateStr}_${timeStr}`;

  if (!providerConfigured()) {
    return json(res, 503, {
      ok: false,
      code: 'provider_unconfigured',
      message: `SMS provider '${PROVIDER}' is not configured. Please set credentials in .env file.`
    });
  }

  const rateCheck = checkRateLimitAndDuplicate(phone, eventKey);
  if (!rateCheck.allowed) {
    if (rateCheck.reason === 'duplicate') {
      return json(res, 200, {
        ok: true,
        code: 'duplicate_prevented',
        message: 'Notification already dispatched for this login event.'
      });
    }
    return json(res, 429, {
      ok: false,
      code: 'rate_limited',
      message: rateCheck.reason === 'cooldown'
        ? 'Please wait before requesting another notification.'
        : 'Too many SMS requests for this number. Try again later.'
    });
  }

  let sendResult;
  try {
    if (PROVIDER === 'fast2sms') {
      sendResult = await sendLoginSuccessFast2Sms(phone, dateStr, timeStr);
    } else if (PROVIDER === '2factor') {
      sendResult = await sendLoginSuccess2Factor(phone, dateStr, timeStr);
    } else {
      sendResult = await sendLoginSuccessMsg91(phone, dateStr, timeStr);
    }
  } catch (error) {
    return json(res, 502, {
      ok: false,
      code: 'delivery_failed',
      message: `SMS provider request error: ${error.message || 'Network error'}`
    });
  }

  if (!sendResult.accepted) {
    return json(res, 502, {
      ok: false,
      code: 'delivery_failed',
      message: 'SMS provider did not accept the message.'
    });
  }

  // Record successful dispatch
  const prev = loginHistory.get(phone) || { sends: [] };
  loginHistory.set(phone, {
    lastSentAt: Date.now(),
    lastEventKey: eventKey,
    sends: [...(prev.sends || []), Date.now()]
  });

  if (body.requestId) {
    processedRequests.set(body.requestId, Date.now());
    // Prune processedRequests older than 24h
    if (processedRequests.size > 1000) {
      const oneDayAgo = Date.now() - 24 * 60 * 60 * 1000;
      for (const [id, ts] of processedRequests.entries()) {
        if (ts < oneDayAgo) processedRequests.delete(id);
      }
    }
  }

  console.log(`Login-success SMS dispatched to ...${phone.slice(-4)} at ${dateStr} ${timeStr}`);
  return json(res, 200, {
    ok: true,
    code: 'sent',
    message: 'Welcome SMS notification dispatched to your mobile number.'
  });
}

const server = http.createServer(async (req, res) => {
  try {
    if (req.method === 'GET' && req.url === '/health') {
      return json(res, 200, {
        ok: true,
        service: 'AgriVision-Edge SMS Notification Service',
        provider: PROVIDER,
        configured: providerConfigured(),
        requiresCredentials: !providerConfigured()
      });
    }
    if (req.method === 'POST' && (
      req.url === '/notify/welcome' ||
      req.url === '/sms/welcome' ||
      req.url === '/notify/login-success' ||
      req.url === '/sms/login-success'
    )) {
      return await handleLoginSuccess(req, res);
    }
    return json(res, 404, { ok: false, code: 'not_found', message: 'Unknown endpoint.' });
  } catch (error) {
    if (error instanceof SyntaxError) {
      return json(res, 400, { ok: false, code: 'invalid_json', message: 'Invalid request body.' });
    }
    return json(res, 500, { ok: false, code: 'error', message: 'Internal server error.' });
  }
});

server.listen(PORT, () => {
  console.log(`AgriVision Login-Success SMS backend running on port ${PORT}`);
  console.log(`Provider: ${PROVIDER}, Configured: ${providerConfigured()}`);
});
